package com.fileleader.ui.duplicates

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fileleader.R
import com.fileleader.data.model.DuplicateFile
import com.fileleader.data.model.DuplicateGroup
import com.fileleader.databinding.FragmentDuplicatesBinding
import com.fileleader.domain.engine.CleanEngine
import com.fileleader.domain.engine.DuplicateScanner
import com.fileleader.util.FileUtils
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ===== ViewModel =====
data class DuplicatesUiState(
    val isScanning: Boolean = false,
    val isCleaning: Boolean = false,
    val groups: List<DuplicateGroup> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val cleanProgress: Int = 0,
    val freedBytes: Long = 0L,
    val error: String? = null
) {
    val selectedBytes: Long get() = groups.flatMap { it.files }
        .filter { it.path in selectedPaths }.sumOf { it.size }
    val totalWastedBytes: Long get() = groups.sumOf { it.wastedBytes }
}

@HiltViewModel
class DuplicatesViewModel @Inject constructor(
    private val scanner: DuplicateScanner,
    private val cleanEngine: CleanEngine
) : ViewModel() {

    private val _state = MutableStateFlow(DuplicatesUiState())
    val state: StateFlow<DuplicatesUiState> = _state.asStateFlow()

    private val _groups = mutableListOf<DuplicateGroup>()

    fun scan() {
        if (_state.value.isScanning) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isScanning = true, error = null)
            _groups.clear()
            scanner.scan().collect { (_, groups) ->
                _groups.clear()
                _groups.addAll(groups)
                _state.value = _state.value.copy(groups = _groups.toList())
            }
            _state.value = _state.value.copy(isScanning = false)
        }
    }

    /** Smart select: in each group, select all except the "keep candidate" (newest file) */
    fun smartSelectAll() {
        val selected = mutableSetOf<String>()
        _groups.forEach { group ->
            group.files.forEach { file ->
                if (!file.isKeepCandidate) selected.add(file.path)
            }
        }
        _state.value = _state.value.copy(selectedPaths = selected)
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selectedPaths = emptySet())
    }

    fun toggleFile(path: String) {
        val current = _state.value.selectedPaths.toMutableSet()
        if (path in current) current.remove(path) else current.add(path)
        _state.value = _state.value.copy(selectedPaths = current)
    }

    fun toggleGroup(groupHash: String, selectAll: Boolean) {
        val group = _groups.find { it.hash == groupHash } ?: return
        val current = _state.value.selectedPaths.toMutableSet()
        if (selectAll) {
            // Select all non-keep files in this group
            group.files.filter { !it.isKeepCandidate }.forEach { current.add(it.path) }
        } else {
            group.files.forEach { current.remove(it.path) }
        }
        _state.value = _state.value.copy(selectedPaths = current)
    }

    fun cleanSelected() {
        val toClean = _state.value.selectedPaths.toList()
        if (toClean.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isCleaning = true)
            var freed = 0L
            cleanEngine.cleanDuplicateFiles(toClean).collect { (done, result) ->
                freed = result.freedBytes
                _state.value = _state.value.copy(
                    cleanProgress = done * 100 / toClean.size,
                    freedBytes = freed
                )
            }
            // Remove cleaned files from groups
            val cleaned = _state.value.selectedPaths
            val updatedGroups = _groups.map { g ->
                g.copy(files = g.files.filter { it.path !in cleaned })
            }.filter { it.files.size >= 2 }
            _groups.clear()
            _groups.addAll(updatedGroups)
            _state.value = _state.value.copy(
                groups = _groups.toList(),
                selectedPaths = emptySet(),
                isCleaning = false
            )
        }
    }
}

// ===== Fragment =====
@AndroidEntryPoint
class DuplicatesFragment : Fragment() {

    private var _binding: FragmentDuplicatesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DuplicatesViewModel by viewModels()
    private lateinit var adapter: DuplicateGroupAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentDuplicatesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = DuplicateGroupAdapter(
            onToggleFile  = { path -> viewModel.toggleFile(path) },
            onToggleGroup = { hash, all -> viewModel.toggleGroup(hash, all) }
        )
        binding.recyclerDuplicates.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerDuplicates.adapter = adapter

        binding.btnSmartSelect.setOnClickListener { viewModel.smartSelectAll() }
        binding.btnClean.setOnClickListener { viewModel.cleanSelected() }
        binding.toolbar.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.progressBar.visibility = if (state.isScanning) View.VISIBLE else View.GONE
                    binding.emptyView.visibility =
                        if (!state.isScanning && state.groups.isEmpty()) View.VISIBLE else View.GONE

                    adapter.submitGroups(state.groups, state.selectedPaths)

