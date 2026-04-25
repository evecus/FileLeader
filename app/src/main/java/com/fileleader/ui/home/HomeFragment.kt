package com.fileleader.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.fileleader.R
import com.fileleader.data.model.PermissionMode
import com.fileleader.data.model.ScanPhase
import com.fileleader.databinding.FragmentHomeBinding
import com.fileleader.util.FileUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        observeState()
    }

    private fun setupClickListeners() {
        binding.btnScan.setOnClickListener {
            viewModel.startFullScan()
        }
        binding.cardJunk.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_clean)
        }
        binding.cardDuplicate.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_duplicates)
        }
        binding.cardOrganize.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_organize)
        }
        binding.cardAnalyze.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_analyze)
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updatePermissionBadge(state.permissionMode)
                    updateStorageRing(state)
                    updateScanCards(state)
                    updateScanButton(state)
                    updateProgressBar(state)
                }
            }
        }
    }

    private fun updatePermissionBadge(mode: PermissionMode) {
        binding.tvPermissionMode.text = mode.label
        val color = when (mode) {
            PermissionMode.ROOT   -> R.color.badge_root
            PermissionMode.ADB    -> R.color.badge_adb
            PermissionMode.NORMAL -> R.color.badge_normal
        }
        binding.tvPermissionMode.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), color)
    }

    private fun updateStorageRing(state: HomeUiState) {
        state.storageInfo?.let { info ->
            val usedPct = (info.usedBytes * 100 / info.totalBytes.coerceAtLeast(1)).toInt()
            binding.storageRing.setProgress(usedPct)
            binding.tvStorageUsed.text = FileUtils.formatSize(info.usedBytes)
            binding.tvStorageTotal.text = "/ ${FileUtils.formatSize(info.totalBytes)}"
            binding.tvStorageFree.text = "剩余 ${FileUtils.formatSize(info.freeBytes)}"
        }
    }

    private fun updateScanCards(state: HomeUiState) {
        // Junk card
        if (state.totalJunkBytes > 0) {
            binding.tvJunkSize.text = FileUtils.formatSize(state.totalJunkBytes)
            binding.tvJunkCount.text = "${state.junkFiles.size} 个文件"
        } else {
            binding.tvJunkSize.text = "待扫描"
            binding.tvJunkCount.text = "—"
        }

        // Duplicate card
        if (state.totalDuplicateBytes > 0) {
            binding.tvDuplicateSize.text = FileUtils.formatSize(state.totalDuplicateBytes)
            binding.tvDuplicateCount.text = "${state.duplicateGroups.size} 组重复"
        } else {
            binding.tvDuplicateSize.text = "待扫描"
            binding.tvDuplicateCount.text = "—"
        }
    }

    private fun updateScanButton(state: HomeUiState) {
        binding.btnScan.isEnabled = !state.isScanning
        binding.btnScan.text = if (state.isScanning) "扫描中…" else "开始扫描"
    }

    private fun updateProgressBar(state: HomeUiState) {
        if (state.isScanning) {
            binding.scanProgressLayout.visibility = View.VISIBLE
            binding.scanProgressBar.progress = state.scanProgress.percent
            binding.tvScanPhase.text = state.scanProgress.phase.label
            binding.tvScanDetail.text = if (state.scanProgress.currentFile.isNotEmpty())
                state.scanProgress.currentFile.substringAfterLast('/') else ""
        } else {
            binding.scanProgressLayout.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
