package com.mauyaapps.aircode.terminal

data class CellStyle(
    val foreground: Int? = null,
    val background: Int? = null,
    val bold: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false,
)

class StyledLine(val text: String, val styles: List<CellStyle>)

data class ScreenSnapshot(
    val lines: List<StyledLine>,
    val cursorRow: Int,
    val cursorColumn: Int,
    val cursorVisible: Boolean,
    val alternate: Boolean,
)

class TerminalBuffer(private val maxLines: Int = DEFAULT_MAX_LINES) {

    private class Row {
        var codePoints = IntArray(0)
        var styles = arrayOfNulls<CellStyle>(0)
        var length = 0

        fun styleAt(index: Int): CellStyle = styles[index] ?: DEFAULT_STYLE

        fun set(column: Int, codePoint: Int, style: CellStyle) {
            ensure(column + 1)
            while (length < column) {
                codePoints[length] = ' '.code
                styles[length] = null
                length++
            }
            codePoints[column] = codePoint
            styles[column] = style
            if (column >= length) length = column + 1
        }

        fun truncate(column: Int) {
            if (column < length) length = column.coerceAtLeast(0)
        }

        fun fill(from: Int, to: Int, style: CellStyle?) {
            if (style == null) {
                if (from <= 0 && to >= length) length = 0
                else if (to >= length) truncate(from)
                else for (i in from until minOf(to, length)) {
                    codePoints[i] = ' '.code
                    styles[i] = null
                }
                return
            }
            for (i in from until to) set(i, ' '.code, style)
        }

        fun delete(column: Int, count: Int) {
            if (column >= length) return
            val end = minOf(column + count, length)
            val removed = end - column
            System.arraycopy(codePoints, end, codePoints, column, length - end)
            System.arraycopy(styles, end, styles, column, length - end)
            length -= removed
        }

        fun insertBlank(column: Int, count: Int, limit: Int) {
            if (column >= length) return
            val newLength = minOf(length + count, limit.coerceAtLeast(column + 1))
            ensure(newLength)
            val moved = newLength - column - count
            if (moved > 0) {
                System.arraycopy(codePoints, column, codePoints, column + count, moved)
                System.arraycopy(styles, column, styles, column + count, moved)
            }
            for (i in column until minOf(column + count, newLength)) {
                codePoints[i] = ' '.code
                styles[i] = null
            }
            length = newLength
        }

        fun toStyled(): StyledLine {
            val text = StringBuilder(length)
            val list = ArrayList<CellStyle>(length)
            for (i in 0 until length) {
                val cp = codePoints[i]
                if (cp == 0) continue
                val before = text.length
                text.appendCodePoint(cp)
                val style = styleAt(i)
                repeat(text.length - before) { list.add(style) }
            }
            return StyledLine(text.toString(), list)
        }

        private fun ensure(size: Int) {
            if (codePoints.size >= size) return
            val capacity = maxOf(size, codePoints.size * 2, 16)
            codePoints = codePoints.copyOf(capacity)
            styles = styles.copyOf(capacity)
        }
    }

    private enum class State { TEXT, ESCAPE, CSI, STRING, STRING_ESCAPE, CHARSET_G0, CHARSET_G1, SKIP_ONE }

