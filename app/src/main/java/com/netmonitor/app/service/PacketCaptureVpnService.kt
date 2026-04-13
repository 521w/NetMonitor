package com.netmonitor.app.service

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.netmonitor.app.MainActivity
import com.netmonitor.app.NetMonitorApp
import com.netmonitor.app.R
import com.netmonitor.app.model.PacketInfo
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.nio.ByteBuffer

class PacketCaptureVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var captureJob: Job? = null
    private val scope =
        CoroutineScope(Dispatchers.IO + SupervisorJob())
    var onPacketCaptured: ((PacketInfo) -> Unit)? = null

    override fun onStartCommand(
        intent: Intent?, flags: Int, startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_START -> startCapture()
            ACTION_STOP -> stopCapture()
        }
        return START_STICKY
    }

    private fun startCapture() {
        val builder = Builder()
            .setSession("NetMonitor VPN")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .setMtu(1500)
            .setBlocking(false)

        vpnInterface = builder.establish() ?: return
        startForeground(NOTIFICATION_ID, createNotification())

        captureJob = scope.launch {
            val input = FileInputStream(
                vpnInterface!!.fileDescriptor
            )
            val buffer = ByteBuffer.allocate(32767)
            while (isActive) {
                try {
                    buffer.clear()
                    val length = input.read(buffer.array())
                    if (length > 0) {
                        buffer.limit(length)
                        processPacket(buffer, length)
                    } else {
                        delay(10)
                    }
                } catch (e: Exception) {
                    if (isActive) delay(100)
                }
            }
        }
    }

    private fun processPacket(buffer: ByteBuffer, length: Int) {
        try {
            val version = (buffer.get(0).toInt() shr 4) and 0xF
            if (version != 4) return

            val protocol = buffer.get(9).toInt() and 0xFF
            val headerLength =
                (buffer.get(0).toInt() and 0x0F) * 4

            val srcIp = "${buffer.get(12).toInt() and 0xFF}" +
                ".${buffer.get(13).toInt() and 0xFF}" +
                ".${buffer.get(14).toInt() and 0xFF}" +
                ".${buffer.get(15).toInt() and 0xFF}"
            val dstIp = "${buffer.get(16).toInt() and 0xFF}" +
                ".${buffer.get(17).toInt() and 0xFF}" +
                ".${buffer.get(18).toInt() and 0xFF}" +
                ".${buffer.get(19).toInt() and 0xFF}"

            val protoName: String
            var srcPort = 0
            var dstPort = 0

            when (protocol) {
                6 -> {
                    protoName = "TCP"
                    if (length >= headerLength + 4) {
                        srcPort = ((buffer.get(headerLength)
                            .toInt() and 0xFF) shl 8) or
                            (buffer.get(headerLength + 1)
                                .toInt() and 0xFF)
                        dstPort = ((buffer.get(headerLength + 2)
                            .toInt() and 0xFF) shl 8) or
                            (buffer.get(headerLength + 3)
                                .toInt() and 0xFF)
                    }
                }
                17 -> {
                    protoName = "UDP"
                    if (length >= headerLength + 4) {
                        srcPort = ((buffer.get(headerLength)
                            .toInt() and 0xFF) shl 8) or
                            (buffer.get(headerLength + 1)
                                .toInt() and 0xFF)
                        dstPort = ((buffer.get(headerLength + 2)
                            .toInt() and 0xFF) shl 8) or
                            (buffer.get(headerLength + 3)
                                .toInt() and 0xFF)
                    }
                }
                1 -> protoName = "ICMP"
                else -> protoName = "OTHER($protocol)"
            }

            val direction =
                if (srcIp.startsWith("10.0.0."))
                    PacketInfo.Direction.OUTBOUND
                else PacketInfo.Direction.INBOUND

            onPacketCaptured?.invoke(
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

    private fun stopCapture() {
        captureJob?.cancel()
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotification() =
        NotificationCompat.Builder(
            this, NetMonitorApp.CHANNEL_CAPTURE
        )
            .setContentTitle("抓包服务运行中")
            .setContentText("正在捕获网络数据包...")
            .setSmallIcon(R.drawable.ic_capture)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT
                        or PendingIntent.FLAG_IMMUTABLE
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
        const val ACTION_START =
            "com.netmonitor.action.START_CAPTURE"
        const val ACTION_STOP =
            "com.netmonitor.action.STOP_CAPTURE"
        const val NOTIFICATION_ID = 1002
    }
}