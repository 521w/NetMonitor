package com.netmonitor.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.netmonitor.app.Constants
import com.netmonitor.app.model.ConnectionInfo
import com.netmonitor.app.model.FilterConfig
import com.netmonitor.app.model.PacketInfo
import com.netmonitor.app.repository.NetworkRepository
import com.netmonitor.app.util.PacketBus
import kotlinx.coroutines.*
import java.util.ArrayDeque

/**
 * 监控 ViewModel
 *
 * 改进点:
 * 1. 通过 Repository 层访问数据，不再直接调用 NetworkParser / ExposureLogManager
 * 2. 使用 PacketBus (SharedFlow) 收集抓包数据，替代静态回调
 * 3. addPacket → 批量防抖更新：累积一批数据包后统一刷新 UI，避免每个包都触发
 *    LiveData.postValue + toList() 的 O(n) 开销
 * 4. packetBuffer 改用 ArrayDeque，尾部删除 O(1)（原 ArrayList 头部插入 O(n)）
 * 5. 连接刷新间隔从 1500ms 提升到 3000ms，减少 CPU 占用
 */
class MonitorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NetworkRepository(application)

    // ── LiveData ──

    private val _connections = MutableLiveData<List<ConnectionInfo>>(emptyList())
    val connections: LiveData<List<ConnectionInfo>> = _connections

    private val _packets = MutableLiveData<List<PacketInfo>>(emptyList())
    val packets: LiveData<List<PacketInfo>> = _packets

    private val _isMonitoring = MutableLiveData(false)
    val isMonitoring: LiveData<Boolean> = _isMonitoring

    private val _isCapturing = MutableLiveData(false)
    val isCapturing: LiveData<Boolean> = _isCapturing

    private val _filterConfig = MutableLiveData(FilterConfig())
    val filterConfig: LiveData<FilterConfig> = _filterConfig

    private val _stats = MutableLiveData(NetworkStats())
    val stats: LiveData<NetworkStats> = _stats

    private val _filteredConnections = MutableLiveData<List<ConnectionInfo>>(emptyList())
    val filteredConnections: LiveData<List<ConnectionInfo>> = _filteredConnections

    private val _filteredPackets = MutableLiveData<List<PacketInfo>>(emptyList())
    val filteredPackets: LiveData<List<PacketInfo>> = _filteredPackets

    // ── 内部状态 ──

    private var monitorJob: Job? = null
    private var packetCollectJob: Job? = null
    private var debounceJob: Job? = null

    /**
     * 使用 ArrayDeque 替代 ArrayList，头部插入和尾部删除均为 O(1)
     */
    private val packetBuffer = ArrayDeque<PacketInfo>(Constants.MAX_PACKET_BUFFER)

    // ── 监控控制 ──

    fun startMonitoring() {
        if (_isMonitoring.value == true) return
        _isMonitoring.value = true

        // 重置暴露记录去重，新会话重新检测
        repository.resetExposureSession()

        monitorJob = viewModelScope.launch {
            while (isActive) {
                refreshConnections()
                delay(Constants.CONNECTION_REFRESH_INTERVAL_MS)
            }
        }
    }

    fun stopMonitoring() {
        _isMonitoring.value = false
        monitorJob?.cancel()
        monitorJob = null
    }

    fun toggleMonitoring() {
        if (_isMonitoring.value == true) stopMonitoring() else startMonitoring()
    }

    // ── 抓包控制 ──

    fun setCapturing(capturing: Boolean) {
        _isCapturing.value = capturing

        if (capturing) {
            startCollectingPackets()
        } else {
            packetCollectJob?.cancel()
            packetCollectJob = null
        }
    }

    /**
     * 启动 PacketBus 数据收集协程
     * 从 SharedFlow 收集数据包，批量防抖更新 UI
     */
    private fun startCollectingPackets() {
        packetCollectJob?.cancel()
        packetCollectJob = viewModelScope.launch {
            PacketBus.packets.collect { packet ->
                addPacketInternal(packet)
            }
        }
    }

    /**
     * 批量防抖更新：
     * 每收到一个包先放入 buffer，然后重置防抖定时器。
     * 在最后一个包到达后 PACKET_DEBOUNCE_MS 毫秒内没有新包，才触发一次 UI 刷新。
     * 高流量场景下大幅减少 LiveData 更新次数。
     */
    private fun addPacketInternal(packet: PacketInfo) {
        synchronized(packetBuffer) {
            packetBuffer.addFirst(packet)
            while (packetBuffer.size > Constants.MAX_PACKET_BUFFER) {
                packetBuffer.removeLast()  // ArrayDeque 尾部删除 O(1)
            }
        }

        // 防抖：合并短时间内的多次更新为一次
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(Constants.PACKET_DEBOUNCE_MS)
            flushPacketsToUI()
        }
    }

    private fun flushPacketsToUI() {
        val snapshot: List<PacketInfo>
        synchronized(packetBuffer) {
            snapshot = packetBuffer.toList()
        }
        _packets.postValue(snapshot)

        val filter = _filterConfig.value ?: FilterConfig()
        _filteredPackets.postValue(snapshot.filter { filter.matchesPacket(it) })
    }

    /**
     * 外部手动添加数据包（兼容旧调用方式）
     */
    fun addPacket(packet: PacketInfo) {
        addPacketInternal(packet)
    }

    fun clearPackets() {
        synchronized(packetBuffer) {
            packetBuffer.clear()
        }
        _packets.postValue(emptyList())
        _filteredPackets.postValue(emptyList())
    }

    // ── 筛选 ──

    fun updateFilter(config: FilterConfig) {
        _filterConfig.value = config
        applyConnectionFilter(_connections.value ?: emptyList())
        val packets = _packets.value ?: emptyList()
        _filteredPackets.value = packets.filter { config.matchesPacket(it) }
    }

    // ── 数据刷新 ──

    private suspend fun refreshConnections() {
        // 通过 Repository 获取数据（自动在 IO 线程执行）
        val allConns = repository.getConnections()

        // 扫描暴露（自动在 IO 线程执行）
        repository.scanExposures(allConns)

        withContext(Dispatchers.Main) {
            _connections.value = allConns
            _stats.value = NetworkStats(
                totalConnections = allConns.size,
                activeConnections = allConns.count { it.isActive },
                listeningPorts = allConns.count { it.displayState == "LISTEN" },
                tcpCount = allConns.count { it.protocol == "TCP" },
                udpCount = allConns.count { it.protocol == "UDP" },
                realIpExposedCount = allConns.count { it.isRealIpExposed },
                exposedListenerCount = allConns.count { it.isExposedListener }
            )
            applyConnectionFilter(allConns)
        }
    }

    private fun applyConnectionFilter(allConns: List<ConnectionInfo>) {
        val filter = _filterConfig.value ?: FilterConfig()
        _filteredConnections.value = allConns.filter { filter.matches(it) }
    }

    // ── 生命周期 ──

    override fun onCleared() {
        super.onCleared()
        stopMonitoring()
        packetCollectJob?.cancel()
        debounceJob?.cancel()

        // App 退出前强制保存暴露记录
        repository.forceSaveExposures()
    }

    // ── 统计数据类 ──

    data class NetworkStats(
        val totalConnections: Int = 0,
        val activeConnections: Int = 0,
        val listeningPorts: Int = 0,
        val tcpCount: Int = 0,
        val udpCount: Int = 0,
        val realIpExposedCount: Int = 0,
        val exposedListenerCount: Int = 0
    )
}
