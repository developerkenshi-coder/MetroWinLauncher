package com.metrowin.launcher.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.data.DefaultStart
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LiveEvent
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.MetroCatalog
import com.metrowin.launcher.data.StartEntries
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Folder
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.StartEntry
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
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.start.StartActions
import com.metrowin.launcher.ui.start.StartScreen
import com.metrowin.launcher.ui.start.buildStartCells
import com.metrowin.launcher.ui.theme.DefaultAccent
import com.metrowin.launcher.ui.theme.MetroAccents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.graphics.Color as AndroidColor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class ScreensTest : ScreenshotBase() {
    /** Colour of the context menu panel (the dark theme surface, opaque). */
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

    private fun noActions() = StartActions(
        onClick = {}, onLongClick = {}, onUnpin = {}, onResize = {}, onRename = {},
        onMove = { _, _ -> }, onMerge = { _, _ -> }, onBackgroundTap = {}, onOpenApps = {},
    )

    /** The Start screen driven by [entries], which the drag and drop callbacks change like the real app does. */
    @Composable
    private fun Start(
        entries: MutableState<List<StartEntry>>,
        items: List<AppItem>,
        columns: Int = 4,
        background: ImageBitmap? = null,
        editingKey: String? = null,
        openFolderId: String? = null,
        badges: Map<String, Int> = emptyMap(),
        scrollState: ScrollState = rememberScrollState(),
    ) = StartScreen(
        cells = buildStartCells(entries.value, items, openFolderId),
        live = live,
        badges = badges,
        columns = columns,
        background = background,
        enterToken = 0,
        exitRequest = null,
        editingKey = editingKey,
        scrollState = scrollState,
        actions = StartActions(
            onClick = {}, onLongClick = {}, onUnpin = {}, onResize = {}, onRename = {},
            onMove = { dragged, target -> entries.value = StartEntries.moveTo(entries.value, dragged, target) },
            onMerge = { dragged, onto -> entries.value = StartEntries.merge(entries.value, dragged, onto) { "folder" } },
            onBackgroundTap = {}, onOpenApps = {},
        ),
    )

    private fun defaultEntries(items: List<AppItem>) = mutableStateOf<List<StartEntry>>(DefaultStart.build(items))

    /** Four medium tiles, A B on the top row and C D below, none of them with live content. */
    private fun fourTiles(): Pair<List<AppItem>, MutableState<List<StartEntry>>> {
        val items = listOf("A", "B", "C", "D").map { AppItem(it, "App $it", Launch.Action("x"), Glyph.entries[it[0] - 'A' + 5]) }
        return items to mutableStateOf<List<StartEntry>>(items.map { Tile(it.id, TileSize.Medium) })
    }

    private fun androidx.compose.ui.graphics.Color.toArgbInt() = this.toArgb()

    private fun solidPhoto(argb: Int): ImageBitmap =
        Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(argb) }.asImageBitmap()

    @Test
    fun startTilesSwingInOneAfterAnother() {
        val items = sampleItems()
        val entries = defaultEntries(items)
        show { Start(entries, items) }
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
        val entries = mutableStateOf<List<StartEntry>>(
            listOf(
                "stock:phone" to TileSize.Small, "stock:people" to TileSize.Small,
                "stock:calendar" to TileSize.Small, "stock:photos" to TileSize.Small,
                "stock:store" to TileSize.Wide, "app:Chrome" to TileSize.Medium, "app:Zoom" to TileSize.Small,
            ).map { (id, size) -> Tile(id, size) },
        )
        show { Start(entries, items) }
        advance(3_000)
        assertTrue(capture("start_sizes").accentShare() > 0.2f)
    }

    @Test
    fun startEditModeShowsTheButtonsOfTheSelectedTile() {
        val items = sampleItems()
        val entries = defaultEntries(items)
        show { Start(entries, items, editingKey = "stock:people") }
        advance(3_000)
        assertTrue(capture("start_editing").accentShare() > 0.2f)
    }

    @Test
    fun startInTheLightThemeIsWhiteWithColouredTiles() {
        val items = sampleItems()
        val entries = defaultEntries(items)
        show(dark = false) { Start(entries, items) }
        advance(3_000)
        val screen = capture("start_light")
        // The strip above the first tile is the page background: white in the light theme, black in the dark one.
        assertEquals(0xFFFFFFFF.toInt(), screen.getPixel(540, 10))
        assertTrue(screen.accentShare() > 0.3f)
    }

    @Test
    fun startWithAPhotoShowsItThroughTheTilesOfBuiltInApps() {
        val (items, entries) = fourTiles()
        // C is an ordinary app (no glyph), the others are built-in apps.
        val mixed = items.map { if (it.id == "C") it.copy(glyph = null) else it }
        show { Start(entries, mixed, background = solidPhoto(AndroidColor.rgb(255, 0, 0))) }
        advance(3_000)
        val screen = capture("start_background")
        // Tile A is at the top left, tile C below it. Sample the top left corner, away from the glyph.
        val builtIn = screen.getPixel(60, 80)
        val ordinary = screen.getPixel(60, 600)
        assertTrue("a built-in tile should show the red photo, got ${Integer.toHexString(builtIn)}", AndroidColor.red(builtIn) > 150 && AndroidColor.green(builtIn) < 60)
        assertTrue("an ordinary app keeps the accent colour", isAccent(ordinary))
    }

    @Test
    fun theStartPhotoMovesSlowerThanTheTiles() {
        // A photo that goes from black at the top to white at the bottom, so a shift shows as a change of brightness.
        val photo = Bitmap.createBitmap(16, 64, Bitmap.Config.ARGB_8888).also { bmp ->
            for (y in 0 until 64) for (x in 0 until 16) bmp.setPixel(x, y, AndroidColor.rgb(y * 4, y * 4, y * 4))
        }.asImageBitmap()
        val items = sampleItems()
        val entries = defaultEntries(items)
        val scroll = ScrollState(0)
        show { Start(entries, items, background = photo, scrollState = scroll) }
        advance(2_500)
        // The left margin (x 0..36) is photo only, with no tile on top of it.
        val before = capture("start_parallax_top").getPixel(10, 1000)
        compose.runOnUiThread {
            runBlocking { scroll.scrollTo(900) }
            Snapshot.sendApplyNotifications()
        }
        advance(300)
        val after = capture("start_parallax_scrolled").getPixel(10, 1000)
        // 900px of scrolling moves the photo 20% of that, 180px up: the same spot is now lower in the photo.
        assertTrue("photo should have moved up (${AndroidColor.red(before)} -> ${AndroidColor.red(after)})", AndroidColor.red(after) > AndroidColor.red(before) + 8)
        assertTrue("but only a little", AndroidColor.red(after) < AndroidColor.red(before) + 80)
    }

    @Test
    fun aSelectedFolderHasButtonsToRemoveResizeAndRenameIt() {
        val (items, _) = fourTiles()
        val entries = mutableStateOf<List<StartEntry>>(listOf(Folder("f", "Games", TileSize.Medium, listOf("A", "B", "C")), Tile("D", TileSize.Medium)))
        show { Start(entries, items, editingKey = "f") }
        advance(2_500)
        val screen = capture("start_folder_editing")
        // A white round button sits in three corners of the folder tile (x 36..531, y 48..543), the bottom left stays free for its name.
        fun whiteAround(cx: Int, cy: Int) = (-30..30).sumOf { dy -> (-30..30).count { dx -> screen.getPixel(cx + dx, cy + dy) == 0xFFFFFFFF.toInt() } }
        assertTrue("remove button", whiteAround(500, 80) > 800)
        assertTrue("resize button", whiteAround(500, 510) > 800)
        assertTrue("rename button", whiteAround(70, 80) > 800)
    }

    @Test
    fun moreTilesPutsThreeMediumTilesOnARow() {
        val (items, entries) = fourTiles()
        show { Start(entries, items, columns = 6) }
        advance(3_000)
        val screen = capture("start_more_tiles")
        // First row, away from the glyphs: the three tiles span 36..360, 378..702 and 720..1044 pixels.
        listOf(60, 400, 740).forEach { x -> assertTrue("tile at x=$x", isAccent(screen.getPixel(x, 80))) }
        // The fourth tile starts the second row, and there is a gap between the tiles of the first.
        assertTrue(isAccent(screen.getPixel(60, 420)))
        assertFalse("gap between the first two tiles", isAccent(screen.getPixel(369, 80)))
    }

    @Test
    fun draggingATileOverTheEdgeOfAnotherMovesItThere() {
        val (items, entries) = fourTiles()
        show { Start(entries, items) }
        advance(2_000)
        val tile = compose.onNodeWithTag("tile:A")
        tile.performTouchInput { down(center) }
        advance(700) // long press: the tile is picked up
        // Its centre ends up near the left edge of tile B.
        tile.performTouchInput { moveBy(Offset(277f, 0f)) }
        advance(100)
        tile.performTouchInput { up() }
        advance(400)
        capture("start_after_drag")
        assertEquals(listOf("B", "A", "C", "D"), entries.value.map { it.id })
    }

    @Test
    fun holdingATileOnTheMiddleOfAnotherMakesAFolder() {
        val (items, entries) = fourTiles()
        show { Start(entries, items) }
        advance(2_000)
        val tile = compose.onNodeWithTag("tile:A")
        tile.performTouchInput { down(center) }
        advance(700)
        tile.performTouchInput { moveBy(Offset(513f, 0f)) } // the middle of tile B
        advance(600) // held there long enough to arm the folder drop
        capture("start_folder_drop")
        tile.performTouchInput { up() }
        advance(400)
        val folder = entries.value.first() as Folder
        assertEquals(listOf("B", "A"), folder.children)
        assertEquals(listOf("folder", "C", "D"), entries.value.map { it.id })
    }

    @Test
    fun anOpenFolderShowsItsAppsRightAfterIt() {
        val (items, _) = fourTiles()
        val entries = mutableStateOf<List<StartEntry>>(
            listOf(Folder("f", "Games", TileSize.Medium, listOf("A", "B", "C")), Tile("D", TileSize.Medium)),
        )
        var open by mutableStateOf<String?>(null)
        show { Start(entries, items, openFolderId = open) }
        advance(2_000)
        compose.onNodeWithTag("tile:f/A").assertDoesNotExist()
        capture("start_folder_closed")
        // The test clock is paused, so tell Compose about the state change by hand.
        compose.runOnUiThread {
            open = "f"
            Snapshot.sendApplyNotifications()
        }
        advance(2_000)
        compose.onNodeWithTag("tile:f/A").assertExists()
        compose.onNodeWithTag("tile:f/C").assertExists()
        capture("start_folder_open")
    }

    @Test
    fun tilesShowTheNumberOfNotificationsOfTheirApp() {
        val items = listOf(AppItem("A", "Messages", Launch.Action("x"), Glyph.Messaging, packageName = "com.example.messages"))
        val entries = mutableStateOf<List<StartEntry>>(listOf(Tile("A", TileSize.Medium)))
        show { Start(entries, items, badges = mapOf("com.example.messages" to 7)) }
        advance(2_000)
        val withBadge = capture("start_badge")
        // The tile spans x 36..531, y 48..543. The number is white on the accent colour in its top right corner,
        // where the glyph (in the middle) and the label (at the bottom) are not.
        val white = (0 until 150).sumOf { y -> (380 until 531).count { x -> withBadge.getPixel(x, 48 + y) == 0xFFFFFFFF.toInt() } }
        assertTrue("the badge should draw white pixels in the corner ($white)", white > 200)
    }

    @Test
    fun settingsPageOffersTwentyAccentColours() {
        show {
            SettingsScreen(
                settings = LauncherSettings(),
                startBackground = null,
                onChange = {}, onPickBackground = {}, onClearBackground = {},
            )
        }
        advance(500)
        val screen = capture("settings_start_theme")
        val pixels = screen.pixels()
        // Every accent colour has a swatch on the page.
        MetroAccents.forEachIndexed { i, color ->
            val argb = color.toArgbInt()
            assertTrue("accent #$i is missing from the page", pixels.any { it == argb })
        }
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
