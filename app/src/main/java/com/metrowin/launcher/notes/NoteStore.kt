package com.metrowin.launcher.notes

import android.content.Context
import com.metrowin.launcher.data.AtomicEncryptedFile
import com.metrowin.launcher.data.SecureStore
import java.io.File

/** What encrypts the notes at rest: the Keystore on the phone, anything in a test. */
interface NoteCrypt {
    fun encrypt(plain: String): String
    fun decrypt(text: String): String?
}

object KeystoreCrypt : NoteCrypt {
    override fun encrypt(plain: String) = SecureStore.encrypt(plain)
    override fun decrypt(text: String) = SecureStore.decrypt(text)
}

/**
 * The notes of the phone, in one encrypted file. A note is never lost to a failed write: the new file is written beside the old
 * and replaces it only when it is whole, and when the Keystore cannot encrypt, the file is left as it was.
 */
class NoteStore(private val file: File, private val crypt: NoteCrypt = KeystoreCrypt) {
    constructor(context: Context) : this(File(context.filesDir, "notes.dat"))

    private val sealed = AtomicEncryptedFile(file, crypt)

    /**
     * The notes. A file that cannot be read (the key of the Keystore is gone) gives none and is put aside, not left to be written over by
     * the next note: it is kept as `notes.unreadable`.
     */
    fun load(): List<Note> {
        if (!sealed.exists()) return emptyList()
        val plain = sealed.read()
        if (plain == null) {
            sealed.putAside(UNREADABLE)
            return emptyList()
        }
        return NoteCodec.decode(plain)
    }

    /** Whether a file of notes that could not be read is being kept (see [load]). */
    fun hasUnreadable() = unreadable().exists()

    fun discardUnreadable() {
        unreadable().delete()
    }

    private fun unreadable() = File(file.parentFile, UNREADABLE)

    /** False when nothing was written. */
    fun save(notes: List<Note>): Boolean = sealed.write(NoteCodec.encode(notes))

    companion object {
        private const val UNREADABLE = "notes.unreadable"

        /** Days a note stays in the bin before it is gone for good. */
        const val BIN_DAYS = 30
        const val SORT_UPDATED = 0
        const val SORT_TITLE = 1
        const val SORT_CREATED = 2

        /** Pinned first, then by [by]: the most recently changed (the default), the title, or the most recently made. */
        fun sorted(notes: List<Note>, by: Int = SORT_UPDATED): List<Note> {
            val pinned = compareByDescending<Note> { it.pinned }
            return notes.sortedWith(
                when (by) {
                    SORT_TITLE -> pinned.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title.ifBlank { it.text.lineSequence().firstOrNull().orEmpty() } }
                    SORT_CREATED -> pinned.thenByDescending { it.created }
                    else -> pinned.thenByDescending { it.updated }
                },
            )
        }

        /**
         * The notes that have every word of [query] in the title, the folder or the text (the text of a locked note is not looked into). With [ranked] the ones with the words in the title come
         * first, then the folder, then the text; without, they stay in the order they came.
         */
        fun search(notes: List<Note>, query: String, ranked: Boolean = true): List<Note> {
            val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) return notes
            fun score(n: Note): Int {
                var total = 0
                for (w in words) {
                    val one = (if (n.title.startsWith(w, true)) 4 else if (n.title.contains(w, true)) 3 else 0) +
                        (if (n.folder.contains(w, true)) 2 else 0) + (if (!n.locked && n.text.contains(w, true)) 1 else 0)
                    if (one == 0) return 0
                    total += one
                }
                return total
            }
            val found = notes.map { it to score(it) }.filter { it.second > 0 }
            return (if (ranked) found.sortedByDescending { it.second } else found).map { it.first }
        }

        /** The notes of the bin that have been there longer than [BIN_DAYS] days at [now]. */
        fun expired(notes: List<Note>, now: Long): List<Note> = notes.filter { it.deleted > 0 && now - it.deleted > BIN_DAYS * 86_400_000L }
    }
}
