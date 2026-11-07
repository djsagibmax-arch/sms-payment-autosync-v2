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

    /**
     * Resilient Fallback Polling: Polls every 6 seconds for any rows where whatsapp_sent = false
     * and whatsapp_number is filled by the website checkout.
     */
    private fun startPollingFallback(context: Context) {
        pollingJob?.cancel()
        pollingJob = managerScope?.launch {
            while (isActive && isRunning) {
                delay(6000L)
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
                    val sentCol = config.whatsappSentColumn.trim().ifEmpty { "whatsapp_sent" }

                    // Primary query: look for rows with whatsapp_number not null and whatsapp_sent is false
                    var queryUrl = "$baseUrl/rest/v1/$table?whatsapp_number=not.is.null&$sentCol=is.false&order=id.desc&limit=5"

                    var request = Request.Builder()
                        .url(queryUrl)
                        .addHeader("apikey", config.supabaseApiKey.trim())
                        .addHeader("Authorization", "Bearer ${config.supabaseApiKey.trim()}")
                        .addHeader("Content-Type", "application/json")
                        .build()

                    var response = httpClient.newCall(request).execute()
                    
                    // If sentCol (e.g. whatsapp_sent) column does not exist yet on Supabase, fallback query
                    if (!response.isSuccessful && response.code == 400) {
                        queryUrl = "$baseUrl/rest/v1/$table?whatsapp_number=not.is.null&order=id.desc&limit=5"
                        request = Request.Builder()
                            .url(queryUrl)
                            .addHeader("apikey", config.supabaseApiKey.trim())
                            .addHeader("Authorization", "Bearer ${config.supabaseApiKey.trim()}")
                            .addHeader("Content-Type", "application/json")
                            .build()
                        response = httpClient.newCall(request).execute()
                    }

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
                    // Ignored in loop, log debug
                    Log.d(TAG, "Polling check completed: ${e.message}")
                }
            }
        }
    }

    /**
     * Evaluates a payment row from either WebSocket or Polling.
     */
    private fun processPaymentRecord(context: Context, record: JSONObject) {
        val config = PreferencesManager.getInstance(context).loadConfig()
        if (!config.whatsappAutomationEnabled) {
            Log.d(TAG, "WhatsApp automation is disabled in settings. Skipping.")
            return
        }

        val rowId = record.optString("id").ifBlank { record.optString("trx_id") }
        if (rowId.isBlank()) {
            Log.w(TAG, "Payment record has no 'id' or 'trx_id'. Skipping.")
            return
        }

        // Check if already processed to avoid duplicate messaging
        if (processedRowIds.contains(rowId)) {
            return
        }

        val sentCol = config.whatsappSentColumn.trim().ifEmpty { "whatsapp_sent" }
        val isAlreadySent = record.optBoolean(sentCol, false)
        if (isAlreadySent) {
            processedRowIds.add(rowId)
            return
        }

        val numCol = config.whatsappNumberColumn.trim().ifEmpty { "whatsapp_number" }
        var rawPhone = record.optString(numCol)
        if (rawPhone.isBlank()) {
            // Fallback to sender_phone or phone if whatsapp_number is not set
            rawPhone = record.optString("sender_phone").ifBlank { record.optString("phone") }
        }

        if (rawPhone.isBlank()) {
            Log.w(TAG, "Row $rowId has empty phone number. Skipping.")
            return
        }

        val sanitized = PhoneNumberSanitizer.sanitize(rawPhone)
        if (sanitized == null) {
            Log.w(TAG, "Row $rowId phone '$rawPhone' cannot be sanitized to valid BD format. Skipping.")
            return
        }

        // Mark rowId as processed in memory so neither WebSocket nor Polling duplicates it
        processedRowIds.add(rowId)
        if (processedRowIds.size > 2000) {
            processedRowIds.clear()
        }

        val method = record.optString("method", "bKash")
        val trxId = record.optString("trx_id", rowId)
        val amount = if (record.has("price")) record.optDouble("price", 0.0)
            else if (record.has("product_price")) record.optDouble("product_price", 0.0)
            else record.optDouble("amount", 0.0)

        val customerName = record.optString("customer_name")
            .ifBlank { record.optString("name") }
            .ifBlank { record.optString("client_name") }
            .ifBlank { record.optString("customer") }
            .ifBlank { "সম্মানিত গ্রাহক" }

        // Optional website-generated custom message, product details, or download links
        val customMsg = record.optString("custom_message")
            .ifBlank { record.optString("message") }
            .ifBlank { record.optString("text") }
            .ifBlank { record.optString("whatsapp_message") }
            .ifBlank { null }

        val prodName = record.optString("product_name")
            .ifBlank { record.optString("product") }
            .ifBlank { record.optString("item_name") }
            .ifBlank { record.optString("course_name") }
            .ifBlank { record.optString("title") }
            .ifBlank { null }

        val prodLink = record.optString("product_link")
            .ifBlank { record.optString("vip_link") }
            .ifBlank { record.optString("link") }
            .ifBlank { record.optString("download_link") }
            .ifBlank { record.optString("drive_link") }
            .ifBlank { record.optString("invoice_url") }
            .ifBlank { null }

        val orderId = record.optString("order_id")
            .ifBlank { record.optString("order_number") }
            .ifBlank { null }

        val brand = record.optString("brand_name")
            .ifBlank { record.optString("company_name") }
            .ifBlank { record.optString("website_name") }
            .ifBlank { "Technography" }

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
        _lastEventLog.value = "New payment queued for +$sanitized (৳$amount)"
        Log.i(TAG, "Queued WhatsApp dispatch for rowId: $rowId, phone: $sanitized")
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
