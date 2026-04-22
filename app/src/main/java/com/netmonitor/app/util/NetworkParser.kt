package com.netmonitor.app.util

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.netmonitor.app.model.ConnectionInfo
import java.io.File
import java.net.InetAddress

object NetworkParser {

    private const val TAG = "NetworkParser"
    private var rootAvailable: Boolean? = null
    private val uidNameCache = HashMap<Int, String>()
    private var cacheBuilt = false

    // ── 增量更新缓存 ──
    private var lastConnectionMap = HashMap<String, ConnectionInfo>()

    private fun isRootAvailable(): Boolean {
        if (rootAvailable == null) {
            rootAvailable = RootShell.isRootAvailable()
        }
        return rootAvailable == true
    }

    // ── UID 缓存 ──

    private fun buildUidCache(context: Context) {
        if (cacheBuilt) return
        AppLogger.i(TAG, "Building UID cache...")
        val startTime = System.currentTimeMillis()

        try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val packages = pm.getInstalledPackages(0)
            for (pkg in packages) {
                val uid = pkg.applicationInfo?.uid ?: continue
                val label = pm.getApplicationLabel(pkg.applicationInfo!!).toString()
                uidNameCache[uid] = label
            }
            AppLogger.i(TAG, "PM cache: ${uidNameCache.size} apps")
        } catch (e: Exception) {
            AppLogger.w(TAG, "PM cache failed: ${e.message}")
        }

        if (isRootAvailable()) {
            try {
                val content = RootShell.readFileAsRoot("/data/system/packages.list")
                if (content != null) {
                    var added = 0
                    for (line in content.lines()) {
                        val parts = line.trim().split(" ")
                        if (parts.size >= 2) {
                            val pkgName = parts[0]
                            val uid = parts[1].toIntOrNull() ?: continue
                            if (!uidNameCache.containsKey(uid)) {
                                uidNameCache[uid] = pkgName
                                added++
                            }
                        }
                    }
                    AppLogger.i(TAG, "Root cache added: $added more")
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Root cache failed: ${e.message}")
            }
        }

        uidNameCache.putIfAbsent(0, "root (kernel)")
        uidNameCache.putIfAbsent(1000, "system")
        uidNameCache.putIfAbsent(1001, "radio")
        uidNameCache.putIfAbsent(1013, "mediaserver")
        uidNameCache.putIfAbsent(1021, "gps")
        uidNameCache.putIfAbsent(1051, "nfc")
        uidNameCache.putIfAbsent(9999, "nobody")

        cacheBuilt = true
        val elapsed = System.currentTimeMillis() - startTime
        AppLogger.i(TAG, "UID cache ready: ${uidNameCache.size} entries in ${elapsed}ms")
    }

    // ── 文件读取（Root 优先） ──

    private fun readFileContent(path: String): List<String>? {
        if (isRootAvailable()) {
            try {
                val content = RootShell.readFileAsRoot(path)
                if (!content.isNullOrBlank()) {
                    return content.lines()
                }
            } catch (_: Exception) {}
        }

        return try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                file.readLines()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    // ── 连接解析 ──

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
        val startTime = System.currentTimeMillis()
        buildUidCache(context)

        val connections = mutableListOf<ConnectionInfo>()
        connections.addAll(parseTcpConnections(context))
        connections.addAll(parseUdpConnections(context))

        // 非 Root 且 /proc/net 不可读时的降级提示
        if (connections.isEmpty() && !isRootAvailable()) {
            AppLogger.w(TAG, "No connections from /proc/net, falling back to ConnectivityManager")
            val fallbackInfo = getConnectivityFallback(context)
            if (fallbackInfo != null) {
                AppLogger.i(TAG, "Fallback: device has active network ($fallbackInfo)")
            }
        }

        val sorted = connections.sortedByDescending { it.timestamp }
        val elapsed = System.currentTimeMillis() - startTime
        AppLogger.i(TAG, "Parsed ${sorted.size} connections in ${elapsed}ms")
        return sorted
    }

    /**
     * 增量差异解析：只返回相比上次有变化的部分
     *
     * 你的设备有 587 条连接，其中大部分（尤其 LISTEN/IDLE）
     * 在两次刷新间不会变化。差异比对可大幅减少 UI 更新量。
     */
    fun parseDifferential(context: Context): DiffResult {
        val current = parseAllConnections(context)
        val currentMap = HashMap<String, ConnectionInfo>(current.size)
        for (conn in current) {
            currentMap[conn.connectionKey] = conn
        }

        val added = mutableListOf<ConnectionInfo>()
        val removed = mutableListOf<ConnectionInfo>()
        val changed = mutableListOf<ConnectionInfo>()

        for ((key, conn) in currentMap) {
            val old = lastConnectionMap[key]
            if (old == null) {
                added.add(conn)
            } else if (old.state != conn.state) {
                changed.add(conn)
            }
        }

        for ((key, conn) in lastConnectionMap) {
            if (!currentMap.containsKey(key)) {
                removed.add(conn)
            }
        }

        lastConnectionMap = currentMap

        return DiffResult(
            all = current,
            added = added,
            removed = removed,
            changed = changed,
            hasChanges = added.isNotEmpty() || removed.isNotEmpty() || changed.isNotEmpty()
        )
    }

    data class DiffResult(
        val all: List<ConnectionInfo>,
        val added: List<ConnectionInfo>,
        val removed: List<ConnectionInfo>,
        val changed: List<ConnectionInfo>,
        val hasChanges: Boolean
    )

    // ── 单文件解析 ──

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
                            appName = uidNameCache[uid] ?: ("UID:$uid")
                        )
                    )
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
        return connections
    }

    // ── 地址解析 ──

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
                    bytes[j * 4] = (wordLong and 0xFF).toByte()
                    bytes[j * 4 + 1] = ((wordLong shr 8) and 0xFF).toByte()
                    bytes[j * 4 + 2] = ((wordLong shr 16) and 0xFF).toByte()
                    bytes[j * 4 + 3] = ((wordLong shr 24) and 0xFF).toByte()
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

    // ── 降级方案 ──

    private fun getConnectivityFallback(context: Context): String? {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return null
            val caps = cm.getNetworkCapabilities(network) ?: return null

            val type = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                else -> "Unknown"
            }

            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            "$type (internet=$hasInternet, validated=$isValidated)"
        } catch (e: Exception) {
            AppLogger.w(TAG, "Fallback failed: ${e.message}")
            null
        }
    }

    // ── 辅助信息查询 ──

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

    fun getNetworkInterfaces(): String? =
        if (isRootAvailable()) RootShell.getActiveInterfaces() else null

    fun getRoutingTable(): String? =
        if (isRootAvailable()) RootShell.getRoutingTable() else null

    fun getIptablesRules(): String? =
        if (isRootAvailable()) RootShell.getIptablesRules() else null

    fun getDnsConfig(): String? =
        if (isRootAvailable()) RootShell.getDnsInfo() else null

    fun getNetworkTrafficStats(): String? =
        if (isRootAvailable()) RootShell.getNetworkStats() else null

    fun getOpenPorts(): String? =
        if (isRootAvailable()) RootShell.getOpenPorts() else null
}