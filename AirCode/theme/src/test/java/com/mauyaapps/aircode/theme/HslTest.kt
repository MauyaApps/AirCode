package com.mauyaapps.aircode.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HslTest {

    @Test
    fun основныеЦветаПереводятсяВArgb() {
        assertEquals(0xFFFF0000.toInt(), hslToArgb(0f, 1f, 0.5f))
        assertEquals(0xFF00FF00.toInt(), hslToArgb(120f, 1f, 0.5f))
        assertEquals(0xFF0000FF.toInt(), hslToArgb(240f, 1f, 0.5f))
        assertEquals(0xFFFFFFFF.toInt(), hslToArgb(0f, 0f, 1f))
        assertEquals(0xFF000000.toInt(), hslToArgb(77f, 0.5f, 0f))
    }

    @Test
    fun оттенокЗамыкаетсяПоКругу() {
        assertEquals(hslToArgb(10f, 0.6f, 0.5f), hslToArgb(370f, 0.6f, 0.5f))
        assertEquals(hslToArgb(350f, 0.6f, 0.5f), hslToArgb(-10f, 0.6f, 0.5f))
    }

    @Test
    fun argbВHslИОбратноДаётТотЖеЦвет() {
        for (color in listOf(0xFF3F7CFF.toInt(), 0xFFE53935.toInt(), 0xFF2E9E5B.toInt(), 0xFF808080.toInt())) {
            val hsl = argbToHsl(color)
            assertEquals(color, hslToArgb(hsl[0], hsl[1], hsl[2]))
        }
    }

    @Test
    fun серыйЦветБезОттенка() {
        val hsl = argbToHsl(0xFF808080.toInt())
        assertEquals(0f, hsl[0], 0f)
        assertEquals(0f, hsl[1], 0f)
    }

    @Test
    fun поворотОттенка() {
        assertEquals(0xFF00FF00.toInt(), rotateHue(0xFFFF0000.toInt(), 120f))
    }

    @Test
    fun разборИЗаписьHex() {
        assertEquals(0xFF3F7CFF.toInt(), parseHexColor("#3f7cff"))
        assertEquals(0xFF3F7CFF.toInt(), parseHexColor(" 3F7CFF "))
        assertNull(parseHexColor("#3F7C"))
        assertNull(parseHexColor("#GGGGGG"))
        assertNull(parseHexColor(""))
        assertEquals("#3F7CFF", formatHexColor(0xFF3F7CFF.toInt()))
        assertEquals("#00000A", formatHexColor(0xFF00000A.toInt()))
    }
}
