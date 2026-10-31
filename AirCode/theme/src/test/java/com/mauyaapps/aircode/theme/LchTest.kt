package com.mauyaapps.aircode.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LchTest {

    @Test
    fun чёрныйИБелыйИмеютКрайниеТона() {
        assertEquals(0f, argbToLch(0xFF000000.toInt()).lightness, 0.1f)
        assertEquals(100f, argbToLch(0xFFFFFFFF.toInt()).lightness, 0.1f)
        assertEquals(0xFF000000.toInt(), toneToArgb(120f, 40f, 0f))
        assertEquals(0xFFFFFFFF.toInt(), toneToArgb(120f, 40f, 100f))
    }

    @Test
    fun серыйЦветБезХромы() {
        assertEquals(0f, argbToLch(0xFF808080.toInt()).chroma, 0.5f)
    }

    @Test
    fun тонСобранногоЦветаСовпадаетСЗапрошенным() {
        for (hue in listOf(0f, 60f, 130f, 200f, 260f, 330f)) {
            for (tone in listOf(10f, 30f, 50f, 70f, 90f)) {
                val color = toneToArgb(hue, 40f, tone)
                assertEquals(tone, argbToLch(color).lightness, 1.2f)
            }
        }
    }

    @Test
    fun оттенокСохраняетсяПриДостаточнойХроме() {
        val source = argbToLch(0xFF3F7CFF.toInt())
        val rebuilt = argbToLch(toneToArgb(source.hue, source.chroma, source.lightness))
        assertEquals(source.hue, rebuilt.hue, 2f)
        assertEquals(source.chroma, rebuilt.chroma, 3f)
    }

    @Test
    fun цветВсегдаВнутриГаммы() {
        for (hue in 0..350 step 10) {
            val color = toneToArgb(hue.toFloat(), 200f, 50f)
            assertTrue(color ushr 24 == 0xFF)
        }
    }
}
