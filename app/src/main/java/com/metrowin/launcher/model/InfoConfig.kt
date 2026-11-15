package com.metrowin.launcher.model

import androidx.compose.runtime.Immutable

/** What an info tile can say, in the order it turns through them. */
enum class InfoPart(val code: Char) {
    Time('t'), Calendar('c'), Alarm('a'), Weather('w'), Battery('b'), Photos('p'), Memories('m'),
}

/** How fast an info tile turns from one face to the next. */
enum class InfoPace(val code: Char, val scale: Float) { Slow('1', 1.8f), Normal('2', 1f), Fast('3', 0.55f) }

/**
 * What an info tile shows and how: the faces it turns through (the time, the next events, the next alarm, the weather, the battery, the
 * latest photos, the photos of this day in past years), and the choices of the user about them. Kept in the shortcut as a short string:
 * the letters of the faces, the letters of the switches, then `;key=value` pairs.
 */
@Immutable
data class InfoConfig(
    val time: Boolean = true,
    val calendar: Boolean = true,
    val photos: Boolean = true,
    val alarm: Boolean = false,
    val weather: Boolean = false,
    val battery: Boolean = false,
    val memories: Boolean = false,
    /** Puts first what matters now: an event or an alarm soon stays on its own, at night only the time. */
    val smart: Boolean = false,
    /** While the phone saves battery the tile shows only the time, instead of keeping its last face. */
    val saver: Boolean = false,
    /** The time zone the time face shows; empty: the phone's. */
    val zone: String = "",
    /** The album the photos come from (its id); empty: the latest photos of the phone. */
    val album: String = "",
    /** The name of that album, to show it. */
    val albumName: String = "",
    val pace: InfoPace = InfoPace.Normal,
) {
    private fun has(part: InfoPart) = when (part) {
        InfoPart.Time -> time
        InfoPart.Calendar -> calendar
        InfoPart.Alarm -> alarm
        InfoPart.Weather -> weather
        InfoPart.Battery -> battery
        InfoPart.Photos -> photos
        InfoPart.Memories -> memories
    }

    /** The faces that are on, in the order the tile turns through them. */
    val parts: List<InfoPart> = InfoPart.entries.filter(::has)

    /** The data of the phone the tile needs read while it is on Start. */
    fun liveKinds(): Set<LiveKind> = buildSet {
        if (calendar) add(LiveKind.Calendar)
        if (alarm) add(LiveKind.Alarm)
        if (photos) add(if (album.isEmpty()) LiveKind.Photos else LiveKind.Album)
        if (memories) add(LiveKind.Memories)
    }

    fun encode(): String = buildString {
        parts.forEach { append(it.code) }
        if (smart) append('S')
        if (saver) append('E')
        if (pace != InfoPace.Normal) append(";v=").append(pace.code)
        if (zone.isNotEmpty()) append(";z=").append(zone)
        if (album.isNotEmpty()) append(";a=").append(album)
        if (album.isNotEmpty() && albumName.isNotEmpty()) append(";n=").append(albumName.replace(';', ','))
    }

    fun toggled(part: InfoPart): InfoConfig {
        val next = when (part) {
            InfoPart.Time -> copy(time = !time)
            InfoPart.Calendar -> copy(calendar = !calendar)
            InfoPart.Alarm -> copy(alarm = !alarm)
            InfoPart.Weather -> copy(weather = !weather)
            InfoPart.Battery -> copy(battery = !battery)
            InfoPart.Photos -> copy(photos = !photos)
            InfoPart.Memories -> copy(memories = !memories)
        }
        // The last face on cannot be switched off: a tile with nothing to say is no tile.
        return if (next.parts.isEmpty()) this else next
    }

    companion object {
        /** An empty or unreadable string is a tile with the first three. */
        fun decode(raw: String): InfoConfig {
            val letters = raw.substringBefore(';')
            val extras = raw.substringAfter(';', "").split(';').mapNotNull { pair -> pair.substringBefore('=', "").takeIf { it.isNotEmpty() }?.let { it to pair.substringAfter('=') } }.toMap()
            val config = InfoConfig(
                time = 't' in letters, calendar = 'c' in letters, photos = 'p' in letters,
                alarm = 'a' in letters, weather = 'w' in letters, battery = 'b' in letters, memories = 'm' in letters,
                smart = 'S' in letters, saver = 'E' in letters,
                zone = extras["z"].orEmpty(), album = extras["a"].orEmpty(), albumName = extras["n"].orEmpty(),
                pace = InfoPace.entries.firstOrNull { it.code.toString() == extras["v"] } ?: InfoPace.Normal,
            )
            return if (config.parts.isEmpty()) InfoConfig(smart = config.smart, saver = config.saver, zone = config.zone, album = config.album, albumName = config.albumName, pace = config.pace) else config
        }
    }
}
