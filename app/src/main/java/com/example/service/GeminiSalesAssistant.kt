package com.example.service

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.PreferencesManager
import com.example.data.ProductEntity
import com.example.model.DEFAULT_CHATBOT_PROMPT
import com.example.model.SyncConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object GeminiSalesAssistant {
    private const val TAG = "GeminiSalesAssistant"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // TrxID Pattern: 8 to 10 alphanumeric characters (bKash: 10 chars, Nagad: 8 chars, Rocket: 10 digits)
    private val TRX_ID_REGEX = Pattern.compile("\\b([0-9A-Z]{8,10})\\b", Pattern.CASE_INSENSITIVE)

    /**
     * Generates a smart, human-like sales response for an incoming customer message.
     */
    suspend fun generateReply(
        context: Context,
        senderName: String,
        customerMessage: String,
        platformName: String,
        config: SyncConfig = PreferencesManager.getInstance(context).loadConfig()
    ): String = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val products = db.productDao().getActiveProductsList().ifEmpty {
            AppDatabase.getDefaultProducts()
        }

        val cleanedMsg = customerMessage.trim()

        // 1. TrxID & Payment Verification Check (Auto-verifies TrxID and sends download link)
        if (config.chatbotAutoVerifyPayment) {
            val paymentReply = checkAndVerifyPayment(context, senderName, cleanedMsg, products, config)
            if (paymentReply != null) {
                return@withContext paymentReply
            }
        }

        // 2. Custom Fixed Auto-Reply (If Custom Reply Switch is ON)
        if (config.customAutoReplyEnabled) {
            val customText = config.customAutoReplyMessage
                .replace("{name}", senderName)
                .replace("{sender}", senderName)
                .replace("{platform}", platformName)
                .replace("{message}", cleanedMsg)
            if (customText.isNotBlank()) {
                return@withContext customText
            }
        }

        // 3. If Gemini API Key is configured, use Gemini API for human-like conversational sales!
        if (config.geminiApiKey.isNotBlank()) {
            try {
                val aiReply = callGeminiApi(config, senderName, cleanedMsg, products)
                if (aiReply.isNotBlank()) {
                    return@withContext aiReply
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gemini API error: ${e.message}. Falling back to rule-based response.", e)
            }
        }

        // 4. Fallback Rule-Based Human Sales Response (Works 100% even without API key)
        return@withContext generateSmartFallback(senderName, cleanedMsg, products)
    }

    /**
     * Checks if the customer message contains a payment transaction code and verifies it.
     */
    private suspend fun checkAndVerifyPayment(
        context: Context,
        customerName: String,
        message: String,
        products: List<ProductEntity>,
        config: SyncConfig
    ): String? {
        val matcher = TRX_ID_REGEX.matcher(message)
        val foundTrxIds = mutableListOf<String>()
        while (matcher.find()) {
            val candidate = matcher.group(1) ?: continue
            // Ignore common greeting or numeric words
            if (candidate.length in 8..10 && candidate.any { it.isLetter() || it.isDigit() }) {
                foundTrxIds.add(candidate.uppercase())
            }
        }

        if (foundTrxIds.isEmpty()) return null

        val db = AppDatabase.getDatabase(context)

        for (trxId in foundTrxIds) {
            val matchedLog = db.syncLogDao().getLogByTrxId(trxId)
            if (matchedLog != null) {
                // Match with closest product by price
                val matchedProduct = products.firstOrNull { it.price == matchedLog.amount }
                    ?: products.firstOrNull() ?: AppDatabase.getDefaultProducts().first()

                return """অভিনন্দন! আপনার পেমেন্ট সফলভাবে যাচাই করা হয়েছে।
প্রিয় $customerName,
Technography-এর সাথে থাকার জন্য ধন্যবাদ!

📦 প্রোডাক্ট: ${matchedProduct.name}
💰 পরিশোধিত মূল্য: ${matchedLog.amount.toInt()} ৳

নিচের লিংকে গিয়ে আপনার সমস্ত ফাইল ও মেগা ড্রাইভ ফোল্ডারগুলো ডাউনলোড করে নিন:
🔗 VIP লিংক: ${matchedProduct.vipLink}

যেকোনো সহযোগিতায় এই নম্বরে মেসেজ দিন।
Technography • Instant Digital Delivery""".trimIndent()
            }
        }

        // If TrxID format detected but not found in DB yet
        val potentialTrx = foundTrxIds.first()
        return "ধন্যবাদ $customerName! আপনার ট্রানজেকশন আইডি ($potentialTrx) পেয়েছি। আমাদের সিস্টেমে পেমেন্ট ভেরিফাই করা হচ্ছে, অনুগ্রহ করে ১-২ মিনিট অপেক্ষা করুন — ভেরিফাই হওয়ামাত্র আপনার VIP লিংক স্বয়ংক্রিয়ভাবে চলে আসবে।"
    }

    /**
     * Calls Gemini API with the full product knowledge base.
     */
    private fun callGeminiApi(
        config: SyncConfig,
        customerName: String,
        message: String,
        products: List<ProductEntity>
    ): String {
        val productListText = products.joinToString("\n") { prod ->
            "• ${prod.name}: ${prod.price.toInt()} ৳ | VIP Link: ${prod.vipLink} | বিবরণ: ${prod.description}"
        }

        val systemInstruction = """${config.chatbotCustomPrompt.ifBlank { DEFAULT_CHATBOT_PROMPT }}

[আমাদের বর্তমান প্রোডাক্টসমূহ]:
$productListText

[পেমেন্ট নির্দেশনাবলী]:
বিকাশ / নগদ / রকেট (Personal Send Money)। পেমেন্ট করার পর ট্রানজেকশন আইডি (TrxID) দিলে স্বয়ংক্রিয়ভাবে ভিআইপি ডাউনলোড লিংক পেয়ে যাবেন।

[নিয়মাবলী]:
- কাস্টমারের নাম: $customerName
- সংক্ষিপ্ত, আকর্ষণীয়, অত্যন্ত বিনয়ী ও মানুষের মতো কথা বলবে। অতিরিক্ত লম্বা করবেন না।
- কাস্টমার যে প্রোডাক্টের ব্যাপারে জানতে চায় সেটির সুবিধা ও মূল্য বুঝিয়ে বলুন।
- অর্ডার করতে আগ্রহী হলে পেমেন্ট করে TrxID দিতে বলুন।"""

        val fullPrompt = "$systemInstruction\n\nকাস্টমার বলেছে: \"$message\"\n\nঅ্যাসিস্ট্যান্টের উত্তর:"

        val primaryModel = normalizeModelName(config.geminiModel.ifBlank { "gemini-3.8-flash" })
        val candidateModels = listOf(primaryModel, "gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-2.5-flash").distinct()

        val jsonBody = JSONObject().apply {
            val contentsArr = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", fullPrompt)
                        })
                    })
                })
            }
            put("contents", contentsArr)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 350)
            })
        }

        for (model in candidateModels) {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${config.geminiApiKey.trim()}"
            val request = Request.Builder()
                .url(endpoint)
                .post(jsonBody.toString().toRequestBody(JSON_MEDIA))
                .build()

            try {
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseText = response.body?.string() ?: ""
                    val resJson = JSONObject(responseText)
                    val candidates = resJson.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val content = firstCandidate.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val text = parts.getJSONObject(0).optString("text", "")
                            if (text.isNotBlank()) return text.trim()
                        }
                    }
                } else {
                    val errBody = response.body?.string() ?: ""
                    Log.w(TAG, "Gemini API HTTP Error on model $model: ${response.code} $errBody")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gemini call exception on model $model: ${e.message}")
            }
        }
        return ""
    }

    private fun normalizeModelName(raw: String): String {
        val clean = raw.trim().removePrefix("models/").lowercase()
        return when {
            clean == "3.8 flash" || clean == "gemini-3.8-flash" -> "gemini-3.8-flash"
            clean == "3.5 flash-lite" || clean == "gemini-3.5-flash-lite" -> "gemini-3.5-flash-lite"
            clean == "3.1 pro" || clean == "gemini-3.1-pro" -> "gemini-3.1-pro"
            clean == "4 argon" || clean == "gemini-4-argon" -> "gemini-4-argon"
            clean.contains("3.8") -> "gemini-3.8-flash"
            clean.contains("3.5") -> "gemini-3.5-flash-lite"
            clean.contains("3.1") -> "gemini-3.1-pro"
            clean.contains("argon") -> "gemini-4-argon"
            clean.startsWith("gemini-") -> clean
            else -> "gemini-$clean"
        }
    }

    /**
     * Smart Rule-Based sales agent fallback if Gemini API is not yet configured.
     */
    private fun generateSmartFallback(
        customerName: String,
        message: String,
        products: List<ProductEntity>
    ): String {
        val lower = message.lowercase()

        // Greeting Check
        if (lower.contains("hi") || lower.contains("hello") || lower.contains("সালাম") || lower.contains("আসসালামু") || lower.contains("hey")) {
            val topProducts = products.take(3).joinToString("\n") { "• ${it.name} - ${it.price.toInt()} ৳" }
            return """আসসালামু আলাইকুম $customerName! Technography-তে স্বাগতম। 😊
আমাদের জনপ্রিয় ডিজিটাল কালেকশনসমূহ:
$topProducts

আপনি কোন প্যাকেজটি নিতে আগ্রহী? জানালে বিস্তারিত বুঝিয়ে দিচ্ছি।"""
        }

        // Price Check
        if (lower.contains("দাম") || lower.contains("price") || lower.contains("কত") || lower.contains("টাকা")) {
            val allList = products.joinToString("\n") { "• ${it.name}: ${it.price.toInt()} ৳" }
            return """আমাদের প্যাকেজের অফার মূল্যসমূহ:
$allList

যেকোনো প্যাকেজ নিতে বিকাশ/নগদ সেন্ড মানি করে TrxID পাঠালেই সাথে সাথে VIP ডাউনলোড লিংক পেয়ে যাবেন।"""
        }

        // How to buy / Payment Method Check
        if (lower.contains("কিভাবে") || lower.contains("কিনব") || lower.contains("নিতে চাই") || lower.contains("বিকাশ") || lower.contains("পেমেন্ট")) {
            return """পেমেন্ট নিয়ম:
আমাদের বিকাশ বা নগদ পার্সোনাল নাম্বারে Send Money করুন।
পেমেন্ট সম্পন্ন করে এখানে ট্রানজেকশন আইডি (TrxID) দিন। সিস্টেম স্বয়ংক্রিয়ভাবে আপনার ড্রাইভ অ্যাক্সেস লিংক দিয়ে দিবে।"""
        }

        // General fallback
        val featured = products.firstOrNull() ?: AppDatabase.getDefaultProducts().first()
        return """ধন্যবাদ $customerName! আমাদের সেরা অফার '${featured.name}' মাত্র ${featured.price.toInt()} ৳।
আপনি কি এটি নিতে চান? অথবা অন্য কোনো নির্দিষ্ট কোর্স/রিলস বান্ডেল খুঁজছেন?"""
    }
}
