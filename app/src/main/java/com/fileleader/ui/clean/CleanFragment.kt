package com.fileleader.ui.clean

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.fileleader.databinding.FragmentCleanBinding
import com.fileleader.util.FileUtils
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CleanFragment : Fragment() {

    private var _binding: FragmentCleanBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CleanViewModel by viewModels()
    private lateinit var adapter: JunkGroupAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCleanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupClickListeners()
        observeState()
        viewModel.scan()
    }

    private fun setupRecyclerView() {
        adapter = JunkGroupAdapter(
            onGroupSelect = { type, checked -> viewModel.selectByType(type, checked) },
            onItemToggle = { path -> viewModel.toggleFile(path) }
        )
        binding.recyclerJunk.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerJunk.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnSelectAll.setOnClickListener {
            val allSelected = viewModel.state.value.selectedCount == viewModel.state.value.junkFiles.size
            viewModel.selectAll(!allSelected)
        }
        binding.btnClean.setOnClickListener {
            viewModel.cleanSelected()
        }
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    // Loading
                    binding.progressBar.visibility = if (state.isScanning) View.VISIBLE else View.GONE
                    binding.emptyView.visibility =
                        if (!state.isScanning && state.junkFiles.isEmpty()) View.VISIBLE else View.GONE

                    // List
                    adapter.submitGroups(state.groupedJunk)

                    // Bottom bar
                    val hasSelected = state.selectedCount > 0
                    binding.btnClean.isEnabled = hasSelected && !state.isCleaning
                    binding.tvSelectedInfo.text = if (hasSelected)
                        "已选 ${state.selectedCount} 项 · ${FileUtils.formatSize(state.selectedBytes)}"
                    else "请选择要清理的文件"

                    // Clean progress
                    if (state.isCleaning) {
                        binding.cleanProgressBar.visibility = View.VISIBLE
                        binding.cleanProgressBar.progress = state.cleanProgress
                    } else {
                        binding.cleanProgressBar.visibility = View.GONE
                    }

                    // Result snackbar
                    state.cleanResult?.let { result ->
                        if (!state.isCleaning && result.success > 0) {
                            Snackbar.make(
                                binding.root,
                                "已清理 ${result.success} 个文件，释放 ${FileUtils.formatSize(result.freedBytes)}",
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                    }

                    // Select all button label
                    binding.btnSelectAll.text = if (
                        state.junkFiles.isNotEmpty() &&
                        state.selectedCount == state.junkFiles.size
                    ) "取消全选" else "全选"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
