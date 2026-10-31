package com.mauyaapps.aircode.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FullScreenTest {

    private val esc = "\u001B"

    private fun screen(cols: Int = 20, rows: Int = 5, input: String = ""): TerminalBuffer =
        TerminalBuffer().apply {
            resize(cols, rows)
            feed(input)
        }

    private fun rows(b: TerminalBuffer): List<String> = b.snapshot().lines.map { it.text.trimEnd() }

    @Test
    fun alternateScreenIsSeparateAndRestoresPrimary() {
        val b = screen(input = "prompt\n$esc[?1049hvim$esc[?1049l")
        assertFalse(b.isAlternateScreen)
        assertEquals("prompt\n", b.text())
    }

    @Test
    fun alternateScreenIsFixedSizeGrid() {
        val b = screen(input = "$esc[?1049h")
        assertTrue(b.isAlternateScreen)
        assertEquals(5, b.lineCount)
        assertEquals(5, b.snapshot().lines.size)
    }

    @Test
    fun cursorPositioning() {
        val b = screen(input = "$esc[?1049h$esc[2;5HX$esc[5;1HY")
        assertEquals(listOf("", "    X", "", "", "Y"), rows(b))
    }

    @Test
    fun cursorPositionDefaultsToHome() {
        val b = screen(input = "$esc[?1049h$esc[3;3Hzz$esc[HA")
        assertEquals("A", rows(b)[0])
        assertEquals("  zz", rows(b)[2])
    }

    @Test
    fun autoWrapUsesPendingWrapSemantics() {
        val b = screen(cols = 10, rows = 3, input = "$esc[?1049h1234567890")
        assertEquals("1234567890", rows(b)[0])
        assertEquals(0, b.snapshot().cursorRow)
        assertEquals(9, b.snapshot().cursorColumn)
        b.feed("A")
        assertEquals("A", rows(b)[1])
    }

    @Test
    fun noWrapWhenDisabled() {
        val b = screen(cols = 10, rows = 3, input = "$esc[?1049h$esc[?7l12345678901234")
        assertEquals("1234567894", rows(b)[0])
        assertEquals("", rows(b)[1])
    }

    @Test
    fun scrollingAtBottomMovesContentUp() {
        val b = screen(cols = 10, rows = 3, input = "$esc[?1049ha\r\nb\r\nc\r\nd")
        assertEquals(listOf("b", "c", "d"), rows(b))
    }

    @Test
    fun scrollRegionKeepsOutsideRowsFixed() {
        val b = screen(cols = 10, rows = 5, input = "$esc[?1049h$esc[1;1Htop$esc[5;1Hbot$esc[2;4r$esc[2;1Ha\r\nb\r\nc\r\nd")
        assertEquals(listOf("top", "b", "c", "d", "bot"), rows(b))
    }

    @Test
    fun reverseIndexScrollsDownAtTop() {
        val b = screen(cols = 10, rows = 3, input = "$esc[?1049h1\r\n2\r\n3$esc[1;1H${esc}M")
        assertEquals(listOf("", "1", "2"), rows(b))
    }

    @Test
    fun insertAndDeleteLines() {
        val b = screen(cols = 10, rows = 4, input = "$esc[?1049h1\r\n2\r\n3\r\n4$esc[2;1H$esc[1L")
        assertEquals(listOf("1", "", "2", "3"), rows(b))
        b.feed("$esc[2M")
        assertEquals(listOf("1", "3", "", ""), rows(b))
    }

    @Test
    fun scrollUpAndDownSequences() {
        val b = screen(cols = 10, rows = 3, input = "$esc[?1049h1\r\n2\r\n3$esc[1S")
        assertEquals(listOf("2", "3", ""), rows(b))
        b.feed("$esc[1T")
        assertEquals(listOf("", "2", "3"), rows(b))
    }

    @Test
    fun eraseInDisplayVariants() {
        val b = screen(cols = 10, rows = 3, input = "$esc[?1049haaa\r\nbbb\r\nccc$esc[2;2H$esc[J")
        assertEquals(listOf("aaa", "b", ""), rows(b))
        val c = screen(cols = 10, rows = 3, input = "$esc[?1049haaa\r\nbbb\r\nccc$esc[2;2H$esc[1J")
        assertEquals(listOf("", "  b", "ccc"), rows(c))
        val d = screen(cols = 10, rows = 3, input = "$esc[?1049haaa\r\nbbb$esc[2J")
        assertEquals(listOf("", "", ""), rows(d))
    }

    @Test
    fun backgroundColorEraseFillsLine() {
        val b = screen(cols = 10, rows = 2, input = "$esc[?1049h$esc[44m$esc[K")
        val line = b.snapshot().lines[0]
        assertEquals(10, line.text.length)
        assertEquals(AnsiPalette.color(4), line.styles[9].background)
    }

    @Test
    fun insertDeleteEraseCharacters() {
        val b = screen(cols = 10, rows = 2, input = "$esc[?1049habcdef$esc[1;3H$esc[2@")
        assertEquals("ab  cdef", rows(b)[0])
        b.feed("$esc[2P")
        assertEquals("abcdef", rows(b)[0])
        b.feed("$esc[1;2H$esc[2X")
        assertEquals("a  def", rows(b)[0])
    }

    @Test
    fun saveAndRestoreCursor() {
        val b = screen(cols = 10, rows = 3, input = "$esc[?1049h${esc}7$esc[3;5Hx${esc}8y")
        assertEquals("y", rows(b)[0])
        assertEquals("    x", rows(b)[2])
    }

    @Test
    fun enteringAndLeaving1049PreservesPrimaryCursor() {
        val b = screen(input = "abc$esc[?1049h$esc[3;3Hjunk$esc[?1049l")
        b.feed("def")
        assertEquals("abcdef", b.text())
    }

    @Test
    fun applicationCursorKeysAndCursorVisibility() {
        val b = screen(input = "$esc[?1h$esc[?25l")
        assertTrue(b.applicationCursorKeys)
        assertFalse(b.snapshot().cursorVisible)
        b.feed("$esc[?1l$esc[?25h")
        assertFalse(b.applicationCursorKeys)
        assertTrue(b.snapshot().cursorVisible)
    }

    @Test
    fun deviceReports() {
        val replies = ArrayList<String>()
        val b = screen(input = "")
        b.responder = { replies.add(it) }
        b.feed("ab$esc[6n$esc[5n$esc[c$esc[>c")
        assertEquals("$esc[1;3R", replies[0])
        assertEquals("$esc[0n", replies[1])
        assertEquals("$esc[?62;c", replies[2])
        assertTrue(replies[3].startsWith("$esc[>"))
    }

    @Test
    fun cursorReportInAlternateScreenIsRelativeToGrid() {
        val replies = ArrayList<String>()
        val b = screen(input = "$esc[?1049h")
        b.responder = { replies.add(it) }
        b.feed("$esc[4;7H$esc[6n")
        assertEquals("$esc[4;7R", replies.single())
    }

    @Test
    fun lineDrawingCharset() {
        val b = screen(input = "$esc[?1049h$esc(0lqk${esc}(Bq")
        assertEquals("┌─┐q", rows(b)[0])
    }

    @Test
    fun wideCharactersTakeTwoCells() {
        val b = screen(cols = 10, rows = 2, input = "$esc[?1049h日本a")
        assertEquals("日本a", rows(b)[0])
        assertEquals(5, b.snapshot().cursorColumn)
    }

    @Test
    fun emojiSurrogatePairsAreOneCell() {
        val b = screen(cols = 10, rows = 2, input = "$esc[?1049h😀x")
        assertEquals("😀x", rows(b)[0])
        assertEquals(3, b.snapshot().cursorColumn)
    }

    @Test
    fun resizeKeepsGridConsistent() {
        val b = screen(cols = 20, rows = 6, input = "$esc[?1049h$esc[6;20Hx")
        b.resize(10, 3)
        val snap = b.snapshot()
        assertEquals(3, snap.lines.size)
        assertTrue(snap.cursorRow in 0..2)
        assertTrue(snap.cursorColumn in 0..9)
        assertNull(snap.lines.firstOrNull { it.styles.size != it.text.length })
    }

    @Test
    fun primaryScreenKeepsScrollbackAndHomeIsRelativeToLastRows() {
        val b = screen(rows = 3, input = "1\n2\n3\n4\n5$esc[HX")
        assertEquals("1\n2\nX\n4\n5", b.text())
    }

    @Test
    fun htopStyleRedrawDoesNotCorruptStyles() {
        val frame = "$esc[?1049h$esc[H$esc[2J$esc[1;1H$esc[42m  CPU $esc[0m[|||   ]$esc[2;1H$esc[7mF1Help$esc[0m$esc[K"
        val b = screen(cols = 30, rows = 4, input = frame)
        val lines = b.snapshot().lines
        lines.forEach { assertEquals(it.text.length, it.styles.size) }
        assertEquals("  CPU [|||   ]", lines[0].text.trimEnd())
        assertTrue(lines[1].styles[0].inverse)
    }
}
