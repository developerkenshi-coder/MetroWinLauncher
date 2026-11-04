package com.metrowin.launcher

import com.metrowin.launcher.notes.Note
import com.metrowin.launcher.notes.NoteBackup
import com.metrowin.launcher.notes.NoteCodec
import com.metrowin.launcher.notes.NoteCrypt
import com.metrowin.launcher.notes.NoteStore
import com.metrowin.launcher.notes.NoteText
import com.metrowin.launcher.notes.NoteText.Kind
import com.metrowin.launcher.notes.Pt
import com.metrowin.launcher.notes.Stroke
import com.metrowin.launcher.notes.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class NotesModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val sketch = Note(
        "a1", "Plan", "# Day\n☐ buy\n☑ call", listOf(
            Stroke(Tool.Pen, 0xFF112233.toInt(), 6f, listOf(Pt(1f, 2f), Pt(30f, 40f), Pt(999f, 1399f))),
            Stroke(Tool.Text, -1, 5f, listOf(Pt(10f, 20f)), "Start \"here\""),
        ),
        color = 3, pinned = true, folder = "work", created = 10, updated = 20,
    )

    @Test
    fun `a note comes back from its JSON as it went`() {
        val back = NoteCodec.decode(NoteCodec.encode(listOf(sketch, Note("b2", "", "x"))))
        assertEquals(2, back.size)
        assertEquals(sketch, back[0])
        assertEquals("x", back[1].text)
    }

    @Test
    fun `reading a damaged file gives what can be read and never fails`() {
        assertTrue(NoteCodec.decode("not json").isEmpty())
        assertTrue(NoteCodec.decode("""{"notes":[{"x":1},{"id":"ok","title":"t"}]}""").map { it.id } == listOf("ok"))
        val tool = NoteCodec.decode("""{"notes":[{"id":"ok","strokes":[{"tool":"Bogus","color":1,"p":[1,2]}]}]}""")
        assertTrue(tool.single().strokes.isEmpty())
    }

    @Test
    fun `lines are read as titles, bullets and boxes`() {
        val lines = NoteText.parse("# A\n## B\n- c\n☐ d\n☑ e\nplain")
        assertEquals(listOf(Kind.Title, Kind.Subtitle, Kind.Bullet, Kind.Unchecked, Kind.Checked, Kind.Plain), lines.map { it.kind })
        assertEquals("d", lines[3].body)
        assertEquals(3, lines[3].index)
    }

    @Test
    fun `a box is ticked and cleared and other lines are left alone`() {
        val text = "☐ a\n☑ b\nc"
        assertEquals("☑ a\n☑ b\nc", NoteText.toggle(text, 0))
        assertEquals("☐ a\n☐ b\nc", NoteText.toggle(NoteText.toggle(text, 1), 1).let { NoteText.toggle(it, 1) })
        assertEquals(text, NoteText.toggle(text, 2))
        assertEquals(text, NoteText.toggle(text, 9))
    }

    @Test
    fun `bold pieces are found`() {
        assertEquals(listOf("a " to false, "b" to true, " c" to false), NoteText.spans("a **b** c"))
        assertEquals(listOf("a **b" to false), NoteText.spans("a **b"))
        assertEquals(listOf("" to false), NoteText.spans(""))
    }

    @Test
    fun `a marker is put on the line of the cursor, changed or taken off`() {
        val (a, cursor) = NoteText.mark("one\ntwo", 5, "☐ ")
        assertEquals("one\n☐ two", a)
        assertEquals(7, cursor)
        assertEquals("one\n- two", NoteText.mark(a, 6, "- ").first)
        assertEquals("one\ntwo", NoteText.mark("one\n- two", 6, "- ").first)
        assertEquals("# ", NoteText.mark("", 0, "# ").first)
    }

    @Test
    fun `bold is put round the selection and taken off again`() {
        val (once, range) = NoteText.bold("say hi now", 4, 6)
        assertEquals("say **hi** now", once)
        assertEquals("say hi now", NoteText.bold(once, range.first, range.last + 1).first)
    }

    @Test
    fun `a backup holds the notes, one text for each and the pictures asked for`() {
        val out = ByteArrayOutputStream()
        NoteBackup.write(listOf(sketch, Note("b2", "Plan", "again")), out) { byteArrayOf(1, 2, 3) }
        val names = ArrayList<String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip -> generateSequence { zip.nextEntry }.forEach { names += it.name } }
        assertTrue(names.toString(), "notes.json" in names)
        assertEquals(2, names.count { it.endsWith(".txt") })
        assertEquals(listOf("readable/001-Plan.png"), names.filter { it.endsWith(".png") })
        assertTrue("same titles do not collide", names.filter { it.endsWith(".txt") }.toSet().size == 2)
        assertEquals(listOf(sketch, Note("b2", "Plan", "again")), NoteBackup.read(ByteArrayInputStream(out.toByteArray())))
    }

    @Test
    fun `a file that is not a backup is refused`() {
        assertNull(NoteBackup.read(ByteArrayInputStream("hello".toByteArray())))
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { it.putNextEntry(java.util.zip.ZipEntry("other.txt")); it.write(1) }
        assertNull(NoteBackup.read(ByteArrayInputStream(out.toByteArray())))
    }

    @Test
    fun `restoring keeps the more recent copy and adds the missing ones`() {
        val old = Note("a", text = "old", updated = 1)
        val new = Note("a", text = "new", updated = 2)
        val other = Note("b", text = "b", updated = 1)
        assertEquals(listOf(new, other), NoteBackup.merge(listOf(old), listOf(other, new)))
        assertEquals(listOf(new, other), NoteBackup.merge(listOf(new, other), listOf(old)))
    }

    private object Plain : NoteCrypt {
        override fun encrypt(plain: String) = "enc:$plain"
        override fun decrypt(text: String) = text.removePrefix("enc:").takeIf { text.startsWith("enc:") }
    }

    @Test
    fun `the store writes encrypted and reads back`() {
        val file = folder.newFile("notes.dat")
        file.delete()
        val store = NoteStore(file, Plain)
        assertTrue(store.load().isEmpty())
        assertTrue(store.save(listOf(sketch)))
        assertTrue(file.readText().startsWith("enc:"))
        assertEquals(listOf(sketch), store.load())
        assertFalse(File2.exists(file.path + ".tmp"))
    }

    @Test
    fun `when the key cannot encrypt nothing is overwritten`() {
        val file = folder.newFile("n.dat")
        file.writeText("enc:" + NoteCodec.encode(listOf(sketch)))
        val broken = object : NoteCrypt {
            override fun encrypt(plain: String) = ""
            override fun decrypt(text: String) = Plain.decrypt(text)
        }
        assertFalse(NoteStore(file, broken).save(emptyList()))
        assertEquals(listOf(sketch), NoteStore(file, Plain).load())
    }

    @Test
    fun `an unreadable file is put aside and not written over`() {
        val file = folder.newFile("m.dat")
        file.writeText("garbage")
        val store = NoteStore(file, Plain)
        assertTrue(store.load().isEmpty())
        assertFalse(file.exists())
        assertEquals("garbage", java.io.File(file.parentFile, "notes.unreadable").readText())
        assertNotNull(store)
    }

    @Test
    fun `pinned notes come first and then the latest`() {
        val list = listOf(Note("1", updated = 5), Note("2", updated = 9), Note("3", updated = 1, pinned = true))
        assertEquals(listOf("3", "2", "1"), NoteStore.sorted(list).map { it.id })
    }

    private object File2 {
        fun exists(path: String) = java.io.File(path).exists()
    }
}
