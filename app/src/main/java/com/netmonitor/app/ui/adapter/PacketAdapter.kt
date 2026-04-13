package com.netmonitor.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.netmonitor.app.R
import com.netmonitor.app.databinding.ItemPacketBinding
import com.netmonitor.app.model.PacketInfo

class PacketAdapter(
    private val onItemClick: (PacketInfo) -> Unit = {}
) : ListAdapter<PacketInfo,
    PacketAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(
        private val binding: ItemPacketBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PacketInfo) {
            binding.apply {
                tvDirection.text = item.directionIcon
                tvProtocol.text = item.protocol
                tvSource.text =
                    "${item.sourceIp}:${item.sourcePort}"
                tvDest.text =
                    "${item.destIp}:${item.destPort}"
                tvSize.text = "${item.length} B"
                tvTimestamp.text =
                    java.text.SimpleDateFormat(
                        "HH:mm:ss.SSS",
                        java.util.Locale.getDefault()
                    ).format(java.util.Date(item.timestamp))

                tvDirection.setTextColor(
                    ContextCompat.getColor(
                        root.context,
                        if (item.direction ==
                            PacketInfo.Direction.OUTBOUND)
                            R.color.direction_out
                        else R.color.direction_in
                    )
                )

                tvProtocol.setBackgroundColor(
                    ContextCompat.getColor(
                        root.context,
                        when (item.protocol) {
                            "TCP" -> R.color.protocol_tcp
                            "UDP" -> R.color.protocol_udp
                            else -> R.color.protocol_other
                        }
                    )
                )

                root.setOnClickListener { onItemClick(item) }
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ) = ViewHolder(
        ItemPacketBinding.inflate(
            LayoutInflater.from(parent.context),
            parent, false
        )
    )

    override fun onBindViewHolder(
        holder: ViewHolder, position: Int
    ) = holder.bind(getItem(position))

    private class DiffCallback :
        DiffUtil.ItemCallback<PacketInfo>() {
        override fun areItemsTheSame(
            old: PacketInfo, new: PacketInfo
        ) = old.id == new.id

        override fun areContentsTheSame(
            old: PacketInfo, new: PacketInfo
        ) = old == new
    }
}