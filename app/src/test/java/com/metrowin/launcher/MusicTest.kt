package com.metrowin.launcher

import android.database.MatrixCursor
import android.provider.MediaStore
import com.metrowin.launcher.music.MusicLibrary
import com.metrowin.launcher.music.MusicPlaylists
import com.metrowin.launcher.music.MusicReader
import com.metrowin.launcher.music.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private fun t(id: Long, title: String, artist: String = "", album: String = "", albumArtist: String = "", no: Int = 0, ms: Long = 0, added: Long = 0, albumId: Long = 0) =
    Track(id, title, artist, albumArtist, album, albumId, ms, no, 0, added)

@RunWith(RobolectricTestRunner::class)
class MusicTest {
    private val songs = listOf(
        t(1, "Zebra", "Beta", "Second", no = 2, albumId = 7),
        t(2, "Alpha", "Beta", "Second", no = 1, albumId = 7),
        t(3, "Café", "Álvaro", "Solo"),
        t(4, "Song A", "Guest One", "Hits", albumArtist = "Various Artists", no = 1001),
        t(5, "Song B", "Guest Two", "Hits", albumArtist = "Various Artists", no = 1002),
        t(6, "Disc two", "Guest One", "Hits", albumArtist = "Various Artists", no = 2001),
        t(7, "No tags"),
    )

    @Test
    fun anAlbumHoldsItsSongsInDiscAndTrackOrderAndACompilationIsOneAlbum() {
        val albums = MusicLibrary.albums(songs)
        val hits = albums.single { it.name == "Hits" }
        assertEquals("Various Artists", hits.artist)
        assertEquals(listOf("Song A", "Song B", "Disc two"), hits.tracks.map { it.title })
        assertEquals(listOf("Alpha", "Zebra"), albums.single { it.name == "Second" }.tracks.map { it.title })
        assertEquals(7L, albums.single { it.name == "Second" }.albumId)
    }

    @Test
    fun artistsSortWithoutCaseOrAccentsAndTheUnknownOneIsLast() {
        val artists = MusicLibrary.artists(songs)
        assertEquals(listOf("Álvaro", "Beta", "Various Artists", ""), artists.map { it.name })
        assertEquals(2, artists.single { it.name == "Beta" }.tracks)
        assertEquals(3, artists.single { it.name == "Various Artists" }.tracks)
        assertEquals(listOf("Hits"), MusicLibrary.albumsOf(songs, "Various Artists").map { it.name })
    }

    @Test
    fun songsSortByTitleRecentOnesByDateAndSearchFindsEveryWordWithoutAccents() {
        assertEquals("Alpha", MusicLibrary.songs(songs).first().title)
        assertEquals("Zebra", MusicLibrary.songs(songs).last().title)
        // A song with no title goes after all the others.
        assertEquals("", MusicLibrary.songs(songs + t(8, "")).last().title)
        val dated = listOf(t(1, "old", added = 5), t(2, "new", added = 9), t(3, "mid", added = 7))
        assertEquals(listOf("new", "mid"), MusicLibrary.recent(dated, 2).map { it.title })
        assertEquals(listOf("Café"), MusicLibrary.search(songs, "cafe").map { it.title })
        assertEquals(listOf("Alpha", "Zebra"), MusicLibrary.search(songs, "beta second").map { it.title }.sorted())
        assertEquals(songs.size, MusicLibrary.search(songs, "  ").size)
        assertTrue(MusicLibrary.search(songs, "nothing like this").isEmpty())
    }

    @Test
    fun aDurationReadsLikeAClock() {
        assertEquals("", MusicLibrary.duration(0))
        assertEquals("0:07", MusicLibrary.duration(7_400))
        assertEquals("3:07", MusicLibrary.duration(187_000))
        assertEquals("1:02:09", MusicLibrary.duration(3_729_000))
    }

    @Test
    fun theReaderTurnsRowsIntoSongsAndAMissingColumnIsEmpty() {
        val cursor = MatrixCursor(arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.DURATION))
        cursor.addRow(arrayOf<Any?>(10L, " Title ", "<unknown>", 2003, 187_000L))
        cursor.addRow(arrayOf<Any?>(11L, null, "Band", 0, 0L))
        val read = MusicReader.parse(cursor)
        assertEquals(2, read.size)
        assertEquals("Title", read[0].title)
        assertEquals("", read[0].artist)
        assertEquals(3, read[0].number)
        assertEquals(187_000L, read[0].durationMs)
        assertEquals("", read[1].title)
        assertEquals("", read[1].album)
        assertEquals("Band", read[1].owner)
    }

    @Test
    fun playlistsKeepTheirNamesAndSongsInAFileAndNeverCrashOnABadOne() {
        var saved: String? = null
        val lists = MusicPlaylists({ saved }, { saved = it })
        assertTrue(lists.create("  Road   trip "))
        assertFalse(lists.create("road trip"))
        assertFalse(lists.create("   "))
        assertEquals(listOf("Road trip"), lists.names())
        assertTrue(lists.add("Road trip", 5))
        assertTrue(lists.add("Road trip", 9))
        assertTrue(lists.add("Road trip", 5))
        assertFalse(lists.add("Nobody", 1))
        assertEquals(listOf(5L, 9L), lists.idsOf("Road trip"))
        // A second reader of the same text sees the same lists.
        assertEquals(listOf(5L, 9L), MusicPlaylists({ saved }, { saved = it }).idsOf("Road trip"))
        lists.remove("Road trip", 5)
        assertEquals(listOf(9L), lists.idsOf("Road trip"))
        lists.delete("Road trip")
        assertTrue(lists.names().isEmpty())
        assertTrue(MusicPlaylists.decode("not json").isEmpty())
        assertTrue(MusicPlaylists.decode(null).isEmpty())
        assertTrue(MusicPlaylists.decode("""[{"name":"","ids":[1]},{"name":"x"}]""").isEmpty())
    }
}
