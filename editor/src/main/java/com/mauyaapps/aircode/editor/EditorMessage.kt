package com.mauyaapps.aircode.editor

import org.json.JSONException
import org.json.JSONObject

/** События из редактора (JS) в Kotlin. Формат — JSON, поле `type` задаёт вид сообщения. */
internal sealed interface EditorMessage {
    /** Страница загружена, редактор готов принимать команды. */
    data object Ready : EditorMessage

    /** Пользователь изменил текст. Сам текст не передаётся. */
    data object Changed : EditorMessage

    data class Cursor(val line: Int, val column: Int) : EditorMessage

    /**
     * Результат поиска: номер выбранного совпадения [current] (с 1, 0 — ничего не выбрано)
     * и общее число совпадений [total].
     */
    data class Search(val current: Int, val total: Int) : EditorMessage

    /** Порция текста документа в ответ на запрос [requestId]. */
    data class TextChunk(
        val requestId: Int,
        val index: Int,
        val total: Int,
        val data: String,
    ) : EditorMessage
}

internal object EditorMessageParser {

    /** Разбирает JSON-сообщение. Для неизвестного или повреждённого сообщения возвращает null. */
    fun parse(json: String): EditorMessage? = try {
        val obj = JSONObject(json)
        when (obj.getString("type")) {
            "ready" -> EditorMessage.Ready
            "changed" -> EditorMessage.Changed
            "cursor" -> EditorMessage.Cursor(obj.getInt("line"), obj.getInt("column"))
            "search" -> EditorMessage.Search(obj.getInt("current"), obj.getInt("total"))
            "text" -> EditorMessage.TextChunk(
                requestId = obj.getInt("requestId"),
                index = obj.getInt("index"),
                total = obj.getInt("total"),
                data = obj.getString("data"),
            )
            else -> null
        }
    } catch (_: JSONException) {
        null
    }
}
