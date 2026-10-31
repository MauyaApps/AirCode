package com.mauyaapps.aircode

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mauyaapps.aircode.filesystem.DraftEntry
import com.mauyaapps.aircode.filesystem.DraftNode
import com.mauyaapps.aircode.filesystem.DraftTree
import com.mauyaapps.aircode.filesystem.TreeEntry
import com.mauyaapps.aircode.filesystem.WorkspaceException
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SavePicker(
    val node: DraftNode,
    val dir: File,
    val path: String,
    val canGoUp: Boolean,
    val folders: List<File>,
)

data class FilesUiState(
    val entries: List<TreeEntry> = emptyList(),
    val drafts: List<DraftEntry> = emptyList(),
    val picker: SavePicker? = null,
    val error: WorkspaceException.Reason? = null,
)

class FilesViewModel(application: Application) : AndroidViewModel(application) {

    private val workspace = (application as AirCodeApp).workspace
    private val drafts = (application as AirCodeApp).drafts

    private var expanded: Set<String> = emptySet()
    private var expandedDrafts: Set<Long> = emptySet()

    private val _state = MutableStateFlow(FilesUiState())
    val state: StateFlow<FilesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            workspace.ensureRoot()
            refresh()
        }
        viewModelScope.launch {
            drafts.nodes.collect { refreshDrafts() }
        }
    }

    fun onDraftToggle(id: Long) {
        expandedDrafts = if (id in expandedDrafts) expandedDrafts - id else expandedDrafts + id
        refreshDrafts()
    }

    fun onDraftCreate(parentId: Long?, name: String, isFolder: Boolean) {
        val result = if (isFolder) drafts.createFolder(parentId, name) else drafts.createFile(parentId, name)
        result.onSuccess { if (parentId != null) expandedDrafts = expandedDrafts + parentId }
            .onFailure { reportError(reasonOf(it)) }
        refreshDrafts()
    }

    fun onDraftRename(id: Long, newName: String) {
        drafts.rename(id, newName).onFailure { reportError(reasonOf(it)) }
    }

    fun onDraftDelete(id: Long) {
        drafts.delete(id)
    }

    fun onDraftSave(id: Long) {
        val node = drafts.find(id) ?: return
        viewModelScope.launch { openPicker(node, workspace.root) }
    }

    fun onPickerOpen(dir: File) {
        val node = _state.value.picker?.node ?: return
        viewModelScope.launch { openPicker(node, dir) }
    }

    fun onPickerUp() {
        val picker = _state.value.picker ?: return
        val parent = picker.dir.parentFile ?: return
        viewModelScope.launch { openPicker(picker.node, parent) }
    }

    fun onPickerDismiss() {
        _state.update { it.copy(picker = null) }
    }

    fun onPickerConfirm() {
        val picker = _state.value.picker ?: return
        viewModelScope.launch {
            workspace.saveDraft(picker.dir, picker.node)
                .onSuccess {
                    expanded = expanded + picker.dir.path
                    drafts.delete(picker.node.id)
                    _state.update { it.copy(picker = null) }
                }
                .onFailure { reportError(reasonOf(it)) }
            refresh()
        }
    }

    fun onEntryClick(entry: TreeEntry) {
        if (!entry.isDirectory) return
        val key = entry.file.path
        expanded = if (key in expanded) expanded - key else expanded + key
        viewModelScope.launch { refresh() }
    }

    fun onCreate(parent: File?, name: String, isFolder: Boolean) {
        val target = parent ?: workspace.root
        launchAction {
            val result = if (isFolder) {
                workspace.createFolder(target, name)
            } else {
                workspace.createFile(target, name)
            }
            if (result.isSuccess) expanded = expanded + target.path
            result
        }
    }

    fun onRename(file: File, newName: String) {
        launchAction { workspace.rename(file, newName) }
    }

    fun onDelete(file: File) {
        launchAction { workspace.delete(file) }
    }

    fun reportError(reason: WorkspaceException.Reason) {
        _state.update { it.copy(error = reason) }
    }

    fun onErrorShown() {
        _state.update { it.copy(error = null) }
    }

    private fun launchAction(action: suspend () -> Result<*>) {
        viewModelScope.launch {
            action().onFailure { e ->
                reportError(reasonOf(e))
            }
            refresh()
        }
    }

    private suspend fun openPicker(node: DraftNode, dir: File) {
        workspace.listFolders(dir)
            .onSuccess { folders ->
                val relative = if (dir == workspace.root) "" else dir.relativeTo(workspace.root).path
                val picker = SavePicker(node, dir, relative, canGoUp = dir != workspace.root, folders = folders)
                _state.update { it.copy(picker = picker) }
            }
            .onFailure { reportError(reasonOf(it)) }
    }

    private fun refreshDrafts() {
        val entries = DraftTree.flatten(drafts.nodes.value, expandedDrafts)
        _state.update { it.copy(drafts = entries) }
    }

    private fun reasonOf(e: Throwable): WorkspaceException.Reason =
        (e as? WorkspaceException)?.reason ?: WorkspaceException.Reason.IO_ERROR

    private suspend fun refresh() {
        val entries = workspace.loadTree(expanded)
        _state.update { it.copy(entries = entries) }
    }
}
