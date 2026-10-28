package com.mauyaapps.aircode

import android.app.Application
import com.mauyaapps.aircode.crash.CrashHandler
import com.mauyaapps.aircode.filesystem.WorkspaceRepository
import java.io.File

class AirCodeApp : Application() {

    val workspace: WorkspaceRepository by lazy {
        WorkspaceRepository(File(filesDir, WORKSPACE_DIR))
    }

    val drafts = DraftStore()

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
    }

    private companion object {
        const val WORKSPACE_DIR = "projects"
    }
}
