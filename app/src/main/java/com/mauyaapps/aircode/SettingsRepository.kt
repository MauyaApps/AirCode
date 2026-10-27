package com.mauyaapps.aircode

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mauyaapps.aircode.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Хранилище настроек приложения на DataStore. */
class SettingsRepository(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_mode")

    /** Выбранный режим темы. Если значения нет или оно повреждено — системная. */
    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        prefs[themeKey]
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: ThemeMode.SYSTEM
    }

    private val autocompleteKey = booleanPreferencesKey("autocomplete_enabled")

    /** Разрешил ли пользователь автодополнение. По умолчанию выключено. */
    val autocompleteEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[autocompleteKey] ?: false
    }

    suspend fun setAutocompleteEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[autocompleteKey] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[themeKey] = mode.name }
    }
}
