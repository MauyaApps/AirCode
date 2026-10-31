package com.mauyaapps.aircode.editor

import org.json.JSONException
import org.json.JSONObject

internal sealed interface EditorMessage {
    data object Ready : EditorMessage

    data object Changed : EditorMessage

    data class Cursor(val line: Int, val column: Int) : EditorMessage

    data class Search(val current: Int, val total: Int) : EditorMessage

    data class TextChunk(
        val requestId: Int,
        val index: Int,
        val total: Int,
        val data: String,
    ) : EditorMessage
}

internal object EditorMessageParser {

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
