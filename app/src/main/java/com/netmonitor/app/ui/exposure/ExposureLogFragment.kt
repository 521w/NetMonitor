package com.netmonitor.app.ui.exposure

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.netmonitor.app.databinding.FragmentExposureLogBinding
import com.netmonitor.app.model.ExposureRecord
import com.netmonitor.app.ui.adapter.ExposureLogAdapter
import com.netmonitor.app.util.ExposureLogManager

class ExposureLogFragment : Fragment() {

    private var _binding: FragmentExposureLogBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ExposureLogAdapter
    private var currentFilter = "all"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExposureLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ExposureLogAdapter { record ->
            showRecordDetail(record)
        }
        binding.rvExposureLog.layoutManager = LinearLayoutManager(requireContext())
        binding.rvExposureLog.adapter = adapter

        binding.chipAll.setOnClickListener {
            currentFilter = "all"
            loadRecords()
        }
        binding.chipIpLeak.setOnClickListener {
            currentFilter = "ip_leak"
            loadRecords()
        }
        binding.chipPortExposed.setOnClickListener {
            currentFilter = "port_exposed"
            loadRecords()
        }

        binding.btnClear.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("\u786e\u8ba4\u6e05\u7a7a")
                .setMessage("\u786e\u5b9a\u8981\u6e05\u7a7a\u6240\u6709\u66b4\u9732\u8bb0\u5f55\u5417\uff1f\u6b64\u64cd\u4f5c\u4e0d\u53ef\u64a4\u9500\u3002")
                .setPositiveButton("\u6e05\u7a7a") { _, _ ->
                    ExposureLogManager.clear(requireContext())
                    loadRecords()
                    Toast.makeText(requireContext(), "\u5df2\u6e05\u7a7a", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("\u53d6\u6d88", null)
                .show()
        }

        binding.btnExport.setOnClickListener {
            val text = ExposureLogManager.exportText()
            val clipboard = requireContext().getSystemService(
                android.content.Context.CLIPBOARD_SERVICE
            ) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("exposure_log", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "\u5df2\u590d\u5236\u5230\u526a\u8d34\u677f", Toast.LENGTH_SHORT).show()
        }

        loadRecords()
    }

    private fun loadRecords() {
        val records = when (currentFilter) {
            "ip_leak" -> ExposureLogManager.getRecordsByType("ip_leak")
            "port_exposed" -> ExposureLogManager.getRecordsByType("port_exposed")
            else -> ExposureLogManager.getRecords()
        }

        adapter.submitList(records)

        val total = ExposureLogManager.getCount()
        binding.tvLogCount.text = when (currentFilter) {
            "ip_leak" -> "\u771f\u5b9eIP\u66b4\u9732: ${records.size} \u6761 (\u5171 $total \u6761)"
            "port_exposed" -> "\u7aef\u53e3\u66b4\u9732: ${records.size} \u6761 (\u5171 $total \u6761)"
            else -> "\u5171 $total \u6761\u66b4\u9732\u8bb0\u5f55"
        }

        binding.tvEmptyHint.visibility =
            if (records.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showRecordDetail(record: ExposureRecord) {
        val ctx = context ?: return
        val sb = StringBuilder()
        sb.appendLine("\u7c7b\u578b: ${record.displayType}")
        sb.appendLine("\u65f6\u95f4: ${record.displayTime}")
        sb.appendLine("")
        sb.appendLine("\u5e94\u7528: ${record.appName}")
        sb.appendLine("UID: ${record.uid}")
        sb.appendLine("\u534f\u8bae: ${record.protocol}")
        sb.appendLine("")
        sb.appendLine("\u672c\u5730\u5730\u5740: ${record.localIp}:${record.localPort}")
        sb.appendLine("\u8fdc\u7a0b\u5730\u5740: ${record.remoteIp}:${record.remotePort}")
        sb.appendLine("")
        if (record.type == "ip_leak") {
            sb.appendLine("\u98ce\u9669\u8bf4\u660e:")
            sb.appendLine("  \u672c\u5730\u5730\u5740 ${record.localIp} \u662f\u516c\u7f51IP\uff0c")
            sb.appendLine("  \u8bf4\u660e\u6b64\u8fde\u63a5\u672a\u901a\u8fc7VPN/\u4ee3\u7406\uff0c")
            sb.appendLine("  \u5bf9\u65b9\u670d\u52a1\u5668\u53ef\u4ee5\u770b\u5230\u60a8\u7684\u771f\u5b9e\u8eab\u4efd\u3002")
        } else {
            sb.appendLine("\u98ce\u9669\u8bf4\u660e:")
            sb.appendLine("  \u8be5\u7aef\u53e3\u76d1\u542c\u5728\u6240\u6709\u7f51\u5361(0.0.0.0)\uff0c")
            sb.appendLine("  \u540c\u4e00\u5c40\u57df\u7f51\u5185\u7684\u8bbe\u5907\u90fd\u53ef\u4ee5\u8fde\u63a5\u3002")
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
            .setTitle("\u66b4\u9732\u8bb0\u5f55\u8be6\u60c5")
            .setView(scrollView)
            .setPositiveButton("\u786e\u5b9a", null)
            .setNegativeButton("\u590d\u5236") { _, _ ->
                val clipboard = ctx.getSystemService(
                    android.content.Context.CLIPBOARD_SERVICE
                ) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("record", sb.toString())
                clipboard.setPrimaryClip(clip)
                Toast.makeText(ctx, "\u5df2\u590d\u5236", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
