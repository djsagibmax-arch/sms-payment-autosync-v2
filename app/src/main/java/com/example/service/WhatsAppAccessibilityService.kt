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

        // Safety timeout: if more than 30 seconds passed since the app triggered the intent, expire it!
        val elapsed = System.currentTimeMillis() - WhatsAppAutomationManager.dispatchTimeMillis
        if (elapsed > 30000L || elapsed < 0L) {
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

        // CRITICAL: Ensure we are actually inside an active chat conversation with the message input box!
        // Never click anything on WhatsApp home screen, contact list, or menus!
        if (!isConversationWindow(rootNode)) {
            return
        }

        val initialBtn = findSendButton(rootNode)
        if (initialBtn != null && initialBtn.isEnabled) {
            isClickInProgress = true
            val delayMs = config.whatsappSendDelayMs.coerceIn(500L, 2000L)
            Log.i(TAG, "WhatsApp Send button detected in active chat. Waiting ${delayMs}ms to click freshly...")

            mainHandler.postDelayed({
                try {
                    if (!WhatsAppAutomationManager.isPending) {
                        return@postDelayed
                    }

                    // Query window FRESHLY so node is not stale
                    val freshRoot = rootInActiveWindow ?: rootNode
                    if (!isConversationWindow(freshRoot)) {
                        return@postDelayed
                    }

                    val freshBtn = findSendButton(freshRoot) ?: initialBtn
                    var success = false
                    if (freshBtn != null) {
                        success = freshBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (!success && freshBtn.parent != null) {
                            success = freshBtn.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        }
                    }

                    if (success) {
                        Log.i(TAG, "✓ Successfully clicked WhatsApp Send button!")
                        WhatsAppAutomationManager.onSendSuccess(applicationContext, this@WhatsAppAccessibilityService)
                    } else {
                        Log.w(TAG, "First click action returned false, retrying click...")
                        val retryRoot = rootInActiveWindow
                        val retryBtn = if (retryRoot != null) findSendButton(retryRoot) else null
                        if (retryBtn != null && retryBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            Log.i(TAG, "✓ Successfully clicked WhatsApp Send button on retry!")
                            WhatsAppAutomationManager.onSendSuccess(applicationContext, this@WhatsAppAccessibilityService)
                        }
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
     * Checks if the active window is an open chat conversation (has the message input box).
     */
    private fun isConversationWindow(root: AccessibilityNodeInfo): Boolean {
        try {
            val entryStd = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/entry")
            if (entryStd != null && entryStd.isNotEmpty()) return true

            val entryBiz = root.findAccessibilityNodeInfosByViewId("com.whatsapp.w4b:id/entry")
            if (entryBiz != null && entryBiz.isNotEmpty()) return true
        } catch (_: Exception) {}

        // Fallback: check if any node has viewId ending with :id/entry
        return findNodeByViewIdEnding(root, ":id/entry") != null
    }

    private fun findNodeByViewIdEnding(node: AccessibilityNodeInfo?, suffix: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val id = node.viewIdResourceName ?: ""
        if (id.endsWith(suffix, ignoreCase = true)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val found = findNodeByViewIdEnding(child, suffix)
            if (found != null) return found
        }
        return null
    }

    /**
     * Recursively searches for the WhatsApp Send button inside an active chat.
     * Strictly avoids FAB buttons, home screen tabs, or non-send elements.
     */
    private fun findSendButton(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""

        // NEVER match the home screen floating action button (:id/fab) or voice recording buttons!
        val isVoiceNote = desc.contains("voice") || desc.contains("ভয়েস") || desc.contains("record") || viewId.contains("voice")
        val isFab = viewId.endsWith(":id/fab") || viewId.contains("floating")

        if (!isVoiceNote && !isFab) {
            val isSendId = viewId.endsWith(":id/send") ||
                    viewId.endsWith(":id/btn_send") ||
                    viewId.endsWith(":id/send_btn")

            val isSendDesc = desc == "send" ||
                    desc == "পাঠান" ||
                    desc == "send message" ||
                    desc == "পাঠিয়ে দিন" ||
                    desc == "বার্তা পাঠান" ||
                    desc == "প্রেরণ"

            if (isSendId || isSendDesc) {
                if (node.isClickable) {
                    return node
                } else if (node.parent?.isClickable == true) {
                    return node.parent
                }
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
