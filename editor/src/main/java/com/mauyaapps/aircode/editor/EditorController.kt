package com.mauyaapps.aircode.editor

import android.os.Build
import android.view.View
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.json.JSONObject

class EditorController {

    var cursor by mutableStateOf(EditorCursor(line = 1, column = 1))
        private set

    var searchResult by mutableStateOf(SearchResult(current = 0, total = 0))
        private set

    internal var onChanged: () -> Unit = {}

    var nativeMatch by mutableStateOf<TextRange?>(null)
        private set

    var tapOffset by mutableStateOf<Int?>(null)
        private set

    private val nativeValues = mutableStateMapOf<Long, TextFieldValue>()
    private val knownText = mutableMapOf<Long, String>()
    private var engine = EditorEngine.NATIVE
    private var searchQuery = ""
    private var searchCaseSensitive = false

    private var webView: WebView? = null
    private var renderMode = EditorRenderMode.SOFTWARE
    private var isReady = false
    private var darkTheme = false
    private var autocompleteEnabled = false
    private var documents: List<EditorDocument> = emptyList()
    private var activeId: Long? = null

    private val loaded = mutableSetOf<Long>()
    private var shownId: Long? = null
    private var nextRequestId = 0
    private val textRequests = mutableMapOf<Int, TextRequest>()

    fun sync(documents: List<EditorDocument>, activeId: Long?) {
        this.documents = documents
        this.activeId = activeId
        knownText.keys.retainAll(documents.map { it.id }.toSet())
        if (engine == EditorEngine.NATIVE) syncNative() else if (isReady) apply()
    }

    internal fun setEngine(value: EditorEngine) {
        if (value == engine) return
        engine = value
        nativeValues.clear()
        nativeMatch = null
        tapOffset = null
        searchResult = SearchResult(current = 0, total = 0)
        if (value == EditorEngine.NATIVE) syncNative()
    }

    internal fun nativeValue(id: Long): TextFieldValue? = nativeValues[id]

    internal fun consumeTap() {
        tapOffset = null
    }

    internal fun applyFix(id: Long, fix: Fix) {
        val current = nativeValues[id] ?: return
        val start = fix.start.coerceIn(0, current.text.length)
        val end = fix.end.coerceIn(start, current.text.length)
        val updated = current.text.replaceRange(start, end, fix.replacement)
        tapOffset = null
        updateNative(id, TextFieldValue(updated, TextRange(start + fix.replacement.length)))
    }

    internal fun updateNative(id: Long, value: TextFieldValue) {
        val old = nativeValues[id]
        nativeValues[id] = value
        if (id == activeId) cursor = cursorOf(value)
        if (old != null && old.text == value.text && value.selection.collapsed && old.selection != value.selection) {
            tapOffset = value.selection.start
        }
        if (old != null && old.text != value.text) {
            if (nativeMatch != null) {
                nativeMatch = null
                searchResult = SearchResult(current = 0, total = 0)
            }
            onChanged()
        }
    }

    private fun syncNative() {
        val ids = documents.map { it.id }.toSet()
        nativeValues.keys.toList().filter { it !in ids }.forEach { nativeValues.remove(it) }
        documents.forEach { document ->
            if (document.id !in nativeValues) nativeValues[document.id] = TextFieldValue(textFor(document))
        }
        activeId?.let { id -> nativeValues[id]?.let { cursor = cursorOf(it) } }
    }

    private fun textFor(document: EditorDocument): String = knownText[document.id] ?: document.text

    private fun cursorOf(value: TextFieldValue): EditorCursor {
        val end = value.selection.end.coerceIn(0, value.text.length)
        val before = value.text.substring(0, end)
        val line = before.count { it == '\n' } + 1
        val column = end - before.lastIndexOf('\n')
        return EditorCursor(line, column)
    }

    private fun editNative(transform: (TextFieldValue) -> TextFieldValue) {
        val id = activeId ?: return
        val current = nativeValues[id] ?: return
        updateNative(id, transform(current))
    }