                    val hasSelected = state.selectedPaths.isNotEmpty()
                    binding.btnClean.isEnabled = hasSelected && !state.isCleaning
                    binding.tvSelectedInfo.text = if (hasSelected)
                        "已选 ${state.selectedPaths.size} 个文件 · ${FileUtils.formatSize(state.selectedBytes)}"
                    else if (state.groups.isNotEmpty())
                        "共 ${state.groups.size} 组重复 · 浪费 ${FileUtils.formatSize(state.totalWastedBytes)}"
                    else "—"

                    if (state.isCleaning) {
                        binding.cleanProgressBar.visibility = View.VISIBLE
                        binding.cleanProgressBar.progress = state.cleanProgress
                    } else {
                        binding.cleanProgressBar.visibility = View.GONE
                        if (state.freedBytes > 0 && !state.isCleaning) {
                            Snackbar.make(binding.root,
                                "已释放 ${FileUtils.formatSize(state.freedBytes)}",
                                Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        viewModel.scan()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ===== Adapter =====
private sealed class DupItem {
    data class GroupHeader(val group: DuplicateGroup, val selected: Int) : DupItem()
    data class FileItem(val file: DuplicateFile, val isSelected: Boolean) : DupItem()
}

class DuplicateGroupAdapter(
    private val onToggleFile: (String) -> Unit,
    private val onToggleGroup: (String, Boolean) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<DupItem>()
    private val expandedHashes = mutableSetOf<String>()

    companion object { private const val VH_HEADER = 0; private const val VH_FILE = 1 }

    fun submitGroups(groups: List<DuplicateGroup>, selectedPaths: Set<String>) {
        items.clear()
        groups.forEach { group ->
            val selectedInGroup = group.files.count { it.path in selectedPaths }
            items.add(DupItem.GroupHeader(group, selectedInGroup))
            if (group.hash in expandedHashes) {
                group.files.forEach { items.add(DupItem.FileItem(it, it.path in selectedPaths)) }
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(p: Int) = if (items[p] is DupItem.GroupHeader) VH_HEADER else VH_FILE
    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, vt: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (vt == VH_HEADER) GroupVH(inf.inflate(R.layout.item_dup_header, parent, false))
               else FileVH(inf.inflate(R.layout.item_dup_file, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
        when (val item = items[pos]) {
            is DupItem.GroupHeader -> (holder as GroupVH).bind(item)
            is DupItem.FileItem    -> (holder as FileVH).bind(item)
        }
    }

    inner class GroupVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvCount: TextView  = v.findViewById(R.id.tvGroupCount)
        val tvSize: TextView   = v.findViewById(R.id.tvGroupSize)
        val tvWasted: TextView = v.findViewById(R.id.tvGroupWasted)
        val cbGroup: CheckBox  = v.findViewById(R.id.cbGroup)
        val tvExpand: TextView = v.findViewById(R.id.tvExpand)

        fun bind(item: DupItem.GroupHeader) {
            val g = item.group
            tvCount.text  = "${g.files.size} 个重复"
            tvSize.text   = FileUtils.formatSize(g.size)
            tvWasted.text = "浪费 ${FileUtils.formatSize(g.wastedBytes)}"

            val nonKeep = g.files.count { !it.isKeepCandidate }
            cbGroup.setOnCheckedChangeListener(null)
            cbGroup.isChecked = item.selected == nonKeep && nonKeep > 0
            cbGroup.setOnCheckedChangeListener { _, checked -> onToggleGroup(g.hash, checked) }

            val expanded = g.hash in expandedHashes
            tvExpand.text = if (expanded) "▲" else "▼"
            itemView.setOnClickListener {
                if (g.hash in expandedHashes) expandedHashes.remove(g.hash) else expandedHashes.add(g.hash)
                notifyDataSetChanged()
            }
        }
    }

    inner class FileVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView  = v.findViewById(R.id.tvFileName)
        val tvPath: TextView  = v.findViewById(R.id.tvFilePath)
        val tvDate: TextView  = v.findViewById(R.id.tvFileDate)
        val tvKeep: TextView  = v.findViewById(R.id.tvKeepBadge)
        val cb: CheckBox      = v.findViewById(R.id.cbFile)

        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

        fun bind(item: DupItem.FileItem) {
            val f = item.file
            tvName.text = f.name
            tvPath.text = f.path.substringBeforeLast('/')
            tvDate.text = sdf.format(Date(f.lastModified))
            tvKeep.visibility = if (f.isKeepCandidate) View.VISIBLE else View.GONE
            cb.setOnCheckedChangeListener(null)
            cb.isChecked = item.isSelected
            cb.isEnabled = !f.isKeepCandidate
            cb.setOnCheckedChangeListener { _, _ -> if (!f.isKeepCandidate) onToggleFile(f.path) }
            itemView.setOnClickListener { if (!f.isKeepCandidate) onToggleFile(f.path) }
        }
    }
}
