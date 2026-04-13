package com.netmonitor.app.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.netmonitor.app.model.ConnectionInfo
import java.io.File
import java.net.InetAddress

object NetworkParser {

    private const val TAG = "NetworkParser"
    private var rootChecked = false
    private var rootAvailable = false

    private fun isRootAvailable(): Boolean {
        if (!rootChecked) {
            rootAvailable = RootShell.isRootAvailable()
            rootChecked = true
            Log.i(TAG, "Root available: $rootAvailable")
        }
        return rootAvailable
    }

    fun parseTcpConnections(context: Context): List<ConnectionInfo> {
        val connections = mutableListOf<ConnectionInfo>()
        connections.addAll(parseFile(context, "/proc/net/tcp", "TCP"))
        connections.addAll(parseFile(context, "/proc/net/tcp6", "TCP"))
        return connections
    }

    fun parseUdpConnections(context: Context): List<ConnectionInfo> {
        val connections = mutableListOf<ConnectionInfo>()
        connections.addAll(parseFile(context, "/proc/net/udp", "UDP"))
        connections.addAll(parseFile(context, "/proc/net/udp6", "UDP"))
        return connections
    }

    fun parseAllConnections(context: Context): List<ConnectionInfo> {
        val connections = mutableListOf<ConnectionInfo>()
        connections.addAll(parseTcpConnections(context))
        connections.addAll(parseUdpConnections(context))
        return connections.sortedByDescending { it.timestamp }
    }

    private fun readFileContent(path: String): List<String>? {
        // Root优先：能获取所有进程的完整连接数据
        if (isRootAvailable()) {
            try {
                val content = RootShell.readFileAsRoot(path)
                if (!content.isNullOrBlank()) {
                    Log.d(TAG, "Read $path via root, ${content.lines().size} lines")
                    return content.lines()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Root read failed for $path: ${e.message}")
            }
        }

        // 降级：普通方式读取（只能看到自身进程的连接）
        return try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                val lines = file.readLines()
                Log.d(TAG, "Read $path normally, ${lines.size} lines")
                lines
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Normal read failed for $path: ${e.message}")
            null
        }
    }

    private fun parseFile(
        context: Context,
        path: String,
        protocol: String
    ): List<ConnectionInfo> {
        val connections = mutableListOf<ConnectionInfo>()
        try {
            val lines = readFileContent(path) ?: return connections

            for (i in 1 until lines.size) {
                val line = lines[i].trim()
                if (line.isBlank()) continue

                try {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size < 10) continue

                    val localAddr = parseAddress(parts[1])
                    val remoteAddr = parseAddress(parts[2])
                    val state = parts[3]
                    val uid = parts[7].toIntOrNull() ?: 0

                    connections.add(
                        ConnectionInfo(
                            protocol = protocol,
                            localIp = localAddr.first,
                            localPort = localAddr.second,
                            remoteIp = remoteAddr.first,
                            remotePort = remoteAddr.second,
                            state = state,
                            uid = uid,
                            appName = getAppNameByUid(context, uid)
                        )
                    )
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
        return connections
    }

    private fun parseAddress(raw: String): Pair<String, Int> {
        val parts = raw.split(":")
        if (parts.size != 2) return Pair("0.0.0.0", 0)

        val hexIp = parts[0]
        val port = parts[1].toInt(16)

        val ip = if (hexIp.length == 8) {
            val ipLong = hexIp.toLong(16)
            "${(ipLong and 0xFF).toInt()}" +
                ".${((ipLong shr 8) and 0xFF).toInt()}" +
                ".${((ipLong shr 16) and 0xFF).toInt()}" +
                ".${((ipLong shr 24) and 0xFF).toInt()}"
        } else if (hexIp.length == 32) {
            try {
                val bytes = ByteArray(16)
                for (j in 0 until 4) {
                    val word = hexIp.substring(j * 8, j * 8 + 8)
                    val wordLong = word.toLong(16)
                    bytes[j * 4 + 3] = (wordLong and 0xFF).toByte()
                    bytes[j * 4 + 2] = ((wordLong shr 8) and 0xFF).toByte()
                    bytes[j * 4 + 1] = ((wordLong shr 16) and 0xFF).toByte()
                    bytes[j * 4] = ((wordLong shr 24) and 0xFF).toByte()
                }
                InetAddress.getByAddress(bytes).hostAddress ?: "::0"
            } catch (_: Exception) {
                "::0"
            }
        } else {
            "0.0.0.0"
        }

        return Pair(ip, port)
    }

    @Suppress("DEPRECATION")
    private fun getAppNameByUid(context: Context, uid: Int): String {
        return try {
            val pm = context.packageManager
            val packages = pm.getPackagesForUid(uid)
            if (!packages.isNullOrEmpty()) {
                val appInfo = pm.getApplicationInfo(packages[0], 0)
                pm.getApplicationLabel(appInfo).toString()
            } else if (isRootAvailable()) {
                getRootAppName(uid)
            } else {
                "UID:$uid"
            }
        } catch (_: PackageManager.NameNotFoundException) {
            if (isRootAvailable()) getRootAppName(uid) else "UID:$uid"
        }
    }

    private fun getRootAppName(uid: Int): String {
        return try {
            val result = RootShell.execute(
                "dumpsys package | grep -A1 'userId=$uid' | head -2"
            )
            if (result.isSuccess && result.output.isNotBlank()) {
                val match = Regex("Package\\s+\

\[(\\S+)]").find(result.output)
                match?.groupValues?.get(1) ?: "UID:$uid"
            } else {
                "UID:$uid"
            }
        } catch (_: Exception) {
            "UID:$uid"
        }
    }

    fun getArpTable(): List<Map<String, String>> {
        val entries = mutableListOf<Map<String, String>>()
        val content = if (isRootAvailable()) {
            RootShell.readFileAsRoot("/proc/net/arp")
        } else {
            try { File("/proc/net/arp").readText() } catch (_: Exception) { null }
        } ?: return entries

        val lines = content.lines()
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isBlank()) continue
            val parts = line.split("\\s+".toRegex())
            if (parts.size >= 6) {
                entries.add(
                    mapOf(
                        "ip" to parts[0],
                        "hwType" to parts[1],
                        "flags" to parts[2],
                        "mac" to parts[3],
                        "mask" to parts[4],
                        "device" to parts[5]
                    )
                )
            }
        }
        return entries
    }

    fun getNetworkInterfaces(): String? {
        return if (isRootAvailable()) {
            RootShell.getActiveInterfaces()
        } else null
    }

    fun getRoutingTable(): String? {
        return if (isRootAvailable()) {
            RootShell.getRoutingTable()
        } else null
    }

    fun getIptablesRules(): String? {
        return if (isRootAvailable()) {
            RootShell.getIptablesRules()
        } else null
    }

    fun getDnsConfig(): String? {
        return if (isRootAvailable()) {
            RootShell.getDnsInfo()
        } else null
    }

    fun getNetworkTrafficStats(): String? {
        return if (isRootAvailable()) {
            RootShell.getNetworkStats()
        } else null
    }

    fun getOpenPorts(): String? {
        return if (isRootAvailable()) {
            RootShell.getOpenPorts()
        } else null
    }
}
