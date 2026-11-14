package com.metrowin.launcher.files

import java.io.File
import java.security.MessageDigest

/** A rename that is planned for a file and whether it can be done. */
class RenamePlan(val file: File, val newName: String, val ok: Boolean)

/**
 * What the file manager finds by walking a whole storage (the recent files, the biggest, the same file twice, the room each folder takes)
 * and the renaming of many files at once. Plain `java.io.File`, tested on real folders.
 */
object FileViews {
    /** Android/data and Android/obb hold the files of other apps: Android does not let a file manager in, and a scan has no business there. */
    fun isOtherAppsData(dir: File): Boolean = dir.parentFile?.name == "Android" && dir.name in setOf("data", "obb")

    /**
     * Calls [visit] for every file under [root], nearest first, never through a link and not in [isOtherAppsData]. [active] is asked
     * now and then and [stop] after each file: when either says so the walk ends.
     */
    fun walk(root: File, showHidden: Boolean, active: () -> Boolean = { true }, stop: () -> Boolean = { false }, visit: (File) -> Unit) {
        val queue = ArrayDeque<File>().apply { add(root) }
        while (queue.isNotEmpty() && !stop() && active()) {
            val dir = queue.removeFirst()
            for (child in dir.listFiles().orEmpty()) {
                if (stop() || !active()) return
                if (!showHidden && child.name.startsWith(".")) continue
                if (child.isDirectory) { if (!FileOps.isLink(child) && !isOtherAppsData(child)) queue.add(child); continue }
                visit(child)
            }
        }
    }

    private fun entry(file: File) = Entry(file, false, file.length(), file.lastModified(), file.name.startsWith("."))

    /** The [limit] files changed since [since] (milliseconds), the newest first. */
    fun recent(root: File, showHidden: Boolean, since: Long, limit: Int = 500, active: () -> Boolean = { true }): List<Entry> {
        val found = ArrayList<Entry>()
        walk(root, showHidden, active, stop = { found.size >= FileKinds.MAX_FOUND }) { if (it.lastModified() >= since) found += entry(it) }
        return found.sortedByDescending { it.modified }.take(limit)
    }

    /** The [limit] biggest files, the biggest first. */
    fun largest(root: File, showHidden: Boolean, limit: Int = 200, active: () -> Boolean = { true }): List<Entry> {
        val heap = java.util.PriorityQueue<Entry>(compareBy { it.size })
        walk(root, showHidden, active) { heap += entry(it); if (heap.size > limit) heap.poll() }
        return heap.sortedByDescending { it.size }
    }

    /** The size of [file] and what is in it, stopping at once (with what it has) when [active] says false. */
    fun sizeOf(file: File, active: () -> Boolean = { true }): Long {
        if (!active()) return 0L
        if (!file.isDirectory) return file.length()
        if (FileOps.isLink(file)) return 0L
        return file.listFiles().orEmpty().sumOf { sizeOf(it, active) }
    }

    /** What each thing in [dir] takes, the biggest first. */
    fun folderSizes(dir: File, showHidden: Boolean, active: () -> Boolean = { true }): List<Pair<File, Long>> =
        dir.listFiles().orEmpty().filter { showHidden || !it.name.startsWith(".") }.map { it to sizeOf(it, active) }.sortedByDescending { it.second }

    /**
     * The files that are the same file twice: equal in size, then in the first 64 KB, then in the whole. Groups are listed one after
     * the other, the group that wastes most first; at most [limit] files. Files under [minSize] bytes are left out.
     */
    fun duplicates(root: File, showHidden: Boolean, minSize: Long = 1024, limit: Int = 500, active: () -> Boolean = { true }, progress: Progress? = null): List<Entry> {
        val bySize = HashMap<Long, MutableList<File>>()
        walk(root, showHidden, active) { f ->
            val n = f.length()
            if (n >= minSize) bySize.getOrPut(n) { ArrayList() } += f
            progress?.current = f.name
        }
        val groups = ArrayList<List<File>>()
        for ((_, sameSize) in bySize) {
            if (sameSize.size < 2 || !active()) continue
            val byHead = sameSize.groupBy { hash(it, HEAD, active) }.filterKeys { it != null }.values.filter { it.size > 1 }
            for (head in byHead) head.groupBy { hash(it, Long.MAX_VALUE, active) }.filterKeys { it != null }.values.filter { it.size > 1 }.forEach { groups += it }
        }
        if (!active()) return emptyList()
        return groups.sortedByDescending { it.first().length() * (it.size - 1) }.flatMap { g -> g.sortedBy { it.lastModified() }.map(::entry) }.take(limit)
    }

    private const val HEAD = 64L * 1024

    /** The SHA-256 of the first [upTo] bytes of [file]; null when it cannot be read or [active] says stop. */
    private fun hash(file: File, upTo: Long, active: () -> Boolean): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var left = upTo
            while (left > 0) {
                if (!active()) return null
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (read < 0) break
                digest.update(buffer, 0, read)
                left -= read
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    // --- renaming many files

    /**
     * What each of [files] would be called: [template] with {name} (the name without its extension, after [find] is replaced by
     * [replace] in it) and {n} (a number from [start], with as many digits as the biggest needs), and the extension kept. A plan is not
     * ok when its name is not allowed, is given twice, or is taken by a file that is not one of [files].
     */
    fun renamePlan(files: List<File>, template: String, find: String = "", replace: String = "", start: Int = 1): List<RenamePlan> {
        val width = (start + files.size - 1).toString().length
        val names = files.mapIndexed { i, f ->
            val stem = f.nameWithoutExtension.let { if (find.isEmpty()) it else it.replace(find, replace, ignoreCase = true) }
            val base = template.replace("{name}", stem).replace("{n}", (start + i).toString().padStart(width, '0'))
            base + if (f.extension.isEmpty() || f.isDirectory) "" else "." + f.extension
        }
        val own = files.map { it.absolutePath }.toSet()
        val taken = names.groupingBy { it }.eachCount()
        return files.mapIndexed { i, f ->
            val name = names[i]
            val clash = taken.getValue(name) > 1 || File(f.parentFile, name).let { it.exists() && it.absolutePath !in own }
            RenamePlan(f, name, FileOps.validName(name) && !clash)
        }
    }

    /** Does the renames of [plans] that are ok. All go through a name of their own first, so a swap (a to b, b to a) works; if one cannot be made all are put back. The number renamed. */
    fun applyRename(plans: List<RenamePlan>): Int {
        val todo = plans.filter { it.ok && it.newName != it.file.name }
        val temps = ArrayList<Pair<RenamePlan, File>>()
        todo.forEachIndexed { i, p ->
            val temp = File(p.file.parentFile, ".rn-${System.nanoTime()}-$i")
            if (p.file.renameTo(temp)) temps += p to temp
            else { temps.forEach { (q, t) -> t.renameTo(q.file) }; return 0 }
        }
        var done = 0
        for ((p, temp) in temps) if (temp.renameTo(File(p.file.parentFile, p.newName))) done++ else temp.renameTo(p.file)
        return done
    }
}
