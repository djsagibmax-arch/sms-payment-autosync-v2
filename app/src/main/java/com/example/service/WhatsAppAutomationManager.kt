package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.PreferencesManager
import com.example.data.SyncLogEntity
import com.example.model.SyncConfig
import com.example.model.SyncStatus
import com.example.util.PhoneNumberSanitizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class WhatsAppAutomationState(
    val isAccessibilityEnabled: Boolean = false,
    val isWhatsAppInstalled: Boolean = false,
    val isBusinessInstalled: Boolean = false,
    val isAutomationPending: Boolean = false,
    val lastTargetPhone: String? = null,
    val lastMessageText: String? = null,
    val lastSendTimestamp: Long = 0L,
    val lastStatusMessage: String = "Idle"
)

object WhatsAppAutomationManager {
    private const val TAG = "WhatsAppAutomation"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    // Current pending automation request
    @Volatile
    var isPending: Boolean = false
        private set

    @Volatile
    var pendingRowId: String? = null
        private set

    @Volatile
    var pendingPhone: String? = null
        private set

    @Volatile
    var dispatchTimeMillis: Long = 0L
        private set

    fun cancelPending() {
        isPending = false
        pendingRowId = null
        pendingPhone = null
        _automationState.value = _automationState.value.copy(isAutomationPending = false)
    }

    fun markClickInitiated() {
        // Keep isPending = true until confirmed click or timeout to protect against race conditions
    }

    private val _automationState = MutableStateFlow(WhatsAppAutomationState())
    val automationState: StateFlow<WhatsAppAutomationState> = _automationState.asStateFlow()

    /**
     * Checks if WhatsApp (Standard or Business) is installed on the device.
     */
    fun isWhatsAppInstalled(context: Context): Boolean {
        val pm = context.packageManager
        val standard = try {
            pm.getPackageInfo("com.whatsapp", PackageManager.GET_ACTIVITIES)
            true
        } catch (e: Exception) {
            false
        }
        val business = try {
            pm.getPackageInfo("com.whatsapp.w4b", PackageManager.GET_ACTIVITIES)
            true
        } catch (e: Exception) {
            false
        }
        return standard || business
    }

