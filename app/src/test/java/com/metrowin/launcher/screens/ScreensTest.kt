package com.metrowin.launcher.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.data.DefaultStart
import com.metrowin.launcher.data.LiveEvent
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.MetroCatalog
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.apps.AppContextMenu
import com.metrowin.launcher.ui.apps.AppListModel
import com.metrowin.launcher.ui.apps.AppListScreen
import com.metrowin.launcher.ui.apps.JumpList
import com.metrowin.launcher.ui.apps.MenuState
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.permissions.PermissionsScreen
import com.metrowin.launcher.ui.start.StartScreen
import com.metrowin.launcher.ui.start.StartTile
import com.metrowin.launcher.ui.theme.DefaultAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class ScreensTest : ScreenshotBase() {
    /** Colour of the context menu panel (MetroSurface, opaque). */
    private val MENU_PANEL = 0xFF1F1F1F.toInt()


    /** Every stock app plus a few ordinary ones, with no real activities behind them. */
    private fun sampleItems(): List<AppItem> {
        val stock = MetroCatalog.apps.map { s ->
            AppItem(MetroCatalog.idOf(s), context.getString(s.label), Launch.Action("sample"), s.glyph, s.live)
        }
        val others = listOf("Authenticator", "Chrome", "1Password", "Zoom", "Spotify", "Telegram", "WhatsApp", "Yelp", "Élan", "Notes")
            .map { AppItem("app:$it", it, Launch.Action("sample"), removable = true) }
        return AppSections.sort(stock + others)
    }

    private val live = LiveTileData(
        nextEvent = LiveEvent("Dentist", System.currentTimeMillis() + 3_600_000, allDay = false),
        missedCalls = 3,
    )

    private fun tilesOf(items: List<AppItem>, layout: List<Tile> = DefaultStart.build(items)): List<StartTile> {
        val byId = items.associateBy { it.id }
        return layout.mapNotNull { tile -> byId[tile.id]?.let { StartTile(tile, it) } }
    }

    @Composable
    private fun Start(tiles: List<StartTile>, editingId: String? = null) = StartScreen(
        tiles = tiles, live = live, enterToken = 0, exitRequest = null, editingId = editingId,
        scrollState = rememberScrollState(),
        onTileClick = {}, onTileLongClick = {}, onUnpin = {}, onResize = {}, onBackgroundTap = {}, onOpenApps = {},
    )

    @Test
    fun startTilesSwingInOneAfterAnother() {
        val tiles = tilesOf(sampleItems())
        show { Start(tiles) }
        advance(16)
        val first = capture("start_entrance_1").accentShare()
        advance(150)
        val second = capture("start_entrance_2").accentShare()
        advance(2_500)
        val done = capture("start").accentShare()

        assertTrue("tiles should still be coming in after the first frame ($first)", first < second)
        assertTrue("tiles should keep coming in ($second < $done)", second < done)
        assertTrue("Start should end up full of tiles ($done)", done > 0.3f)
    }

    @Test
    fun startShowsSmallMediumAndWideTiles() {
        val items = sampleItems()
        val layout = listOf(
            "stock:phone" to TileSize.Small, "stock:people" to TileSize.Small,
            "stock:calendar" to TileSize.Small, "stock:photos" to TileSize.Small,
            "stock:store" to TileSize.Wide, "app:Chrome" to TileSize.Medium, "app:Zoom" to TileSize.Small,
        ).map { (id, size) -> Tile(id, size) }
        show { Start(tilesOf(items, layout)) }
        advance(3_000)
        assertTrue(capture("start_sizes").accentShare() > 0.2f)
    }

    @Test
    fun startEditModeShowsTheButtonsOfTheSelectedTile() {
        val tiles = tilesOf(sampleItems())
        show { Start(tiles, editingId = "stock:people") }
        advance(3_000)
        assertTrue(capture("start_editing").accentShare() > 0.2f)
    }

    @Test
    fun appListIsAlphabeticWithLetterHeaders() {
        val model = AppListModel(AppSections.group(sampleItems()))
        show {
            AppListScreen(
                model = model, listState = rememberLazyListState(), replayToken = 0, exiting = false,
                onLaunch = {}, onLongPress = { _, _, _ -> }, onHeaderClick = {},
            )
        }
        advance(80)
        val entering = capture("apps_entering").accentShare()
        advance(1_500)
        val settled = capture("apps").accentShare()
        assertTrue("rows should be coming in ($entering < $settled)", entering < settled)
    }

    @Test
    fun jumpListHighlightsLettersThatHaveApps() {
        val sections = AppSections.group(sampleItems())
        show { JumpList(AppSections.jumpKeys(sections), sections.map { it.key }.toSet(), onPick = {}, onDismiss = {}) }
        advance(1_500)
        assertTrue(capture("jumplist").accentShare() > 0.2f)
    }

    @Test
    fun contextMenuOpensBelowARowAtTheTop() {
        val item = sampleItems().first { it.id == "stock:photos" }
        show { AppContextMenu(MenuState(item, top = 300f, height = 192f), pinned = false, onPinToggle = {}, onUninstall = {}, onDismiss = {}) }
        advance(100)
        val screen = capture("menu_below")
        // The row spans y 300..492: the menu panel is right under it and nothing is above it.
        assertEquals(MENU_PANEL, screen.getPixel(1000, 552))
        assertNotEquals(MENU_PANEL, screen.getPixel(1000, 240))
    }

    @Test
    fun contextMenuOpensAboveARowAtTheBottom() {
        val item = sampleItems().first { it.id == "app:Chrome" }
        show { AppContextMenu(MenuState(item, top = 1900f, height = 192f), pinned = true, onPinToggle = {}, onUninstall = {}, onDismiss = {}) }
        advance(100)
        val screen = capture("menu_above")
        // The row spans y 1900..2092: the menu panel is right above it and nothing is below it.
        assertEquals(MENU_PANEL, screen.getPixel(1000, 1840))
        assertNotEquals(MENU_PANEL, screen.getPixel(1000, 2150))
    }

    @Test
    fun permissionsPageExplainsEachPermission() {
        show { PermissionsScreen(onFinished = {}) }
        advance(100)
        capture("permissions")
    }

    @Test
    fun everyGlyphIsDrawn() {
        show {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Glyph.entries.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { glyph ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(64.dp).background(DefaultAccent), contentAlignment = Alignment.Center) {
                                    GlyphIcon(glyph, 44.dp)
                                }
                                MetroText(glyph.name, size = 9.sp)
                            }
                        }
                    }
                }
            }
        }
        advance(100)
        capture("glyphs")
    }
}
