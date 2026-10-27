package com.mauyaapps.aircode.filesystem

import com.mauyaapps.aircode.filesystem.WorkspaceException.Reason
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Рабочая папка проектов. Все операции выполняются в фоне и не выпускают
 * действия за пределы [root]: пути приводятся к каноническому виду (в том числе
 * раскрываются символические ссылки) и проверяются перед каждой операцией.
 * Ошибки возвращаются как [Result] с [WorkspaceException].
 */
class WorkspaceRepository(rootDir: File) {

    val root: File = rootDir.canonicalFile

    /** Создаёт рабочую папку, если её ещё нет. */
    suspend fun ensureRoot() {
        withContext(Dispatchers.IO) { root.mkdirs() }
    }

    /** Плоский список дерева; раскрыты папки из [expanded] (набор путей). */
    suspend fun loadTree(expanded: Set<String>): List<TreeEntry> =
        withContext(Dispatchers.IO) { FileTree.flatten(root, expanded) }

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

    /**
     * Читает текстовый файл целиком. Файлы больше [MAX_TEXT_BYTES], с нулевыми байтами
     * или не в UTF-8 не открываются.
     */
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

    /** Записывает текст в существующий файл в UTF-8. Запись идёт через временный файл, чтобы не потерять данные при сбое. */
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

    /** Проверяет родителя и имя, возвращает путь нового элемента, которого ещё нет. */
    private fun prepareChild(parent: File, name: String): File {
        val dir = requireInside(parent)
        if (!dir.isDirectory) throw WorkspaceException(Reason.NOT_FOUND)
        val target = requireInside(File(dir, validateName(name)))
        if (target.exists()) throw WorkspaceException(Reason.ALREADY_EXISTS)
        return target
    }

    private fun validateName(name: String): String {
        val trimmed = name.trim()
        val invalid = trimmed.isEmpty() ||
            trimmed == "." ||
            trimmed == ".." ||
            trimmed.any { it == '/' || it == '\u0000' }
        if (invalid) throw WorkspaceException(Reason.INVALID_NAME)
        return trimmed
    }

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
        /** Максимальный размер текстового файла для редактора: 2 МБ. */
        const val MAX_TEXT_BYTES = 2L * 1024 * 1024
    }
}
