package com.mauyaapps.aircode.crash

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import java.io.File
import kotlin.system.exitProcess

/** Перехватывает необработанные исключения и показывает экран краша. */
object CrashHandler {

    private const val MAX_REPORT_LENGTH = 50_000
    private const val CRASH_PROCESS_SUFFIX = ":crash"

    fun install(context: Context) {
        val app = context.applicationContext
        // В процессе экрана краша обработчик не ставим, чтобы не зациклиться
        if (currentProcessName().endsWith(CRASH_PROCESS_SUFFIX)) return

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val started = runCatching {
                val intent = Intent(app, CrashActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra(CrashActivity.EXTRA_REPORT, buildReport(app, throwable))
                }
                app.startActivity(intent)
            }.isSuccess

            if (started) {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            } else {
                previous?.uncaughtException(thread, throwable)
            }
        }
    }

    /** Отчёт: версия приложения, устройство и стек ошибки. Содержимое файлов и ключи не включаются. */
    private fun buildReport(context: Context, throwable: Throwable): String {
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"

        val header = buildString {
            appendLine("AirCode $versionName")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Устройство: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
        }
        return (header + throwable.stackTraceToString()).take(MAX_REPORT_LENGTH)
    }

    private fun currentProcessName(): String =
        runCatching { File("/proc/self/cmdline").readText().trim('\u0000', ' ') }
            .getOrDefault("")
}
