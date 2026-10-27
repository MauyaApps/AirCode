package com.mauyaapps.aircode.filesystem

import java.io.File

/** Одна строка плоского списка дерева файлов. */
data class TreeEntry(
    val file: File,
    val depth: Int,
    val isDirectory: Boolean,
    val isExpanded: Boolean,
)
