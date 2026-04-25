package com.fileleader.ui.analyze

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fileleader.R
import com.fileleader.data.model.StorageCategory
import com.fileleader.data.model.StorageInfo
import com.fileleader.databinding.FragmentAnalyzeBinding
import com.fileleader.domain.engine.StorageAnalyzer
import com.fileleader.util.FileUtils
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ===== ViewModel =====
data class AnalyzeUiState(
    val isLoading: Boolean = false,
    val storageInfo: StorageInfo? = null,
    val largeFiles: List<Pair<String, Long>> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class AnalyzeViewModel @Inject constructor(
    private val analyzer: StorageAnalyzer
) : ViewModel() {

    private val _state = MutableStateFlow(AnalyzeUiState())
    val state: StateFlow<AnalyzeUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val info = analyzer.analyze()
                val large = analyzer.getLargeFiles(20).map { (f, s) -> f.absolutePath to s }
                _state.value = _state.value.copy(
                    isLoading = false,
                    storageInfo = info,
                    largeFiles = large
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }
}

// ===== Fragment =====
@AndroidEntryPoint
class AnalyzeFragment : Fragment() {

    private var _binding: FragmentAnalyzeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AnalyzeViewModel by viewModels()
    private lateinit var largFilesAdapter: LargeFilesAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentAnalyzeBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        largFilesAdapter = LargeFilesAdapter()
        binding.recyclerLargeFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerLargeFiles.adapter = largFilesAdapter

        setupPieChart()
        binding.toolbar.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    state.storageInfo?.let { info ->
                        updateStorageStats(info)
                        updatePieChart(info)
                        updateCategoryList(info.categories)
                    }
                    largFilesAdapter.submit(state.largeFiles)
                }
            }
        }

        viewModel.load()
    }

    private fun setupPieChart() {
        binding.pieChart.apply {
            description.isEnabled = false
            isDrawHoleEnabled = true
            holeRadius = 60f
            setHoleColor(Color.WHITE)
            setUsePercentValues(true)
            setDrawEntryLabels(false)
            legend.isEnabled = false
        }
    }

    private fun updateStorageStats(info: StorageInfo) {
        val usedPct = (info.usedBytes * 100 / info.totalBytes.coerceAtLeast(1)).toInt()
        binding.tvStorageUsed.text = FileUtils.formatSize(info.usedBytes)
        binding.tvStorageTotal.text = FileUtils.formatSize(info.totalBytes)
        binding.tvStorageFree.text = "剩余 ${FileUtils.formatSize(info.freeBytes)}"
        binding.storageProgressBar.progress = usedPct
        binding.tvUsedPercent.text = "$usedPct%"
    }

    private fun updatePieChart(info: StorageInfo) {
        val entries = info.categories
            .filter { it.bytes > 0 }
            .map { cat -> PieEntry(cat.bytes.toFloat(), cat.name) }

        if (entries.isEmpty()) return

        val ds = PieDataSet(entries, "").apply {
            colors = info.categories.filter { it.bytes > 0 }.map { it.color }
            sliceSpace = 2f
            setDrawValues(false)
        }
        binding.pieChart.data = PieData(ds)
        binding.pieChart.invalidate()
    }

    private fun updateCategoryList(categories: List<StorageCategory>) {
        binding.categoryLayout.removeAllViews()
        categories.take(7).forEach { cat ->
            val row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_storage_category, binding.categoryLayout, false)
            row.findViewById<View>(R.id.colorDot).backgroundTintList =
                android.content.res.ColorStateList.valueOf(cat.color)
            row.findViewById<TextView>(R.id.tvCatName).text = "${cat.icon} ${cat.name}"
            row.findViewById<TextView>(R.id.tvCatSize).text = FileUtils.formatSize(cat.bytes)
            binding.categoryLayout.addView(row)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ===== Adapter =====
class LargeFilesAdapter : RecyclerView.Adapter<LargeFilesAdapter.VH>() {
    private val items = mutableListOf<Pair<String, Long>>()

    fun submit(list: List<Pair<String, Long>>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    override fun getItemCount() = items.size
    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(
        LayoutInflater.from(p.context).inflate(R.layout.item_large_file, p, false)
    )

    override fun onBindViewHolder(h: VH, pos: Int) {
        val (path, size) = items[pos]
        h.tvName.text = path.substringAfterLast('/')
        h.tvPath.text = path.substringBeforeLast('/')
        h.tvSize.text = FileUtils.formatSize(size)
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvFileName)
        val tvPath: TextView = v.findViewById(R.id.tvFilePath)
        val tvSize: TextView = v.findViewById(R.id.tvFileSize)
    }
}
