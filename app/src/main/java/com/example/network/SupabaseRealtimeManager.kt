package com.example.network

import android.content.Context
import android.util.Log
import com.example.data.PreferencesManager
import com.example.model.SyncConfig
import com.example.service.WhatsAppAutomationManager
import com.example.util.PhoneNumberSanitizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

enum class RealtimeStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    LISTENING,
    ERROR
}

data class PaymentQueueItem(
    val rowId: String,
    val phone: String,
    val method: String,
    val trxId: String,
    val amount: Double,
    val customerName: String,
    val customMessage: String? = null,
    val productName: String? = null,
    val productLink: String? = null,
    val orderId: String? = null,
    val brandName: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

object SupabaseRealtimeManager {
    private const val TAG = "SupabaseRealtime"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private var managerScope: CoroutineScope? = null
    private var webSocket: WebSocket? = null
    private var isRunning = false
    private var heartbeatJob: Job? = null
    private var pollingJob: Job? = null
    private var queueProcessorJob: Job? = null

    // Track recently processed row IDs to avoid duplicate sends
    private val processedRowIds = Collections.synchronizedSet(LinkedHashSet<String>())
    private val failureCountMap = ConcurrentHashMap<String, Int>()
    private val pendingQueue = ConcurrentLinkedQueue<PaymentQueueItem>()

    @Volatile
    private var inFlightRowId: String? = null

    fun onRowSentSuccessfully(rowId: String) {
        processedRowIds.add(rowId)
        failureCountMap.remove(rowId)
        _lastEventLog.value = "Sent successfully for row #$rowId"
    }

    private val _status = MutableStateFlow(RealtimeStatus.DISCONNECTED)
    val status: StateFlow<RealtimeStatus> = _status.asStateFlow()

    private val _lastEventLog = MutableStateFlow("Ready to connect")
    val lastEventLog: StateFlow<String> = _lastEventLog.asStateFlow()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    /**
     * Start listening for Supabase postgres changes on the configured payments table.
     */
    fun start(context: Context) {
        if (isRunning) {
            Log.d(TAG, "SupabaseRealtimeManager is already running.")
            return
        }

        val config = PreferencesManager.getInstance(context).loadConfig()
        if (config.supabaseUrl.isBlank() || config.supabaseApiKey.isBlank()) {
            _status.value = RealtimeStatus.DISCONNECTED
            _lastEventLog.value = "Supabase URL / API Key not configured"
            Log.w(TAG, "Supabase URL or API Key is blank. Realtime listener paused.")
            return
        }

        isRunning = true
        managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        _status.value = RealtimeStatus.CONNECTING
        _lastEventLog.value = "Connecting to Supabase Realtime..."

        // Seed existing rows on startup so previous test/historical records NEVER trigger WhatsApp
        managerScope?.launch {
            baselineExistingRows(context)
            connectWebSocket(context)
            startPollingFallback(context)
            startQueueProcessor(context)
        }
    }

    /**
     * Stop all listeners and disconnect WebSocket.
     */
    fun stop() {
        isRunning = false
        heartbeatJob?.cancel()
        pollingJob?.cancel()
        queueProcessorJob?.cancel()

        try {
            webSocket?.close(1000, "Service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing WebSocket: ${e.message}")
        }
        webSocket = null

        managerScope?.cancel()
        managerScope = null

        _status.value = RealtimeStatus.DISCONNECTED
        _lastEventLog.value = "Listener stopped"
    }

    /**
     * Reconnects with updated configuration.
     */
    fun restart(context: Context) {
        stop()
        start(context)
    }

    private fun connectWebSocket(context: Context) {
        managerScope?.launch {
            val config = PreferencesManager.getInstance(context).loadConfig()
            var rawUrl = config.supabaseUrl.trim().removeSuffix("/")
            if (rawUrl.isBlank() || config.supabaseApiKey.isBlank()) return@launch

            // Convert https://abc.supabase.co to wss://abc.supabase.co/realtime/v1/websocket
            val host = rawUrl.removePrefix("https://").removePrefix("http://")
            val wsUrl = "wss://$host/realtime/v1/websocket?apikey=${config.supabaseApiKey.trim()}&vsn=1.0.0"
            val tableName = config.supabasePaymentsTable.trim().ifEmpty { "payments" }

            val request = Request.Builder()
                .url(wsUrl)
                .build()

            webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    Log.i(TAG, "Supabase Realtime WebSocket Connected successfully!")
                    _status.value = RealtimeStatus.CONNECTED
                    _lastEventLog.value = "WebSocket connected. Subscribing to $tableName..."

                    // 1. Join Phoenix Channel for postgres_changes (INSERT & UPDATE)
                    val joinPayload = JSONObject().apply {
                        put("topic", "realtime:public:$tableName")
                        put("event", "phx_join")
                        put("payload", JSONObject().apply {
                            put("config", JSONObject().apply {
                                val changesArray = JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("event", "*")
                                        put("schema", "public")
                                        put("table", tableName)
                                    })
                                }
                                put("postgres_changes", changesArray)
                            })
                        })
                        put("ref", "join_1")
                    }

                    ws.send(joinPayload.toString())
                    _status.value = RealtimeStatus.LISTENING
                    _lastEventLog.value = "Listening to table '$tableName' in real-time"

                    // 2. Start Phoenix Heartbeat (every 25 seconds)
                    startHeartbeat(ws)
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    handleIncomingMessage(context, text)
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "Supabase WebSocket failure: ${t.message}. Will retry via polling.")
                    _status.value = RealtimeStatus.ERROR
                    _lastEventLog.value = "WebSocket disconnected: ${t.localizedMessage ?: "Network error"}"
                    // Reconnect attempt after 10 seconds
                    managerScope?.launch {
                        delay(10000L)
                        if (isRunning) {
                            connectWebSocket(context)
                        }
                    }
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "Supabase WebSocket closed ($code: $reason)")
                    if (isRunning) {
                        _status.value = RealtimeStatus.CONNECTING
                    }
                }
            })
        }
    }

    private fun startHeartbeat(ws: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = managerScope?.launch {
            var count = 0
            while (isActive && isRunning) {
                delay(25000L)
                try {
                    count++
                    val heartbeat = JSONObject().apply {
                        put("topic", "phoenix")
                        put("event", "heartbeat")
                        put("payload", JSONObject())
                        put("ref", "hb_$count")
                    }
                    ws.send(heartbeat.toString())
                } catch (e: Exception) {
                    Log.e(TAG, "Heartbeat failed: ${e.message}")
                }
            }
        }
    }

    private fun handleIncomingMessage(context: Context, jsonText: String) {
        try {
            val json = JSONObject(jsonText)
            val event = json.optString("event")

            if (event == "postgres_changes") {
                val payload = json.optJSONObject("payload") ?: return
                val data = payload.optJSONObject("data") ?: payload
                val record = data.optJSONObject("record")
                    ?: data.optJSONObject("new")
                    ?: payload.optJSONObject("record")
                    ?: payload.optJSONObject("new")
                    ?: return

                Log.i(TAG, "Received Realtime postgres event record: $record")
                processPaymentRecord(context, record)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming WebSocket message: ${e.message}", e)
        }
    }

    private fun JSONObject.getCleanString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = optString(key).trim()
        if (v.isEmpty() || v.equals("null", ignoreCase = true) || v.equals("undefined", ignoreCase = true)) {
            return null
        }
        return v
    }

    private fun parseIsoTimestamp(iso: String): Long? {
        val formats = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (pattern in formats) {
            try {
                val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val date = sdf.parse(iso)
                if (date != null) return date.time
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Seeds existing rows on app startup so old orders are never re-sent.
     */
    private fun baselineExistingRows(context: Context) {
        try {
            val config = PreferencesManager.getInstance(context).loadConfig()
            if (config.supabaseUrl.isBlank() || config.supabaseApiKey.isBlank()) return
            var baseUrl = config.supabaseUrl.trim().removeSuffix("/")
            if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                baseUrl = "https://$baseUrl"
            }
            val table = config.supabasePaymentsTable.trim().ifEmpty { "payments" }
            // Only baseline rows that were ALREADY SENT in Supabase so unsent/new orders are never skipped
            val queryUrl = "$baseUrl/rest/v1/$table?whatsapp_sent=eq.true&select=id,trx_id&order=id.desc&limit=50"

            val request = Request.Builder()
                .url(queryUrl)
                .addHeader("apikey", config.supabaseApiKey.trim())
                .addHeader("Authorization", "Bearer ${config.supabaseApiKey.trim()}")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                if (body.startsWith("[")) {
                    val array = JSONArray(body)
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val id = item.getCleanString("id") ?: item.getCleanString("trx_id")
                        if (!id.isNullOrBlank()) {
                            processedRowIds.add(id)
                        }
                    }
                    Log.i(TAG, "Baselined ${array.length()} already-sent orders on startup.")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Baseline note: ${e.message}")
        }
    }

    /**
     * Resilient Fallback Polling:
     * Queries confirmed orders where whatsapp_number is not null and whatsapp_sent is false or null.
     */
    private fun startPollingFallback(context: Context) {
        pollingJob?.cancel()
        pollingJob = managerScope?.launch {
            while (isActive && isRunning) {
                delay(5000L)
                try {
                    val config = PreferencesManager.getInstance(context).loadConfig()
                    if (config.whatsappAutomationEnabled && config.supabaseUrl.isNotBlank() && config.supabaseApiKey.isNotBlank()) {
                        var baseUrl = config.supabaseUrl.trim().removeSuffix("/")
                        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                            baseUrl = "https://$baseUrl"
                        }

                        val table = config.supabasePaymentsTable.trim().ifEmpty { "payments" }
                        // Query orders that have a WhatsApp number and haven't been sent yet (handles both false and null)
                        val queryUrl = "$baseUrl/rest/v1/$table?whatsapp_number=not.is.null&or=(whatsapp_sent.eq.false,whatsapp_sent.is.null)&order=id.desc&limit=10"

                        val request = Request.Builder()
                            .url(queryUrl)
                            .addHeader("apikey", config.supabaseApiKey.trim())
                            .addHeader("Authorization", "Bearer ${config.supabaseApiKey.trim()}")
                            .addHeader("Content-Type", "application/json")
                            .build()

                        val response = httpClient.newCall(request).execute()
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            if (body.startsWith("[")) {
                                val array = JSONArray(body)
                                for (i in 0 until array.length()) {
                                    val item = array.getJSONObject(i)
                                    processPaymentRecord(context, item)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Polling check: ${e.message}")
                }
            }
        }
    }

    /**
     * Evaluates a payment row from either WebSocket or Polling.
     * Verifies that the customer has confirmed their order and dispatches WhatsApp automation.
     */
    private fun processPaymentRecord(context: Context, record: JSONObject) {
        val prefs = PreferencesManager.getInstance(context)
        val config = prefs.loadConfig()
        if (!config.whatsappAutomationEnabled) {
            Log.d(TAG, "WhatsApp automation is disabled in settings. Skipping.")
            return
        }

        val rowId = record.getCleanString("id") ?: record.getCleanString("trx_id")
        if (rowId.isNullOrBlank()) {
            return
        }

        // 1. Check if already marked as sent in Supabase database
        val sentCol = config.whatsappSentColumn.trim().ifEmpty { "whatsapp_sent" }
        val isAlreadySent = record.optBoolean(sentCol, false) ||
                record.optBoolean("sent", false) ||
                record.optBoolean("is_sent", false)
        if (isAlreadySent) {
            processedRowIds.add(rowId)
            prefs.markRowProcessed(rowId)
            return
        }

        // 2. Deduplication check (prevents re-queuing while already processed in this session)
        if (processedRowIds.contains(rowId)) {
            return
        }

        // 3. Order Age Filter: Ignore orders older than 45 minutes (checks updated_at first, then created_at)
        val timeStr = record.getCleanString("updated_at")
            ?: record.getCleanString("created_at")
            ?: record.getCleanString("timestamp")
            ?: record.getCleanString("date")
        if (!timeStr.isNullOrBlank()) {
            val recordTime = parseIsoTimestamp(timeStr)
            if (recordTime != null) {
                val ageMillis = System.currentTimeMillis() - recordTime
                if (ageMillis > 45 * 60 * 1000L) {
                    Log.i(TAG, "Row $rowId is old (${ageMillis / 60000} mins). Skipping automatic WhatsApp dispatch.")
                    processedRowIds.add(rowId)
                    return
                }
            }
        }

        // 4. Status Check: Do not dispatch if explicitly marked cancelled, failed, or rejected
        val statusVal = (record.getCleanString("status")
            ?: record.getCleanString("order_status")
            ?: record.getCleanString("payment_status")
            ?: "").lowercase().trim()

        if (statusVal == "cancelled" || statusVal == "failed" || statusVal == "rejected") {
            Log.d(TAG, "Row $rowId is $statusVal. Skipping.")
            return
        }

        // 5. Must be a confirmed order with a valid customer WhatsApp number!
        // When customer has not yet confirmed order on website, whatsapp_number is null.
        val numCol = config.whatsappNumberColumn.trim().ifEmpty { "whatsapp_number" }
        val rawPhone = record.getCleanString(numCol)
            ?: record.getCleanString("whatsapp_number")
            ?: record.getCleanString("customer_phone")
            ?: record.getCleanString("phone")

        if (rawPhone.isNullOrBlank()) {
            Log.d(TAG, "Row $rowId: Customer WhatsApp number not found (unconfirmed order). Skipping.")
            return
        }

        val sanitized = PhoneNumberSanitizer.sanitize(rawPhone)
        if (sanitized == null) {
            Log.w(TAG, "Row $rowId: Phone '$rawPhone' cannot be sanitized to valid format. Skipping.")
            return
        }

        // If is_used exists in row, it must be true/1 (confirmed by customer on website)
        if (record.has("is_used") && !record.isNull("is_used")) {
            val v = record.opt("is_used")
            val isConfirmed = when (v) {
                is Boolean -> v
                is Number -> v.toInt() == 1
                is String -> v.equals("true", true) || v.equals("t", true) || v == "1"
                else -> true
            }
            if (!isConfirmed) {
                Log.d(TAG, "Row $rowId: Order is_used is false (unconfirmed). Skipping.")
                return
            }
        }

        // 5. Prevent duplicate queueing if already in queue or currently sending
        if (pendingQueue.any { it.rowId == rowId } || inFlightRowId == rowId) {
            return
        }

        // 6. Customer Name & Product Name with safe fallbacks (never output 'null'!)
        val rawCustomerName = record.getCleanString("customer_name")
            ?: record.getCleanString("name")
            ?: record.getCleanString("client_name")
            ?: record.getCleanString("customer")

        val customerName = if (rawCustomerName.isNullOrBlank() || rawCustomerName.equals("null", ignoreCase = true)) {
            "সম্মানিত গ্রাহক"
        } else {
            rawCustomerName
        }

        val rawProdName = record.getCleanString("product_name")
            ?: record.getCleanString("product")
            ?: record.getCleanString("item_name")
            ?: record.getCleanString("course_name")
            ?: record.getCleanString("title")

        val prodName = if (rawProdName.isNullOrBlank() || rawProdName.equals("null", ignoreCase = true)) {
            "Mega Bundle (All in One)"
        } else {
            rawProdName
        }

        val rawProdLink = record.getCleanString("product_link")
            ?: record.getCleanString("vip_link")
            ?: record.getCleanString("link")
            ?: record.getCleanString("download_link")
            ?: record.getCleanString("drive_link")
            ?: record.getCleanString("invoice_url")

        val prodLink = if (rawProdLink.isNullOrBlank() || rawProdLink.equals("null", ignoreCase = true)) {
            "https://technographybd.xyz"
        } else {
            rawProdLink
        }

        val method = record.getCleanString("method") ?: "bKash"
        val trxId = record.getCleanString("trx_id") ?: rowId
        val amount = if (record.has("price")) record.optDouble("price", 0.0)
            else if (record.has("product_price")) record.optDouble("product_price", 0.0)
            else record.optDouble("amount", 0.0)

        // Optional website-generated custom message
        val customMsg = record.getCleanString("custom_message")
            ?: record.getCleanString("message")
            ?: record.getCleanString("text")
            ?: record.getCleanString("whatsapp_message")

        val orderId = record.getCleanString("order_id") ?: record.getCleanString("order_number")
        val brand = record.getCleanString("brand_name")
            ?: record.getCleanString("company_name")
            ?: record.getCleanString("website_name")
            ?: "Technography"

        val queueItem = PaymentQueueItem(
            rowId = rowId,
            phone = sanitized,
            method = method,
            trxId = trxId,
            amount = amount,
            customerName = customerName,
            customMessage = customMsg,
            productName = prodName,
            productLink = prodLink,
            orderId = orderId,
            brandName = brand
        )

        pendingQueue.add(queueItem)
        _pendingCount.value = pendingQueue.size
        _lastEventLog.value = "New confirmed order for $customerName (+${sanitized})"
        Log.i(TAG, "Queued WhatsApp dispatch for confirmed order rowId: $rowId, customer: $customerName, phone: $sanitized")
    }

    /**
     * Processes queue items one by one to ensure WhatsApp intents don't clash.
     */
    private fun startQueueProcessor(context: Context) {
        queueProcessorJob?.cancel()
        queueProcessorJob = managerScope?.launch {
            while (isActive && isRunning) {
                val item = pendingQueue.peek()
                if (item != null) {
                    // Check if WhatsApp is currently busy sending another message
                    if (!WhatsAppAutomationManager.isPending) {
                        val config = PreferencesManager.getInstance(context).loadConfig()

                        val formattedAmount = if (item.amount % 1.0 == 0.0) item.amount.toInt().toString() else item.amount.toString()
                        val message = if (!item.customMessage.isNullOrBlank()) {
                            item.customMessage
                        } else {
                            config.whatsappMessageTemplate
                                .replace("{name}", item.customerName)
                                .replace("{product_name}", item.productName ?: "Mega Bundle (All in One)")
                                .replace("{product_link}", item.productLink ?: "https://technographybd.xyz")
                                .replace("{vip_link}", item.productLink ?: "https://technographybd.xyz")
                                .replace("{link}", item.productLink ?: "https://technographybd.xyz")
                                .replace("{amount}", formattedAmount)
                                .replace("{price}", formattedAmount)
                                .replace("{brand_name}", item.brandName ?: "Technography")
                                .replace("{order_id}", item.orderId ?: "")
                                .replace("{method}", item.method)
                                .replace("{trx_id}", item.trxId)
                                .replace("{phone}", item.phone)
                        }

                        inFlightRowId = item.rowId
                        val dispatched = WhatsAppAutomationManager.triggerSend(
                            context = context,
                            rawPhone = item.phone,
                            message = message,
                            rowId = item.rowId,
                            method = item.method,
                            trxId = item.trxId,
                            amount = item.amount
                        )

                        if (dispatched) {
                            _lastEventLog.value = "Dispatched WhatsApp to +${item.phone}"
                            // Wait while WhatsApp automation sends the message
                            var waitedMs = 0L
                            while (WhatsAppAutomationManager.isPending && isActive && isRunning && waitedMs < 26000L) {
                                delay(500L)
                                waitedMs += 500L
                            }

                            if (waitedMs >= 26000L) {
                                val fails = (failureCountMap[item.rowId] ?: 0) + 1
                                failureCountMap[item.rowId] = fails
                                if (fails >= 2) {
                                    processedRowIds.add(item.rowId)
                                    _lastEventLog.value = "Row #${item.rowId} skipped after $fails attempts"
                                }
                            }

                            pendingQueue.poll() // Remove item after completion
                            inFlightRowId = null
                            _pendingCount.value = pendingQueue.size
                            // Wait 3 seconds before attempting next queue item
                            delay(3000L)
                        } else {
                            processedRowIds.add(item.rowId)
                            pendingQueue.poll()
                            inFlightRowId = null
                            _pendingCount.value = pendingQueue.size
                            delay(2000L)
                        }
                    } else {
                        // Wait for current send to complete
                        delay(1500L)
                    }
                } else {
                    delay(1500L)
                }
            }
        }
    }
}
