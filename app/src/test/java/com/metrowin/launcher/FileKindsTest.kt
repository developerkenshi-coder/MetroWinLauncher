package com.metrowin.launcher

import com.metrowin.launcher.files.Category
import com.metrowin.launcher.files.DocKind
import com.metrowin.launcher.files.Entry
import com.metrowin.launcher.files.FileKinds
import com.metrowin.launcher.files.FileOps
import com.metrowin.launcher.files.Sort
import com.metrowin.launcher.files.SortKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileKindsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }
    private fun text(s: String) = s.toByteArray(Charsets.ISO_8859_1)
    private val ole = bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)
    private fun zipWith(name: String) = text("PK\u0003\u0004") + ByteArray(26) + text(name) + ByteArray(40)
    private fun kind(name: String, header: ByteArray? = null) = FileKinds.kindOf(name) { header }

    @Test
    fun theExtensionTellsTheKind() {
        assertEquals(Category.Documents to DocKind.Pdf, kind("a.PDF").let { it.category to it.doc })
        assertEquals(DocKind.Word, kind("a.docx").doc)
        assertEquals(DocKind.Excel, kind("a.xls").doc)
        assertEquals(DocKind.PowerPoint, kind("a.pptx").doc)
        assertEquals(DocKind.Text, kind("a.txt").doc)
        assertEquals(DocKind.Ebook, kind("a.epub").doc)
        assertEquals(DocKind.Other, kind("a.odt").doc)
        assertEquals(Category.Photos, kind("a.JPG").category)
        assertEquals(Category.Videos, kind("a.mkv").category)
        assertEquals(Category.Audio, kind("a.opus").category)
        assertEquals(Category.Apk, kind("a.apk").category)
        assertEquals(Category.Other, kind("a.zip").category)
    }

    @Test
    fun theExtensionIsNotAskedTwice() {
        var read = false
        FileKinds.kindOf("a.pdf") { read = true; null }
        assertFalse("a known extension does not open the file", read)
        assertTrue(FileKinds.needsHeader("noext"))
        assertTrue(FileKinds.needsHeader("a.weird"))
        assertFalse(FileKinds.needsHeader("a.pdf"))
    }

    @Test
    fun theHeaderTellsWhenTheExtensionDoesNot() {
        assertEquals(DocKind.Pdf, kind("report", text("%PDF-1.7")).doc)
        assertEquals(DocKind.Pdf, kind("report.bin", text("%PDF-1.4")).doc)
        assertEquals(DocKind.Word, kind("noext", ole + "W\u0000o\u0000r\u0000d\u0000D\u0000o\u0000c\u0000u\u0000m\u0000e\u0000n\u0000t\u0000".toByteArray(Charsets.ISO_8859_1)).doc)
        assertEquals(DocKind.Excel, kind("noext", ole + "W\u0000o\u0000r\u0000k\u0000b\u0000o\u0000o\u0000k\u0000".toByteArray(Charsets.ISO_8859_1)).doc)
        assertEquals(DocKind.PowerPoint, kind("noext", ole + "PowerPoint Document".toByteArray(Charsets.UTF_16LE)).doc)
        assertEquals("an old file of unknown kind is still a document", DocKind.Other, kind("noext", ole).doc)
        assertEquals(DocKind.Word, kind("x", zipWith("word/document.xml")).doc)
        assertEquals(DocKind.Excel, kind("x", zipWith("xl/workbook.xml")).doc)
        assertEquals(DocKind.PowerPoint, kind("x", zipWith("ppt/slides/slide1.xml")).doc)
        assertEquals(DocKind.Ebook, kind("x", text("PK\u0003\u0004") + ByteArray(26) + text("mimetypeapplication/epub+zip")).doc)
        assertEquals(Category.Apk, kind("x", zipWith("AndroidManifest.xml")).category)
        assertEquals("a plain zip is no document", Category.Other, kind("x", zipWith("readme.txt")).category)
        assertEquals(Category.Photos, kind("x", bytes(0xFF, 0xD8, 0xFF, 0xE0)).category)
        assertEquals(Category.Photos, kind("x", bytes(0x89, 0x50, 0x4E, 0x47)).category)
        assertEquals(Category.Photos, kind("x", text("RIFF") + ByteArray(4) + text("WEBP")).category)
        assertEquals(Category.Audio, kind("x", text("ID3")).category)
        assertEquals(Category.Audio, kind("x", text("RIFF") + ByteArray(4) + text("WAVE")).category)
        assertEquals(Category.Audio, kind("x", ByteArray(4) + text("ftypM4A ")).category)
        assertEquals(Category.Videos, kind("x", ByteArray(4) + text("ftypisom")).category)
        assertEquals(Category.Photos, kind("x", ByteArray(4) + text("ftypheic")).category)
        assertEquals(Category.Videos, kind("x", bytes(0x1A, 0x45, 0xDF, 0xA3)).category)
        assertEquals(Category.Other, kind("x", text("just some words")).category)
        assertNull(FileKinds.byHeader(ByteArray(0)))
    }

    @Test
    fun theScanFindsFilesByKindAndStopsAtTheLimit() {
        val root = tmp.newFolder("root")
        File(root, "a.pdf").writeBytes(text("%PDF"))
        File(root, "noext").writeBytes(text("%PDF-1.5"))
        File(root, "sub").mkdirs(); File(root, "sub/b.docx").writeText("x")
        File(root, "sub/c.jpg").writeText("x")
        File(root, ".hidden").mkdirs(); File(root, ".hidden/d.pdf").writeText("x")
        assertEquals(setOf("a.pdf", "noext"), FileKinds.scan(root, Category.Documents, DocKind.Pdf, false).map { it.name }.toSet())
        assertEquals(setOf("a.pdf", "noext", "b.docx"), FileKinds.scan(root, Category.Documents, null, false).map { it.name }.toSet())
        assertEquals("hidden folders are shown only when asked", 3, FileKinds.scan(root, Category.Documents, DocKind.Pdf, true).size)
        assertEquals(1, FileKinds.scan(root, Category.Documents, null, false, limit = 1).size)
        assertTrue("a stopped scan finds nothing more", FileKinds.scan(root, Category.Documents, null, false, active = { false }).isEmpty())
    }

    @Test
    fun hiddenFoldersAndFilesAreListedOnlyWhenAsked() {
        val root = tmp.newFolder("home")
        File(root, ".secret").mkdirs(); File(root, ".dot.txt").writeText("x"); File(root, "open").mkdirs()
        assertEquals(listOf("open"), FileOps.list(root, false)!!.map { it.name })
        assertEquals(setOf(".secret", ".dot.txt", "open"), FileOps.list(root, true)!!.map { it.name }.toSet())
        assertEquals(listOf(".secret"), FileOps.search(root, "secret", true).map { it.name })
        assertTrue(FileOps.search(root, "secret", false).isEmpty())
    }

    private fun entry(name: String, size: Long = 0, modified: Long = 0, opened: Long = 0, dir: Boolean = false) = Entry(File(name), dir, size, modified, false, opened)
    private fun order(sort: Sort, vararg e: Entry) = e.sortedWith(FileOps.comparator(sort)).map { it.name }

    @Test
    fun theOrderCanBeByLastOpening() {
        val a = entry("a", opened = 30); val b = entry("b", opened = 10); val c = entry("c", opened = 0); val d = entry("d", dir = true, opened = 99)
        assertEquals(listOf("d", "c", "b", "a"), order(Sort(SortKey.Opened), a, b, c, d))
        assertEquals("a folder stays first when descending", listOf("d", "a", "b", "c"), order(Sort(SortKey.Opened, true), a, b, c, d))
    }

    @Test
    fun theOrderByDateSizeTypeAndNameHolds() {
        val a = entry("a.txt", size = 5, modified = 3); val b = entry("B.pdf", size = 9, modified = 1); val c = entry("c.apk", size = 1, modified = 2)
        assertEquals(listOf("a.txt", "B.pdf", "c.apk"), order(Sort(SortKey.Name), c, b, a))
        assertEquals("the newest first is the date, descending", listOf("a.txt", "c.apk", "B.pdf"), order(Sort(SortKey.Date, true), c, b, a))
        assertEquals(listOf("c.apk", "a.txt", "B.pdf"), order(Sort(SortKey.Size), a, b, c))
        assertEquals(listOf("c.apk", "B.pdf", "a.txt"), order(Sort(SortKey.Type), a, b, c))
    }

    @Test
    fun theLogOfOpeningsKeepsTheNewestOnes() {
        var log = emptyMap<String, Long>()
        for (i in 1..5) log = FileOps.withOpened(log, "f$i", i.toLong(), cap = 3)
        assertEquals(setOf("f3", "f4", "f5"), log.keys)
        assertEquals(9L, FileOps.withOpened(log, "f4", 9L, cap = 3)["f4"])
        assertEquals(3, FileOps.withOpened(log, "f4", 9L, cap = 3).size)
    }
}
