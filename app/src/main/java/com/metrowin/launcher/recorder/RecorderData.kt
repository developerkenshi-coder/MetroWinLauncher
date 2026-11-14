package com.metrowin.launcher.recorder

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.metrowin.launcher.data.Recordings
import com.metrowin.launcher.data.SafFolder
import com.metrowin.launcher.data.VoiceRecorder
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Reading the recordings with what is known of each: for the list, off the main thread. */
object RecorderData {
    private val durations = ConcurrentHashMap<String, Long>()

    /** The length of [file], read once for as long as the file is the same (its size and time of change are part of the key). */
    fun durationOf(file: File): Long = durations.getOrPut("${file.name}|${file.length()}|${file.lastModified()}") { AudioTools.durationMs(file) }

    /**
     * Every recording with its length, size and meta. The first call after the screen opens also turns a part left by a crash into a
     * recording, takes away what was left in the trash and in the export folder, and the meta of recordings that are gone.
     */
    fun load(context: Context, store: Recordings, metas: RecMetaStore, first: Boolean): List<Rec> {
        if (first) {
            VoiceRecorder.recover(store)
            RecTrash.empty(context)
            RecorderShare.clearExports(context)
        }
        val files = store.list()
        val all = metas.loadAll()
        if (first) metas.purgeOrphans(files.map { RecorderFormat.stampOf(it.name) }.toSet())
        return files.map { Rec(it, durationOf(it), it.length(), all[RecorderFormat.stampOf(it.name)] ?: RecMeta()) }
    }

    /** A new recording for sound made from [source] (a cut, a normalised copy, a join): a moment later than any other, never over one. */
    fun nextFile(store: Recordings): File {
        var target = store.newFile()
        var now = java.time.LocalDateTime.now()
        while (target.exists() || store.list().any { RecorderFormat.stampOf(it.name) == RecorderFormat.stampOf(target.name) }) {
            now = now.plusSeconds(1)
            target = store.newFile(now)
        }
        return target
    }

    /** Names the new recording after its source ("Lesson (cut)") when the source has a name; the file, or the target as it was when that fails. */
    fun named(store: Recordings, target: File, name: String): File = if (name.isBlank()) target else store.rename(target, name) ?: target
}

/** A deleted recording waits here for a few seconds so it can come back; nothing stays here longer than that (the folder is emptied when the screen opens). */
object RecTrash {
    private fun dir(context: Context) = File(context.filesDir, "recordings/.trash").apply { mkdirs() }

    /** Moves [file] (and keeps its meta file where it is). The place it was moved to, or null when it could not be moved. */
    fun put(context: Context, file: File): File? {
        val target = File(dir(context), file.name)
        return if (file.renameTo(target)) target else null
    }

    /** Puts a file back into [store]'s folder; the file there, or null when the name is taken again. */
    fun restore(trashed: File, back: File): File? = if (!back.exists() && trashed.renameTo(back)) back else null

    fun empty(context: Context) { File(context.filesDir, "recordings/.trash").listFiles()?.forEach { it.delete() } }
}

/** Copies the recordings to a folder the user picked, once each (by name), where another app or the cloud can keep them. */
object RecorderBackup {
    /** The number of files copied; -1 when the folder cannot be reached any more. Blocking. */
    fun sync(context: Context, tree: Uri, files: List<File>): Int {
        val folder = SafFolder(context, tree)
        if (folder.displayName() == null) return -1
        val have = folder.names().toSet()
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return -1
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
        var copied = 0
        for (file in files) {
            if (file.name in have || !file.exists()) continue
            val ok = runCatching {
                val target = DocumentsContract.createDocument(context.contentResolver, parent, Recordings.mimeOf(file), file.name) ?: return@runCatching false
                context.contentResolver.openOutputStream(target)?.use { out -> file.inputStream().use { it.copyTo(out) } } != null
            }.getOrDefault(false)
            if (ok) copied++
        }
        return copied
    }
}
