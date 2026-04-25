package com.fileleader.ui.clean

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fileleader.data.model.JunkFile
import com.fileleader.data.model.JunkType
import com.fileleader.domain.engine.CleanEngine
import com.fileleader.domain.engine.CleanResult
import com.fileleader.domain.engine.JunkScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CleanUiState(
    val isScanning: Boolean = false,
    val isCleaning: Boolean = false,
    val junkFiles: List<JunkFile> = emptyList(),
    val groupedJunk: Map<JunkType, List<JunkFile>> = emptyMap(),
    val cleanProgress: Int = 0,
    val cleanResult: CleanResult? = null,
    val selectedCount: Int = 0,
    val selectedBytes: Long = 0L,
    val error: String? = null
)

@HiltViewModel
class CleanViewModel @Inject constructor(
    private val junkScanner: JunkScanner,
    private val cleanEngine: CleanEngine
) : ViewModel() {

    private val _state = MutableStateFlow(CleanUiState())
    val state: StateFlow<CleanUiState> = _state.asStateFlow()

    private val _allFiles = mutableListOf<JunkFile>()

    fun scan() {
        if (_state.value.isScanning) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isScanning = true, error = null)
            _allFiles.clear()

            junkScanner.scan().collect { (_, files) ->
                _allFiles.clear()
                _allFiles.addAll(files)
                updateState()
            }

            _state.value = _state.value.copy(isScanning = false)
        }
    }

    fun selectAll(selected: Boolean) {
        _allFiles.forEachIndexed { i, f -> _allFiles[i] = f.copy(isSelected = selected) }
        updateState()
    }

    fun selectByType(type: JunkType, selected: Boolean) {
        _allFiles.forEachIndexed { i, f ->
            if (f.type == type) _allFiles[i] = f.copy(isSelected = selected)
        }
        updateState()
    }

    fun toggleFile(path: String) {
        val idx = _allFiles.indexOfFirst { it.path == path }
        if (idx >= 0) {
            _allFiles[idx] = _allFiles[idx].copy(isSelected = !_allFiles[idx].isSelected)
            updateState()
        }
    }

    fun cleanSelected() {
        val toClean = _allFiles.filter { it.isSelected }
        if (toClean.isEmpty()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isCleaning = true)
            cleanEngine.cleanJunkFiles(toClean).collect { (done, result) ->
                val pct = (done * 100 / toClean.size)
                _state.value = _state.value.copy(cleanProgress = pct, cleanResult = result)
            }
            // Remove cleaned files from list
            _allFiles.removeAll { it.isSelected }
            updateState()
            _state.value = _state.value.copy(isCleaning = false)
        }
    }

    private fun updateState() {
        val selected = _allFiles.filter { it.isSelected }
        _state.value = _state.value.copy(
            junkFiles = _allFiles.toList(),
            groupedJunk = _allFiles.groupBy { it.type },
            selectedCount = selected.size,
            selectedBytes = selected.sumOf { it.size }
        )
    }
}
