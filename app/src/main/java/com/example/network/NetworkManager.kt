package com.example.network

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.SyncLogEntity
import com.example.model.ConnectionMode
import com.example.model.PaymentData
import com.example.model.SyncConfig
import com.example.model.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class NetworkResult(
    val isSuccess: Boolean,
    val statusCode: Int,
    val responseBody: String,
    val latencyMs: Long,
    val errorMessage: String? = null,
    val endpointUsed: String
)

object NetworkManager {
    private const val TAG = "NetworkManager"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Test connection for the provided configuration with a simulated dummy payload.
     */
    suspend fun testConnection(config: SyncConfig, targetMode: ConnectionMode? = null): NetworkResult = withContext(Dispatchers.IO) {
        val dummyPayment = PaymentData(
            trxId = "TEST_" + System.currentTimeMillis().toString().takeLast(6),
            amount = 500.0,
            currency = "BDT",
            method = "bKash_Test",
            senderPhone = "01700000000",
            senderId = "bKash",
            rawMessage = "TEST: You have received Tk 500.00 from 01700000000. TrxID TEST12345 at Test Mode",
            reference = "Test_Connection",
            balance = 10000.0,
            transactionType = "Test Payment"
        )
        val modeToTest = targetMode ?: config.connectionMode
        return@withContext sendSinglePayment(config, modeToTest, dummyPayment, isTest = true)
    }

    /**
     * Synchronize and forward payment data based on all enabled destinations.
     * Records the transaction log to Room Database.
     */
    suspend fun forwardPayment(
        context: Context,
        config: SyncConfig,
        paymentData: PaymentData
    ): NetworkResult = withContext(Dispatchers.IO) {
        // Collect all enabled modes
        val enabledModes = mutableListOf<ConnectionMode>()
        if (config.forwardToCustomApi) enabledModes.add(ConnectionMode.CUSTOM_API)
        if (config.forwardToSupabase) enabledModes.add(ConnectionMode.SUPABASE)
        if (config.forwardToWebhook) enabledModes.add(ConnectionMode.DIRECT_WEBHOOK)

        // Fallback: If none checked, use selected connectionMode
        if (enabledModes.isEmpty()) {
            enabledModes.add(config.connectionMode)
        }

        val results = mutableListOf<NetworkResult>()
        for (mode in enabledModes) {
            val res = sendSinglePayment(config, mode, paymentData, isTest = false)
            results.add(res)
        }

        // Overall status: success if at least one succeeded, or primary result
        val allSuccessful = results.all { it.isSuccess }
        val anySuccessful = results.any { it.isSuccess }
        val combinedStatusCode = if (results.size == 1) results.first().statusCode else if (allSuccessful) 200 else if (anySuccessful) 207 else results.first().statusCode
        val combinedResponseBody = results.joinToString(" | ") { "${it.endpointUsed}: [HTTP ${it.statusCode}]" }
        val combinedEndpoints = results.joinToString(", ") { it.endpointUsed }
        val combinedErrors = results.mapNotNull { it.errorMessage }.joinToString(" | ").ifEmpty { null }
        val totalLatency = results.sumOf { it.latencyMs }

        val summaryResult = NetworkResult(
            isSuccess = anySuccessful,
            statusCode = combinedStatusCode,
            responseBody = combinedResponseBody,
            latencyMs = totalLatency,
            errorMessage = combinedErrors,
            endpointUsed = combinedEndpoints
        )

        // Save log to Room DB
        try {
            val db = AppDatabase.getDatabase(context)
            val log = SyncLogEntity(
                timestamp = System.currentTimeMillis(),
                senderId = paymentData.senderId,
                rawMessage = paymentData.rawMessage,
                method = paymentData.method,
                trxId = paymentData.trxId,
                amount = paymentData.amount,
                senderPhone = paymentData.senderPhone,
                reference = paymentData.reference,
                balance = paymentData.balance,
                status = if (summaryResult.isSuccess) SyncStatus.SUCCESS.name else SyncStatus.FAILED.name,
                responseCode = summaryResult.statusCode,
                responseBody = summaryResult.responseBody,
                endpointUrl = summaryResult.endpointUsed,
                errorMessage = summaryResult.errorMessage
            )
            db.syncLogDao().insertLog(log)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save sync log to database: ${e.message}", e)
        }

        return@withContext summaryResult
    }

