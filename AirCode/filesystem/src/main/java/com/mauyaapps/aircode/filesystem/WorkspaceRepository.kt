package com.mauyaapps.aircode.filesystem

import com.mauyaapps.aircode.filesystem.WorkspaceException.Reason
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WorkspaceRepository(rootDir: File) {

    val root: File = rootDir.canonicalFile

    suspend fun ensureRoot() {
        withContext(Dispatchers.IO) { root.mkdirs() }
    }

    suspend fun loadTree(expanded: Set<String>): List<TreeEntry> =
        withContext(Dispatchers.IO) { FileTree.flatten(root, expanded) }

    suspend fun listFolders(dir: File): Result<List<File>> = io {
        val source = requireInside(dir)
        if (!source.isDirectory) throw WorkspaceException(Reason.NOT_FOUND)
        source.listFiles { child -> child.isDirectory }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    suspend fun createFile(parent: File, name: String): Result<File> = io {
        val target = prepareChild(parent, name)
        if (!target.createNewFile()) throw WorkspaceException(Reason.ALREADY_EXISTS)
        target
    }

    suspend fun createFolder(parent: File, name: String): Result<File> = io {
        val target = prepareChild(parent, name)
        if (!target.mkdir()) throw WorkspaceException(Reason.IO_ERROR)
        target
    }

    suspend fun saveDraft(parent: File, node: DraftNode): Result<File> = io { saveNode(parent, node) }

    suspend fun rename(file: File, newName: String): Result<File> = io {
        val source = requireInside(file)
        if (source == root) throw WorkspaceException(Reason.PROTECTED_ROOT)
        if (!source.exists()) throw WorkspaceException(Reason.NOT_FOUND)

        val name = validateName(newName)
        val target = requireInside(File(source.parentFile, name))
        if (target == source) return@io source
        if (target.exists()) throw WorkspaceException(Reason.ALREADY_EXISTS)
        if (!source.renameTo(target)) throw WorkspaceException(Reason.IO_ERROR)
        target
    }

    suspend fun delete(file: File): Result<Unit> = io {
        val target = requireInside(file)
        if (target == root) throw WorkspaceException(Reason.PROTECTED_ROOT)
        if (!target.exists()) throw WorkspaceException(Reason.NOT_FOUND)
        if (!target.deleteRecursively()) throw WorkspaceException(Reason.IO_ERROR)
    }

    suspend fun readText(file: File): Result<String> = io {
        val source = requireInside(file)
        if (!source.isFile) throw WorkspaceException(Reason.NOT_FOUND)
        if (source.length() > MAX_TEXT_BYTES) throw WorkspaceException(Reason.TOO_LARGE)

        val bytes = source.readBytes()
        if (bytes.contains(0.toByte())) throw WorkspaceException(Reason.NOT_TEXT)

        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            throw WorkspaceException(Reason.NOT_TEXT, e)
        }
    }

    suspend fun writeText(file: File, text: String): Result<Unit> = io {
        val target = requireInside(file)
        if (!target.isFile) throw WorkspaceException(Reason.NOT_FOUND)

        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_TEXT_BYTES) throw WorkspaceException(Reason.TOO_LARGE)

        val temp = File.createTempFile(".aircode", ".tmp", target.parentFile)
        try {
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) throw WorkspaceException(Reason.IO_ERROR)
        } finally {
            temp.delete()
        }
    }

    private fun saveNode(parent: File, node: DraftNode): File = when (node) {
        is DraftFolder -> {
            val dir = prepareChild(parent, node.name)
            if (!dir.mkdir()) throw WorkspaceException(Reason.IO_ERROR)
            node.children.forEach { saveNode(dir, it) }
            dir
        }

        is DraftFile -> {
            val file = prepareChild(parent, node.name)
            val bytes = node.text.toByteArray(Charsets.UTF_8)
            if (bytes.size > MAX_TEXT_BYTES) throw WorkspaceException(Reason.TOO_LARGE)
            file.writeBytes(bytes)
            file
        }
    }

    private fun prepareChild(parent: File, name: String): File {
        val dir = requireInside(parent)
        if (!dir.isDirectory) throw WorkspaceException(Reason.NOT_FOUND)
        val target = requireInside(File(dir, validateName(name)))
        if (target.exists()) throw WorkspaceException(Reason.ALREADY_EXISTS)
        return target
    }

    private fun validateName(name: String): String = NameRules.validate(name)

    private fun requireInside(file: File): File {
        val canonical = file.canonicalFile
        val inside = canonical == root || canonical.path.startsWith(root.path + File.separator)
        if (!inside) throw WorkspaceException(Reason.OUTSIDE_WORKSPACE)
        return canonical
    }

    private suspend fun <T> io(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: WorkspaceException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(WorkspaceException(Reason.IO_ERROR, e))
        } catch (e: SecurityException) {
            Result.failure(WorkspaceException(Reason.IO_ERROR, e))
        }
    }

    companion object {
        const val MAX_TEXT_BYTES = 2L * 1024 * 1024
    }
}
