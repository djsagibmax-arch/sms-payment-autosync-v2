package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.AppDatabase
import com.example.data.PreferencesManager
import com.example.data.SyncLogEntity
import com.example.model.SyncStatus
import com.example.network.NetworkManager
import com.example.parser.SmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
        private const val NOTIFICATION_CHANNEL_ID = "sms_payment_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "Payment SMS Sync"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val fullBodyBuilder = StringBuilder()
        var sender = ""
        var timestamp = System.currentTimeMillis()

        for (sms in messages) {
            sender = sms.displayOriginatingAddress ?: sms.originatingAddress ?: "Unknown"
            fullBodyBuilder.append(sms.displayMessageBody ?: sms.messageBody ?: "")
            timestamp = sms.timestampMillis
        }

        val fullMessage = fullBodyBuilder.toString()
        Log.d(TAG, "Incoming SMS from [$sender]: $fullMessage")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                processIncomingSms(context, sender, fullMessage, timestamp)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling incoming SMS: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun processIncomingSms(
        context: Context,
        sender: String,
        body: String,
        timestamp: Long
    ) {
        val prefs = PreferencesManager.getInstance(context)
        val config = prefs.loadConfig()

        // 1. Check if sender is in configured wallets or custom keywords
        val isAllowed = SmsParser.isSenderAllowed(
            senderId = sender,
            body = body,
            bkashEnabled = config.bkashEnabled,
            nagadEnabled = config.nagadEnabled,
            rocketEnabled = config.rocketEnabled,
            upayEnabled = config.upayEnabled,
            cellfinEnabled = config.cellfinEnabled,
            customKeywords = config.customKeywords
        )

        if (!isAllowed) {
            Log.d(TAG, "SMS ignored: sender [$sender] is not enabled in settings.")
            return
        }

        // 2. Parse SMS
        val paymentData = SmsParser.parseSms(sender, body, timestamp)
        if (paymentData == null) {
            Log.w(TAG, "Could not extract payment details from SMS: $body")
            // Record unparsed log
            val db = AppDatabase.getDatabase(context)
            db.syncLogDao().insertLog(
                SyncLogEntity(
                    timestamp = timestamp,
                    senderId = sender,
                    rawMessage = body,
                    method = SmsParser.detectPaymentMethod(sender, body),
                    trxId = "UNPARSED",
                    amount = 0.0,
                    senderPhone = null,
                    reference = null,
                    balance = null,
                    status = SyncStatus.PARSED_ONLY.name,
                    responseCode = 0,
                    responseBody = "No TrxID/Amount extracted",
                    endpointUrl = "",
                    errorMessage = "Regex pattern did not match transaction data"
                )
            )
            return
        }

        // 3. Filter check if onlyReceivedAndCashIn is enabled
        if (config.onlyReceivedAndCashIn) {
            val type = paymentData.transactionType.lowercase()
            val isIncoming = type.contains("received") || type.contains("cash in") || type.contains("payment")
            if (!isIncoming) {
                Log.d(TAG, "Ignored outgoing or non-incoming transaction: ${paymentData.transactionType}")
                val db = AppDatabase.getDatabase(context)
                db.syncLogDao().insertLog(
                    SyncLogEntity(
                        timestamp = timestamp,
                        senderId = sender,
                        rawMessage = body,
                        method = paymentData.method,
                        trxId = paymentData.trxId,
                        amount = paymentData.amount,
                        senderPhone = paymentData.senderPhone,
                        reference = paymentData.reference,
                        balance = paymentData.balance,
                        status = SyncStatus.FILTERED_IGNORED.name,
                        responseCode = 0,
                        responseBody = "Filtered: Not an incoming payment/cash-in",
                        endpointUrl = "",
                        errorMessage = null
                    )
                )
                return
            }
        }

        // 4. Forward to Server / Database
        Log.i(TAG, "Forwarding payment: ${paymentData.method} | TrxID: ${paymentData.trxId} | Amount: ${paymentData.amount}")
        val result = NetworkManager.forwardPayment(context, config, paymentData)

        // 5. Post notification
        if (config.notifyOnSuccess && result.isSuccess) {
            showNotification(
                context,
                title = "✓ ${paymentData.method} Synced: ৳${String.format(java.util.Locale.US, "%,.2f", paymentData.amount)}",
                message = "TrxID: ${paymentData.trxId} • HTTP ${result.statusCode} OK"
            )
        } else if (config.notifyOnFailure && !result.isSuccess) {
            showNotification(
                context,
                title = "⚠ Sync Failed: ${paymentData.method} ৳${paymentData.amount}",
                message = "TrxID: ${paymentData.trxId} • ${result.errorMessage ?: "HTTP " + result.statusCode}"
            )
        }
    }

    private fun showNotification(context: Context, title: String, message: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for automatic SMS payment sync"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            notificationManager.notify((System.currentTimeMillis() % 100000).toInt(), notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error posting notification: ${e.message}")
        }
    }
}
