package com.mauyaapps.aircode.filesystem

import com.mauyaapps.aircode.filesystem.WorkspaceException.Reason
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var repo: WorkspaceRepository

    @Before
    fun setUp() {
        repo = WorkspaceRepository(File(tmp.root, "projects"))
        runBlocking { repo.ensureRoot() }
    }

    private fun reasonOf(result: Result<*>): Reason =
        (result.exceptionOrNull() as WorkspaceException).reason

    @Test
    fun createFile_создаётФайл() = runBlocking {
        val file = repo.createFile(repo.root, "main.py").getOrThrow()
        assertTrue(file.isFile)
        assertEquals("main.py", file.name)
    }

    @Test
    fun createFile_повторноеИмяОтклоняется() = runBlocking {
        repo.createFile(repo.root, "a.txt").getOrThrow()
        assertEquals(Reason.ALREADY_EXISTS, reasonOf(repo.createFile(repo.root, "a.txt")))
    }

    @Test
    fun createFile_недопустимыеИмена() = runBlocking {
        for (name in listOf("", "   ", ".", "..", "a/b")) {
            assertEquals(Reason.INVALID_NAME, reasonOf(repo.createFile(repo.root, name)))
        }
    }

    @Test
    fun createFile_запрещаетВыходЗаРабочуюПапку() = runBlocking {
        assertEquals(Reason.OUTSIDE_WORKSPACE, reasonOf(repo.createFile(tmp.root, "x.txt")))
    }

    @Test
    fun rename_переименовываетИЗапрещаетСуществующееИмя() = runBlocking {
        val a = repo.createFile(repo.root, "a.txt").getOrThrow()
        repo.createFile(repo.root, "b.txt").getOrThrow()

        assertEquals(Reason.ALREADY_EXISTS, reasonOf(repo.rename(a, "b.txt")))
        val renamed = repo.rename(a, "c.txt").getOrThrow()
        assertEquals("c.txt", renamed.name)
        assertFalse(a.exists())
    }

    @Test
    fun корневаяПапкаЗащищена() = runBlocking {
        assertEquals(Reason.PROTECTED_ROOT, reasonOf(repo.rename(repo.root, "x")))
        assertEquals(Reason.PROTECTED_ROOT, reasonOf(repo.delete(repo.root)))
    }

    @Test
    fun delete_удаляетПапкуСоДержимым() = runBlocking {
        val dir = repo.createFolder(repo.root, "src").getOrThrow()
        repo.createFile(dir, "main.kt").getOrThrow()

        repo.delete(dir).getOrThrow()
        assertFalse(dir.exists())
    }

    @Test
    fun дерево_сортируетПапкиПерваИРаскрываетПоНабору() = runBlocking {
        val dir = repo.createFolder(repo.root, "src").getOrThrow()
        repo.createFile(dir, "inner.txt").getOrThrow()
        repo.createFile(repo.root, "B.txt").getOrThrow()
        repo.createFile(repo.root, "a.txt").getOrThrow()

        val collapsed = repo.loadTree(emptySet())
        assertEquals(listOf("src", "a.txt", "B.txt"), collapsed.map { it.file.name })

        val expanded = repo.loadTree(setOf(dir.path))
        assertEquals(listOf("src", "inner.txt", "a.txt", "B.txt"), expanded.map { it.file.name })
        assertEquals(1, expanded[1].depth)
    }

    @Test
    fun writeText_иReadText_сохраняютТекстВUtf8() = runBlocking {
        val file = repo.createFile(repo.root, "a.txt").getOrThrow()
        repo.writeText(file, "привет\nмир").getOrThrow()
        assertEquals("привет\nмир", repo.readText(file).getOrThrow())
        assertEquals(listOf("a.txt"), repo.root.listFiles()!!.map { it.name })
    }

    @Test
    fun readText_отклоняетДвоичныеИБольшиеФайлы() = runBlocking {
        val binary = repo.createFile(repo.root, "bin.dat").getOrThrow()
        binary.writeBytes(byteArrayOf(1, 0, 2))
        assertEquals(Reason.NOT_TEXT, reasonOf(repo.readText(binary)))

        val invalid = repo.createFile(repo.root, "bad.txt").getOrThrow()
        invalid.writeBytes(byteArrayOf(0xC3.toByte(), 0x28))
        assertEquals(Reason.NOT_TEXT, reasonOf(repo.readText(invalid)))

        val big = repo.createFile(repo.root, "big.txt").getOrThrow()
        big.writeBytes(ByteArray((WorkspaceRepository.MAX_TEXT_BYTES + 1).toInt()) { 'a'.code.toByte() })
        assertEquals(Reason.TOO_LARGE, reasonOf(repo.readText(big)))
    }

    @Test
    fun readText_запрещаетВыходЗаРабочуюПапку() = runBlocking {
        val outside = tmp.newFile("outside.txt")
        assertEquals(Reason.OUTSIDE_WORKSPACE, reasonOf(repo.readText(outside)))
    }
}
