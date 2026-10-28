package com.mauyaapps.aircode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.mauyaapps.aircode.filesystem.DraftEntry
import com.mauyaapps.aircode.filesystem.DraftFolder
import com.mauyaapps.aircode.filesystem.TreeEntry
import java.io.File

private const val MENU_GLYPH = "⋮"

private sealed interface FilesDialog {
    data class NewItem(val parent: File?, val isFolder: Boolean) : FilesDialog
    data class Rename(val file: File) : FilesDialog
    data class Delete(val file: File) : FilesDialog
    data class NewDraft(val parentId: Long?, val isFolder: Boolean) : FilesDialog
    data class RenameDraft(val id: Long, val name: String) : FilesDialog
    data class DeleteDraft(val id: Long, val name: String, val isFolder: Boolean) : FilesDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    state: FilesUiState,
    listState: LazyListState,
    onEntryClick: (TreeEntry) -> Unit,
    onCreate: (parent: File?, name: String, isFolder: Boolean) -> Unit,
    onRename: (File, String) -> Unit,
    onDelete: (File) -> Unit,
    onErrorShown: () -> Unit,
    onOpenThemeSettings: () -> Unit,
    onDraftToggle: (Long) -> Unit,
    onDraftOpen: (Long) -> Unit,
    onDraftCreate: (parentId: Long?, name: String, isFolder: Boolean) -> Unit,
    onDraftRename: (Long, String) -> Unit,
    onDraftDelete: (Long) -> Unit,
    onDraftSave: (Long) -> Unit,
    onPickerOpen: (File) -> Unit,
    onPickerUp: () -> Unit,
    onPickerConfirm: () -> Unit,
    onPickerDismiss: () -> Unit,
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
                            MenuItem(R.string.new_file_draft) {
                                topMenuOpen = false
                                dialog = FilesDialog.NewDraft(parentId = null, isFolder = false)
                            }
                            MenuItem(R.string.new_folder_draft) {
                                topMenuOpen = false
                                dialog = FilesDialog.NewDraft(parentId = null, isFolder = true)
                            }
                            MenuItem(R.string.theme_section) {
                                topMenuOpen = false
                                onOpenThemeSettings()
                            }
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
        if (state.entries.isEmpty() && state.drafts.isEmpty()) {
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
                if (state.drafts.isNotEmpty()) {
                    item(key = "drafts-header") {
                        Text(
                            text = stringResource(R.string.drafts_header),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(state.drafts, key = { "draft-${it.node.id}" }) { entry ->
                        val id = entry.node.id
                        DraftRow(
                            entry = entry,
                            onClick = {
                                if (entry.node is DraftFolder) onDraftToggle(id) else onDraftOpen(id)
                            },
                            onNewFile = { dialog = FilesDialog.NewDraft(id, isFolder = false) },
                            onNewFolder = { dialog = FilesDialog.NewDraft(id, isFolder = true) },
                            onSave = { onDraftSave(id) },
                            onRename = { dialog = FilesDialog.RenameDraft(id, entry.node.name) },
                            onDelete = {
                                dialog = FilesDialog.DeleteDraft(id, entry.node.name, entry.node is DraftFolder)
                            },
                        )
                    }
                    item(key = "drafts-divider") { HorizontalDivider() }
                }
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

        is FilesDialog.NewDraft -> NameDialog(
            title = stringResource(if (current.isFolder) R.string.new_folder else R.string.new_file),
            label = if (current.isFolder) R.string.name_label else R.string.name_with_extension_label,
            confirmLabel = stringResource(R.string.create),
            initial = "",
            onConfirm = { name ->
                onDraftCreate(current.parentId, name, current.isFolder)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        is FilesDialog.RenameDraft -> NameDialog(
            title = stringResource(R.string.rename),
            confirmLabel = stringResource(R.string.rename),
            initial = current.name,
            onConfirm = { name ->
                onDraftRename(current.id, name)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        is FilesDialog.DeleteDraft -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.delete_title, current.name)) },
            text = { Text(stringResource(R.string.delete_draft_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    onDraftDelete(current.id)
                    dialog = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) }
            },
        )

        null -> Unit
    }

    state.picker?.let { picker ->
        SavePickerDialog(
            picker = picker,
            onOpen = onPickerOpen,
            onUp = onPickerUp,
            onConfirm = onPickerConfirm,
            onDismiss = onPickerDismiss,
        )
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
private fun DraftRow(
    entry: DraftEntry,
    onClick: () -> Unit,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onSave: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isFolder = entry.node is DraftFolder

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (8 + 20 * entry.depth).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val marker = when {
            !isFolder -> ""
            entry.isExpanded -> "▾"
            else -> "▸"
        }
        Text(text = marker, modifier = Modifier.width(20.dp))
        Text(
            text = entry.node.name,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box {
            TextButton(onClick = { menuOpen = true }) { Text(MENU_GLYPH) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (isFolder) {
                    MenuItem(R.string.new_file) {
                        menuOpen = false
                        onNewFile()
                    }
                    MenuItem(R.string.new_folder) {
                        menuOpen = false
                        onNewFolder()
                    }
                }
                MenuItem(if (isFolder) R.string.save_draft_folder else R.string.save_draft_file) {
                    menuOpen = false
                    onSave()
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
private fun SavePickerDialog(
    picker: SavePicker,
    onOpen: (File) -> Unit,
    onUp: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.picker_title, picker.node.name)) },
        text = {
            Column {
                Text(
                    text = picker.path.ifEmpty { stringResource(R.string.picker_root) },
                    style = MaterialTheme.typography.labelLarge,
                )
                if (picker.canGoUp) {
                    TextButton(onClick = onUp) { Text(stringResource(R.string.picker_up)) }
                }
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(picker.folders, key = { it.path }) { folder ->
                        Text(
                            text = "▸ ${folder.name}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(folder) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.picker_here)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun MenuItem(labelRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(labelRes)) }, onClick = onClick)
}

@Composable
private fun NameDialog(
    title: String,
    label: Int = R.string.name_label,
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
                label = { Text(stringResource(label)) },
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