    private inner class Screen(val fixed: Boolean) {
        val lines = ArrayList<Row>()
        var row = 0
        var column = 0
        var pendingWrap = false
        var regionTop = 0
        var regionBottom = 0
        var savedRow = 0
        var savedColumn = 0
        var savedStyle = CellStyle()

        init {
            clear()
        }

        fun clear() {
            lines.clear()
            if (fixed) repeat(rows) { lines.add(Row()) } else lines.add(Row())
            row = 0
            column = 0
            pendingWrap = false
            regionTop = 0
            regionBottom = rows - 1
        }

        fun resize() {
            if (!fixed) return
            while (lines.size < rows) lines.add(Row())
            while (lines.size > rows) lines.removeAt(lines.lastIndex)
            row = row.coerceIn(0, rows - 1)
            column = column.coerceIn(0, cols - 1)
            pendingWrap = false
            regionTop = 0
            regionBottom = rows - 1
        }

        fun screenTop(): Int = if (fixed) 0 else maxOf(0, lines.size - rows)

        fun ensureRow(index: Int) {
            while (lines.size <= index) lines.add(Row())
        }

        fun lineFeed() {
            pendingWrap = false
            if (fixed) {
                if (row == regionBottom) scrollUp(1) else if (row < rows - 1) row++
            } else {
                column = 0
                row++
                ensureRow(row)
                if (lines.size > maxLines) {
                    lines.removeAt(0)
                    row--
                }
            }
        }

        fun reverseIndex() {
            pendingWrap = false
            if (fixed) {
                if (row == regionTop) scrollDown(1) else if (row > 0) row--
            } else if (row > 0) {
                row--
            }
        }

        fun scrollUp(count: Int) {
            if (!fixed) return
            repeat(count.coerceAtMost(regionBottom - regionTop + 1)) {
                lines.removeAt(regionTop)
                lines.add(regionBottom, Row())
            }
        }

        fun scrollDown(count: Int) {
            if (!fixed) return
            repeat(count.coerceAtMost(regionBottom - regionTop + 1)) {
                lines.removeAt(regionBottom)
                lines.add(regionTop, Row())
            }
        }

        fun moveTo(targetRow: Int, targetColumn: Int) {
            pendingWrap = false
            if (fixed) {
                row = targetRow.coerceIn(0, rows - 1)
                column = targetColumn.coerceIn(0, cols - 1)
            } else {
                row = screenTop() + targetRow.coerceIn(0, rows - 1)
                ensureRow(row)
                column = targetColumn.coerceAtLeast(0)
            }
        }

        fun moveRowBy(delta: Int) {
            pendingWrap = false
            if (fixed) {
                val limitTop = if (row >= regionTop) regionTop else 0
                val limitBottom = if (row <= regionBottom) regionBottom else rows - 1
                row = (row + delta).coerceIn(limitTop, limitBottom)
            } else {
                row = (row + delta).coerceIn(0, lines.lastIndex)
            }
        }

        fun moveColumnTo(target: Int) {
            pendingWrap = false
            column = if (fixed) target.coerceIn(0, cols - 1) else target.coerceAtLeast(0)
        }

        fun put(codePoint: Int) {
            val width = CellWidth.of(codePoint)
            if (width == 0) return
            if (fixed) {
                if (pendingWrap && autoWrap) {
                    column = 0
                    lineFeed()
                }
                pendingWrap = false
                if (width == 2 && column == cols - 1) {
                    if (autoWrap) {
                        lines[row].set(column, ' '.code, style)
                        column = 0
                        lineFeed()
                    } else {
                        return
                    }
                }
            }
            val line = lines[row]
            line.set(column, codePoint, style)
            if (width == 2) line.set(column + 1, 0, style)
            column += width
            if (fixed && column >= cols) {
                column = cols - 1
                pendingWrap = autoWrap
            }
        }

        fun tab() {
            pendingWrap = false
            val target = (column / TAB_WIDTH + 1) * TAB_WIDTH
            if (fixed) {
                column = minOf(target, cols - 1)
            } else {
                while (column < target) put(' '.code)
            }
        }

        fun eraseColor(): CellStyle? = style.background?.let { CellStyle(background = it) }

        fun eraseLine(mode: Int) {
            val line = lines[row]
            when (mode) {
                0 -> if (fixed) line.fill(column, cols, eraseColor()) else line.fill(column, Int.MAX_VALUE, eraseColor())
                1 -> line.fill(0, minOf(column + 1, maxOf(line.length, column + 1)), eraseColor() ?: DEFAULT_STYLE)
                2 -> if (fixed) line.fill(0, cols, eraseColor()) else line.fill(0, Int.MAX_VALUE, eraseColor())
            }
            if (mode == 1) trimBlankStyleOnly(line)
        }

        private fun trimBlankStyleOnly(line: Row) {
            while (line.length > 0 && line.codePoints[line.length - 1] == ' '.code && line.styles[line.length - 1] == DEFAULT_STYLE) {
                line.styles[line.length - 1] = null
                if (line.length - 1 >= column + 1) line.length-- else break
            }
        }

        fun eraseDisplay(mode: Int) {
            when (mode) {
                0 -> {
                    eraseLine(0)
                    if (fixed) {
                        for (i in row + 1 until rows) lines[i].fill(0, cols, eraseColor())
                    } else {
                        while (lines.size > row + 1) lines.removeAt(lines.lastIndex)
                    }
                }
                1 -> {
                    eraseLine(1)
                    if (fixed) for (i in 0 until row) lines[i].fill(0, cols, eraseColor())
                }
                2, 3 -> if (fixed) {
                    for (i in 0 until rows) lines[i].fill(0, cols, eraseColor())
                } else {
                    clear()
                }
            }
        }

        fun eraseChars(count: Int) {
            lines[row].fill(column, column + count, eraseColor())
        }

        fun deleteChars(count: Int) {
            lines[row].delete(column, count)
        }

        fun insertChars(count: Int) {
            lines[row].insertBlank(column, count, if (fixed) cols else Int.MAX_VALUE / 2)
        }

        fun insertLines(count: Int) {
            if (fixed) {
                if (row < regionTop || row > regionBottom) return
                repeat(count.coerceAtMost(regionBottom - row + 1)) {
                    lines.removeAt(regionBottom)
                    lines.add(row, Row())
                }
            } else {
                repeat(count) { lines.add(row, Row()) }
                while (lines.size > maxLines) {
                    lines.removeAt(0)
                    row--
                }
            }
            column = 0
            pendingWrap = false
        }

        fun deleteLines(count: Int) {
            if (fixed) {
                if (row < regionTop || row > regionBottom) return
                repeat(count.coerceAtMost(regionBottom - row + 1)) {
                    lines.removeAt(row)
                    lines.add(regionBottom, Row())
                }
            } else {
                repeat(count) { if (lines.size > 1 && row < lines.size) lines.removeAt(row) }
                if (row >= lines.size) row = lines.lastIndex
            }
            column = 0
            pendingWrap = false
        }

        fun setRegion(top: Int, bottom: Int) {
            if (!fixed) return
            val t = top.coerceIn(0, rows - 1)
            val b = bottom.coerceIn(0, rows - 1)
            if (t < b) {
                regionTop = t
                regionBottom = b
            } else {
                regionTop = 0
                regionBottom = rows - 1
            }
            moveTo(0, 0)
        }

        fun saveCursor() {
            savedRow = row - screenTop()
            savedColumn = column
            savedStyle = style
        }

        fun restoreCursor() {
            moveTo(savedRow, savedColumn)
            style = savedStyle
        }
    }

