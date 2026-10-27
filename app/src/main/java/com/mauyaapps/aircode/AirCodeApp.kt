package com.mauyaapps.aircode

import android.app.Application
import com.mauyaapps.aircode.crash.CrashHandler
import com.mauyaapps.aircode.filesystem.WorkspaceRepository
import java.io.File

/** Точка входа приложения: подключает перехват необработанных исключений и общую рабочую папку. */
class AirCodeApp : Application() {

    /** Рабочая папка проектов, общая для дерева файлов и редактора. */
    val workspace: WorkspaceRepository by lazy {
        WorkspaceRepository(File(filesDir, WORKSPACE_DIR))
    }

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
    }

    private companion object {
        const val WORKSPACE_DIR = "projects"
    }
}
