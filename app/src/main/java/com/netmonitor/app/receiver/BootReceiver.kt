package com.netmonitor.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.netmonitor.app.service.NetworkMonitorService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.i("BootReceiver", "Boot completed, starting NetworkMonitorService")

            val serviceIntent = Intent(context, NetworkMonitorService::class.java).apply {
                this.action = NetworkMonitorService.ACTION_START
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to start service on boot: ${e.message}")
            }
        }
    }
}