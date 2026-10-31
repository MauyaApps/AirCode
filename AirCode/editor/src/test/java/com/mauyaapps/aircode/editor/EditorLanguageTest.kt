package com.mauyaapps.aircode.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorLanguageTest {

    @Test
    fun определяетЯзыкПоРасширению() {
        assertEquals("python", languageForFile("main.py"))
        assertEquals("java", languageForFile("Main.java"))
        assertEquals("javascript", languageForFile("app.JS"))
        assertEquals("cpp", languageForFile("lib.hpp"))
        assertEquals("csharp", languageForFile("Program.cs"))
        assertEquals("objectivec", languageForFile("View.m"))
        assertEquals("cream", languageForFile("calculator.cream"))
    }

    @Test
    fun безРасширенияИлиНеизвестное_этоОбычныйТекст() {
        assertEquals("plain", languageForFile("README"))
        assertEquals("plain", languageForFile("notes.txt"))
        assertEquals("plain", languageForFile(""))
    }
}
