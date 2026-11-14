package com.metrowin.launcher.recorder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * What is known about a recording besides its sound: the text written from it, the note it went to, its markers, its label and how it
 * was made. It lives in a small file of its own, named after the date stamp of the recording (which a rename never changes), so the
 * sound files stay what they were.
 */
data class RecMeta(
    val transcript: String = "",
    val transcribedAt: Long = 0L,
    val noteId: String = "",
    val marks: List<Long> = emptyList(),
    val label: String = "",
    /** "cut", "normalised", "merged", "faded", "trimmed" or "": how a recording that came from others was made. */
    val made: String = "",
) {
    val isEmpty get() = this == RecMeta()

    fun toJson(): String = JSONObject().apply {
        if (transcript.isNotEmpty()) put("t", transcript)
        if (transcribedAt != 0L) put("ta", transcribedAt)
        if (noteId.isNotEmpty()) put("n", noteId)
        if (marks.isNotEmpty()) put("m", JSONArray(marks))
        if (label.isNotEmpty()) put("l", label)
        if (made.isNotEmpty()) put("k", made)
    }.toString()

    companion object {
        fun fromJson(text: String?): RecMeta {
            val o = runCatching { JSONObject(text ?: return RecMeta()) }.getOrNull() ?: return RecMeta()
            val marks = o.optJSONArray("m")?.let { a -> (0 until a.length()).map { a.optLong(it) } }.orEmpty()
            return RecMeta(o.optString("t"), o.optLong("ta"), o.optString("n"), marks, o.optString("l"), o.optString("k"))
        }
    }
}

/** The files of [RecMeta], one per recording, found by the date stamp in the name of the sound ("20261026-183005"). */
class RecMetaStore(private val dir: File) {
    constructor(context: Context) : this(File(context.filesDir, "recordings_meta"))

    private fun file(stamp: String) = File(dir, "$stamp.json")

    fun load(stamp: String): RecMeta = runCatching { RecMeta.fromJson(file(stamp).takeIf { it.exists() }?.readText()) }.getOrDefault(RecMeta())

    fun loadAll(): Map<String, RecMeta> = dir.listFiles { f -> f.extension == "json" }.orEmpty()
        .associate { it.nameWithoutExtension to runCatching { RecMeta.fromJson(it.readText()) }.getOrDefault(RecMeta()) }

    /** Writes through a part file, so a cut-short write never leaves half a file; an empty meta removes the file. */
    fun save(stamp: String, meta: RecMeta) {
        if (stamp.isEmpty()) return
        if (meta.isEmpty) { file(stamp).delete(); return }
        dir.mkdirs()
        val part = File(dir, "$stamp.tmp")
        runCatching {
            part.writeText(meta.toJson())
            if (!part.renameTo(file(stamp))) part.delete()
        }.onFailure { part.delete() }
    }

    fun update(stamp: String, change: (RecMeta) -> RecMeta) = save(stamp, change(load(stamp)))

    fun delete(stamp: String) { file(stamp).delete() }

    /** Takes away the meta of recordings that are gone (a delete from another page leaves them). */
    fun purgeOrphans(stamps: Set<String>) {
        dir.listFiles().orEmpty().forEach { f -> if (f.extension == "tmp" || f.nameWithoutExtension !in stamps) f.delete() }
    }
}
