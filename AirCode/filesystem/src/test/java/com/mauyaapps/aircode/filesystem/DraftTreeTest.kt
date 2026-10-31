package com.mauyaapps.aircode.filesystem

import com.mauyaapps.aircode.filesystem.WorkspaceException.Reason
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DraftTreeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun reasonOf(result: Result<*>): Reason =
        (result.exceptionOrNull() as WorkspaceException).reason

    private val project = DraftFolder(
        id = 1,
        name = "app",
        children = listOf(
            DraftFile(2, "main.py", "print(1)\n"),
            DraftFolder(3, "src", listOf(DraftFile(4, "util.py", "x = 1\n"))),
        ),
    )

    @Test
    fun add_кладётУзелВПапкуИОтклоняетПовтор() {
        val roots = DraftTree.add(listOf(project), 1, DraftFile(5, " new.txt ")).getOrThrow()
        assertEquals("new.txt", DraftTree.find(roots, 5)?.name)
        assertEquals(
            Reason.ALREADY_EXISTS,
            reasonOf(DraftTree.add(roots, 1, DraftFile(6, "new.txt"))),
        )
        assertEquals(Reason.INVALID_NAME, reasonOf(DraftTree.add(roots, null, DraftFile(7, "a/b"))))
        assertEquals(Reason.NOT_FOUND, reasonOf(DraftTree.add(roots, 99, DraftFile(8, "x"))))
        assertEquals(Reason.NOT_FOUND, reasonOf(DraftTree.add(roots, 2, DraftFile(9, "x"))))
    }

    @Test
    fun remove_убираетВместеСПотомками() {
        val roots = DraftTree.remove(listOf(project), 3)
        assertNull(DraftTree.find(roots, 3))
        assertNull(DraftTree.find(roots, 4))
        assertEquals("main.py", DraftTree.find(roots, 2)?.name)
    }

    @Test
    fun rename_меняетИмяИПроверяетСоседей() {
        val roots = DraftTree.rename(listOf(project), 2, "app.py").getOrThrow()
        assertEquals("app.py", DraftTree.find(roots, 2)?.name)
        assertEquals(Reason.ALREADY_EXISTS, reasonOf(DraftTree.rename(roots, 2, "src")))
        DraftTree.rename(roots, 2, "app.py").getOrThrow()
    }

    @Test
    fun setText_меняетТекстФайла() {
        val roots = DraftTree.setText(listOf(project), 4, "y = 2\n")
        assertEquals("y = 2\n", (DraftTree.find(roots, 4) as DraftFile).text)
    }

    @Test
    fun flatten_папкиРаньшеФайловИТолькоРаскрытые() {
        val collapsed = DraftTree.flatten(listOf(project), emptySet())
        assertEquals(listOf("app"), collapsed.map { it.node.name })

        val entries = DraftTree.flatten(listOf(project), setOf(1L, 3L))
        assertEquals(listOf("app", "src", "util.py", "main.py"), entries.map { it.node.name })
        assertEquals(listOf(0, 1, 2, 1), entries.map { it.depth })
        assertTrue(entries[0].isExpanded)
    }

    @Test
    fun saveDraft_записываетПапкуСФайлами() = runBlocking {
        val repo = WorkspaceRepository(File(tmp.root, "projects"))
        repo.ensureRoot()
        val saved = repo.saveDraft(repo.root, project).getOrThrow()
        assertEquals("print(1)\n", File(saved, "main.py").readText())
        assertEquals("x = 1\n", File(saved, "src/util.py").readText())
        assertEquals(Reason.ALREADY_EXISTS, reasonOf(repo.saveDraft(repo.root, project)))
    }
}
