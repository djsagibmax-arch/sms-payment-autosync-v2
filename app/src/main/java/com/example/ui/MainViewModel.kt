package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.PreferencesManager
import com.example.data.SyncLogDao
import com.example.data.SyncLogEntity
import com.example.model.ConnectionMode
import com.example.model.PaymentData
import com.example.model.SyncConfig
import com.example.model.SyncStatus
import com.example.network.NetworkManager
import com.example.network.NetworkResult
import com.example.parser.SmsParser
import com.example.service.SyncForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardStats(
    val totalCount: Int = 0,
    val successCount: Int = 0,
    val failedCount: Int = 0,
    val totalAmount: Double = 0.0,
    val successRate: Int = 0
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefsManager = PreferencesManager.getInstance(application)
    private val db = AppDatabase.getDatabase(application)
    private val dao: SyncLogDao = db.syncLogDao()

    val config: StateFlow<SyncConfig> = prefsManager.config

    val allLogs: StateFlow<List<SyncLogEntity>> = dao.getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentLogs: StateFlow<List<SyncLogEntity>> = dao.getRecentLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<com.example.data.ProductEntity>> = db.productDao().getAllProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val botActivities = com.example.service.SocialNotificationListenerService.recentBotActivities

    private val _isNotificationAccessGranted = MutableStateFlow(false)
    val isNotificationAccessGranted: StateFlow<Boolean> = _isNotificationAccessGranted.asStateFlow()

    val stats: StateFlow<DashboardStats> = combine(
        dao.getTotalCount(),
        dao.getSuccessCount(),
        dao.getFailedCount(),
        dao.getTotalSyncedAmount()
    ) { total, success, failed, amount ->
        val rate = if (total > 0) ((success.toDouble() / total.toDouble()) * 100).toInt() else 100
        DashboardStats(
            totalCount = total,
            successCount = success,
            failedCount = failed,
            totalAmount = amount,
            successRate = rate
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())

    private val _isServiceRunning = MutableStateFlow(SyncForegroundService.isServiceRunning)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _testConnectionResult = MutableStateFlow<NetworkResult?>(null)
    val testConnectionResult: StateFlow<NetworkResult?> = _testConnectionResult.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _selectedLog = MutableStateFlow<SyncLogEntity?>(null)
    val selectedLog: StateFlow<SyncLogEntity?> = _selectedLog.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _simulationResult = MutableStateFlow<NetworkResult?>(null)
    val simulationResult: StateFlow<NetworkResult?> = _simulationResult.asStateFlow()

    private val _isSimulating = MutableStateFlow(false)
    val isSimulating: StateFlow<Boolean> = _isSimulating.asStateFlow()

    // WhatsApp UI Automation & Supabase Realtime State
    val whatsappAutomationState = com.example.service.WhatsAppAutomationManager.automationState
    val supabaseRealtimeStatus = com.example.network.SupabaseRealtimeManager.status
    val supabaseRealtimeLog = com.example.network.SupabaseRealtimeManager.lastEventLog
    val pendingQueueCount = com.example.network.SupabaseRealtimeManager.pendingCount

    fun refreshWhatsAppState(context: Context) {
        com.example.service.WhatsAppAutomationManager.refreshState(context)
    }

    fun openAccessibilitySettings(context: Context) {
        com.example.service.WhatsAppAutomationManager.openAccessibilitySettings(context)
    }

    fun restartRealtimeListener(context: Context) {
        com.example.network.SupabaseRealtimeManager.restart(context)
        _userMessage.value = "Restarting Supabase Realtime listener..."
    }

    fun triggerTestWhatsAppSend(
        context: Context,
        phone: String,
        message: String,
        method: String = "bKash",
        trxId: String = "TEST123456",
        amount: Double = 500.0
    ) {
        val success = com.example.service.WhatsAppAutomationManager.triggerSend(
            context = context,
            rawPhone = phone,
            message = message,
            rowId = "TEST_ROW_" + System.currentTimeMillis().toString().takeLast(4),
            method = method,
            trxId = trxId,
            amount = amount
        )
        if (success) {
            _userMessage.value = "Launching WhatsApp intent for test..."
        } else {
            _userMessage.value = "Test failed. Check phone number & WhatsApp installation."
        }
    }

    fun refreshNotificationAccessState(context: Context) {
        _isNotificationAccessGranted.value = com.example.service.SocialNotificationListenerService.isNotificationAccessGranted(context)
    }

    fun openNotificationAccessSettings(context: Context) {
        com.example.service.SocialNotificationListenerService.openNotificationAccessSettings(context)
    }

    fun saveProduct(product: com.example.data.ProductEntity) {
        viewModelScope.launch {
            db.productDao().insertProduct(product)
            _userMessage.value = "Product saved: ${product.name}"
        }
    }

    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            db.productDao().deleteProductById(productId)
            _userMessage.value = "Product removed"
        }
    }

    fun toggleProductActive(product: com.example.data.ProductEntity) {
        viewModelScope.launch {
            val updated = product.copy(isActive = !product.isActive)
            db.productDao().updateProduct(updated)
            _userMessage.value = if (updated.isActive) "Product activated" else "Product deactivated"
        }
    }

    fun pauseBotForSender(senderKey: String, minutes: Int = 15) {
        com.example.service.SocialNotificationListenerService.pauseAutoReplyForSender(senderKey, minutes)
        _userMessage.value = "Bot paused for $senderKey for $minutes mins"
    }

    fun resumeBotForSender(senderKey: String) {
        com.example.service.SocialNotificationListenerService.resumeAutoReplyForSender(senderKey)
        _userMessage.value = "Bot resumed for $senderKey"
    }

    fun testChatbotReply(context: Context, query: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val currentCfg = config.value
            val response = com.example.service.GeminiSalesAssistant.generateReply(
                context = context,
                senderName = "Test Customer",
                customerMessage = query,
                platformName = "Test Simulator",
                config = currentCfg
            )
            onResult(response)
        }
    }

    fun updateConfig(newConfig: SyncConfig) {
        prefsManager.saveConfig(newConfig)
        _userMessage.value = "Settings saved successfully"
    }

    fun toggleService(context: Context) {
        if (_isServiceRunning.value) {
            SyncForegroundService.stop(context)
            _isServiceRunning.value = false
            _userMessage.value = "Background service stopped"
        } else {
            SyncForegroundService.start(context)
            _isServiceRunning.value = true
            _userMessage.value = "Background service started"
        }
    }

    fun refreshServiceState() {
        _isServiceRunning.value = SyncForegroundService.isServiceRunning
    }

    fun testConnection(customConfig: SyncConfig? = null) {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _testConnectionResult.value = null
            val cfg = customConfig ?: config.value
            val result = NetworkManager.testConnection(cfg)
            _testConnectionResult.value = result
            _isTestingConnection.value = false
        }
    }

    fun clearTestResult() {
        _testConnectionResult.value = null
    }

    fun selectLog(log: SyncLogEntity?) {
        _selectedLog.value = log
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            dao.clearAllLogs()
            _userMessage.value = "All activity logs cleared"
        }
    }

    fun deleteLog(id: Long) {
        viewModelScope.launch {
            dao.deleteLogById(id)
        }
    }

    fun retrySync(context: Context, log: SyncLogEntity) {
        viewModelScope.launch {
            val paymentData = PaymentData(
                trxId = log.trxId,
                amount = log.amount,
                currency = "BDT",
                method = log.method,
                senderPhone = log.senderPhone,
                senderId = log.senderId,
                rawMessage = log.rawMessage,
                timestamp = log.timestamp,
                reference = log.reference,
                balance = log.balance
            )
            val result = NetworkManager.forwardPayment(context, config.value, paymentData)
            if (result.isSuccess) {
                _userMessage.value = "Retry successful! HTTP ${result.statusCode}"
            } else {
                _userMessage.value = "Retry failed: ${result.errorMessage ?: "HTTP " + result.statusCode}"
            }
        }
    }

    fun clearSimulationResult() {
        _simulationResult.value = null
    }

    /**
     * Simulate an incoming SMS (for emulator & sandbox testing).
     */
    fun simulateIncomingSms(context: Context, sender: String, body: String, customConfig: SyncConfig? = null) {
        viewModelScope.launch {
            _isSimulating.value = true
            _simulationResult.value = null
            val cfg = customConfig ?: config.value
            val paymentData = SmsParser.parseSms(sender, body)
            if (paymentData == null) {
                _isSimulating.value = false
                val failResult = NetworkResult(
                    isSuccess = false,
                    statusCode = 0,
                    responseBody = "Regex pattern did not match transaction data. Check format.",
                    latencyMs = 0,
                    errorMessage = "Regex match failed",
                    endpointUsed = "SMS Parser"
                )
                _simulationResult.value = failResult
                dao.insertLog(
                    SyncLogEntity(
                        timestamp = System.currentTimeMillis(),
                        senderId = sender,
                        rawMessage = body,
                        method = SmsParser.detectPaymentMethod(sender, body),
                        trxId = "FAILED_PARSE",
                        amount = 0.0,
                        senderPhone = null,
                        reference = null,
                        balance = null,
                        status = SyncStatus.PARSED_ONLY.name,
                        responseCode = 0,
                        responseBody = "No Transaction ID or Amount detected in SMS.",
                        endpointUrl = "",
                        errorMessage = "Regex match failed"
                    )
                )
                _userMessage.value = "Simulator: Failed to extract TrxID/Amount"
                return@launch
            }

            val result = NetworkManager.forwardPayment(context, cfg, paymentData)
            _simulationResult.value = result
            _isSimulating.value = false
            if (result.isSuccess) {
                _userMessage.value = "✓ Synced: ${paymentData.method} ৳${paymentData.amount}"
            } else {
                _userMessage.value = "⚠ Sync issue: ${result.errorMessage ?: "HTTP " + result.statusCode}"
            }
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}