    private val lock = Any()
    private val params = StringBuilder()
    private var state = State.TEXT
    private var style = CellStyle()
    private var cols = DEFAULT_COLUMNS
    private var rows = DEFAULT_ROWS
    private var autoWrap = true
    private var highSurrogate = 0
    private var graphicsG0 = false
    private var graphicsG1 = false
    private var shiftedOut = false
    private val primary = Screen(fixed = false)
    private val alternate = Screen(fixed = true)
    private var current = primary

    var responder: ((String) -> Unit)? = null

    var applicationCursorKeys = false
        get() = synchronized(lock) { field }
        private set

    val isAlternateScreen: Boolean
        get() = synchronized(lock) { current === alternate }

    val columns: Int
        get() = synchronized(lock) { cols }

    val rowCount: Int
        get() = synchronized(lock) { rows }

    val lineCount: Int
        get() = synchronized(lock) { current.lines.size }

    fun lineAt(index: Int): StyledLine = synchronized(lock) {
        current.lines.getOrNull(index)?.toStyled() ?: StyledLine("", emptyList())
    }

    fun text(): String = synchronized(lock) { current.lines.joinToString("\n") { it.toStyled().text } }

    fun snapshot(): ScreenSnapshot = synchronized(lock) {
        val screen = current
        ScreenSnapshot(
            lines = screen.lines.map { it.toStyled() },
            cursorRow = screen.row - screen.screenTop(),
            cursorColumn = screen.column,
            cursorVisible = cursorVisible,
            alternate = screen === alternate,
        )
    }

