package com.example.bluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class KeystrokeForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "codeflow_keystroke_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ACTION_START_STREAMING"
        const val ACTION_UPDATE = "ACTION_UPDATE_STATUS"
        const val ACTION_STOP = "ACTION_STOP_STREAMING"
        const val EXTRA_STATUS_TEXT = "EXTRA_STATUS_TEXT"

        fun start(context: Context, statusText: String = "Bluetooth HID Keyboard Active & Ready") {
            try {
                val appCtx = context.applicationContext
                val intent = Intent(appCtx, KeystrokeForegroundService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_STATUS_TEXT, statusText)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appCtx.startForegroundService(intent)
                } else {
                    appCtx.startService(intent)
                }
            } catch (_: Exception) {
                // Ignore if called while backgrounded before permissions
            }
        }

        fun updateStatus(context: Context, statusText: String) {
            start(context, statusText)
        }

        fun stop(context: Context) {
            try {
                val appCtx = context.applicationContext
                appCtx.stopService(Intent(appCtx, KeystrokeForegroundService::class.java))
            } catch (_: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CodeFlowStudio::KeystrokeServiceWakeLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L) // 6-hour continuous session wake lock
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        acquireWakeLock()

        val statusText = intent?.getStringExtra(EXTRA_STATUS_TEXT)
            ?: "Bluetooth HID Keyboard Active (Protected in Background)"
        val notification = buildNotification(statusText)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (_: Exception) {}
        }

        return START_STICKY
    }

    private fun buildNotification(statusText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CodeFlow Keyboard Active")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bluetooth Keyboard Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Bluetooth HID keyboard connection and keystroke streaming alive in background and Recents"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
