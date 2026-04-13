package com.netmonitor.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.netmonitor.app.databinding.ItemExposureLogBinding
import com.netmonitor.app.model.ExposureRecord

class ExposureLogAdapter(
    private val onItemClick: (ExposureRecord) -> Unit = {}
) : ListAdapter<ExposureRecord, ExposureLogAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(
        private val binding: ItemExposureLogBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ExposureRecord) {
            binding.tvType.text = item.displayType
            binding.tvType.setTextColor(
                if (item.type == "ip_leak") 0xFFF44336.toInt()
                else 0xFFFF5722.toInt()
            )
            binding.tvAppName.text = item.appName
            binding.tvAddress.text = "${item.localIp}:${item.localPort} \u2192 ${item.remoteIp}:${item.remotePort}"
            binding.tvTime.text = item.displayTime
            binding.tvProtocol.text = item.protocol

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExposureLogBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<ExposureRecord>() {
        override fun areItemsTheSame(old: ExposureRecord, new: ExposureRecord): Boolean {
            return old.timestamp == new.timestamp &&
                    old.deduplicationKey == new.deduplicationKey
        }

        override fun areContentsTheSame(old: ExposureRecord, new: ExposureRecord): Boolean {
            return old == new
        }
    }
}
