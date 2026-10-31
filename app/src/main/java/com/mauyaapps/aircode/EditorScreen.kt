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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mauyaapps.aircode.editor.EditorController
import com.mauyaapps.aircode.editor.EditorRenderMode
import com.mauyaapps.aircode.editor.EditorView
import com.mauyaapps.aircode.editor.SymbolBar
import com.mauyaapps.aircode.editor.previewKindForFile
import com.mauyaapps.aircode.terminal.R as TerminalR
import com.mauyaapps.aircode.terminal.RunStart
import com.mauyaapps.aircode.terminal.TerminalManager
import com.mauyaapps.aircode.terminal.TerminalPanel
import com.mauyaapps.aircode.theme.LocalGlassStyle
import com.mauyaapps.aircode.theme.ThemeMode
import com.mauyaapps.aircode.theme.glassFrame

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    state: EditorUiState,
    terminal: TerminalManager,
    darkTheme: Boolean,
    visible: Boolean,
    autocompleteEnabled: Boolean,
    renderMode: EditorRenderMode,
    onChanged: () -> Unit,
    onSave: (tabId: Long, text: String, thenClose: Boolean, onSaved: () -> Unit) -> Unit,
    onSelectTab: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
    onShowFiles: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onOpenThemeSettings: () -> Unit,
    onAutocompleteChange: (Boolean) -> Unit,
    onRenderModeChange: (EditorRenderMode) -> Unit,
    onErrorShown: () -> Unit,
) {
    val controller = remember { EditorController() }
    val glass = LocalGlassStyle.current
    var askSaveFor by remember { mutableStateOf<EditorTab?>(null) }
    var diagnosticsText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(visible) { if (visible) controller.refresh() }
    var askAutocomplete by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var paletteOpen by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<PreviewState?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    var terminalOpen by remember { mutableStateOf(false) }
    var terminalExpanded by remember { mutableStateOf(false) }
    var terminalInputFocused by remember { mutableStateOf(false) }
    var packagesOpen by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<Int?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val findTitle = stringResource(R.string.find_in_file)
    val previewTitle = stringResource(R.string.cmd_preview)
    val terminalToggleTitle = stringResource(
        if (terminalOpen) R.string.menu_terminal_hide else R.string.menu_terminal_show,
    )
    val runTitle = stringResource(R.string.menu_run)
    val packagesTitle = stringResource(R.string.menu_packages)

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
    val documents = remember(state.tabs) { state.tabs.map { it.document } }
    val fileName = active?.name.orEmpty()
    val previewKind = active?.let { previewKindForFile(it.name) }
    val runnable = remember(active?.name, menuOpen, paletteOpen) { active?.let { terminal.canRun(it.name) } == true }

    fun save(tab: EditorTab, thenClose: Boolean) {
        controller.requestText(tab.id) { text -> onSave(tab.id, text, thenClose) {} }
    }

    fun requestClose(tabId: Long) {
        val tab = state.tabs.firstOrNull { it.id == tabId } ?: return
        if (tab.isDirty) askSaveFor = tab else onCloseTab(tabId)
    }

    fun showFiles() {
        state.tabs.filter { it.isDirty && it.draftId != null }.forEach { save(it, thenClose = false) }
        onShowFiles()
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
        val kind = previewKindForFile(tab.name) ?: return
        keyboard?.hide()
        controller.requestText(tab.id) { text -> preview = PreviewState(kind, tab.name, text) }
    }

    val noticeText = notice?.let { stringResource(it) }
    LaunchedEffect(noticeText) {
        if (noticeText != null) {
            snackbarHostState.showSnackbar(noticeText)
            notice = null
        }
    }

    fun startRun(file: java.io.File) {
        terminalOpen = true
        notice = when (terminal.run(file)) {
            RunStart.STARTED -> null
            RunStart.UNSUPPORTED -> TerminalR.string.run_unsupported
            RunStart.NEEDS_ENVIRONMENT -> TerminalR.string.run_needs_environment
            RunStart.OUTSIDE_WORKSPACE -> TerminalR.string.run_outside_workspace
        }
    }

    fun runActive() {
        val tab = active ?: return
        val file = tab.file
        if (file == null) {
            notice = TerminalR.string.run_save_first
            return
        }
        keyboard?.hide()
        if (tab.isDirty) {
            controller.requestText(tab.id) { text -> onSave(tab.id, text, false) { startRun(file) } }
        } else {
            startRun(file)
        }
    }

    LaunchedEffect(state.activeId) { if (searchOpen) closeSearch() }

    BackHandler(enabled = visible && active != null) { active?.let { requestClose(it.id) } }
    BackHandler(enabled = visible && searchOpen) { closeSearch() }
    BackHandler(enabled = visible && terminalOpen) { terminalOpen = false }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    colors = if (glass.enabled) {
                        TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    } else {
                        TopAppBarDefaults.topAppBarColors()
                    },
                    title = {
                        Text(
                            text = if (active?.isDirty == true) "$fileName •" else fileName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        TextButton(onClick = ::showFiles) { Text(FILES_GLYPH) }
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
                                if (runnable) {
                                    DropdownMenuItem(
                                        text = { Text(runTitle) },
                                        onClick = {
                                            menuOpen = false
                                            runActive()
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(terminalToggleTitle) },
                                    onClick = {
                                        menuOpen = false
                                        terminalOpen = !terminalOpen
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(packagesTitle) },
                                    onClick = {
                                        menuOpen = false
                                        packagesOpen = true
                                    },
                                )
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
                        result = { controller.searchResult },
                        onNext = controller::findNext,
                        onPrevious = controller::findPrevious,
                        onClose = ::closeSearch,
                    )
                }
                EditorView(
                    controller = controller,
                    documents = documents,
                    activeId = state.activeId,
                    darkTheme = darkTheme,
                    renderMode = renderMode,
                    autocompleteEnabled = autocompleteEnabled,
                    onChanged = onChanged,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(
                            if (glass.enabled) {
                                Modifier
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .glassFrame(RoundedCornerShape(20.dp), glass.dark)
                            } else {
                                Modifier
                            },
                        ),
                )
                if (terminalOpen) {
                    TerminalPanel(
                        manager = terminal,
                        expanded = terminalExpanded,
                        onToggleExpanded = { terminalExpanded = !terminalExpanded },
                        onHide = { terminalOpen = false },
                        onInputFocusChange = { terminalInputFocused = it },
                        active = visible,
                        modifier = Modifier
                            .weight(if (terminalExpanded) 2f else 1f)
                            .fillMaxWidth(),
                    )
                }
                if (!searchOpen && !(terminalOpen && terminalInputFocused)) ImeSymbolBar(controller)
                CursorStatus(controller)
            }
        }

        preview?.let { current ->
            Surface(modifier = Modifier.fillMaxSize()) {
                PreviewScreen(
                    preview = current,
                    darkTheme = darkTheme,
                    onClose = { preview = null },
                )
            }
        }
    }

    if (paletteOpen) {
        val saveTitle = stringResource(R.string.cmd_save)
        val closeTabTitle = stringResource(R.string.cmd_close_tab)
        val showFilesTitle = stringResource(R.string.cmd_show_files)
        val themeSettingsTitle = stringResource(R.string.cmd_theme_settings)
        val themeSystemTitle = stringResource(R.string.cmd_theme_system)
        val themeLightTitle = stringResource(R.string.cmd_theme_light)
        val themeDarkTitle = stringResource(R.string.cmd_theme_dark)
        val autocompleteTitle = stringResource(
            if (autocompleteEnabled) R.string.cmd_autocomplete_off else R.string.cmd_autocomplete_on,
        )
        val nextRender = if (renderMode == EditorRenderMode.SOFTWARE) EditorRenderMode.HARDWARE else EditorRenderMode.SOFTWARE
        val renderTitle = stringResource(
            if (nextRender == EditorRenderMode.SOFTWARE) R.string.cmd_render_software else R.string.cmd_render_hardware,
        )
        val refreshTitle = stringResource(R.string.cmd_editor_refresh)
        val diagnoseTitle = stringResource(R.string.cmd_editor_diagnose)
        val gotoTabFormat = R.string.cmd_goto_tab
        val commands = buildList {
            if (active != null) {
                if (active.isDirty) add(PaletteCommand(saveTitle) { save(active, thenClose = false) })
                add(PaletteCommand(findTitle) { openSearch() })
                if (previewKind != null) add(PaletteCommand(previewTitle) { openPreview() })
                if (runnable) add(PaletteCommand(runTitle) { runActive() })
                add(PaletteCommand(closeTabTitle) { requestClose(active.id) })
            }
            add(PaletteCommand(terminalToggleTitle) { terminalOpen = !terminalOpen })
            add(PaletteCommand(packagesTitle) { packagesOpen = true })
            add(PaletteCommand(showFilesTitle) { showFiles() })
            add(PaletteCommand(themeSettingsTitle) { onOpenThemeSettings() })
            add(PaletteCommand(themeSystemTitle) { onThemeSelected(ThemeMode.SYSTEM) })
            add(PaletteCommand(themeLightTitle) { onThemeSelected(ThemeMode.LIGHT) })
            add(PaletteCommand(themeDarkTitle) { onThemeSelected(ThemeMode.DARK) })
            add(
                PaletteCommand(autocompleteTitle) {
                    if (autocompleteEnabled) onAutocompleteChange(false) else askAutocomplete = true
                },
            )
            add(PaletteCommand(renderTitle) { onRenderModeChange(nextRender) })
            add(PaletteCommand(refreshTitle) { controller.refresh() })
            add(PaletteCommand(diagnoseTitle) { controller.diagnose { diagnosticsText = it } })
            state.tabs.filter { it.id != state.activeId }.forEach { tab ->
                val title = stringResource(gotoTabFormat, tab.name)
                add(PaletteCommand(title) { onSelectTab(tab.id) })
            }
        }
        CommandPalette(commands = commands, onDismiss = { paletteOpen = false })
    }

    diagnosticsText?.let { text ->
        AlertDialog(
            onDismissRequest = { diagnosticsText = null },
            title = { Text(stringResource(R.string.diagnostics_title)) },
            text = {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { diagnosticsText = null }) { Text(stringResource(R.string.close)) }
            },
        )
    }

    if (packagesOpen) {
        PackagesDialog(
            onDismiss = { packagesOpen = false },
            onInstall = { names ->
                packagesOpen = false
                terminalOpen = true
                if (!terminal.installPackages(names)) notice = TerminalR.string.run_needs_environment
            },
        )
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
            text = { Text(stringResource(R.string.unsaved_text, tab.name)) },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImeSymbolBar(controller: EditorController) {
    if (WindowInsets.isImeVisible) SymbolBar(controller)
}

@Composable
private fun CursorStatus(controller: EditorController) {
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

@Composable
private fun PackagesDialog(onDismiss: () -> Unit, onInstall: (List<String>) -> Unit) {
    var names by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(TerminalR.string.packages_title)) },
        text = {
            Column {
                Text(stringResource(TerminalR.string.packages_text))
                TextField(
                    value = names,
                    onValueChange = { names = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(TerminalR.string.packages_hint)) },
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onInstall(names.split(' ', '\n', '\t').filter { it.isNotBlank() }) },
                enabled = names.isNotBlank(),
            ) { Text(stringResource(TerminalR.string.packages_install)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(TerminalR.string.packages_cancel)) }
        },
    )
}
