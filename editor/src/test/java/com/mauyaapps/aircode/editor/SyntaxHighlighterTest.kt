package com.mauyaapps.aircode.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxHighlighterTest {

    private fun kinds(text: String, language: String): List<Pair<String, TokenKind>> =
        SyntaxHighlighter.tokenize(text, language).map { text.substring(it.start, it.end) to it.kind }

    @Test
    fun plainHasNoTokens() {
        assertTrue(SyntaxHighlighter.tokenize("any text 123", "plain").isEmpty())
    }

    @Test
    fun creamTokens() {
        val text = "say \"hello world\"\n-- note\nx = 12\nif yes\nfor each item in list(a)"
        val result = kinds(text, "cream")
        assertTrue("say" to TokenKind.KEYWORD in result)
        assertTrue("\"hello world\"" to TokenKind.STRING in result)
        assertTrue("-- note" to TokenKind.COMMENT in result)
        assertTrue("12" to TokenKind.NUMBER in result)
        assertTrue("yes" to TokenKind.ATOM in result)
        assertTrue("for each" to TokenKind.KEYWORD in result)
        assertTrue("list" to TokenKind.BUILTIN in result)
    }

    @Test
    fun creamDefinitionName() {
        val result = kinds("action greet(name)", "cream")
        assertEquals("action" to TokenKind.KEYWORD, result[0])
        assertEquals("greet" to TokenKind.DEFINITION, result[1])
    }

    @Test
    fun creamBuiltinNeedsParenthesis() {
        assertTrue(kinds("list = 1", "cream").none { it.second == TokenKind.BUILTIN })
    }

    @Test
    fun pythonTripleQuotesSpanLines() {
        val text = "x = \"\"\"a\nb\"\"\"\n# c\nreturn"
        val result = kinds(text, "python")
        assertTrue("\"\"\"a\nb\"\"\"" to TokenKind.STRING in result)
        assertTrue("# c" to TokenKind.COMMENT in result)
        assertTrue("return" to TokenKind.KEYWORD in result)
    }

    @Test
    fun javaBlockComment() {
        val text = "int a; /* x\ny */ String s = \"q\\\"r\";"
        val result = kinds(text, "java")
        assertTrue("int" to TokenKind.KEYWORD in result)
        assertTrue("/* x\ny */" to TokenKind.COMMENT in result)
        assertTrue("\"q\\\"r\"" to TokenKind.STRING in result)
    }

    @Test
    fun cppPreprocessorLine() {
        val result = kinds("  #include <stdio.h>\nint x;", "cpp")
        assertEquals("#include <stdio.h>" to TokenKind.KEYWORD, result[0])
    }

    @Test
    fun javascriptTemplateStringMultiline() {
        val text = "const a = `x\ny`;"
        assertTrue("`x\ny`" to TokenKind.STRING in kinds(text, "javascript"))
    }

    @Test
    fun unterminatedStringStopsAtNewline() {
        val result = kinds("a = \"oops\nreturn", "python")
        assertEquals("\"oops" to TokenKind.STRING, result[0])
        assertTrue("return" to TokenKind.KEYWORD in result)
    }

    @Test
    fun numbersNotInsideWords() {
        val result = kinds("x1 = 0x1F + 3.14", "python")
        assertTrue("0x1F" to TokenKind.NUMBER in result)
        assertTrue("3.14" to TokenKind.NUMBER in result)
        assertTrue(result.none { it.first == "1" })
    }

    @Test
    fun csharpAndObjectiveC() {
        assertTrue("using" to TokenKind.KEYWORD in kinds("using System;", "csharp"))
        assertTrue("@interface" !in kinds("@interface A", "objectivec").map { it.first })
        assertTrue("nil" to TokenKind.KEYWORD in kinds("id a = nil;", "objectivec"))
    }

    @Test
    fun searchFindsAllCaseInsensitive() {
        assertEquals(listOf(0, 4), TextSearch.matches("Abc abc", "ABC", false))
        assertEquals(listOf(4), TextSearch.matches("Abc abc", "abc", true))
        assertTrue(TextSearch.matches("abc", "", false).isEmpty())
    }
}
