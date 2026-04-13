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
import com.netmonitor.app.R
import com.netmonitor.app.databinding.FragmentConnectionsBinding
import com.netmonitor.app.model.ConnectionInfo
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

        // Chip filter
        binding.chipAll.setOnClickListener { setFilter("all") }
        binding.chipTcp.setOnClickListener { setFilter("tcp") }
        binding.chipUdp.setOnClickListener { setFilter("udp") }
        binding.chipEstablished.setOnClickListener { setFilter("established") }
        binding.chipListen.setOnClickListener { setFilter("listen") }
        binding.chipCloseWait.setOnClickListener { setFilter("close_wait") }
        binding.chipTimeWait.setOnClickListener { setFilter("time_wait") }

        // Search box
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchText = s?.toString()?.trim() ?: ""
                applyFilter()
            }
        })

        // Observe connections
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

        // Apply chip filter
        filtered = when (currentFilter) {
            "tcp" -> filtered.filter { it.protocol == "TCP" }
            "udp" -> filtered.filter { it.protocol == "UDP" }
            "established" -> filtered.filter { it.display