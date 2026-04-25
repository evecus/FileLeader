package com.fileleader.ui.organize

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fileleader.R
import com.fileleader.data.model.OrganizePreview
import com.fileleader.data.model.OrganizeRule
import com.fileleader.databinding.FragmentOrganizeBinding
import com.fileleader.domain.engine.FileOrganizer
import com.fileleader.util.FileUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ===== ViewModel =====
data class OrganizeUiState(
    val isPreviewLoading: Boolean = false,
    val isOrganizing: Boolean = false,
    val previews: List<OrganizePreview> = emptyList(),
    val enabledRules: Set<String> = emptySet(),
    val organizeProgress: Int = 0,
    val doneCount: Int = 0,
    val totalCount: Int = 0,
    val error: String? = null
)

@HiltViewModel
class OrganizeViewModel @Inject constructor(
    private val organizer: FileOrganizer
) : ViewModel() {

    private val _state = MutableStateFlow(OrganizeUiState(
        enabledRules = organizer.defaultRules.filter { it.enabled }.map { it.id }.toSet()
    ))
    val state: StateFlow<OrganizeUiState> = _state.asStateFlow()

    fun loadPreview() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isPreviewLoading = true, error = null)
            try {
                val activeRules = organizer.defaultRules.map { rule ->
                    rule.copy(enabled = rule.id in _state.value.enabledRules)
                }
                val previews = organizer.preview(rules = activeRules)
                _state.value = _state.value.copy(previews = previews, isPreviewLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isPreviewLoading = false, error = e.message)
            }
        }
    }

    fun toggleRule(ruleId: String) {
        val current = _state.value.enabledRules.toMutableSet()
        if (ruleId in current) current.remove(ruleId) else current.add(ruleId)
        _state.value = _state.value.copy(enabledRules = current)
    }

    fun execute() {
        val allFiles = _state.value.previews.flatMap { it.files }
        if (allFiles.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isOrganizing = true, totalCount = allFiles.size, doneCount = 0)
            organizer.execute(allFiles).collect { (done, total) ->
                _state.value = _state.value.copy(
                    doneCount = done,
                    organizeProgress = done * 100 / total
                )
            }
            _state.value = _state.value.copy(isOrganizing = false)
            loadPreview() // Reload after organize
        }
    }
}

// ===== Fragment =====
@AndroidEntryPoint
class OrganizeFragment : Fragment() {

    private var _binding: FragmentOrganizeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OrganizeViewModel by viewModels()
    private lateinit var adapter: OrganizePreviewAdapter

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentOrganizeBinding.inflate(inflater, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = OrganizePreviewAdapter { ruleId -> viewModel.toggleRule(ruleId) }
        binding.recyclerOrganize.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerOrganize.adapter = adapter

        binding.btnPreview.setOnClickListener { viewModel.loadPreview() }
        binding.btnOrganize.setOnClickListener { showConfirmDialog() }
        binding.toolbar.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.progressBar.visibility = if (state.isPreviewLoading) View.VISIBLE else View.GONE
                    adapter.submitPreviews(state.previews, state.enabledRules)

                    val totalFiles = state.previews.sumOf { it.files.size }
                    val totalSize  = state.previews.sumOf { it.totalSize }
                    binding.tvPreviewSummary.text = if (totalFiles > 0)
                        "共 $totalFiles 个文件 · ${FileUtils.formatSize(totalSize)}"
                    else if (!state.isPreviewLoading) "点击「预览」扫描可整理的文件" else ""

                    binding.btnOrganize.isEnabled = !state.isOrganizing && totalFiles > 0
                    if (state.isOrganizing) {
                        binding.organizeProgressBar.visibility = View.VISIBLE
                        binding.organizeProgressBar.progress = state.organizeProgress
                        binding.tvOrganizeProgress.text = "${state.doneCount}/${state.totalCount}"
                        binding.tvOrganizeProgress.visibility = View.VISIBLE
                    } else {
                        binding.organizeProgressBar.visibility = View.GONE
                        binding.tvOrganizeProgress.visibility = View.GONE
                        if (state.doneCount > 0) {
                            Snackbar.make(binding.root, "已整理 ${state.doneCount} 个文件", Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        viewModel.loadPreview()
    }

    private fun showConfirmDialog() {
        val state = viewModel.state.value
        val total = state.previews.sumOf { it.files.size }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("确认整理")
            .setMessage("将移动 $total 个文件到对应分类文件夹，此操作不可直接撤销（文件不会删除，仅移动位置）。")
            .setPositiveButton("确认整理") { _, _ -> viewModel.execute() }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ===== Adapter =====
class OrganizePreviewAdapter(
    private val onToggleRule: (String) -> Unit
) : RecyclerView.Adapter<OrganizePreviewAdapter.PreviewVH>() {

    private val previews = mutableListOf<OrganizePreview>()
    private var enabledRules = emptySet<String>()

    fun submitPreviews(list: List<OrganizePreview>, enabled: Set<String>) {
        previews.clear()
        previews.addAll(list)
        enabledRules = enabled
        notifyDataSetChanged()
    }

    override fun getItemCount() = previews.size
    override fun onCreateViewHolder(parent: ViewGroup, vt: Int) = PreviewVH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_organize_rule, parent, false)
    )

    override fun onBindViewHolder(h: PreviewVH, pos: Int) = h.bind(previews[pos])

    inner class PreviewVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvEmoji: TextView = v.findViewById(R.id.tvRuleEmoji)
        val tvName: TextView  = v.findViewById(R.id.tvRuleName)
        val tvTarget: TextView= v.findViewById(R.id.tvRuleTarget)
        val tvInfo: TextView  = v.findViewById(R.id.tvRuleInfo)
        val sw: Switch        = v.findViewById(R.id.switchRule)

        fun bind(preview: OrganizePreview) {
            tvEmoji.text  = preview.rule.icon
            tvName.text   = preview.rule.name
            tvTarget.text = "→ /${preview.rule.targetFolder}/"
            tvInfo.text   = "${preview.files.size} 个文件 · ${FileUtils.formatSize(preview.totalSize)}"
            sw.setOnCheckedChangeListener(null)
            sw.isChecked = preview.rule.id in enabledRules
            sw.setOnCheckedChangeListener { _, _ -> onToggleRule(preview.rule.id) }
        }
    }
}