    private fun replaceSelection(value: TextFieldValue, text: String): TextFieldValue {
        val start = minOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
        val end = maxOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
        val updated = value.text.replaceRange(start, end, text)
        return TextFieldValue(updated, TextRange(start + text.length))
    }

    private enum class SearchStep { FIRST, NEXT, PREVIOUS }

    private fun searchNative(step: SearchStep) {
        val id = activeId ?: return
        val current = nativeValues[id] ?: return
        val hits = TextSearch.matches(current.text, searchQuery, searchCaseSensitive)
        if (hits.isEmpty()) {
            nativeMatch = null
            searchResult = SearchResult(current = 0, total = 0)
            return
        }
        val from = minOf(current.selection.start, current.selection.end)
        val index = when (step) {
            SearchStep.FIRST -> hits.indexOfFirst { it >= from }.takeIf { it >= 0 } ?: 0
            SearchStep.NEXT -> hits.indexOfFirst { it > from }.takeIf { it >= 0 } ?: 0
            SearchStep.PREVIOUS -> hits.indexOfLast { it < from }.takeIf { it >= 0 } ?: hits.lastIndex
        }
        val range = TextRange(hits[index], hits[index] + searchQuery.length)
        nativeMatch = range
        nativeValues[id] = current.copy(selection = range)
        searchResult = SearchResult(current = index + 1, total = hits.size)
    }

    fun requestText(tabId: Long, onResult: (String) -> Unit) {
        if (engine == EditorEngine.NATIVE) {
            val text = nativeValues[tabId]?.text ?: return
            knownText[tabId] = text
            onResult(text)
            return
        }
        if (!isReady) return
        val id = nextRequestId++
        textRequests[id] = TextRequest { text ->
            knownText[tabId] = text
            onResult(text)
        }
        evaluate("AirCode.requestText($id, $tabId)")
    }

    fun insertText(text: String) {
        if (engine == EditorEngine.NATIVE) {
            editNative { replaceSelection(it, text) }
        } else if (isReady) {
            evaluate("AirCode.insertText(${JSONObject.quote(text)})")
        }
    }

    fun indent() {
        if (engine == EditorEngine.NATIVE) {
            editNative { replaceSelection(it, INDENT) }
        } else if (isReady) {
            evaluate("AirCode.indent()")
        }
    }

    fun find(query: String, caseSensitive: Boolean) {
        searchQuery = query
        searchCaseSensitive = caseSensitive
        if (engine == EditorEngine.NATIVE) {
            searchNative(SearchStep.FIRST)
        } else if (isReady) {
            evaluate("AirCode.find(${JSONObject.quote(query)}, $caseSensitive)")
        }
    }

    fun findNext() {
        if (engine == EditorEngine.NATIVE) {
            searchNative(SearchStep.NEXT)
        } else if (isReady) {
            evaluate("AirCode.findNext()")
        }
    }

    fun findPrevious() {
        if (engine == EditorEngine.NATIVE) {
            searchNative(SearchStep.PREVIOUS)
        } else if (isReady) {
            evaluate("AirCode.findPrevious()")
        }
    }

    fun clearSearch() {
        searchResult = SearchResult(current = 0, total = 0)
        nativeMatch = null
        if (isReady) evaluate("AirCode.clearSearch()")
    }

    internal fun setAutocomplete(enabled: Boolean) {
        autocompleteEnabled = enabled
        if (isReady) evaluate("AirCode.setAutocomplete($enabled)")
    }

    internal fun setDarkTheme(dark: Boolean) {
        darkTheme = dark
        if (isReady) evaluate("AirCode.setTheme($dark)")
    }

    internal fun setRenderMode(mode: EditorRenderMode) {
        renderMode = mode
        applyRenderMode()
    }

    fun refresh() {
        val view = webView ?: return
        view.invalidate()
        if (isReady) evaluate("AirCodeDiag.refresh()")
    }

