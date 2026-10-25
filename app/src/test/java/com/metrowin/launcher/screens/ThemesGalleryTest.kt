package com.metrowin.launcher.screens

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.data.DefaultStart
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.MetroCatalog
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.data.ThemeStyle
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Folder
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.notifications.NotificationEntry
import com.metrowin.launcher.ui.actioncenter.ActionCenter
import com.metrowin.launcher.ui.apps.AppContextMenu
import com.metrowin.launcher.ui.apps.AppListModel
import com.metrowin.launcher.ui.apps.AppListScreen
import com.metrowin.launcher.ui.apps.MenuState
import com.metrowin.launcher.ui.components.ChoiceRow
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.MetroToggle
import com.metrowin.launcher.ui.gesture.rememberPullPanel
import com.metrowin.launcher.ui.settings.SettingsEnv
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.settings.SettingsSection
import com.metrowin.launcher.ui.start.StartActions
import com.metrowin.launcher.ui.start.StartScreen
import com.metrowin.launcher.ui.start.buildStartCells
import com.metrowin.launcher.ui.theme.DefaultAccent
import com.metrowin.launcher.ui.theme.MetroTheme
import com.metrowin.launcher.ui.theme.Palettes
import com.metrowin.launcher.ui.theme.pageBackground
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The screens in the three looks side by side (Classic, Modern Glass, Modern Droid), saved as
 * `app/build/screenshots/gallery_*.png`. They are there to be looked at; the tests only check that the three looks
 * really differ and that nothing is left blank.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1080dp-h760dp-xhdpi")
class ThemesGalleryTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun showLooks(dark: Boolean = true, seed: Color = DefaultAccent, oled: Boolean = false, content: @Composable (ThemeStyle) -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Row {
                ThemeStyle.entries.forEach { style ->
                    MetroTheme(seed, dark, style = style, droid = if (style == ThemeStyle.Droid) Palettes.fromSeed(seed) else null, oled = oled) {
                        Box(Modifier.size(360.dp, 760.dp).pageBackground()) { content(style) }
                    }
                }
            }
        }
    }

    private fun capture(name: String): Bitmap {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val dir = java.io.File("build/screenshots").apply { mkdirs() }
        java.io.FileOutputStream(java.io.File(dir, "gallery_$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    /** The three looks are columns of the picture: they must not be the same picture three times. */
    private fun assertLooksDiffer(shot: Bitmap) {
        val third = shot.width / 3
        // A grid of 60 by 60 points: a screen that is mostly empty (a menu on a dark page) still has a few on its words.
        fun sample(column: Int) = (0 until 3600).map { i -> shot.getPixel(column * third + 8 + (i % 60) * (third - 16) / 60, 8 + (i / 60) * (shot.height - 16) / 60) }
        val classic = sample(0)
        val glass = sample(1)
        val droid = sample(2)
        assertTrue("Glass differs from Classic", classic != glass)
        assertTrue("Droid differs from Classic", classic != droid)
        assertTrue("Droid differs from Glass", glass != droid)
        assertTrue("every look has colors in it", listOf(classic, glass, droid).all { it.toSet().size > 2 })
    }

    private fun items(): List<AppItem> {
        val stock = MetroCatalog.apps.map { s ->
            AppItem(MetroCatalog.idOf(s), context.getString(s.label), Launch.Action("sample"), s.glyph, s.live)
        }
        val others = listOf("Authenticator", "Chrome", "Zoom", "Spotify", "Telegram", "Notes")
            .map { AppItem("app:$it", it, Launch.Action("sample"), removable = true) }
        return AppSections.sort(stock + others)
    }

    @Composable
    private fun Start(items: List<AppItem>) {
        val base = DefaultStart.build(items)
        val entries = base + Folder("games", "Games", TileSize.Medium, listOf("app:Chrome", "app:Zoom", "app:Spotify", "app:Telegram", "app:Notes"))
        StartScreen(
            cells = buildStartCells(entries, items, null),
            live = LiveTileData(missedCalls = 3),
            badges = mapOf("app:Telegram" to 4),
            columns = 4,
            background = null,
            enterToken = 0,
            exitRequest = null,
            editingKey = null,
            scrollState = rememberScrollState(),
            actions = StartActions(
                onClick = {}, onLongClick = {}, onUnpin = {}, onResize = {}, onRename = {},
                onMove = { _, _ -> }, onMerge = { _, _ -> }, onBackgroundTap = {}, onNextPage = {},
            ),
        )
    }

    @Test
    fun startInTheDark() {
        val items = items()
        showLooks { Start(items) }
        compose.mainClock.advanceTimeBy(3_000)
        assertLooksDiffer(capture("start_dark"))
    }

    @Test
    fun startInTheLight() {
        val items = items()
        showLooks(dark = false, seed = Color(0xFFD80073)) { Start(items) }
        compose.mainClock.advanceTimeBy(3_000)
        assertLooksDiffer(capture("start_light"))
    }

    @Test
    fun startOnAnOledScreen() {
        val items = items()
        showLooks(oled = true) { Start(items) }
        compose.mainClock.advanceTimeBy(3_000)
        val shot = capture("start_oled")
        assertLooksDiffer(shot)
        // Between the tiles, in the margin and above them, every look is black.
        val third = shot.width / 3
        for (look in 0..2) {
            val corner = shot.getPixel(look * third + 2, 2)
            assertTrue("look $look: the corner is black (${Integer.toHexString(corner)})", (corner and 0xFFFFFF) < 0x0A0A0A)
        }
    }

    @Test
    fun appList() {
        val model = AppListModel(AppSections.group(items()))
        showLooks {
            AppListScreen(
                model = model, listState = rememberLazyListState(), replayToken = 0, exiting = false,
                onLaunch = {}, onLongPress = { _, _, _ -> }, onHeaderClick = {},
                query = "", onQueryChange = {}, results = emptyList(),
            )
        }
        compose.mainClock.advanceTimeBy(2_000)
        assertLooksDiffer(capture("apps"))
    }

    @Test
    fun appMenu() {
        val item = items().first { it.removable }
        showLooks { AppContextMenu(MenuState(item, 200f, 160f), pinned = false, onPinToggle = {}, onUninstall = {}, onDismiss = {}, onHide = {}, onAddToFolder = {}) }
        compose.mainClock.advanceTimeBy(500)
        assertLooksDiffer(capture("menu"))
    }

    @Test
    fun actionCenter() {
        showLooks {
            ActionCenter(
                slots = QuickAction.defaults,
                states = mapOf(QuickAction.Wifi to true, QuickAction.Bluetooth to false),
                notifications = emptyList<NotificationEntry>(),
                appsByPackage = emptyMap(),
                hasNotificationAccess = true,
                panel = rememberPullPanel(initialProgress = 1f),
                onAction = {}, onAllSettings = {}, onOpenNotification = {}, onDismissNotification = {},
                onClearAll = {}, onTurnOnNotificationAccess = {}, onClose = {},
            )
        }
        compose.mainClock.advanceTimeBy(500)
        assertLooksDiffer(capture("actioncenter"))
    }

    @Test
    fun controls() {
        showLooks {
            Column(Modifier.padding(24.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp)) {
                MetroText("Controls", size = 34.sp)
                MetroToggle("Wi-Fi", true, {})
                MetroToggle("Bluetooth", false, {})
                ChoiceRow(listOf("one", "two", "three"), 1, {})
                MetroButton("Save") {}
                MetroTextField("", {}, "search")
            }
        }
        compose.mainClock.advanceTimeBy(500)
        assertLooksDiffer(capture("controls"))
    }

    @Test
    fun themeSettings() {
        showLooks {
            SettingsScreen(
                SettingsEnv(LauncherSettings(themeStyle = it), onChange = {}),
                SettingsSection.StartTheme, onSectionChange = {}, sectionAway = remember { Animatable(0f) },
            )
        }
        compose.mainClock.advanceTimeBy(500)
        assertLooksDiffer(capture("settings"))
    }

    @Test
    fun settingsHome() {
        showLooks {
            SettingsScreen(SettingsEnv(LauncherSettings(themeStyle = it), onChange = {}), null, onSectionChange = {}, sectionAway = remember { Animatable(1f) })
        }
        compose.mainClock.advanceTimeBy(500)
        assertLooksDiffer(capture("settings_home"))
    }
}
