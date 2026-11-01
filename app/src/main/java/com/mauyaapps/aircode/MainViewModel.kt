package com.mauyaapps.aircode

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mauyaapps.aircode.editor.EditorEngine
import com.mauyaapps.aircode.editor.EditorRenderMode
import com.mauyaapps.aircode.theme.Appearance
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsRepository(application)

    val appearance: StateFlow<Appearance> = settings.appearance.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = Appearance(),
    )

    val autocompleteEnabled: StateFlow<Boolean> = settings.autocompleteEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    fun onAutocompleteChange(enabled: Boolean) {
        viewModelScope.launch { settings.setAutocompleteEnabled(enabled) }
    }

    val renderMode: StateFlow<EditorRenderMode> = settings.renderMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EditorRenderMode.SOFTWARE,
    )

    val editorEngine: StateFlow<EditorEngine> = settings.editorEngine.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EditorEngine.NATIVE,
    )

    fun onEditorEngineChange(engine: EditorEngine) {
        viewModelScope.launch { settings.setEditorEngine(engine) }
    }

    fun onRenderModeChange(mode: EditorRenderMode) {
        viewModelScope.launch { settings.setRenderMode(mode) }
    }

    fun onAppearanceChange(appearance: Appearance) {
        viewModelScope.launch { settings.setAppearance(appearance) }
    }
}
