package com.netmonitor.app.model

data class ConnectionInfo(
    val protocol: String,
    val localIp: String,
    val localPort: Int,
    val remoteIp: String,
    val remotePort: Int,
    val state: String,
    val uid: Int,
    val appName: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {

    // ── 状态显示（区分 TCP / UDP） ──

    private val tcpStateMap = mapOf(
        "01" to "ESTABLISHED",
        "02" to "SYN_SENT",
        "03" to "SYN_RECV",
        "04" to "FIN_WAIT1",
        "05" to "FIN_WAIT2",
        "06" to "TIME_WAIT",
        "07" to "CLOSE",
        "08" to "CLOSE_WAIT",
        "09" to "LAST_ACK",
        "0A" to "LISTEN",
        "0B" to "CLOSING"
    )

    /**
     * UDP 状态码映射（/proc/net/udp 的 st 字段）
     * UDP 是无连接协议，状态含义完全不同于 TCP：
     * - 07 表示未绑定远端的 socket → IDLE（不是 TCP 的 CLOSE！）
     * - 01 表示调用过 connect() 绑定了远端地址 → CONNECTED
     */
    private val udpStateMap = mapOf(
        "01" to "CONNECTED",
        "07" to "IDLE"
    )

    val displayState: String
        get() = when (protocol) {
            "UDP" -> udpStateMap[state] ?: state
            else  -> tcpStateMap[state] ?: state
        }

    // ── 连接状态判断 ──

    val isActive: Boolean
        get() = when (protocol) {
            "UDP" -> state == "01"
            else  -> state == "01"
        }

    /**
     * 连接唯一标识 key，用于增量差异更新时比对新旧列表
     */
    val connectionKey: String
        get() = "$protocol|$localIp:$localPort|$remoteIp:$remotePort|$uid"

    // ── 清洗后的 IP 地址 ──

    val cleanLocalIp: String
        get() = cleanIp(localIp)

    val cleanRemoteIp: String
        get() = cleanIp(remoteIp)

    private fun cleanIp(ip: String): String {
        return ip.replace("::ffff:", "").trim()
    }

    // ── 暴露检测 ──

    val isRealIpExposed: Boolean
        get() {
            if (displayState != "ESTABLISHED") return false
            if (isPrivateOrReservedIp(cleanLocalIp)) return false
            if (isPrivateOrReservedIp(cleanRemoteIp)) return false
            return true
        }

    val isExposedListener: Boolean
        get() {
            val isListening = when (protocol) {
                "TCP" -> displayState == "LISTEN"
                "UDP" -> displayState == "IDLE" && localPort > 0
                else -> false
            }
            return isListening && isWildcardAddress(cleanLocalIp)
        }

    fun formatSummary(): String {
        val dir = if (isActive) "↔" else if (displayState == "LISTEN" || displayState == "IDLE") "◉" else "·"
        return "$protocol $dir $cleanLocalIp:$localPort → $cleanRemoteIp:$remotePort [$displayState] $appName"
    }

    companion object {

        fun isPrivateOrReservedIp(ip: String): Boolean {
            val clean = ip.replace("::ffff:", "").trim()
            if (clean.startsWith("127.")) return true
            if (clean.startsWith("10.")) return true
            if (clean.startsWith("192.168.")) return true
            if (clean == "0.0.0.0") return true
            if (clean.startsWith("169.254.")) return true
            if (clean.startsWith("172.")) {
                val parts = clean.split(".")
                if (parts.size >= 2) {
                    val second = parts[1].toIntOrNull() ?: 0
                    if (second in 16..31) return true
                }
            }
            if (clean == "::1" || clean == "::0" || clean == "::" || clean == ":::0") return true
            if (clean.startsWith("fe80:")) return true
            if (clean.startsWith("fc") || clean.startsWith("fd")) return true
            if (clean == "0:0:0:0:0:0:0:0" || clean == "0:0:0:0:0:0:0:1") return true
            if (clean.startsWith("::") && clean.length < 6) return true
            if (clean.startsWith("0:0:")) return true
            return false
        }

        fun isWildcardAddress(ip: String): Boolean {
            val clean = ip.replace("::ffff:", "").trim()
            return clean == "0.0.0.0" ||
                    clean == "::" ||
                    clean == "::0" ||
                    clean == ":::0" ||
                    clean == "0:0:0:0:0:0:0:0"
        }
    }
}