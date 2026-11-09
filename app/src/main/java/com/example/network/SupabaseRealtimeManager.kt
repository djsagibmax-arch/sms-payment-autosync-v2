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
    private val pendingQueue = ConcurrentLinkedQueue<PaymentQueueItem>()

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

        connectWebSocket(context)
        startPollingFallback(context)
        startQueueProcessor(context)
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

    /**
     * Resilient Fallback Polling:
     * Only runs when WebSocket is NOT listening, querying only confirmed orders
     * where customer_name and whatsapp_number are non-null and whatsapp_sent is false.
     */
    private fun startPollingFallback(context: Context) {
        pollingJob?.cancel()
        pollingJob = managerScope?.launch {
            while (isActive && isRunning) {
                // If WebSocket is actively listening to real-time events, do not poll aggressively!
                if (_status.value == RealtimeStatus.LISTENING) {
                    delay(30000L)
                    continue
                }

                delay(20000L)
                try {
                    val config = PreferencesManager.getInstance(context).loadConfig()
                    if (!config.whatsappAutomationEnabled || config.supabaseUrl.isBlank() || config.supabaseApiKey.isBlank()) {
                        continue
                    }

                    var baseUrl = config.supabaseUrl.trim().removeSuffix("/")
                    if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                        baseUrl = "https://$baseUrl"
                    }

                    val table = config.supabasePaymentsTable.trim().ifEmpty { "payments" }

                    // Fetch latest payment records (safe query that won't fail if specific column filters are missing)
                    val queryUrl = "$baseUrl/rest/v1/$table?order=id.desc&limit=10"

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
                } catch (e: Exception) {
                    Log.d(TAG, "Fallback polling check: ${e.message}")
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
            Log.w(TAG, "Payment record has no valid id or trx_id. Skipping.")
            return
        }

        // 1. Persistent deduplication check: has this row already been processed?
        if (processedRowIds.contains(rowId) || prefs.isRowProcessed(rowId)) {
            return
        }

        // 2. Check if already marked as sent in Supabase database
        val sentCol = config.whatsappSentColumn.trim().ifEmpty { "whatsapp_sent" }
        val isAlreadySent = record.optBoolean(sentCol, false) ||
                record.optBoolean("sent", false) ||
                record.optBoolean("is_sent", false)
        if (isAlreadySent) {
            processedRowIds.add(rowId)
            prefs.markRowProcessed(rowId)
            return
        }

        // 3. Status Check: Do not dispatch if explicitly marked cancelled, failed, or rejected
        val statusVal = (record.getCleanString("status")
            ?: record.getCleanString("order_status")
            ?: record.getCleanString("payment_status")
            ?: "").lowercase().trim()

        if (statusVal == "cancelled" || statusVal == "failed" || statusVal == "rejected") {
            Log.d(TAG, "Row $rowId is $statusVal. Skipping.")
            return
        }

        // 4. Extract Customer Phone Number
        val numCol = config.whatsappNumberColumn.trim().ifEmpty { "whatsapp_number" }
        val rawPhone = record.getCleanString(numCol)
            ?: record.getCleanString("whatsapp_number")
            ?: record.getCleanString("customer_phone")
            ?: record.getCleanString("phone")
            ?: record.getCleanString("sender_phone")
            ?: record.getCleanString("mobile")

        if (rawPhone.isNullOrBlank()) {
            Log.d(TAG, "Row $rowId: Customer phone number not found in record. Skipping.")
            return
        }

        val sanitized = PhoneNumberSanitizer.sanitize(rawPhone)
        if (sanitized == null) {
            Log.w(TAG, "Row $rowId: Phone '$rawPhone' cannot be sanitized to valid format. Skipping.")
            return
        }

        // 5. Customer Name & Product Name with safe fallbacks (never output 'null'!)
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

        // Mark rowId as processed in memory and disk so it will never be queued again
        processedRowIds.add(rowId)
        prefs.markRowProcessed(rowId)
        if (processedRowIds.size > 2000) {
            processedRowIds.clear()
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

                        // Prepare message:
                        // 1. If the website generated an exact custom_message in the database row, use it!
                        // 2. Otherwise, use the template with dynamic product, link, and payment variables.
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
                            pendingQueue.poll() // Remove item
                            _pendingCount.value = pendingQueue.size
                            _lastEventLog.value = "Dispatched WhatsApp to +${item.phone}"
                            // Wait at least 6 seconds before attempting next queue item
                            delay(6000L)
                        } else {
                            // If failed to launch (e.g. invalid), drop it
                            pendingQueue.poll()
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
