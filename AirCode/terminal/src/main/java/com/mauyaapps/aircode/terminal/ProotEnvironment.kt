package com.mauyaapps.aircode.terminal

import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed interface EnvironmentState {
    data object BinariesMissing : EnvironmentState
    data object NotInstalled : EnvironmentState
    data class Installing(@StringRes val step: Int, val percent: Int? = null) : EnvironmentState
    data object Ready : EnvironmentState
    data class Failed(@StringRes val reason: Int, val detail: String? = null) : EnvironmentState
}

class ProotEnvironment(
    private val context: Context,
    private val workspace: File,
) {
    val paths = TerminalPaths(context.filesDir, File(context.applicationInfo.nativeLibraryDir))

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<EnvironmentState> = _state.asStateFlow()

    val isReady: Boolean get() = _state.value == EnvironmentState.Ready

    val hasScript: Boolean get() = paths.scriptBinary.exists()

    suspend fun install() {
        val current = _state.value
        if (current is EnvironmentState.Installing || current == EnvironmentState.BinariesMissing) return
        _state.value = EnvironmentState.Installing(R.string.install_step_listing)
        try {
            withContext(Dispatchers.IO) { performInstall() }
            _state.value = EnvironmentState.Ready
        } catch (e: InstallException) {
            paths.installedMarker.delete()
            _state.value = EnvironmentState.Failed(e.reason, e.message)
        } catch (e: IOException) {
            paths.installedMarker.delete()
            _state.value = EnvironmentState.Failed(R.string.install_failed_network, e.message)
        }
    }

    suspend fun reset() {
        if (_state.value is EnvironmentState.Installing) return
        withContext(Dispatchers.IO) {
            paths.rootfs.deleteRecursively()
            paths.installedMarker.delete()
            paths.archive.delete()
        }
        _state.value = initialState()
    }

    private fun initialState(): EnvironmentState = when {
        !paths.prootBinary.exists() || !paths.prootLoader.exists() -> EnvironmentState.BinariesMissing
        paths.installedMarker.exists() && paths.rootfs.isDirectory -> EnvironmentState.Ready
        else -> EnvironmentState.NotInstalled
    }

    private fun performInstall() {
        val arch = AlpineRelease.archFor(Build.SUPPORTED_ABIS)
            ?: throw InstallException(R.string.install_failed_arch, Build.SUPPORTED_ABIS.joinToString())
        paths.base.mkdirs()
        paths.tmp.mkdirs()
        paths.home.mkdirs()

        val listing = download(AlpineRelease.listingUrl(arch)).toString(Charsets.UTF_8)
        val release = AlpineRelease.parse(listing)
            ?: throw InstallException(R.string.install_failed_listing, null)

        _state.value = EnvironmentState.Installing(R.string.install_step_download, 0)
        val archiveDigest = downloadToFile(AlpineRelease.fileUrl(arch, release), paths.archive)
        if (!archiveDigest.equals(release.sha256, ignoreCase = true)) {
            paths.archive.delete()
            throw InstallException(R.string.install_failed_checksum, null)
        }

        _state.value = EnvironmentState.Installing(R.string.install_step_extract)
        paths.rootfs.deleteRecursively()
        paths.rootfs.mkdirs()
        runTool(listOf(TAR, "-xzf", paths.archive.path, "-C", paths.rootfs.path), R.string.install_failed_extract)
        paths.archive.delete()

        _state.value = EnvironmentState.Installing(R.string.install_step_configure)
        configureGuest()

        _state.value = EnvironmentState.Installing(R.string.install_step_packages)
        val packagesOutput = installBasePackages()
        if (!paths.scriptBinary.exists()) {
            throw InstallException(R.string.install_failed_packages, packagesOutput.takeLast(DETAIL_LIMIT))
        }

        paths.installedMarker.writeText(release.file)
    }

    private fun configureGuest() {
        File(paths.rootfs, "etc/resolv.conf").writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
        File(paths.rootfs, "tmp").mkdirs()
        File(paths.rootfs, "workspace").mkdirs()
        copyAsset("aircode-pty.sh", paths.ptyWrapper, executable = true)
        copyAsset("aircode-inner.sh", paths.ptyInner, executable = true)
        paths.sizeFile.writeText(ShellLauncher.sizeLine(ShellLauncher.DEFAULT_COLUMNS, ShellLauncher.DEFAULT_ROWS))
        copyAsset("aircode-profile.sh", File(paths.rootfs, "etc/profile.d/aircode.sh"), executable = false)
    }

    private fun installBasePackages(): String {
        val spec = ShellLauncher.proot(
            paths,
            workspace,
            listOf("/bin/sh", "-c", "apk add --no-progress util-linux-misc"),
        )
        try {
            val process = ProcessBuilder(spec.command)
                .directory(spec.workingDir)
                .redirectErrorStream(true)
                .apply { environment().putAll(spec.environment) }
                .start()
            val output = process.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            process.waitFor()
            return output
        } catch (e: IOException) {
            return e.message.orEmpty()
        }
    }

    private fun copyAsset(name: String, target: File, executable: Boolean) {
        target.parentFile?.mkdirs()
        context.assets.open(name).use { input -> target.outputStream().use { input.copyTo(it) } }
        target.setReadable(true, false)
        target.setExecutable(executable, false)
    }

    private fun runTool(command: List<String>, @StringRes failure: Int) {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        if (process.waitFor() != 0) throw InstallException(failure, output.takeLast(DETAIL_LIMIT))
    }

    private fun download(url: String): ByteArray {
        val connection = open(url)
        try {
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadToFile(url: String, target: File): String {
        val connection = open(url)
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val total = connection.contentLengthLong
            var received = 0L
            var lastPercent = -1
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val chunk = ByteArray(DOWNLOAD_CHUNK)
                    while (true) {
                        val count = input.read(chunk)
                        if (count < 0) break
                        output.write(chunk, 0, count)
                        digest.update(chunk, 0, count)
                        received += count
                        if (total > 0) {
                            val percent = (received * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _state.value = EnvironmentState.Installing(R.string.install_step_download, percent)
                            }
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = true
        if (connection.responseCode !in 200..299) {
            connection.disconnect()
            throw InstallException(R.string.install_failed_network, "HTTP ${connection.responseCode}")
        }
        return connection
    }

    private class InstallException(@StringRes val reason: Int, detail: String?) : Exception(detail)

    private companion object {
        const val TAR = "/system/bin/tar"
        const val TIMEOUT_MS = 20_000
        const val DOWNLOAD_CHUNK = 64 * 1024
        const val DETAIL_LIMIT = 400
    }
}
