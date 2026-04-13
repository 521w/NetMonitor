package com.netmonitor.app.util

import android.content.Context
import android.content.pm.PackageManager
import com.netmonitor.app.model.ConnectionInfo
import java.io.File
import java.net.InetAddress

object NetworkParser {

    fun parseTcpConnections(
        context: Context
    ): List<ConnectionInfo> {
        val connections = mutableListOf<ConnectionInfo>()
        connections.addAll(
            parseFile(context, "/proc/net/tcp", "TCP")
        )
        connections.addAll(
            parseFile(context, "/proc/net/tcp6", "TCP")
        )
        return connections
    }

    fun parseUdpConnections(
        context: Context
    ): List<ConnectionInfo> {
        val connections = mutableListOf<ConnectionInfo>()
        connections.addAll(
            parseFile(context, "/proc/net/udp", "UDP")
        )
        connections.addAll(
            parseFile(context, "/proc/net/udp6", "UDP")
        )
        return connections
    }

    fun parseAllConnections(
        context: Context
    ): List<ConnectionInfo> {
        val connections = mutableListOf<ConnectionInfo>()
        connections.addAll(parseTcpConnections(context))
        connections.addAll(parseUdpConnections(context))
        return connections.sortedByDescending { it.timestamp }
    }

    private fun parseFile(
        context: Context, path: String, protocol: String
    ): List<ConnectionInfo> {
        val connections = mutableListOf<ConnectionInfo>()
        try {
            val file = File(path)
            if (!file.exists() || !file.canRead())
                return connections
            val lines = file.readLines()
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
                            appName = getAppNameByUid(
                                context, uid
                            )
                        )
                    )
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
        return connections
    }

    private fun parseAddress(
        raw: String
    ): Pair<String, Int> {
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
                    val word = hexIp.substring(
                        j * 8, j * 8 + 8
                    )
                    val wordLong = word.toLong(16)
                    bytes[j * 4 + 3] =
                        (wordLong and 0xFF).toByte()
                    bytes[j * 4 + 2] =
                        ((wordLong shr 8) and 0xFF).toByte()
                    bytes[j * 4 + 1] =
                        ((wordLong shr 16) and 0xFF).toByte()
                    bytes[j * 4] =
                        ((wordLong shr 24) and 0xFF).toByte()
                }
                InetAddress.getByAddress(bytes)
                    .hostAddress ?: "::0"
            } catch (_: Exception) { "::0" }
        } else {
            "0.0.0.0"
        }
        return Pair(ip, port)
    }

    private fun getAppNameByUid(
        context: Context, uid: Int
    ): String {
        return try {
            val pm = context.packageManager
            val packages = pm.getPackagesForUid(uid)
            if (!packages.isNullOrEmpty()) {
                pm.getApplicationLabel(
                    pm.getApplicationInfo(packages[0], 0)
                ).toString()
            } else {
                "UID:$uid"
            }
        } catch (_: PackageManager.NameNotFoundException) {
            "UID:$uid"
        }
    }
}