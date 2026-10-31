package com.mauyaapps.aircode.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private class Accent(val hue: Float, val chroma: Float, val contrast: Float)

private class Neutral(val hue: Float, val chroma: Float, val variantChroma: Float, val contrast: Float)

private class AccentSet(val main: Color, val on: Color, val container: Color, val onContainer: Color)

private const val SECONDARY_DEFAULT_CHROMA = 16f
private const val TERTIARY_DEFAULT_CHROMA = 24f
private const val TERTIARY_HUE_SHIFT = 60f
private const val NEUTRAL_DEFAULT_CHROMA = 6f
private const val NEUTRAL_DEFAULT_VARIANT_CHROMA = 8f
private const val NEUTRAL_CHROMA_SHARE = 0.15f
private const val NEUTRAL_VARIANT_FACTOR = 4f / 3f
private const val ERROR_HUE = 25f
private const val ERROR_CHROMA = 84f

private fun tone(hue: Float, chroma: Float, tone: Float): Color = Color(toneToArgb(hue, chroma, tone))

private fun byContrast(contrast: Float, low: Float, standard: Float, high: Float): Float {
    val value = contrast.coerceIn(MIN_CONTRAST, MAX_CONTRAST)
    return if (value >= 0f) standard + (high - standard) * value else standard + (low - standard) * -value
}

private fun accentFrom(slot: ColorSlot, minChroma: Float, maxChroma: Float): Accent {
    val lch = argbToLch(slot.color)
    return Accent(lch.hue, lch.chroma.coerceIn(minChroma, maxChroma), slot.contrast)
}

private fun neutralFrom(slot: ColorSlot?, primary: Accent): Neutral {
    if (slot == null) {
        return Neutral(primary.hue, NEUTRAL_DEFAULT_CHROMA, NEUTRAL_DEFAULT_VARIANT_CHROMA, primary.contrast)
    }
    val lch = argbToLch(slot.color)
    val chroma = (lch.chroma * NEUTRAL_CHROMA_SHARE).coerceIn(4f, 16f)
    return Neutral(lch.hue, chroma, chroma * NEUTRAL_VARIANT_FACTOR, slot.contrast)
}

private fun accentSet(accent: Accent, dark: Boolean): AccentSet {
    val c = accent.contrast
    val h = accent.hue
    val s = accent.chroma
    return if (dark) {
        AccentSet(
            main = tone(h, s, byContrast(c, 74f, 80f, 92f)),
            on = tone(h, s, byContrast(c, 28f, 20f, 6f)),
            container = tone(h, s, byContrast(c, 34f, 30f, 18f)),
            onContainer = tone(h, s, byContrast(c, 80f, 90f, 98f)),
        )
    } else {
        AccentSet(
            main = tone(h, s, byContrast(c, 46f, 40f, 28f)),
            on = tone(h, s, 100f),
            container = tone(h, s, byContrast(c, 85f, 90f, 94f)),
            onContainer = tone(h, s, byContrast(c, 26f, 10f, 2f)),
        )
    }
}

