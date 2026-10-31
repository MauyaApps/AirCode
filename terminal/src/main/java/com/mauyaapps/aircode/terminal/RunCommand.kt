package com.mauyaapps.aircode.terminal

data class Requirement(val check: String, val packages: String)

data class RunPlan(val requirements: List<Requirement>, val command: String)

data class CustomRunner(val command: String, val packages: List<String>)

object CustomRunners {

    const val FILE_NAME = "aircode-runners.txt"

    private val EXTENSION = Regex("[a-z0-9_+-]+")
    private val PACKAGE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._+-]*")

    fun parse(text: String): Map<String, CustomRunner> {
        val commands = LinkedHashMap<String, String>()
        val packages = HashMap<String, List<String>>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val separator = line.indexOf('=')
            if (separator <= 0) continue
            val key = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim()
            if (value.isEmpty()) continue
            if (key.endsWith(".packages")) {
                val extension = key.removeSuffix(".packages")
                if (EXTENSION.matches(extension)) {
                    packages[extension] = value.split(' ', '\t').filter { it.isNotEmpty() && PACKAGE_NAME.matches(it) }
                }
            } else if (EXTENSION.matches(key)) {
                commands[key] = value
            }
        }
        return commands.mapValues { (extension, command) -> CustomRunner(command, packages[extension].orEmpty()) }
    }
}

object RunCommand {

    const val OUTPUT_BINARY = "/tmp/aircode-run"
    const val OUTPUT_EXECUTABLE = "/tmp/aircode-run.exe"

    private val python = listOf(Requirement("command -v python3", "python3"))
    private val node = listOf(Requirement("command -v node", "nodejs"))
    private val java = listOf(Requirement("command -v java", "openjdk21-jdk"))
    private val c = listOf(Requirement("command -v gcc", "build-base"))
    private val cpp = listOf(Requirement("command -v g++", "build-base"))
    private val csharp = listOf(Requirement("command -v mcs", "mono"))
    private val objectiveC = listOf(
        Requirement("command -v gcc", "build-base"),
        Requirement("ls /usr/libexec/gcc/*/*/cc1obj", "gcc-objc"),
    )

    fun isRunnable(fileName: String, custom: Map<String, CustomRunner> = emptyMap()): Boolean =
        extension(fileName) in SUPPORTED || extension(fileName) in custom

    fun plan(fileName: String, guestPath: String, custom: Map<String, CustomRunner> = emptyMap()): RunPlan? {
        val path = quote(guestPath)
        custom[extension(fileName)]?.let { runner ->
            val directory = guestPath.substringBeforeLast('/', "/").ifEmpty { "/" }
            val command = runner.command
                .replace("{file}", path)
                .replace("{dir}", quote(directory))
                .replace("{name}", quote(fileName))
            val requirements = runner.packages.map { Requirement("apk info -e $it", it) }
            return RunPlan(requirements, command)
        }
        return when (extension(fileName)) {
            "py", "pyw" -> RunPlan(python, "python3 $path")
            "js", "mjs", "cjs" -> RunPlan(node, "node $path")
            "java" -> RunPlan(java, "java $path")
            "c" -> RunPlan(c, "gcc $path -o $OUTPUT_BINARY && $OUTPUT_BINARY")
            "cpp", "cc", "cxx" -> RunPlan(cpp, "g++ $path -o $OUTPUT_BINARY && $OUTPUT_BINARY")
            "cs" -> RunPlan(csharp, "mcs $path -out:$OUTPUT_EXECUTABLE && mono $OUTPUT_EXECUTABLE")
            "m" -> RunPlan(objectiveC, "gcc -x objective-c $path -lobjc -o $OUTPUT_BINARY && $OUTPUT_BINARY")
            "mm" -> RunPlan(objectiveC, "g++ -x objective-c++ $path -lobjc -o $OUTPUT_BINARY && $OUTPUT_BINARY")
            else -> null
        }
    }

    fun script(plan: RunPlan, doneFormat: String): String {
        val installs = plan.requirements.joinToString(" && ") { requirement ->
            "(${requirement.check} >/dev/null 2>&1 || apk add --no-progress ${requirement.packages})"
        }
        val body = if (installs.isEmpty()) plan.command else "$installs && ${plan.command}"
        return "$body; printf ${quote("\\n$doneFormat\\n")} \$?"
    }

    fun installScript(packages: List<String>): String? {
        val names = packages.map { it.trim() }.filter { it.isNotEmpty() }
        if (names.isEmpty() || names.any { !PACKAGE_NAME.matches(it) }) return null
        return "apk add --no-progress ${names.joinToString(" ")}"
    }

    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun extension(fileName: String): String = fileName.substringAfterLast('.', "").lowercase()

    private val SUPPORTED = setOf("py", "pyw", "js", "mjs", "cjs", "java", "c", "cpp", "cc", "cxx", "cs", "m", "mm")
    private val PACKAGE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._+-]*")
}
