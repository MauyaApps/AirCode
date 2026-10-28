package com.mauyaapps.aircode.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

fun supportsModernStyles(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

fun effectiveStyle(style: ThemeStyle): ThemeStyle =
    if ((style == ThemeStyle.MATERIAL_YOU || style == ThemeStyle.GLASS) && !supportsModernStyles()) {
        ThemeStyle.BASE
    } else {
        style
    }

@Composable
fun AirCodeTheme(
    appearance: Appearance,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(appearance.mode)
    val style = effectiveStyle(appearance.style)
    val context = LocalContext.current
    val colorScheme = when (style) {
        ThemeStyle.BASE -> baseColorScheme(dark)
        ThemeStyle.MATERIAL_YOU -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        ThemeStyle.CUSTOM -> accentColorScheme(appearance.accent, dark)
        ThemeStyle.GLASS -> glassColorScheme(appearance.accent, dark)
    }
    MaterialTheme(colorScheme = colorScheme) {
        if (style == ThemeStyle.GLASS) {
            GlassBackground(accent = Color(appearance.accent), dark = dark, content = content)
        } else {
            content()
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
