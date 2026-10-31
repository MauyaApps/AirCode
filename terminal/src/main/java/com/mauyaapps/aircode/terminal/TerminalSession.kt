package com.mauyaapps.aircode.terminal

import java.io.IOException
import java.io.InputStreamReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TerminalSession(
    val id: Long,
    val title: String,
    private val spec: LaunchSpec,
    private val localEcho: Boolean,
    private val startFailedFormat: String,
    private val exitedFormat: String,
    private val scope: CoroutineScope,
) {
    val buffer = TerminalBuffer()

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val _exitCode = MutableStateFlow<Int?>(null)
    val exitCode: StateFlow<Int?> = _exitCode.asStateFlow()

    private val outbox = Channel<ByteArray>(Channel.UNLIMITED)
    private var process: Process? = null

    val applicationCursorKeys: Boolean get() = buffer.applicationCursorKeys

    fun start() {
        buffer.responder = { reply -> outbox.trySend(reply.toByteArray(Charsets.UTF_8)) }
        val started = try {
            ProcessBuilder(spec.command)
                .directory(spec.workingDir)
                .redirectErrorStream(true)
                .apply { environment().putAll(spec.environment) }
                .start()
        } catch (e: IOException) {
            append(String.format(startFailedFormat, e.message.orEmpty()))
            _exitCode.value = START_FAILED
            return
        }
        process = started
        scope.launch(Dispatchers.IO) { writeLoop(started) }
        scope.launch(Dispatchers.IO) { readLoop(started) }
    }

    fun send(text: String) {
        if (_exitCode.value != null || text.isEmpty()) return
        if (localEcho) append(text)
        outbox.trySend(text.toByteArray(Charsets.UTF_8))
    }

    fun sendInterrupt() {
        if (_exitCode.value != null) return
        outbox.trySend(byteArrayOf(CTRL_C))
    }

    fun resize(columns: Int, rows: Int) {
        buffer.resize(columns, rows)
        _revision.update { it + 1 }
    }

    fun clearScreen() {
        buffer.clear()
        _revision.update { it + 1 }
    }

    fun close() {
        outbox.close()
        val started = process
        if (started != null) {
            started.destroyForcibly()
            try {
                started.inputStream.close()
            } catch (e: IOException) {
                return markClosed()
            }
        }
        markClosed()
    }

    private fun markClosed() {
        _exitCode.compareAndSet(null, CLOSED)
    }

    private fun append(text: String) {
        buffer.feed(text)
        _revision.update { it + 1 }
    }

    private suspend fun writeLoop(started: Process) {
        try {
            val stream = started.outputStream
            for (bytes in outbox) {
                stream.write(bytes)
                stream.flush()
            }
        } catch (e: IOException) {
            outbox.close()
        }
    }

    private fun readLoop(started: Process) {
        try {
            InputStreamReader(started.inputStream, Charsets.UTF_8).use { reader ->
                val chars = CharArray(READ_CHUNK)
                while (true) {
                    val count = reader.read(chars)
                    if (count < 0) break
                    append(String(chars, 0, count))
                }
            }
        } catch (e: IOException) {
            outbox.close()
        }
        val code = try {
            started.waitFor()
        } catch (e: InterruptedException) {
            START_FAILED
        }
        outbox.close()
        if (_exitCode.value == null) {
            append(String.format(exitedFormat, code))
            _exitCode.compareAndSet(null, code)
        }
    }

    private companion object {
        const val READ_CHUNK = 4096
        const val START_FAILED = -1
        const val CLOSED = -2
        const val CTRL_C: Byte = 3
    }
}
