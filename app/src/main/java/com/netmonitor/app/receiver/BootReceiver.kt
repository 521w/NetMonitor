package com.netmonitor.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.netmonitor.app.util.AppLogger

/**
 * 开机自启广播接收器
 *
 * 改进点:
 * - 统一使用 AppLogger 替代 android.util.Log，保持日志一致性
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            AppLogger.i(TAG, "设备启动完成，NetMonitor 准备就绪")
            // TODO: 如果用户开启了「开机自动监控」选项，在此启动 NetworkMonitorService
        }
    }
}
