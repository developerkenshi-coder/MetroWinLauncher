package com.metrowin.launcher.music

import java.text.Normalizer
import java.util.Locale

/** One song of the phone, as MediaStore lists it. [albumArtist] is empty when the file has none (then the artist stands for it). */
data class Track(
    val id: Long,
    val title: String,
    val artist: String = "",
    val albumArtist: String = "",
    val album: String = "",
    val albumId: Long = 0L,
    val durationMs: Long = 0L,
    /** The track number as stored: 1005 is the fifth track of disc 1 (see [number]). */
    val trackNo: Int = 0,
    val year: Int = 0,
    val addedAt: Long = 0L,
) {
    /** The track number inside its disc. */
    val number: Int get() = trackNo % 1000

    /** The artist an album is filed under: the album artist when there is one (a compilation is "Various Artists", not forty names). */
    val owner: String get() = albumArtist.ifBlank { artist }
}

/** An album: the songs that share a name and an owner, in disc and track order. */
class MusicAlbum(val name: String, val artist: String, val albumId: Long, val tracks: List<Track>) {
    val key: String get() = "$name\u0000$artist"
    val year: Int get() = tracks.maxOfOrNull { it.year } ?: 0
    val durationMs: Long get() = tracks.sumOf { it.durationMs }
}

class MusicArtist(val name: String, val albums: Int, val tracks: Int)

/** What the Music hub shows from the list of songs: pure, so it is tested without a phone. A name that is empty is shown as "Unknown" by the screen. */
object MusicLibrary {
    private fun fold(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

    /** Names sort without case or accents, and the empty (unknown) one goes last. */
    private val byName = Comparator<String> { a, b ->
        when {
            a.isEmpty() && b.isEmpty() -> 0
            a.isEmpty() -> 1
            b.isEmpty() -> -1
            else -> fold(a).compareTo(fold(b))
        }
    }

    private val inAlbumOrder = compareBy<Track>({ it.trackNo / 1000 }, { it.number == 0 }, { it.number }, { fold(it.title) })

    fun albums(tracks: List<Track>): List<MusicAlbum> = tracks.groupBy { it.album to it.owner }
        .map { (key, list) -> MusicAlbum(key.first, key.second, list.firstOrNull { it.albumId != 0L }?.albumId ?: 0L, list.sortedWith(inAlbumOrder)) }
        .sortedWith(compareBy(byName) { a: MusicAlbum -> a.artist }.thenComparing(compareBy(byName) { a: MusicAlbum -> a.name }))

    fun artists(tracks: List<Track>): List<MusicArtist> = albums(tracks).groupBy { it.artist }
        .map { (name, albums) -> MusicArtist(name, albums.size, albums.sumOf { it.tracks.size }) }
        .sortedWith(compareBy(byName) { a: MusicArtist -> a.name })

    fun albumsOf(tracks: List<Track>, artist: String): List<MusicAlbum> = albums(tracks).filter { it.artist == artist }

    fun songs(tracks: List<Track>): List<Track> = tracks.sortedWith(compareBy(byName) { t: Track -> t.title })

    /** The songs added last, newest first. */
    fun recent(tracks: List<Track>, count: Int = 50): List<Track> = tracks.sortedByDescending { it.addedAt }.take(count)

    /** The songs whose title, artist or album holds every word of [query] (no case, no accents); an empty query is everything. */
    fun search(tracks: List<Track>, query: String): List<Track> {
        val words = fold(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return tracks
        return tracks.filter { t ->
            val hay = fold("${t.title} ${t.artist} ${t.albumArtist} ${t.album}")
            words.all { it in hay }
        }
    }

    /** "3:07", or "1:02:09" for an hour or more; nothing for an unknown length. */
    fun duration(ms: Long): String {
        if (ms <= 0) return ""
        val total = ms / 1000
        val h = total / 3600
        val m = total % 3600 / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s) else "%d:%02d".format(Locale.ROOT, m, s)
    }
}
