package com.mauyaapps.aircode

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mauyaapps.aircode.theme.ACCENT_PRESETS
import com.mauyaapps.aircode.theme.Appearance
import com.mauyaapps.aircode.theme.ColorSlot
import com.mauyaapps.aircode.theme.MAX_CONTRAST
import com.mauyaapps.aircode.theme.MIN_CONTRAST
import com.mauyaapps.aircode.theme.ThemeMode
import com.mauyaapps.aircode.theme.ThemeStyle
import com.mauyaapps.aircode.theme.argbToHsl
import com.mauyaapps.aircode.theme.formatHexColor
import com.mauyaapps.aircode.theme.hslToArgb
import com.mauyaapps.aircode.theme.parseHexColor
import com.mauyaapps.aircode.theme.rotateHue
import com.mauyaapps.aircode.theme.supportsModernStyles
import com.mauyaapps.aircode.theme.usesWallpaperColors

private data class StyleOption(
    val style: ThemeStyle,
    val label: Int,
    val description: Int,
    val needsModern: Boolean,
)

private val STYLE_OPTIONS = listOf(
    StyleOption(ThemeStyle.BASE, R.string.style_base, R.string.style_base_desc, needsModern = false),
    StyleOption(ThemeStyle.MATERIAL_YOU, R.string.style_material_you, R.string.style_material_you_desc, needsModern = false),
    StyleOption(ThemeStyle.CUSTOM, R.string.style_custom, R.string.style_custom_desc, needsModern = false),
    StyleOption(ThemeStyle.GLASS, R.string.style_glass, R.string.style_glass_desc, needsModern = true),
)

