package com.mauyaapps.aircode.editor

enum class PreviewKind { MARKDOWN, HTML }

fun previewKindForFile(fileName: String): PreviewKind? =
    when (fileName.substringAfterLast('.', "").lowercase()) {
        "md", "markdown" -> PreviewKind.MARKDOWN
        "html", "htm" -> PreviewKind.HTML
        else -> null
    }
