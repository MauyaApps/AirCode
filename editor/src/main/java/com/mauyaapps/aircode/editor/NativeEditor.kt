package com.mauyaapps.aircode.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private class EditorPalette(
    val background: Color,
    val text: Color,
    val cursor: Color,
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val atom: Color,
    val builtin: Color,
    val definition: Color,
    val match: Color,
    val error: Color,
    val gutter: Color,
) {
    fun colorOf(kind: TokenKind): Color = when (kind) {
        TokenKind.KEYWORD -> keyword
        TokenKind.STRING -> string
        TokenKind.COMMENT -> comment
        TokenKind.NUMBER -> number
        TokenKind.ATOM -> atom
        TokenKind.BUILTIN -> builtin
        TokenKind.DEFINITION -> definition
    }
}

private val DarkPalette = EditorPalette(
    background = Color(0xFF282C34),
    text = Color(0xFFE8EAED),
    cursor = Color(0xFFFFFFFF),
    keyword = Color(0xFFC678DD),
    string = Color(0xFF98C379),
    comment = Color(0xFF8B93A1),
    number = Color(0xFFD19A66),
    atom = Color(0xFFD19A66),
    builtin = Color(0xFF61AFEF),
    definition = Color(0xFFE5C07B),
    match = Color(0x66E5C07B),
    error = Color(0xFFFF6B6B),
    gutter = Color(0xFF6B7280),
)

private val LightPalette = EditorPalette(
    background = Color(0xFFFFFFFF),
    text = Color(0xFF1F2328),
    cursor = Color(0xFF000000),
    keyword = Color(0xFFCF222E),
    string = Color(0xFF0A3069),
    comment = Color(0xFF6E7781),
    number = Color(0xFF0550AE),
    atom = Color(0xFF0550AE),
    builtin = Color(0xFF8250DF),
    definition = Color(0xFF953800),
    match = Color(0x66FFD33D),
    error = Color(0xFFD1242F),
    gutter = Color(0xFF8C959F),
)

private const val HIGHLIGHT_LIMIT = 300_000

