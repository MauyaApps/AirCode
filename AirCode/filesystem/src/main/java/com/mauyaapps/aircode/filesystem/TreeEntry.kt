package com.mauyaapps.aircode.filesystem

import java.io.File

data class TreeEntry(
    val file: File,
    val depth: Int,
    val isDirectory: Boolean,
    val isExpanded: Boolean,
)
