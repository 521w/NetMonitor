package com.netmonitor.app.model

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 暴露记录数据类
 * @param type "ip_leak" = 真实IP暴露, "port_exposed" = 端口暴露
 */
data class ExposureRecord(
    val type: String,
    val protocol: String,
    val localIp: String,
    val localPort: Int,
    val remoteIp: String,
    val remotePort: Int,
    val appName: String,
    val uid: Int,
    val timestamp: Long = System.currentTimeMillis()
) {
    val displayType: String
        get() = when (type) {
            "ip_leak" -> "\u26a0 IP\u66b4\u9732"
            "port_exposed" -> "\u26a0 \u7aef\u53e3\u66b4\u9732"
            else -> type
        }

    val displayTime: String
        get() {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

    /**
     * 用于去重的唯一标识
     * 同一个连接在一次监控会话中只记录一次
     */
    val deduplicationKey: String
        get() = "$type|$localIp|$localPort|$remoteIp|$remotePort|$appName"

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("type", type)
            put("protocol", protocol)
            put("localIp", localIp)
            put("localPort", localPort)
            put("remoteIp", remoteIp)
            put("remotePort", remotePort)
            put("appName", appName)
            put("uid", uid)
            put("timestamp", timestamp)
        }
    }

    fun format(): String {
        val sb = StringBuilder()
        sb.appendLine("[$displayTime] $displayType")
        sb.appendLine("\u5e94\u7528: $appName (UID: $uid)")
        sb.appendLine("\u534f\u8bae: $protocol")
        sb.appendLine("\u672c\u5730: $localIp:$localPort")
        sb.appendLine("\u8fdc\u7a0b: $remoteIp:$remotePort")
        return sb.toString()
    }

    companion object {
        fun fromJson(json: JSONObject): ExposureRecord? {
            return try {
                ExposureRecord(
                    type = json.getString("type"),
                    protocol = json.getString("protocol"),
                    localIp = json.getString("localIp"),
                    localPort = json.getInt("localPort"),
                    remoteIp = json.getString("remoteIp"),
                    remotePort = json.getInt("remotePort"),
                    appName = json.getString("appName"),
                    uid = json.getInt("uid"),
                    timestamp = json.getLong("timestamp")
                )
            } catch (e: Exception) {
                null
            }
        }

        fun fromConnection(conn: ConnectionInfo, type: String): ExposureRecord {
            return ExposureRecord(
                type = type,
                protocol = conn.protocol,
                localIp = conn.localIp,
                localPort = conn.localPort,
                remoteIp = conn.remoteIp,
                remotePort = conn.remotePort,
                appName = conn.appName,
                uid = conn.uid
            )
        }
    }
}