private fun modeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    appearance: Appearance,
    onChange: (Appearance) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val modernSupported = supportsModernStyles()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.theme_settings_title)) },
                navigationIcon = { TextButton(onClick = onClose) { Text(BACK_GLYPH) } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SectionTitle(R.string.theme_mode_title)
            ThemeMode.entries.forEach { mode ->
                OptionRow(
                    label = stringResource(modeLabel(mode)),
                    description = null,
                    selected = appearance.mode == mode,
                    enabled = true,
                    onClick = { onChange(appearance.copy(mode = mode)) },
                )
            }

            SectionTitle(R.string.theme_style_title)
            STYLE_OPTIONS.forEach { option ->
                OptionRow(
                    label = stringResource(option.label),
                    description = stringResource(option.description),
                    selected = appearance.style == option.style,
                    enabled = !option.needsModern || modernSupported,
                    onClick = { onChange(appearance.copy(style = option.style)) },
                )
            }

            if (appearance.style == ThemeStyle.MATERIAL_YOU) {
                SectionTitle(R.string.you_source_title)
                val wallpaper = usesWallpaperColors(appearance)
                OptionRow(
                    label = stringResource(R.string.you_source_wallpaper),
                    description = stringResource(R.string.you_source_wallpaper_desc),
                    selected = wallpaper,
                    enabled = modernSupported,
                    onClick = { onChange(appearance.copy(youCustom = false)) },
                )
                OptionRow(
                    label = stringResource(R.string.you_source_custom),
                    description = stringResource(R.string.you_source_custom_desc),
                    selected = !wallpaper,
                    enabled = true,
                    onClick = { onChange(appearance.copy(youCustom = true)) },
                )
                if (!wallpaper) {
                    val colors = appearance.youColors
                    SlotEditor(
                        title = R.string.slot_primary,
                        slot = colors.primary,
                        defaultColor = colors.primary.color,
                        optional = false,
                        onChange = { slot -> onChange(appearance.copy(youColors = colors.copy(primary = slot ?: colors.primary))) },
                    )
                    SlotEditor(
                        title = R.string.slot_secondary,
                        slot = colors.secondary,
                        defaultColor = rotateHue(colors.primary.color, SECONDARY_HUE_SHIFT),
                        optional = true,
                        onChange = { slot -> onChange(appearance.copy(youColors = colors.copy(secondary = slot))) },
                    )
                    SlotEditor(
                        title = R.string.slot_tertiary,
                        slot = colors.tertiary,
                        defaultColor = rotateHue(colors.primary.color, TERTIARY_HUE_SHIFT),
                        optional = true,
                        onChange = { slot -> onChange(appearance.copy(youColors = colors.copy(tertiary = slot))) },
                    )
                    SlotEditor(
                        title = R.string.slot_neutral,
                        slot = colors.neutral,
                        defaultColor = colors.primary.color,
                        optional = true,
                        onChange = { slot -> onChange(appearance.copy(youColors = colors.copy(neutral = slot))) },
                    )
                }
            }

            if (appearance.style == ThemeStyle.CUSTOM || appearance.style == ThemeStyle.GLASS) {
                SectionTitle(R.string.accent_title)
                AccentPicker(
                    accent = appearance.accent,
                    onAccentChange = { onChange(appearance.copy(accent = it)) },
                )
            }

            SectionTitle(R.string.sample_title)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.sample_text), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {}) { Text(stringResource(R.string.sample_button)) }
                        OutlinedButton(onClick = {}) { Text(stringResource(R.string.sample_outline)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun OptionRow(
    label: String,
    description: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SlotEditor(
    title: Int,
    slot: ColorSlot?,
    defaultColor: Int,
    optional: Boolean,
    onChange: (ColorSlot?) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (optional) {
                    Switch(
                        checked = slot != null,
                        onCheckedChange = { enabled -> onChange(if (enabled) ColorSlot(defaultColor) else null) },
                    )
                }
            }
            if (slot == null) {
                Text(
                    text = stringResource(R.string.slot_auto_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                AccentPicker(accent = slot.color, onAccentChange = { onChange(slot.copy(color = it)) })
                ContrastSlider(contrast = slot.contrast, onChange = { onChange(slot.copy(contrast = it)) })
            }
        }
    }
}

private fun contrastName(value: Float): Int = when {
    value < -0.25f -> R.string.contrast_low
    value < 0.25f -> R.string.contrast_standard
    value < 0.75f -> R.string.contrast_medium
    else -> R.string.contrast_high
}

@Composable
private fun ContrastSlider(contrast: Float, onChange: (Float) -> Unit) {
    var value by remember(contrast) { mutableFloatStateOf(contrast) }
    Text(
        text = stringResource(R.string.contrast_label, stringResource(contrastName(value))),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 8.dp),
    )
    Slider(
        value = value,
        onValueChange = { value = it },
        onValueChangeFinished = { onChange(value) },
        valueRange = MIN_CONTRAST..MAX_CONTRAST,
    )
}

@Composable
private fun AccentPicker(accent: Int, onAccentChange: (Int) -> Unit) {
    ACCENT_PRESETS.chunked(5).forEach { row ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(vertical = 6.dp),
        ) {
            row.forEach { color ->
                Swatch(color = color, selected = color == accent, onClick = { onAccentChange(color) })
            }
        }
    }

    var hue by remember(accent) { mutableFloatStateOf(argbToHsl(accent)[0]) }
    Text(
        text = stringResource(R.string.accent_hue),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 8.dp),
    )
    Slider(
        value = hue,
        onValueChange = { hue = it },
        onValueChangeFinished = { onAccentChange(hslToArgb(hue, SLIDER_SATURATION, SLIDER_LIGHTNESS)) },
        valueRange = 0f..360f,
    )

    var hex by remember(accent) { mutableStateOf(formatHexColor(accent)) }
    OutlinedTextField(
        value = hex,
        onValueChange = { text ->
            hex = text
            parseHexColor(text)?.let(onAccentChange)
        },
        label = { Text(stringResource(R.string.accent_hex)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Swatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    val ring = if (selected) {
        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(color))
            .then(ring)
            .clickable(onClick = onClick),
    ) {}
}

private const val BACK_GLYPH = "←"
private const val SLIDER_SATURATION = 0.65f
private const val SLIDER_LIGHTNESS = 0.50f
private const val SECONDARY_HUE_SHIFT = 30f
private const val TERTIARY_HUE_SHIFT = 60f
