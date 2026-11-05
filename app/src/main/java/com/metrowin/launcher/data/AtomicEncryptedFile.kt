package com.metrowin.launcher.data

import com.metrowin.launcher.notes.NoteCrypt
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * One encrypted file that is never half written: the new text goes to a file beside it and replaces the old one in a single step, so a
 * crash or a full disk leaves the old file whole. The notes, the wallet and the register of notifications keep their data this way.
 */
class AtomicEncryptedFile(private val file: File, private val crypt: NoteCrypt) {
    fun exists() = file.exists()

    /** The plain text, or null when the file cannot be read (the key of the Keystore is gone, the file is damaged). */
    fun read(): String? = runCatching { crypt.decrypt(file.readText()) }.getOrNull()

    /** Puts the file aside under [name] in the same folder, so the next write does not cover what could not be read. */
    fun putAside(name: String) {
        file.renameTo(File(file.parentFile, name))
    }

    /** False when nothing was written: the Keystore cannot encrypt, or the disk refused. The file is then as it was. */
    fun write(plain: String): Boolean {
        val sealed = crypt.encrypt(plain)
        if (sealed.isEmpty()) return false
        val temporary = File(file.parentFile, file.name + ".tmp")
        return runCatching {
            temporary.writeText(sealed)
            replace(temporary, file)
            true
        }.getOrElse {
            temporary.delete()
            false
        }
    }

    private fun replace(from: File, to: File) {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            // shortcut: not atomic where the file system cannot rename in place, but still one call that replaces the old file; upgrade if such a device shows up.
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
