package com.mauyaapps.aircode.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private fun tone(hue: Float, saturation: Float, lightness: Float) =
    Color(hslToArgb(hue, saturation, lightness))

internal fun schemeFromHue(hue: Float, chroma: Float, dark: Boolean): ColorScheme {
    val s = chroma
    val n = (s * 0.25f).coerceAtMost(0.15f)
    val t = hue + 60f
    return if (dark) {
        darkColorScheme(
            primary = tone(hue, s, 0.78f),
            onPrimary = tone(hue, s, 0.15f),
            primaryContainer = tone(hue, s * 0.8f, 0.28f),
            onPrimaryContainer = tone(hue, s, 0.90f),
            secondary = tone(hue, s * 0.35f, 0.78f),
            onSecondary = tone(hue, s * 0.35f, 0.15f),
            secondaryContainer = tone(hue, s * 0.3f, 0.26f),
            onSecondaryContainer = tone(hue, s * 0.35f, 0.90f),
            tertiary = tone(t, s * 0.5f, 0.78f),
            onTertiary = tone(t, s * 0.5f, 0.15f),
            tertiaryContainer = tone(t, s * 0.45f, 0.28f),
            onTertiaryContainer = tone(t, s * 0.5f, 0.90f),
            background = tone(hue, n, 0.08f),
            onBackground = tone(hue, n, 0.90f),
            surface = tone(hue, n, 0.08f),
            onSurface = tone(hue, n, 0.90f),
            surfaceVariant = tone(hue, n * 1.2f, 0.22f),
            onSurfaceVariant = tone(hue, n, 0.78f),
            outline = tone(hue, n, 0.55f),
            outlineVariant = tone(hue, n, 0.30f),
            inverseSurface = tone(hue, n, 0.90f),
            inverseOnSurface = tone(hue, n, 0.15f),
            inversePrimary = tone(hue, s, 0.40f),
            surfaceDim = tone(hue, n, 0.08f),
            surfaceBright = tone(hue, n, 0.26f),
            surfaceContainerLowest = tone(hue, n, 0.05f),
            surfaceContainerLow = tone(hue, n, 0.10f),
            surfaceContainer = tone(hue, n, 0.12f),
            surfaceContainerHigh = tone(hue, n, 0.17f),
            surfaceContainerHighest = tone(hue, n, 0.22f),
        )
    } else {
        lightColorScheme(
            primary = tone(hue, s, 0.40f),
            onPrimary = Color.White,
            primaryContainer = tone(hue, s * 0.9f, 0.90f),
            onPrimaryContainer = tone(hue, s, 0.12f),
            secondary = tone(hue, s * 0.35f, 0.42f),
            onSecondary = Color.White,
            secondaryContainer = tone(hue, s * 0.35f, 0.90f),
            onSecondaryContainer = tone(hue, s * 0.35f, 0.12f),
            tertiary = tone(t, s * 0.5f, 0.42f),
            onTertiary = Color.White,
            tertiaryContainer = tone(t, s * 0.5f, 0.90f),
            onTertiaryContainer = tone(t, s * 0.5f, 0.12f),
            background = tone(hue, n, 0.98f),
            onBackground = tone(hue, n, 0.10f),
            surface = tone(hue, n, 0.98f),
            onSurface = tone(hue, n, 0.10f),
            surfaceVariant = tone(hue, n * 1.2f, 0.90f),
            onSurfaceVariant = tone(hue, n, 0.30f),
            outline = tone(hue, n, 0.45f),
            outlineVariant = tone(hue, n, 0.80f),
            inverseSurface = tone(hue, n, 0.20f),
            inverseOnSurface = tone(hue, n, 0.95f),
            inversePrimary = tone(hue, s, 0.78f),
            surfaceDim = tone(hue, n, 0.87f),
            surfaceBright = tone(hue, n, 0.98f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = tone(hue, n, 0.96f),
            surfaceContainer = tone(hue, n, 0.94f),
            surfaceContainerHigh = tone(hue, n, 0.92f),
            surfaceContainerHighest = tone(hue, n, 0.90f),
        )
    }
}

fun baseColorScheme(dark: Boolean): ColorScheme = schemeFromHue(hue = 215f, chroma = 0.10f, dark = dark)

fun accentColorScheme(accent: Int, dark: Boolean): ColorScheme {
    val hsl = argbToHsl(accent)
    return schemeFromHue(hue = hsl[0], chroma = hsl[1].coerceIn(0.35f, 0.9f), dark = dark)
}

fun glassColorScheme(accent: Int, dark: Boolean): ColorScheme {
    val scheme = accentColorScheme(accent, dark)
    return scheme.copy(
        background = Color.Transparent,
        surface = scheme.surface.copy(alpha = 0.50f),
        surfaceVariant = scheme.surfaceVariant.copy(alpha = 0.45f),
        surfaceContainerLowest = scheme.surfaceContainerLowest.copy(alpha = 0.50f),
        surfaceContainerLow = scheme.surfaceContainerLow.copy(alpha = 0.55f),
        surfaceContainer = scheme.surfaceContainer.copy(alpha = 0.88f),
        surfaceContainerHigh = scheme.surfaceContainerHigh.copy(alpha = 0.90f),
        surfaceContainerHighest = scheme.surfaceContainerHighest.copy(alpha = 0.70f),
    )
}
