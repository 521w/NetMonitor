package com.netmonitor.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.netmonitor.app.model.ConnectionInfo
import com.netmonitor.app.model.FilterConfig
import com.netmonitor.app.model.PacketInfo
import com.netmonitor.app.util.NetworkParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MonitorViewModel(application: Application) : AndroidViewModel(application) {

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

    private var monitorJob: Job? = null
    private val packetBuffer = mutableListOf<PacketInfo>()
    private val maxPacketBuffer = 5000

    fun startMonitoring() {
        if (_isMonitoring.value == true) return
        _isMonitoring.value = true

        monitorJob = viewModelScope.launch {
            while (isActive) {
                refreshConnections()
                delay(1500)
            }
        }
    }

    fun stopMonitoring() {
        _isMonitoring.value = false
        monitorJob?.cancel()
        monitorJob = null
    }

    fun toggleMonitoring() {
        if (_isMonitoring.value == true) stopMonitoring()
        else startMonitoring()
    }

    fun setCapturing(capturing: Boolean) {
        _isCapturing.value = capturing
    }

    fun addPacket(packet: PacketInfo) {
        val snapshot: List<PacketInfo>
        synchronized(packetBuffer) {
            packetBuffer.add(0, packet)
            if (packetBuffer.size > maxPacketBuffer) {
                packetBuffer.removeAt(packetBuffer.lastIndex)
            }
            snapshot = packetBuffer.toList()
        }
        _packets.postValue(snapshot)
        val filter = _filterConfig.value ?: FilterConfig()
        _filteredPackets.postValue(snapshot.filter { filter.matchesPacket(it) })
    }

    fun clearPackets() {
        synchronized(packetBuffer) {
            packetBuffer.clear()
        }
        _packets.postValue(emptyList())
        _filteredPackets.postValue(emptyList())
    }

    fun updateFilter(config: FilterConfig) {
        _filterConfig.value = config
        applyConnectionFilter(_connections.value ?: emptyList())
        val packets = _packets.value ?: emptyList()
        _filteredPackets.value = packets.filter { config.matchesPacket(it) }
    }

    private suspend fun refreshConnections() {
        val allConns = withContext(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            NetworkParser.parseAllConnections(ctx)
        }

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

    override fun onCleared() {
        super.onCleared()
        stopMonitoring()
    }

    data class NetworkStats(
        val totalConnections: Int = 0,
        val activeConnections: Int = 0,
        val listeningPorts: Int = 0,
        val tcpCount: Int = 0,
        val udpCount: Int = 0,
        val realIpExposedCount: Int = 0,
        val exposedListenerCount: Int = 0
    )