    private var cursorVisible = true

    fun feed(text: CharSequence) {
        synchronized(lock) {
            for (c in text) accept(c)
        }
    }

    fun clear() {
        synchronized(lock) {
            if (current === primary) fullReset()
        }
    }

    fun resize(columns: Int, rowCount: Int) {
        synchronized(lock) {
            cols = columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
            rows = rowCount.coerceIn(MIN_ROWS, MAX_ROWS)
            alternate.resize()
        }
    }

    private fun fullReset() {
        primary.clear()
        alternate.clear()
        current = primary
        style = CellStyle()
        state = State.TEXT
        params.clear()
        autoWrap = true
        cursorVisible = true
        applicationCursorKeys = false
        graphicsG0 = false
        graphicsG1 = false
        shiftedOut = false
        highSurrogate = 0
    }

    private fun accept(c: Char) {
        when (state) {
            State.TEXT -> acceptText(c)
            State.ESCAPE -> acceptEscape(c)
            State.CSI -> acceptCsi(c)
            State.STRING -> when (c) {
                BELL -> state = State.TEXT
                ESC -> state = State.STRING_ESCAPE
            }
            State.STRING_ESCAPE -> state = State.TEXT
            State.CHARSET_G0 -> {
                graphicsG0 = c == '0'
                state = State.TEXT
            }
            State.CHARSET_G1 -> {
                graphicsG1 = c == '0'
                state = State.TEXT
            }
            State.SKIP_ONE -> state = State.TEXT
        }
    }

    private fun acceptText(c: Char) {
        if (Character.isHighSurrogate(c)) {
            highSurrogate = c.code
            return
        }
        if (Character.isLowSurrogate(c)) {
            if (highSurrogate != 0) {
                current.put(Character.toCodePoint(highSurrogate.toChar(), c))
                highSurrogate = 0
            }
            return
        }
        highSurrogate = 0
        when (c) {
            ESC -> state = State.ESCAPE
            '\n', '\u000B', '\u000C' -> current.lineFeed()
            '\r' -> {
                current.column = 0
                current.pendingWrap = false
            }
            '\b' -> if (current.column > 0) {
                current.column--
                current.pendingWrap = false
            }
            '\t' -> current.tab()
            SHIFT_OUT -> shiftedOut = true
            SHIFT_IN -> shiftedOut = false
            else -> if (c >= ' ' && c != DELETE) current.put(mapCharset(c.code))
        }
    }

    private fun mapCharset(code: Int): Int {
        val graphics = if (shiftedOut) graphicsG1 else graphicsG0
        if (!graphics || code !in 0x60..0x7E) return code
        return DEC_GRAPHICS[code - 0x60]
    }

    private fun acceptEscape(c: Char) {
        state = State.TEXT
        when (c) {
            '[' -> {
                params.clear()
                state = State.CSI
            }
            ']', 'P', '_', '^', 'X' -> state = State.STRING
            '(' -> state = State.CHARSET_G0
            ')' -> state = State.CHARSET_G1
            '#', '%', '*', '+' -> state = State.SKIP_ONE
            '7' -> current.saveCursor()
            '8' -> current.restoreCursor()
            'M' -> current.reverseIndex()
            'D' -> {
                val keep = current.column
                current.lineFeed()
                if (current.fixed) current.column = keep
            }
            'E' -> {
                current.column = 0
                current.lineFeed()
            }
            'c' -> fullReset()
        }
    }

    private fun acceptCsi(c: Char) {
        when {
            c in ' '..'?' -> params.append(c)
            c in '@'..'~' -> {
                csi(c, params.toString())
                state = State.TEXT
            }
            else -> state = State.TEXT
        }
    }

