package com.mauyaapps.aircode.editor

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
