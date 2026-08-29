package com.example.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.PreferencesManager
import com.example.receiver.RestartServiceReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SyncForegroundService : Service() {

    companion object {
        private const val TAG = "SyncForegroundService"
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
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting service: ${e.message}", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping service: ${e.message}", e)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var statsJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SmsAutoSync:ServiceForegroundWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // 10 minutes refreshable
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring wake lock: ${e.message}")
        }
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.w(TAG, "App swiped from Recents! Scheduling immediate Auto-Restart...")

        // Schedule an immediate revival via AlarmManager & Broadcast
        try {
            val restartIntent = Intent(applicationContext, RestartServiceReceiver::class.java).apply {
                action = "com.example.RESTART_SERVICE"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                1002,
                restartIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerTime = SystemClock.elapsedRealtime() + 1000 // 1 sec

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule restart alarm: ${e.message}")
        }

        // Direct restart attempt
        start(applicationContext)
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
            .setContentTitle("SMS Payment AutoSync Running (24/7)")
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
                "SMS Background Monitor (24/7)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the SMS payment listener active non-stop in the background"
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
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
