package com.metrowin.launcher.files

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A line of a folder as the file manager lists it. */
data class Entry(val file: File, val isDir: Boolean, val size: Long, val modified: Long, val hidden: Boolean, val opened: Long = 0L) {
    val name: String get() = file.name
    val extension: String get() = if (isDir) "" else file.extension.lowercase(Locale.ROOT)
}

/** [Date] is the date of the last change (the newest first when descending); [Opened] is when the file manager last opened the file. */
enum class SortKey { Name, Date, Size, Type, Opened }

/** [natural] puts "file2" before "file10" (digits compared as numbers) instead of letter by letter. */
data class Sort(val key: SortKey = SortKey.Name, val descending: Boolean = false, val natural: Boolean = true)

/** The archives the file manager makes. RAR is not here: nobody can make one without the closed tool of its owner. */
enum class ArchiveFormat(val extension: String) { Zip("zip"), SevenZ("7z") }

/** What to do with a file that is already where another is going. */
enum class Conflict { Skip, Replace, KeepBoth }

/** Why one file could not be dealt with; the screen turns it into words. */
enum class Why { Denied, Missing, NoSpace, Outside, Other }

/** A file that was not dealt with, and why. */
class Failure(val name: String, val why: Why)

/** The progress of a long operation, read by the screen while another thread works; [cancel] stops it at the next file. */
class Progress {
    @Volatile var totalBytes: Long = 0L
    @Volatile var doneBytes: Long = 0L
    @Volatile var current: String = ""
    @Volatile var cancelled: Boolean = false
    @Volatile var failed: Int = 0
    /** Why an archive was refused: [FileOps.ENCRYPTED] or [FileOps.DAMAGED]; 0 when it was not. */
    @Volatile var reason: Int = 0
    /** True when [doneBytes] and [totalBytes] count files (a deletion) and not bytes. */
    @Volatile var byCount: Boolean = false
    /** The first hundred things that failed, with the reason. */
    val failures: MutableList<Failure> = java.util.Collections.synchronizedList(ArrayList())
    /** What a copy or a move has done, as source to destination, so it can be undone. */
    val results: MutableList<Pair<File, File>> = java.util.Collections.synchronizedList(ArrayList())
    fun cancel() { cancelled = true }
    fun fail(name: String, why: Why = Why.Other) { failed++; if (failures.size < 100) failures += Failure(name, why) }
}

/**
 * What the file manager does to files, on plain `java.io.File`. Nothing here knows Android, so every rule (a folder cannot be copied into
 * itself, an archive cannot write outside its folder, a name cannot hold a slash) is tested on a real folder.
 */
object FileOps {
    private const val BUFFER = 64 * 1024

    // --- looking

    /** The entries of [dir], sorted; null when it cannot be read. */
    fun list(dir: File, showHidden: Boolean, sort: Sort = Sort(), opened: Map<String, Long> = emptyMap()): List<Entry>? {
        val files = dir.listFiles() ?: return null
        return files.filter { showHidden || !it.name.startsWith(".") }
            .map { Entry(it, it.isDirectory, if (it.isDirectory) 0L else it.length(), it.lastModified(), it.name.startsWith("."), opened[it.absolutePath] ?: 0L) }
            .sortedWith(comparator(sort))
    }

    /** Folders first; then by the key. A descending order reverses the order of the files and of the folders, not their separation. */
    fun comparator(sort: Sort): Comparator<Entry> {
        val names: Comparator<String> = if (sort.natural) NATURAL else String.CASE_INSENSITIVE_ORDER
        val byKey: Comparator<Entry> = when (sort.key) {
            SortKey.Name -> compareBy(names) { it.name }
            SortKey.Date -> compareBy { it.modified }
            SortKey.Size -> compareBy<Entry> { it.size }.thenBy(names) { it.name }
            SortKey.Type -> compareBy<Entry> { it.extension }.thenBy(names) { it.name }
            SortKey.Opened -> compareBy<Entry> { it.opened }.thenBy(names) { it.name }
        }
        val ordered = if (sort.descending) byKey.reversed() else byKey
        return compareByDescending<Entry> { it.isDir }.then(ordered)
    }

