package com.mauyaapps.aircode

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mauyaapps.aircode.theme.Appearance
import com.mauyaapps.aircode.theme.DEFAULT_ACCENT
import com.mauyaapps.aircode.theme.ThemeMode
import com.mauyaapps.aircode.theme.ThemeStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_mode")
    private val styleKey = stringPreferencesKey("theme_style")
    private val accentKey = intPreferencesKey("theme_accent")

    val appearance: Flow<Appearance> = context.settingsDataStore.data.map { prefs ->
        Appearance(
            mode = prefs[themeKey]
                ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: ThemeMode.SYSTEM,
            style = prefs[styleKey]
                ?.let { name -> ThemeStyle.entries.firstOrNull { it.name == name } }
                ?: ThemeStyle.BASE,
            accent = prefs[accentKey] ?: DEFAULT_ACCENT,
        )
    }

    private val autocompleteKey = booleanPreferencesKey("autocomplete_enabled")

    val autocompleteEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[autocompleteKey] ?: false
    }

    suspend fun setAutocompleteEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[autocompleteKey] = enabled }
    }

    suspend fun setAppearance(appearance: Appearance) {
        context.settingsDataStore.edit {
            it[themeKey] = appearance.mode.name
            it[styleKey] = appearance.style.name
            it[accentKey] = appearance.accent
        }
    }
}
