package com.netmonitor.app

/**
 * 全局常量集中管理，消除散落在各处的魔法数字
 */
object Constants {

    // ── 监控配置 ──
    /** 连接刷新间隔（毫秒） */
    const val CONNECTION_REFRESH_INTERVAL_MS = 3000L

    /** 抓包 UI 批量刷新防抖间隔（毫秒） */
    const val PACKET_DEBOUNCE_MS = 150L

    // ── 缓冲区上限 ──
    /** 抓包缓冲区最大条数 */
    const val MAX_PACKET_BUFFER = 5000

    /** 暴露记录最大条数 */
    const val MAX_EXPOSURE_RECORDS = 500

    /** 日志缓冲区最大条数 */
    const val MAX_LOG_ENTRIES = 2000

    // ── VPN 配置 ──
    const val VPN_ADDRESS = "10.0.0.2"
    const val VPN_ROUTE_V4 = "0.0.0.0"
    const val VPN_ROUTE_V6 = "::"
    const val VPN_MTU = 1500
    const val VPN_DNS_PRIMARY = "8.8.8.8"
    const val VPN_DNS_SECONDARY = "8.8.4.4"
    const val VPN_DNS_V6 = "2001:4860:4860::8888"

    // ── RootShell 配置 ──
    /** Root 命令超时时间（秒） */
    const val ROOT_TIMEOUT_SECONDS = 5L

    // ── 通知 ID ──
    const val NOTIFICATION_ID_MONITOR = 1001
    const val NOTIFICATION_ID_CAPTURE = 1002

    // ── 暴露日志 ──
    const val EXPOSURE_LOG_FILE = "exposure_log.json"
    const val EXPOSURE_DEBOUNCE_SAVE_MS = 2000L
}
