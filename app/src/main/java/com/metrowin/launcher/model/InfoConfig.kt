package com.metrowin.launcher.model

import androidx.compose.runtime.Immutable

/** What an info tile can say. */
enum class InfoPart { Time, Calendar, Photos }

/**
 * What an info tile shows: the time, the next events of the calendar, the latest photos, any of them or all together. With more than one
 * the tile turns from one to the next, as the other live tiles do. Kept in the shortcut as a short string ("tcp").
 */
@Immutable
data class InfoConfig(val time: Boolean = true, val calendar: Boolean = true, val photos: Boolean = true) {
    /** What is shown, in the order the tile turns through it. */
    val parts: List<InfoPart> = buildList {
        if (time) add(InfoPart.Time)
        if (calendar) add(InfoPart.Calendar)
        if (photos) add(InfoPart.Photos)
    }

    /** The data of the phone the tile needs read while it is on Start. */
    fun liveKinds(): Set<LiveKind> = buildSet {
        if (calendar) add(LiveKind.Calendar)
        if (photos) add(LiveKind.Photos)
    }

    fun encode(): String = buildString {
        if (time) append('t')
        if (calendar) append('c')
        if (photos) append('p')
    }

    /** The same with [part] switched; the last one on cannot be switched off (a tile with nothing to say is no tile). */
    fun toggled(part: InfoPart): InfoConfig {
        val next = when (part) {
            InfoPart.Time -> copy(time = !time)
            InfoPart.Calendar -> copy(calendar = !calendar)
            InfoPart.Photos -> copy(photos = !photos)
        }
        return if (next.parts.isEmpty()) this else next
    }

    companion object {
        /** An empty or unreadable string is a tile with all three. */
        fun decode(raw: String): InfoConfig {
            val config = InfoConfig('t' in raw, 'c' in raw, 'p' in raw)
            return if (config.parts.isEmpty()) InfoConfig() else config
        }
    }
}
