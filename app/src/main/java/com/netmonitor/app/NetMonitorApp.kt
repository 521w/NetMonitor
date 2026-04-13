package com.netmonitor.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class NetMonitorApp : Application() {

    companion object {
        const val CHANNEL_MONITOR = "net_monitor_channel"
        const val CHANNEL_CAPTURE = "net_capture_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val monitorChannel = NotificationChannel(
            CHANNEL_MONITOR,
            "网络监控服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "实时网络连接监控通知"
            setShowBadge(false)
        }

        val captureChannel = NotificationChannel(
            CHANNEL_CAPTURE,
            "抓包服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "VPN数据包捕获通知"
            setShowBadge(false)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(monitorChannel)
        manager.createNotificationChannel(captureChannel)
    }
}