    private fun csi(terminator: Char, raw: String) {
        val prefix = raw.firstOrNull()?.takeIf { it in "?>=!<" }
        val body = if (prefix != null) raw.substring(1) else raw
        if (prefix != null) {
            when {
                prefix == '?' && (terminator == 'h' || terminator == 'l') -> privateMode(body, terminator == 'h')
                prefix == '>' && terminator == 'c' -> respond("\u001B[>0;115;0c")
            }
            return
        }
        if (body.isNotEmpty() && body.last() == ' ') return
        val numbers = body.replace(':', ';').split(';').map { it.toIntOrNull() }
        val first = numbers.firstOrNull()
        val count = if (first == null || first < 1) 1 else first
        val screen = current
        when (terminator) {
            'm' -> applySgr(numbers)
            'H', 'f' -> screen.moveTo((numbers.getOrNull(0) ?: 1).coerceAtLeast(1) - 1, (numbers.getOrNull(1) ?: 1).coerceAtLeast(1) - 1)
            'A' -> screen.moveRowBy(-count)
            'B', 'e' -> screen.moveRowBy(count)
            'C', 'a' -> screen.moveColumnTo(screen.column + count)
            'D' -> screen.moveColumnTo(screen.column - count)
            'E' -> {
                screen.moveRowBy(count)
                screen.moveColumnTo(0)
            }
            'F' -> {
                screen.moveRowBy(-count)
                screen.moveColumnTo(0)
            }
            'G', '`' -> screen.moveColumnTo(count - 1)
            'd' -> screen.moveTo(count - 1, screen.column)
            'J' -> screen.eraseDisplay(first ?: 0)
            'K' -> screen.eraseLine(first ?: 0)
            'X' -> screen.eraseChars(count)
            'P' -> screen.deleteChars(count)
            '@' -> screen.insertChars(count)
            'L' -> screen.insertLines(count)
            'M' -> screen.deleteLines(count)
            'S' -> screen.scrollUp(count)
            'T' -> screen.scrollDown(count)
            'r' -> screen.setRegion((numbers.getOrNull(0) ?: 1).coerceAtLeast(1) - 1, (numbers.getOrNull(1) ?: rows).coerceAtLeast(1) - 1)
            's' -> screen.saveCursor()
            'u' -> screen.restoreCursor()
            'c' -> if ((first ?: 0) == 0) respond("\u001B[?62;c")
            'n' -> when (first) {
                5 -> respond("\u001B[0n")
                6 -> respond("\u001B[${screen.row - screen.screenTop() + 1};${screen.column + 1}R")
            }
        }
    }

    private fun privateMode(body: String, enable: Boolean) {
        for (part in body.split(';')) {
            when (part.toIntOrNull()) {
                1 -> applicationCursorKeys = enable
                7 -> autoWrap = enable
                25 -> cursorVisible = enable
                47, 1047 -> switchScreen(enable, saveCursor = false)
                1049 -> switchScreen(enable, saveCursor = true)
            }
        }
    }

    private fun switchScreen(toAlternate: Boolean, saveCursor: Boolean) {
        if (toAlternate == (current === alternate)) return
        if (toAlternate) {
            if (saveCursor) primary.saveCursor()
            alternate.clear()
            current = alternate
        } else {
            current = primary
            if (saveCursor) primary.restoreCursor()
        }
    }

    private fun respond(text: String) {
        responder?.invoke(text)
    }

    private fun applySgr(numbers: List<Int?>) {
        val codes = if (numbers.all { it == null }) listOf(0) else numbers.map { it ?: 0 }
        var i = 0
        while (i < codes.size) {
            when (val code = codes[i]) {
                0 -> style = CellStyle()
                1 -> style = style.copy(bold = true)
                4 -> style = style.copy(underline = true)
                7 -> style = style.copy(inverse = true)
                22 -> style = style.copy(bold = false)
                24 -> style = style.copy(underline = false)
                27 -> style = style.copy(inverse = false)
                in 30..37 -> style = style.copy(foreground = AnsiPalette.color(code - 30))
                39 -> style = style.copy(foreground = null)
                in 40..47 -> style = style.copy(background = AnsiPalette.color(code - 40))
                49 -> style = style.copy(background = null)
                in 90..97 -> style = style.copy(foreground = AnsiPalette.color(code - 90 + 8))
                in 100..107 -> style = style.copy(background = AnsiPalette.color(code - 100 + 8))
                38, 48 -> {
                    val parsed = extendedColor(codes, i)
                    if (parsed != null) {
                        style = if (code == 38) style.copy(foreground = parsed.first) else style.copy(background = parsed.first)
                        i += parsed.second
                    }
                }
            }
            i++
        }
    }

