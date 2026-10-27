package com.mauyaapps.aircode

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mauyaapps.aircode.editor.EditorDocument
import com.mauyaapps.aircode.editor.languageForFile
import com.mauyaapps.aircode.filesystem.WorkspaceException
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Открытая вкладка. [document].id совпадает с id вкладки.
 * CodeMirror приводит все переводы строк к \n, поэтому исходный формат запоминаем в [usesCrlf].
 */
data class EditorTab(
    val file: File,
    val document: EditorDocument,
    val usesCrlf: Boolean,
    val isDirty: Boolean = false,
) {
    val id: Long get() = document.id
}

/** Состояние редактора. Пока [tabs] пуст, редактор закрыт. */
data class EditorUiState(
    val tabs: List<EditorTab> = emptyList(),
    val activeId: Long? = null,
    val error: WorkspaceException.Reason? = null,
) {
    val active: EditorTab? get() = tabs.firstOrNull { it.id == activeId }
}

/** Вкладки, открытие и сохранение файлов. Сам текст живёт в WebView до момента сохранения. */
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val workspace = (application as AirCodeApp).workspace

    private var nextTabId = 0L

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    /**
     * Открывает файл во вкладке или переключается на уже открытую. После успеха вызывается
     * [onOpened], при неудаче причина передаётся в [onError].
     */
    fun open(file: File, onError: (WorkspaceException.Reason) -> Unit, onOpened: () -> Unit) {
        if (activateOpened(file)) {
            onOpened()
            return
        }
        viewModelScope.launch {
            workspace.readText(file)
                .onSuccess { text ->
                    // Пока файл читался, его могли открыть ещё раз
                    if (!activateOpened(file)) addTab(file, text)
                    onOpened()
                }
                .onFailure { onError(reasonOf(it)) }
        }
    }

    fun onSelectTab(id: Long) {
        _state.update { if (it.tabs.any { tab -> tab.id == id }) it.copy(activeId = id) else it }
    }

    /** Помечает активную вкладку изменённой. */
    fun onChanged() {
        _state.update { state ->
            state.copy(tabs = state.tabs.map { if (it.id == state.activeId) it.copy(isDirty = true) else it })
        }
    }

    /** Сохраняет [text] во вкладку [tabId]; при [thenClose] после успеха закрывает её. */
    fun onSave(tabId: Long, text: String, thenClose: Boolean) {
        val tab = _state.value.tabs.firstOrNull { it.id == tabId } ?: return
        val content = if (tab.usesCrlf) text.replace("\n", "\r\n") else text
        viewModelScope.launch {
            workspace.writeText(tab.file, content)
                .onSuccess {
                    _state.update { state ->
                        state.copy(tabs = state.tabs.map { if (it.id == tabId) it.copy(isDirty = false) else it })
                    }
                    if (thenClose) onCloseTab(tabId)
                }
                .onFailure { e -> _state.update { it.copy(error = reasonOf(e)) } }
        }
    }

    /** Закрывает вкладку; активной становится соседняя справа, а если её нет, то слева. */
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

    /** Закрывает вкладки файла [path] и всего, что лежит внутри него (при удалении или переименовании). */
    fun onPathChanged(path: File) {
        val prefix = path.path + File.separator
        _state.value.tabs
            .filter { it.file.path == path.path || it.file.path.startsWith(prefix) }
            .forEach { onCloseTab(it.id) }
    }

    fun onErrorShown() {
        _state.update { it.copy(error = null) }
    }

    /** Переключается на вкладку файла, если он уже открыт. */
    private fun activateOpened(file: File): Boolean {
        val tab = _state.value.tabs.firstOrNull { it.file == file } ?: return false
        _state.update { it.copy(activeId = tab.id) }
        return true
    }

    private fun addTab(file: File, text: String) {
        val tab = EditorTab(
            file = file,
            document = EditorDocument(
                id = nextTabId++,
                text = text,
                language = languageForFile(file.name),
            ),
            usesCrlf = "\r\n" in text,
        )
        _state.update { it.copy(tabs = it.tabs + tab, activeId = tab.id) }
    }

    private fun reasonOf(e: Throwable): WorkspaceException.Reason =
        (e as? WorkspaceException)?.reason ?: WorkspaceException.Reason.IO_ERROR
}
