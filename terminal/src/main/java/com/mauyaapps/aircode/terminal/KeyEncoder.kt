package com.mauyaapps.aircode.terminal

enum class TerminalKey { UP, DOWN, LEFT, RIGHT, HOME, END, PAGE_UP, PAGE_DOWN, INSERT, DELETE, ESCAPE, TAB, BACKSPACE, ENTER }

object KeyEncoder {

    private const val ESC = "\u001B"

    fun key(key: TerminalKey, applicationCursor: Boolean, ctrl: Boolean = false, alt: Boolean = false, shift: Boolean = false): String {
        val modifier = 1 + (if (shift) 1 else 0) + (if (alt) 2 else 0) + (if (ctrl) 4 else 0)
        val plain = modifier == 1
        val cursorIntro = if (applicationCursor) "${ESC}O" else "$ESC["
        val sequence = when (key) {
            TerminalKey.UP -> arrow('A', plain, modifier, cursorIntro)
            TerminalKey.DOWN -> arrow('B', plain, modifier, cursorIntro)
            TerminalKey.RIGHT -> arrow('C', plain, modifier, cursorIntro)
            TerminalKey.LEFT -> arrow('D', plain, modifier, cursorIntro)
            TerminalKey.HOME -> arrow('H', plain, modifier, cursorIntro)
            TerminalKey.END -> arrow('F', plain, modifier, cursorIntro)
            TerminalKey.PAGE_UP -> tilde(5, plain, modifier)
            TerminalKey.PAGE_DOWN -> tilde(6, plain, modifier)
            TerminalKey.INSERT -> tilde(2, plain, modifier)
            TerminalKey.DELETE -> tilde(3, plain, modifier)
            TerminalKey.ESCAPE -> ESC
            TerminalKey.TAB -> if (shift) "$ESC[Z" else "\t"
            TerminalKey.BACKSPACE -> "\u007F"
            TerminalKey.ENTER -> "\r"
        }
        return if (alt && key in prefixOnly) ESC + sequence else sequence
    }

    fun function(number: Int, ctrl: Boolean = false, alt: Boolean = false, shift: Boolean = false): String {
        val modifier = 1 + (if (shift) 1 else 0) + (if (alt) 2 else 0) + (if (ctrl) 4 else 0)
        return when (number) {
            1, 2, 3, 4 -> {
                val letter = "PQRS"[number - 1]
                if (modifier == 1) "${ESC}O$letter" else "$ESC[1;$modifier$letter"
            }
            else -> {
                val code = when (number) {
                    5 -> 15
                    6 -> 17
                    7 -> 18
                    8 -> 19
                    9 -> 20
                    10 -> 21
                    11 -> 23
                    12 -> 24
                    else -> return ""
                }
                if (modifier == 1) "$ESC[$code~" else "$ESC[$code;$modifier~"
            }
        }
    }

    fun character(char: Char, ctrl: Boolean, alt: Boolean): String {
        val base = if (ctrl) controlOf(char)?.toString() ?: char.toString() else char.toString()
        return if (alt) ESC + base else base
    }

    fun controlOf(char: Char): Char? = when (char) {
        in 'a'..'z' -> (char.code - 'a'.code + 1).toChar()
        in 'A'..'Z' -> (char.code - 'A'.code + 1).toChar()
        '@', ' ' -> '\u0000'
        '[' -> '\u001B'
        '\\' -> '\u001C'
        ']' -> '\u001D'
        '^' -> '\u001E'
        '_' -> '\u001F'
        '?' -> '\u007F'
        else -> null
    }

    private val prefixOnly = setOf(TerminalKey.BACKSPACE, TerminalKey.TAB, TerminalKey.ENTER)

    private fun arrow(letter: Char, plain: Boolean, modifier: Int, intro: String): String =
        if (plain) "$intro$letter" else "$ESC[1;$modifier$letter"

    private fun tilde(code: Int, plain: Boolean, modifier: Int): String =
        if (plain) "$ESC[$code~" else "$ESC[$code;$modifier~"
}
