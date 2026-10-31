package com.mauyaapps.aircode.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunCommandTest {

    @Test
    fun runnableExtensions() {
        listOf("a.py", "a.JS", "A.java", "a.c", "a.cpp", "a.cc", "a.cs", "a.m", "a.mm").forEach { assertTrue(it, RunCommand.isRunnable(it)) }
        listOf("a.txt", "a.cream", "a.h", "README").forEach { assertFalse(it, RunCommand.isRunnable(it)) }
    }

    @Test
    fun pythonPlan() {
        val plan = RunCommand.plan("main.py", "/workspace/main.py")!!
        assertEquals("python3 '/workspace/main.py'", plan.command)
        assertEquals(listOf(Requirement("command -v python3", "python3")), plan.requirements)
    }

    @Test
    fun cPlanCompilesAndRuns() {
        val plan = RunCommand.plan("a.c", "/workspace/a.c")!!
        assertEquals("gcc '/workspace/a.c' -o /tmp/aircode-run && /tmp/aircode-run", plan.command)
    }

    @Test
    fun unsupportedGivesNull() {
        assertNull(RunCommand.plan("a.cream", "/workspace/a.cream"))
        assertNull(RunCommand.plan("a.txt", "/workspace/a.txt"))
    }

    @Test
    fun quotingEscapesSingleQuotes() {
        assertEquals("'it'\\''s'", RunCommand.quote("it's"))
        val plan = RunCommand.plan("a b.py", "/workspace/it's a.py")!!
        assertEquals("python3 '/workspace/it'\\''s a.py'", plan.command)
    }

    @Test
    fun scriptInstallsMissingToolsThenRunsThenReportsExit() {
        val plan = RunCommand.plan("main.js", "/workspace/main.js")!!
        val script = RunCommand.script(plan, "[done %s]")
        assertEquals(
            "(command -v node >/dev/null 2>&1 || apk add --no-progress nodejs) && node '/workspace/main.js'; printf '\\n[done %s]\\n' \$?",
            script,
        )
        assertFalse(script.contains('\n'))
    }

    @Test
    fun objectiveCNeedsTwoRequirements() {
        val plan = RunCommand.plan("a.m", "/workspace/a.m")!!
        assertEquals(2, plan.requirements.size)
        val script = RunCommand.script(plan, "x")
        assertTrue(script.contains("&&") && script.contains("gcc-objc"))
    }

    @Test
    fun installScriptValidatesNames() {
        assertEquals("apk add --no-progress python3 py3-pip", RunCommand.installScript(listOf("python3", " py3-pip ")))
        assertNull(RunCommand.installScript(emptyList()))
        assertNull(RunCommand.installScript(listOf("git; rm -rf /")))
        assertNull(RunCommand.installScript(listOf("-X")))
    }

    @Test
    fun csharpUsesMono() {
        val plan = RunCommand.plan("Program.cs", "/workspace/Program.cs")!!
        assertEquals(listOf(Requirement("command -v mcs", "mono")), plan.requirements)
        assertEquals("mcs '/workspace/Program.cs' -out:/tmp/aircode-run.exe && mono /tmp/aircode-run.exe", plan.command)
    }

    @Test
    fun customRunnersAreParsed() {
        val text = """
            # comment
            cream = cream {file}
            cream.packages = git nodejs
            CS = dotnet run {file}
            bad ext = x
            .packages = y
            empty =
        """.trimIndent()
        val runners = CustomRunners.parse(text)
        assertEquals(setOf("cream", "cs"), runners.keys)
        assertEquals(CustomRunner("cream {file}", listOf("git", "nodejs")), runners.getValue("cream"))
        assertEquals(CustomRunner("dotnet run {file}", emptyList()), runners.getValue("cs"))
    }

    @Test
    fun customRunnerOverridesBuiltInAndSubstitutes() {
        val custom = CustomRunners.parse("py = mypy {file} in {dir} named {name}\npy.packages = py3-mypy")
        assertTrue(RunCommand.isRunnable("x.py", custom))
        assertTrue(RunCommand.isRunnable("x.cream", CustomRunners.parse("cream = cream {file}")))
        val plan = RunCommand.plan("x.py", "/workspace/sub dir/x.py", custom)!!
        assertEquals("mypy '/workspace/sub dir/x.py' in '/workspace/sub dir' named 'x.py'", plan.command)
        assertEquals(listOf(Requirement("apk info -e py3-mypy", "py3-mypy")), plan.requirements)
    }

    @Test
    fun customPackageNamesAreValidated() {
        val runners = CustomRunners.parse("x = run {file}\nx.packages = good bad;rm -rf")
        assertEquals(listOf("good"), runners.getValue("x").packages)
    }
}
