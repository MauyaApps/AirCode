package com.mauyaapps.aircode.terminal

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellLauncherTest {

    private val paths = TerminalPaths(File("/data/files"), File("/data/app/lib"))
    private val workspace = File("/data/files/projects")

    @Test
    fun prootCommandBindsWorkspaceAndRunsGuestCommand() {
        val spec = ShellLauncher.proot(paths, workspace, listOf("/bin/sh", "-l"))
        val command = spec.command
        assertEquals("/data/app/lib/libproot.so", command.first())
        assertTrue(command.containsAll(listOf("-0", "--kill-on-exit", "--link2symlink")))
        val rootIndex = command.indexOf("-r")
        assertEquals("/data/files/linux/rootfs", command[rootIndex + 1])
        assertTrue(command.contains("/data/files/projects:/workspace"))
        assertEquals("/workspace", command[command.indexOf("-w") + 1])
        assertEquals(listOf("/bin/sh", "-l"), command.takeLast(2))
        assertEquals("/data/app/lib/libproot-loader.so", spec.environment["PROOT_LOADER"])
        assertEquals("/data/files/linux/tmp", spec.environment["PROOT_TMP_DIR"])
        assertEquals(workspace, spec.workingDir)
    }

    @Test
    fun guestShellUsesPtyWrapperOnlyWhenAvailable() {
        assertEquals("/usr/local/bin/aircode-pty", ShellLauncher.interactiveGuestShell(true).first())
        assertEquals(listOf("/bin/sh", "-l", "-i"), ShellLauncher.interactiveGuestShell(false))
    }

    @Test
    fun nativeShellStartsInWorkspace() {
        val spec = ShellLauncher.native(workspace, File("/data/files/linux/home"))
        assertEquals(listOf("/system/bin/sh", "-i"), spec.command)
        assertEquals(workspace, spec.workingDir)
    }

    @Test
    fun guestPathMapsInsideWorkspaceOnly() {
        val root = createTempDirCompat()
        assertEquals("/workspace/src/a.py", ShellLauncher.guestPath(root, File(root, "src/a.py")))
        assertNull(ShellLauncher.guestPath(root, File(root.parentFile, "other.py")))
        assertNull(ShellLauncher.guestPath(root, File(root, "../x.py")))
    }

    private fun createTempDirCompat(): File =
        java.nio.file.Files.createTempDirectory("ws").toFile().canonicalFile

    @Test
    fun prootPassesTerminalSize() {
        val spec = ShellLauncher.proot(paths, workspace, listOf("/bin/sh"), columns = 52, rows = 19)
        assertTrue(spec.command.contains("AIRCODE_COLS=52"))
        assertTrue(spec.command.contains("AIRCODE_ROWS=19"))
        assertEquals("cols 52 rows 19\n", ShellLauncher.sizeLine(52, 19))
    }
}
