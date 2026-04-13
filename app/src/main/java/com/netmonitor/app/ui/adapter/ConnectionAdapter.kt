package com.netmonitor.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.netmonitor.app.databinding.ItemConnectionBinding
import com.netmonitor.app.model.ConnectionInfo

class ConnectionAdapter(
    private val onItemClick: (ConnectionInfo) -> Unit = {},
    private val onIpLookup: (String) -> Unit = {}
) : ListAdapter<ConnectionInfo, ConnectionAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(
        private val binding: ItemConnectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ConnectionInfo) {
            binding.tvProtocol.text = item.protocol
            binding.tvLocalAddr.text = item.localIp + ":" + item.localPort
            binding.tvRemoteAddr.text = item.remoteIp + ":" + item.remotePort
            binding.tvAppName.text = item.appName

            val stateText: String
            val stateColor: Int

            when {
                item.isRealIpExposed -> {
                    stateText = "\u26a0 IP\u66b4\u9732"
                    stateColor = 0xFFF44336.toInt()  // 红色
                }
                item.isExposedListener -> {
                    stateText = "\u26a0 \u7aef\u53e3\u66b4\u9732"
                    stateColor = 0xFFFF5722.toInt()  // 深橙色
                }
                else -> {
                    stateText = item.displayState
                    stateColor = when (item.displayState) {
                        "ESTABLISHED" -> 0xFF4CAF50.toInt()
                        "LISTEN" -> 0xFF2196F3.toInt()
                        "CLOSE_WAIT" -> 0xFFFF9800.toInt()
                        "TIME_WAIT" -> 0xFF9E9E9E.toInt()
                        else -> 0xFF757575.toInt()
                    }
                }
            }

            binding.tvState.text = stateText
            binding.tvState.setTextColor(stateColor)

            binding.root.setOnClickListener { onItemClick(item) }
            binding.root.setOnLongClickListener {
                onIpLookup(item.remoteIp)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConnectionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<ConnectionInfo>() {
        override fun areItemsTheSame(old: ConnectionInfo, new: ConnectionInfo): Boolean {
            return old.localIp == new.localIp &&
                    old.localPort == new.localPort &&
                    old.remoteIp == new.remoteIp &&
                    old.remotePort == new.remotePort &&
                    old.protocol == new.protocol
        }

        override fun areContentsTheSame(old: ConnectionInfo, new: ConnectionInfo): Boolean {
            return old == new
        }
    }
}