package com.mauyaapps.aircode.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TerminalBackground = Color(0xFF1E1E1E)
private val TerminalForeground = Color(0xFFD4D4D4)

private const val EXPAND_GLYPH = "⤢"
private const val COLLAPSE_GLYPH = "⤡"
private const val HIDE_GLYPH = "⌄"
private const val NEW_GLYPH = "+"
private const val CLOSE_GLYPH = "×"
private const val EOF_CHAR = "\u0004"
private const val DIRECT_SENTINEL = "​"
private const val FUNCTION_KEYS = 12
private const val CELL_SAMPLE = "M"

private val terminalTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    color = TerminalForeground,
)

@Composable
fun TerminalPanel(
    manager: TerminalManager,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onHide: () -> Unit,
    onInputFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    showExpandToggle: Boolean = true,
) {
    val sessions by manager.sessions.collectAsState()
    val activeId by manager.activeId.collectAsState()
    val environment by manager.environment.state.collectAsState()
    val current = sessions.firstOrNull { it.id == activeId }

    LaunchedEffect(Unit) { manager.ensureSession() }

    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        PanelHeader(
            manager = manager,
            sessions = sessions,
            activeId = activeId,
            expanded = expanded,
            showExpandToggle = showExpandToggle,
            onToggleExpanded = onToggleExpanded,
            onHide = onHide,
        )
        EnvironmentBanner(state = environment, onInstall = manager::installEnvironment)
        SessionView(
            manager = manager,
            session = current,
            reportSize = active,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        if (current != null) {
            SessionInput(session = current, onFocusChange = onInputFocusChange)
        }
    }
}

@Composable
private fun PanelHeader(
    manager: TerminalManager,
    sessions: List<TerminalSession>,
    activeId: Long?,
    expanded: Boolean,
    showExpandToggle: Boolean,
    onToggleExpanded: () -> Unit,
    onHide: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            sessions.forEach { session ->
                SessionTab(
                    title = session.title,
                    selected = session.id == activeId,
                    onSelect = { manager.select(session.id) },
                    onClose = { manager.close(session.id) },
                )
            }
            TextButton(onClick = { manager.newShell() }) { Text(NEW_GLYPH) }
        }
        if (showExpandToggle) {
            TextButton(onClick = onToggleExpanded) {
                Text(if (expanded) COLLAPSE_GLYPH else EXPAND_GLYPH)
            }
        }
        TextButton(onClick = onHide) { Text(HIDE_GLYPH) }
    }
}

@Composable
private fun SessionTab(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onSelect)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
        Text(
            text = CLOSE_GLYPH,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .clickable(onClick = onClose)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun EnvironmentBanner(state: EnvironmentState, onInstall: () -> Unit) {
    when (state) {
        EnvironmentState.Ready -> Unit
        EnvironmentState.BinariesMissing -> BannerText(stringResource(R.string.env_missing_binaries))
        EnvironmentState.NotInstalled -> BannerWithAction(
            text = stringResource(R.string.env_not_installed),
            action = stringResource(R.string.env_install),
            onAction = onInstall,
        )
        is EnvironmentState.Installing -> Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(stringResource(state.step), style = MaterialTheme.typography.bodySmall)
            val percent = state.percent
            if (percent != null) {
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
        is EnvironmentState.Failed -> {
            val reason = stringResource(state.reason)
            val message = if (state.detail.isNullOrBlank()) reason else "$reason (${state.detail})"
            BannerWithAction(
                text = stringResource(R.string.env_failed, message),
                action = stringResource(R.string.env_retry),
                onAction = onInstall,
            )
        }
    }
}

@Composable
private fun BannerText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun BannerWithAction(text: String, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun SessionView(manager: TerminalManager, session: TerminalSession?, reportSize: Boolean, modifier: Modifier) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val cellWidthPx = remember(density) { measurer.measure(CELL_SAMPLE, terminalTextStyle).size.width.coerceAtLeast(1) }
    val cellHeightPx = remember(density) { with(density) { 16.sp.toPx() }.coerceAtLeast(1f) }
    val paddingPx = remember(density) { with(density) { TERMINAL_PADDING.toPx() } }

    BoxWithConstraints(modifier = modifier.background(TerminalBackground)) {
        val columns = ((constraints.maxWidth - 2 * paddingPx) / cellWidthPx).toInt()
        val rows = ((constraints.maxHeight - 2 * paddingPx) / cellHeightPx).toInt()
        LaunchedEffect(columns, rows, reportSize) {
            if (reportSize && columns > 0 && rows > 0) manager.resize(columns, rows)
        }
        if (session != null) {
            val revision by session.revision.collectAsState()
            val alternate = remember(revision) { session.buffer.isAlternateScreen }
            if (alternate) {
                AlternateScreen(session, revision)
            } else {
                ScrollbackOutput(session, revision)
            }
        }
    }
}

private val TERMINAL_PADDING = 8.dp

@Composable
private fun ScrollbackOutput(session: TerminalSession, revision: Int) {
    val listState = rememberLazyListState()
    var follow by remember(session.id) { mutableStateOf(true) }
    val count = remember(revision) { session.buffer.lineCount }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) follow = !listState.canScrollForward
        }
    }
    LaunchedEffect(revision, follow) {
        if (follow && count > 0) listState.scrollToItem(count - 1)
    }

    SelectionContainer {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = TERMINAL_PADDING),
        ) {
            items(count = count) { index ->
                val line = remember(revision, index) { session.buffer.lineAt(index) }
                Text(text = line.toAnnotated(), style = terminalTextStyle)
            }
        }
    }
}

