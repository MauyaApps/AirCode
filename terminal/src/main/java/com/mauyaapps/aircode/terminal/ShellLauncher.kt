package com.mauyaapps.aircode.terminal

import java.io.File

enum class ShellMode { PROOT, NATIVE }

class TerminalPaths(filesDir: File, val nativeLibDir: File) {
    val base = File(filesDir, "linux")
    val rootfs = File(base, "rootfs")
    val tmp = File(base, "tmp")
    val home = File(base, "home")
    val archive = File(base, "rootfs.tar.gz")
    val installedMarker = File(base, ".installed")
    val prootBinary = File(nativeLibDir, "libproot.so")
    val prootLoader = File(nativeLibDir, "libproot-loader.so")
    val ptyWrapper = File(rootfs, "usr/local/bin/aircode-pty")
    val ptyInner = File(rootfs, "usr/local/bin/aircode-inner")
    val sizeFile = File(rootfs, "tmp/aircode-size")
    val scriptBinary = File(rootfs, "usr/bin/script")
}

data class LaunchSpec(
    val command: List<String>,
    val environment: Map<String, String>,
    val workingDir: File,
)

object ShellLauncher {

    const val GUEST_WORKSPACE = "/workspace"
    const val GUEST_PATH = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
    const val DEFAULT_COLUMNS = 80
    const val DEFAULT_ROWS = 24

    fun proot(
        paths: TerminalPaths,
        workspace: File,
        guestCommand: List<String>,
        columns: Int = DEFAULT_COLUMNS,
        rows: Int = DEFAULT_ROWS,
    ): LaunchSpec {
        val command = buildList {
            add(paths.prootBinary.path)
            add("-0")
            add("--link2symlink")
            add("--kill-on-exit")
            add("-r")
            add(paths.rootfs.path)
            add("-b")
            add("/dev")
            add("-b")
            add("/proc")
            add("-b")
            add("/sys")
            add("-b")
            add("${workspace.path}:$GUEST_WORKSPACE")
            add("-w")
            add(GUEST_WORKSPACE)
            add("/usr/bin/env")
            add("-i")
            add("HOME=/root")
            add("USER=root")
            add("TERM=xterm-256color")
            add("LANG=C.UTF-8")
            add("PATH=$GUEST_PATH")
            add("AIRCODE_COLS=$columns")
            add("AIRCODE_ROWS=$rows")
            addAll(guestCommand)
        }
        val environment = mapOf(
            "PROOT_LOADER" to paths.prootLoader.path,
            "PROOT_TMP_DIR" to paths.tmp.path,
            "PROOT_NO_SECCOMP" to "1",
        )
        return LaunchSpec(command, environment, workspace)
    }

    fun interactiveGuestShell(usePty: Boolean): List<String> =
        if (usePty) listOf("/usr/local/bin/aircode-pty", "/bin/sh", "-l") else listOf("/bin/sh", "-l", "-i")

    fun sizeLine(columns: Int, rows: Int): String = "cols $columns rows $rows\n"

    fun native(workspace: File, home: File): LaunchSpec = LaunchSpec(
        command = listOf("/system/bin/sh", "-i"),
        environment = mapOf("HOME" to home.path, "TERM" to "dumb"),
        workingDir = workspace,
    )

    fun guestPath(workspace: File, file: File): String? {
        val root = workspace.canonicalFile
        val target = file.canonicalFile
        if (!target.path.startsWith(root.path + File.separator)) return null
        val relative = target.relativeTo(root).path.replace(File.separatorChar, '/')
        return "$GUEST_WORKSPACE/$relative"
    }
}
