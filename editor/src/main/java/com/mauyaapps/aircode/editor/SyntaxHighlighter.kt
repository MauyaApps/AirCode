package com.mauyaapps.aircode.editor

enum class TokenKind { KEYWORD, STRING, COMMENT, NUMBER, ATOM, BUILTIN, DEFINITION }

data class Token(val start: Int, val end: Int, val kind: TokenKind)

object SyntaxHighlighter {

    private class Spec(
        val keywords: Set<String>,
        val atoms: Set<String> = emptySet(),
        val lineComment: String? = null,
        val blockStart: String? = null,
        val blockEnd: String? = null,
        val quotes: String = "\"'",
        val multilineQuotes: String = "",
        val tripleQuotes: Boolean = false,
        val preprocessor: Boolean = false,
    )

    private val cLike = setOf(
        "auto", "break", "case", "char", "const", "continue", "default", "do", "double", "else", "enum",
        "extern", "float", "for", "goto", "if", "inline", "int", "long", "register", "return", "short",
        "signed", "sizeof", "static", "struct", "switch", "typedef", "union", "unsigned", "void",
        "volatile", "while",
    )

    private val cpp = cLike + setOf(
        "bool", "catch", "class", "constexpr", "delete", "explicit", "friend", "mutable", "namespace",
        "new", "noexcept", "operator", "override", "private", "protected", "public", "template", "this",
        "throw", "try", "typename", "using", "virtual", "final", "nullptr", "static_cast", "dynamic_cast",
        "const_cast", "reinterpret_cast", "decltype", "auto",
    )

