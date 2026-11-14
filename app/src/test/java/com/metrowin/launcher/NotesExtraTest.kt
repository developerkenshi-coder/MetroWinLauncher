package com.metrowin.launcher

import com.metrowin.launcher.notes.Note
import com.metrowin.launcher.notes.NoteBackup
import com.metrowin.launcher.notes.NoteCodec
import com.metrowin.launcher.notes.NoteFiles
import com.metrowin.launcher.notes.NoteHistory
import com.metrowin.launcher.notes.NoteSeal
import com.metrowin.launcher.notes.NoteStore
import com.metrowin.launcher.notes.NoteText
import com.metrowin.launcher.notes.Pt
import com.metrowin.launcher.notes.SealException
import com.metrowin.launcher.notes.SketchGeometry
import com.metrowin.launcher.notes.Stroke
import com.metrowin.launcher.notes.Tool
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** What was added to the notes after the first version: lists that go on, ranked search, the bin, files nobody holds, sealed backups, undo. */
class NotesExtraTest {
    @get:Rule
    val folder = TemporaryFolder()

    // --- lists that go on

    @Test
    fun `Enter at the end of a bullet starts the next one`() {
        assertEquals("- a\n- " to 6, NoteText.continueList("- a", "- a\n", 4))
        assertEquals("• a\n• " to 6, NoteText.continueList("• a", "• a\n", 4))
    }

    @Test
    fun `a box is cleared on the next line and a number goes up`() {
        assertEquals("☑ done\n☐ " to 9, NoteText.continueList("☑ done", "☑ done\n", 7))
        assertEquals("1. one\n2. " to 10, NoteText.continueList("1. one", "1. one\n", 7))
        assertEquals("9. x\n10. " to 9, NoteText.continueList("9. x", "9. x\n", 5))
    }

    @Test
    fun `Enter on an empty item ends the list`() {
        assertEquals("a\n" to 2, NoteText.continueList("a\n- ", "a\n- \n", 5))
        assertEquals("" to 0, NoteText.continueList("1. ", "1. \n", 4))
    }

    @Test
    fun `Enter in the middle of a list item carries the rest to the next one`() {
        assertEquals("- ab\n- cd" to 7, NoteText.continueList("- abcd", "- ab\ncd", 5))
    }

    @Test
    fun `typing that is not Enter at a list is left alone`() {
        assertNull(NoteText.continueList("plain", "plain\n", 6))
        assertNull(NoteText.continueList("- a", "- ab", 4))
        assertNull(NoteText.continueList("- a", "- a\nb\n", 6))
        assertNull(NoteText.continueList("", "\n", 0))
    }

    @Test
    fun `numbered lines are read with their number`() {
        val line = NoteText.parse("12. twelve").single()
        assertEquals(NoteText.Kind.Numbered, line.kind)
        assertEquals(12, line.number)
        assertEquals("twelve", line.body)
        assertEquals(NoteText.Kind.Plain, NoteText.parse("2024 was a year").single().kind)
    }

    @Test
    fun `bold pieces are found as ranges of the text`() {
        assertEquals(listOf(2..9), NoteText.boldRanges("a **bold** c"))
        assertTrue(NoteText.boldRanges("a **b\nc** d").isEmpty())
        assertTrue(NoteText.boldRanges("a ** b").isEmpty())
    }

    // --- search, order, bin

    private val notes = listOf(
        Note("1", title = "Garden", text = "tomatoes and basil", updated = 3),
        Note("2", title = "Shopping", text = "garden hose", folder = "home", updated = 9),
        Note("3", title = "x", text = "nothing", folder = "garden club", updated = 1),
        Note("4", title = "Locked", text = "secret garden", updated = 5, locked = true),
    )

    @Test
    fun `search puts the title first, then the folder, then the text`() {
        // The text of note 4 has the word too, but it is locked.
        assertEquals(listOf("1", "3", "2"), NoteStore.search(notes, "garden").map { it.id })
        assertEquals(listOf("1", "2", "3"), NoteStore.search(notes, "garden", ranked = false).map { it.id })
    }

    @Test
    fun `every word must be found, in any place`() {
        assertEquals(listOf("2"), NoteStore.search(notes, "garden shopping").map { it.id })
        assertTrue(NoteStore.search(notes, "garden zebra").isEmpty())
        assertEquals(notes, NoteStore.search(notes, "   "))
    }

