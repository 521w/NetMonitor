package com.netmonitor.app.util

import com.netmonitor.app.Constants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用日志管理器
 *
 * 改进点:
 * - 使用 ArrayDeque 作为循环缓冲区，替代 CopyOnWriteArrayList
 *   原来 removeAt(0) 在 CopyOnWriteArrayList 上每次都复制整个数组 O(n)
 *   ArrayDeque.removeFirst() 是 O(1)
 * - 所有访问通过 synchronized 保护，线程安全
 * - 常量统一引用 Constants
 */
object AppLogger {

    enum class Level { DEBUG, INFO, WARN, ERROR, CRASH }

    data class LogEntry(
        val time: Long = System.currentTimeMillis(),
        val level: Level,
        val tag: String,
        val message: String
    ) {
        fun format(): String {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            val t = sdf.format(Date(time))
            return "[$t] [${level.name}] [$tag] $message"
        }
    }

    /**
     * 使用 ArrayDeque 作为环形缓冲区
     * 优势：addLast O(1)，removeFirst O(1)
     * 比原来 CopyOnWriteArrayList.removeAt(0) 的 O(n) 复制快得多
     */
    private val logs = ArrayDeque<LogEntry>(Constants.MAX_LOG_ENTRIES + 16)
    private val lock = Any()

