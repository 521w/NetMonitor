package com.netmonitor.app.model

data class FilterConfig(
    val showTcp: Boolean = true,
    val showUdp: Boolean = true,
    val showEstablished: Boolean = true,
    val showListening: Boolean = true,
    val showOther: Boolean = true,
    val ipFilter: String = "",
    val portFilter: String = "",
    val appFilter: String = ""
) {
    fun matches(conn: ConnectionInfo): Boolean {
        if (!showTcp && conn.protocol == "TCP") return false
        if (!showUdp && conn.protocol == "UDP") return false
        if (!showEstablished && conn.isActive) return false
        if (!showListening && conn.displayState == "LISTEN") return false
        if (!showOther && !conn.isActive
            && conn.displayState != "LISTEN") return false
        if (ipFilter.isNotBlank()
            && !conn.remoteIp.contains(ipFilter)) return false
        if (portFilter.isNotBlank()) {
            val port = portFilter.toIntOrNull() ?: return true
            if (conn.remotePort != port
                && conn.localPort != port) return false
        }
        if (appFilter.isNotBlank()
            && !conn.appName.contains(appFilter, ignoreCase = true))
            return false
        return true
    }

    fun matchesPacket(pkt: PacketInfo): Boolean {
        if (!showTcp && pkt.protocol == "TCP") return false
        if (!showUdp && pkt.protocol == "UDP") return false
        if (ipFilter.isNotBlank()
            && !pkt.sourceIp.contains(ipFilter)
            && !pkt.destIp.contains(ipFilter)) return false
        if (portFilter.isNotBlank()) {
            val port = portFilter.toIntOrNull() ?: return true
            if (pkt.sourcePort != port
                && pkt.destPort != port) return false
        }
        return true
    }
}