package com.mauyaapps.aircode.editor

import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

class EditorController {

    var cursor by mutableStateOf(EditorCursor(line = 1, column = 1))
        private set

    var searchResult by mutableStateOf(SearchResult(current = 0, total = 0))
        private set

    internal var onChanged: () -> Unit = {}

    private var webView: WebView? = null
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
        if (isReady) apply()
    }

    fun requestText(tabId: Long, onResult: (String) -> Unit) {
        if (!isReady) return
        val id = nextRequestId++
        textRequests[id] = TextRequest(onResult)
        evaluate("AirCode.requestText($id, $tabId)")
    }

    fun insertText(text: String) {
        if (isReady) evaluate("AirCode.insertText(${JSONObject.quote(text)})")
    }

    fun indent() {
        if (isReady) evaluate("AirCode.indent()")
    }

    fun find(query: String, caseSensitive: Boolean) {
        if (isReady) evaluate("AirCode.find(${JSONObject.quote(query)}, $caseSensitive)")
    }

    fun findNext() {
        if (isReady) evaluate("AirCode.findNext()")
    }

    fun findPrevious() {
        if (isReady) evaluate("AirCode.findPrevious()")
    }

    fun clearSearch() {
        searchResult = SearchResult(current = 0, total = 0)
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

    internal fun attach(view: WebView) {
        webView = view
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
        document.text.chunked(CHUNK_SIZE).forEach { part ->
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
    }
}
