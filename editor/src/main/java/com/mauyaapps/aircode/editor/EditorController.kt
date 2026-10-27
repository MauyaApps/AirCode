package com.mauyaapps.aircode.editor

import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * Управляет редактором в WebView: отправляет команды в JS и принимает события.
 * Все методы вызываются из главного потока. Список вкладок задаётся через [sync];
 * если редактор ещё не готов, применяется последнее заданное состояние.
 */
class EditorController {

    /** Текущая позиция курсора. */
    var cursor by mutableStateOf(EditorCursor(line = 1, column = 1))
        private set

    /** Результат поиска по файлу; обновляется после каждого запроса и правки текста. */
    var searchResult by mutableStateOf(SearchResult(current = 0, total = 0))
        private set

    /** Вызывается, когда пользователь изменил текст. */
    internal var onChanged: () -> Unit = {}

    private var webView: WebView? = null
    private var isReady = false
    private var darkTheme = false
    private var autocompleteEnabled = false
    private var documents: List<EditorDocument> = emptyList()
    private var activeId: Long? = null

    // Вкладки, уже переданные в редактор, и вкладка, показанная в нём сейчас
    private val loaded = mutableSetOf<Long>()
    private var shownId: Long? = null
    private var nextRequestId = 0
    private val textRequests = mutableMapOf<Int, TextRequest>()

    /**
     * Приводит редактор к списку вкладок: новые загружает, исчезнувшие забывает,
     * активную показывает. История отмены каждой вкладки сохраняется в редакторе.
     */
    fun sync(documents: List<EditorDocument>, activeId: Long?) {
        this.documents = documents
        this.activeId = activeId
        if (isReady) apply()
    }

    /** Запрашивает текущий текст вкладки [tabId]. Результат придёт в [onResult] в главном потоке. */
    fun requestText(tabId: Long, onResult: (String) -> Unit) {
        if (!isReady) return
        val id = nextRequestId++
        textRequests[id] = TextRequest(onResult)
        evaluate("AirCode.requestText($id, $tabId)")
    }

    /** Вставляет [text] на место выделения. Редактор сохраняет фокус и клавиатуру. */
    fun insertText(text: String) {
        if (isReady) evaluate("AirCode.insertText(${JSONObject.quote(text)})")
    }

    /** Увеличивает отступ так же, как клавиша Tab. */
    fun indent() {
        if (isReady) evaluate("AirCode.indent()")
    }

    /** Ищет [query] в активной вкладке и выбирает первое совпадение от курсора. */
    fun find(query: String, caseSensitive: Boolean) {
        if (isReady) evaluate("AirCode.find(${JSONObject.quote(query)}, $caseSensitive)")
    }

    fun findNext() {
        if (isReady) evaluate("AirCode.findNext()")
    }

    fun findPrevious() {
        if (isReady) evaluate("AirCode.findPrevious()")
    }

    /** Заканчивает поиск: убирает подсветку и сбрасывает счётчик. */
    fun clearSearch() {
        searchResult = SearchResult(current = 0, total = 0)
        if (isReady) evaluate("AirCode.clearSearch()")
    }

    /** Включает или выключает подсказки при вводе. */
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
        /** Размер порции текста в символах, совпадает с CHUNK_SIZE в main.js. */
        const val CHUNK_SIZE = 100_000
    }
}
