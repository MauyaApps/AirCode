package com.mauyaapps.aircode.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRendererTest {

    @Test
    fun превращаетЗаголовокИСписок() {
        val html = MarkdownRenderer.renderPage("# Привет\n\n- один\n- два", dark = false)
        assertTrue(html.contains("<h1>Привет</h1>"))
        assertTrue(html.contains("<li>один</li>"))
    }

    @Test
    fun поддерживаетТаблицыИЗачёркивание() {
        val html = MarkdownRenderer.renderPage("|a|b|\n|-|-|\n|1|2|\n\n~~x~~", dark = false)
        assertTrue(html.contains("<table>"))
        assertTrue(html.contains("<del>x</del>"))
    }

    @Test
    fun экранируетСкриптыИОпасныеСсылки() {
        val html = MarkdownRenderer.renderPage("<script>alert(1)</script>\n\n[x](javascript:alert(1))", dark = false)
        assertFalse(html.contains("<script>alert"))
        assertFalse(html.contains("javascript:"))
    }

    @Test
    fun темаМеняетЦветФона() {
        assertTrue(MarkdownRenderer.renderPage("a", dark = true).contains("#282c34"))
        assertTrue(MarkdownRenderer.renderPage("a", dark = false).contains("#ffffff"))
    }

    @Test
    fun видПросмотраОпределяетсяПоРасширению() {
        assertEquals(PreviewKind.MARKDOWN, previewKindForFile("README.md"))
        assertEquals(PreviewKind.MARKDOWN, previewKindForFile("notes.MARKDOWN"))
        assertEquals(PreviewKind.HTML, previewKindForFile("index.html"))
        assertEquals(PreviewKind.HTML, previewKindForFile("page.htm"))
        assertNull(previewKindForFile("main.py"))
        assertNull(previewKindForFile("README"))
    }
}