    @Test
    fun `the text of a locked note is not searched`() {
        // "secret" is only in the text of the locked note.
        assertTrue(NoteStore.search(notes, "secret").isEmpty())
    }

    @Test
    fun `the order can be by title or by the day it was made`() {
        val list = listOf(Note("a", title = "b", created = 1, updated = 5), Note("b", title = "A", created = 9, updated = 1), Note("c", title = "c", created = 5, updated = 9, pinned = true))
        assertEquals(listOf("c", "a", "b"), NoteStore.sorted(list).map { it.id })
        assertEquals(listOf("c", "b", "a"), NoteStore.sorted(list, NoteStore.SORT_TITLE).map { it.id })
        assertEquals(listOf("c", "b", "a"), NoteStore.sorted(list, NoteStore.SORT_CREATED).map { it.id })
    }

    @Test
    fun `a note stays in the bin thirty days`() {
        val day = 86_400_000L
        val now = 100 * day
        val list = listOf(Note("1", deleted = now - 29 * day), Note("2", deleted = now - 31 * day), Note("3"))
        assertEquals(listOf("2"), NoteStore.expired(list, now).map { it.id })
    }

    @Test
    fun `the bin, the paper and the lock come back from the JSON, and the colour stays among the swatches`() {
        val note = Note("n", deleted = 77, paper = 2, locked = true, color = 7)
        assertEquals(note, NoteCodec.decode(NoteCodec.encode(listOf(note))).single())
        val wild = NoteCodec.encode(listOf(note)).replace("\"color\":7", "\"color\":15").replace("\"paper\":2", "\"paper\":9")
        val back = NoteCodec.decode(wild).single()
        assertEquals(NoteCodec.MAX_COLOR, back.color)
        assertEquals(NoteCodec.PAPERS - 1, back.paper)
        // A note of before the bin, the paper and the lock has none of them.
        val old = NoteCodec.decode("""{"notes":[{"id":"o","title":"t"}]}""").single()
        assertEquals(0L, old.deleted)
        assertFalse(old.locked)
    }

    // --- files nobody holds

    @Test
    fun `files that no note holds are taken away and the others stay`() {
        val dir = folder.newFolder("files")
        listOf("aaaa.jpg", "bbbb.m4a", "cccc.png").forEach { File(dir, it).writeText("x") }
        val files = NoteFiles(dir)
        assertEquals(2, files.prune(setOf("bbbb.m4a")))
        assertEquals(listOf("bbbb.m4a"), dir.list()!!.toList())
        assertEquals(0, files.prune(setOf("bbbb.m4a")))
        assertEquals(0, NoteFiles(File(dir, "missing")).prune(emptySet()))
    }

    @Test
    fun `a backup keeps the fallback name of a note without a title in the language given`() {
        val out = ByteArrayOutputStream()
        NoteBackup.write(listOf(Note("a", "", "")), out, fallback = "nota") { null }
        val names = java.util.zip.ZipInputStream(ByteArrayInputStream(out.toByteArray())).let { zip -> generateSequence { zip.nextEntry }.map { it.name }.toList() }
        assertTrue(names.toString(), names.contains("readable/001-nota.txt"))
    }

    // --- sealed backups

    private fun seal(bytes: ByteArray, password: String): ByteArray = ByteArrayOutputStream().also { NoteSeal.seal(it, password).use { s -> s.write(bytes) } }.toByteArray()

    @Test
    fun `a sealed file opens with the password and not without it`() {
        // More than three chunks of 64 KB, and a size that is not a multiple of one.
        val plain = ByteArray(200_000) { (it * 31).toByte() }
        val sealed = seal(plain, "correct horse")
        assertTrue(NoteSeal.isSealed(sealed))
        assertArrayEquals(plain, NoteSeal.open(ByteArrayInputStream(sealed), "correct horse").readBytes())
        try {
            NoteSeal.open(ByteArrayInputStream(sealed), "wrong").readBytes()
            fail("opened with the wrong password")
        } catch (e: SealException) {
            // as it should
        }
    }

    @Test
    fun `an empty sealed file and one of exactly one chunk both open`() {
        assertEquals(0, NoteSeal.open(ByteArrayInputStream(seal(ByteArray(0), "pass")), "pass").readBytes().size)
        val chunk = ByteArray(64 * 1024) { 5 }
        assertArrayEquals(chunk, NoteSeal.open(ByteArrayInputStream(seal(chunk, "pass")), "pass").readBytes())
    }

