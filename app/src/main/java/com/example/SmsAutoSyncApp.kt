package com.example

import android.app.Application
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.PreferencesManager
import com.example.service.SyncForegroundService

class SmsAutoSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("SmsAutoSyncApp", "SMS Payment AutoSync initialized.")
        // Ensure Database is pre-warmed
        AppDatabase.getDatabase(this)

        val prefs = PreferencesManager.getInstance(this)
        val config = prefs.loadConfig()
        if (config.autoStartService) {
            try {
                SyncForegroundService.start(this)
            } catch (e: Exception) {
                Log.w("SmsAutoSyncApp", "Could not autostart foreground service at boot: ${e.message}")
            }
        }
    }
}
