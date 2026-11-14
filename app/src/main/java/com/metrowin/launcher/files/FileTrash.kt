package com.metrowin.launcher.files

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A thing in the trash: where it came from, when it was thrown away and how big it was. */
data class Trashed(val id: String, val name: String, val original: String, val deleted: Long, val size: Long)

/**
 * The trash of the file manager: a folder with what was deleted, and an index that says where each thing came from, so it can go back.
 * Things stay [KEEP_DAYS] days. The index is read once and kept, and written beside and moved over, so a cut write never loses it.
 * Pure but for the folder it lives in.
 */
class FileTrash(private val dir: File, private val clock: () -> Long = System::currentTimeMillis) {
    private val index get() = File(dir, "index.json")
    private var cache: MutableList<Trashed>? = null

    init { dir.mkdirs() }

    @Synchronized
    private fun items(): MutableList<Trashed> = cache ?: runCatching {
        val array = JSONArray(index.takeIf { it.exists() }?.readText() ?: "[]")
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Trashed(o.getString("id"), o.getString("name"), o.getString("original"), o.getLong("deleted"), o.optLong("size"))
        }.toMutableList()
    }.getOrDefault(ArrayList()).also { cache = it }

    /** What is in the trash, the newest first; a thing whose file is gone is not listed. */
    @Synchronized
    fun list(): List<Trashed> = items().filter { File(dir, it.id).exists() }.sortedByDescending { it.deleted }

    @Synchronized
    private fun save() {
        val array = JSONArray()
        items().forEach { array.put(JSONObject().put("id", it.id).put("name", it.name).put("original", it.original).put("deleted", it.deleted).put("size", it.size)) }
        val part = File(dir, "index.json.part")
        part.writeText(array.toString())
        java.nio.file.Files.move(part.toPath(), index.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    /** Throws [file] into the trash: renamed into it when it is on the same volume, copied and deleted when it is not. False when it could not be moved. */
    @Synchronized
    fun put(file: File, progress: Progress = Progress()): Boolean {
        val id = UUID.randomUUID().toString()
        val size = FileOps.size(file)
        val target = File(dir, id)
        if (!file.renameTo(target)) {
            // Another volume: copied under its id, and the original deleted only when everything arrived.
            val stage = File(dir, ".in-$id")
            val before = progress.failed
            val copied = FileOps.copy(listOf(file), stage.also { it.mkdirs() }, Conflict.Replace, progress).singleOrNull()
            if (copied == null || progress.failed != before || !copied.renameTo(target)) { FileOps.delete(stage); FileOps.delete(target); return false }
            FileOps.delete(stage)
            if (!FileOps.delete(file)) { FileOps.delete(target); return false }
        }
        items() += Trashed(id, file.name, file.absolutePath, clock(), size)
        save()
        return true
    }

    /** Puts [item] back where it was (under another name if that is taken; in [fallback] if its folder is gone). The file it became. */
    @Synchronized
    fun restore(item: Trashed, fallback: File): File? {
        val source = File(dir, item.id).takeIf { it.exists() } ?: return null
        val original = File(item.original)
        val folder = original.parentFile?.takeIf { it.isDirectory } ?: fallback.also { it.mkdirs() }
        val target = FileOps.uniqueFile(folder, item.name)
        if (!source.renameTo(target)) {
            val stage = File(dir, ".out-${item.id}").also { it.mkdirs() }
            val made = FileOps.copy(listOf(source), stage, Conflict.Replace, Progress()).firstOrNull()
            val ok = made != null && made.renameTo(target)
            FileOps.delete(stage)
            if (!ok) { FileOps.delete(target); return null }
            FileOps.delete(source)
        }
        items().removeAll { it.id == item.id }
        save()
        return target
    }

    @Synchronized
    fun purge(item: Trashed) {
        FileOps.delete(File(dir, item.id))
        items().removeAll { it.id == item.id }
        save()
    }

    @Synchronized
    fun empty() {
        items().forEach { FileOps.delete(File(dir, it.id)) }
        items().clear()
        save()
    }

    /** Takes out what has been here more than [KEEP_DAYS] days. */
    @Synchronized
    fun purgeOld() {
        val limit = clock() - KEEP_DAYS * 24L * 3_600_000L
        val old = items().filter { it.deleted < limit }
        if (old.isEmpty()) return
        old.forEach { FileOps.delete(File(dir, it.id)) }
        items().removeAll(old.toSet())
        save()
    }

    companion object {
        const val KEEP_DAYS = 30
    }
}
