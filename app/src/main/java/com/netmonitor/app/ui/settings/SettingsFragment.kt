package com.netmonitor.app.ui.settings

import android.content.Intent
import android.os.Bundle
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
import com.netmonitor.app.databinding.FragmentSettingsBinding
import com.netmonitor.app.model.FilterConfig
import com.netmonitor.app.util.AppLogger
import com.netmonitor.app.viewmodel.MonitorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MonitorViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        AppLogger.i("Settings", "SettingsFragment opened")

        viewModel.filterConfig.observe(viewLifecycleOwner) { config ->
            binding.switchTcp.isChecked = config.showTcp
            binding.switchUdp.isChecked = config.showUdp
            binding.switchEstablished.isChecked = config.showEstablished
            binding.switchListening.isChecked = config.showListening
            binding.switchOther.isChecked = config.showOther
            binding.etIpFilter.setText(config.ipFilter)
            binding.etPortFilter.setText(config.portFilter)
            binding.etAppFilter.setText(config.appFilter)
        }

        binding.btnApplyFilter.setOnClickListener {
            val config = FilterConfig(
                showTcp = binding.switchTcp.isChecked,
                showUdp = binding.switchUdp.isChecked,
                showEstablished = binding.switchEstablished.isChecked,
                showListening = binding.switchListening.isChecked,
                showOther = binding.switchOther.isChecked,
                ipFilter = binding.etIpFilter.text.toString().trim(),
                portFilter = binding.etPortFilter.text.toString().trim(),
                appFilter = binding.etAppFilter.text.toString().trim()
            )
            viewModel.updateFilter(config)
            AppLogger.i("Settings", "Filter applied")
            Toast.makeText(requireContext(), "筛选已应用", Toast.LENGTH_SHORT).show()
        }

        binding.btnResetFilter.setOnClickListener {
            viewModel.updateFilter(FilterConfig())
            AppLogger.i("Settings", "Filter reset")
            Toast.makeText(requireContext(), "筛选已重置", Toast.LENGTH_SHORT).show()
        }

        binding.tvLogStats.text = AppLogger.getStats()

        binding.btnRunDiagnostics.setOnClickListener {
            AppLogger.i("Settings", "Running diagnostics...")
            binding.btnRunDiagnostics.isEnabled = false
            binding.btnRunDiagnostics.text = "诊断中..."

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val ctx = requireContext().applicationContext
                    val result = withContext(Dispatchers.IO) {
                        try {
                            AppLogger.runDiagnostics(ctx)
                        } catch (ex: Exception) {
                            AppLogger.e("Settings", "Diagnostics crash: " + ex.message)
                            "诊断失败:\n" + ex.javaClass.simpleName + ": " + ex.message +
                                "\n\n部分日志:\n" + AppLogger.getLogsText()
                        }
                    }

                    if (_binding != null) {
                        binding.btnRunDiagnostics.isEnabled = true
                        binding.btnRunDiagnostics.text = "运行诊断"
                        binding.tvLogStats.text = AppLogger.getStats()
                        showLogDialog("诊断结果", result)
                    }
                } catch (ex: Exception) {
                    AppLogger.e("Settings", "UI update failed: " + ex.message)
                    if (_binding != null) {
                        binding.btnRunDiagnostics.isEnabled = true
                        binding.btnRunDiagnostics.text = "运行诊断"
                        Toast.makeText(
                            requireContext(),
                            "错误: " + ex.message,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        binding.btnViewLogs.setOnClickListener {
            val text = AppLogger.getLogsText()
            if (text.isBlank()) {
                Toast.makeText(requireContext(), "暂无日志", Toast.LENGTH_SHORT).show()
            } else {
                showLogDialog("全部日志 (" + AppLogger.getAllLogs().size + ")", text)
            }
            binding.tvLogStats.text = AppLogger.getStats()
        }

        binding.btnViewErrors.setOnClickListener {
            val text = AppLogger.getErrorsAndCrashes()
            if (text.isBlank()) {
                Toast.makeText(requireContext(), "没有发现错误！", Toast.LENGTH_SHORT).show()
            } else {
                showLogDialog("错误与崩溃", text)
            }
        }

        binding.btnShareLogs.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val ctx = requireContext().applicationContext
                    val diag = withContext(Dispatchers.IO) {
                        try {
                            AppLogger.runDiagnostics(ctx)
                        } catch (ex: Exception) {
                            "诊断失败: " + ex.message
                        }
                    }
                    val allLogs = AppLogger.getLogsText()
                    val fullText = diag + "\n\n===== 完整日志 =====\n" + allLogs

                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, fullText)
                        putExtra(Intent.EXTRA_SUBJECT, "NetMonitor 日志")
                        type = "text/plain"
                    }
                    startActivity(Intent.createChooser(sendIntent, "分享日志"))
                    AppLogger.i("Settings", "Logs shared")
                } catch (ex: Exception) {
                    AppLogger.e("Settings", "Share failed: " + ex.message)
                }
            }
        }

        binding.btnClearLogs.setOnClickListener {
            AppLogger.clear()
            binding.tvLogStats.text = AppLogger.getStats()
            Toast.makeText(requireContext(), "日志已清除", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLogDialog(title: String, content: String) {
        val ctx = context ?: return

        val scrollView = ScrollView(ctx)
        val textView = TextView(ctx).apply {
            text = content
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(32, 24, 32, 24)
            setTextIsSelectable(true)
        }
        scrollView.addView(textView)

        AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton("确定", null)
            .setNeutralButton("复制") { _, _ ->
                val clipboard = ctx.getSystemService(
                    android.content.Context.CLIPBOARD_SERVICE
                ) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("logs", content)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(ctx, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        binding.tvLogStats.text = AppLogger.getStats()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}