private class HighlightTransformation(
    private val language: String,
    private val palette: EditorPalette,
    private val match: TextRange?,
    private val diagnostics: List<Diagnostic>,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val styled = buildAnnotatedString {
            append(raw)
            if (raw.length <= HIGHLIGHT_LIMIT) {
                SyntaxHighlighter.tokenize(raw, language).forEach { token ->
                    addStyle(SpanStyle(color = palette.colorOf(token.kind)), token.start, token.end)
                }
            }
            if (match != null && match.end <= raw.length && !match.collapsed) {
                addStyle(SpanStyle(background = palette.match), match.start, match.end)
            }
            diagnostics.forEach { item ->
                if (item.end <= raw.length && item.start < item.end) {
                    addStyle(
                        SpanStyle(color = palette.error, textDecoration = TextDecoration.Underline),
                        item.start,
                        item.end,
                    )
                }
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

private class Analysis(val text: String, val items: List<Diagnostic>)

private const val ANALYSIS_DELAY_MS = 700L
private const val ANALYSIS_LIMIT = 150_000

private val EditorTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 14.sp,
    lineHeight = 20.sp,
)

private fun lineIndexOf(text: String, offset: Int): Int {
    var count = 0
    val limit = minOf(offset, text.length)
    for (i in 0 until limit) if (text[i] == '\n') count++
    return count
}

@Composable
internal fun NativeEditor(
    controller: EditorController,
    activeId: Long?,
    language: String,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val value = activeId?.let { controller.nativeValue(it) }
    BoxWithConstraints(modifier = modifier.background(palette.background)) {
        val viewportWidth = maxWidth
        val viewportHeight = maxHeight
        if (activeId != null && value != null) {
            key(activeId) {
                val scroll = rememberScrollState()
                val horizontal = rememberScrollState()
                var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
                var analysis by remember { mutableStateOf<Analysis?>(null) }
                val text = value.text
                LaunchedEffect(text, language) {
                    if (analysis != null) delay(ANALYSIS_DELAY_MS)
                    val items = if (text.length <= ANALYSIS_LIMIT) {
                        withContext(Dispatchers.Default) { CodeAnalyzer.analyze(text, language) }
                    } else {
                        emptyList()
                    }
                    analysis = Analysis(text, items)
                }
                val diagnostics = analysis?.takeIf { it.text == text }?.items.orEmpty()
                val match = controller.nativeMatch
                val transformation = remember(language, darkTheme, match, diagnostics) {
                    HighlightTransformation(language, palette, match, diagnostics)
                }
                LaunchedEffect(match, layout) {
                    val target = match ?: return@LaunchedEffect
                    val current = layout ?: return@LaunchedEffect
                    if (target.start > current.layoutInput.text.length) return@LaunchedEffect
                    val line = current.getLineForOffset(target.start)
                    scroll.animateScrollTo(current.getLineTop(line).toInt().coerceAtLeast(0))
                }
                val lineCount = remember(text) { text.count { it == '\n' } + 1 }
                val errorLines = remember(diagnostics, text) {
                    diagnostics.map { lineIndexOf(text, it.start) }.toSet()
                }
                val numbers = remember(lineCount, errorLines, palette) {
                    buildAnnotatedString {
                        for (line in 0 until lineCount) {
                            val label = (line + 1).toString()
                            if (line in errorLines) {
                                pushStyle(SpanStyle(color = palette.error))
                                append(label)
                                pop()
                            } else {
                                append(label)
                            }
                            if (line < lineCount - 1) append("\n")
                        }
                    }
                }
                val tap = controller.tapOffset
                val hits = if (tap != null) diagnostics.filter { tap >= it.start && tap <= it.end } else emptyList()
                Row(modifier = Modifier.fillMaxSize().verticalScroll(scroll).heightIn(min = viewportHeight)) {
                    Text(
                        text = numbers,
                        style = EditorTextStyle.copy(color = palette.gutter, textAlign = TextAlign.End),
                        softWrap = false,
                        modifier = Modifier
                            .background(palette.background)
                            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                            .widthIn(min = 24.dp),
                    )
                    Box(modifier = Modifier.weight(1f).horizontalScroll(horizontal)) {
                        Box(modifier = Modifier.padding(start = 4.dp, end = 24.dp, top = 8.dp, bottom = 8.dp)) {
                            BasicTextField(
                                value = value,
                                onValueChange = { controller.updateNative(activeId, it) },
                                textStyle = EditorTextStyle.copy(color = palette.text),
                                cursorBrush = SolidColor(palette.cursor),
                                visualTransformation = transformation,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                                onTextLayout = { layout = it },
                                modifier = Modifier.widthIn(min = maxOf(viewportWidth - 48.dp, 0.dp)).heightIn(min = maxOf(viewportHeight - 16.dp, 0.dp)),
                            )
                            if (hits.isNotEmpty() && tap != null) {
                                val current = layout
                                val anchor = if (current != null && tap <= current.layoutInput.text.length) {
                                    current.getCursorRect(tap)
                                } else {
                                    null
                                }
                                val x = anchor?.left?.toInt() ?: 0
                                val y = anchor?.bottom?.toInt() ?: 0
                                Box(modifier = Modifier.offset { IntOffset(x, y) }) {
                                    DropdownMenu(expanded = true, onDismissRequest = { controller.consumeTap() }) {
                                        hits.forEach { item ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = item.message,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.error,
                                                    )
                                                },
                                                onClick = {},
                                                enabled = false,
                                            )
                                            item.fixes.forEach { fix ->
                                                DropdownMenuItem(
                                                    text = { Text(fix.label) },
                                                    onClick = { controller.applyFix(activeId, fix) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
