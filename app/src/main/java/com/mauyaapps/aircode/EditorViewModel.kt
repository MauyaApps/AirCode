package com.mauyaapps.aircode

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mauyaapps.aircode.editor.EditorDocument
import com.mauyaapps.aircode.editor.languageForFile
import com.mauyaapps.aircode.filesystem.DraftFile
import com.mauyaapps.aircode.filesystem.DraftTree
import com.mauyaapps.aircode.filesystem.WorkspaceException
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorTab(
    val name: String,
    val file: File?,
    val draftId: Long?,
    val document: EditorDocument,
    val usesCrlf: Boolean,
    val isDirty: Boolean = false,
) {
    val id: Long get() = document.id
}

data class EditorUiState(
    val tabs: List<EditorTab> = emptyList(),
    val activeId: Long? = null,
    val error: WorkspaceException.Reason? = null,
) {
    val active: EditorTab? get() = tabs.firstOrNull { it.id == activeId }
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AirCodeApp
    private val workspace = app.workspace
    private val drafts = app.drafts

    private var nextTabId = 0L

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            drafts.nodes.collect { nodes ->
                _state.update { state ->
                    val tabs = state.tabs.mapNotNull { tab ->
                        val id = tab.draftId ?: return@mapNotNull tab
                        val node = DraftTree.find(nodes, id) as? DraftFile ?: return@mapNotNull null
                        if (node.name == tab.name) tab else tab.copy(name = node.name)
                    }
                    if (tabs == state.tabs) state else state.copy(tabs = tabs, activeId = activeAfter(state, tabs))
                }
            }
        }
    }

    fun open(file: File, onError: (WorkspaceException.Reason) -> Unit, onOpened: () -> Unit) {
        if (activateOpened { it.file == file }) {
            onOpened()
            return
        }
        viewModelScope.launch {
            workspace.readText(file)
                .onSuccess { text ->
                    if (!activateOpened { it.file == file }) {
                        addTab(name = file.name, file = file, draftId = null, text = text)
                    }
                    onOpened()
                }
                .onFailure { onError(reasonOf(it)) }
        }
    }

    fun openDraft(node: DraftFile, onOpened: () -> Unit) {
        if (!activateOpened { it.draftId == node.id }) {
            addTab(name = node.name, file = null, draftId = node.id, text = node.text)
        }
        onOpened()
    }

    fun onSelectTab(id: Long) {
        _state.update { if (it.tabs.any { tab -> tab.id == id }) it.copy(activeId = id) else it }
    }

    fun onChanged() {
        _state.update { state ->
            if (state.active?.isDirty != false) {
                state
            } else {
                state.copy(tabs = state.tabs.map { if (it.id == state.activeId) it.copy(isDirty = true) else it })
            }
        }
    }

    fun onSave(tabId: Long, text: String, thenClose: Boolean) {
        val tab = _state.value.tabs.firstOrNull { it.id == tabId } ?: return
        if (tab.draftId != null) {
            drafts.setText(tab.draftId, text)
            markSaved(tabId)
            if (thenClose) onCloseTab(tabId)
            return
        }
        val file = tab.file ?: return
        val content = if (tab.usesCrlf) text.replace("\n", "\r\n") else text
        viewModelScope.launch {
            workspace.writeText(file, content)
                .onSuccess {
                    markSaved(tabId)
                    if (thenClose) onCloseTab(tabId)
                }
                .onFailure { e -> _state.update { it.copy(error = reasonOf(e)) } }
        }
    }

    fun onCloseTab(id: Long) {
        _state.update { state ->
            val index = state.tabs.indexOfFirst { it.id == id }
            if (index < 0) return@update state
            val tabs = state.tabs.filterNot { it.id == id }
            val activeId = if (state.activeId != id) {
                state.activeId
            } else {
                (tabs.getOrNull(index) ?: tabs.lastOrNull())?.id
            }
            state.copy(tabs = tabs, activeId = activeId)
        }
    }

    fun onPathChanged(path: File) {
        val prefix = path.path + File.separator
        _state.value.tabs
            .filter { tab -> tab.file?.path.let { it != null && (it == path.path || it.startsWith(prefix)) } }
            .forEach { onCloseTab(it.id) }
    }

    fun onErrorShown() {
        _state.update { it.copy(error = null) }
    }

    private fun markSaved(tabId: Long) {
        _state.update { state ->
            state.copy(tabs = state.tabs.map { if (it.id == tabId) it.copy(isDirty = false) else it })
        }
    }

    private fun activateOpened(matches: (EditorTab) -> Boolean): Boolean {
        val tab = _state.value.tabs.firstOrNull(matches) ?: return false
        _state.update { it.copy(activeId = tab.id) }
        return true
    }

    private fun addTab(name: String, file: File?, draftId: Long?, text: String) {
        val tab = EditorTab(
            name = name,
            file = file,
            draftId = draftId,
            document = EditorDocument(
                id = nextTabId++,
                text = text,
                language = languageForFile(name),
            ),
            usesCrlf = "\r\n" in text,
        )
        _state.update { it.copy(tabs = it.tabs + tab, activeId = tab.id) }
    }

    private fun activeAfter(state: EditorUiState, tabs: List<EditorTab>): Long? =
        if (tabs.any { it.id == state.activeId }) state.activeId else tabs.lastOrNull()?.id

    private fun reasonOf(e: Throwable): WorkspaceException.Reason =
        (e as? WorkspaceException)?.reason ?: WorkspaceException.Reason.IO_ERROR
}
