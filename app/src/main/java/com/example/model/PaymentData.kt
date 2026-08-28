package com.example.model

enum class ConnectionMode {
    SUPABASE,
    CUSTOM_API,
    DIRECT_WEBHOOK
}

enum class SyncStatus {
    SUCCESS,
    FAILED,
    PARSED_ONLY,
    FILTERED_IGNORED,
    PENDING
}

data class PaymentData(
    val trxId: String,
    val amount: Double,
    val currency: String = "BDT",
    val method: String, // bKash, Nagad, Rocket, Upay, Cellfin, Custom
    val senderPhone: String? = null,
    val senderId: String,
    val rawMessage: String,
    val timestamp: Long = System.currentTimeMillis(),
    val reference: String? = null,
    val balance: Double? = null,
    val transactionType: String = "Received" // Cash In, Received, Payment, etc.
) {
    fun toJsonMap(): Map<String, Any?> {
        return mapOf(
            "trx_id" to trxId,
            "amount" to amount,
            "currency" to currency,
            "method" to method,
            "sender_phone" to senderPhone,
            "sender_id" to senderId,
            "transaction_type" to transactionType,
            "reference" to reference,
            "balance" to balance,
            "raw_message" to rawMessage,
            "timestamp" to timestamp,
            "created_at" to java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date(timestamp))
        )
    }
}

data class SyncConfig(
    val connectionMode: ConnectionMode = ConnectionMode.CUSTOM_API,
    
    // Multi-Destination Forwarding Toggles
    val forwardToCustomApi: Boolean = true,
    val forwardToSupabase: Boolean = false,
    val forwardToWebhook: Boolean = false,
    
    // Supabase Configuration
    val supabaseUrl: String = "",
    val supabaseApiKey: String = "",
    val supabaseTable: String = "sms_payments",
    
    // Custom API Configuration (e.g. Render / Node.js / PHP)
    val customEndpointUrl: String = "https://webhook.site/placeholder",
    val customAuthHeaderName: String = "Authorization",
    val customAuthToken: String = "",
    val customHttpMethod: String = "POST",
    
    // Direct Webhook Configuration
    val webhookUrl: String = "",
    val webhookSecret: String = "",
    
    // Wallet Settings
    val bkashEnabled: Boolean = true,
    val nagadEnabled: Boolean = true,
    val rocketEnabled: Boolean = true,
    val upayEnabled: Boolean = true,
    val cellfinEnabled: Boolean = true,
    val customKeywords: String = "16247, 16216, NAGAD, bKash, Upay",
    val onlyReceivedAndCashIn: Boolean = true,
    
    // Automation Settings
    val autoStartService: Boolean = true,
    val notifyOnSuccess: Boolean = true,
    val notifyOnFailure: Boolean = true
)
