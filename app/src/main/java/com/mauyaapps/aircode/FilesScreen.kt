package com.mauyaapps.aircode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mauyaapps.aircode.filesystem.TreeEntry
import com.mauyaapps.aircode.theme.ThemeMode
import java.io.File

/** Открытое диалоговое окно экрана файлов. */
private sealed interface FilesDialog {
    /** Новый файл или папка; [parent] = null означает корень. */
    data class NewItem(val parent: File?, val isFolder: Boolean) : FilesDialog
    data class Rename(val file: File) : FilesDialog
    data class Delete(val file: File) : FilesDialog
    data object Theme : FilesDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    state: FilesUiState,
    themeMode: ThemeMode,
    listState: LazyListState,
    onEntryClick: (TreeEntry) -> Unit,
    onCreate: (parent: File?, name: String, isFolder: Boolean) -> Unit,
    onRename: (File, String) -> Unit,
    onDelete: (File) -> Unit,
    onErrorShown: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
) {
    var dialog by remember { mutableStateOf<FilesDialog?>(null) }
    var topMenuOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val errorMessage = state.error?.let { stringResource(workspaceErrorRes(it)) }
    LaunchedEffect(state.error) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onErrorShown()
        }
    }

    val crashMessage = stringResource(R.string.debug_crash_message)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    Box {
                        TextButton(onClick = { topMenuOpen = true }) { Text(MENU_GLYPH) }
                        DropdownMenu(
                            expanded = topMenuOpen,
                            onDismissRequest = { topMenuOpen = false },
                        ) {
                            MenuItem(R.string.new_file) {
                                topMenuOpen = false
                                dialog = FilesDialog.NewItem(parent = null, isFolder = false)
                            }
                            MenuItem(R.string.new_folder) {
                                topMenuOpen = false
                                dialog = FilesDialog.NewItem(parent = null, isFolder = true)
                            }
                            MenuItem(R.string.theme_section) {
                                topMenuOpen = false
                                dialog = FilesDialog.Theme
                            }
                            // Проверка экрана краша доступна только в debug-сборке
                            if (BuildConfig.DEBUG) {
                                MenuItem(R.string.debug_crash) {
                                    topMenuOpen = false
                                    throw IllegalStateException(crashMessage)
                                }
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.files_empty))
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.padding(padding)) {
                items(state.entries, key = { it.file.path }) { entry ->
                    TreeRow(
                        entry = entry,
                        onClick = { onEntryClick(entry) },
                        onNewFile = { dialog = FilesDialog.NewItem(entry.file, isFolder = false) },
                        onNewFolder = { dialog = FilesDialog.NewItem(entry.file, isFolder = true) },
                        onRename = { dialog = FilesDialog.Rename(entry.file) },
                        onDelete = { dialog = FilesDialog.Delete(entry.file) },
                    )
                }
            }
        }
    }

    when (val current = dialog) {
        is FilesDialog.NewItem -> NameDialog(
            title = stringResource(if (current.isFolder) R.string.new_folder else R.string.new_file),
            confirmLabel = stringResource(R.string.create),
            initial = "",
            onConfirm = { name ->
                onCreate(current.parent, name, current.isFolder)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        is FilesDialog.Rename -> NameDialog(
            title = stringResource(R.string.rename),
            confirmLabel = stringResource(R.string.rename),
            initial = current.file.name,
            onConfirm = { name ->
                onRename(current.file, name)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        is FilesDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.delete_title, current.file.name)) },
            text = {
                if (current.file.isDirectory) Text(stringResource(R.string.delete_folder_hint))
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(current.file)
                    dialog = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) }
            },
        )

        FilesDialog.Theme -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.theme_section)) },
            text = {
                Column {
                    ThemeOption(ThemeMode.SYSTEM, R.string.theme_system, themeMode, onThemeSelected)
                    ThemeOption(ThemeMode.LIGHT, R.string.theme_light, themeMode, onThemeSelected)
                    ThemeOption(ThemeMode.DARK, R.string.theme_dark, themeMode, onThemeSelected)
                }
            },
            confirmButton = {
                TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.close)) }
            },
        )

        null -> Unit
    }
}

@Composable
private fun TreeRow(
    entry: TreeEntry,
    onClick: () -> Unit,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (8 + 20 * entry.depth).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val marker = when {
            !entry.isDirectory -> ""
            entry.isExpanded -> "▾"
            else -> "▸"
        }
        Text(text = marker, modifier = Modifier.width(20.dp))
        Text(
            text = entry.file.name,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box {
            TextButton(onClick = { menuOpen = true }) { Text(MENU_GLYPH) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (entry.isDirectory) {
                    MenuItem(R.string.new_file) {
                        menuOpen = false
                        onNewFile()
                    }
                    MenuItem(R.string.new_folder) {
                        menuOpen = false
                        onNewFolder()
                    }
                }
                MenuItem(R.string.rename) {
                    menuOpen = false
                    onRename()
                }
                MenuItem(R.string.delete) {
                    menuOpen = false
                    onDelete()
                }
            }
        }
    }
}

@Composable
private fun MenuItem(labelRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(labelRes)) }, onClick = onClick)
}

@Composable
private fun NameDialog(
    title: String,
    confirmLabel: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.name_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ThemeOption(
    mode: ThemeMode,
    labelRes: Int,
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelected(mode) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = mode == selected, onClick = null)
        Text(
            text = stringResource(labelRes),
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
        )
    }
}

private const val MENU_GLYPH = "⋮"
