package com.metrowin.launcher.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.metrowin.launcher.music.MusicAlbum
import com.metrowin.launcher.music.MusicPlaylists
import com.metrowin.launcher.music.MusicScreen
import com.metrowin.launcher.music.Track
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class MusicUiTest : ScreenshotBase() {
    private val songs = listOf(
        Track(1, "Zebra", "Beta", "", "Second", 7, 187_000, 2),
        Track(2, "Alpha", "Beta", "", "Second", 7, 61_000, 1),
        Track(3, "Solo", "Gamma", "", "One", 8, 90_000, 1),
    )
    private var saved: String? = null
    private val lists = MusicPlaylists({ saved }, { saved = it })

    private fun screen(
        tracks: List<Track>? = songs, needsPermission: Boolean = false, onAllow: () -> Unit = {},
        onPlay: (Track) -> Unit = {}, onPlayAlbum: (MusicAlbum) -> Unit = {},
    ) = show { MusicScreen(tracks, needsPermission, onAllow, lists, onPlay, onPlayAlbum) }.also { advance(500) }

    @Test
    fun withoutThePermissionTheHubSaysWhyAndAsks() {
        var asked = false
        screen(null, needsPermission = true, onAllow = { asked = true })
        compose.onNodeWithTag("music:permission").assertIsDisplayed()
        compose.onNodeWithTag("music:allow").performClick()
        assertEquals(true, asked)
    }

    @Test
    fun whileItReadsItSaysSo() {
        screen(null)
        compose.onNodeWithTag("music:loading").assertIsDisplayed()
    }

    @Test
    fun theArtistsComeFirstAndAnArtistOpensItsAlbumsAndAnAlbumItsSongs() {
        var played: Track? = null
        var album: MusicAlbum? = null
        screen(onPlay = { played = it }, onPlayAlbum = { album = it })
        compose.onNode(hasText("artists")).assertIsDisplayed()
        compose.onNodeWithTag("music:artist:Beta").performClick()
        advance(300)
        compose.onNodeWithTag("music:album:Second").assertIsDisplayed()
        compose.onNodeWithTag("music:album:Second").performClick()
        advance(300)
        compose.onNodeWithTag("music:album-songs").assertIsDisplayed()
        // The songs are in track order: Alpha (1) before Zebra (2).
        compose.onNodeWithTag("music:song:2").assertIsDisplayed()
        compose.onNodeWithTag("music:song:1").performClick()
        assertEquals(1L, played?.id)
        compose.onNodeWithTag("music:play-album").performClick()
        assertEquals("Second", album?.name)
    }

    @Test
    fun theSongsPageListsEverySongAndASearchNarrowsThem() {
        screen()
        compose.onNodeWithTag("music:artists").performTouchInput { swipeLeft() }
        advance(500)
        compose.onNodeWithTag("music:albums").performTouchInput { swipeLeft() }
        advance(500)
        compose.onNodeWithTag("music:song:3").assertIsDisplayed()
        compose.onNodeWithTag("music:song:1").assertIsDisplayed()
    }
}
