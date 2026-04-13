package com.netmonitor.app.util

import android.content.Context
import android.content.pm.PackageManager
import com.netmonitor.app.model.ConnectionInfo
import java.io.File
import java.net.InetAddress

object NetworkParser {

    private const val TAG = "NetworkParser"
    private var rootAvailable: Boolean? = null
    private val uidNameCache = HashMap<Int, String>()
    private var cacheBuilt = false

    private fun isRootAvailable(): Boolean {
        if (rootAvailable == null) {
            rootAvailable = RootShell.isRootAvailable()
        }
        return rootAvailable == true
    }

    private fun buildUidCache(context: Context) {
        if (cacheBuilt) return
        AppLogger.i(TAG, "Building UID cache...")
        val startTime = System.currentTimeMillis()

        // Step 1: PackageManager (works without root, gets most apps)
        try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val packages = pm.getInstalledPackages(0)
            for (pkg in packages) {
                val uid = pkg.applicationInfo?.uid ?: continue
                val label = pm.getApplicationLabel(pkg.applicationInfo!!).toString()
                uidNameCache[uid] = label
            }
            AppLogger.i(TAG, "PM cache: " + uidNameCache.size + " apps")
        } catch (e: Exception) {
            AppLogger.w(TAG, "PM cache failed: " + e.message)
        }

        // Step 2: Root packages.list (gets system UIDs too)
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
                    AppLogger.i(TAG, "Root cache added: " + added + " more")
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Root cache failed: " + e.message)
            }
        }

        // Well-known system UIDs
        uidNameCache.putIfAbsent(0, "root (kernel)")
        uidNameCache.putIfAbsent(1000, "system")
        uidNameCache.putIfAbsent(1001, "radio")
        uidNameCache.putIfAbsent(1013, "mediaserver")
        uidNameCache.putIfAbsent(1021, "gps")
        uidNameCache.putIfAbsent(1051, "nfc")
        uidNameCache.putIfAbsent(9999, "nobody")

        cacheBuilt = true
        val elapsed = System.currentTimeMillis() - startTime
        AppLogger.i(TAG, "UID cache ready: " + uidNameCache.size + " entries in " + elapsed + "ms")
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
        val startTime = System.currentTimeMillis()
        buildUidCache(context)

        val connections = mutableListOf<ConnectionInfo>()
        connections.addAll(parseTcpConnections(context))
        connections.addAll(parseUdpConnections(context))

        val sorted = connections.sortedByDescending { it.timestamp }
        val elapsed = System.currentTimeMillis() - startTime
        AppLogger.i(TAG, "Parsed " + sorted.size + " connections in " + elapsed + "ms")
        return sorted
    }

    private fun readFileContent(path: String): List<String>? {
        // Root first: sees ALL connections from ALL processes
        if (isRootAvailable()) {
            try {
                val content = RootShell.readFileAsRoot(path)
                if (!content.isNullOrBlank()) {
                    return content.lines()
                }
            } catch (_: Exception) {}
        }

        // Fallback: normal read (only own process connections on Android 10+)
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
                            appName = uidNameCache[uid] ?: ("UID:" + uid)
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
            "" + (ipLong and 0xFF).toInt() +
                "." + ((ipLong shr 8) and 0xFF).toInt() +
                "." + ((ipLong shr 16) and 0xFF).toInt() +
                "." + ((ipLong shr 24) and 0xFF).toInt()
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