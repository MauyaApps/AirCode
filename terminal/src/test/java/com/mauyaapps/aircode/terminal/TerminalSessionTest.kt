package com.mauyaapps.aircode.terminal

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalSessionTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun session(vararg command: String, localEcho: Boolean = false) = TerminalSession(
        id = 1,
        title = "t",
        spec = LaunchSpec(command.toList(), mapOf("AIRCODE_TEST" to "ok"), File(".")),
        localEcho = localEcho,
        startFailedFormat = "failed: %s\n",
        exitedFormat = "\n[exit %d]\n",
        scope = scope,
    )

    private fun waitFor(timeoutMs: Long = 5000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue("timeout", condition())
    }

    @Test
    fun capturesOutputAndExitCode() {
        val s = session("/bin/sh", "-c", "printf 'hello\\n'; echo \$AIRCODE_TEST; exit 3")
        s.start()
        waitFor { s.exitCode.value != null }
        assertEquals(3, s.exitCode.value)
        val text = s.buffer.text()
        assertTrue(text, text.startsWith("hello\nok\n"))
        assertTrue(text, text.contains("[exit 3]"))
    }

    @Test
    fun writesInputInOrderAndEchoesLocally() {
        val s = session("/bin/sh", localEcho = true)
        s.start()
        s.send("echo one\n")
        s.send("echo two\n")
        s.send("exit 0\n")
        waitFor { s.exitCode.value != null }
        val text = s.buffer.text()
        val one = text.indexOf("one")
        val two = text.indexOf("two")
        assertTrue(text, one >= 0 && two > one)
        assertTrue(text, text.contains("echo one"))
    }

    @Test
    fun startFailureIsReported() {
        val s = session("/definitely/not/here")
        s.start()
        assertEquals(-1, s.exitCode.value)
        assertTrue(s.buffer.text().startsWith("failed:"))
    }

    @Test
    fun closeKillsProcess() {
        val s = session("/bin/sh", "-c", "sleep 30")
        s.start()
        s.close()
        waitFor { s.exitCode.value != null }
    }

    @Test
    fun utf8SurvivesChunking() {
        val script = "i=0; while [ \$i -lt 5000 ]; do printf '\\321\\226'; i=\$((i+1)); done"
        val s = session("/bin/sh", "-c", script)
        s.start()
        waitFor { s.exitCode.value != null }
        val line = s.buffer.lineAt(0).text
        assertEquals(5000, line.count { it == '\u0456' })
        assertEquals(5000, line.length)
    }
}
