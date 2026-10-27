package com.mauyaapps.aircode

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mauyaapps.aircode.editor.EditorController
import com.mauyaapps.aircode.editor.EditorView
import com.mauyaapps.aircode.editor.SymbolBar
import com.mauyaapps.aircode.editor.previewKindForFile
import com.mauyaapps.aircode.theme.ThemeMode

/**
 * Экран редактора: верхняя панель, вкладки, поиск, редактор и строка состояния. Пока открыта
 * хотя бы одна вкладка, экран остаётся в композиции (поверх него может лежать список файлов),
 * чтобы WebView не уничтожался и несохранённые правки не терялись. [visible] говорит, виден ли он.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    state: EditorUiState,
    darkTheme: Boolean,
    visible: Boolean,
    autocompleteEnabled: Boolean,
    onChanged: () -> Unit,
    onSave: (tabId: Long, text: String, thenClose: Boolean) -> Unit,
    onSelectTab: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
    onShowFiles: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onAutocompleteChange: (Boolean) -> Unit,
    onErrorShown: () -> Unit,
) {
    val controller = remember { EditorController() }
    // Вкладка, закрытие которой ждёт ответа на вопрос о сохранении
    var askSaveFor by remember { mutableStateOf<EditorTab?>(null) }
    var askAutocomplete by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var paletteOpen by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<PreviewState?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val errorMessage = state.error?.let { stringResource(workspaceErrorRes(it)) }
    LaunchedEffect(state.error) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onErrorShown()
        }
    }

    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(visible) { if (!visible) keyboard?.hide() }

    val active = state.active
    val fileName = active?.file?.name.orEmpty()
    val previewKind = active?.let { previewKindForFile(it.file.name) }

    fun save(tab: EditorTab, thenClose: Boolean) {
        controller.requestText(tab.id) { text -> onSave(tab.id, text, thenClose) }
    }

    fun requestClose(tabId: Long) {
        val tab = state.tabs.firstOrNull { it.id == tabId } ?: return
        if (tab.isDirty) askSaveFor = tab else onCloseTab(tabId)
    }

    fun openSearch() {
        query = ""
        controller.clearSearch()
        searchOpen = true
    }

    fun closeSearch() {
        searchOpen = false
        controller.clearSearch()
    }

    fun openPreview() {
        val tab = active ?: return
        val kind = previewKindForFile(tab.file.name) ?: return
        keyboard?.hide()
        controller.requestText(tab.id) { text -> preview = PreviewState(kind, tab.file.name, text) }
    }

    // Поиск относится к одной вкладке, поэтому при переключении закрывается
    LaunchedEffect(state.activeId) { if (searchOpen) closeSearch() }

    // Порядок важен: обработчик, объявленный ниже, срабатывает раньше
    // Назад закрывает активную вкладку, а когда закрыта последняя, открывается список файлов
    BackHandler(enabled = visible && active != null) { active?.let { requestClose(it.id) } }
    BackHandler(enabled = visible && searchOpen) { closeSearch() }

    val saveTitle = stringResource(R.string.cmd_save)
    val findTitle = stringResource(R.string.find_in_file)
    val closeTabTitle = stringResource(R.string.cmd_close_tab)
    val showFilesTitle = stringResource(R.string.cmd_show_files)
    val previewTitle = stringResource(R.string.cmd_preview)
    val themeSystemTitle = stringResource(R.string.cmd_theme_system)
    val themeLightTitle = stringResource(R.string.cmd_theme_light)
    val themeDarkTitle = stringResource(R.string.cmd_theme_dark)
    val autocompleteTitle = stringResource(
        if (autocompleteEnabled) R.string.cmd_autocomplete_off else R.string.cmd_autocomplete_on,
    )
    val gotoTabFormat = R.string.cmd_goto_tab
    val commands = buildList {
        if (active != null) {
            if (active.isDirty) add(PaletteCommand(saveTitle) { save(active, thenClose = false) })
            add(PaletteCommand(findTitle) { openSearch() })
            if (previewKind != null) add(PaletteCommand(previewTitle) { openPreview() })
            add(PaletteCommand(closeTabTitle) { requestClose(active.id) })
        }
        add(PaletteCommand(showFilesTitle) { onShowFiles() })
        add(PaletteCommand(themeSystemTitle) { onThemeSelected(ThemeMode.SYSTEM) })
        add(PaletteCommand(themeLightTitle) { onThemeSelected(ThemeMode.LIGHT) })
        add(PaletteCommand(themeDarkTitle) { onThemeSelected(ThemeMode.DARK) })
        add(
            PaletteCommand(autocompleteTitle) {
                // Включение требует согласия пользователя, выключение сразу
                if (autocompleteEnabled) onAutocompleteChange(false) else askAutocomplete = true
            },
        )
        state.tabs.filter { it.id != state.activeId }.forEach { tab ->
            val title = stringResource(gotoTabFormat, tab.file.name)
            add(PaletteCommand(title) { onSelectTab(tab.id) })
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (active?.isDirty == true) "$fileName •" else fileName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        TextButton(onClick = onShowFiles) { Text(FILES_GLYPH) }
                    },
                    actions = {
                        TextButton(
                            onClick = { active?.let { save(it, thenClose = false) } },
                            enabled = active?.isDirty == true,
                        ) {
                            Text(stringResource(R.string.save))
                        }
                        Box {
                            TextButton(onClick = { menuOpen = true }) { Text(MORE_GLYPH) }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(findTitle) },
                                    onClick = {
                                        menuOpen = false
                                        openSearch()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.commands)) },
                                    onClick = {
                                        menuOpen = false
                                        paletteOpen = true
                                    },
                                )
                                if (previewKind != null) {
                                    DropdownMenuItem(
                                        text = { Text(previewTitle) },
                                        onClick = {
                                            menuOpen = false
                                            openPreview()
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding()
                    .fillMaxSize(),
            ) {
                EditorTabBar(
                    tabs = state.tabs,
                    activeId = state.activeId,
                    onSelect = onSelectTab,
                    onClose = ::requestClose,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (searchOpen) {
                    FindBar(
                        query = query,
                        onQueryChange = {
                            query = it
                            controller.find(it, caseSensitive)
                        },
                        caseSensitive = caseSensitive,
                        onCaseSensitiveChange = {
                            caseSensitive = it
                            controller.find(query, it)
                        },
                        result = controller.searchResult,
                        onNext = controller::findNext,
                        onPrevious = controller::findPrevious,
                        onClose = ::closeSearch,
                    )
                }
                EditorView(
                    controller = controller,
                    documents = state.tabs.map { it.document },
                    activeId = state.activeId,
                    darkTheme = darkTheme,
                    autocompleteEnabled = autocompleteEnabled,
                    onChanged = onChanged,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                // В строке поиска символы панели вставлялись бы в текст, а не в запрос
                if (WindowInsets.isImeVisible && !searchOpen) SymbolBar(controller)
                Text(
                    text = stringResource(
                        R.string.cursor_position,
                        controller.cursor.line,
                        controller.cursor.column,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }

        preview?.let { current ->
            PreviewScreen(
                preview = current,
                darkTheme = darkTheme,
                onClose = { preview = null },
            )
        }
    }

    if (paletteOpen) {
        CommandPalette(commands = commands, onDismiss = { paletteOpen = false })
    }

    if (askAutocomplete) {
        AlertDialog(
            onDismissRequest = { askAutocomplete = false },
            title = { Text(stringResource(R.string.autocomplete_title)) },
            text = { Text(stringResource(R.string.autocomplete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    askAutocomplete = false
                    onAutocompleteChange(true)
                }) { Text(stringResource(R.string.enable)) }
            },
            dismissButton = {
                TextButton(onClick = { askAutocomplete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    askSaveFor?.let { tab ->
        AlertDialog(
            onDismissRequest = { askSaveFor = null },
            title = { Text(stringResource(R.string.unsaved_title)) },
            text = { Text(stringResource(R.string.unsaved_text, tab.file.name)) },
            confirmButton = {
                TextButton(onClick = {
                    askSaveFor = null
                    save(tab, thenClose = true)
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        askSaveFor = null
                        onCloseTab(tab.id)
                    }) { Text(stringResource(R.string.discard)) }
                    TextButton(onClick = { askSaveFor = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            },
        )
    }
}

private const val FILES_GLYPH = "☰"
private const val MORE_GLYPH = "⋮"
