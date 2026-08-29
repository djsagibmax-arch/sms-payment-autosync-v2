package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.service.SyncForegroundService

class RestartServiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("RestartReceiver", "Auto-Restart triggered: Relaunching SyncForegroundService")
        try {
            SyncForegroundService.start(context)
        } catch (e: Exception) {
            Log.e("RestartReceiver", "Failed to restart service: ${e.message}", e)
        }
    }
}