    @Test
    fun `a sealed file that was cut short or changed is refused`() {
        val sealed = seal(ByteArray(150_000) { 1 }, "pass")
        fun fails(bytes: ByteArray, why: String) = try {
            NoteSeal.open(ByteArrayInputStream(bytes), "pass").readBytes()
            fail(why)
        } catch (e: SealException) {
            // as it should
        }
        fails(sealed.copyOf(sealed.size - 50), "cut in the last chunk")
        // Whole chunks taken off the end: the last mark is gone, and a file that does not end with one is cut.
        val firstChunkEnd = 5 + 16 + 1 + 4 + 64 * 1024 + 16
        fails(sealed.copyOf(firstChunkEnd), "last chunks dropped")
        fails(sealed.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }, "a byte changed")
        assertTrue(runCatching { NoteSeal.open(ByteArrayInputStream("not sealed at all......".toByteArray()), "pass") }.exceptionOrNull() is SealException)
    }

    @Test
    fun `two seals of the same text are not the same bytes`() {
        assertFalse(seal(byteArrayOf(1, 2, 3), "pass").contentEquals(seal(byteArrayOf(1, 2, 3), "pass")))
    }

    @Test
    fun `a backup goes through the seal and comes back with its files`() {
        val note = Note("f1", "Trip", "x", files = listOf("11111111-2222.jpg"))
        val dir = folder.newFolder("held")
        File(dir, "11111111-2222.jpg").writeBytes(byteArrayOf(9, 8, 7))
        val out = ByteArrayOutputStream()
        NoteSeal.seal(out, "pass").use { NoteBackup.write(listOf(note), it, { name -> File(dir, name) }) }
        val got = HashMap<String, List<Byte>>()
        val back = NoteBackup.read(NoteSeal.open(ByteArrayInputStream(out.toByteArray()), "pass")) { name, stream -> got[name] = stream.readBytes().toList() }
        assertEquals(listOf(note), back)
        assertEquals(mapOf("11111111-2222.jpg" to listOf<Byte>(9, 8, 7)), got)
    }

    // --- undo

    @Test
    fun `undo goes back through what was typed and redo comes forward`() {
        val h = NoteHistory("a")
        assertFalse(h.canUndo)
        h.push(NoteHistory.State("ab", 2))
        h.push(NoteHistory.State("abc", 3))
        // "abcd" was typed since the last step: undo keeps it first and goes back one step.
        assertEquals("abc", h.undo(NoteHistory.State("abcd", 4))?.text)
        assertEquals("ab", h.undo(NoteHistory.State("abc", 3))?.text)
        assertEquals("abc", h.redo()?.text)
        assertEquals("abcd", h.redo()?.text)
        assertNull(h.redo())
    }

    @Test
    fun `a new text after an undo forgets the steps ahead, and the history is bounded`() {
        val h = NoteHistory("a", limit = 3)
        h.push(NoteHistory.State("b", 1))
        h.undo(NoteHistory.State("b", 1))
        h.push(NoteHistory.State("c", 1))
        assertFalse(h.canRedo)
        repeat(10) { h.push(NoteHistory.State("x$it", 1)) }
        var steps = 0
        while (h.undo(NoteHistory.State(h.text, 0)) != null) steps++
        assertEquals(2, steps)
        assertNull(NoteHistory("z").undo(NoteHistory.State("z", 0)))
    }

    // --- sketch

    @Test
    fun `a stroke moves whole and stays on the page`() {
        val stroke = Stroke(Tool.Line, 0, 6f, listOf(Pt(100f, 100f), Pt(200f, 150f)))
        assertEquals(listOf(Pt(130f, 90f), Pt(230f, 140f)), SketchGeometry.moved(stroke, 30f, -10f).points)
        // Pushed past the edges, it stops with its far end at the edge, its shape unchanged.
        assertEquals(listOf(Pt(900f, 1350f), Pt(1000f, 1400f)), SketchGeometry.moved(stroke, 5000f, 5000f).points)
        assertEquals(listOf(Pt(0f, 0f), Pt(100f, 50f)), SketchGeometry.moved(stroke, -5000f, -5000f).points)
        // A stroke off the page (from a backup) is not held back and does not fail.
        val off = Stroke(Tool.Pen, 0, 6f, listOf(Pt(-50f, 0f), Pt(2000f, 10f)))
        assertEquals(listOf(Pt(-40f, 5f), Pt(2010f, 15f)), SketchGeometry.moved(off, 10f, 5f).points)
    }
}
