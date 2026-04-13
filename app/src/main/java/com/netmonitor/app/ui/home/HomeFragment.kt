package com.netmonitor.app.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.netmonitor.app.databinding.FragmentHomeBinding
import com.netmonitor.app.service.NetworkMonitorService
import com.netmonitor.app.viewmodel.MonitorViewModel

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MonitorViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(
            inflater, container, false
        )
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.fabToggleMonitor.setOnClickListener {
            viewModel.toggleMonitoring()
            toggleService()
        }

        viewModel.isMonitoring.observe(viewLifecycleOwner) { monitoring ->
            binding.fabToggleMonitor.text =
                if (monitoring) "\u505c\u6b62\u76d1\u63a7" else "\u542f\u52a8\u76d1\u63a7"
            binding.fabToggleMonitor.setIconResource(
                if (monitoring) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )
            binding.tvStatus.text =
                if (monitoring) "\u25cf \u76d1\u63a7\u8fd0\u884c\u4e2d"
                else "\u25cb \u76d1\u63a7\u5df2\u505c\u6b62"
            binding.tvStatus.setTextColor(
                if (monitoring) requireContext().getColor(
                    com.netmonitor.app.R.color.state_active
                ) else requireContext().getColor(
                    com.netmonitor.app.R.color.state_other
                )
            )
        }

        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            binding.tvTotalConnections.text = stats.totalConnections.toString()
            binding.tvActiveConnections.text = stats.activeConnections.toString()
            binding.tvListeningPorts.text = stats.listeningPorts.toString()
            binding.tvTcpCount.text = stats.tcpCount.toString()
            binding.tvUdpCount.text = stats.udpCount.toString()

            // 暴露检测卡片
            binding.tvIpExposedCount.text = stats.realIpExposedCount.toString()
            binding.tvExposedListenerCount.text = stats.exposedListenerCount.toString()
            binding.cardExposure.visibility =
                if (stats.realIpExposedCount > 0 || stats.exposedListenerCount > 0)
                    View.VISIBLE else View.GONE
        }
    }

    private fun toggleService() {
        val ctx = requireContext()
        val intent = Intent(
            ctx,
            NetworkMonitorService::class.java
        )
        if (viewModel.isMonitoring.value == true) {
            intent.action = NetworkMonitorService.ACTION_START
            ctx.startForegroundService(intent)
        } else {
            intent.action = NetworkMonitorService.ACTION_STOP
            ctx.startService(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}