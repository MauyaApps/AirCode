package com.mauyaapps.aircode.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageGuessTest {

    @Test
    fun guessesByContent() {
        assertEquals("python", LanguageGuess.guess("def f(x):\n    return x\n"))
        assertEquals("python", LanguageGuess.guess("import os\nprint(os.name)\n"))
        assertEquals("java", LanguageGuess.guess("public class A {\n    public static void main(String[] a) {}\n}\n"))
        assertEquals("csharp", LanguageGuess.guess("using System;\nclass P { }\n"))
        assertEquals("cpp", LanguageGuess.guess("#include <stdio.h>\nint main() { return 0; }\n"))
        assertEquals("objectivec", LanguageGuess.guess("#import <Foundation/Foundation.h>\n@interface A : NSObject\n@end\n"))
        assertEquals("javascript", LanguageGuess.guess("const a = 1;\nconsole.log(a);\n"))
        assertEquals("cream", LanguageGuess.guess("say \"hello\"\nx = 5\n"))
        assertEquals("cream", LanguageGuess.guess("action greet(name)\n    say name\n"))
    }

    @Test
    fun shebangWins() {
        assertEquals("python", LanguageGuess.guess("#!/usr/bin/env python3\nx = 1\n"))
        assertEquals("javascript", LanguageGuess.guess("#!/usr/bin/env node\nx = 1\n"))
    }

    @Test
    fun unknownStaysPlain() {
        assertEquals("plain", LanguageGuess.guess(""))
        assertEquals("plain", LanguageGuess.guess("просто заметки\nвторая строка\n"))
    }

    @Test
    fun titlesCoverAllLanguages() {
        EDITOR_LANGUAGES.forEach { assert(languageTitle(it).isNotBlank()) }
        assertEquals("Python", languageTitle("python"))
        assertEquals("Обычный текст", languageTitle("plain"))
    }
}
