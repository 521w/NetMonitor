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
}