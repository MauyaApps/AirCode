package com.mauyaapps.aircode.editor

class EditorDocument(
    val id: Long,
    val text: String,
    val language: String,
)

data class EditorCursor(val line: Int, val column: Int)

data class SearchResult(val current: Int, val total: Int)
