package com.metrowin.launcher.music

import android.content.Context
import android.media.session.PlaybackState
import androidx.core.content.edit

/** Where the song is: the position the player gave, moved on by the time since it said so (at the speed it plays), kept inside the song. Pure, so it is tested. */
fun playbackPosition(position: Long, updatedAt: Long, now: Long, speed: Float, playing: Boolean, duration: Long): Long {
    val moved = if (playing) ((now - updatedAt) * speed).toLong().coerceAtLeast(0) else 0L
    val at = (position + moved).coerceAtLeast(0)
    return if (duration > 0) at.coerceAtMost(duration) else at
}

fun mmss(millis: Long): String = "%d:%02d".format(millis / 60_000, millis / 1000 % 60)

/** The time left, as "-m:ss". */
fun remaining(at: Long, duration: Long): String = "-" + mmss((duration - at).coerceAtLeast(0))

/** Where a touch at [x] of a bar [width] wide seeks to, inside the song. */
fun seekTarget(duration: Long, x: Float, width: Float): Long =
    if (width <= 0f || duration <= 0) 0L else (duration * (x / width).coerceIn(0f, 1f)).toLong()

/**
 * What the player lets the user do. A player that says nothing at all (actions 0) is not locked out: it may well answer; one that
 * lists its actions is believed.
 */
class Abilities(private val actions: Long) {
    private fun can(flag: Long) = actions == 0L || actions and flag != 0L
    val previous get() = can(PlaybackState.ACTION_SKIP_TO_PREVIOUS)
    val next get() = can(PlaybackState.ACTION_SKIP_TO_NEXT)
    val seek get() = can(PlaybackState.ACTION_SEEK_TO)
    val jump get() = can(PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM)
    // Rating and speed are only offered when the player says so: unlike skipping, they are not guessed.
    val rate get() = actions and PlaybackState.ACTION_SET_RATING != 0L
    val speed get() = actions and PlaybackState.ACTION_SET_PLAYBACK_SPEED != 0L
}

class QueueEntry(val id: Long, val title: String, val subtitle: String)

/** A song of the queue that has no title is still a row (its subtitle, or a dash), never dropped; [filter] keeps the rows that have it in the title or the subtitle; [limit] is how many are shown. */
fun queueRows(rows: List<QueueEntry>, limit: Int, filter: String = ""): List<QueueEntry> {
    val word = filter.trim()
    return rows.filter { word.isEmpty() || it.title.contains(word, true) || it.subtitle.contains(word, true) }.take(limit)
}

val QUEUE_LIMITS = listOf(10, 30, 100)
val SLEEP_MINUTES = listOf(0, 15, 30, 60)
val SPEEDS = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)

/** The next in [list] after [current] (the first when [current] is not there), going round. */
fun <T> following(list: List<T>, current: T): T = list[(list.indexOf(current) + 1) % list.size]

class Listened(val at: Long, val title: String, val artist: String)

/** The songs heard, newest first: the same song again straight away is not twice, and only [max] are kept. */
fun addListened(list: List<Listened>, song: Listened, max: Int = 50): List<Listened> =
    if (list.firstOrNull()?.let { it.title == song.title && it.artist == song.artist } == true) list else (listOf(song) + list).take(max)

/** What the user chose for this app (the menu of the app); everything that is not plain looks is off until asked for. */
data class NpOptions(
    val coverPage: Boolean = true,
    val blur: Boolean = false,
    val accentFromCover: Boolean = false,
    val disc: Boolean = false,
    val swipe: Boolean = true,
    val volume: Boolean = true,
    val queueCovers: Boolean = true,
    val car: Boolean = false,
    val contrast: Boolean = false,
    val history: Boolean = false,
    val queueLimit: Int = 30,
)

/** The options and the history of listening, in preferences of this app only. */
class NpStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("now_playing", Context.MODE_PRIVATE)

    fun load(): NpOptions {
        val d = NpOptions()
        return NpOptions(
            prefs.getBoolean("cover_page", d.coverPage), prefs.getBoolean("blur", d.blur), prefs.getBoolean("accent", d.accentFromCover),
            prefs.getBoolean("disc", d.disc), prefs.getBoolean("swipe", d.swipe), prefs.getBoolean("volume", d.volume),
            prefs.getBoolean("queue_covers", d.queueCovers), prefs.getBoolean("car", d.car), prefs.getBoolean("contrast", d.contrast),
            prefs.getBoolean("history", d.history), prefs.getInt("queue_limit", d.queueLimit).takeIf { it in QUEUE_LIMITS } ?: d.queueLimit,
        )
    }

    fun save(o: NpOptions) = prefs.edit {
        putBoolean("cover_page", o.coverPage).putBoolean("blur", o.blur).putBoolean("accent", o.accentFromCover).putBoolean("disc", o.disc)
        putBoolean("swipe", o.swipe).putBoolean("volume", o.volume).putBoolean("queue_covers", o.queueCovers).putBoolean("car", o.car)
        putBoolean("contrast", o.contrast).putBoolean("history", o.history).putInt("queue_limit", o.queueLimit)
        // Turning the history off forgets it.
        if (!o.history) remove("listened")
    }

    fun listened(): List<Listened> = prefs.getString("listened", null).orEmpty().lines().mapNotNull { line ->
        val p = line.split('\t')
        if (p.size == 3) p[0].toLongOrNull()?.let { Listened(it, p[1], p[2]) } else null
    }

    fun saveListened(list: List<Listened>) = prefs.edit { putString("listened", list.joinToString("\n") { "${it.at}\t${it.title.flat()}\t${it.artist.flat()}" }) }

    fun forgetListened() = prefs.edit { remove("listened") }

    private fun String.flat() = replace('\t', ' ').replace('\n', ' ')
}
