package com.metrowin.launcher.notes

import com.metrowin.launcher.data.readAtMost
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A backup of the notes: a `.zip` with `notes.json` (what restores them) and, to be read without the launcher, one `.txt` for each
 * note and one `.png` for each sketch. Pure but for the pictures, which the caller draws ([png]).
 */
object NoteBackup {
    const val JSON = "notes.json"
    private const val FILES = "files/"
    private const val MAX_JSON = 60L * 1024 * 1024

    fun write(notes: List<Note>, out: OutputStream, files: (String) -> File? = { null }, png: (Note) -> ByteArray? = { null }) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(JSON))
            zip.write(NoteCodec.encode(notes).toByteArray())
            zip.closeEntry()
            // The photos and sounds go in once each, under the names the notes hold them by.
            notes.flatMap { it.files }.distinct().forEach { name ->
                val file = files(name)?.takeIf { it.isFile } ?: return@forEach
                zip.putNextEntry(ZipEntry("$FILES$name"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            val used = HashSet<String>()
            notes.forEachIndexed { index, note ->
                val name = unique(fileName(note, index), used)
                zip.putNextEntry(ZipEntry("readable/$name.txt"))
                zip.write(plain(note).toByteArray())
                zip.closeEntry()
                if (note.strokes.isNotEmpty()) png(note)?.let {
                    zip.putNextEntry(ZipEntry("readable/$name.png"))
                    zip.write(it)
                    zip.closeEntry()
                }
            }
        }
    }

    /**
     * The notes of a backup, or null when the file is not one (or is too big to be one). The photos and sounds in it are handed to
     * [onFile] with their names as it meets them (after the notes: that is how a backup is written).
     */
    fun read(input: InputStream, onFile: (String, InputStream) -> Unit = { _, _ -> }): List<Note>? {
        var notes: List<Note>? = null
        ZipInputStream(input).use { zip ->
            var entries = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++entries > 20_000) return null
                if (entry.name == JSON && notes == null) {
                    // readNBytes needs Android 13; this stops at the limit on every version.
                    val bytes = zip.readAtMost(MAX_JSON.toInt()) ?: return null
                    notes = NoteCodec.decode(String(bytes)).takeIf { it.isNotEmpty() || String(bytes).contains("\"notes\"") }
                } else if (notes != null && entry.name.startsWith(FILES)) {
                    onFile(entry.name.removePrefix(FILES), zip)
                }
            }
        }
        return notes
    }

    /** The note as text for anyone: its title, then what it says (the markers of [NoteText] stay: they read as a list or a title). */
    fun plain(note: Note): String = (if (note.title.isBlank()) "" else note.title + "\n\n") + note.text

    private fun fileName(note: Note, index: Int): String {
        val base = (note.title.ifBlank { note.text.lineSequence().firstOrNull().orEmpty() }).trim()
            .map { if (it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_') it else '_' }.joinToString("").trim().take(40)
        return "%03d-%s".format(index + 1, base.ifEmpty { "note" })
    }

    private fun unique(name: String, used: MutableSet<String>): String {
        var candidate = name
        var n = 2
        while (!used.add(candidate)) candidate = "$name-${n++}"
        return candidate
    }

    /** [incoming] joined to [existing]: a note that is in both stays the more recent of the two, the new ones are added. */
    fun merge(existing: List<Note>, incoming: List<Note>): List<Note> {
        val byId = LinkedHashMap<String, Note>()
        existing.forEach { byId[it.id] = it }
        incoming.forEach { note -> byId[note.id]?.let { if (note.updated > it.updated) byId[note.id] = note } ?: run { byId[note.id] = note } }
        return byId.values.toList()
    }
}
