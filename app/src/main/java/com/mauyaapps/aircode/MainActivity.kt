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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mauyaapps.aircode.theme.AirCodeTheme
import com.mauyaapps.aircode.theme.isDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val filesViewModel: FilesViewModel = viewModel()
            val editorViewModel: EditorViewModel = viewModel()
            val themeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()
            val filesState by filesViewModel.state.collectAsStateWithLifecycle()
            val autocompleteEnabled by mainViewModel.autocompleteEnabled.collectAsStateWithLifecycle()
            val editorState by editorViewModel.state.collectAsStateWithLifecycle()
            // Положение списка сохраняется, пока открыт редактор
            val listState = rememberLazyListState()
            // Список файлов поверх редактора, когда вкладки уже открыты
            var showFiles by rememberSaveable { mutableStateOf(false) }
            val hasTabs = editorState.tabs.isNotEmpty()
            val filesVisible = !hasTabs || showFiles

            AirCodeTheme(mode = themeMode) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Редактор остаётся в композиции, пока есть вкладки: иначе уничтожится WebView
                    if (hasTabs) {
                        EditorScreen(
                            state = editorState,
                            darkTheme = isDarkTheme(themeMode),
                            visible = !filesVisible,
                            autocompleteEnabled = autocompleteEnabled,
                            onChanged = editorViewModel::onChanged,
                            onSave = editorViewModel::onSave,
                            onSelectTab = editorViewModel::onSelectTab,
                            onCloseTab = editorViewModel::onCloseTab,
                            onShowFiles = { showFiles = true },
                            onThemeSelected = mainViewModel::onThemeSelected,
                            onAutocompleteChange = mainViewModel::onAutocompleteChange,
                            onErrorShown = editorViewModel::onErrorShown,
                        )
                    }
                    if (filesVisible) {
                        // Назад из списка файлов возвращает к вкладкам, а не закрывает приложение
                        BackHandler(enabled = hasTabs) { showFiles = false }
                        Surface(modifier = Modifier.fillMaxSize()) {
                            FilesScreen(
                                state = filesState,
                                themeMode = themeMode,
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
                                onThemeSelected = mainViewModel::onThemeSelected,
                            )
                        }
                    }
                }
            }
        }
    }
}
