package com.metrowin.launcher.recorder

import android.content.Context
import android.content.Intent
import android.content.ContentResolver
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.metrowin.launcher.data.Recordings
import java.io.File

/** Hands recordings, and the text written from them, to other apps, and takes sound from other places into the recordings. */
object RecorderShare {
    /** The types the system picker is asked for when sound is imported: every audio type, and the ones some phones give to ogg and to old files. */
    val IMPORT_TYPES = arrayOf("audio/*", "application/ogg", "video/3gpp", "video/mp4")

    private fun uri(context: Context, file: File): Uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)

    /** The chooser for one or more recordings: one file as it is typed, several as audio. Null when there is nothing to send. */
    fun audio(context: Context, files: List<File>): Intent? {
        if (files.isEmpty()) return null
        val send = if (files.size == 1) Intent(Intent.ACTION_SEND).setType(Recordings.mimeOf(files[0])).putExtra(Intent.EXTRA_STREAM, uri(context, files[0]))
        else Intent(Intent.ACTION_SEND_MULTIPLE).setType("audio/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files.map { uri(context, it) }))
        return Intent.createChooser(send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null)
    }

    /** The chooser for the text of a transcript. */
    fun text(text: String, subject: String): Intent =
        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).putExtra(Intent.EXTRA_SUBJECT, subject), null)

    /** How many bytes of one imported file are taken at most: a file that big is not a recording to be kept in the storage of an app. */
    const val MAX_IMPORT = 1L shl 30

    /**
     * Copies the sound of each of [uris] into [store]. A file that the phone cannot read as sound (no audio track, no length), is not
     * of a type that is known, or is bigger than [MAX_IMPORT] is not copied: those are the second number of the pair, the files made the first.
     */
    fun import(context: Context, store: Recordings, uris: List<Uri>): Pair<List<File>, Int> {
        val made = ArrayList<File>()
        var refused = 0
        for (uri in uris) {
            val name = displayName(context.contentResolver, uri)
            val extension = Recordings.extensionFor(name, context.contentResolver.getType(uri))
            val readable = extension != null && isSound(context, uri)
            val file = if (!readable) null else store.addImported(name ?: "", extension!!) { part ->
                var total = 0L
                context.contentResolver.openInputStream(uri)?.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_IMPORT) throw java.io.IOException("too big")
                            output.write(buffer, 0, read)
                        }
                    }
                } ?: throw java.io.IOException("not opened")
            }
            if (file != null) made += file else refused++
        }
        return made to refused
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? =
        runCatching { resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull()

    /** Whether the phone sees sound in the file: the retriever finds an audio track and a length. */
    private fun isSound(context: Context, uri: Uri): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes" &&
                (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) > 0L
        } catch (e: Exception) {
            false
        } finally {
            runCatching { retriever.release() }
        }
    }
}
