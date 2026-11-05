package com.metrowin.launcher.notes

import android.content.Context
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

    /**
     * The notes. A file that cannot be read (the key of the Keystore is gone) gives none and is put aside, not left to be written over by
     * the next note: it is kept as `notes.unreadable`.
     */
    fun load(): List<Note> {
        if (!file.exists()) return emptyList()
        val plain = runCatching { crypt.decrypt(file.readText()) }.getOrNull()
        if (plain == null) {
            file.renameTo(unreadable())
            return emptyList()
        }
        return NoteCodec.decode(plain)
    }

    /** Whether a file of notes that could not be read is being kept (see [load]). */
    fun hasUnreadable() = unreadable().exists()

    fun discardUnreadable() {
        unreadable().delete()
    }

    private fun unreadable() = File(file.parentFile, "notes.unreadable")

    /** False when nothing was written. */
    fun save(notes: List<Note>): Boolean {
        val sealed = crypt.encrypt(NoteCodec.encode(notes))
        if (sealed.isEmpty()) return false
        return runCatching {
            val temporary = File(file.parentFile, file.name + ".tmp")
            temporary.writeText(sealed)
            if (!temporary.renameTo(file)) {
                file.delete()
                temporary.renameTo(file)
            }
            true
        }.getOrDefault(false)
    }

    companion object {
        /** Pinned first, then the most recently changed. */
        fun sorted(notes: List<Note>): List<Note> = notes.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updated })
    }
}
