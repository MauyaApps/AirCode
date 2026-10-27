package com.mauyaapps.aircode

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mauyaapps.aircode.filesystem.TreeEntry
import com.mauyaapps.aircode.filesystem.WorkspaceException
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Состояние дерева файлов. [error] — причина последней неудачной операции. */
data class FilesUiState(
    val entries: List<TreeEntry> = emptyList(),
    val error: WorkspaceException.Reason? = null,
)

/** Логика дерева файлов: раскрытие папок, создание, переименование, удаление. */
class FilesViewModel(application: Application) : AndroidViewModel(application) {

    private val workspace = (application as AirCodeApp).workspace

    private var expanded: Set<String> = emptySet()

    private val _state = MutableStateFlow(FilesUiState())
    val state: StateFlow<FilesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            workspace.ensureRoot()
            refresh()
        }
    }

    /** Раскрывает или сворачивает папку. Открытие файлов обрабатывается отдельно. */
    fun onEntryClick(entry: TreeEntry) {
        if (!entry.isDirectory) return
        val key = entry.file.path
        expanded = if (key in expanded) expanded - key else expanded + key
        viewModelScope.launch { refresh() }
    }

    /** Создаёт файл или папку внутри [parent]; null означает корень рабочей папки. */
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

    /** Показывает ошибку, возникшую вне дерева (например, при открытии файла). */
    fun reportError(reason: WorkspaceException.Reason) {
        _state.update { it.copy(error = reason) }
    }

    fun onErrorShown() {
        _state.update { it.copy(error = null) }
    }

    private fun launchAction(action: suspend () -> Result<*>) {
        viewModelScope.launch {
            action().onFailure { e ->
                reportError((e as? WorkspaceException)?.reason ?: WorkspaceException.Reason.IO_ERROR)
            }
            refresh()
        }
    }

    private suspend fun refresh() {
        val entries = workspace.loadTree(expanded)
        _state.update { it.copy(entries = entries) }
    }
}
