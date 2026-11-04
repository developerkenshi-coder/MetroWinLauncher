package com.metrowin.launcher.files

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A thing in the trash: where it came from, when it was thrown away and how big it was. */
data class Trashed(val id: String, val name: String, val original: String, val deleted: Long, val size: Long)

/**
 * The trash of the file manager: a folder with what was deleted, and an index that says where each thing came from, so it can go back.
 * Things stay [KEEP_DAYS] days. Pure but for the folder it lives in.
 */
class FileTrash(private val dir: File, private val clock: () -> Long = System::currentTimeMillis) {
    private val index get() = File(dir, "index.json")

    init { dir.mkdirs() }

    fun list(): List<Trashed> = runCatching {
        val array = JSONArray(index.takeIf { it.exists() }?.readText() ?: "[]")
        (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            Trashed(o.getString("id"), o.getString("name"), o.getString("original"), o.getLong("deleted"), o.optLong("size"))
                .takeIf { File(dir, it.id).exists() }
        }.sortedByDescending { it.deleted }
    }.getOrDefault(emptyList())

    private fun save(items: List<Trashed>) {
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name).put("original", it.original).put("deleted", it.deleted).put("size", it.size)) }
        index.writeText(array.toString())
    }

    /** Throws [file] into the trash. False when it could not be moved. */
    fun put(file: File, progress: Progress = Progress()): Boolean {
        val id = UUID.randomUUID().toString()
        val size = FileOps.size(file)
        val moved = FileOps.move(listOf(file), dir, Conflict.KeepBoth, progress)
        val inside = moved.singleOrNull() ?: return false
        val renamed = File(dir, id)
        if (!inside.renameTo(renamed)) { FileOps.move(listOf(inside), file.parentFile ?: return false, Conflict.KeepBoth, Progress()); return false }
        save(list() + Trashed(id, file.name, file.absolutePath, clock(), size))
        return true
    }

    /** Puts [item] back where it was (under another name if that is taken; in [fallback] if its folder is gone). The file it became. */
    fun restore(item: Trashed, fallback: File): File? {
        val source = File(dir, item.id).takeIf { it.exists() } ?: return null
        val original = File(item.original)
        val folder = original.parentFile?.takeIf { it.isDirectory } ?: fallback.also { it.mkdirs() }
        val target = FileOps.uniqueFile(folder, item.name)
        if (!source.renameTo(target)) {
            val made = FileOps.copy(listOf(source), folder, Conflict.KeepBoth, Progress()).firstOrNull() ?: return null
            FileOps.delete(source)
            made.renameTo(target)
        }
        save(list().filter { it.id != item.id })
        return target
    }

    fun purge(item: Trashed) {
        FileOps.delete(File(dir, item.id))
        save(list().filter { it.id != item.id })
    }

    fun empty() {
        list().forEach { FileOps.delete(File(dir, it.id)) }
        save(emptyList())
    }

    /** Takes out what has been here more than [KEEP_DAYS] days. */
    fun purgeOld() {
        val limit = clock() - KEEP_DAYS * 24L * 3_600_000L
        list().filter { it.deleted < limit }.forEach { purge(it) }
    }

    companion object {
        const val KEEP_DAYS = 30
    }
}
