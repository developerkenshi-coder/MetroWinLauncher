package com.metrowin.launcher.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.LiveKind
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.notifications.LiveCard
import com.metrowin.launcher.notifications.NowPlaying
import com.metrowin.launcher.ui.start.TileFace
import com.metrowin.launcher.ui.theme.DefaultAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.graphics.Color as AndroidColor

/** The faces a tile of another app turns over to: conversations, notifications, pictures, progress, music. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class LiveCardFacesTest : ScreenshotBase() {
    private val whatsapp = AppItem("app:wa", "WhatsApp", Launch.Action("x"), packageName = "com.whatsapp")
    private val music = AppItem("stock:music", "Music", Launch.Action("x"), Glyph.Music, live = LiveKind.Music)

    private fun solid(argb: Int): ImageBitmap =
        Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(argb) }.asImageBitmap()

    private fun showTile(
        item: AppItem, cards: List<LiveCard>, size: TileSize = TileSize.Medium, live: LiveTileData = LiveTileData(),
        dark: Boolean = true,
    ) = show(dark = dark) {
        val w = if (size == TileSize.Wide) 408.dp else 200.dp
        Box(Modifier.size(w, 200.dp).background(DefaultAccent)) {
            TileFace(item, size, w, 200.dp, live, seed = 0, notifications = 0, cards = cards)
        }
    }

    /** Turns the tile over to its first card: the first flip comes after about a second and a half. */
    private fun firstCard() = advance(4_000)

    // --- conversations

    @Test
    fun aTileTurnsOverToWhoWroteAndWhat() {
        showTile(whatsapp, listOf(LiveCard.Message("Giulia", "Ci vediamo alle otto?", null, null, 0)))
        advance(300)
        compose.onAllNodesWithText("Giulia").assertCountEquals(0)
        firstCard()
        capture("card_message")
        compose.onAllNodesWithText("Giulia").assertCountEquals(1)
        compose.onAllNodesWithText("Ci vediamo alle otto?").assertCountEquals(1)
        compose.onNodeWithTag("card:Message").assertExists()
    }

    @Test
    fun theNameOfAGroupChatAndThePictureOfTheSenderShow() {
        showTile(whatsapp, listOf(LiveCard.Message("Marco", "Arrivo", "Famiglia", solid(AndroidColor.rgb(200, 30, 30)), 2)), size = TileSize.Wide)
        firstCard()
        capture("card_message_wide")
        compose.onAllNodesWithText("Famiglia").assertCountEquals(1)
        compose.onNodeWithTag("card:avatar").assertExists()
    }

    @Test
    fun withTheTextHiddenOnlyTheNameShows() {
        showTile(whatsapp, listOf(LiveCard.Message("Giulia", "", null, null, 0)))
        firstCard()
        compose.onAllNodesWithText("Giulia").assertCountEquals(1)
        compose.onNodeWithTag("card:Message").assertExists()
    }

    @Test
    fun severalChatsTurnOverOneAfterTheOther() {
        showTile(whatsapp, listOf(LiveCard.Message("Anna", "uno", null, null, 0), LiveCard.Message("Bruno", "due", null, null, 0)))
        firstCard()
        compose.onAllNodesWithText("Anna").assertCountEquals(1)
        compose.onAllNodesWithText("Bruno").assertCountEquals(0)
        advance(5_000)
        compose.onAllNodesWithText("Bruno").assertCountEquals(1)
        compose.onAllNodesWithText("Anna").assertCountEquals(0)
    }

    @Test
    fun aSmallTileDoesNotTurnOver() {
        showTile(whatsapp, listOf(LiveCard.Message("Giulia", "Ciao", null, null, 0)), size = TileSize.Small)
        advance(8_000)
        compose.onAllNodesWithText("Giulia").assertCountEquals(0)
    }

    // --- other notifications

    @Test
    fun aNotificationIsATitleAndALineAndAMarkSaysItIsGoingOnNow() {
        showTile(whatsapp, listOf(LiveCard.Digest("5 min", "Turn left onto Via Roma", null, live = true)))
        firstCard()
        capture("card_digest_live")
        compose.onAllNodesWithText("5 min").assertCountEquals(1)
        compose.onNodeWithTag("card:live").assertExists()
    }

    @Test
    fun aPlainNotificationHasNoLiveMark() {
        showTile(whatsapp, listOf(LiveCard.Digest("marco", "liked your photo")))
        firstCard()
        compose.onAllNodesWithTag("card:live").assertCountEquals(0)
        compose.onAllNodesWithText("liked your photo").assertCountEquals(1)
    }

    // --- pictures, progress, music

    @Test
    fun aPictureFillsTheTileWithItsWordsUnderIt() {
        showTile(whatsapp, listOf(LiveCard.Picture(solid(AndroidColor.rgb(0, 200, 0)), "Anna: guarda!")))
        firstCard()
        val shot = capture("card_picture")
        // The middle of the tile is the picture, not the accent color.
        val middle = shot.getPixel(300, 300)
        assertTrue("green picture at the middle (${Integer.toHexString(middle)})", AndroidColor.green(middle) > 150 && AndroidColor.red(middle) < 60)
        compose.onAllNodesWithText("Anna: guarda!").assertCountEquals(1)
    }

    @Test
    fun aDownloadShowsItsNameAndHowFarAlong() {
        showTile(whatsapp, listOf(LiveCard.Progress("film.mp4", 45)))
        firstCard()
        capture("card_progress")
        compose.onAllNodesWithText("film.mp4").assertCountEquals(1)
        compose.onAllNodesWithText("45%").assertCountEquals(1)
    }

    @Test
    fun aProgressThatDoesNotKnowHasNoPercent() {
        showTile(whatsapp, listOf(LiveCard.Progress("Syncing", null)))
        firstCard()
        compose.onAllNodesWithText("Syncing").assertCountEquals(1)
        compose.onAllNodesWithText("%", substring = true).assertCountEquals(0)
    }

    @Test
    fun theSongShowsItsNameItsArtistItsCoverAndWhetherItPlays() {
        showTile(whatsapp, listOf(LiveCard.Playing("Bohemian Rhapsody", "Queen", solid(AndroidColor.rgb(30, 30, 200)), true)), size = TileSize.Wide)
        firstCard()
        capture("card_playing")
        compose.onAllNodesWithText("Bohemian Rhapsody").assertCountEquals(1)
        compose.onAllNodesWithText("Queen").assertCountEquals(1)
        compose.onNodeWithTag("card:art").assertExists()
        compose.onNodeWithTag("card:playing").assertExists()
    }

    @Test
    fun aPausedSongSaysSo() {
        showTile(whatsapp, listOf(LiveCard.Playing("Song", "Artist", null, false)))
        firstCard()
        compose.onNodeWithTag("card:paused").assertExists()
        compose.onAllNodesWithTag("card:playing").assertCountEquals(0)
    }

    // --- the Music tile

    @Test
    fun theMusicTileShowsTheSongThatPlaysInAnyApp() {
        showTile(music, emptyList(), live = LiveTileData(nowPlaying = NowPlaying("Song", "Artist", null, true)))
        advance(300)
        compose.onAllNodesWithText("Song").assertCountEquals(0)
        firstCard()
        capture("card_music_tile")
        compose.onAllNodesWithText("Song").assertCountEquals(1)
        compose.onNodeWithTag("card:Playing").assertExists()
    }

    @Test
    fun theMusicTileStaysStillWhenNothingPlays() {
        showTile(music, emptyList())
        advance(8_000)
        compose.onAllNodesWithTag("card:Playing").assertCountEquals(0)
    }

    @Test
    fun theCardsAreDrawnInTheLightThemeToo() {
        showTile(whatsapp, listOf(LiveCard.Message("Giulia", "Ciao", null, null, 0)), dark = false)
        firstCard()
        capture("card_message_light")
        compose.onAllNodesWithText("Giulia").assertCountEquals(1)
        assertEquals(1, 1)
    }
}
