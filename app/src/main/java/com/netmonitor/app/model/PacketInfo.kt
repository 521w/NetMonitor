package com.netmonitor.app.model

data class PacketInfo(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val protocol: String,
    val sourceIp: String,
    val sourcePort: Int,
    val destIp: String,
    val destPort: Int,
    val length: Int,
    val payload: ByteArray? = null,
    val direction: Direction = Direction.OUTBOUND
) {
    enum class Direction { INBOUND, OUTBOUND }

    val directionIcon: String
        get() = if (direction == Direction.OUTBOUND) "↑" else "↓"

    val summary: String
        get() = "$directionIcon $protocol " +
                "$sourceIp:$sourcePort → $destIp:$destPort [$length B]"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PacketInfo) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}