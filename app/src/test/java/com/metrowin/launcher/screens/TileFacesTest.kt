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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.LiveKind
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.model.TileStyle
import com.metrowin.launcher.notifications.TilePeek
import com.metrowin.launcher.ui.start.EditBar
import com.metrowin.launcher.ui.start.TileFace
import com.metrowin.launcher.ui.theme.DefaultAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The styles of a tile and the notification that a tile can flip to. A medium tile is 200 dp, 600 px, at the top left. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class TileFacesTest : ScreenshotBase() {
    private val chat = AppItem("stock:messaging", "Messaging", Launch.Action("x"), Glyph.Messaging, packageName = "chat")
    private val photos = AppItem("stock:photos", "Photos", Launch.Action("x"), Glyph.Photos, live = LiveKind.Photos)

    private fun solid(argb: Int): ImageBitmap =
        Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(argb) }.asImageBitmap()

    private fun showTile(
        item: AppItem, size: TileSize = TileSize.Medium, style: TileStyle = TileStyle.Standard,
        notifications: Int = 0, live: LiveTileData = LiveTileData(), peek: TilePeek? = null,
    ) = show {
        Box(Modifier.size(if (size == TileSize.Wide) 408.dp else 200.dp, 200.dp).background(DefaultAccent)) {
            TileFace(
                item, size, if (size == TileSize.Wide) 408.dp else 200.dp, 200.dp, live, seed = 0, notifications = notifications,
                style = style, peek = peek,
            )
        }
    }

    private fun android.graphics.Bitmap.whiteIn(x: IntRange, y: IntRange) =
        y.sumOf { py -> x.count { px -> getPixel(px, py) == 0xFFFFFFFF.toInt() } }

    // --- iconic

    @Test
    fun theIconicTileShowsTheCounterBigOnTheRight() {
        showTile(chat, style = TileStyle.Iconic, notifications = 7)
        advance(300)
        val iconic = capture("tile_iconic")
        // The digits are on the right, where a standard tile has nothing (its glyph ends before x = 430).
        val iconicWhite = iconic.whiteIn(450..580, 180..420)
        assertTrue("big digits on the right ($iconicWhite)", iconicWhite > 600)
    }

    @Test
    fun aStandardTileKeepsItsCounterInTheCorner() {
        showTile(chat, style = TileStyle.Standard, notifications = 7)
        advance(300)
        val standard = capture("tile_standard_counter")
        val middleRight = standard.whiteIn(450..580, 180..420)
        val corner = standard.whiteIn(430..600, 0..110)
        assertTrue("the number is in the top right corner ($corner)", corner > 100)
        assertTrue("and not in the middle right ($middleRight)", middleRight < 200)
    }

    @Test
    fun anIconicTileWithNothingToCountShowsOnlyItsIcon() {
        showTile(chat, style = TileStyle.Iconic, notifications = 0)
        advance(300)
        val screen = capture("tile_iconic_empty")
        assertEquals("no digits on the right", 0, screen.whiteIn(400..560, 150..420))
    }

    @Test
    fun aSmallTileCannotBeIconic() {
        showTile(chat, size = TileSize.Small, style = TileStyle.Iconic, notifications = 7)
        advance(300)
        // A small tile has no room for the big digits: it keeps the small counter, so nothing big in the middle.
        val screen = capture("tile_iconic_small")
        assertTrue(screen.whiteIn(460..580, 180..420) < 200)
    }

    // --- cycle

    @Test
    fun aCycleTileFadesFromOnePhotoToTheNext() {
        val red = 0xFFE51400.toInt()
        val blue = 0xFF0050EF.toInt()
        showTile(photos, style = TileStyle.Cycle, live = LiveTileData(photos = listOf(solid(red), solid(blue))))
        advance(300)
        assertEquals("the first photo", red, capture("tile_cycle_1").getPixel(300, 150))
        advance(8_000)
        assertEquals("then the second", blue, capture("tile_cycle_2").getPixel(300, 150))
    }

    @Test
    fun aCycleTileWithoutPhotosFallsBackToItsUsualFace() {
        showTile(photos, style = TileStyle.Cycle, live = LiveTileData(photos = emptyList()))
        advance(300)
        // The accent colour shows, not a photo.
        assertTrue(isAccent(capture("tile_cycle_no_photos").getPixel(20, 20)))
    }

    // --- the notification a tile flips to

    @Test
    fun aTileWithANotificationFlipsToItsText() {
        showTile(chat, peek = TilePeek("Giulia", "Ci vediamo alle otto?"))
        advance(300)
        compose.onAllNodesWithText("Giulia").assertCountEquals(0)
        advance(4_000)
        capture("tile_peek")
        compose.onAllNodesWithText("Giulia").assertCountEquals(1)
        compose.onAllNodesWithText("Ci vediamo alle otto?").assertCountEquals(1)
    }

    @Test
    fun aSmallTileDoesNotFlipToANotification() {
        showTile(chat, size = TileSize.Small, peek = TilePeek("Giulia", "Ciao"))
        advance(5_000)
        compose.onAllNodesWithText("Giulia").assertCountEquals(0)
    }

    @Test
    fun withoutAPeekTheTileNeverFlips() {
        showTile(chat, peek = null)
        advance(5_000)
        compose.onAllNodesWithText("Giulia").assertCountEquals(0)
    }

    // --- the style button of the edit bar

    @Test
    fun theEditBarShowsTheStyleOfATileAndAsksForTheNext() {
        var asked = 0
        show {
            Box(Modifier.size(360.dp, 760.dp)) {
                EditBar(
                    size = TileSize.Medium, page = 0, pageCount = 1, onSize = {}, onMovePage = {}, onDone = {},
                    style = TileStyle.Iconic, onStyle = { asked++ },
                )
            }
        }
        advance(300)
        compose.onNodeWithTag("edit:style").performClick()
        assertEquals(1, asked)
    }

    @Test
    fun aFolderHasNoStyleButton() {
        show {
            Box(Modifier.size(360.dp, 760.dp)) {
                EditBar(size = TileSize.Medium, page = 0, pageCount = 1, onSize = {}, onMovePage = {}, onDone = {}, style = null)
            }
        }
        advance(300)
        compose.onAllNodesWithTag("edit:style").assertCountEquals(0)
    }
}