    private val java = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
        "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
        "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
        "volatile", "while", "var", "record", "sealed", "permits", "yield",
    )

    private val csharp = setOf(
        "abstract", "as", "base", "bool", "break", "byte", "case", "catch", "char", "checked", "class",
        "const", "continue", "decimal", "default", "delegate", "do", "double", "else", "enum", "event",
        "explicit", "extern", "finally", "fixed", "float", "for", "foreach", "goto", "if", "implicit",
        "in", "int", "interface", "internal", "is", "lock", "long", "namespace", "new", "object",
        "operator", "out", "override", "params", "private", "protected", "public", "readonly", "ref",
        "return", "sbyte", "sealed", "short", "sizeof", "stackalloc", "static", "string", "struct",
        "switch", "this", "throw", "try", "typeof", "uint", "ulong", "unchecked", "unsafe", "ushort",
        "using", "virtual", "void", "volatile", "while", "var", "async", "await", "get", "set", "record",
    )

    private val objectiveC = cLike + setOf(
        "id", "self", "super", "BOOL", "SEL", "Class", "IMP", "nil", "Nil", "YES", "NO", "in", "out",
        "inout", "bycopy", "byref", "oneway", "atomic", "nonatomic", "strong", "weak", "assign", "copy",
        "retain", "readonly", "readwrite", "instancetype", "nullable", "nonnull",
    )

    private val javascript = setOf(
        "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger", "default",
        "delete", "do", "else", "export", "extends", "finally", "for", "function", "if", "import", "in",
        "instanceof", "let", "new", "of", "return", "static", "super", "switch", "this", "throw", "try",
        "typeof", "var", "void", "while", "with", "yield", "get", "set",
    )

    private val python = setOf(
        "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del", "elif",
        "else", "except", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda",
        "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield", "match",
        "case",
    )

    private val creamWords = setOf(
        "if", "else", "in", "repeat", "while", "try", "action", "task", "struct", "import", "return",
        "wait", "together", "say", "and", "or", "not",
    )
    private val creamConstants = setOf("yes", "no", "empty", "PI", "E", "INF")
    private val creamBuiltins = setOf(
        "math", "num", "rand", "stats", "convert", "regex", "list", "table", "file", "folder", "encode",
        "net", "date", "length", "sum", "min", "max", "abs", "round", "range", "sort", "reverse", "first",
        "last", "join", "split", "upper", "lower", "trim", "contains", "number", "bool", "input",
    )
    private val creamPhrases = listOf("for each", "on error", "or if")

    private val specs = mapOf(
        "python" to Spec(
            keywords = python,
            atoms = setOf("True", "False", "None"),
            lineComment = "#",
            tripleQuotes = true,
        ),
        "javascript" to Spec(
            keywords = javascript,
            atoms = setOf("true", "false", "null", "undefined", "NaN", "Infinity"),
            lineComment = "//",
            blockStart = "/*",
            blockEnd = "*/",
            quotes = "\"'`",
            multilineQuotes = "`",
        ),
        "java" to Spec(
            keywords = java,
            atoms = setOf("true", "false", "null"),
            lineComment = "//",
            blockStart = "/*",
            blockEnd = "*/",
            tripleQuotes = true,
        ),
        "cpp" to Spec(
            keywords = cpp,
            atoms = setOf("true", "false", "NULL", "nullptr"),
            lineComment = "//",
            blockStart = "/*",
            blockEnd = "*/",
            preprocessor = true,
        ),
        "csharp" to Spec(
            keywords = csharp,
            atoms = setOf("true", "false", "null"),
            lineComment = "//",
            blockStart = "/*",
            blockEnd = "*/",
            tripleQuotes = true,
        ),
        "objectivec" to Spec(
            keywords = objectiveC,
            atoms = setOf("true", "false", "NULL"),
            lineComment = "//",
            blockStart = "/*",
            blockEnd = "*/",
            preprocessor = true,
        ),
        "cream" to Spec(keywords = creamWords, atoms = creamConstants, lineComment = "--", quotes = "\""),
    )

    fun supports(language: String): Boolean = language in specs

    fun hasTripleQuotes(language: String): Boolean = specs[language]?.tripleQuotes == true

    fun vocabulary(language: String): Set<String> =
        specs[language]?.let { it.keywords + it.atoms }.orEmpty()

    fun tokenize(text: String, language: String): List<Token> {
        val spec = specs[language] ?: return emptyList()
        val tokens = ArrayList<Token>()
        val cream = language == "cream"
        var expectName = false
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (spec.lineComment != null && text.startsWith(spec.lineComment, i)) {
                val end = lineEnd(text, i)
                tokens += Token(i, end, TokenKind.COMMENT)
                i = end
                continue
            }
            if (spec.blockStart != null && text.startsWith(spec.blockStart, i)) {
                val close = text.indexOf(spec.blockEnd.orEmpty(), i + spec.blockStart.length)
                val end = if (close < 0) n else close + spec.blockEnd.orEmpty().length
                tokens += Token(i, end, TokenKind.COMMENT)
                i = end
                continue
            }
            if (spec.preprocessor && c == '#' && onlySpaceBefore(text, i)) {
                val end = lineEnd(text, i)
                tokens += Token(i, end, TokenKind.KEYWORD)
                i = end
                continue
            }
            if (c in spec.quotes) {
                val end = stringEnd(text, i, spec)
                tokens += Token(i, end, TokenKind.STRING)
                i = end
                continue
            }
            if (c.isDigit() && (i == 0 || !isWordPart(text[i - 1]))) {
                var end = i + 1
                while (end < n) {
                    val d = text[end]
                    if (d.isLetterOrDigit() || d == '_' || (d == '.' && end + 1 < n && text[end + 1].isDigit())) end++ else break
                }
                tokens += Token(i, end, TokenKind.NUMBER)
                i = end
                continue
            }
            if (isWordStart(c)) {
                if (cream) {
                    val phrase = creamPhrases.firstOrNull { matchesPhrase(text, i, it) }
                    if (phrase != null) {
                        tokens += Token(i, i + phrase.length, TokenKind.KEYWORD)
                        i += phrase.length
                        continue
                    }
                }
                var end = i + 1
                while (end < n && isWordPart(text[end])) end++
                val word = text.substring(i, end)
                if (cream) {
                    when {
                        expectName -> {
                            expectName = false
                            tokens += Token(i, end, TokenKind.DEFINITION)
                        }
                        word in spec.keywords -> {
                            expectName = word == "action" || word == "task" || word == "struct"
                            tokens += Token(i, end, TokenKind.KEYWORD)
                        }
                        word in spec.atoms -> tokens += Token(i, end, TokenKind.ATOM)
                        word in creamBuiltins && followedByParen(text, end) ->
                            tokens += Token(i, end, TokenKind.BUILTIN)
                    }
                } else {
                    when {
                        word in spec.keywords -> tokens += Token(i, end, TokenKind.KEYWORD)
                        word in spec.atoms -> tokens += Token(i, end, TokenKind.ATOM)
                    }
                }
                i = end
                continue
            }
            i++
        }
        return tokens
    }

    private fun lineEnd(text: String, from: Int): Int {
        val end = text.indexOf('\n', from)
        return if (end < 0) text.length else end
    }

    private fun onlySpaceBefore(text: String, index: Int): Boolean {
        var j = index - 1
        while (j >= 0 && text[j] != '\n') {
            if (!text[j].isWhitespace()) return false
            j--
        }
        return true
    }

    private fun stringEnd(text: String, start: Int, spec: Spec): Int {
        val quote = text[start]
        val n = text.length
        if (spec.tripleQuotes && text.startsWith("$quote$quote$quote", start)) {
            val close = text.indexOf("$quote$quote$quote", start + 3)
            return if (close < 0) n else close + 3
        }
        val multiline = quote in spec.multilineQuotes
        var i = start + 1
        while (i < n) {
            val c = text[i]
            if (c == '\\') {
                i += 2
                continue
            }
            if (c == quote) return i + 1
            if (c == '\n' && !multiline) return i
            i++
        }
        return n
    }

    private fun matchesPhrase(text: String, index: Int, phrase: String): Boolean {
        if (!text.startsWith(phrase, index)) return false
        if (index > 0 && isWordPart(text[index - 1])) return false
        val after = index + phrase.length
        return after >= text.length || !isWordPart(text[after])
    }

    private fun followedByParen(text: String, from: Int): Boolean {
        var j = from
        while (j < text.length && (text[j] == ' ' || text[j] == '\t')) j++
        return j < text.length && text[j] == '('
    }

    private fun isWordStart(c: Char) = c.isLetter() || c == '_'

    private fun isWordPart(c: Char) = c.isLetterOrDigit() || c == '_'
}

object TextSearch {

    fun matches(text: String, query: String, caseSensitive: Boolean): List<Int> {
        if (query.isEmpty()) return emptyList()
        val hits = ArrayList<Int>()
        var from = 0
        while (true) {
            val index = text.indexOf(query, from, ignoreCase = !caseSensitive)
            if (index < 0) break
            hits += index
            from = index + query.length
        }
        return hits
    }
}
