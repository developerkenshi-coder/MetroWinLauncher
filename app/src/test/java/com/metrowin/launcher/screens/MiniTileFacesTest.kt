package com.metrowin.launcher.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.notifications.LiveCard
import com.metrowin.launcher.ui.start.TileFace
import com.metrowin.launcher.ui.theme.DefaultAccent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The faces of the mini apps are the usual cards of a tile: a medium tile of an app with no live kind says what its card says. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class MiniTileFacesTest : ScreenshotBase() {
    private val notes = AppItem("stock:notes", "Notes", Launch.Action("x"), Glyph.Notes)

    private fun showTile(item: AppItem, cards: List<LiveCard>) = show {
        Box(Modifier.size(200.dp, 200.dp).background(DefaultAccent)) {
            TileFace(item, TileSize.Medium, 200.dp, 200.dp, LiveTileData(), seed = 0, notifications = 0, cards = cards)
        }
    }

    @Test
    fun aCardOfAMiniAppIsOnTheTile() {
        showTile(notes, listOf(LiveCard.Digest("Plan", "first line")))
        advance(300)
        compose.onAllNodesWithText("Plan").assertCountEquals(1)
        compose.onAllNodesWithText("first line").assertCountEquals(1)
    }

    @Test
    fun withNoCardTheTileKeepsItsNameOnly() {
        showTile(notes, emptyList())
        advance(300)
        compose.onAllNodesWithText("Plan").assertCountEquals(0)
    }

    @Test
    fun aLockedNoteCardCarriesNoTitle() {
        showTile(notes, listOf(LiveCard.Digest("Locked note", "")))
        advance(300)
        compose.onAllNodesWithText("Locked note").assertCountEquals(1)
        compose.onAllNodesWithText("Bank PIN").assertCountEquals(0)
    }
}