    /** Names in the order a person expects: "file2" before "file10", capitals and small letters alike; a tie is broken by the plain order. */
    val NATURAL: Comparator<String> = Comparator { a, b ->
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val x = a[i]
            val y = b[j]
            if (x in '0'..'9' && y in '0'..'9') {
                var ie = i; while (ie < a.length && a[ie] in '0'..'9') ie++
                var je = j; while (je < b.length && b[je] in '0'..'9') je++
                val na = a.substring(i, ie).trimStart('0')
                val nb = b.substring(j, je).trimStart('0')
                if (na.length != nb.length) return@Comparator na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return@Comparator c
                i = ie; j = je
            } else {
                val c = x.lowercaseChar().compareTo(y.lowercaseChar())
                if (c != 0) return@Comparator c
                i++; j++
            }
        }
        val rest = (a.length - i) - (b.length - j)
        if (rest != 0) rest else a.compareTo(b)
    }

    /** [log] with [path] opened at [now]; when it holds more than [cap] files the ones opened longest ago are dropped. */
    fun withOpened(log: Map<String, Long>, path: String, now: Long, cap: Int = 300): Map<String, Long> {
        val all = log + (path to now)
        return if (all.size <= cap) all else all.entries.sortedByDescending { it.value }.take(cap).associate { it.key to it.value }
    }

    /** The total size of [file] (a folder's is the sum of its files). Stops early with what it has when [progress] is cancelled. */
    fun size(file: File, progress: Progress? = null): Long {
        if (progress?.cancelled == true) return 0L
        if (!file.isDirectory) return file.length()
        return file.listFiles().orEmpty().sumOf { if (isLink(it)) 0L else size(it, progress) }
    }

    fun countFiles(file: File): Int = if (!file.isDirectory) 1 else 1 + file.listFiles().orEmpty().sumOf { countFiles(it) }

    /**
     * Files and folders under [root] whose name has [query] in it (or, with [content], a text file of at most 1 MB that has it inside), at most
     * [limit], breadth first so the nearest come first. [active] is asked now and then: when it says false the walk stops with what it has.
     */
    fun search(root: File, query: String, showHidden: Boolean, limit: Int = 200, progress: Progress? = null, content: Boolean = false, active: () -> Boolean = { true }): List<File> {
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) return emptyList()
        val found = ArrayList<File>()
        val queue = ArrayDeque<File>().apply { add(root) }
        while (queue.isNotEmpty() && found.size < limit && progress?.cancelled != true && active()) {
            val dir = queue.removeFirst()
            for (child in dir.listFiles().orEmpty().sortedBy { it.name.lowercase(Locale.ROOT) }) {
                if (!showHidden && child.name.startsWith(".")) continue
                if (!active()) break
                if (child.name.lowercase(Locale.ROOT).contains(needle) || (content && inText(child, needle))) found += child
                if (found.size >= limit) break
                if (child.isDirectory && !isLink(child)) queue.add(child)
            }
        }
        return found
    }

    /** Whether the text file [file] (at most 1 MB) has [needle] (in small letters) in it. */
    private fun inText(file: File, needle: String): Boolean =
        isText(file) && file.length() in 1..1_000_000L && runCatching { file.readText().lowercase(Locale.ROOT).contains(needle) }.getOrDefault(false)

    fun isLink(file: File): Boolean = runCatching { java.nio.file.Files.isSymbolicLink(file.toPath()) }.getOrDefault(false)

    /** Whether [child] is [parent] or lies below it. */
    fun isInside(child: File, parent: File): Boolean {
        val c = runCatching { child.canonicalFile }.getOrDefault(child.absoluteFile)
        val p = runCatching { parent.canonicalFile }.getOrDefault(parent.absoluteFile)
        return c == p || c.path.startsWith(p.path + File.separator)
    }

    // --- names

    /** A name that can be given to a file: not empty, no slash, not "." or "..", at most 255 bytes, no control character. */
    fun validName(name: String): Boolean {
        val n = name.trim()
        return n.isNotEmpty() && n != "." && n != ".." && '/' !in n && '\u0000' !in n && n.none { it.isISOControl() } && n.toByteArray().size <= 255
    }

    /** [name] in [dir], or "name (1).ext", "name (2).ext"… when it is taken. */
    fun uniqueFile(dir: File, name: String): File {
        var target = File(dir, name)
        if (!target.exists()) return target
        val dot = name.lastIndexOf('.').takeIf { it > 0 }
        val stem = if (dot == null) name else name.substring(0, dot)
        val ext = if (dot == null) "" else name.substring(dot)
        var n = 1
        while (target.exists()) target = File(dir, "$stem ($n)$ext").also { n++ }
        return target
    }

    fun rename(file: File, newName: String): File? {
        val name = newName.trim()
        if (!validName(name) || name == file.name) return null
        val target = File(file.parentFile ?: return null, name)
        if (target.exists()) return null
        return if (file.renameTo(target)) target else null
    }

    fun mkdir(dir: File, name: String): File? {
        val n = name.trim()
        if (!validName(n)) return null
        val target = File(dir, n)
        return if (!target.exists() && target.mkdirs()) target else null
    }

    fun createFile(dir: File, name: String): File? {
        val n = name.trim()
        if (!validName(n)) return null
        val target = File(dir, n)
        return if (!target.exists() && runCatching { target.createNewFile() }.getOrDefault(false)) target else null
    }

    // --- changing

    /** The files of [sources] that would be overwritten by being put in [targetDir]. */
    fun conflicts(sources: List<File>, targetDir: File): List<File> = sources.filter { File(targetDir, it.name).exists() }

    /** The bytes and the files of [sources], for a progress bar. */
    fun measure(sources: List<File>): Long = sources.sumOf { size(it) }

    /**
     * Copies [sources] into [targetDir]. A folder is copied with everything in it; a folder is never copied into itself; a file that is
     * already there is treated by [conflict]. Returns the files made. Whatever cannot be copied is counted in [Progress.failed].
     */
    fun copy(sources: List<File>, targetDir: File, conflict: Conflict, progress: Progress): List<File> = copyAll(sources, targetDir, conflict, progress, true)

    private fun copyAll(sources: List<File>, targetDir: File, conflict: Conflict, progress: Progress, record: Boolean): List<File> {
        val made = ArrayList<File>()
        for (source in sources) {
            if (progress.cancelled) break
            if (source.isDirectory && isInside(targetDir, source)) { progress.fail(source.name, Why.Outside); continue }
            copyOne(source, targetDir, conflict, progress)?.let { made += it; if (record) progress.results += source to it }
        }
        return made
    }

    private fun copyOne(source: File, targetDir: File, conflict: Conflict, progress: Progress): File? {
        if (progress.cancelled) return null
        var target = File(targetDir, source.name)
        if (target.exists()) {
            // A file onto itself is no copy at all: with "keep both" it becomes "name (1)", otherwise nothing is done.
            when (conflict) {
                Conflict.Skip -> { progress.doneBytes += size(source); return null }
                Conflict.KeepBoth -> target = uniqueFile(targetDir, source.name)
                Conflict.Replace -> {
                    if (runCatching { source.canonicalFile == target.canonicalFile }.getOrDefault(false)) return null
                    if (source.isDirectory != target.isDirectory) delete(target)
                }
            }
        }
        progress.current = source.name
        return runCatching {
            if (source.isDirectory) {
                if (!target.exists() && !target.mkdirs()) throw IOException("mkdir")
                for (child in source.listFiles().orEmpty()) {
                    if (progress.cancelled) break
                    // Links are not followed: a link that points up would copy the disk.
                    if (isLink(child)) continue
                    copyOne(child, target, if (conflict == Conflict.KeepBoth) Conflict.Replace else conflict, progress)
                }
            } else {
                // Written beside the target and moved over it at the end, so a failure or a cancel never leaves a cut file where a good one was.
                val part = File(targetDir, ".part-${System.nanoTime()}")
                try {
                    source.inputStream().use { input -> part.outputStream().use { output -> pipe(input, output, progress) } }
                    part.setLastModified(source.lastModified())
                    java.nio.file.Files.move(part.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                } finally {
                    part.delete()
                }
            }
            target
        }.getOrElse { if (!progress.cancelled) progress.fail(source.name, whyOf(it)); null }
    }

    /** The reason an operation threw, as the screen can put it in words. */
    fun whyOf(e: Throwable): Why {
        val text = e.message.orEmpty()
        return when {
            e is java.nio.file.AccessDeniedException || text.contains("denied", true) || text.contains("EACCES") || text.contains("EPERM") || text.contains("Read-only", true) -> Why.Denied
            e is java.nio.file.NoSuchFileException || text.contains("No such file", true) || text.contains("ENOENT") -> Why.Missing
            text.contains("No space", true) || text.contains("ENOSPC") -> Why.NoSpace
            text.contains("outside") -> Why.Outside
            else -> Why.Other
        }
    }

    private fun pipe(input: java.io.InputStream, output: java.io.OutputStream, progress: Progress) {
        val buffer = ByteArray(BUFFER)
        while (true) {
            if (progress.cancelled) throw IOException("cancelled")
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            progress.doneBytes += read
        }
    }

    /** Moves [sources] into [targetDir]: by renaming when the two are on the same volume, by copying and deleting when they are not. */
    fun move(sources: List<File>, targetDir: File, conflict: Conflict, progress: Progress): List<File> {
        val made = ArrayList<File>()
        for (source in sources) {
            if (progress.cancelled) break
            if (source.parentFile?.canonicalFile == targetDir.canonicalFile) continue
            if (source.isDirectory && isInside(targetDir, source)) { progress.fail(source.name, Why.Outside); continue }
            val target = File(targetDir, source.name)
            val taken = target.exists()
            if (taken && conflict == Conflict.Skip) { progress.doneBytes += size(source); continue }
            val destination = if (taken && conflict == Conflict.KeepBoth) uniqueFile(targetDir, source.name) else target
            // What is replaced is set aside first and deleted only when the move has worked; if it fails it goes back.
            var aside: File? = null
            if (taken && conflict == Conflict.Replace) {
                aside = File(targetDir, ".old-${System.nanoTime()}")
                if (!target.renameTo(aside)) { progress.fail(source.name, Why.Denied); continue }
            }
            progress.current = source.name
            if (source.renameTo(destination)) {
                aside?.let { delete(it) }
                progress.doneBytes += size(destination)
                made += destination
                progress.results += source to destination
            } else {
                // Not the same volume: copy, and delete the original only when everything arrived.
                val before = progress.failed
                val copied = copyAll(listOf(source), targetDir, if (taken && conflict == Conflict.KeepBoth) Conflict.KeepBoth else Conflict.Replace, progress, false)
                if (copied.isNotEmpty() && progress.failed == before && !progress.cancelled) {
                    if (!delete(source, progress)) progress.fail(source.name, Why.Denied)
                    aside?.let { delete(it) }; made += copied; progress.results += source to copied.first()
                } else if (aside != null) {
                    delete(target); aside.renameTo(target)
                }
            }
        }
        return made
    }

    /**
     * Deletes [file] and what is in it (a link is deleted, never followed). False when something stayed: each thing that could not be
     * deleted is counted in [progress], and a cancel stops at the next file. A folder with something left in it is not tried.
     */
    fun delete(file: File, progress: Progress? = null): Boolean {
        if (progress?.cancelled == true) return false
        if (file.isDirectory && !isLink(file)) {
            var clean = true
            for (child in file.listFiles().orEmpty()) if (!delete(child, progress)) clean = false
            if (!clean) return false
        }
        if (file.delete()) return true
        if (file.exists()) progress?.fail(file.name, Why.Denied)
        return !file.exists()
    }

    // --- archives

    /** Makes a zip of [sources] in [target]. The names inside are relative to the folder of each source. False when it could not be written. */
    fun zip(sources: List<File>, target: File, progress: Progress): Boolean = runCatching {
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            fun add(file: File, path: String) {
                if (progress.cancelled) throw IOException("cancelled")
                // The archive being written is not put in itself.
                if (runCatching { file.canonicalFile == target.canonicalFile }.getOrDefault(false)) return
                if (file.isDirectory) {
                    zip.putNextEntry(ZipEntry("$path/").apply { time = file.lastModified() })
                    zip.closeEntry()
                    file.listFiles().orEmpty().filter { !isLink(it) }.forEach { add(it, "$path/${it.name}") }
                } else {
                    progress.current = file.name
                    zip.putNextEntry(ZipEntry(path).apply { time = file.lastModified() })
                    file.inputStream().use { pipe(it, zip, progress) }
                    zip.closeEntry()
                }
            }
            sources.forEach { add(it, it.name) }
        }
        true
    }.getOrElse { target.delete(); false }

    /** Writes what comes out of an archive under [root]: refuses what leaves it, stops a bomb, treats a taken name by [conflict], and takes back what it wrote when it fails. */
    private class Extractor(root: File, val progress: Progress, val maxBytes: Long, val conflict: Conflict) {
        val root: File = root.canonicalFile
        private val files = ArrayList<File>()
        private val dirs = ArrayList<File>()
        var written = 0
        var entries = 0
        private var total = 0L

        private fun place(name: String): File {
            val out = File(root, name).canonicalFile
            if (out != root && !out.path.startsWith(root.path + File.separator)) throw IOException("outside the folder")
            return out
        }

        fun dir(name: String) {
            entries++
            val d = place(name)
            var up: File? = d
            while (up != null && up != root && !up.exists()) { dirs += up; up = up.parentFile }
            d.mkdirs()
        }

        /** Writes one file of the archive; [input] is read to its end (or to the cancel). */
        fun file(name: String, modified: Long, input: java.io.InputStream) {
            entries++
            var out = place(name)
            if (out.exists() && !out.isDirectory) {
                when (conflict) {
                    Conflict.Skip -> { copyOut(input, null); return }
                    Conflict.KeepBoth -> out = uniqueFile(out.parentFile, out.name)
                    Conflict.Replace -> {}
                }
            }
            var up: File? = out.parentFile
            while (up != null && up != root && !up.exists()) { dirs += up; up = up.parentFile }
            out.parentFile?.mkdirs()
            progress.current = out.name
            val existed = out.exists()
            // Written beside and moved over at the end, so a cut file never takes the place of a good one.
            val part = File(out.parentFile, ".part-${System.nanoTime()}")
            try {
                part.outputStream().use { copyOut(input, it) }
                if (modified > 0) part.setLastModified(modified)
                java.nio.file.Files.move(part.toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } finally {
                part.delete()
            }
            if (!existed) files += out
            written++
        }

        private fun copyOut(input: java.io.InputStream, output: java.io.OutputStream?) {
            val buffer = ByteArray(BUFFER)
            while (true) {
                if (progress.cancelled) throw IOException("cancelled")
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) throw IOException("too big")
                output?.write(buffer, 0, read)
                progress.doneBytes += read
            }
        }

        /** Deletes the files and the folders this extraction made (a folder that holds something else stays). */
        fun rollback() {
            files.forEach { it.delete() }
            dirs.sortedByDescending { it.path.length }.forEach { it.delete() }
        }
    }

    /**
     * Extracts [zip] into [targetDir]. An entry whose name would put the file outside [targetDir] ("../x") is refused, as is an
     * archive that grows past [maxBytes] (a bomb); a file that is already there is treated by [conflict] (by default kept beside, never
     * lost). When it fails or is cancelled what it wrote is taken away again. Returns the number of files written, or -1 when the archive
     * is refused or damaged.
     */
    fun unzip(zip: File, targetDir: File, progress: Progress, maxBytes: Long = MAX_EXTRACT, conflict: Conflict = Conflict.KeepBoth): Int {
        val extractor = Extractor(targetDir, progress, maxBytes, conflict)
        return runCatching {
            ZipFile.builder().setFile(zip).get().use { z ->
                for (entry in z.entries) {
                    if (progress.cancelled) throw IOException("cancelled")
                    val name = zipName(entry)
                    if (entry.isDirectory) extractor.dir(name)
                    else z.getInputStream(entry).use { extractor.file(name, entry.time, it) }
                }
            }
            // A file with no entry at all is not an archive (or has nothing in it).
            if (extractor.entries == 0) throw IOException("empty")
            extractor.written
        }.getOrElse { extractor.rollback(); DAMAGED }
    }

    /**
     * The name of a zip entry: as UTF-8 when the archive says so or the bytes are valid UTF-8, else as the old DOS code page (what
     * Windows writes without a flag), so a name with an accent is not turned into marks.
     */
    fun zipName(entry: org.apache.commons.compress.archivers.zip.ZipArchiveEntry): String {
        val raw = entry.rawName ?: return entry.name
        if (entry.generalPurposeBit.usesUTF8ForNames()) return String(raw, Charsets.UTF_8)
        val strict = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        return runCatching { strict.decode(java.nio.ByteBuffer.wrap(raw)).toString() }.getOrElse { String(raw, charset("Cp437")) }
    }

    // --- 7z

    /** Makes a 7z of [sources] in [target] (names as in [zip]). False when it could not be written. */
    fun sevenZip(sources: List<File>, target: File, progress: Progress): Boolean = runCatching {
        SevenZOutputFile(target).use { out ->
            fun add(file: File, path: String) {
                if (progress.cancelled) throw IOException("cancelled")
                if (runCatching { file.canonicalFile == target.canonicalFile }.getOrDefault(false)) return
                if (file.isDirectory) {
                    out.putArchiveEntry(out.createArchiveEntry(file, "$path/"))
                    out.closeArchiveEntry()
                    file.listFiles().orEmpty().filter { !isLink(it) }.forEach { add(it, "$path/${it.name}") }
                } else {
                    progress.current = file.name
                    out.putArchiveEntry(out.createArchiveEntry(file, path))
                    file.inputStream().use { input ->
                        val buffer = ByteArray(BUFFER)
                        while (true) {
                            if (progress.cancelled) throw IOException("cancelled")
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            progress.doneBytes += read
                        }
                    }
                    out.closeArchiveEntry()
                }
            }
            sources.forEach { add(it, it.name) }
        }
        true
    }.getOrElse { target.delete(); false }

    /** Extracts a 7z with the same guards as [unzip]. Returns the files written, [DAMAGED], or [ENCRYPTED] when it needs a password. */
    fun un7z(archive: File, targetDir: File, progress: Progress, maxBytes: Long = MAX_EXTRACT, conflict: Conflict = Conflict.KeepBoth): Int {
        val extractor = Extractor(targetDir, progress, maxBytes, conflict)
        return runCatching {
            SevenZFile.builder().setFile(archive).get().use { input ->
                val stream = object : java.io.InputStream() {
                    override fun read(): Int = input.read()
                    override fun read(b: ByteArray, off: Int, len: Int): Int = input.read(b, off, len)
                }
                while (true) {
                    if (progress.cancelled) throw IOException("cancelled")
                    val entry = input.nextEntry ?: break
                    if (entry.isDirectory) { extractor.dir(entry.name); continue }
                    // An anti item marks a file as deleted in an update: there is nothing to write.
                    if (entry.isAntiItem) continue
                    extractor.file(entry.name, if (entry.hasLastModifiedDate) entry.lastModifiedDate.time else 0L, stream)
                }
            }
            if (extractor.entries == 0) throw IOException("empty")
            extractor.written
        }.getOrElse { extractor.rollback(); if (it is PasswordRequiredException) ENCRYPTED else DAMAGED }
    }

    /** One thing in an archive, for a list that shows what is inside before it is extracted. */
    class ArchiveItem(val name: String, val size: Long, val isDir: Boolean)

    /** What a zip or a 7z holds, at most [limit] entries; null when it cannot be read (or needs a password to be listed). */
    fun listArchive(archive: File, limit: Int = 5000): List<ArchiveItem>? = runCatching {
        if (is7z(archive)) SevenZFile.builder().setFile(archive).get().use { z -> z.entries.asSequence().take(limit).map { ArchiveItem(it.name, it.size, it.isDirectory) }.toList() }
        else ZipFile.builder().setFile(archive).get().use { z -> z.entries.asSequence().take(limit).map { ArchiveItem(zipName(it), it.size, it.isDirectory) }.toList() }
    }.getOrNull()

    /** Extracts the one entry called [name] of [archive] into [targetDir] (kept beside what is there); the file written, or null. */
    fun extractOne(archive: File, name: String, targetDir: File, progress: Progress = Progress()): File? = runCatching {
        val extractor = Extractor(targetDir, progress, MAX_EXTRACT, Conflict.KeepBoth)
        var made: File? = null
        val before = targetDir.listFiles().orEmpty().toSet()
        if (is7z(archive)) SevenZFile.builder().setFile(archive).get().use { z ->
            val stream = object : java.io.InputStream() {
                override fun read(): Int = z.read()
                override fun read(b: ByteArray, off: Int, len: Int): Int = z.read(b, off, len)
            }
            while (true) {
                val e = z.nextEntry ?: break
                if (!e.isDirectory && e.name == name) { extractor.file(e.name.substringAfterLast('/'), if (e.hasLastModifiedDate) e.lastModifiedDate.time else 0L, stream); break }
            }
        } else ZipFile.builder().setFile(archive).get().use { z ->
            val e = z.entries.asSequence().firstOrNull { !it.isDirectory && zipName(it) == name } ?: return@use
            z.getInputStream(e).use { extractor.file(zipName(e).substringAfterLast('/'), e.time, it) }
        }
        made = targetDir.listFiles().orEmpty().firstOrNull { it !in before }
        made
    }.getOrNull()

    // --- reading archives

    /** Whether a zip has an entry that is encrypted (the library of Java reads such an entry as noise). */
    fun zipEncrypted(zip: File): Boolean = runCatching {
        ZipFile.builder().setFile(zip).get().use { z -> z.entries.asSequence().any { it.generalPurposeBit.usesEncryption() } }
    }.getOrDefault(false)

    const val DAMAGED = -1
    const val ENCRYPTED = -2
    private const val MAX_EXTRACT = 64L * 1024 * 1024 * 1024

    /** Extracts a zip or a 7z into [targetDir], and says in [progress] why when it cannot. */
    fun extract(archive: File, targetDir: File, progress: Progress, conflict: Conflict = Conflict.KeepBoth): Int {
        val result = if (is7z(archive)) un7z(archive, targetDir, progress, conflict = conflict)
        else unzip(archive, targetDir, progress, conflict = conflict).let { if (it == DAMAGED && zipEncrypted(archive)) ENCRYPTED else it }
        if (result < 0) progress.reason = result
        return result
    }

    fun is7z(file: File): Boolean = !file.isDirectory && file.extension.lowercase(Locale.ROOT) == "7z"

    /** Whether the file manager can extract [file]: a zip, or a 7z (a RAR is not: its library is not free software). */
    fun isArchive(file: File): Boolean = isZip(file) && !file.name.endsWith(".apk", true) || is7z(file)

    // --- reading

    /** The hex digest of [file] with [algorithm] ("MD5", "SHA-1", "SHA-256"); null when it cannot be read. */
    fun checksum(file: File, algorithm: String, progress: Progress? = null): String? = checksums(file, listOf(algorithm), progress)?.get(algorithm)

    /** The digests of [file] with all of [algorithms], in one reading of the file; null when it cannot be read or [progress] is cancelled. */
    fun checksums(file: File, algorithms: List<String> = listOf("MD5", "SHA-1", "SHA-256"), progress: Progress? = null): Map<String, String>? = runCatching {
        val digests = algorithms.associateWith { MessageDigest.getInstance(it) }
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                if (progress?.cancelled == true) return null
                val read = input.read(buffer)
                if (read < 0) break
                digests.values.forEach { it.update(buffer, 0, read) }
                progress?.let { it.doneBytes += read }
            }
        }
        digests.mapValues { (_, d) -> d.digest().joinToString("") { "%02x".format(it) } }
    }.getOrNull()

    fun isText(file: File): Boolean = !file.isDirectory && file.extension.lowercase(Locale.ROOT) in FileKinds.TEXT

    fun isZip(file: File): Boolean = !file.isDirectory && file.extension.lowercase(Locale.ROOT) in FileKinds.ZIP

    /** The type of a file for "open with", from its extension; a generic one when it is not known. */
    fun mime(file: File): String =
        android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase(Locale.ROOT)) ?: "application/octet-stream"
}

/** The letter a name is listed under in a list sorted by name: its first letter in capitals, or # for a digit or a sign. */
fun letterOf(name: String): String = name.firstOrNull()?.takeIf { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"

/** [items] cut into the runs that share a letter, in the order they come: a folder list put before the files has each letter twice. */
fun <T> letterRuns(items: List<T>, name: (T) -> String): List<Pair<String, List<T>>> {
    val runs = ArrayList<Pair<String, MutableList<T>>>()
    items.forEach { item ->
        val letter = letterOf(name(item))
        if (runs.lastOrNull()?.first == letter) runs.last().second += item else runs += letter to mutableListOf(item)
    }
    return runs
}
