package com.mauyaapps.aircode.editor

data class Fix(val label: String, val start: Int, val end: Int, val replacement: String)

data class Diagnostic(val start: Int, val end: Int, val message: String, val fixes: List<Fix>)

object CodeAnalyzer {

    private const val MAX_DIAGNOSTICS = 200

    private val pythonBlockWords = setOf(
        "if", "elif", "else", "for", "while", "def", "class", "try", "except", "finally", "with",
    )

    private val controlWords = setOf(
        "if", "else", "for", "foreach", "while", "switch", "case", "default", "do", "try", "catch",
        "finally", "class", "struct", "enum", "interface", "namespace", "union", "lock", "fixed",
        "unsafe", "checked", "unchecked", "synchronized",
    )

    private val statementWords = setOf("return", "break", "continue", "throw", "goto", "yield", "delete")

    private val introducers = setOf(
        "def", "class", "action", "task", "struct", "function", "var", "let", "const", "import", "from",
        "as", "namespace", "using", "package", "enum", "interface", "record",
    )

    private val callWords = mapOf(
        "cream" to setOf(
            "math", "num", "rand", "stats", "convert", "regex", "list", "table", "file", "folder",
            "encode", "net", "date", "length", "sum", "min", "max", "abs", "round", "range", "sort",
            "reverse", "first", "last", "join", "split", "upper", "lower", "trim", "contains", "number",
            "bool", "input",
        ),
        "python" to setOf(
            "print", "input", "range", "len", "open", "int", "str", "float", "list", "dict", "set",
            "tuple", "sorted", "sum", "min", "max", "abs", "round", "enumerate", "zip", "map", "filter",
            "isinstance", "reversed", "format",
        ),
        "javascript" to setOf("parseInt", "parseFloat", "setTimeout", "setInterval", "require", "fetch"),
        "cpp" to setOf("printf", "scanf", "malloc", "free", "strlen", "strcpy", "strcmp", "puts", "fopen", "fclose"),
        "objectivec" to setOf("printf", "scanf", "malloc", "free", "strlen", "strcpy", "strcmp", "puts", "NSLog"),
    )

    private val cLikeLanguages = setOf("java", "cpp", "csharp", "objectivec")

    fun analyze(text: String, language: String): List<Diagnostic> {
        if (!SyntaxHighlighter.supports(language) || text.isEmpty()) return emptyList()
        val tokens = SyntaxHighlighter.tokenize(text, language)
        val n = text.length
        val skip = BooleanArray(n)
        val comment = BooleanArray(n)
        for (token in tokens) {
            val preprocessor = token.kind == TokenKind.KEYWORD && text[token.start] == '#'
            if (token.kind == TokenKind.COMMENT || token.kind == TokenKind.STRING || preprocessor) {
                for (i in token.start until token.end) skip[i] = true
            }
            if (token.kind == TokenKind.COMMENT) {
                for (i in token.start until token.end) comment[i] = true
            }
        }
        val found = ArrayList<Diagnostic>()
        val scan = scanBrackets(text, skip, comment, found)
        unterminated(text, tokens, language, found)
        typos(text, language, skip, found)
        if (language == "python") pythonColons(text, comment, skip, scan, found)
        if (language in cLikeLanguages) semicolons(text, comment, skip, scan, found)
        return found.sortedBy { it.start }.take(MAX_DIAGNOSTICS)
    }

    private class Scan(val depth: IntArray, val paren: IntArray)

    private fun closerOf(open: Char): Char = when (open) {
        '(' -> ')'
        '[' -> ']'
        else -> '}'
    }

