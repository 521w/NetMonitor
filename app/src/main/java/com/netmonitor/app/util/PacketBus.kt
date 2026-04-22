package com.netmonitor.app.util

import com.netmonitor.app.model.PacketInfo
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * 全局数据包事件总线
 *
 * 替代 PacketCaptureVpnService 中的静态回调 onPacketCaptured，
 * 解决 companion object 持有外部引用导致的内存泄漏问题。
 *
 * 使用 SharedFlow：
 * - 多个订阅者可同时收集
 * - 背压策略：缓冲区满时丢弃最旧的包，不阻塞 VPN 线程
 * - Service 销毁后不会残留任何外部引用
 */
object PacketBus {

    private val _packets = MutableSharedFlow<PacketInfo>(
        replay = 0,
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** 订阅方收集此 Flow 即可接收抓包数据 */
    val packets: SharedFlow<PacketInfo> = _packets

    /** VPN Service 调用此方法发射新捕获的数据包 */
    suspend fun emit(packet: PacketInfo) {
        _packets.emit(packet)
    }

    /**
     * 非挂起版本，用于不方便使用协程的场景
     * @return true 表示成功放入缓冲区
     */
    fun tryEmit(packet: PacketInfo): Boolean {
        return _packets.tryEmit(packet)
    }
}
