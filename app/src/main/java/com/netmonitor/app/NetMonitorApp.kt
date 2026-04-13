package com.netmonitor.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.netmonitor.app.util.AppLogger

class NetMonitorApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        setupCrashHandler()
        AppLogger.i("App", "NetMonitor started")
        AppLogger.i("App", "Android " + Build.VERSION.RELEASE + " API " + Build.VERSION.SDK_INT)
        AppLogger.i("App", "Device: " + Build.MANUFACTURER + " " + Build.MODEL)
    }

    private fun createNotificationChannels() {
        val monitorChannel = NotificationChannel(
            CHANNEL_MONITOR,
            "Network Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Network monitoring service"
        }

        val captureChannel = NotificationChannel(
            CHANNEL_CAPTURE,
            "Packet Capture",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Packet capture service"
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(monitorChannel)
        manager.createNotificationChannel(captureChannel)

        AppLogger.d("App", "Notification channels created")
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val trace = throwable.stackTrace.take(15).joinToString("\n  ") {
                    it.toString()
                }
                val msg = "CRASH in thread [" + thread.name + "]: " +
                    throwable.javaClass.simpleName + ": " + throwable.message +
                    "\n  " + trace

                AppLogger.crash("CRASH", msg)

                // Also log cause if present
                val cause = throwable.cause
                if (cause != null) {
                    val causeTrace = cause.stackTrace.take(5).joinToString("\n  ") {
                        it.toString()
                    }
                    AppLogger.crash("CRASH", "Caused by: " +
                        cause.javaClass.simpleName + ": " + cause.message +
                        "\n  " + causeTrace)
                }
            } catch (_: Exception) {
                // Cannot log the crash, give up logging
            }

            // Forward to default handler
            defaultHandler?.uncaughtException(thread, throwable)
        }
        AppLogger.d("App", "Crash handler installed")
    }

    companion object {
        const val CHANNEL_MONITOR = "network_monitor"
        const val CHANNEL_CAPTURE = "packet_capture"
    }
}