fun youColorScheme(colors: YouColors, dark: Boolean): ColorScheme {
    val primaryAccent = accentFrom(colors.primary, 16f, 60f)
    val secondaryAccent = colors.secondary?.let { accentFrom(it, 12f, 48f) }
        ?: Accent(primaryAccent.hue, SECONDARY_DEFAULT_CHROMA, primaryAccent.contrast)
    val tertiaryAccent = colors.tertiary?.let { accentFrom(it, 12f, 60f) }
        ?: Accent(primaryAccent.hue + TERTIARY_HUE_SHIFT, TERTIARY_DEFAULT_CHROMA, primaryAccent.contrast)
    val neutral = neutralFrom(colors.neutral, primaryAccent)
    val error = accentSet(Accent(ERROR_HUE, ERROR_CHROMA, 0f), dark)
    val primary = accentSet(primaryAccent, dark)
    val secondary = accentSet(secondaryAccent, dark)
    val tertiary = accentSet(tertiaryAccent, dark)

    val h = neutral.hue
    val n = neutral.chroma
    val v = neutral.variantChroma
    val c = neutral.contrast

    return if (dark) {
        darkColorScheme(
            primary = primary.main,
            onPrimary = primary.on,
            primaryContainer = primary.container,
            onPrimaryContainer = primary.onContainer,
            inversePrimary = tone(primaryAccent.hue, primaryAccent.chroma, 40f),
            secondary = secondary.main,
            onSecondary = secondary.on,
            secondaryContainer = secondary.container,
            onSecondaryContainer = secondary.onContainer,
            tertiary = tertiary.main,
            onTertiary = tertiary.on,
            tertiaryContainer = tertiary.container,
            onTertiaryContainer = tertiary.onContainer,
            background = tone(h, n, 6f),
            onBackground = tone(h, n, byContrast(c, 78f, 90f, 98f)),
            surface = tone(h, n, 6f),
            onSurface = tone(h, n, byContrast(c, 78f, 90f, 98f)),
            surfaceVariant = tone(h, v, 30f),
            onSurfaceVariant = tone(h, v, byContrast(c, 75f, 80f, 92f)),
            surfaceTint = primary.main,
            inverseSurface = tone(h, n, 90f),
            inverseOnSurface = tone(h, n, 20f),
            error = error.main,
            onError = error.on,
            errorContainer = error.container,
            onErrorContainer = error.onContainer,
            outline = tone(h, v, byContrast(c, 45f, 60f, 80f)),
            outlineVariant = tone(h, v, byContrast(c, 22f, 30f, 50f)),
            surfaceBright = tone(h, n, 24f),
            surfaceDim = tone(h, n, 6f),
            surfaceContainerLowest = tone(h, n, 4f),
            surfaceContainerLow = tone(h, n, 10f),
            surfaceContainer = tone(h, n, 12f),
            surfaceContainerHigh = tone(h, n, 17f),
            surfaceContainerHighest = tone(h, n, 22f),
        )
    } else {
        lightColorScheme(
            primary = primary.main,
            onPrimary = primary.on,
            primaryContainer = primary.container,
            onPrimaryContainer = primary.onContainer,
            inversePrimary = tone(primaryAccent.hue, primaryAccent.chroma, 80f),
            secondary = secondary.main,
            onSecondary = secondary.on,
            secondaryContainer = secondary.container,
            onSecondaryContainer = secondary.onContainer,
            tertiary = tertiary.main,
            onTertiary = tertiary.on,
            tertiaryContainer = tertiary.container,
            onTertiaryContainer = tertiary.onContainer,
            background = tone(h, n, 98f),
            onBackground = tone(h, n, byContrast(c, 22f, 10f, 2f)),
            surface = tone(h, n, 98f),
            onSurface = tone(h, n, byContrast(c, 22f, 10f, 2f)),
            surfaceVariant = tone(h, v, 90f),
            onSurfaceVariant = tone(h, v, byContrast(c, 42f, 30f, 15f)),
            surfaceTint = primary.main,
            inverseSurface = tone(h, n, 20f),
            inverseOnSurface = tone(h, n, 95f),
            error = error.main,
            onError = error.on,
            errorContainer = error.container,
            onErrorContainer = error.onContainer,
            outline = tone(h, v, byContrast(c, 60f, 50f, 30f)),
            outlineVariant = tone(h, v, byContrast(c, 88f, 80f, 60f)),
            surfaceBright = tone(h, n, 98f),
            surfaceDim = tone(h, n, 87f),
            surfaceContainerLowest = tone(h, n, 100f),
            surfaceContainerLow = tone(h, n, 96f),
            surfaceContainer = tone(h, n, 94f),
            surfaceContainerHigh = tone(h, n, 92f),
            surfaceContainerHighest = tone(h, n, 90f),
        )
    }
}
