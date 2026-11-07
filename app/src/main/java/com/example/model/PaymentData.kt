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
    val connectionMode: ConnectionMode = ConnectionMode.SUPABASE,
    
    // Multi-Destination Forwarding Toggles
    val forwardToCustomApi: Boolean = true,
    val forwardToSupabase: Boolean = true,
    val forwardToWebhook: Boolean = false,
    
    // Supabase Configuration
    val supabaseUrl: String = "https://safymsagxrjymhfdzkph.supabase.co",
    val supabaseApiKey: String = "sb_publishable_qthdZZg-tjDRc_loLbVUYg_b77rK-Bv",
    val supabaseTable: String = "payments",
    
    // Custom API Configuration (Render Bot)
    val customEndpointUrl: String = "https://my-sales-bot-5d70.onrender.com/webhook/sms",
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
    val notifyOnFailure: Boolean = true,

    // WhatsApp UI Automation Settings (Zero QR / Zero Meta Cloud API)
    val whatsappAutomationEnabled: Boolean = true,
    val supabasePaymentsTable: String = "payments",
    val whatsappNumberColumn: String = "whatsapp_number",
    val whatsappSentColumn: String = "whatsapp_sent",
    val whatsappMessageTemplate: String = DEFAULT_VIP_WHATSAPP_TEMPLATE,
    val whatsappSendDelayMs: Long = 1200L,
    val whatsappAutoBack: Boolean = true,

    // AI Sales Chatbot Settings (WhatsApp & Messenger Notification Auto-Reply)
    val chatbotEnabled: Boolean = true,
    val chatbotWhatsAppEnabled: Boolean = true,
    val chatbotMessengerEnabled: Boolean = true,
    val geminiApiKey: String = "",
    val geminiModel: String = "gemini-2.5-flash",
    val liveChatHandoverActive: Boolean = false,
    val chatbotAutoVerifyPayment: Boolean = true,
    val chatbotCustomPrompt: String = DEFAULT_CHATBOT_PROMPT
)

const val DEFAULT_CHATBOT_PROMPT = """তুমি Technography (বাংলাদেশের প্রিমিয়াম ডিজিটাল প্রোডাক্ট স্টোর)-এর একজন অত্যন্ত ভদ্র, বন্ধুসুলভ ও পারদর্শী সেলস অ্যাসিস্ট্যান্ট।
কাস্টমারদের সাথে সম্পূর্ণ মানুষের মতো স্বাভাবিক, মধুর ও প্রফেশনাল বাংলায় কথা বলবে।

তোমার দায়িত্বসমূহ:
১. কাস্টমারকে সালাম/অভিবাদন জানিয়ে উষ্ণভাবে স্বাগতম জানাও।
২. আমাদের প্রোডাক্ট কালেকশন থেকে কাস্টমারের প্রয়োজন বুঝে সঠিক প্রোডাক্টটি সুন্দরভাবে সাজেস্ট কর এবং মূল্য ও বিশেষ সুবিধাসমূহ বুঝিয়ে বল।
৩. কাস্টমার কিনতে চাইলে আমাদের পার্সোনাল বিকাশ/নগদ/রকেট নাম্বারে Send Money করতে বল এবং পেমেন্টের পর TrxID দিতে বল।
৪. কাস্টমার কোনো TrxID দিলে তা যাচাই করে অর্ডার নিশ্চিত কর এবং নির্দিষ্ট প্রোডাক্টের VIP ডাউনলোড লিংক প্রদান কর।
৫. কোনো অবান্তর বিষয়ে কথা না বাড়িয়ে সদাচরণ ও বিশ্বাসযোগ্যতার সাথে পণ্য বিক্রয় ও গ্রাহক সেবায় মনোযোগ দাও।"""

const val DEFAULT_VIP_WHATSAPP_TEMPLATE = """অভিনন্দন! আপনার অর্ডার কনফার্ম হয়েছে
প্রিয় {name},
{brand_name}-এর সাথে থাকার জন্য ধন্যবাদ! আপনার পেমেন্ট সফলভাবে যাচাই করা হয়েছে।

📦 প্রোডাক্ট: {product_name}
💰 পরিশোধিত মূল্য: {amount} ৳

নিচের লিংকে গিয়ে আপনার সমস্ত ফাইল ও মেগা ড্রাইভ ফোল্ডারগুলো ডাউনলোড করে নিন:
🔗 VIP লিংক: {product_link}

যেকোনো সহযোগিতায় এই নম্বরে মেসেজ দিন।
{brand_name} • Instant Digital Delivery"""


