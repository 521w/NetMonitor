package com.netmonitor.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.netmonitor.app.R
import com.netmonitor.app.databinding.ItemConnectionBinding
import com.netmonitor.app.model.ConnectionInfo

class ConnectionAdapter(
    private val onItemClick: (ConnectionInfo) -> Unit = {}
) : ListAdapter<ConnectionInfo,
    ConnectionAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(
        private val binding: ItemConnectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ConnectionInfo) {
            binding.apply {
                tvProtocol.text = item.protocol
                tvLocalAddress.text =
                    "${item.localIp}:${item.localPort}"
                tvRemoteAddress.text =
                    "${item.remoteIp}:${item.remotePort}"
                tvState.text = item.displayState
                tvAppName.text = item.appName

                tvProtocol.setBackgroundColor(
                    ContextCompat.getColor(
                        root.context,
                        if (item.protocol == "TCP")
                            R.color.protocol_tcp
                        else R.color.protocol_udp
                    )
                )

                val stateColor = when {
                    item.isActive -> R.color.state_active
                    item.displayState == "LISTEN" ->
                        R.color.state_listen
                    else -> R.color.state_other
                }
                viewStateIndicator.setBackgroundColor(
                    ContextCompat.getColor(
                        root.context, stateColor
                    )
                )

                root.setOnClickListener { onItemClick(item) }
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ) = ViewHolder(
        ItemConnectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent, false
        )
    )

    override fun onBindViewHolder(
        holder: ViewHolder, position: Int
    ) = holder.bind(getItem(position))

    private class DiffCallback :
        DiffUtil.ItemCallback<ConnectionInfo>() {
        override fun areItemsTheSame(
            old: ConnectionInfo, new: ConnectionInfo
        ) = old.localIp == new.localIp
            && old.localPort == new.localPort
            && old.remoteIp == new.remoteIp
            && old.remotePort == new.remotePort
            && old.protocol == new.protocol

        override fun areContentsTheSame(
            old: ConnectionInfo, new: ConnectionInfo
        ) = old == new
    }
}