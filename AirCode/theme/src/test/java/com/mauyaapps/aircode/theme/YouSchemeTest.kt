package com.mauyaapps.aircode.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouSchemeTest {

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun ratio(a: Color, b: Color): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    private fun colors(contrast: Float) = YouColors(primary = ColorSlot(0xFF3F7CFF.toInt(), contrast))

    @Test
    fun стандартныйКонтрастДаётЧитаемыеПары() {
        for (dark in listOf(false, true)) {
            val scheme = youColorScheme(colors(0f), dark)
            assertTrue(ratio(scheme.primary, scheme.onPrimary) >= 4.5)
            assertTrue(ratio(scheme.primaryContainer, scheme.onPrimaryContainer) >= 4.5)
            assertTrue(ratio(scheme.surface, scheme.onSurface) >= 7.0)
            assertTrue(ratio(scheme.surfaceVariant, scheme.onSurfaceVariant) >= 4.5)
        }
    }

    @Test
    fun высокийКонтрастСильнееСтандартного() {
        for (dark in listOf(false, true)) {
            val standard = youColorScheme(colors(0f), dark)
            val high = youColorScheme(colors(1f), dark)
            val low = youColorScheme(colors(-1f), dark)
            assertTrue(ratio(high.primary, high.onPrimary) > ratio(standard.primary, standard.onPrimary))
            assertTrue(ratio(low.primary, low.onPrimary) < ratio(standard.primary, standard.onPrimary))
            assertTrue(ratio(low.primary, low.onPrimary) >= 4.5)
        }
    }

    @Test
    fun безВторогоИТретьегоЦветаОниВыводятсяИзОсновного() {
        val derived = youColorScheme(colors(0f), dark = false)
        val own = youColorScheme(
            YouColors(
                primary = ColorSlot(0xFF3F7CFF.toInt()),
                secondary = ColorSlot(0xFFE53935.toInt()),
                tertiary = ColorSlot(0xFF2E9E5B.toInt()),
            ),
            dark = false,
        )
        assertNotEquals(derived.secondary.toArgb(), own.secondary.toArgb())
        assertNotEquals(derived.tertiary.toArgb(), own.tertiary.toArgb())
        assertEquals(derived.primary.toArgb(), own.primary.toArgb())
    }

    @Test
    fun цветФонаМеняетПоверхностиНоНеТекст() {
        val plain = youColorScheme(colors(0f), dark = true)
        val tinted = youColorScheme(
            YouColors(
                primary = ColorSlot(0xFF3F7CFF.toInt()),
                neutral = ColorSlot(0xFF2E9E5B.toInt()),
            ),
            dark = true,
        )
        assertNotEquals(plain.surface.toArgb(), tinted.surface.toArgb())
        assertTrue(ratio(tinted.surface, tinted.onSurface) >= 7.0)
    }
}
