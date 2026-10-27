package com.mauyaapps.aircode.editor

/** Определяет язык подсветки по имени файла. Идентификаторы совпадают с `editor/web/src/main.js`. */
fun languageForFile(fileName: String): String =
    when (fileName.substringAfterLast('.', "").lowercase()) {
        "py", "pyw" -> "python"
        "java" -> "java"
        "js", "mjs", "cjs", "jsx" -> "javascript"
        "c", "h", "cpp", "cc", "cxx", "hpp", "hh", "hxx" -> "cpp"
        "cs" -> "csharp"
        "m", "mm" -> "objectivec"
        "cream" -> "cream"
        else -> "plain"
    }
