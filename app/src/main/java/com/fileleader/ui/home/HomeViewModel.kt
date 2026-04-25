package com.fileleader.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fileleader.domain.engine.DuplicateScanner
import com.fileleader.domain.engine.JunkScanner
import com.fileleader.domain.engine.StorageAnalyzer
import com.fileleader.data.model.*
import com.fileleader.util.PermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val permissionMode: PermissionMode = PermissionMode.NORMAL,
    val storageInfo: StorageInfo? = null,
    val junkFiles: List<JunkFile> = emptyList(),
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val scanProgress: ScanProgress = ScanProgress(ScanPhase.IDLE, 0, 0),
    val isScanning: Boolean = false,
    val totalJunkBytes: Long = 0L,
    val totalDuplicateBytes: Long = 0L,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val junkScanner: JunkScanner,
    private val duplicateScanner: DuplicateScanner,
    private val storageAnalyzer: StorageAnalyzer,
    private val permissionManager: PermissionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val mode = permissionManager.detectMode()
            _uiState.value = _uiState.value.copy(permissionMode = mode)
            loadStorageInfo()
        }
    }

    fun startFullScan() {
        if (_uiState.value.isScanning) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true, error = null)
            scanJunk()
            scanDuplicates()
            loadStorageInfo()
            _uiState.value = _uiState.value.copy(
                isScanning = false,
                scanProgress = ScanProgress(ScanPhase.DONE, 100, 100)
            )
        }
    }

    private suspend fun scanJunk() {
        junkScanner.scan().collect { (progress, files) ->
            _uiState.value = _uiState.value.copy(
                scanProgress = progress,
                junkFiles = files,
                totalJunkBytes = files.sumOf { it.size }
            )
        }
    }

    private suspend fun scanDuplicates() {
        duplicateScanner.scan().collect { (progress, groups) ->
            _uiState.value = _uiState.value.copy(
                scanProgress = progress,
                duplicateGroups = groups,
                totalDuplicateBytes = groups.sumOf { it.wastedBytes }
            )
        }
    }

    private suspend fun loadStorageInfo() {
        try {
            val info = storageAnalyzer.analyze()
            _uiState.value = _uiState.value.copy(storageInfo = info)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = e.message)
        }
    }

    fun refreshPermissionMode() {
        viewModelScope.launch {
            val mode = permissionManager.detectMode()
            _uiState.value = _uiState.value.copy(permissionMode = mode)
        }
    }
}
