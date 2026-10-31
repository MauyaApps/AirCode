package com.mauyaapps.aircode.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

fun supportsModernStyles(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

fun usesWallpaperColors(appearance: Appearance): Boolean = !appearance.youCustom && supportsModernStyles()

fun effectiveStyle(appearance: Appearance): ThemeStyle = when {
    appearance.style == ThemeStyle.GLASS && !supportsModernStyles() -> ThemeStyle.BASE
    else -> appearance.style
}

@Composable
fun AirCodeTheme(
    appearance: Appearance,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(appearance.mode)
    val style = effectiveStyle(appearance)
    val context = LocalContext.current
    val colorScheme = remember(appearance, dark, style) {
        when (style) {
            ThemeStyle.BASE -> baseColorScheme(dark)
            ThemeStyle.MATERIAL_YOU -> when {
                !usesWallpaperColors(appearance) -> youColorScheme(appearance.youColors, dark)
                dark -> dynamicDarkColorScheme(context)
                else -> dynamicLightColorScheme(context)
            }
            ThemeStyle.CUSTOM -> accentColorScheme(appearance.accent, dark)
            ThemeStyle.GLASS -> glassColorScheme(appearance.accent, dark)
        }
    }
    val glass = GlassStyle(enabled = style == ThemeStyle.GLASS, dark = dark)
    MaterialTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(LocalGlassStyle provides glass) {
            if (glass.enabled) {
                GlassBackground(accent = Color(appearance.accent), dark = dark, content = content)
            } else {
                content()
            }
        }
    }
}

@Composable
fun AirCodeTheme(
    mode: ThemeMode,
    content: @Composable () -> Unit,
) {
    AirCodeTheme(appearance = Appearance(mode = mode), content = content)
}