    private fun extendedColor(codes: List<Int>, at: Int): Pair<Int, Int>? {
        return when (codes.getOrNull(at + 1)) {
            5 -> codes.getOrNull(at + 2)?.let { AnsiPalette.color(it) to 2 }
            2 -> {
                val r = codes.getOrNull(at + 2) ?: return null
                val g = codes.getOrNull(at + 3) ?: return null
                val b = codes.getOrNull(at + 4) ?: return null
                AnsiPalette.rgb(r, g, b) to 4
            }
            else -> null
        }
    }

    companion object {
        const val DEFAULT_MAX_LINES = 5000
        const val DEFAULT_COLUMNS = 80
        const val DEFAULT_ROWS = 24
        const val MIN_COLUMNS = 10
        const val MAX_COLUMNS = 400
        const val MIN_ROWS = 3
        const val MAX_ROWS = 200
        private const val TAB_WIDTH = 8
        private const val ESC = '\u001B'
        private const val BELL = '\u0007'
        private const val DELETE = '\u007F'
        private const val SHIFT_OUT = '\u000E'
        private const val SHIFT_IN = '\u000F'
        private val DEFAULT_STYLE = CellStyle()

        private val DEC_GRAPHICS = intArrayOf(
            0x25C6, 0x2592, 0x2409, 0x240C, 0x240D, 0x240A, 0x00B0, 0x00B1,
            0x2424, 0x240B, 0x2518, 0x2510, 0x250C, 0x2514, 0x253C, 0x23BA,
            0x23BB, 0x2500, 0x23BC, 0x23BD, 0x251C, 0x2524, 0x2534, 0x252C,
            0x2502, 0x2264, 0x2265, 0x03C0, 0x2260, 0x00A3, 0x00B7,
        )
    }
}

object CellWidth {

    private val wideRanges = arrayOf(
        0x1100..0x115F, 0x2E80..0x303E, 0x3041..0x33FF, 0x3400..0x4DBF,
        0x4E00..0x9FFF, 0xA000..0xA4CF, 0xAC00..0xD7A3, 0xF900..0xFAFF,
        0xFE30..0xFE6F, 0xFF00..0xFF60, 0xFFE0..0xFFE6, 0x1F300..0x1F64F,
        0x1F680..0x1F6FF, 0x1F900..0x1F9FF, 0x20000..0x3FFFD,
    )

    private val zeroRanges = arrayOf(
        0x0300..0x036F, 0x200B..0x200F, 0x2060..0x2064, 0x20D0..0x20FF, 0xFE00..0xFE0F,
    )

    fun of(codePoint: Int): Int = when {
        codePoint < 0x20 -> 0
        codePoint < 0x300 -> 1
        zeroRanges.any { codePoint in it } -> 0
        wideRanges.any { codePoint in it } -> 2
        else -> 1
    }
}

object AnsiPalette {

    private val base = intArrayOf(
        0xFF000000.toInt(), 0xFFCD3131.toInt(), 0xFF0DBC79.toInt(), 0xFFE5E510.toInt(),
        0xFF2472C8.toInt(), 0xFFBC3FBC.toInt(), 0xFF11A8CD.toInt(), 0xFFE5E5E5.toInt(),
        0xFF666666.toInt(), 0xFFF14C4C.toInt(), 0xFF23D18B.toInt(), 0xFFF5F543.toInt(),
        0xFF3B8EEA.toInt(), 0xFFD670D6.toInt(), 0xFF29B8DB.toInt(), 0xFFFFFFFF.toInt(),
    )

    private val cubeLevels = intArrayOf(0, 95, 135, 175, 215, 255)

    fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    fun color(index: Int): Int = when {
        index < 0 -> base[0]
        index < 16 -> base[index]
        index < 232 -> {
            val n = index - 16
            rgb(cubeLevels[n / 36], cubeLevels[(n / 6) % 6], cubeLevels[n % 6])
        }
        index < 256 -> {
            val level = 8 + (index - 232) * 10
            rgb(level, level, level)
        }
        else -> base[7]
    }
}
