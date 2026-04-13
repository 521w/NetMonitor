package com.netmonitor.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.netmonitor.app.databinding.FragmentSettingsBinding
import com.netmonitor.app.model.FilterConfig
import com.netmonitor.app.viewmodel.MonitorViewModel

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MonitorViewModel
        by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(
            inflater, container, false
        )
        return binding.root
    }

    override fun onViewCreated(
        view: View, savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.filterConfig.observe(
            viewLifecycleOwner
        ) { config ->
            binding.switchTcp.isChecked = config.showTcp
            binding.switchUdp.isChecked = config.showUdp
            binding.switchEstablished.isChecked =
                config.showEstablished
            binding.switchListening.isChecked =
                config.showListening
            binding.switchOther.isChecked =
                config.showOther
            binding.etIpFilter.setText(config.ipFilter)
            binding.etPortFilter.setText(config.portFilter)
            binding.etAppFilter.setText(config.appFilter)
        }

        binding.btnApplyFilter.setOnClickListener {
            applyFilter()
        }

        binding.btnResetFilter.setOnClickListener {
            viewModel.updateFilter(FilterConfig())
        }
    }

    private fun applyFilter() {
        val config = FilterConfig(
            showTcp = binding.switchTcp.isChecked,
            showUdp = binding.switchUdp.isChecked,
            showEstablished =
                binding.switchEstablished.isChecked,
            showListening =
                binding.switchListening.isChecked,
            showOther = binding.switchOther.isChecked,
            ipFilter = binding.etIpFilter.text
                .toString().trim(),
            portFilter = binding.etPortFilter.text
                .toString().trim(),
            appFilter = binding.etAppFilter.text
                .toString().trim()
        )
        viewModel.updateFilter(config)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}