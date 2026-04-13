package com.netmonitor.app.ui.connections

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.netmonitor.app.databinding.FragmentConnectionsBinding
import com.netmonitor.app.model.ConnectionInfo
import com.netmonitor.app.ui.adapter.ConnectionAdapter
import com.netmonitor.app.viewmodel.MonitorViewModel

class ConnectionsFragment : Fragment() {

    private var _binding: FragmentConnectionsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MonitorViewModel
        by activityViewModels()
    private lateinit var adapter: ConnectionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConnectionsBinding.inflate(
            inflater, container, false
        )
        return binding.root
    }

    override fun onViewCreated(
        view: View, savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ConnectionAdapter { conn ->
            showConnectionDetail(conn)
        }
        binding.recyclerConnections.layoutManager =
            LinearLayoutManager(requireContext())
        binding.recyclerConnections.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener {
            binding.swipeRefresh.isRefreshing = false
        }

        viewModel.filteredConnections.observe(
            viewLifecycleOwner
        ) { connections ->
            adapter.submitList(connections)
            binding.tvEmpty.visibility =
                if (connections.isEmpty()) View.VISIBLE
                else View.GONE
            binding.tvConnectionCount.text =
                "共 ${connections.size} 个连接"
        }
    }

    private fun showConnectionDetail(
        conn: ConnectionInfo
    ) {
        val detail = """
            |协议: ${conn.protocol}
            |状态: ${conn.displayState}
            |本地地址: ${conn.localIp}:${conn.localPort}
            |远程地址: ${conn.remoteIp}:${conn.remotePort}
            |应用: ${conn.appName}
            |UID: ${conn.uid}
        """.trimMargin()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("连接详情")
            .setMessage(detail)
            .setPositiveButton("关闭", null)
            .setNeutralButton("复制 IP") { _, _ ->
                val clipboard = requireContext()
                    .getSystemService(
                        Context.CLIPBOARD_SERVICE
                    ) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                        "IP", conn.remoteIp
                    )
                )
                Toast.makeText(
                    requireContext(),
                    "已复制: ${conn.remoteIp}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}