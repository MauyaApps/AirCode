package com.mauyaapps.aircode

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mauyaapps.aircode.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Настройки приложения: тема и разрешение на автодополнение. */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsRepository(application)

    val themeMode: StateFlow<ThemeMode> = settings.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThemeMode.SYSTEM,
    )

    val autocompleteEnabled: StateFlow<Boolean> = settings.autocompleteEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    fun onAutocompleteChange(enabled: Boolean) {
        viewModelScope.launch { settings.setAutocompleteEnabled(enabled) }
    }

    fun onThemeSelected(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }
}