    private fun scanBrackets(text: String, skip: BooleanArray, comment: BooleanArray, out: MutableList<Diagnostic>): Scan {
        val n = text.length
        val depth = IntArray(n)
        val paren = IntArray(n)
        val stack = ArrayList<Int>()
        var parenCount = 0
        for (i in 0 until n) {
            if (!skip[i]) {
                val c = text[i]
                if (c == '(' || c == '[' || c == '{') {
                    stack += i
                    if (c != '{') parenCount++
                } else if (c == ')' || c == ']' || c == '}') {
                    if (c != '}' && parenCount > 0) parenCount--
                    if (stack.isEmpty()) {
                        out += Diagnostic(
                            i, i + 1, "Лишняя закрывающая скобка «$c»",
                            listOf(Fix("Удалить «$c»", i, i + 1, "")),
                        )
                    } else {
                        val open = stack.removeAt(stack.lastIndex)
                        val expected = closerOf(text[open])
                        if (expected != c) {
                            out += Diagnostic(
                                i, i + 1, "Здесь ожидалась «$expected», а стоит «$c»",
                                listOf(
                                    Fix("Заменить на «$expected»", i, i + 1, expected.toString()),
                                    Fix("Удалить «$c»", i, i + 1, ""),
                                ),
                            )
                        }
                    }
                }
            }
            depth[i] = stack.size
            paren[i] = parenCount
        }
        for (open in stack) {
            val c = text[open]
            val closer = closerOf(c)
            val fix = if (c == '{') {
                val suffix = if (text.endsWith("\n")) "}" else "\n}"
                Fix("Добавить «}» в конец файла", n, n, suffix)
            } else {
                val at = contentEnd(text, open, lineEnd(text, open), comment)
                Fix("Добавить «$closer» в конец строки", at, at, closer.toString())
            }
            out += Diagnostic(open, open + 1, "Не закрыта скобка «$c»", listOf(fix))
        }
        return Scan(depth, paren)
    }

    private fun unterminated(text: String, tokens: List<Token>, language: String, out: MutableList<Diagnostic>) {
        val hasBlock = language != "python" && language != "cream"
        for (token in tokens) {
            val raw = text.substring(token.start, token.end)
            if (token.kind == TokenKind.STRING) {
                val quote = raw[0]
                val triple = SyntaxHighlighter.hasTripleQuotes(language) && raw.startsWith("$quote$quote$quote")
                val closed = if (triple) {
                    raw.length >= 6 && raw.endsWith("$quote$quote$quote")
                } else {
                    raw.length >= 2 && raw.last() == quote && !escaped(raw, raw.length - 1)
                }
                if (!closed) {
                    val closing = if (triple) "$quote$quote$quote" else quote.toString()
                    val name = if (triple) "тройные кавычки" else "кавычку «$quote»"
                    out += Diagnostic(
                        token.start, token.end, "Строка не закрыта",
                        listOf(Fix("Добавить $name", token.end, token.end, closing)),
                    )
                }
            } else if (token.kind == TokenKind.COMMENT && hasBlock && raw.startsWith("/*")) {
                if (raw.length < 4 || !raw.endsWith("*/")) {
                    out += Diagnostic(
                        token.start, minOf(token.start + 2, token.end), "Комментарий не закрыт",
                        listOf(Fix("Добавить «*/»", token.end, token.end, "*/")),
                    )
                }
            }
        }
    }

    private fun escaped(raw: String, index: Int): Boolean {
        var count = 0
        var j = index - 1
        while (j > 0 && raw[j] == '\\') {
            count++
            j--
        }
        return count % 2 == 1
    }

    private fun lineStart(text: String, index: Int): Int = text.lastIndexOf('\n', index - 1) + 1

    private fun lineEnd(text: String, index: Int): Int {
        val end = text.indexOf('\n', index)
        return if (end < 0) text.length else end
    }

    private fun contentEnd(text: String, start: Int, end: Int, comment: BooleanArray): Int {
        var j = end - 1
        while (j >= start && (text[j].isWhitespace() || (j < comment.size && comment[j]))) j--
        return j + 1
    }

    private fun contentStart(text: String, start: Int, end: Int): Int {
        var j = start
        while (j < end && text[j].isWhitespace()) j++
        return j
    }

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '_'

    private fun wordAt(text: String, from: Int, limit: Int): String {
        var j = from
        while (j < limit && isWordChar(text[j])) j++
        return text.substring(from, j)
    }

