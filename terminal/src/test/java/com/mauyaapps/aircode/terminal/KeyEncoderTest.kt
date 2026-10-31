package com.mauyaapps.aircode.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyEncoderTest {

    @Test
    fun arrowsFollowCursorMode() {
        assertEquals("\u001B[A", KeyEncoder.key(TerminalKey.UP, applicationCursor = false))
        assertEquals("\u001BOA", KeyEncoder.key(TerminalKey.UP, applicationCursor = true))
        assertEquals("\u001B[D", KeyEncoder.key(TerminalKey.LEFT, applicationCursor = false))
        assertEquals("\u001BOH", KeyEncoder.key(TerminalKey.HOME, applicationCursor = true))
    }

    @Test
    fun modifiedArrowsUseCsiForm() {
        assertEquals("\u001B[1;5C", KeyEncoder.key(TerminalKey.RIGHT, applicationCursor = true, ctrl = true))
        assertEquals("\u001B[1;2A", KeyEncoder.key(TerminalKey.UP, applicationCursor = false, shift = true))
        assertEquals("\u001B[1;3D", KeyEncoder.key(TerminalKey.LEFT, applicationCursor = false, alt = true))
    }

    @Test
    fun editingKeys() {
        assertEquals("\u001B[5~", KeyEncoder.key(TerminalKey.PAGE_UP, false))
        assertEquals("\u001B[6~", KeyEncoder.key(TerminalKey.PAGE_DOWN, false))
        assertEquals("\u001B[3~", KeyEncoder.key(TerminalKey.DELETE, false))
        assertEquals("\u001B[3;5~", KeyEncoder.key(TerminalKey.DELETE, false, ctrl = true))
        assertEquals("\u007F", KeyEncoder.key(TerminalKey.BACKSPACE, false))
        assertEquals("\u001B[Z", KeyEncoder.key(TerminalKey.TAB, false, shift = true))
    }

    @Test
    fun functionKeys() {
        assertEquals("\u001BOP", KeyEncoder.function(1))
        assertEquals("\u001B[15~", KeyEncoder.function(5))
        assertEquals("\u001B[24~", KeyEncoder.function(12))
        assertEquals("\u001B[1;5Q", KeyEncoder.function(2, ctrl = true))
        assertEquals("", KeyEncoder.function(13))
    }

    @Test
    fun controlAndAltCharacters() {
        assertEquals("\u0003", KeyEncoder.character('c', ctrl = true, alt = false))
        assertEquals("\u0018", KeyEncoder.character('X', ctrl = true, alt = false))
        assertEquals("\u001Bx", KeyEncoder.character('x', ctrl = false, alt = true))
        assertEquals("\u001B\u0003", KeyEncoder.character('c', ctrl = true, alt = true))
        assertEquals("7", KeyEncoder.character('7', ctrl = true, alt = false))
        assertEquals("\u007F", KeyEncoder.character('?', ctrl = true, alt = false))
    }
}
