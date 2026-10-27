package com.mauyaapps.aircode.editor

/** Что умеет показывать просмотр: Markdown превращается в страницу, HTML открывается как есть. */
enum class PreviewKind { MARKDOWN, HTML }

/** Вид просмотра для файла или null, если для него просмотра нет. */
fun previewKindForFile(fileName: String): PreviewKind? =
    when (fileName.substringAfterLast('.', "").lowercase()) {
        "md", "markdown" -> PreviewKind.MARKDOWN
        "html", "htm" -> PreviewKind.HTML
        else -> null
    }