    fun diagnose(onResult: (String) -> Unit) {
        if (engine == EditorEngine.NATIVE) {
            val id = activeId
            val value = id?.let { nativeValues[it] }
            onResult(
                "Движок: встроенный редактор\n" +
                    "Документов: ${documents.size}, показан: $id\n" +
                    "Длина текста: ${value?.text?.length ?: 0}\n" +
                    "Язык: ${documents.firstOrNull { it.id == id }?.language}",
            )
            return
        }
        val view = webView
        val native = buildString {
            append("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("WebView: ${webViewVersion()}\n")
            append("Режим отрисовки: $renderMode\n")
            if (view == null) {
                append("WebView не создан\n")
            } else {
                append("Размер: ${view.width}x${view.height}, слой: ${layerName(view.layerType)}, аппаратное ускорение: ${view.isHardwareAccelerated}\n")
                append("Видимость: ${view.visibility}, показан: ${view.isShown}, alpha: ${view.alpha}\n")
            }
            append("Страница готова: $isReady, документов: ${documents.size}, показан: $shownId\n")
        }
        if (view == null || !isReady) {
            onResult(native + "JS недоступен")
            return
        }
        view.evaluateJavascript("AirCodeDiag.diagnose()") { raw ->
            val text = try {
                org.json.JSONTokener(raw).nextValue()?.toString().orEmpty()
            } catch (e: org.json.JSONException) {
                raw.orEmpty()
            }
            onResult(native + "JS: " + text)
        }
    }

    private fun applyRenderMode() {
        val view = webView ?: return
        view.setLayerType(
            if (renderMode == EditorRenderMode.SOFTWARE) View.LAYER_TYPE_SOFTWARE else View.LAYER_TYPE_NONE,
            null,
        )
        view.invalidate()
    }

    private fun layerName(type: Int): String = when (type) {
        View.LAYER_TYPE_SOFTWARE -> "software"
        View.LAYER_TYPE_HARDWARE -> "hardware"
        else -> "none"
    }

    private fun webViewVersion(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WebView.getCurrentWebViewPackage()?.let { "${it.packageName} ${it.versionName}" } ?: "неизвестно"
        } else {
            "неизвестно"
        }

    internal fun attach(view: WebView) {
        webView = view
        applyRenderMode()
        isReady = false
        loaded.clear()
        shownId = null
    }

    internal fun detach() {
        webView = null
        isReady = false
        loaded.clear()
        shownId = null
        textRequests.clear()
    }

    internal fun handle(message: EditorMessage) {
        when (message) {
            EditorMessage.Ready -> {
                isReady = true
                evaluate("AirCode.setTheme($darkTheme)")
                evaluate("AirCode.setAutocomplete($autocompleteEnabled)")
                apply()
                refresh()
            }

            EditorMessage.Changed -> onChanged()
            is EditorMessage.Cursor -> cursor = EditorCursor(message.line, message.column)
            is EditorMessage.Search -> searchResult = SearchResult(message.current, message.total)
            is EditorMessage.TextChunk -> collect(message)
        }
    }

    private fun collect(chunk: EditorMessage.TextChunk) {
        val request = textRequests[chunk.requestId] ?: return
        request.builder.append(chunk.data)
        request.received++
        if (request.received >= chunk.total) {
            textRequests.remove(chunk.requestId)
            request.onResult(request.builder.toString())
        }
    }

    private fun apply() {
        val ids = documents.map { it.id }.toSet()
        (loaded - ids).forEach { evaluate("AirCode.close($it)") }
        loaded.retainAll(ids)
        documents.filter { it.id !in loaded }.forEach {
            send(it)
            loaded += it.id
        }
        val target = activeId
        if (target != null && target != shownId) evaluate("AirCode.show($target)")
        shownId = target
    }

    private fun send(document: EditorDocument) {
        evaluate("AirCode.beginOpen(${document.id}, ${JSONObject.quote(document.language)})")
        textFor(document).chunked(CHUNK_SIZE).forEach { part ->
            evaluate("AirCode.appendChunk(${JSONObject.quote(part)})")
        }
        evaluate("AirCode.endOpen()")
    }

    private fun evaluate(script: String) {
        webView?.evaluateJavascript(script, null)
    }

    private class TextRequest(val onResult: (String) -> Unit) {
        val builder = StringBuilder()
        var received = 0
    }

    private companion object {
        const val CHUNK_SIZE = 100_000
        const val INDENT = "    "
    }
}
