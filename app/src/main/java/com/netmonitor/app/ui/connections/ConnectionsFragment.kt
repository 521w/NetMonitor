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
        binding.chipIpExposed.setOnClickListener { setFilter("ip_exposed") }
        binding.chipExposedListener.setOnClickListener { setFilter("exposed_listener") }

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
            "ip_exposed" -> filtered.filter { it.isRealIpExposed }
            "exposed_listener" -> filtered.filter { it.isExposedListener }
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
        binding.tvConnectionCount.text = when (currentFilter) {
            "ip_exposed" -> "\u26a0 \u771f\u5b9eIP\u66b4\u9732: ${filtered.size} \u4e2a\u8fde\u63a5"
            "exposed_listener" -> "\u26a0 \u7aef\u53e3\u66b4\u9732: ${filtered.size} \u4e2a\u76d1\u542c"
            else -> "\u5171 ${filtered.size} \u4e2a\u8fde\u63a5"
        }
    }

    private fun showConnectionDetail(conn: ConnectionInfo) {
        val ctx = context ?: return

        val sb = StringBuilder()
        sb.appendLine("\u534f\u8bae: " + conn.protocol)
        sb.appendLine("\u72b6\u6001: " + conn.displayState)
        sb.appendLine("")
        sb.appendLine("\u672c\u5730\u5730\u5740: " + conn.localIp + ":" + conn.localPort)
        sb.appendLine("\u8fdc\u7a0b\u5730\u5740: " + conn.remoteIp + ":" + conn.remotePort)
        sb.appendLine("")
        sb.appendLine("\u5e94\u7528: " + conn.appName)
        sb.appendLine("UID: " + conn.uid)

        if (conn.isRealIpExposed) {
            sb.appendLine("")
            sb.appendLine("\u26a0 \u8b66\u544a: \u8be5\u8fde\u63a5\u66b4\u9732\u4e86\u60a8\u7684\u771f\u5b9eIP\u5730\u5740\uff01")
            sb.appendLine("  \u672c\u5730\u5730\u5740 ${conn.localIp} \u662f\u516c\u7f51IP\uff0c")
            sb.appendLine("  \u8bf4\u660e\u6b64\u8fde\u63a5\u672a\u901a\u8fc7VPN/\u4ee3\u7406\uff0c\u76f4\u63a5\u66b4\u9732\u771f\u5b9e\u8eab\u4efd\u3002")
        }
        if (conn.isExposedListener) {
            sb.appendLine("")
            sb.appendLine("\u26a0 \u8b66\u544a: \u8be5\u7aef\u53e3\u76d1\u542c\u5728\u6240\u6709\u7f51\u5361\uff01")
            sb.appendLine("  \u540c\u4e00\u5c40\u57df\u7f51\u5185\u5176\u4ed6\u8bbe\u5907\u53ef\u4ee5\u8fde\u63a5\u6b64\u7aef\u53e3\u3002")
            sb.appendLine("  \u5728\u516c\u5171WiFi\u4e0b\u5b58\u5728\u5b89\u5168\u98ce\u9669\u3002")
        }

        val scrollView = ScrollView(ctx)
        val textView = TextView(ctx).apply {
            text = sb.toString()
            textSize = 14f
            setPadding(48, 32, 48, 32)
            setTextIsSelectable(true)
        }
        scrollView.addView(textView)

        AlertDialog.Builder(ctx)
            .setTitle("\u8fde\u63a5\u8be6\u60c5")
            .setView(scrollView)
            .setPositiveButton("\u786e\u5b9a", null)
            .setNeutralButton("\u67e5\u8be2\u8fdc\u7a0bIP") { _, _ ->
                lookupIp(conn.remoteIp)
            }
            .setNegativeButton("\u590d\u5236") { _, _ ->
                val clipboard = ctx.getSystemService(
                    android.content.Context.CLIPBOARD_SERVICE
                ) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("connection", sb.toString())
                clipboard.setPrimaryClip(clip)
                Toast.makeText(ctx, "\u5df2\u590d\u5236", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun lookupIp(ip: String) {
        val ctx = context ?: return
        if (IpLocator.isLocalIp(ip)) {
            Toast.makeText(ctx, "\u8fd9\u662f\u672c\u673a/\u5c40\u57df\u7f51\u5730\u5740\uff0c\u65e0\u9700\u67e5\u8be2", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(ctx, "\u6b63\u5728\u67e5\u8be2 $ip ...", Toast.LENGTH_SHORT).show()
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
                    .setTitle("IP \u5f52\u5c5e\u5730\u67e5\u8be2")
                    .setView(scrollView)
                    .setPositiveButton("\u786e\u5b9a", null)
                    .setNeutralButton("\u590d\u5236") { _, _ ->
                        val clipboard = ctx.getSystemService(
                            android.content.Context.CLIPBOARD_SERVICE
                        ) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("ip_info", info.format())
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(ctx, "\u5df2\u590d\u5236", Toast.LENGTH_SHORT).show()
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