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

val EDITOR_LANGUAGES = listOf("plain", "cream", "python", "java", "javascript", "cpp", "csharp", "objectivec")

fun languageTitle(language: String): String = when (language) {
    "cream" -> "Cream"
    "python" -> "Python"
    "java" -> "Java"
    "javascript" -> "JavaScript"
    "cpp" -> "C / C++"
    "csharp" -> "C#"
    "objectivec" -> "Objective-C"
    else -> "Обычный текст"
}

object LanguageGuess {

    private const val SAMPLE_LIMIT = 20_000

    private val objectiveC = Regex("""@interface\b|@implementation\b|\bNSLog\s*\(|#import\b|\bNSString\b""")
    private val java = Regex("""\bpublic\s+(static\s+|final\s+|abstract\s+)*(class|interface|enum|void)\b|System\.out\.print|\bimport\s+java\.""")
    private val csharp = Regex("""\busing\s+System\b|Console\.Write|\bnamespace\s+\w+|\bstatic\s+void\s+Main\b""")
    private val cream = Regex("""(?m)^\s*(say|wait|together|repeat)\b\s*["\w(]|^\s*(action|task|struct)\s+\w+|^\s*for each\s""")
    private val python = Regex("""(?m)^\s*(def|class)\s+\w+[^\n]*:\s*$|^\s*(import|from)\s+\w+|^\s*(elif|except)\b[^\n]*:\s*$|^\s*if\s+__name__""")
    private val javascript = Regex("""\b(const|let)\s+\w+\s*=|\bfunction\s*\w*\s*\(|\bconsole\.log\b|=>\s*[{(\w]|\brequire\s*\(""")
    private val cpp = Regex("""#include\s*[<"]|\bstd::|\bint\s+main\s*\(|\bcout\s*<<|\bprintf\s*\(""")

    fun guess(text: String): String {
        val sample = if (text.length > SAMPLE_LIMIT) text.substring(0, SAMPLE_LIMIT) else text
        if (sample.isBlank()) return "plain"
        if (sample.startsWith("#!")) {
            val first = sample.lineSequence().first()
            if ("python" in first) return "python"
            if ("node" in first) return "javascript"
        }
        return when {
            objectiveC.containsMatchIn(sample) -> "objectivec"
            java.containsMatchIn(sample) -> "java"
            csharp.containsMatchIn(sample) -> "csharp"
            cpp.containsMatchIn(sample) -> "cpp"
            cream.containsMatchIn(sample) -> "cream"
            python.containsMatchIn(sample) -> "python"
            javascript.containsMatchIn(sample) -> "javascript"
            else -> "plain"
        }
    }
}