    private fun typos(text: String, language: String, skip: BooleanArray, out: MutableList<Diagnostic>) {
        val keywords = SyntaxHighlighter.vocabulary(language)
        val calls = callWords[language].orEmpty()
        val n = text.length
        class Occurrence(val start: Int, val end: Int, val word: String, val previous: String, val assigned: Boolean, val isCall: Boolean)
        val occurrences = ArrayList<Occurrence>()
        val counts = HashMap<String, Int>()
        var previousWord = ""
        var i = 0
        while (i < n) {
            val c = text[i]
            if (skip[i]) {
                i++
                continue
            }
            if (c.isDigit()) {
                while (i < n && (isWordChar(text[i]) || text[i] == '.')) i++
                previousWord = ""
                continue
            }
            if (c.isLetter() || c == '_') {
                var end = i + 1
                while (end < n && isWordChar(text[end])) end++
                val word = text.substring(i, end)
                counts[word] = (counts[word] ?: 0) + 1
                var k = end
                while (k < n && (text[k] == ' ' || text[k] == '\t')) k++
                val assigned = k < n && text[k] == '=' && (k + 1 >= n || text[k + 1] != '=')
                val isCall = k < n && text[k] == '('
                val dotted = i > 0 && text[i - 1] == '.'
                if (!dotted) occurrences += Occurrence(i, end, word, previousWord, assigned, isCall)
                previousWord = word
                i = end
                continue
            }
            if (!c.isWhitespace()) previousWord = ""
            i++
        }
        for (o in occurrences) {
            if (o.word in keywords || o.word in calls) continue
            if (counts[o.word] != 1 || o.assigned || o.previous in introducers) continue
            val candidates = LinkedHashSet<String>()
            if (o.word.length >= 5) keywords.filter { it.length >= 5 && isTypoOf(o.word, it) }.forEach { candidates += it }
            if (o.isCall && o.word.length >= 4) {
                (calls + keywords).filter { it.length >= 4 && isTypoOf(o.word, it) }.forEach { candidates += it }
            }
            if (candidates.isEmpty()) continue
            out += Diagnostic(
                o.start, o.end, "Возможно, опечатка в «${o.word}»",
                candidates.take(3).map { Fix("Заменить на «$it»", o.start, o.end, it) },
            )
        }
    }

    internal fun isTypoOf(word: String, target: String): Boolean {
        if (word == target) return false
        if (word.length == target.length) {
            var first = -1
            for (i in word.indices) {
                if (word[i] != target[i]) {
                    first = i
                    break
                }
            }
            if (first < 0 || first + 1 >= word.length) return false
            if (word[first] != target[first + 1] || word[first + 1] != target[first]) return false
            return word.substring(first + 2) == target.substring(first + 2)
        }
        val (longer, shorter) = if (word.length > target.length) word to target else target to word
        if (longer.length - shorter.length != 1) return false
        var i = 0
        while (i < shorter.length && longer[i] == shorter[i]) i++
        return longer.substring(i + 1) == shorter.substring(i)
    }

    private fun lastChunkStart(text: String, start: Int, end: Int): Int {
        var j = end
        while (j > start && !text[j - 1].isWhitespace()) j--
        return j
    }

    private fun pythonColons(
        text: String,
        comment: BooleanArray,
        skip: BooleanArray,
        scan: Scan,
        out: MutableList<Diagnostic>,
    ) {
        var start = 0
        var previousEndsWithBackslash = false
        while (start <= text.length) {
            val end = lineEnd(text, start)
            val from = contentStart(text, start, end)
            val to = contentEnd(text, start, end, comment)
            val continued = previousEndsWithBackslash
            if (to > from) {
                previousEndsWithBackslash = text[to - 1] == '\\'
                checkPythonLine(text, from, to, continued, skip, scan, out)
            }
            if (end >= text.length) break
            start = end + 1
        }
    }

