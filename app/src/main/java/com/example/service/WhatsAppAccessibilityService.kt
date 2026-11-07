package com.example.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.PreferencesManager

class WhatsAppAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "WhatsAppAccessibility"
        private const val TARGET_WHATSAPP = "com.whatsapp"
        private const val TARGET_WHATSAPP_BUSINESS = "com.whatsapp.w4b"

        @Volatile
        var isConnected: Boolean = false
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isClickInProgress = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        Log.i(TAG, "WhatsAppAccessibilityService connected & ready.")
        WhatsAppAutomationManager.refreshState(applicationContext)

        // Ensure foreground service is kept alive
        if (!SyncForegroundService.isServiceRunning) {
            SyncForegroundService.start(applicationContext)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName != TARGET_WHATSAPP && packageName != TARGET_WHATSAPP_BUSINESS) {
            return
        }

        // Check if automation is enabled in settings
        val config = PreferencesManager.getInstance(applicationContext).loadConfig()
        if (!config.whatsappAutomationEnabled) {
            return
        }

        // Only proceed if our app has an active pending automation request!
        if (!WhatsAppAutomationManager.isPending) {
            return
        }

        // Safety timeout: if more than 15 seconds passed since the app triggered the intent, expire it!
        val elapsed = System.currentTimeMillis() - WhatsAppAutomationManager.dispatchTimeMillis
        if (elapsed > 15000L || elapsed < 0L) {
            Log.w(TAG, "WhatsApp dispatch request expired ($elapsed ms). Ignoring.")
            WhatsAppAutomationManager.cancelPending()
            return
        }

        if (isClickInProgress) {
            return
        }

        val eventType = event.eventType
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            processActiveWindow(config)
        }
    }

    private fun processActiveWindow(config: com.example.model.SyncConfig) {
        val rootNode = rootInActiveWindow ?: return
        val sendButton = findSendButton(rootNode)

        if (sendButton != null && sendButton.isEnabled) {
            isClickInProgress = true
            // Immediately mark click as initiated so no other accessibility event can trigger another click!
            WhatsAppAutomationManager.markClickInitiated()

            val delayMs = config.whatsappSendDelayMs.coerceIn(800L, 3000L)
            Log.i(TAG, "Send button detected! Waiting safe delay of ${delayMs}ms before auto-click...")

            mainHandler.postDelayed({
                try {
                    // Re-query or perform click on the found button
                    var success = sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (!success) {
                        // Try clicking parent container if child is not directly clickable
                        success = sendButton.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
                    }

                    if (success) {
                        Log.i(TAG, "Successfully clicked WhatsApp Send button!")
                        WhatsAppAutomationManager.onSendSuccess(applicationContext, this@WhatsAppAccessibilityService)
                    } else {
                        Log.w(TAG, "Click action returned false on node. Retrying root search...")
                        val freshRoot = rootInActiveWindow
                        val freshBtn = findSendButton(freshRoot)
                        freshBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        WhatsAppAutomationManager.onSendSuccess(applicationContext, this@WhatsAppAccessibilityService)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing click action: ${e.message}", e)
                } finally {
                    isClickInProgress = false
                }
            }, delayMs)
        }
    }

    /**
     * Recursively searches for the WhatsApp Send button by View ID, Content Description, or text.
     */
    private fun findSendButton(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
        val text = node.text?.toString()?.trim()?.lowercase() ?: ""

        val isSendId = viewId.endsWith(":id/send") ||
                viewId.contains("send_container") ||
                viewId.endsWith(":id/send_btn") ||
                viewId.endsWith(":id/btn_send")

        val isSendDesc = desc == "send" ||
                desc == "পাঠান" ||
                desc == "send message" ||
                desc == "পাঠিয়ে দিন" ||
                desc == "প্রেরণ" ||
                desc.contains("send") ||
                desc.contains("পাঠান")

        val isSendText = text == "send" || text == "পাঠান"

        // Make sure it's not voice note / audio record button
        val isVoiceNote = desc.contains("voice") || desc.contains("ভয়েস") || desc.contains("record")

        if ((isSendId || isSendDesc || isSendText) && !isVoiceNote) {
            if (node.isClickable || node.parent?.isClickable == true) {
                return if (node.isClickable) node else node.parent
            }
        }

        // Search children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val found = findSendButton(child)
            if (found != null) {
                return found
            }
        }

        return null
    }

    override fun onInterrupt() {
        Log.w(TAG, "WhatsAppAccessibilityService interrupted.")
        isClickInProgress = false
    }

    override fun onDestroy() {
        super.onDestroy()
        isConnected = false
        isClickInProgress = false
        WhatsAppAutomationManager.refreshState(applicationContext)
    }
}
