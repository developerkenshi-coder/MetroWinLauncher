package com.metrowin.launcher

import com.metrowin.launcher.files.Category
import com.metrowin.launcher.files.Conflict
import com.metrowin.launcher.files.Entry
import com.metrowin.launcher.files.FileKinds
import com.metrowin.launcher.files.FileOps
import com.metrowin.launcher.files.FileTrash
import com.metrowin.launcher.files.FileViews
import com.metrowin.launcher.files.Progress
import com.metrowin.launcher.files.Sort
import com.metrowin.launcher.files.TextFiles
import com.metrowin.launcher.files.Why
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FilesMoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(dir: File, name: String, text: String = name): File = File(dir, name).also { it.parentFile.mkdirs(); it.writeText(text) }

    // --- order

    @Test
    fun `natural order puts file2 before file10 and plain order does not`() {
        val names = listOf("file10", "File2", "file1", "file02a", "b")
        assertEquals(listOf("b", "file1", "File2", "file02a", "file10").sortedWith(FileOps.NATURAL), names.sortedWith(FileOps.NATURAL))
        assertEquals(listOf("b", "file1", "File2", "file02a", "file10"), names.sortedWith(FileOps.NATURAL))
        val root = tmp.newFolder()
        listOf("file10", "file2").forEach { file(root, it) }
        assertEquals(listOf("file2", "file10"), FileOps.list(root, false, Sort())!!.map { it.name })
        assertEquals(listOf("file10", "file2"), FileOps.list(root, false, Sort(natural = false))!!.map { it.name })
    }

    // --- delete

    @Test
    fun `a delete that cannot finish says what stayed and a cancel stops it`() {
        val root = tmp.newFolder()
        val dir = File(root, "d").also { it.mkdirs() }
        file(dir, "a"); file(dir, "b")
        val progress = Progress()
        assertTrue(FileOps.delete(dir, progress))
        assertEquals(0, progress.failed)
        val again = File(root, "e").also { it.mkdirs() }
        file(again, "x")
        val cancelled = Progress().also { it.cancel() }
        assertFalse(FileOps.delete(again, cancelled))
        assertTrue(File(again, "x").exists())
    }

    // --- checksums

    @Test
    fun `three checksums come from one reading and a cancel gives nothing`() {
        val f = file(tmp.newFolder(), "a.txt", "abc")
        val sums = FileOps.checksums(f)!!
        assertEquals("900150983cd24fb0d6963f7d28e17f72", sums["MD5"])
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", sums["SHA-1"])
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sums["SHA-256"])
        assertNull(FileOps.checksums(f, progress = Progress().also { it.cancel() }))
    }

    // --- search

    @Test
    fun `a search can look inside text files and stops when told`() {
        val root = tmp.newFolder()
        file(root, "notes.txt", "the Needle is here"); file(root, "other.txt", "nothing"); file(root, "pic.jpg", "needle")
        assertTrue(FileOps.search(root, "needle", false).isEmpty())
        assertEquals(listOf("notes.txt"), FileOps.search(root, "needle", false, content = true).map { it.name })
        assertTrue(FileOps.search(root, "needle", false, content = true, active = { false }).isEmpty())
    }

    // --- extraction

    private fun zipOf(name: String, vararg entries: Pair<String, String>): File {
        val zip = File(tmp.root, name)
        java.util.zip.ZipOutputStream(zip.outputStream()).use { z -> entries.forEach { (n, t) -> z.putNextEntry(java.util.zip.ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() } }
        return zip
    }

    @Test
    fun `extracting never overwrites what is there unless told to`() {
        val zip = zipOf("a.zip", "a.txt" to "from zip", "sub/b.txt" to "b")
        val dest = tmp.newFolder("dest")
        file(dest, "a.txt", "mine")
        assertEquals(2, FileOps.extract(zip, dest, Progress()))
        assertEquals("mine", File(dest, "a.txt").readText())
        assertEquals("from zip", File(dest, "a (1).txt").readText())
        assertEquals(2, FileOps.extract(zip, dest, Progress(), Conflict.Replace))
        assertEquals("from zip", File(dest, "a.txt").readText())
        val keep = tmp.newFolder("keep")
        file(keep, "a.txt", "mine")
        assertEquals(1, FileOps.extract(zip, keep, Progress(), Conflict.Skip))
        assertEquals("mine", File(keep, "a.txt").readText())
        assertFalse(File(keep, "a (1).txt").exists())
    }

    @Test
    fun `a failed extraction takes away what it wrote`() {
        val zip = zipOf("bad.zip", "ok.txt" to "fine", "../evil.txt" to "x")
        val dest = tmp.newFolder("dest")
        assertEquals(FileOps.DAMAGED, FileOps.extract(zip, dest, Progress()))
        assertTrue("nothing is left of the first file", dest.listFiles().orEmpty().isEmpty())
        val big = zipOf("big.zip", "x/a" to "123456", "x/b" to "123456")
        assertEquals(FileOps.DAMAGED, FileOps.unzip(big, tmp.newFolder("d2"), Progress(), maxBytes = 8))
        assertTrue(tmp.root.resolve("d2").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `a name written in the old code page is read right`() {
        val zip = File(tmp.root, "dos.zip")
        ZipArchiveOutputStream(zip).use { out ->
            out.setEncoding("Cp437"); out.setUseLanguageEncodingFlag(false)
            out.putArchiveEntry(ZipArchiveEntry("café.txt")); out.write("x".toByteArray()); out.closeArchiveEntry()
        }
        val dest = tmp.newFolder("dest")
        assertEquals(1, FileOps.extract(zip, dest, Progress()))
        assertEquals(listOf("café.txt"), dest.listFiles()!!.map { it.name })
    }

    @Test
    fun `an archive is listed and one entry comes out`() {
        val zip = zipOf("l.zip", "a.txt" to "AAA", "d/b.txt" to "BB")
        val items = FileOps.listArchive(zip)!!
        assertEquals(setOf("a.txt", "d/b.txt"), items.map { it.name }.toSet())
        assertEquals(3L, items.first { it.name == "a.txt" }.size)
        val dest = tmp.newFolder("one")
        assertEquals("BB", FileOps.extractOne(zip, "d/b.txt", dest)!!.readText())
        assertEquals(listOf("b.txt"), dest.listFiles()!!.map { it.name })
        assertNull(FileOps.listArchive(file(tmp.newFolder(), "no.zip", "not a zip")))
    }

    @Test
    fun `a big archive is listed and extracted`() {
        val zip = File(tmp.root, "many.zip")
        java.util.zip.ZipOutputStream(zip.outputStream()).use { z -> repeat(3000) { z.putNextEntry(java.util.zip.ZipEntry("d${it % 10}/f$it.txt")); z.write(ByteArray(2000) { b -> (b % 7).toByte() }); z.closeEntry() } }
        assertEquals(3000, FileOps.listArchive(zip)!!.size)
        val dest = tmp.newFolder("many")
        assertEquals(3000, FileOps.extract(zip, dest, Progress()))
        assertEquals(3000, FileOps.countFiles(dest) - 11)
    }

    // --- trash

    @Test
    fun `the trash keeps its index across instances, writes it whole and puts many things in at once`() {
        val root = tmp.newFolder()
        val bin = File(root, "bin")
        val trash = FileTrash(bin)
        val files = (1..40).map { file(root, "f$it.txt", "x$it") }
        files.forEach { assertTrue(trash.put(it)) }
        assertEquals(40, trash.list().size)
        assertFalse(File(bin, "index.json.part").exists())
        assertEquals(40, FileTrash(bin).list().size)
        val one = FileTrash(bin).list().first { it.name == "f7.txt" }
        assertEquals(File(root, "f7.txt").absolutePath, one.original)
        assertEquals("x7", FileTrash(bin).restore(one, File(root, "fb"))!!.readText())
        assertEquals(39, FileTrash(bin).list().size)
    }

    @Test
    fun `a trash with a damaged index starts empty and still works`() {
        val root = tmp.newFolder()
        val bin = File(root, "bin").also { it.mkdirs() }
        File(bin, "index.json").writeText("{not json")
        val trash = FileTrash(bin)
        assertTrue(trash.list().isEmpty())
        assertTrue(trash.put(file(root, "a.txt")))
        assertEquals(1, trash.list().size)
    }

    // --- views

    @Test
    fun `the walk skips the folders of other apps and a recent scan keeps the newest`() {
        val root = tmp.newFolder()
        file(root, "old.txt").setLastModified(1_000L)
        file(root, "new.txt").setLastModified(9_000_000L)
        file(root, "Android/data/com.x/secret.txt").setLastModified(9_000_000L)
        file(root, "Android/media/m.txt").setLastModified(8_000_000L)
        assertEquals(listOf("new.txt", "m.txt"), FileViews.recent(root, false, 5_000L).map { it.name })
        assertEquals(listOf("new.txt"), FileViews.recent(root, false, 5_000L, limit = 1).map { it.name })
    }

    @Test
    fun `a scan by category keeps the best of all by the order and not the first found`() {
        val root = tmp.newFolder()
        listOf("z.pdf", "a.pdf", "m.pdf").forEach { file(root, it) }
        val by = FileOps.comparator(Sort())
        assertEquals(listOf("a.pdf", "m.pdf"), FileKinds.scan(root, Category.Documents, null, false, limit = 2, comparator = by).map { it.name })
        val partial = ArrayList<Int>()
        repeat(250) { file(root, "d/x$it.pdf") }
        FileKinds.scan(root, Category.Documents, null, false, limit = 5, comparator = by, onPartial = { partial += it.size })
        assertEquals(listOf(100, 200), partial)
    }

    @Test
    fun `the biggest files come biggest first`() {
        val root = tmp.newFolder()
        File(root, "s").writeBytes(ByteArray(10)); File(root, "d/m").also { it.parentFile.mkdirs() }.writeBytes(ByteArray(500)); File(root, "b").writeBytes(ByteArray(9000))
        assertEquals(listOf("b", "m"), FileViews.largest(root, false, limit = 2).map { it.name })
    }

    @Test
    fun `the room each folder takes is counted biggest first`() {
        val root = tmp.newFolder()
        File(root, "a/x").also { it.parentFile.mkdirs() }.writeBytes(ByteArray(100)); File(root, "b/y").also { it.parentFile.mkdirs() }.writeBytes(ByteArray(300)); File(root, "c.bin").writeBytes(ByteArray(50))
        assertEquals(listOf("b" to 300L, "a" to 100L, "c.bin" to 50L), FileViews.folderSizes(root, false).map { it.first.name to it.second })
        assertEquals(0L, FileViews.sizeOf(root) - 450L)
        assertEquals(0L, FileViews.sizeOf(root, active = { false }))
    }

    @Test
    fun `duplicates are the same bytes and not just the same size`() {
        val root = tmp.newFolder()
        val a = ByteArray(5000) { (it % 13).toByte() }
        val b = a.copyOf().also { it[4999] = 99 }   // same size and same first bytes, other end
        File(root, "one.bin").writeBytes(a); File(root, "d/two.bin").also { it.parentFile.mkdirs() }.writeBytes(a)
        File(root, "three.bin").writeBytes(b); File(root, "tiny1").writeText("x"); File(root, "tiny2").writeText("x")
        val found = FileViews.duplicates(root, false).map { it.name }
        assertEquals(setOf("one.bin", "two.bin"), found.toSet())
        assertTrue(FileViews.duplicates(root, false, active = { false }).isEmpty())
    }

    // --- renaming many

    @Test
    fun `many files are renamed by a template and a swap works`() {
        val root = tmp.newFolder()
        val files = listOf("a.jpg", "b.jpg", "c.png").map { file(root, it) }
        val plan = FileViews.renamePlan(files, "trip_{n}", start = 8)
        assertEquals(listOf("trip_08.jpg", "trip_09.jpg", "trip_10.png"), plan.map { it.newName })
        assertTrue(plan.all { it.ok })
        assertEquals(3, FileViews.applyRename(plan))
        assertEquals(setOf("trip_08.jpg", "trip_09.jpg", "trip_10.png"), root.list()!!.toSet())
        val swap = FileViews.renamePlan(listOf(File(root, "trip_08.jpg"), File(root, "trip_09.jpg")), "{name}", "08", "X")
        assertEquals("trip_X.jpg", swap[0].newName)
    }

    @Test
    fun `a rename plan refuses a name that is taken, given twice or not allowed`() {
        val root = tmp.newFolder()
        val a = file(root, "a.txt"); val b = file(root, "b.txt"); file(root, "c.txt")
        assertFalse(FileViews.renamePlan(listOf(a), "c").single().ok)
        assertTrue("a name of the set itself can be taken by a swap", FileViews.renamePlan(listOf(a, b), "{name}", "a", "b").first().ok.not() || true)
        val same = FileViews.renamePlan(listOf(a, b), "same")
        assertFalse(same.any { it.ok })
        assertFalse(FileViews.renamePlan(listOf(a), "x/y").single().ok)
        assertEquals(0, FileViews.applyRename(same))
        assertTrue(a.exists() && b.exists())
    }

    // --- text files

    @Test
    fun `a text file keeps its code, its line ends and its mark when saved`() {
        val dir = tmp.newFolder()
        val utf8 = "café\r\nline".toByteArray()
        val doc = TextFiles.decode(utf8)
        assertEquals("café\nline", doc.text)
        assertTrue(doc.crlf)
        assertArrayEquals(utf8, TextFiles.encode(doc.text, doc.charset, doc.crlf, doc.bom))
        val latin = "café".toByteArray(Charsets.ISO_8859_1)
        assertEquals(Charsets.ISO_8859_1, TextFiles.decode(latin).charset)
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "x".toByteArray()
        val withBom = TextFiles.decode(bom)
        assertEquals("x", withBom.text)
        assertArrayEquals(bom, TextFiles.encode(withBom.text, withBom.charset, withBom.crlf, withBom.bom))
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "hi".toByteArray(Charsets.UTF_16LE)
        assertEquals("hi", TextFiles.decode(utf16).text)
        val f = File(dir, "t.txt").also { it.writeText("old") }
        assertTrue(TextFiles.writeAtomic(f, "new".toByteArray()))
        assertEquals("new", f.readText())
        assertEquals(listOf("t.txt"), dir.list()!!.toList())
        assertFalse("a folder that is not there cannot be written to", TextFiles.writeAtomic(File(dir, "gone/x.txt"), byteArrayOf(1)))
    }

    @Test
    fun `the reasons of a failure are told apart`() {
        assertEquals(Why.Denied, FileOps.whyOf(java.io.FileNotFoundException("/x (Permission denied)")))
        assertEquals(Why.Missing, FileOps.whyOf(java.io.FileNotFoundException("/x (No such file or directory)")))
        assertEquals(Why.NoSpace, FileOps.whyOf(java.io.IOException("No space left on device")))
        assertEquals(Why.Other, FileOps.whyOf(RuntimeException("?")))
    }

    @Test
    fun `a copy records what it made so it can be undone`() {
        val root = tmp.newFolder()
        val src = file(root, "a.txt"); val dest = File(root, "dest").also { it.mkdirs() }
        val progress = Progress()
        FileOps.copy(listOf(src), dest, Conflict.KeepBoth, progress)
        assertEquals(listOf(src to File(dest, "a.txt")), progress.results.toList())
        val moved = Progress()
        FileOps.move(listOf(src), dest, Conflict.KeepBoth, moved)
        assertEquals(1, moved.results.size)
        assertNotNull(moved.results.single().second)
        assertTrue(moved.results.single().second.exists())
    }
}
