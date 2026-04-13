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
    val displayState: String
        get() = when (state) {
            "01" -> "ESTABLISHED"
            "02" -> "SYN_SENT"
            "03" -> "SYN_RECV"
            "04" -> "FIN_WAIT1"
            "05" -> "FIN_WAIT2"
            "06" -> "TIME_WAIT"
            "07" -> "CLOSE"
            "08" -> "CLOSE_WAIT"
            "09" -> "LAST_ACK"
            "0A" -> "LISTEN"
            "0B" -> "CLOSING"
            else -> state
        }

    val isActive: Boolean
        get() = state == "01" || displayState == "ESTABLISHED"

    /**
     * 检测是否暴露了真实IP地址
     * 本地地址是公网IP + 远程地址不是本地 + 正在通信
     * → 该连接没走VPN/代理，直接暴露了真实IP
     */
    val isRealIpExposed: Boolean
        get() {
            if (displayState != "ESTABLISHED") return false
            if (isPrivateOrReservedIp(localIp)) return false
            if (isPrivateOrReservedIp(remoteIp)) return false
            return true
        }

    /**
     * 检测是否在所有网卡上监听（0.0.0.0 或 ::）
     * 同一局域网内设备都能连，公共WiFi下有安全风险
     */
    val isExposedListener: Boolean
        get() {
            if (displayState != "LISTEN") return false
            return isWildcardAddress(localIp)
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