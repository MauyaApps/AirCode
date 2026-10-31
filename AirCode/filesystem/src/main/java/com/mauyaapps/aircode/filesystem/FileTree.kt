package com.mauyaapps.aircode.filesystem

import java.io.File

object FileTree {

    fun flatten(root: File, expanded: Set<String>): List<TreeEntry> {
        val result = mutableListOf<TreeEntry>()

        fun walk(dir: File, depth: Int) {
            val children = dir.listFiles()?.sortedWith(
                compareByDescending<File> { it.isDirectory }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            ) ?: return

            for (child in children) {
                val isDirectory = child.isDirectory
                val isExpanded = isDirectory && child.path in expanded
                result += TreeEntry(child, depth, isDirectory, isExpanded)
                if (isExpanded) walk(child, depth + 1)
            }
        }

        walk(root, 0)
        return result
    }
}
