package com.mauyaapps.aircode.editor

import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/** Превращает Markdown в самостоятельную HTML-страницу для просмотра. */
object MarkdownRenderer {

    private val extensions = listOf(TablesExtension.create(), StrikethroughExtension.create())

    private val parser: Parser = Parser.builder().extensions(extensions).build()

    // HTML внутри Markdown экранируется, опасные ссылки (javascript:) отбрасываются
    private val renderer: HtmlRenderer = HtmlRenderer.builder()
        .extensions(extensions)
        .escapeHtml(true)
        .sanitizeUrls(true)
        .build()

    /** Готовая страница с оформлением под светлую или тёмную тему. */
    fun renderPage(markdown: String, dark: Boolean): String {
        val body = renderer.render(parser.parse(markdown))
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>${styles(dark)}</style>
            </head>
            <body>$body</body>
            </html>
        """.trimIndent()
    }

    private fun styles(dark: Boolean): String {
        val background = if (dark) "#282c34" else "#ffffff"
        val text = if (dark) "#abb2bf" else "#1f2328"
        val border = if (dark) "#4b5263" else "#d0d7de"
        val codeBackground = if (dark) "#21252b" else "#f6f8fa"
        val link = if (dark) "#61afef" else "#0969da"
        return """
            body { margin: 0; padding: 16px; background: $background; color: $text;
                   font-family: sans-serif; line-height: 1.5; word-wrap: break-word; }
            h1, h2 { border-bottom: 1px solid $border; padding-bottom: 4px; }
            a { color: $link; }
            code, pre { background: $codeBackground; border-radius: 4px; font-family: monospace; }
            code { padding: 1px 4px; }
            pre { padding: 12px; overflow-x: auto; }
            pre code { padding: 0; }
            blockquote { margin: 0; padding: 0 12px; border-left: 4px solid $border; }
            table { border-collapse: collapse; display: block; overflow-x: auto; }
            th, td { border: 1px solid $border; padding: 6px 10px; }
            img { max-width: 100%; }
            hr { border: 0; border-top: 1px solid $border; }
        """.trimIndent()
    }
}
