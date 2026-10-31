package com.mauyaapps.aircode.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalBufferTest {

    private fun buffer(input: String, maxLines: Int = 100) = TerminalBuffer(maxLines).apply { feed(input) }

    @Test
    fun plainTextAndNewlines() {
        assertEquals("one\ntwo", buffer("one\ntwo").text())
        assertEquals("one\ntwo", buffer("one\r\ntwo").text())
    }

    @Test
    fun carriageReturnOverwritesLine() {
        assertEquals("doneing", buffer("working\rdone").text())
    }

    @Test
    fun backspaceMovesCursorBack() {
        assertEquals("aXc", buffer("abc\b\bX").text())
    }

    @Test
    fun tabAdvancesToNextStop() {
        assertEquals("a       b", buffer("a\tb").text())
    }

    @Test
    fun sgrColorsAreApplied() {
        val b = buffer("\u001B[31mred\u001B[0m ok")
        val line = b.lineAt(0)
        assertEquals("red ok", line.text)
        assertEquals(AnsiPalette.color(1), line.styles[0].foreground)
        assertEquals(AnsiPalette.color(1), line.styles[2].foreground)
        assertNull(line.styles[3].foreground)
    }

    @Test
    fun sgrBoldUnderlineInverseAndReset() {
        val line = buffer("\u001B[1;4;7mx\u001B[m y").lineAt(0)
        assertTrue(line.styles[0].bold)
        assertTrue(line.styles[0].underline)
        assertTrue(line.styles[0].inverse)
        assertEquals(CellStyle(), line.styles[2])
    }

    @Test
    fun extendedColors() {
        val line = buffer("\u001B[38;5;196ma\u001B[48;2;1;2;3mb").lineAt(0)
        assertEquals(AnsiPalette.color(196), line.styles[0].foreground)
        assertEquals(AnsiPalette.rgb(1, 2, 3), line.styles[1].background)
        assertEquals(AnsiPalette.color(196), line.styles[1].foreground)
    }

    @Test
    fun brightColors() {
        val line = buffer("\u001B[91mx").lineAt(0)
        assertEquals(AnsiPalette.color(9), line.styles[0].foreground)
    }

    @Test
    fun eraseToEndOfLine() {
        assertEquals("ab", buffer("abcde\b\b\b\u001B[K").text())
    }

    @Test
    fun eraseWholeLine() {
        assertEquals("", buffer("abc\u001B[2K").text())
    }

    @Test
    fun clearScreenDropsEverything() {
        assertEquals("", buffer("a\nb\nc\u001B[H\u001B[2J\u001B[3J").text())
    }

    @Test
    fun cursorUpRewritesPreviousLine() {
        assertEquals("new\nlast", buffer("old\nlast\u001B[1A\rnew").text())
    }

    @Test
    fun cursorForwardAndBack() {
        assertEquals("a  b", buffer("a\u001B[2Cb").text())
        assertEquals("aXc", buffer("abc\u001B[2DX").text())
    }

    @Test
    fun deleteCharsShiftsRest() {
        assertEquals("ace", buffer("abcde\r\u001B[1C\u001B[1P\u001B[1C\u001B[1P").text())
    }

    @Test
    fun oscSequencesAreSkipped() {
        assertEquals("text", buffer("\u001B]0;title\u0007text").text())
        assertEquals("text", buffer("\u001B]0;title\u001B\\text").text())
    }

    @Test
    fun privateModesAndCharsetsAreIgnored() {
        assertEquals("hi", buffer("\u001B[?2004h\u001B(Bhi\u001B[?25l").text())
    }

    @Test
    fun sequenceSplitAcrossFeeds() {
        val b = TerminalBuffer()
        b.feed("\u001B[3")
        b.feed("1mred")
        assertEquals(AnsiPalette.color(1), b.lineAt(0).styles[0].foreground)
        assertEquals("red", b.text())
    }

    @Test
    fun oldestLinesAreTrimmed() {
        val b = buffer((1..10).joinToString("\n"), maxLines = 3)
        assertEquals("8\n9\n10", b.text())
        assertEquals(3, b.lineCount)
    }

    @Test
    fun clearResetsState() {
        val b = buffer("\u001B[31mabc")
        b.clear()
        b.feed("x")
        assertEquals("x", b.text())
        assertNull(b.lineAt(0).styles[0].foreground)
    }

    @Test
    fun stylesStayInSyncWithText() {
        val b = buffer("abc\u001B[2Kdef\u001B[1P\n\u001B[31mq\u001B[1;1H")
        for (i in 0 until b.lineCount) {
            val line = b.lineAt(i)
            assertEquals(line.text.length, line.styles.size)
        }
    }
}