@Composable
private fun AlternateScreen(session: TerminalSession, revision: Int) {
    val snapshot = remember(revision) { session.buffer.snapshot() }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = TERMINAL_PADDING)) {
        snapshot.lines.forEachIndexed { index, line ->
            val cursor = if (snapshot.cursorVisible && index == snapshot.cursorRow) snapshot.cursorColumn else -1
            Text(
                text = line.toAnnotated(cursor),
                style = terminalTextStyle,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun SessionInput(session: TerminalSession, onFocusChange: (Boolean) -> Unit) {
    val revision by session.revision.collectAsState()
    val alternate = remember(revision) { session.buffer.isAlternateScreen }
    var directToggle by remember(session.id) { mutableStateOf(false) }
    var ctrl by remember(session.id) { mutableStateOf(false) }
    var alt by remember(session.id) { mutableStateOf(false) }
    var line by remember(session.id) { mutableStateOf("") }
    var directValue by remember(session.id) { mutableStateOf(sentinelValue()) }
    val direct = alternate || directToggle

    fun flushLine() {
        if (line.isNotEmpty()) {
            session.send(line)
            line = ""
        }
    }

    fun releaseModifiers() {
        ctrl = false
        alt = false
    }

    fun sendKey(key: TerminalKey, shift: Boolean = false) {
        flushLine()
        session.send(KeyEncoder.key(key, session.applicationCursorKeys, ctrl, alt, shift))
        releaseModifiers()
    }

    fun sendFunction(number: Int) {
        flushLine()
        session.send(KeyEncoder.function(number, ctrl, alt))
        releaseModifiers()
    }

    fun sendTyped(text: String) {
        text.forEachIndexed { index, char ->
            session.send(if (index == 0) KeyEncoder.character(char, ctrl, alt) else char.toString())
        }
        releaseModifiers()
    }

    fun submitLine() {
        session.send(line + "\n")
        line = ""
    }

    fun handleHardwareKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val terminalKey = hardwareKey(event.key)
        if (terminalKey != null) {
            flushLine()
            session.send(KeyEncoder.key(terminalKey, session.applicationCursorKeys, event.isCtrlPressed, event.isAltPressed, event.isShiftPressed))
            return true
        }
        functionNumber(event.key)?.let { number ->
            flushLine()
            session.send(KeyEncoder.function(number, event.isCtrlPressed, event.isAltPressed, event.isShiftPressed))
            return true
        }
        if (event.isCtrlPressed || event.isAltPressed) {
            val codePoint = event.utf16CodePoint
            if (codePoint in 0x20..0xFFFF) {
                flushLine()
                session.send(KeyEncoder.character(codePoint.toChar(), event.isCtrlPressed, event.isAltPressed))
                return true
            }
        }
        return false
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
        ) {
            ToggleKey(stringResource(R.string.terminal_key_ctrl), ctrl) { ctrl = !ctrl }
            ToggleKey(stringResource(R.string.terminal_key_alt), alt) { alt = !alt }
            ToggleKey(stringResource(R.string.terminal_key_direct), directToggle || alternate) { directToggle = !directToggle }
            TextButton(onClick = { sendKey(TerminalKey.ESCAPE) }) { Text(stringResource(R.string.terminal_key_escape)) }
            TextButton(onClick = { sendKey(TerminalKey.TAB) }) { Text(stringResource(R.string.terminal_key_tab)) }
            TextButton(onClick = { sendKey(TerminalKey.UP) }) { Text("↑") }
            TextButton(onClick = { sendKey(TerminalKey.DOWN) }) { Text("↓") }
            TextButton(onClick = { sendKey(TerminalKey.LEFT) }) { Text("←") }
            TextButton(onClick = { sendKey(TerminalKey.RIGHT) }) { Text("→") }
            TextButton(onClick = { sendKey(TerminalKey.HOME) }) { Text(stringResource(R.string.terminal_key_home)) }
            TextButton(onClick = { sendKey(TerminalKey.END) }) { Text(stringResource(R.string.terminal_key_end)) }
            TextButton(onClick = { sendKey(TerminalKey.PAGE_UP) }) { Text(stringResource(R.string.terminal_key_page_up)) }
            TextButton(onClick = { sendKey(TerminalKey.PAGE_DOWN) }) { Text(stringResource(R.string.terminal_key_page_down)) }
            TextButton(onClick = { sendKey(TerminalKey.DELETE) }) { Text(stringResource(R.string.terminal_key_delete)) }
            TextButton(onClick = { sendKey(TerminalKey.INSERT) }) { Text(stringResource(R.string.terminal_key_insert)) }
            TextButton(onClick = {
                line = ""
                session.sendInterrupt()
            }) { Text(stringResource(R.string.terminal_key_interrupt)) }
            TextButton(onClick = {
                flushLine()
                session.send(EOF_CHAR)
            }) { Text(stringResource(R.string.terminal_key_eof)) }
            for (number in 1..FUNCTION_KEYS) {
                TextButton(onClick = { sendFunction(number) }) { Text("F$number") }
            }
            TextButton(onClick = session::clearScreen) { Text(stringResource(R.string.terminal_clear)) }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (direct) {
                OutlinedTextField(
                    value = directValue,
                    onValueChange = { value ->
                        if (value.text.length < DIRECT_SENTINEL.length) {
                            sendKey(TerminalKey.BACKSPACE)
                        } else {
                            val typed = value.text.substring(DIRECT_SENTINEL.length)
                            if (typed.isNotEmpty()) sendTyped(typed)
                        }
                        directValue = sentinelValue()
                    },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.terminal_direct_hint)) },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(onSend = { sendKey(TerminalKey.ENTER) }),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { onFocusChange(it.isFocused) }
                        .onPreviewKeyEvent(::handleHardwareKey),
                )
            } else {
                OutlinedTextField(
                    value = line,
                    onValueChange = { line = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.terminal_input_hint)) },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(onSend = { submitLine() }),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { onFocusChange(it.isFocused) }
                        .onPreviewKeyEvent(::handleHardwareKey),
                )
                TextButton(onClick = ::submitLine) { Text(stringResource(R.string.terminal_send)) }
            }
        }
    }
}

