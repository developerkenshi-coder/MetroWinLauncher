package com.metrowin.launcher.notes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * The photos and sounds of the notes, one file each in the storage of the app.
 * shortcut: kept as they came, not encrypted like the text of the notes (the storage of the app is closed to other apps); upgrade with
 * a stream cipher if the pictures must be as private as the words.
 */
class NoteFiles(private val dir: File) {
    constructor(context: Context) : this(File(context.filesDir, "note_files"))

    fun file(name: String): File? = if (valid(name)) File(dir, name) else null

    /** Copies [input] in under a new name ending in [extension]; the name, or null when it is too big or cannot be written. */
    fun add(input: InputStream, extension: String): String? {
        val ext = extension.lowercase().filter { it.isLetterOrDigit() }.take(5).ifEmpty { "bin" }
        return store("${UUID.randomUUID()}.$ext", input)
    }

    /** A file of a backup, back under its own name. */
    fun restore(name: String, input: InputStream): Boolean = valid(name) && store(name, input) != null

    private fun store(name: String, input: InputStream): String? {
        dir.mkdirs()
        val target = File(dir, name)
        val ok = runCatching {
            target.outputStream().use { out ->
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) return@runCatching false
                    out.write(buffer, 0, n)
                }
                true
            }
        }.getOrDefault(false)
        if (!ok) target.delete()
        return name.takeIf { ok }
    }

    fun delete(name: String) {
        file(name)?.delete()
    }

    /** The picture of [name] read down to about [max] pixels wide. */
    fun picture(name: String, max: Int): Bitmap? = runCatching {
        val path = file(name)!!.path
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(path, it) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= max) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    companion object {
        const val MAX_BYTES = 25L * 1024 * 1024
        private val name = Regex("[A-Za-z0-9-]{1,40}\\.[a-z0-9]{1,5}")
        private val pictures = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp")

        /** A name that stays inside the folder: letters, digits and dashes, one dot, an extension. */
        fun valid(candidate: String) = name.matches(candidate)

        fun isPicture(name: String) = name.substringAfterLast('.').lowercase() in pictures
    }
}
