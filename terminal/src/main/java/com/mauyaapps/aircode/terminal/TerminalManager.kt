package com.mauyaapps.aircode.terminal

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RunStart { STARTED, UNSUPPORTED, NEEDS_ENVIRONMENT, OUTSIDE_WORKSPACE }

class TerminalManager(
    context: Context,
    private val workspace: File,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val appContext = context.applicationContext

    val environment = ProotEnvironment(appContext, workspace)

    private val _sessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    val sessions: StateFlow<List<TerminalSession>> = _sessions.asStateFlow()

    private val _activeId = MutableStateFlow<Long?>(null)
    val activeId: StateFlow<Long?> = _activeId.asStateFlow()

    private var columns = ShellLauncher.DEFAULT_COLUMNS
    private var rows = ShellLauncher.DEFAULT_ROWS
    private var nextId = 0L
    private var shellCounter = 0
    private var runSession: TerminalSession? = null

    init {
        instance = this
        scope.launch {
            combine(sessions, environment.state) { list, state ->
                list.isNotEmpty() || state is EnvironmentState.Installing
            }.distinctUntilChanged().collect { needed ->
                if (needed) startService() else stopService()
            }
        }
    }

    fun installEnvironment() {
        scope.launch {
            environment.install()
            if (environment.isReady) {
                _sessions.value.toList().forEach { close(it.id) }
                newShell()
            }
        }
    }

    fun newShell(): TerminalSession {
        shellCounter++
        val title = appContext.getString(R.string.terminal_session_name, shellCounter)
        return startSession(title, interactiveSpec())
    }

    fun select(id: Long) {
        if (_sessions.value.any { it.id == id }) _activeId.value = id
    }

    fun close(id: Long) {
        val session = _sessions.value.firstOrNull { it.id == id } ?: return
        session.close()
        if (runSession?.id == id) runSession = null
        val remaining = _sessions.value.filterNot { it.id == id }
        _sessions.value = remaining
        if (_activeId.value == id) _activeId.value = remaining.lastOrNull()?.id
    }

    fun resize(newColumns: Int, newRows: Int) {
        if (newColumns == columns && newRows == rows) return
        columns = newColumns
        rows = newRows
        _sessions.value.forEach { it.resize(newColumns, newRows) }
        if (environment.isReady) {
            scope.launch(Dispatchers.IO) {
                try {
                    environment.paths.sizeFile.writeText(ShellLauncher.sizeLine(newColumns, newRows))
                } catch (e: java.io.IOException) {
                    return@launch
                }
            }
        }
    }

    fun closeAll() {
        _sessions.value.toList().forEach { close(it.id) }
        runSession = null
    }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(appContext, Intent(appContext, TerminalService::class.java))
        } catch (e: RuntimeException) {
            return
        }
    }

    private fun stopService() {
        appContext.stopService(Intent(appContext, TerminalService::class.java))
    }

    fun ensureSession() {
        if (_sessions.value.isEmpty()) newShell()
    }

    fun canRun(fileName: String): Boolean = RunCommand.isRunnable(fileName, customRunners())

    private fun customRunners(): Map<String, CustomRunner> {
        val file = File(workspace, CustomRunners.FILE_NAME)
        if (!file.isFile || file.length() > MAX_RUNNERS_BYTES) return emptyMap()
        return try {
            CustomRunners.parse(file.readText())
        } catch (e: java.io.IOException) {
            emptyMap()
        }
    }

    fun run(file: File): RunStart {
        if (!environment.isReady) return RunStart.NEEDS_ENVIRONMENT
        val guestPath = ShellLauncher.guestPath(workspace, file) ?: return RunStart.OUTSIDE_WORKSPACE
        val plan = RunCommand.plan(file.name, guestPath, customRunners()) ?: return RunStart.UNSUPPORTED
        val script = RunCommand.script(plan, appContext.getString(R.string.terminal_run_done))
        runSession?.let { close(it.id) }
        val title = appContext.getString(R.string.terminal_run_session_name, file.name)
        val session = startSession(title, interactiveSpec())
        runSession = session
        session.send("$script\n")
        return RunStart.STARTED
    }

    fun installPackages(packages: List<String>): Boolean {
        val script = RunCommand.installScript(packages) ?: return false
        if (!environment.isReady) return false
        val session = activeSession() ?: newShell()
        session.send("$script\n")
        return true
    }

    fun activeSession(): TerminalSession? {
        val id = _activeId.value ?: return null
        return _sessions.value.firstOrNull { it.id == id }
    }

    private fun interactiveSpec(): LaunchSpec {
        val paths = environment.paths
        workspace.mkdirs()
        return if (environment.isReady) {
            ShellLauncher.proot(
                paths,
                workspace,
                ShellLauncher.interactiveGuestShell(environment.hasScript),
                columns,
                rows,
            )
        } else {
            paths.home.mkdirs()
            ShellLauncher.native(workspace, paths.home)
        }
    }

    private fun startSession(title: String, spec: LaunchSpec): TerminalSession {
        val localEcho = !(environment.isReady && environment.hasScript)
        val session = TerminalSession(
            id = nextId++,
            title = title,
            spec = spec,
            localEcho = localEcho,
            startFailedFormat = appContext.getString(R.string.terminal_start_failed),
            exitedFormat = appContext.getString(R.string.terminal_exited),
            scope = scope,
        )
        session.resize(columns, rows)
        session.start()
        _sessions.update { it + session }
        _activeId.value = session.id
        return session
    }

    companion object {
        private const val MAX_RUNNERS_BYTES = 64 * 1024L

        @Volatile
        var instance: TerminalManager? = null
            private set
    }
}
