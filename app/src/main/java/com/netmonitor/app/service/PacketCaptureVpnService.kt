package com.netmonitor.app.service

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.netmonitor.app.Constants
import com.netmonitor.app.MainActivity
import com.netmonitor.app.NetMonitorApp
import com.netmonitor.app.R
import com.netmonitor.app.model.PacketInfo
import com.netmonitor.app.util.PacketBus
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * VPN 抓包服务
 *
 * 改进点:
 * - 移除 companion object 中的静态回调 onPacketCaptured，改用 PacketBus (SharedFlow)
 *   解决内存泄漏：不再持有 ViewModel/Activity 的引用
 * - VPN 配置参数统一引用 Constants
 * - 通知 ID 统一引用 Constants
 */
class PacketCaptureVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCapture()
            ACTION_STOP -> stopCapture()
        }
        return START_STICKY
    }

    private fun startCapture() {
        val builder = Builder()
            .setSession("NetMonitor VPN")
            .addAddress(Constants.VPN_ADDRESS, 32)
            .addRoute(Constants.VPN_ROUTE_V4, 0)
            .addRoute(Constants.VPN_ROUTE_V6, 0)
            .addDnsServer(Constants.VPN_DNS_PRIMARY)
            .addDnsServer(Constants.VPN_DNS_SECONDARY)
            .addDnsServer(Constants.VPN_DNS_V6)
            .setMtu(Constants.VPN_MTU)
            .setBlocking(false)

        // 排除自身流量，防止回环
        try {
            builder.addDisallowedApplication(packageName)
        } catch (_: Exception) {}

        vpnInterface = builder.establish() ?: return

        startForeground(Constants.NOTIFICATION_ID_CAPTURE, createNotification())

        captureJob = scope.launch {
            val fd = vpnInterface!!.fileDescriptor
            val input = FileInputStream(fd)
            val output = FileOutputStream(fd)
            val buffer = ByteArray(65535)

            while (isActive) {
                try {
                    val length = input.read(buffer)
                    if (length > 0) {
                        // 写回隧道，保持网络通畅
                        output.write(buffer, 0, length)
                        output.flush()
                        processPacket(buffer, length)
                    } else {
                        delay(10)
                    }
                } catch (e: Exception) {
                    if (isActive) delay(50)
                }
            }
        }
    }

    private suspend fun processPacket(data: ByteArray, length: Int) {
        try {
            val version = (data[0].toInt() shr 4) and 0xF
            val srcIp: String
            val dstIp: String
            val protocol: Int
            val headerLength: Int
            val protoName: String
            var srcPort = 0
            var dstPort = 0

            when (version) {
                4 -> {
                    protocol = data[9].toInt() and 0xFF
                    headerLength = (data[0].toInt() and 0x0F) * 4
                    srcIp = "${data[12].toInt() and 0xFF}" +
                            ".${data[13].toInt() and 0xFF}" +
                            ".${data[14].toInt() and 0xFF}" +
                            ".${data[15].toInt() and 0xFF}"
                    dstIp = "${data[16].toInt() and 0xFF}" +
                            ".${data[17].toInt() and 0xFF}" +
                            ".${data[18].toInt() and 0xFF}" +
                            ".${data[19].toInt() and 0xFF}"
                }
                6 -> {
                    protocol = data[6].toInt() and 0xFF
                    headerLength = 40
                    srcIp = formatIpv6(data, 8)
                    dstIp = formatIpv6(data, 24)
                }
                else -> return
            }

            when (protocol) {
                6 -> {
                    protoName = "TCP"
                    if (length >= headerLength + 4) {
                        srcPort = ((data[headerLength].toInt() and 0xFF) shl 8) or
                                (data[headerLength + 1].toInt() and 0xFF)
                        dstPort = ((data[headerLength + 2].toInt() and 0xFF) shl 8) or
                                (data[headerLength + 3].toInt() and 0xFF)
                    }
                }
                17 -> {
                    protoName = "UDP"
                    if (length >= headerLength + 4) {
                        srcPort = ((data[headerLength].toInt() and 0xFF) shl 8) or
                                (data[headerLength + 1].toInt() and 0xFF)
                        dstPort = ((data[headerLength + 2].toInt() and 0xFF) shl 8) or
                                (data[headerLength + 3].toInt() and 0xFF)
                    }
                }
                1 -> protoName = "ICMP"
                58 -> protoName = "ICMPv6"
                else -> protoName = "OTHER($protocol)"
            }

            val direction = if (srcIp.startsWith("10.0.0."))
                PacketInfo.Direction.OUTBOUND
            else
                PacketInfo.Direction.INBOUND

            // ✅ 改用 PacketBus 替代静态回调，不再持有外部引用
            PacketBus.emit(
                PacketInfo(
                    protocol = protoName,
                    sourceIp = srcIp,
                    sourcePort = srcPort,
                    destIp = dstIp,
                    destPort = dstPort,
                    length = length,
                    direction = direction
                )
            )
        } catch (_: Exception) { }
    }

    private fun formatIpv6(data: ByteArray, offset: Int): String {
        val sb = StringBuilder()
        for (i in 0 until 8) {
            if (i > 0) sb.append(':')
            val hi = data[offset + i * 2].toInt() and 0xFF
            val lo = data[offset + i * 2 + 1].toInt() and 0xFF
            sb.append(String.format("%x", (hi shl 8) or lo))
        }
        return sb.toString()
    }

    private fun stopCapture() {
        captureJob?.cancel()
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotification() = NotificationCompat.Builder(
        this, NetMonitorApp.CHANNEL_CAPTURE
    )
        .setContentTitle(getString(R.string.capture_running_title))
        .setContentText(getString(R.string.capture_running_text))
        .setSmallIcon(R.drawable.ic_capture)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .setOngoing(true)
        .setSilent(true)
        .build()

    override fun onDestroy() {
        super.onDestroy()
        captureJob?.cancel()
        scope.cancel()
        vpnInterface?.close()
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        const val ACTION_START = "com.netmonitor.action.START_CAPTURE"
        const val ACTION_STOP = "com.netmonitor.action.STOP_CAPTURE"
    }
}
