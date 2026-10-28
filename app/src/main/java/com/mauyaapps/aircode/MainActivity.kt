package com.mauyaapps.aircode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mauyaapps.aircode.filesystem.DraftFile
import com.mauyaapps.aircode.theme.AirCodeTheme
import com.mauyaapps.aircode.theme.isDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val drafts = (application as AirCodeApp).drafts
            val mainViewModel: MainViewModel = viewModel()
            val filesViewModel: FilesViewModel = viewModel()
            val editorViewModel: EditorViewModel = viewModel()
            val appearance by mainViewModel.appearance.collectAsStateWithLifecycle()
            val filesState by filesViewModel.state.collectAsStateWithLifecycle()
            val autocompleteEnabled by mainViewModel.autocompleteEnabled.collectAsStateWithLifecycle()
            val editorState by editorViewModel.state.collectAsStateWithLifecycle()
            val listState = rememberLazyListState()
            var showFiles by rememberSaveable { mutableStateOf(false) }
            var showThemeSettings by rememberSaveable { mutableStateOf(false) }
            val hasTabs = editorState.tabs.isNotEmpty()
            val filesVisible = !hasTabs || showFiles

            AirCodeTheme(appearance = appearance) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(alpha = if (showThemeSettings) 0f else 1f),
                    ) {
                        if (hasTabs) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(alpha = if (filesVisible) 0f else 1f),
                            ) {
                                EditorScreen(
                                    state = editorState,
                                    darkTheme = isDarkTheme(appearance.mode),
                                    visible = !filesVisible && !showThemeSettings,
                                    autocompleteEnabled = autocompleteEnabled,
                                    onChanged = editorViewModel::onChanged,
                                    onSave = editorViewModel::onSave,
                                    onSelectTab = editorViewModel::onSelectTab,
                                    onCloseTab = editorViewModel::onCloseTab,
                                    onShowFiles = { showFiles = true },
                                    onThemeSelected = { mode ->
                                        mainViewModel.onAppearanceChange(appearance.copy(mode = mode))
                                    },
                                    onOpenThemeSettings = { showThemeSettings = true },
                                    onAutocompleteChange = mainViewModel::onAutocompleteChange,
                                    onErrorShown = editorViewModel::onErrorShown,
                                )
                            }
                        }
                        if (filesVisible) {
                            BackHandler(enabled = hasTabs && !showThemeSettings) { showFiles = false }
                            Surface(modifier = Modifier.fillMaxSize()) {
                                FilesScreen(
                                    state = filesState,
                                    listState = listState,
                                    onEntryClick = { entry ->
                                        if (entry.isDirectory) {
                                            filesViewModel.onEntryClick(entry)
                                        } else {
                                            editorViewModel.open(
                                                file = entry.file,
                                                onError = filesViewModel::reportError,
                                                onOpened = { showFiles = false },
                                            )
                                        }
                                    },
                                    onCreate = filesViewModel::onCreate,
                                    onRename = { file, name ->
                                        editorViewModel.onPathChanged(file)
                                        filesViewModel.onRename(file, name)
                                    },
                                    onDelete = { file ->
                                        editorViewModel.onPathChanged(file)
                                        filesViewModel.onDelete(file)
                                    },
                                    onErrorShown = filesViewModel::onErrorShown,
                                    onOpenThemeSettings = { showThemeSettings = true },
                                    onDraftToggle = filesViewModel::onDraftToggle,
                                    onDraftOpen = { id ->
                                        (drafts.find(id) as? DraftFile)?.let { node ->
                                            editorViewModel.openDraft(node) { showFiles = false }
                                        }
                                    },
                                    onDraftCreate = filesViewModel::onDraftCreate,
                                    onDraftRename = filesViewModel::onDraftRename,
                                    onDraftDelete = filesViewModel::onDraftDelete,
                                    onDraftSave = filesViewModel::onDraftSave,
                                    onPickerOpen = filesViewModel::onPickerOpen,
                                    onPickerUp = filesViewModel::onPickerUp,
                                    onPickerConfirm = filesViewModel::onPickerConfirm,
                                    onPickerDismiss = filesViewModel::onPickerDismiss,
                                )
                            }
                        }
                    }
                    if (showThemeSettings) {
                        ThemeSettingsScreen(
                            appearance = appearance,
                            onChange = mainViewModel::onAppearanceChange,
                            onClose = { showThemeSettings = false },
                        )
                    }
                }
            }
        }
    }
}
