package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.ConnectionMode
import com.example.model.SyncConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sms_payment_autosync_prefs", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<SyncConfig> = _config.asStateFlow()

    fun loadConfig(): SyncConfig {
        val modeStr = prefs.getString("connection_mode", ConnectionMode.SUPABASE.name) ?: ConnectionMode.SUPABASE.name
        val mode = try {
            ConnectionMode.valueOf(modeStr)
        } catch (e: Exception) {
            ConnectionMode.SUPABASE
        }

        return SyncConfig(
            connectionMode = mode,
            forwardToCustomApi = prefs.getBoolean("forward_to_custom_api", true),
            forwardToSupabase = prefs.getBoolean("forward_to_supabase", true),
            forwardToWebhook = prefs.getBoolean("forward_to_webhook", false),
            
            supabaseUrl = prefs.getString("supabase_url", "https://safymsagxrjymhfdzkph.supabase.co") ?: "https://safymsagxrjymhfdzkph.supabase.co",
            supabaseApiKey = prefs.getString("supabase_api_key", "sb_publishable_qthdZZg-tjDRc_loLbVUYg_b77rK-Bv") ?: "sb_publishable_qthdZZg-tjDRc_loLbVUYg_b77rK-Bv",
            supabaseTable = prefs.getString("supabase_table", "payments") ?: "payments",
            
            customEndpointUrl = prefs.getString("custom_endpoint_url", "https://my-sales-bot-5d70.onrender.com/webhook/sms") ?: "https://my-sales-bot-5d70.onrender.com/webhook/sms",
            customAuthHeaderName = prefs.getString("custom_auth_header_name", "Authorization") ?: "Authorization",
            customAuthToken = prefs.getString("custom_auth_token", "") ?: "",
            customHttpMethod = prefs.getString("custom_http_method", "POST") ?: "POST",
            
            webhookUrl = prefs.getString("webhook_url", "") ?: "",
            webhookSecret = prefs.getString("webhook_secret", "") ?: "",
            
            bkashEnabled = prefs.getBoolean("bkash_enabled", true),
            nagadEnabled = prefs.getBoolean("nagad_enabled", true),
            rocketEnabled = prefs.getBoolean("rocket_enabled", true),
            upayEnabled = prefs.getBoolean("upay_enabled", true),
            cellfinEnabled = prefs.getBoolean("cellfin_enabled", true),
            customKeywords = prefs.getString("custom_keywords", "16247, 16216, NAGAD, bKash, Upay, Cellfin") ?: "16247, 16216, NAGAD, bKash, Upay, Cellfin",
            onlyReceivedAndCashIn = prefs.getBoolean("only_received_and_cash_in", true),
            
            autoStartService = prefs.getBoolean("auto_start_service", true),
            notifyOnSuccess = prefs.getBoolean("notify_on_success", true),
            notifyOnFailure = prefs.getBoolean("notify_on_failure", true),

            whatsappAutomationEnabled = prefs.getBoolean("whatsapp_automation_enabled", true),
            supabasePaymentsTable = prefs.getString("supabase_payments_table", "payments") ?: "payments",
            whatsappNumberColumn = prefs.getString("whatsapp_number_column", "whatsapp_number") ?: "whatsapp_number",
            whatsappSentColumn = prefs.getString("whatsapp_sent_column", "whatsapp_sent") ?: "whatsapp_sent",
            whatsappMessageTemplate = prefs.getString("whatsapp_msg_template", com.example.model.DEFAULT_VIP_WHATSAPP_TEMPLATE) ?: com.example.model.DEFAULT_VIP_WHATSAPP_TEMPLATE,
            whatsappSendDelayMs = prefs.getLong("whatsapp_send_delay_ms", 1200L),
            whatsappAutoBack = prefs.getBoolean("whatsapp_auto_back", true)
        )
    }

    fun isRowProcessed(rowId: String): Boolean {
        if (rowId.isBlank()) return false
        val set = prefs.getStringSet("processed_row_ids", emptySet()) ?: emptySet()
        return set.contains(rowId)
    }

    fun markRowProcessed(rowId: String) {
        if (rowId.isBlank()) return
        val set = prefs.getStringSet("processed_row_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(rowId)
        if (set.size > 500) {
            val trimmed = set.toList().takeLast(300).toSet()
            prefs.edit().putStringSet("processed_row_ids", trimmed).apply()
        } else {
            prefs.edit().putStringSet("processed_row_ids", set).apply()
        }
    }

    fun saveConfig(config: SyncConfig) {
        prefs.edit().apply {
            putString("connection_mode", config.connectionMode.name)
            putBoolean("forward_to_custom_api", config.forwardToCustomApi)
            putBoolean("forward_to_supabase", config.forwardToSupabase)
            putBoolean("forward_to_webhook", config.forwardToWebhook)
            
            putString("supabase_url", config.supabaseUrl.trim())
            putString("supabase_api_key", config.supabaseApiKey.trim())
            putString("supabase_table", config.supabaseTable.trim())
            
            putString("custom_endpoint_url", config.customEndpointUrl.trim())
            putString("custom_auth_header_name", config.customAuthHeaderName.trim())
            putString("custom_auth_token", config.customAuthToken.trim())
            putString("custom_http_method", config.customHttpMethod.trim())
            
            putString("webhook_url", config.webhookUrl.trim())
            putString("webhook_secret", config.webhookSecret.trim())
            
            putBoolean("bkash_enabled", config.bkashEnabled)
            putBoolean("nagad_enabled", config.nagadEnabled)
            putBoolean("rocket_enabled", config.rocketEnabled)
            putBoolean("upay_enabled", config.upayEnabled)
            putBoolean("cellfinEnabled", config.cellfinEnabled)
            putString("custom_keywords", config.customKeywords.trim())
            putBoolean("only_received_and_cash_in", config.onlyReceivedAndCashIn)
            
            putBoolean("auto_start_service", config.autoStartService)
            putBoolean("notify_on_success", config.notifyOnSuccess)
            putBoolean("notify_on_failure", config.notifyOnFailure)

            putBoolean("whatsapp_automation_enabled", config.whatsappAutomationEnabled)
            putString("supabase_payments_table", config.supabasePaymentsTable.trim())
            putString("whatsapp_number_column", config.whatsappNumberColumn.trim())
            putString("whatsapp_sent_column", config.whatsappSentColumn.trim())
            putString("whatsapp_msg_template", config.whatsappMessageTemplate)
            putLong("whatsapp_send_delay_ms", config.whatsappSendDelayMs)
            putBoolean("whatsapp_auto_back", config.whatsappAutoBack)
            apply()
        }
        _config.value = config
    }

    companion object {
        @Volatile
        private var INSTANCE: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PreferencesManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
