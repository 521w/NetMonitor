package com.netmonitor.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.netmonitor.app.MainActivity
import com.netmonitor.app.NetMonitorApp
import com.netmonitor.app.R

class NetworkMonitorService : Service() {

    private val binder = MonitorBinder()
    var isRunning = false
        private set

    inner class MonitorBinder : Binder() {
        fun getService(): NetworkMonitorService =
            this@NetworkMonitorService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(
        intent: Intent?, flags: Int, startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> stopMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        if (isRunning) return
        isRunning = true
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun stopMonitoring() {
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT
                or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(
            this, NetMonitorApp.CHANNEL_MONITOR
        )
            .setContentTitle("NetMonitor 运行中")
            .setContentText("正在监控网络连接...")
            .setSmallIcon(R.drawable.ic_monitor)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        const val ACTION_START =
            "com.netmonitor.action.START_MONITOR"
        const val ACTION_STOP =
            "com.netmonitor.action.STOP_MONITOR"
        const val NOTIFICATION_ID = 1001
    }
}