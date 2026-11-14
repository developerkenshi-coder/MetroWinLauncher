package com.metrowin.launcher.notes

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Sending a picture, a PDF or a photo of a note to another app: the file is copied into the shared folder of the cache, the only one the provider of the launcher hands out. */
object NoteShare {
    private fun uri(context: Context, file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    private fun folder(context: Context) = File(context.cacheDir, "shared").apply { mkdirs() }

    /** [name] with nothing in it that a path could use. */
    fun safeName(name: String) = name.map { if (it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' || it == '.') it else '_' }.joinToString("").trim().take(60).ifEmpty { "note" }

    /** Sends [bytes] as the file [name] of type [mime]. */
    fun sendBytes(context: Context, name: String, mime: String, bytes: ByteArray) {
        val file = File(folder(context), safeName(name)).also { it.writeBytes(bytes) }
        send(context, listOf(uri(context, file)), mime)
    }

    /** Sends the copies of [files] (a name in the folder of the photos, and the file). */
    fun sendFiles(context: Context, files: List<Pair<String, File>>, mime: String) {
        val uris = files.map { (name, source) -> uri(context, File(folder(context), safeName(name)).also { source.copyTo(it, overwrite = true) }) }
        send(context, uris, mime)
    }

    private fun send(context: Context, uris: List<Uri>, mime: String) {
        val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        intent.setType(mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
    }
}
