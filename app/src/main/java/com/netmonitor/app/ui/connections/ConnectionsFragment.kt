package com.netmonitor.app.ui.connections

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.netmonitor.app.databinding.FragmentConnectionsBinding
import com.netmonitor.app.model.ConnectionInfo
import com.netmonitor.app.ui.adapter.ConnectionAdapter
import com.netmonitor.app.util.AppLogger
import com.netmonitor.app.util.IpLocator
import com.netmonitor.app.viewmodel.MonitorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConnectionsFragment : Fragment() {

    private var _binding: FragmentConnectionsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MonitorViewModel by activityViewModels()
    private lateinit var adapter: ConnectionAdapter

    private var currentFilter = "all"
    private var searchText = ""
    private var allConnections: List<ConnectionInfo> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConnectionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ConnectionAdapter(
            onItemClick = { conn -> showConnectionDetail(conn) },
            onIpLookup = { ip -> lookupIp(ip) }
        )

        binding.rvConnections.layoutManager = LinearLayoutManager(requireContext())
        binding.rvConnections.adapter = adapter

        binding.chipAll.setOnClickListener { setFilter("all") }
        binding.chipTcp.setOnClickListener { setFilter("tcp") }
        binding.chipUdp.setOnClickListener { setFilter("udp") }
        binding.chipEstablished.setOnClickListener { setFilter("established") }
        binding.chipListen.setOnClickListener { setFilter("listen") }
        binding.chipCloseWait.setOnClickListener { setFilter("close_wait") }
        binding.chipTimeWait.setOnClickListener { setFilter("time_wait") }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchText = s?.toString()?.trim() ?: ""
                applyFilter()
            }
        })

        viewModel.filteredConnections.observe(viewLifecycleOwner) { connections ->
            allConnections = connections ?: emptyList()
            applyFilter()
        }
    }

    private fun setFilter(filter: String) {
        currentFilter = filter
        applyFilter()
    }

    private fun applyFilter() {
        var filtered = allConnections

        filtered = when (currentFilter) {
            "tcp" -> filtered.filter { it.protocol == "TCP" }
            "udp" -> filtered.filter { it.protocol == "UDP" }
            "established" -> filtered.filter { it.displayState == "ESTABLISHED" }
            "listen" -> filtered.filter { it.displayState == "LISTEN" }
            "close_wait" -> filtered.filter { it.displayState == "CLOSE_WAIT" }
            "time_wait" -> filtered.filter { it.displayState == "TIME_WAIT" }
            else -> filtered
        }

        if (searchText.isNotBlank()) {
            val query = searchText.lowercase()
            filtered = filtered.filter { conn ->
                conn.localIp.lowercase().contains(query) ||
                    conn.remoteIp.lowercase().contains(query) ||
                    conn.localPort.toString().contains(query) ||
                    conn.remotePort.toString().contains(query) ||
                    conn.appName.lowercase().contains(query) ||
                    conn.protocol.lowercase().contains(query)
            }
        }

        adapter.submitList(filtered)
        binding.tvConnectionCount.text = "共 " + filtered.size + " 个连接"
    }

    private fun showConnectionDetail(conn: ConnectionInfo) {
        val ctx = context ?: return

        val sb = StringBuilder()
        sb.appendLine("协议: " + conn.protocol)
        sb.appendLine("状态: " + conn.displayState)
        sb.appendLine("")
        sb.appendLine("本地地址: " + conn.localIp + ":" + conn.localPort)
        sb.appendLine("远程地址: " + conn.remoteIp + ":" + conn.remotePort)
        sb.appendLine("")
        sb.appendLine("应用: " + conn.appName)
        sb.appendLine("UID: " + conn.uid)

        val scrollView = ScrollView(ctx)
        val textView = TextView(ctx).apply {
            text = sb.toString()
            textSize = 14f
            setPadding(48, 32, 48, 32)
            setTextIsSelectable(true)
        }
        scrollView.addView(textView)

        AlertDialog.Builder(ctx)
            .setTitle("连接详情")
            .setView(scrollView)
            .setPositiveButton("确定", null)
            .setNeutralButton("查询远程IP") { _, _ ->
                lookupIp(conn.remoteIp)
            }
            .setNegativeButton("复制") { _, _ ->
                val clipboard = ctx.getSystemService(
                    android.content.Context.CLIPBOARD_SERVICE
                ) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("connection", sb.toString())
                clipboard.setPrimaryClip(clip)
                Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun lookupIp(ip: String) {
        val ctx = context ?: return

        if (IpLocator.isLocalIp(ip)) {
            Toast.makeText(ctx, "这是本机/局域网地址，无需查询", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(ctx, "正在查询 " + ip + " ...", Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) {
                IpLocator.lookup(ip)
            }

            if (_binding != null) {
                val scrollView = ScrollView(ctx)
                val textView = TextView(ctx).apply {
                    text = info.format()
                    textSize = 14f
                    setPadding(48, 32, 48, 32)
                    setTextIsSelectable(true)
                }
                scrollView.addView(textView)

                AlertDialog.Builder(ctx)
                    .setTitle("IP 归属地查询")
                    .setView(scrollView)
                    .setPositiveButton("确定", null)
                    .setNeutralButton("复制") { _, _ ->
                        val clipboard = ctx.getSystemService(
                            android.content.Context.CLIPBOARD_SERVICE
                        ) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("ip_info", info.format())
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}