@Composable
private fun ToggleKey(title: String, active: Boolean, onClick: () -> Unit) {
    val background = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge)
    }
}

private fun sentinelValue() = TextFieldValue(DIRECT_SENTINEL, TextRange(DIRECT_SENTINEL.length))

private fun hardwareKey(key: Key): TerminalKey? = when (key) {
    Key.DirectionUp -> TerminalKey.UP
    Key.DirectionDown -> TerminalKey.DOWN
    Key.DirectionLeft -> TerminalKey.LEFT
    Key.DirectionRight -> TerminalKey.RIGHT
    Key.MoveHome -> TerminalKey.HOME
    Key.MoveEnd -> TerminalKey.END
    Key.PageUp -> TerminalKey.PAGE_UP
    Key.PageDown -> TerminalKey.PAGE_DOWN
    Key.Insert -> TerminalKey.INSERT
    Key.Delete -> TerminalKey.DELETE
    Key.Escape -> TerminalKey.ESCAPE
    Key.Tab -> TerminalKey.TAB
    else -> null
}

private fun functionNumber(key: Key): Int? = when (key) {
    Key.F1 -> 1
    Key.F2 -> 2
    Key.F3 -> 3
    Key.F4 -> 4
    Key.F5 -> 5
    Key.F6 -> 6
    Key.F7 -> 7
    Key.F8 -> 8
    Key.F9 -> 9
    Key.F10 -> 10
    Key.F11 -> 11
    Key.F12 -> 12
    else -> null
}

private fun StyledLine.toAnnotated(cursor: Int = -1): AnnotatedString {
    val length = maxOf(text.length, cursor + 1)
    if (length == 0) return AnnotatedString(" ")
    return buildAnnotatedString {
        var start = 0
        while (start < length) {
            val style = styles.getOrNull(start) ?: CellStyle()
            val atCursor = start == cursor
            var end = start + 1
            if (!atCursor) {
                while (end < length && end != cursor && (styles.getOrNull(end) ?: CellStyle()) == style) end++
            }
            val chunk = buildString {
                for (i in start until end) append(text.getOrElse(i) { ' ' })
            }
            val inverse = style.inverse != atCursor
            val foreground = style.foreground?.let { Color(it) }
            val background = style.background?.let { Color(it) }
            val shownForeground = if (inverse) background ?: TerminalBackground else foreground
            val shownBackground = if (inverse) foreground ?: TerminalForeground else background
            withStyle(
                SpanStyle(
                    color = shownForeground ?: Color.Unspecified,
                    background = shownBackground ?: Color.Unspecified,
                    fontWeight = if (style.bold) FontWeight.Bold else null,
                    textDecoration = if (style.underline) TextDecoration.Underline else null,
                ),
            ) {
                append(chunk)
            }
            start = end
        }
    }
}
