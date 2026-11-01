package com.mauyaapps.aircode

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mauyaapps.aircode.editor.EditorEngine
import com.mauyaapps.aircode.editor.EditorRenderMode
import com.mauyaapps.aircode.theme.Appearance
import com.mauyaapps.aircode.theme.ColorSlot
import com.mauyaapps.aircode.theme.DEFAULT_ACCENT
import com.mauyaapps.aircode.theme.MAX_CONTRAST
import com.mauyaapps.aircode.theme.MIN_CONTRAST
import com.mauyaapps.aircode.theme.ThemeMode
import com.mauyaapps.aircode.theme.ThemeStyle
import com.mauyaapps.aircode.theme.YouColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

private class SlotKeys(name: String) {
    val color = intPreferencesKey("you_${name}_color")
    val contrast = floatPreferencesKey("you_${name}_contrast")
}

private fun Preferences.readSlot(keys: SlotKeys): ColorSlot? =
    this[keys.color]?.let { ColorSlot(it, (this[keys.contrast] ?: 0f).coerceIn(MIN_CONTRAST, MAX_CONTRAST)) }

private fun MutablePreferences.writeSlot(keys: SlotKeys, slot: ColorSlot?) {
    if (slot == null) {
        remove(keys.color)
        remove(keys.contrast)
    } else {
        this[keys.color] = slot.color
        this[keys.contrast] = slot.contrast
    }
}

class SettingsRepository(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_mode")
    private val styleKey = stringPreferencesKey("theme_style")
    private val accentKey = intPreferencesKey("theme_accent")
    private val youCustomKey = booleanPreferencesKey("you_custom")
    private val youPrimaryKeys = SlotKeys("primary")
    private val youSecondaryKeys = SlotKeys("secondary")
    private val youTertiaryKeys = SlotKeys("tertiary")
    private val youNeutralKeys = SlotKeys("neutral")

    val appearance: Flow<Appearance> = context.settingsDataStore.data.map { prefs ->
        Appearance(
            mode = prefs[themeKey]
                ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: ThemeMode.SYSTEM,
            style = prefs[styleKey]
                ?.let { name -> ThemeStyle.entries.firstOrNull { it.name == name } }
                ?: ThemeStyle.BASE,
            accent = prefs[accentKey] ?: DEFAULT_ACCENT,
            youCustom = prefs[youCustomKey] ?: false,
            youColors = YouColors(
                primary = prefs.readSlot(youPrimaryKeys) ?: ColorSlot(DEFAULT_ACCENT),
                secondary = prefs.readSlot(youSecondaryKeys),
                tertiary = prefs.readSlot(youTertiaryKeys),
                neutral = prefs.readSlot(youNeutralKeys),
            ),
        )
    }

    private val autocompleteKey = booleanPreferencesKey("autocomplete_enabled")

    val autocompleteEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[autocompleteKey] ?: false
    }

    suspend fun setAutocompleteEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[autocompleteKey] = enabled }
    }

    private val engineKey = stringPreferencesKey("editor_engine")

    val editorEngine: Flow<EditorEngine> = context.settingsDataStore.data.map { prefs ->
        EditorEngine.entries.firstOrNull { it.name == prefs[engineKey] } ?: EditorEngine.NATIVE
    }

    suspend fun setEditorEngine(engine: EditorEngine) {
        context.settingsDataStore.edit { it[engineKey] = engine.name }
    }

    private val renderModeKey = stringPreferencesKey("editor_render_mode")

    val renderMode: Flow<EditorRenderMode> = context.settingsDataStore.data.map { prefs ->
        EditorRenderMode.entries.firstOrNull { it.name == prefs[renderModeKey] } ?: EditorRenderMode.SOFTWARE
    }

    suspend fun setRenderMode(mode: EditorRenderMode) {
        context.settingsDataStore.edit { it[renderModeKey] = mode.name }
    }

    suspend fun setAppearance(appearance: Appearance) {
        context.settingsDataStore.edit {
            it[themeKey] = appearance.mode.name
            it[styleKey] = appearance.style.name
            it[accentKey] = appearance.accent
            it[youCustomKey] = appearance.youCustom
            it.writeSlot(youPrimaryKeys, appearance.youColors.primary)
            it.writeSlot(youSecondaryKeys, appearance.youColors.secondary)
            it.writeSlot(youTertiaryKeys, appearance.youColors.tertiary)
            it.writeSlot(youNeutralKeys, appearance.youColors.neutral)
        }
    }
}
