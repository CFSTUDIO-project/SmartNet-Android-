package app.smartnet.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.pm.ServiceInfo
import android.content.Intent
import android.os.Build
import android.os.IBinder
import app.smartnet.MainActivity
import app.smartnet.R

class OverlayService : Service() {
    companion object {
        const val ACTION_START = "app.smartnet.action.START_OVERLAY"
        const val ACTION_STOP = "app.smartnet.action.STOP_OVERLAY"
        const val ACTION_REFRESH = "app.smartnet.action.REFRESH_OVERLAY"
        private const val CHANNEL_ID = "smartnet_workspace"
        private const val NOTIFICATION_ID = 7001
    }

    private var overlayController: SmartOverlayController? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        overlayController = SmartOverlayController(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                overlayController?.destroy()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_REFRESH -> {
                overlayController?.destroy()
                overlayController = SmartOverlayController(this)
                overlayController?.showHandle()
            }
            else -> overlayController?.showHandle()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        overlayController?.destroy()
        overlayController = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Smart .Net workspace",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status of the Smart .Net floating workspace"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 7002, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder
            .setSmallIcon(R.drawable.ic_stat_smartnet)
            .setContentTitle("Smart .Net is active")
            .setContentText("Tap the edge handle to open your app sidebar")
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }
}
