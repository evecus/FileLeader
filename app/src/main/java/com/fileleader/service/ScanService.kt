package com.fileleader.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.fileleader.FileLeaderApp.Companion.CHANNEL_SCAN
import com.fileleader.R
import com.fileleader.ui.MainActivity

/**
 * Foreground service that keeps the scan alive when app is backgrounded.
 * Progress updates are posted via LocalBroadcastManager or notification.
 */
class ScanService : Service() {

    companion object {
        const val ACTION_START = "com.fileleader.SCAN_START"
        const val ACTION_STOP  = "com.fileleader.SCAN_STOP"
        const val NOTIF_ID = 1001
    }

    private val notificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIF_ID, buildNotification("准备扫描…", 0))
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    fun updateProgress(phase: String, percent: Int) {
        notificationManager.notify(NOTIF_ID, buildNotification(phase, percent))
    }

    private fun buildNotification(text: String, progress: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_SCAN)
            .setContentTitle("文件方领 – 扫描中")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_nav_clean)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .setSilent(true)
            .build()
    }
}
