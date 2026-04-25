package com.fileleader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FileLeaderApp : Application() {

    companion object {
        const val CHANNEL_SCAN = "channel_scan"
        const val CHANNEL_CLEAN = "channel_clean"

        // Configure libsu BEFORE Application.onCreate
        init {
            Shell.enableVerboseLogging = BuildConfig.DEBUG
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_REDIRECT_STDERR)
                    .setTimeout(10)
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val scanChannel = NotificationChannel(
                CHANNEL_SCAN,
                "扫描进度",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示文件扫描进度"
            }

            val cleanChannel = NotificationChannel(
                CHANNEL_CLEAN,
                "清理进度",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示文件清理进度"
            }

            manager.createNotificationChannels(listOf(scanChannel, cleanChannel))
        }
    }
}
