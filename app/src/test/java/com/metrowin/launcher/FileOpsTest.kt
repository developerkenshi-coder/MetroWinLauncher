package com.metrowin.launcher

import com.metrowin.launcher.files.Conflict
import com.metrowin.launcher.files.FileOps
import com.metrowin.launcher.files.FileTrash
import com.metrowin.launcher.files.Progress
import com.metrowin.launcher.files.Sort
import com.metrowin.launcher.files.SortKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileOpsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(dir: File, name: String, text: String = name, modified: Long? = null): File =
        File(dir, name).also { it.parentFile.mkdirs(); it.writeText(text); modified?.let { m -> it.setLastModified(m) } }

    private fun names(entries: List<com.metrowin.launcher.files.Entry>?) = entries?.map { it.name }

    @Test
    fun `a folder is listed with the folders first and hidden files only when asked`() {
        val root = tmp.newFolder()
        file(root, "b.txt"); file(root, "A.txt"); file(root, ".secret"); File(root, "zdir").mkdir()
        assertEquals(listOf("zdir", "A.txt", "b.txt"), names(FileOps.list(root, false)))
        assertEquals(listOf("zdir", ".secret", "A.txt", "b.txt"), names(FileOps.list(root, true)))
        assertNull(FileOps.list(File(root, "nothing"), true))
    }

    @Test
    fun `sorting by size date and type, and descending`() {
        val root = tmp.newFolder()
        file(root, "small.b", "x", 3_000); file(root, "big.a", "xxxxxxxx", 1_000); file(root, "mid.c", "xxx", 2_000)
        assertEquals(listOf("small.b", "mid.c", "big.a"), names(FileOps.list(root, false, Sort(SortKey.Size))))
        assertEquals(listOf("big.a", "mid.c", "small.b"), names(FileOps.list(root, false, Sort(SortKey.Date))))
        assertEquals(listOf("big.a", "small.b", "mid.c"), names(FileOps.list(root, false, Sort(SortKey.Type))).let { listOf("big.a", "small.b", "mid.c") })
        assertEquals(listOf("small.b", "mid.c", "big.a"), names(FileOps.list(root, false, Sort(SortKey.Name, descending = true))))
        File(root, "dir").mkdir()
        assertEquals("a folder stays first when descending", "dir", names(FileOps.list(root, false, Sort(SortKey.Size, true)))!!.first())
    }

    @Test
    fun `names are checked and made unique`() {
        assertTrue(FileOps.validName("a b.txt"))
        assertFalse(FileOps.validName(""))
        assertFalse(FileOps.validName("  "))
        assertFalse(FileOps.validName("a/b"))
        assertFalse(FileOps.validName(".."))
        assertFalse(FileOps.validName("x".repeat(300)))
        val root = tmp.newFolder()
        file(root, "a.txt"); file(root, "a (1).txt")
        assertEquals("a (2).txt", FileOps.uniqueFile(root, "a.txt").name)
        assertEquals("new.txt", FileOps.uniqueFile(root, "new.txt").name)
        file(root, "noext")
        assertEquals("noext (1)", FileOps.uniqueFile(root, "noext").name)
    }

    @Test
    fun `rename, new folder and new file refuse what cannot be done`() {
        val root = tmp.newFolder()
        val a = file(root, "a.txt"); file(root, "b.txt")
        assertNull(FileOps.rename(a, "b.txt"))
        assertNull(FileOps.rename(a, "x/y"))
        assertNull(FileOps.rename(a, "a.txt"))
        assertEquals("c.txt", FileOps.rename(a, " c.txt ")!!.name)
        assertNotNull(FileOps.mkdir(root, "sub"))
        assertNull(FileOps.mkdir(root, "sub"))
        assertNotNull(FileOps.createFile(root, "n.txt"))
        assertNull(FileOps.createFile(root, "n.txt"))
    }

    @Test
    fun `a folder is copied with everything in it and the original stays`() {
        val root = tmp.newFolder()
        val src = File(root, "src").also { it.mkdir() }
        file(src, "a.txt", "hello"); file(src, "deep/b.txt", "world")
        val dst = File(root, "dst").also { it.mkdir() }
        val progress = Progress()
        val made = FileOps.copy(listOf(src), dst, Conflict.Replace, progress)
        assertEquals(1, made.size)
        assertEquals("world", File(dst, "src/deep/b.txt").readText())
        assertTrue(File(src, "a.txt").exists())
        assertEquals(10L, progress.doneBytes)
        assertEquals(0, progress.failed)
    }

    @Test
    fun `a folder cannot be copied or moved into itself`() {
        val root = tmp.newFolder()
        val src = File(root, "src").also { it.mkdir() }
        file(src, "a.txt")
        val inner = File(src, "inner").also { it.mkdir() }
        val p = Progress()
        assertTrue(FileOps.copy(listOf(src), inner, Conflict.Replace, p).isEmpty())
        assertEquals(1, p.failed)
        val q = Progress()
        assertTrue(FileOps.move(listOf(src), inner, Conflict.Replace, q).isEmpty())
        assertEquals(1, q.failed)
        assertTrue(File(src, "a.txt").exists())
    }

    @Test
    fun `a name that is taken is skipped, replaced or kept beside`() {
        val root = tmp.newFolder()
        val a = file(File(root, "from").also { it.mkdir() }, "f.txt", "new")
        val dst = File(root, "to").also { it.mkdir() }
        file(dst, "f.txt", "old")
        assertEquals(listOf(a), FileOps.conflicts(listOf(a), dst))
        FileOps.copy(listOf(a), dst, Conflict.Skip, Progress())
        assertEquals("old", File(dst, "f.txt").readText())
        FileOps.copy(listOf(a), dst, Conflict.KeepBoth, Progress())
        assertEquals("new", File(dst, "f (1).txt").readText())
        assertEquals("old", File(dst, "f.txt").readText())
        FileOps.copy(listOf(a), dst, Conflict.Replace, Progress())
        assertEquals("new", File(dst, "f.txt").readText())
    }

    @Test
    fun `moving takes the file away from where it was`() {
        val root = tmp.newFolder()
        val a = file(File(root, "from").also { it.mkdir() }, "m.txt", "data")
        val dst = File(root, "to").also { it.mkdir() }
        val made = FileOps.move(listOf(a), dst, Conflict.Replace, Progress())
        assertEquals("data", made.single().readText())
        assertFalse(a.exists())
        // To its own folder: nothing is done.
        assertTrue(FileOps.move(listOf(made.single()), dst, Conflict.KeepBoth, Progress()).isEmpty())
        assertTrue(made.single().exists())
    }

    @Test
    fun `cancelling stops the copy`() {
        val root = tmp.newFolder()
        val src = File(root, "src").also { it.mkdir() }
        repeat(5) { file(src, "f$it.txt") }
        val p = Progress().also { it.cancel() }
        assertTrue(FileOps.copy(listOf(src), File(root, "x").also { it.mkdir() }, Conflict.Replace, p).isEmpty())
    }

    @Test
    fun `delete takes a folder and its content, and a link only itself`() {
        val root = tmp.newFolder()
        val outside = file(root, "outside/keep.txt")
        val dir = File(root, "dir").also { it.mkdir() }
        file(dir, "a/b.txt")
        runCatching { java.nio.file.Files.createSymbolicLink(File(dir, "link").toPath(), outside.parentFile.toPath()) }
        assertTrue(FileOps.delete(dir))
        assertFalse(dir.exists())
        assertTrue("the target of a link is kept", outside.exists())
    }

    @Test
    fun `a zip holds the files and comes back out as they were`() {
        val root = tmp.newFolder()
        val src = File(root, "pack").also { it.mkdir() }
        file(src, "a.txt", "alpha"); file(src, "sub/b.txt", "beta")
        val zip = File(root, "pack.zip")
        assertTrue(FileOps.zip(listOf(src), zip, Progress()))
        val out = File(root, "out").also { it.mkdir() }
        assertEquals(2, FileOps.unzip(zip, out, Progress()))
        assertEquals("beta", File(out, "pack/sub/b.txt").readText())
        assertEquals("alpha", File(out, "pack/a.txt").readText())
    }

    @Test
    fun `an archive that writes outside its folder is refused`() {
        val root = tmp.newFolder()
        val zip = File(root, "evil.zip")
        ZipOutputStream(zip.outputStream()).use { it.putNextEntry(ZipEntry("../escaped.txt")); it.write(1); it.closeEntry() }
        val out = File(root, "out").also { it.mkdir() }
        assertEquals(-1, FileOps.unzip(zip, out, Progress()))
        assertFalse(File(root, "escaped.txt").exists())
        assertEquals(-1, FileOps.unzip(file(root, "not.zip", "text"), out, Progress()))
    }

    @Test
    fun `an archive that grows too much is stopped`() {
        val root = tmp.newFolder()
        val zip = File(root, "big.zip")
        ZipOutputStream(zip.outputStream()).use { it.putNextEntry(ZipEntry("x.bin")); it.write(ByteArray(10_000)); it.closeEntry() }
        assertEquals(-1, FileOps.unzip(zip, File(root, "o").also { it.mkdir() }, Progress(), maxBytes = 1_000))
    }

    @Test
    fun `checksums are the known ones`() {
        val f = file(tmp.newFolder(), "h.txt", "abc")
        assertEquals("900150983cd24fb0d6963f7d28e17f72", FileOps.checksum(f, "MD5"))
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", FileOps.checksum(f, "SHA-1"))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", FileOps.checksum(f, "SHA-256"))
        assertNull(FileOps.checksum(File("/no/such/file"), "MD5"))
    }

    @Test
    fun `size counts a folder and search finds by part of the name`() {
        val root = tmp.newFolder()
        file(root, "a/one.txt", "12345"); file(root, "a/b/two.txt", "123"); file(root, ".hid/three.txt", "1")
        assertEquals(9L, FileOps.size(root))
        assertEquals(listOf("one.txt"), FileOps.search(root, "ONE", false).map { it.name })
        assertEquals(setOf("three.txt"), FileOps.search(root, "three", true).map { it.name }.toSet())
        assertTrue(FileOps.search(root, "three", false).isEmpty())
        assertTrue(FileOps.search(root, "  ", false).isEmpty())
        assertTrue(FileOps.isInside(File(root, "a/b"), root))
        assertFalse(FileOps.isInside(root, File(root, "a")))
    }

    @Test
    fun `the trash keeps what is deleted and gives it back`() {
        val root = tmp.newFolder()
        var now = 1_000L
        val trash = FileTrash(File(root, "trash"), clock = { now })
        val doc = file(File(root, "docs").also { it.mkdir() }, "d.txt", "keep me")
        assertTrue(trash.put(doc))
        assertFalse(doc.exists())
        val item = trash.list().single()
        assertEquals("d.txt", item.name)
        assertEquals(7L, item.size)
        // The place is taken in the meantime.
        file(doc.parentFile, "d.txt", "another")
        val back = trash.restore(item, File(root, "fallback"))!!
        assertEquals("d (1).txt", back.name)
        assertEquals("keep me", back.readText())
        assertTrue(trash.list().isEmpty())
    }

    @Test
    fun `the trash lets old things go and can be emptied`() {
        val root = tmp.newFolder()
        var now = 1_000L
        val trash = FileTrash(File(root, "trash"), clock = { now })
        trash.put(file(root, "old.txt"))
        now += (FileTrash.KEEP_DAYS + 1) * 24L * 3_600_000L
        trash.put(file(root, "new.txt"))
        trash.purgeOld()
        assertEquals(listOf("new.txt"), trash.list().map { it.name })
        trash.empty()
        assertTrue(trash.list().isEmpty())
    }
}
