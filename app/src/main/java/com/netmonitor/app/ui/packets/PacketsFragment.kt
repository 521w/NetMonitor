package com.netmonitor.app.ui.packets

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.netmonitor.app.databinding.FragmentPacketsBinding
import com.netmonitor.app.model.PacketInfo
import com.netmonitor.app.service.PacketCaptureVpnService
import com.netmonitor.app.ui.adapter.PacketAdapter
import com.netmonitor.app.viewmodel.MonitorViewModel

class PacketsFragment : Fragment() {

    private var _binding: FragmentPacketsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MonitorViewModel by activityViewModels()
    private lateinit var adapter: PacketAdapter

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            startVpnCapture()
        } else {
            Toast.makeText(
                requireContext(),
                "需要 VPN 权限才能抓包",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPacketsBinding.inflate(
            inflater, container, false
        )
        return binding.root
    }

    override fun onViewCreated(
        view: View, savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PacketAdapter { packet ->
            showPacketDetail(packet)
        }
        binding.recyclerPackets.layoutManager =
            LinearLayoutManager(requireContext())
        binding.recyclerPackets.adapter = adapter

        binding.btnToggleCapture.setOnClickListener {
            if (viewModel.isCapturing.value == true) {
                stopVpnCapture()
            } else {
                requestVpnPermission()
            }
        }

        binding.btnClearPackets.setOnClickListener {
            viewModel.clearPackets()
        }

        viewModel.isCapturing.observe(viewLifecycleOwner) { capturing ->
            binding.btnToggleCapture.text =
                if (capturing) "停止抓包" else "开始抓包"
        }

        viewModel.filteredPackets.observe(
            viewLifecycleOwner
        ) { packets ->
            adapter.submitList(packets.take(500))
            binding.tvEmpty.visibility =
                if (packets.isEmpty()) View.VISIBLE else View.GONE
            binding.tvPacketCount.text = "已捕获 ${packets.size} 个数据包"
        }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(requireContext())
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpnCapture()
        }
    }

    private fun startVpnCapture() {
        // ★ 关键修复：启动前设置静态回调
        PacketCaptureVpnService.onPacketCaptured = { packet ->
            viewModel.addPacket(packet)
        }

        val intent = Intent(
            requireContext(),
            PacketCaptureVpnService::class.java
        ).apply {
            action = PacketCaptureVpnService.ACTION_START
        }
        requireContext().startForegroundService(intent)
        viewModel.setCapturing(true)
    }

    private fun stopVpnCapture() {
        val intent = Intent(
            requireContext(),
            PacketCaptureVpnService::class.java
        ).apply {
            action = PacketCaptureVpnService.ACTION_STOP
        }
        requireContext().startService(intent)
        viewModel.setCapturing(false)

        // ★ 关键修复：停止时清除回调
        PacketCaptureVpnService.onPacketCaptured = null
    }

    private fun showPacketDetail(packet: PacketInfo) {
        val detail = """
            |方向: ${if (packet.direction == PacketInfo.Direction.OUTBOUND) "出站 ↑" else "入站 ↓"}
            |协议: ${packet.protocol}
            |源地址: ${packet.sourceIp}:${packet.sourcePort}
            |目标地址: ${packet.destIp}:${packet.destPort}
            |大小: ${packet.length} 字节
            |时间: ${java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS",
                java.util.Locale.getDefault()
            ).format(java.util.Date(packet.timestamp))}
        """.trimMargin()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("数据包详情")
            .setMessage(detail)
            .setPositiveButton("关闭", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
