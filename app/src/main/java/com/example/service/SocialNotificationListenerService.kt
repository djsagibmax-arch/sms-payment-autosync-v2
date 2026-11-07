package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.PreferencesManager
import com.example.data.SyncLogEntity
import com.example.model.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

data class ChatbotActivityItem(
    val id: String = System.currentTimeMillis().toString(),
    val senderName: String,
    val platform: String, // WhatsApp, Messenger
    val incomingMessage: String,
    val replyMessage: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isLiveChatPaused: Boolean = false
)

class SocialNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "SocialNotificationBot"

        const val PKG_WHATSAPP = "com.whatsapp"
        const val PKG_WHATSAPP_BIZ = "com.whatsapp.w4b"
        const val PKG_MESSENGER = "com.facebook.orca"
        const val PKG_MESSENGER_LITE = "com.facebook.mlite"

        // Track paused conversations for Live Chat Handover (SenderKey -> ExpireTime)
        private val pausedSenders = ConcurrentHashMap<String, Long>()

        // Recent messages deduplication (Key -> Timestamp)
        private val recentReplies = ConcurrentHashMap<String, Long>()

        private val _recentBotActivities = MutableStateFlow<List<ChatbotActivityItem>>(emptyList())
        val recentBotActivities: StateFlow<List<ChatbotActivityItem>> = _recentBotActivities.asStateFlow()

        fun clearBotActivities() {
            _recentBotActivities.value = emptyList()
        }

        /**
         * Pause bot auto-reply for a specific sender for the given duration in minutes.
         * When merchant is live-chatting, this prevents bot interference.
         */
        fun pauseAutoReplyForSender(senderKey: String, durationMinutes: Int = 15) {
            val expireTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
            pausedSenders[senderKey.lowercase().trim()] = expireTime
            Log.d(TAG, "Paused auto-reply for '$senderKey' until $expireTime")
        }

        fun resumeAutoReplyForSender(senderKey: String) {
            pausedSenders.remove(senderKey.lowercase().trim())
        }

        fun isSenderPaused(senderKey: String): Boolean {
            val expire = pausedSenders[senderKey.lowercase().trim()] ?: return false
            if (System.currentTimeMillis() > expire) {
                pausedSenders.remove(senderKey.lowercase().trim())
                return false
            }
            return true
        }

        /**
         * Checks whether Notification Access is granted by the user.
         */
        fun isNotificationAccessGranted(context: Context): Boolean {
            val expectedComponentName = ComponentName(context, SocialNotificationListenerService::class.java)
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val names = flat.split(":")
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null && cn == expectedComponentName) {
                    return true
                }
            }
            return false
        }

        /**
         * Opens the Android Notification Listener Settings screen.
         */
        fun openNotificationAccessSettings(context: Context) {
            try {
                val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open notification settings: ${e.message}")
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        val isWhatsApp = pkg == PKG_WHATSAPP || pkg == PKG_WHATSAPP_BIZ
        val isMessenger = pkg == PKG_MESSENGER || pkg == PKG_MESSENGER_LITE

        if (!isWhatsApp && !isMessenger) return

        val config = PreferencesManager.getInstance(applicationContext).loadConfig()
        if (!config.chatbotEnabled) return
        if (isWhatsApp && !config.chatbotWhatsAppEnabled) return
        if (isMessenger && !config.chatbotMessengerEnabled) return

        // 1. Check Global Live Chat Handover switch
        if (config.liveChatHandoverActive) {
            Log.d(TAG, "Live Chat Handover is ACTIVE globally. Bot is paused.")
            return
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // Extract title (sender name) and text (message)
        val senderTitle = extras.getString(Notification.EXTRA_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: return

        val messageText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getString(Notification.EXTRA_TEXT)
            ?: return

        if (senderTitle.isBlank() || messageText.isBlank()) return

        // Filter out system or summary notifications (e.g. "Checking for new messages", "X new messages")
        if (messageText.contains("new messages", ignoreCase = true) ||
            messageText.contains("WhatsApp Web", ignoreCase = true) ||
            messageText.contains("messages from", ignoreCase = true) ||
            messageText.contains("নতু মেসেজ", ignoreCase = true)
        ) {
            return
        }

        val platform = if (isWhatsApp) "WhatsApp" else "Messenger"
        val senderKey = "${platform}_$senderTitle".lowercase().trim()

        // 2. Check per-sender Live Chat Handover
        if (isSenderPaused(senderKey) || isSenderPaused(senderTitle)) {
            Log.d(TAG, "Bot is paused for sender '$senderTitle' due to Live Chat Handover.")
            return
        }

        // 3. Deduplication check (ignore duplicate notifications for same text within 12 seconds)
        val dedupeKey = "$senderKey:$messageText"
        val lastTime = recentReplies[dedupeKey] ?: 0L
        if (System.currentTimeMillis() - lastTime < 12000L) {
            return
        }
        recentReplies[dedupeKey] = System.currentTimeMillis()

        // 4. Find Quick Reply Action with RemoteInput
        val quickReplyAction = findQuickReplyAction(notification)
        if (quickReplyAction == null) {
            Log.d(TAG, "No RemoteInput Quick Reply action found in notification for $senderTitle")
            return
        }

        // 5. Generate Response via Gemini AI Sales Assistant
        serviceScope.launch {
            try {
                val reply = GeminiSalesAssistant.generateReply(
                    context = applicationContext,
                    senderName = senderTitle,
                    customerMessage = messageText,
                    platformName = platform
                )

                if (reply.isNotBlank()) {
                    val sent = sendQuickReply(quickReplyAction, reply)
                    if (sent) {
                        Log.i(TAG, "✓ Auto-replied to $senderTitle on $platform: \"$reply\"")

                        // Add to UI activity feed
                        val activity = ChatbotActivityItem(
                            senderName = senderTitle,
                            platform = platform,
                            incomingMessage = messageText,
                            replyMessage = reply,
                            timestamp = System.currentTimeMillis()
                        )
                        _recentBotActivities.value = (listOf(activity) + _recentBotActivities.value).take(50)

                        // Save log to local Room DB
                        val db = AppDatabase.getDatabase(applicationContext)
                        db.syncLogDao().insertLog(
                            SyncLogEntity(
                                timestamp = System.currentTimeMillis(),
                                senderId = "$platform ($senderTitle)",
                                rawMessage = messageText,
                                method = "AI Bot",
                                trxId = "BOT_${System.currentTimeMillis().toString().takeLast(6)}",
                                amount = 0.0,
                                senderPhone = senderTitle,
                                reference = platform,
                                balance = null,
                                status = SyncStatus.SUCCESS.name,
                                responseCode = 200,
                                responseBody = reply,
                                endpointUrl = "$platform Notification Reply",
                                errorMessage = null
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing auto-reply: ${e.message}", e)
            }
        }
    }

    /**
     * Extracts the Quick Reply Action from notification actions.
     */
    private fun findQuickReplyAction(notification: Notification): Notification.Action? {
        val actions = notification.actions ?: return null
        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (ri in remoteInputs) {
                if (ri.resultKey != null && ri.allowFreeFormInput) {
                    return action
                }
            }
        }
        return null
    }

    /**
     * Sends the text reply directly via Android RemoteInput mechanism.
     */
    private fun sendQuickReply(action: Notification.Action, text: String): Boolean {
        val remoteInputs = action.remoteInputs ?: return false
        val remoteInput = remoteInputs.firstOrNull { it.resultKey != null } ?: return false

        return try {
            val bundle = Bundle().apply {
                putCharSequence(remoteInput.resultKey, text)
            }
            val intent = Intent().apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
            action.actionIntent.send(applicationContext, 0, intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send RemoteInput: ${e.message}", e)
            false
        }
    }
}
