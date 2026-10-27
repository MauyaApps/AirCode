package com.mauyaapps.aircode.editor

/**
 * Документ вкладки. [id] уникален для каждой открытой вкладки;
 * [text] — исходный текст на момент открытия, дальше текст живёт в редакторе.
 */
class EditorDocument(
    val id: Long,
    val text: String,
    val language: String,
)

/** Позиция курсора, отсчёт с 1. */
data class EditorCursor(val line: Int, val column: Int)

/** Результат поиска: номер выбранного совпадения (с 1, 0 — ничего не выбрано) и их общее число. */
data class SearchResult(val current: Int, val total: Int)
