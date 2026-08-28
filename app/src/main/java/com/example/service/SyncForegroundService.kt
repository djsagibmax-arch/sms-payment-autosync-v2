package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SyncForegroundService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START_SYNC_SERVICE"
        const val ACTION_STOP = "ACTION_STOP_SYNC_SERVICE"
        const val CHANNEL_ID = "sms_foreground_monitor_channel"
        const val NOTIFICATION_ID = 1001

        @Volatile
        var isServiceRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var statsJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                isServiceRunning = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                isServiceRunning = true
                startForeground(NOTIFICATION_ID, buildNotification(0, 0.0))
                monitorStats()
                return START_STICKY
            }
        }
    }

    private fun monitorStats() {
        statsJob?.cancel()
        statsJob = serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.syncLogDao().getSuccessCount().collectLatest { successCount ->
                if (isServiceRunning) {
                    val notification = buildNotification(successCount, 0.0)
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, notification)
                }
            }
        }
    }

    private fun buildNotification(successCount: Int, totalAmount: Double): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val config = PreferencesManager.getInstance(this).loadConfig()
        val wallets = mutableListOf<String>()
        if (config.bkashEnabled) wallets.add("bKash")
        if (config.nagadEnabled) wallets.add("Nagad")
        if (config.rocketEnabled) wallets.add("Rocket")
        if (config.upayEnabled) wallets.add("Upay")
        if (config.cellfinEnabled) wallets.add("Cellfin")

        val walletSummary = if (wallets.isEmpty()) "No wallets active" else wallets.joinToString(", ")

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Payment AutoSync Active")
            .setContentText("Listening: $walletSummary • Synced: $successCount")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SMS Background Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the SMS payment listener active in background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        statsJob?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
