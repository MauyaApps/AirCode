package com.mauyaapps.aircode.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeAnalyzerTest {

    private fun messages(text: String, language: String) =
        CodeAnalyzer.analyze(text, language).map { it.message }

    private fun fixed(text: String, language: String, index: Int = 0, fix: Int = 0): String {
        val diagnostic = CodeAnalyzer.analyze(text, language)[index]
        val f = diagnostic.fixes[fix]
        return text.replaceRange(f.start, f.end, f.replacement)
    }

    @Test
    fun cleanCodeHasNoDiagnostics() {
        val java = "public class A {\n    public static void main(String[] args) {\n        int x = 1;\n        System.out.println(x);\n    }\n}\n"
        assertTrue(messages(java, "java").isEmpty())
        val python = "def f(a, b):\n    if a and b:\n        return [a, b]\n    else:\n        print(\"x\")\n"
        assertTrue(messages(python, "python").isEmpty())
        val cream = "say \"hello\"\nx = 12\nif x > 3\n    say x\n"
        assertTrue(messages(cream, "cream").isEmpty())
    }

    @Test
    fun plainLanguageIsNotAnalyzed() {
        assertTrue(messages("((( \"", "plain").isEmpty())
    }

    @Test
    fun bracketsInsideStringsAndCommentsAreIgnored() {
        assertTrue(messages("x = \"(\" # )\ny = ']'", "python").isEmpty())
        assertTrue(messages("int a = 1; // {\n/* ) */", "java").isEmpty())
    }

    @Test
    fun unclosedParenthesisGetsFixAtLineEnd() {
        val text = "print(\"a\"\nx = 1"
        assertEquals(listOf("Не закрыта скобка «(»"), messages(text, "python"))
        assertEquals("print(\"a\")\nx = 1", fixed(text, "python"))
    }

    @Test
    fun unclosedBraceFixAppendsToFile() {
        val text = "int f() {\n    return 1;\n"
        assertEquals("int f() {\n    return 1;\n}", fixed(text, "cpp"))
    }

    @Test
    fun extraClosingBracket() {
        val text = "a = 1)"
        assertEquals("a = 1", fixed(text, "python"))
    }

    @Test
    fun mismatchedBracketOffersReplace() {
        val text = "x = (1, 2]"
        val diagnostic = CodeAnalyzer.analyze(text, "python").single()
        assertEquals("x = (1, 2)", text.replaceRange(diagnostic.fixes[0].start, diagnostic.fixes[0].end, diagnostic.fixes[0].replacement))
        assertEquals("x = (1, 2", text.replaceRange(diagnostic.fixes[1].start, diagnostic.fixes[1].end, diagnostic.fixes[1].replacement))
    }

    @Test
    fun unterminatedString() {
        val text = "say \"hello\nsay 1"
        assertEquals(listOf("Строка не закрыта"), messages(text, "cream"))
        assertEquals("say \"hello\"\nsay 1", fixed(text, "cream"))
    }

    @Test
    fun escapedQuoteDoesNotCloseString() {
        assertEquals(listOf("Строка не закрыта"), messages("x = \"a\\\"", "python"))
        assertTrue(messages("x = \"a\\\\\"", "python").isEmpty())
    }

    @Test
    fun unterminatedTripleQuote() {
        assertEquals(listOf("Строка не закрыта"), messages("x = \"\"\"abc\n", "python"))
    }

    @Test
    fun unterminatedBlockComment() {
        val text = "int a; /* open\nint b;"
        assertEquals(listOf("Комментарий не закрыт"), messages(text, "java"))
        assertEquals("int a; /* open\nint b;*/", fixed(text, "java"))
    }

    @Test
    fun keywordTypoTranspositionAndDeletion() {
        val text = "def f(x):\n    retrun x\n"
        val diagnostic = CodeAnalyzer.analyze(text, "python").single()
        assertEquals("retrun", text.substring(diagnostic.start, diagnostic.end))
        assertEquals("return", diagnostic.fixes[0].replacement)
        assertEquals(listOf("Возможно, опечатка в «improt»"), messages("improt os\n", "python"))
    }

    @Test
    fun builtinCallTypo() {
        val text = "pritn(\"a\")"
        val diagnostic = CodeAnalyzer.analyze(text, "python").single()
        assertEquals("print", diagnostic.fixes[0].replacement)
    }

    @Test
    fun typoNotReportedForAssignmentsDefinitionsOrRepeatedNames() {
        assertTrue(messages("retrun = 1\n", "python").isEmpty())
        assertTrue(messages("def retrun():\n    pass\n", "python").isEmpty())
        assertTrue(messages("retrun = 1\nprint(retrun)\n", "python").isEmpty())
        assertTrue(messages("obj.retrun\n", "python").isEmpty())
    }

    @Test
    fun pythonMissingColon() {
        val text = "if x > 1\n    pass\n"
        assertEquals(listOf("Не хватает «:» в конце строки"), messages(text, "python"))
        assertEquals("if x > 1:\n    pass\n", fixed(text, "python"))
    }

    @Test
    fun pythonColonFalsePositivesAvoided() {
        assertTrue(messages("if x: pass\n", "python").isEmpty())
        assertTrue(messages("else:  # c\n", "python").isEmpty())
        assertTrue(messages("x = [i\n     for i in y]\n", "python").isEmpty())
        assertTrue(messages("d = {\"a\": 1}\nif d[\"a\"]:\n    pass\n", "python").isEmpty())
        assertTrue(messages("y = 1 if a else 2\n", "python").isEmpty())
        assertTrue(messages("if (a and\n        b):\n    pass\n", "python").isEmpty())
    }

    @Test
    fun javaMissingSemicolon() {
        val text = "class A {\n    void f() {\n        int x = 1\n        g(x);\n    }\n}\n"
        assertEquals(listOf("Не хватает «;» в конце строки"), messages(text, "java"))
        assertEquals(
            "class A {\n    void f() {\n        int x = 1;\n        g(x);\n    }\n}\n",
            fixed(text, "java"),
        )
        assertEquals(1, CodeAnalyzer.analyze("void f() {\n    return x\n}\n", "cpp").size)
        assertEquals(1, CodeAnalyzer.analyze("void f() {\n    foo(1)\n}\n", "csharp").size)
    }

    @Test
    fun semicolonFalsePositivesAvoided() {
        val allman = "class A\n{\n    void f()\n    {\n        if (x)\n        {\n            y();\n        }\n        else if (z)\n            w();\n    }\n}\n"
        assertTrue(messages(allman, "csharp").isEmpty())
        val chain = "var a = b\n    .c()\n    .d();\nint s = 1 +\n    2;\nfoo(a,\n    b);\n"
        assertTrue(messages(chain, "csharp").isEmpty())
        val enumeration = "enum Color {\n    RED,\n    GREEN\n}\n"
        assertTrue(messages(enumeration, "java").isEmpty())
        val cpp = "#include <stdio.h>\n#define X 1\nint main() {\n    int a[] = {1,\n        2};\n    do { a[0]++; } while (a[0] < 3);\n    return 0;\n}\n"
        assertTrue(messages(cpp, "cpp").isEmpty())
        val tryCatch = "try {\n    x();\n} catch (Exception e) {\n    y();\n} finally {\n}\n"
        assertTrue(messages(tryCatch, "java").isEmpty())
        val comment = "int x = 1; // note\nint y = 2 /* c */;\n"
        assertTrue(messages(comment, "java").isEmpty())
    }

    @Test
    fun javascriptSemicolonsAreOptional() {
        assertTrue(messages("const a = 1\nconsole.log(a)\n", "javascript").isEmpty())
    }

    @Test
    fun javaTextBlockIsOneString() {
        val text = "String t = \"\"\"\n    hello;\n    \"\"\";\n"
        assertTrue(messages(text, "java").isEmpty())
    }

    @Test
    fun typoHelper() {
        assertTrue(CodeAnalyzer.isTypoOf("retrun", "return"))
        assertTrue(CodeAnalyzer.isTypoOf("retun", "return"))
        assertTrue(CodeAnalyzer.isTypoOf("returnn", "return"))
        assertTrue(!CodeAnalyzer.isTypoOf("whole", "while"))
        assertTrue(!CodeAnalyzer.isTypoOf("return", "return"))
    }
}
