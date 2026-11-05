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

        /** Pinned first, then the most recently changed. */
        fun sorted(notes: List<Note>): List<Note> = notes.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updated })
    }
}
