package com.metrowin.launcher.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** What of a backup can be put back: the Start screens with their shortcuts, the settings, or only how the launcher looks. */
enum class RestorePart { Start, Settings, Theme }

/** The files of the backups kept in the backup folder, named by the minute they were made. Pure, so the names and what to let go are tested. */
object BackupCopies {
    private val FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
    private const val PREFIX = "metrowin-layout-"
    private const val SUFFIX = ".json"

    fun name(at: LocalDateTime): String = PREFIX + FORMAT.format(at) + SUFFIX

    /** When a file of a copy was made, or null when [name] is not the name of one. */
    fun parse(name: String): LocalDateTime? {
        if (!name.startsWith(PREFIX) || !name.endsWith(SUFFIX)) return null
        return runCatching { LocalDateTime.parse(name.removePrefix(PREFIX).removeSuffix(SUFFIX), FORMAT) }.getOrNull()
    }

    /** The copies among [names], the newest first. */
    fun newestFirst(names: Collection<String>): List<String> = names.filter { parse(it) != null }.distinct().sortedDescending()

    /** The copies to let go so that no more than [keep] stay: the oldest ones. */
    fun excess(names: Collection<String>, keep: Int): List<String> = newestFirst(names).drop(keep.coerceAtLeast(1))
}