    fun isStandardWhatsAppInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.whatsapp", PackageManager.GET_ACTIVITIES)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isBusinessWhatsAppInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.whatsapp.w4b", PackageManager.GET_ACTIVITIES)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks whether the Auto WhatsApp Sender AccessibilityService is active.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (WhatsAppAccessibilityService.isConnected) return true

        // 1. Official Android AccessibilityManager API (reliable across all Android versions)
        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            if (am != null) {
                val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                for (service in enabledServices) {
                    val serviceInfo = service.resolveInfo?.serviceInfo
                    if (serviceInfo != null) {
                        val pkg = serviceInfo.packageName
                        val name = serviceInfo.name
                        if (pkg == context.packageName &&
                            (name == WhatsAppAccessibilityService::class.java.name || name.endsWith("WhatsAppAccessibilityService"))
                        ) {
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "AccessibilityManager check fallback: ${e.message}")
        }

        // 2. Settings.Secure check (fallback for older or custom Android builds)
        try {
            val expectedComponentName = ComponentName(context, WhatsAppAccessibilityService::class.java)
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (!enabledServicesSetting.isNullOrBlank()) {
                val colonSplitter = TextUtils.SimpleStringSplitter(':')
                colonSplitter.setString(enabledServicesSetting)
                while (colonSplitter.hasNext()) {
                    val componentNameString = colonSplitter.next()
                    if (componentNameString.contains("WhatsAppAccessibilityService", ignoreCase = true)) {
                        return true
                    }
                    val enabledComponent = ComponentName.unflattenFromString(componentNameString)
                    if (enabledComponent != null &&
                        (enabledComponent == expectedComponentName || enabledComponent.className.endsWith("WhatsAppAccessibilityService"))
                    ) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Settings.Secure check fallback: ${e.message}")
        }

        return false
    }

    /**
     * Opens Android Accessibility Settings so the user can easily toggle the service.
     */
    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open accessibility settings: ${e.message}")
        }
    }

    /**
     * Refreshes the cached state for the UI.
     */
    fun refreshState(context: Context) {
        val isAcc = isAccessibilityServiceEnabled(context)
        val isStd = isStandardWhatsAppInstalled(context)
        val isBiz = isBusinessWhatsAppInstalled(context)
        _automationState.value = _automationState.value.copy(
            isAccessibilityEnabled = isAcc,
            isWhatsAppInstalled = isStd,
            isBusinessInstalled = isBiz
        )
    }

    /**
     * Triggers an automated WhatsApp message send.
     * Sanitizes the phone number, forms the official WhatsApp intent, and sets pending automation state.
     */
    fun triggerSend(
        context: Context,
        rawPhone: String?,
        message: String,
        rowId: String? = null,
        method: String = "bKash",
        trxId: String = "",
        amount: Double = 0.0
    ): Boolean {
        val sanitizedPhone = PhoneNumberSanitizer.sanitize(rawPhone)
        if (sanitizedPhone == null) {
            Log.e(TAG, "Cannot send WhatsApp message: Invalid phone number: $rawPhone")
            updateStatus("Failed: Invalid phone number ($rawPhone)")
            return false
        }

        if (!isWhatsAppInstalled(context)) {
            Log.e(TAG, "WhatsApp is not installed on this device.")
            updateStatus("Failed: WhatsApp is not installed")
            return false
        }

        try {
            val encodedMessage = URLEncoder.encode(message, "UTF-8")
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$sanitizedPhone&text=$encodedMessage")

            val config = PreferencesManager.getInstance(context).loadConfig()
            val preferred = config.preferredWhatsAppPackage.trim()

            val targetPackage = when {
                preferred == "com.whatsapp.w4b" && isBusinessWhatsAppInstalled(context) -> "com.whatsapp.w4b"
                preferred == "com.whatsapp" && isStandardWhatsAppInstalled(context) -> "com.whatsapp"
                isStandardWhatsAppInstalled(context) -> "com.whatsapp"
                isBusinessWhatsAppInstalled(context) -> "com.whatsapp.w4b"
                else -> null
            }

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (targetPackage != null) {
                    setPackage(targetPackage)
                }
            }

            // Register pending state before opening WhatsApp
            isPending = true
            pendingRowId = rowId
            pendingPhone = sanitizedPhone
            dispatchTimeMillis = System.currentTimeMillis()

            _automationState.value = _automationState.value.copy(
                isAutomationPending = true,
                lastTargetPhone = sanitizedPhone,
                lastMessageText = message,
                lastStatusMessage = "WhatsApp opening for +$sanitizedPhone..."
            )

            context.startActivity(intent)
            Log.i(TAG, "WhatsApp intent launched for phone: $sanitizedPhone, rowId: $rowId")

            // Timeout safety: expire after 25 seconds if send button wasn't clicked
            scope.launch {
                delay(25000L)
                if (isPending && pendingRowId == rowId) {
                    Log.w(TAG, "Automation timeout reached for rowId: $rowId. Resetting pending state.")
                    isPending = false
                    _automationState.value = _automationState.value.copy(
                        isAutomationPending = false,
                        lastStatusMessage = "Timeout waiting for WhatsApp Send button"
                    )
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch WhatsApp Intent: ${e.message}", e)
            isPending = false
            updateStatus("Error: ${e.localizedMessage}")
            return false
        }
    }

    /**
     * Called by WhatsAppAccessibilityService when the Send button was successfully clicked!
     */
    fun onSendSuccess(context: Context, service: AccessibilityService) {
        val rowId = pendingRowId
        val phone = pendingPhone
        val now = System.currentTimeMillis()

        Log.i(TAG, "AccessibilityService confirmed Send clicked for rowId: $rowId, phone: $phone")
        isPending = false
        pendingRowId = null
        pendingPhone = null

        _automationState.value = _automationState.value.copy(
            isAutomationPending = false,
            lastSendTimestamp = now,
            lastStatusMessage = "Sent successfully to +$phone"
        )

        val config = PreferencesManager.getInstance(context).loadConfig()

        // Handle auto-back if enabled
        if (config.whatsappAutoBack) {
            scope.launch {
                // Wait for WhatsApp to complete message dispatch animation
                delay(1200L)
                withContext(Dispatchers.Main) {
                    try {
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                        Log.d(TAG, "Performed GLOBAL_ACTION_BACK to return from WhatsApp")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error performing back action: ${e.message}")
                    }
                }
            }
        }

        // Update Supabase whatsapp_sent = true and log to Room DB
        scope.launch {
            if (!rowId.isNullOrBlank()) {
                PreferencesManager.getInstance(context).markRowProcessed(rowId)
                markSupabaseRowSent(context, config, rowId)
                com.example.network.SupabaseRealtimeManager.onRowSentSuccessfully(rowId)
            }

            // Save log to local Room DB
            try {
                val db = AppDatabase.getDatabase(context)
                db.syncLogDao().insertLog(
                    SyncLogEntity(
                        timestamp = now,
                        senderId = "WhatsApp_Auto",
                        rawMessage = "Auto-sent WhatsApp message to +$phone",
                        method = "WhatsApp",
                        trxId = "WA_$now",
                        amount = 0.0,
                        senderPhone = phone,
                        reference = rowId,
                        balance = null,
                        status = SyncStatus.SUCCESS.name,
                        responseCode = 200,
                        responseBody = "Sent via Accessibility Automation",
                        endpointUrl = "WhatsApp (${phone})",
                        errorMessage = null
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save WhatsApp send log: ${e.message}")
            }
        }
    }

    /**
     * Updates the row in Supabase so whatsapp_sent = true
     */
    private suspend fun markSupabaseRowSent(context: Context, config: SyncConfig, rowId: String) = withContext(Dispatchers.IO) {
        var baseUrl = config.supabaseUrl.trim().removeSuffix("/")
        if (baseUrl.isBlank() || config.supabaseApiKey.isBlank()) {
            Log.w(TAG, "Supabase URL or Key missing, cannot update whatsapp_sent for row: $rowId")
            return@withContext
        }
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            baseUrl = "https://$baseUrl"
        }

        val table = config.supabasePaymentsTable.trim().ifEmpty { "payments" }
        val sentCol = config.whatsappSentColumn.trim().ifEmpty { "whatsapp_sent" }
        val targetUrl = if (rowId.toLongOrNull() != null) {
            "$baseUrl/rest/v1/$table?id=eq.$rowId"
        } else {
            "$baseUrl/rest/v1/$table?trx_id=eq.$rowId"
        }

        try {
            val json = JSONObject().apply {
                put(sentCol, true)
                put("whatsapp_sent", true)
            }
            val body = json.toString().toRequestBody(JSON_MEDIA)

            val request = Request.Builder()
                .url(targetUrl)
                .patch(body)
                .addHeader("apikey", config.supabaseApiKey.trim())
                .addHeader("Authorization", "Bearer ${config.supabaseApiKey.trim()}")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                Log.i(TAG, "Successfully updated $sentCol=true in Supabase for row: $rowId")
            } else {
                Log.e(TAG, "Failed to update Supabase row $rowId: HTTP ${response.code} ${response.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Supabase row $rowId: ${e.message}", e)
        }
    }

    private fun updateStatus(msg: String) {
        _automationState.value = _automationState.value.copy(lastStatusMessage = msg)
    }
}