    private fun checkPythonLine(
        text: String,
        from: Int,
        to: Int,
        continued: Boolean,
        skip: BooleanArray,
        scan: Scan,
        out: MutableList<Diagnostic>,
    ) {
        if (continued) return
        val depthBefore = if (from == 0) 0 else scan.depth[from - 1]
        if (depthBefore != 0 || scan.depth[to - 1] != 0) return
        var word = wordAt(text, from, to)
        var wordEnd = from + word.length
        if (word == "async") {
            val next = contentStart(text, wordEnd, to)
            word = wordAt(text, next, to)
            wordEnd = next + word.length
            if (word != "def" && word != "for" && word != "with") return
        }
        if (word !in pythonBlockWords) return
        val last = text[to - 1]
        if (last == ':' || last == '\\') return
        for (i in wordEnd until to) {
            if (!skip[i] && text[i] == ':' && scan.depth[i] == 0) return
        }
        val chunk = lastChunkStart(text, from, to)
        out += Diagnostic(
            chunk, to, "Не хватает «:» в конце строки",
            listOf(Fix("Добавить «:»", to, to, ":")),
        )
    }

    private fun semicolons(
        text: String,
        comment: BooleanArray,
        skip: BooleanArray,
        scan: Scan,
        out: MutableList<Diagnostic>,
    ) {
        val lines = ArrayList<IntArray>()
        var start = 0
        while (start <= text.length) {
            val end = lineEnd(text, start)
            val from = contentStart(text, start, end)
            val to = contentEnd(text, start, end, comment)
            if (to > from) lines += intArrayOf(from, to, end)
            if (end >= text.length) break
            start = end + 1
        }
        for ((index, line) in lines.withIndex()) {
            val from = line[0]
            val to = line[1]
            val lineStop = line[2]
            if (skip[to - 1] && lineStop < text.length && skip[lineStop]) continue
            if (!needsSemicolon(text, from, to, skip, scan)) continue
            val next = lines.getOrNull(index + 1)
            if (next != null) {
                val nextChar = text[next[0]]
                if (nextChar in "{.)]:?+-*/%=&|<>,^") continue
                val nextWord = wordAt(text, next[0], next[1])
                if (nextWord == "throws" || nextWord == "where") continue
            }
            val chunk = lastChunkStart(text, from, to)
            out += Diagnostic(
                chunk, to, "Не хватает «;» в конце строки",
                listOf(Fix("Добавить «;»", to, to, ";")),
            )
        }
    }

    private fun needsSemicolon(text: String, from: Int, to: Int, skip: BooleanArray, scan: Scan): Boolean {
        val parenBefore = if (from == 0) 0 else scan.paren[from - 1]
        if (parenBefore != 0 || scan.paren[to - 1] != 0) return false
        val first = text[from]
        if (first == '#' || first == '@' || first == '[') return false
        var bodyStart = from
        while (bodyStart < to && (text[bodyStart] == '}' || text[bodyStart].isWhitespace())) bodyStart++
        if (bodyStart >= to) return false
        val firstWord = wordAt(text, bodyStart, to)
        val last = text[to - 1]
        if (firstWord in controlWords) return false
        if (firstWord == "using" && last == ')') return false
        if (last in ";{},:([\\") return false
        val doubled = to >= 2 && text[to - 2] == last && (last == '+' || last == '-')
        if (!doubled && last in "+-*/%=&|<>?.!^~") return false
        val hasAssignment = hasAssignment(text, from, to, skip)
        return firstWord in statementWords || hasAssignment || last == ')' || doubled
    }

    private fun hasAssignment(text: String, from: Int, to: Int, skip: BooleanArray): Boolean {
        for (i in from until to) {
            if (skip[i] || text[i] != '=') continue
            val next = if (i + 1 < to) text[i + 1] else ' '
            val previous = if (i > from) text[i - 1] else ' '
            if (next == '=' || next == '>') continue
            if (previous == '=' || previous == '!' || previous == '<' || previous == '>') continue
            return true
        }
        return false
    }
}