    fun d(tag: String, msg: String) = add(Level.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = add(Level.INFO, tag, msg)
    fun w(tag: String, msg: String) = add(Level.WARN, tag, msg)
    fun e(tag: String, msg: String) = add(Level.ERROR, tag, msg)
    fun crash(tag: String, msg: String) = add(Level.CRASH, tag, msg)

    private fun add(level: Level, tag: String, msg: String) {
        val entry = LogEntry(level = level, tag = tag, message = msg)

        synchronized(lock) {
            logs.addLast(entry)
            while (logs.size > Constants.MAX_LOG_ENTRIES) {
                logs.removeFirst()  // O(1) 替代原来 removeAt(0) 的 O(n)
            }
        }

        // 同步输出到 Logcat
        android.util.Log.println(
            when (level) {
                Level.DEBUG -> android.util.Log.DEBUG
                Level.INFO -> android.util.Log.INFO
                Level.WARN -> android.util.Log.WARN
                Level.ERROR -> android.util.Log.ERROR
                Level.CRASH -> android.util.Log.ERROR
            },
            "NM_$tag",
            msg
        )
    }

    fun getAllLogs(): List<LogEntry> = synchronized(lock) { logs.toList() }

    fun getLogsText(): String = synchronized(lock) {
        logs.joinToString("\n") { it.format() }
    }

    fun getLogsByLevel(level: Level): List<LogEntry> = synchronized(lock) {
        logs.filter { it.level == level }
    }

    fun getErrorsAndCrashes(): String = synchronized(lock) {
        logs
            .filter { it.level == Level.ERROR || it.level == Level.CRASH }
            .joinToString("\n") { it.format() }
    }

    fun clear() = synchronized(lock) {
        logs.clear()
    }

    fun getStats(): String = synchronized(lock) {
        val total = logs.size
        val debug = logs.count { it.level == Level.DEBUG }
        val info = logs.count { it.level == Level.INFO }
        val warn = logs.count { it.level == Level.WARN }
        val error = logs.count { it.level == Level.ERROR }
        val crash = logs.count { it.level == Level.CRASH }
        "Total: $total | D:$debug I:$info W:$warn E:$error C:$crash"
    }

    fun runDiagnostics(context: android.content.Context): String {
        val sb = StringBuilder()
        sb.appendLine("===== NetMonitor Diagnostics =====")
        sb.appendLine("Time: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
        sb.appendLine("Android: " + android.os.Build.VERSION.RELEASE +
                " (API " + android.os.Build.VERSION.SDK_INT + ")")
        sb.appendLine("Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL)
        sb.appendLine()

        // Root check
        sb.appendLine("--- Root Status ---")
        try {
            val hasRoot = RootShell.isRootAvailable()
            sb.appendLine("Root available: $hasRoot")
            if (hasRoot) {
                val whoami = RootShell.execute("whoami")
                sb.appendLine("whoami: " + whoami.output)
                val id = RootShell.execute("id")
                sb.appendLine("id: " + id.output)
            }
        } catch (ex: Exception) {
            sb.appendLine("Root check error: " + ex.message)
        }
        sb.appendLine()

        // /proc/net files
        sb.appendLine("--- /proc/net Readability ---")
        val procFiles = listOf(
            "/proc/net/tcp", "/proc/net/tcp6",
            "/proc/net/udp", "/proc/net/udp6",
            "/proc/net/arp"
        )
        for (path in procFiles) {
            try {
                val f = java.io.File(path)
                val exists = f.exists()
                val canRead = f.canRead()
                var lineCount = 0
                if (canRead) {
                    lineCount = f.readLines().size
                }
                sb.appendLine("$path: exists=$exists read=$canRead lines=$lineCount")
                if (RootShell.isRootAvailable()) {
                    val rootContent = RootShell.readFileAsRoot(path)
                    val rootLines = rootContent?.lines()?.size ?: 0
                    sb.appendLine("  (root read: $rootLines lines)")
                }
            } catch (ex: Exception) {
                sb.appendLine("$path: ERROR " + ex.message)
            }
        }
        sb.appendLine()

        // Network connections test
        sb.appendLine("--- Connection Parse Test ---")
        try {
            val conns = NetworkParser.parseAllConnections(context)
            sb.appendLine("Total connections found: " + conns.size)
            val tcp = conns.count { it.protocol == "TCP" }
            val udp = conns.count { it.protocol == "UDP" }
            val active = conns.count { it.isActive }
            sb.appendLine("TCP: $tcp | UDP: $udp | Active: $active")
            if (conns.isNotEmpty()) {
                sb.appendLine("Sample: " + conns.first().let {
                    it.protocol + " " + it.localIp + ":" + it.localPort +
                            " -> " + it.remoteIp + ":" + it.remotePort +
                            " [" + it.displayState + "] " + it.appName
                })
            }
        } catch (ex: Exception) {
            sb.appendLine("Parse error: " + ex.message)
        }
        sb.appendLine()

        // Service status
        sb.appendLine("--- Service Status ---")
        try {
            val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE)
                    as android.app.ActivityManager
            @Suppress("DEPRECATION")
            val services = am.getRunningServices(100)
            val myServices = services.filter { it.service.packageName == context.packageName }
            if (myServices.isEmpty()) {
                sb.appendLine("No active services")
            } else {
                for (s in myServices) {
                    sb.appendLine("Running: " + s.service.className)
                }
            }
        } catch (ex: Exception) {
            sb.appendLine("Service check error: " + ex.message)
        }
        sb.appendLine()

        // Permissions
        sb.appendLine("--- Key Permissions ---")
        val perms = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.READ_PHONE_STATE",
            "android.permission.FOREGROUND_SERVICE"
        )
        for (p in perms) {
            val granted = context.checkSelfPermission(p) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            val shortName = p.substringAfterLast(".")
            sb.appendLine("$shortName: $granted")
        }
        sb.appendLine()

        // Memory
        sb.appendLine("--- Memory ---")
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024
        val total = rt.totalMemory() / 1024 / 1024
        val max = rt.maxMemory() / 1024 / 1024
        sb.appendLine("Used: ${used}MB / Total: ${total}MB / Max: ${max}MB")

        // Log stats
        sb.appendLine()
        sb.appendLine("--- Log Stats ---")
        sb.appendLine(getStats())
        sb.appendLine()
        sb.appendLine("===== End Diagnostics =====")

        val result = sb.toString()
        i("Diagnostics", "Diagnostics run completed")
        return result
    }
}