    private fun sendSinglePayment(
        config: SyncConfig,
        mode: ConnectionMode,
        paymentData: PaymentData,
        isTest: Boolean
    ): NetworkResult {
        val startTime = System.currentTimeMillis()
        var targetUrl = ""
        val requestBuilder = Request.Builder()

        try {
            val jsonObject = JSONObject().apply {
                put("trx_id", paymentData.trxId)
                put("amount", paymentData.amount)
                put("currency", paymentData.currency)
                put("method", paymentData.method)
                put("sender_phone", paymentData.senderPhone ?: "")
                put("sender_id", paymentData.senderId)
                put("transaction_type", paymentData.transactionType)
                put("reference", paymentData.reference ?: "")
                put("balance", paymentData.balance ?: 0.0)
                put("raw_message", paymentData.rawMessage)
                put("timestamp", paymentData.timestamp)
                put("is_test", isTest)
            }

            when (mode) {
                ConnectionMode.SUPABASE -> {
                    var baseUrl = config.supabaseUrl.trim().removeSuffix("/")
                    if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                        baseUrl = "https://$baseUrl"
                    }
                    val table = config.supabaseTable.trim().ifEmpty { "sms_payments" }
                    targetUrl = "$baseUrl/rest/v1/$table"

                    requestBuilder.url(targetUrl)
                    requestBuilder.addHeader("apikey", config.supabaseApiKey.trim())
                    requestBuilder.addHeader("Authorization", "Bearer ${config.supabaseApiKey.trim()}")
                    requestBuilder.addHeader("Prefer", "return=representation")
                    requestBuilder.addHeader("Content-Type", "application/json")
                    
                    val body = jsonObject.toString().toRequestBody(JSON_MEDIA_TYPE)
                    requestBuilder.post(body)
                }

                ConnectionMode.CUSTOM_API -> {
                    var url = config.customEndpointUrl.trim()
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "https://$url"
                    }
                    targetUrl = url
                    requestBuilder.url(targetUrl)

                    if (config.customAuthHeaderName.isNotBlank() && config.customAuthToken.isNotBlank()) {
                        requestBuilder.addHeader(config.customAuthHeaderName.trim(), config.customAuthToken.trim())
                    }
                    requestBuilder.addHeader("Content-Type", "application/json")
                    requestBuilder.addHeader("User-Agent", "SMS-Payment-AutoSync/1.0")

                    val body = jsonObject.toString().toRequestBody(JSON_MEDIA_TYPE)
                    if (config.customHttpMethod.equals("PUT", ignoreCase = true)) {
                        requestBuilder.put(body)
                    } else {
                        requestBuilder.post(body)
                    }
                }

                ConnectionMode.DIRECT_WEBHOOK -> {
                    var url = config.webhookUrl.trim().ifEmpty { config.customEndpointUrl.trim() }
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "https://$url"
                    }
                    targetUrl = url
                    requestBuilder.url(targetUrl)

                    if (config.webhookSecret.isNotBlank()) {
                        requestBuilder.addHeader("X-Webhook-Secret", config.webhookSecret.trim())
                    }
                    requestBuilder.addHeader("Content-Type", "application/json")
                    requestBuilder.addHeader("User-Agent", "SMS-Payment-AutoSync-Webhook/1.0")

                    val body = jsonObject.toString().toRequestBody(JSON_MEDIA_TYPE)
                    requestBuilder.post(body)
                }
            }

            val request = requestBuilder.build()
            val response: Response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val responseCode = response.code
            val responseBody = response.body?.string() ?: ""

            val isSuccess = response.isSuccessful // 200..299

            return NetworkResult(
                isSuccess = isSuccess,
                statusCode = responseCode,
                responseBody = if (responseBody.length > 500) responseBody.take(500) + "..." else responseBody,
                latencyMs = latency,
                errorMessage = if (!isSuccess) "HTTP $responseCode: ${response.message}" else null,
                endpointUsed = targetUrl
            )
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            Log.e(TAG, "HTTP execution failed for $targetUrl: ${e.message}", e)
            return NetworkResult(
                isSuccess = false,
                statusCode = -1,
                responseBody = "",
                latencyMs = latency,
                errorMessage = e.localizedMessage ?: e.javaClass.simpleName,
                endpointUsed = targetUrl
            )
        }
    }
}
