package com.metrowin.launcher.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.centerY
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.data.DefaultStart
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.MetroCatalog
import com.metrowin.launcher.data.ThemeStyle
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.StartEntry
import com.metrowin.launcher.ui.components.ChoiceRow
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.MetroToggle
import com.metrowin.launcher.ui.settings.SettingsEnv
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.settings.SettingsSection
import com.metrowin.launcher.ui.start.StartActions
import com.metrowin.launcher.ui.start.StartScreen
import com.metrowin.launcher.ui.start.buildStartCells
import com.metrowin.launcher.ui.theme.DefaultAccent
import com.metrowin.launcher.ui.theme.Palettes
import com.metrowin.launcher.ui.theme.tileColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The three looks on the real screens: Classic, Modern Glass and Modern Droid, dark and light. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class ThemesTest : ScreenshotBase() {
    private fun sampleItems(): List<AppItem> {
        val stock = MetroCatalog.apps.map { s ->
            AppItem(MetroCatalog.idOf(s), context.getString(s.label), Launch.Action("sample"), s.glyph, s.live)
        }
        val others = listOf("Authenticator", "Chrome", "Zoom", "Spotify", "Telegram", "Notes")
            .map { AppItem("app:$it", it, Launch.Action("sample"), removable = true) }
        return AppSections.sort(stock + others)
    }

    @Composable
    private fun Start(entries: MutableState<List<StartEntry>>, items: List<AppItem>) = StartScreen(
        cells = buildStartCells(entries.value, items, null),
        live = LiveTileData(missedCalls = 3),
        badges = emptyMap(),
        columns = 4,
        background = null,
        enterToken = 0,
        exitRequest = null,
        editingKey = null,
        scrollState = androidx.compose.foundation.rememberScrollState(),
        actions = StartActions(
            onClick = {}, onLongClick = {}, onUnpin = {}, onResize = {}, onRename = {},
            onMove = { _, _ -> }, onMerge = { _, _ -> }, onBackgroundTap = {}, onNextPage = {},
        ),
    )

    private fun startIn(style: ThemeStyle, dark: Boolean = true, accent: Color = DefaultAccent): Pair<List<AppItem>, MutableState<List<StartEntry>>> {
        val items = sampleItems()
        val entries = mutableStateOf<List<StartEntry>>(DefaultStart.build(items))
        show(dark = dark, accent = accent, style = style) { Start(entries, items) }
        advance(3_000)
        return items to entries
    }

    private fun tileBounds(tag: String): androidx.compose.ui.geometry.Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun luminance(argb: Int): Float {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    // --- Start

    @Test
    fun classicStartKeepsFlatSquareTilesInTheAccent() {
        val (_, entries) = startIn(ThemeStyle.Classic)
        val screen = capture("theme_classic_start")
        val tile = tileBounds("tile:${entries.value.first().id}")
        // The very corner of a flat tile is the accent, and the page behind is black.
        assertTrue(isAccent(screen.getPixel(tile.left.toInt() + 1, tile.top.toInt() + 1)))
        assertEquals(0xFF000000.toInt(), screen.getPixel(2, 2))
    }

    @Test
    fun glassStartHasARoundedTileOverAGlowingBackdrop() {
        val (_, entries) = startIn(ThemeStyle.Glass)
        val screen = capture("theme_glass_start")
        val tile = tileBounds("tile:${entries.value.first().id}")
        val corner = screen.getPixel(tile.left.toInt() + 2, tile.top.toInt() + 2)
        val inside = screen.getPixel(tile.left.toInt() + 30, tile.top.toInt() + 30)
        assertNotEquals("the corner of a rounded tile shows the backdrop", corner, inside)
        // The backdrop is a glowing dark blue, neither black nor white.
        val backdrop = screen.getPixel(2, 2)
        assertNotEquals(0xFF000000.toInt(), backdrop)
        assertTrue(luminance(backdrop) < 0.45f && luminance(backdrop) > 0.02f)
    }

    @Test
    fun glassTilesAreTranslucentSoTheBackdropShowsThroughThem() {
        val (_, entries) = startIn(ThemeStyle.Glass)
        val screen = capture("theme_glass_start_tint")
        val tile = tileBounds("tile:${entries.value.first().id}")
        val inside = screen.getPixel(tile.left.toInt() + 40, tile.top.toInt() + 40)
        // A solid accent would be exactly the accent; glass over the backdrop is a different, blended color.
        assertFalse(isAccent(inside))
    }

    @Test
    fun droidStartTilesTakeTheContainersOfTheSeedPalette() {
        val seed = Color(0xFF0050EF)
        val items = sampleItems()
        val entries = mutableStateOf<List<StartEntry>>(DefaultStart.build(items))
        show(accent = seed, style = ThemeStyle.Droid, droid = Palettes.fromSeed(seed)) { Start(entries, items) }
        advance(3_000)
        val screen = capture("theme_droid_start")
        val colors = Palettes.colors(Palettes.fromSeed(seed), dark = true)
        val first = tileBounds("tile:${entries.value[0].id}")
        val second = tileBounds("tile:${entries.value[1].id}")
        fun rgb(x: Float, y: Float) = screen.getPixel(x.toInt(), y.toInt()) and 0xFFFFFF
        // Just inside the top left corner of a tile, clear of the glyph and the name.
        assertEquals(colors.tileColor(0, seed).toArgb() and 0xFFFFFF, rgb(first.left + 40, first.top + 40))
        assertEquals(colors.tileColor(1, seed).toArgb() and 0xFFFFFF, rgb(second.left + 40, second.top + 40))
        // And the corner itself is rounded away.
        assertNotEquals(colors.tileColor(0, seed).toArgb() and 0xFFFFFF, rgb(first.left + 2, first.top + 2))
    }

    @Test
    fun everyLookDrawsStartInTheLightToo() {
        ThemeStyle.entries.forEach { style ->
            val items = sampleItems()
            val entries = mutableStateOf<List<StartEntry>>(DefaultStart.build(items))
            val seed = Color(0xFFD80073)
            show(dark = false, accent = seed, style = style, droid = if (style == ThemeStyle.Droid) Palettes.fromSeed(seed) else null) { Start(entries, items) }
            advance(3_000)
            val screen = capture("theme_${style.code}_start_light")
            assertTrue("$style: the light page is light", luminance(screen.getPixel(2, 2)) > 0.5f)
            compose.activityRule.scenario.recreate()
        }
    }

    // --- controls

    private fun controlsIn(style: ThemeStyle) {
        var checked by mutableStateOf(false)
        var chosen by mutableStateOf(1)
        var pressed = 0
        show(style = style, droid = if (style == ThemeStyle.Droid) Palettes.fromSeed(DefaultAccent) else null) {
            Column(Modifier.padding(24.dp)) {
                MetroToggle("Wi-Fi", checked, { checked = it }, Modifier.testTag("toggle"))
                ChoiceRow(listOf("one", "two", "three"), chosen, { chosen = it })
                MetroButton("Save", Modifier.testTag("button")) { pressed++ }
            }
        }
        advance(400)
        compose.onNodeWithTag("toggle").performClick()
        compose.onNodeWithTag("button").performClick()
        compose.waitForIdle()
        assertTrue("$style: the toggle switches", checked)
        assertEquals("$style: the button is pressed", 1, pressed)
        advance(400)
        capture("theme_${style.code}_controls")
    }

    @Test
    fun theControlsOfClassicWork() = controlsIn(ThemeStyle.Classic)

    @Test
    fun theControlsOfModernGlassWork() = controlsIn(ThemeStyle.Glass)

    @Test
    fun theControlsOfModernDroidWork() = controlsIn(ThemeStyle.Droid)

    private var settings by mutableStateOf(LauncherSettings())

    @Composable
    private fun Settings(initial: SettingsSection?) {
        val current = settings
        var section by remember { mutableStateOf(initial) }
        val away = remember { Animatable(if (initial != null) 0f else 1f) }
        val env = SettingsEnv(current, onChange = { change -> settings = change(settings) })
        SettingsScreen(env, section, onSectionChange = { section = it }, sectionAway = away)
    }

    private fun showSettings(style: ThemeStyle = ThemeStyle.Classic, dark: Boolean = true) {
        settings = LauncherSettings(themeStyle = style)
        show(dark = dark, style = style, droid = if (style == ThemeStyle.Droid) Palettes.fromSeed(DefaultAccent) else null) {
            Settings(SettingsSection.StartTheme)
        }
        advance(500)
    }

    @Test
    fun theThemePageLetsYouPickTheLook() {
        showSettings()
        capture("theme_settings_top")
        compose.onNodeWithTag("style:classic").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        compose.onNodeWithTag("style:glass").performClick()
        compose.waitForIdle()
        assertEquals(ThemeStyle.Glass, settings.themeStyle)
        compose.onNodeWithTag("style:droid").performClick()
        compose.waitForIdle()
        assertEquals(ThemeStyle.Droid, settings.themeStyle)
        compose.onNodeWithTag("style:classic").performClick()
        compose.waitForIdle()
        assertEquals(ThemeStyle.Classic, settings.themeStyle)
    }

    @Test
    fun theOledSwitchTurnsTheBlackBackgroundOnAndOff() {
        showSettings()
        scrollTo("theme:oled")
        assertFalse(settings.oledMode)
        compose.onNodeWithTag("theme:oled").performClick()
        compose.waitForIdle()
        assertTrue(settings.oledMode)
        advance(100) // the switch now knows it is on
        compose.onNodeWithTag("theme:oled").performClick()
        compose.waitForIdle()
        assertFalse(settings.oledMode)
    }

    private fun cornerOf(style: ThemeStyle, oled: Boolean): Int {
        val items = sampleItems()
        val entries = mutableStateOf<List<StartEntry>>(DefaultStart.build(items))
        show(style = style, droid = if (style == ThemeStyle.Droid) Palettes.fromSeed(DefaultAccent) else null, oled = oled) { Start(entries, items) }
        advance(3_000)
        return capture("theme_${style.code}_start_oled_$oled").getPixel(2, 2) and 0xFFFFFF
    }

    @Test
    fun glassOnAnOledScreenIsBlackWhereItWasNavy() {
        assertTrue("it is navy without OLED", cornerOf(ThemeStyle.Glass, false) != 0)
    }

    @Test
    fun glassOnAnOledScreenIsBlackAtTheCorner() {
        assertTrue("black with a faint glow at most", cornerOf(ThemeStyle.Glass, true) < 0x0C0C0C)
    }

    @Test
    fun modernDroidOnAnOledScreenIsExactlyBlack() {
        assertEquals(0, cornerOf(ThemeStyle.Droid, true))
    }

    @Test
    fun classicIsBlackAlreadyAndStaysSo() {
        assertEquals(0, cornerOf(ThemeStyle.Classic, true))
    }

    @Test
    fun theWallpaperColorsSwitchIsOnlyForModernDroid() {
        showSettings(ThemeStyle.Classic)
        compose.onNodeWithTag("theme:dynamic").assertDoesNotExist()
        compose.onNodeWithTag("style:droid").performClick()
        advance(300)
        compose.onNodeWithTag("theme:dynamic").assertExists()
        assertTrue(settings.dynamicColor)
        scrollTo("theme:dynamic")
        compose.onNodeWithTag("theme:dynamic").performClick()
        compose.waitForIdle()
        assertFalse(settings.dynamicColor)
    }

    @Test
    fun aPresetAccentIsPickedFromTheGridAndBeatsAnyCustomColor() {
        showSettings()
        settings = settings.copy(customAccent = 0xFF336699.toInt())
        advance(200)
        scrollTo("accent:12")
        compose.onNodeWithTag("accent:12").performClick()
        compose.waitForIdle()
        assertEquals(12, settings.accentIndex)
        assertEquals(0, settings.customAccent)
        assertFalse(settings.autoAccent)
    }

    @Test
    fun theThreeSlidersMakeAColorOfYourOwn() {
        showSettings()
        scrollTo("accent:custom")
        compose.onNodeWithTag("accent:custom").performClick()
        compose.waitForIdle()
        advance(200)
        compose.onNodeWithTag("accent:sliders").assertExists()
        scrollTo("accent:hue")

        compose.onNodeWithTag("accent:hue").performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        compose.onNodeWithTag("accent:saturation").performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        compose.onNodeWithTag("accent:brightness").performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        compose.waitForIdle()
        advance(200)
        // Hue 180 at full strength and lightness is pure cyan, as an opaque color.
        assertEquals(0xFF00FFFF.toInt(), settings.customAccent)
        assertFalse(settings.autoAccent)

        // The way back to the 20 colors.
        scrollTo("accent:preset")
        compose.onNodeWithTag("accent:preset").performClick()
        compose.waitForIdle()
        assertEquals(0, settings.customAccent)
    }

    @Test
    fun draggingTheHueSliderMovesTheColor() {
        showSettings()
        scrollTo("accent:custom")
        compose.onNodeWithTag("accent:custom").performClick()
        compose.waitForIdle()
        advance(200)
        scrollTo("accent:hue")
        val before = settings.customAccent
        compose.onNodeWithTag("accent:hue").performTouchInput {
            swipe(Offset(width * 0.1f, centerY), Offset(width * 0.7f, centerY), durationMillis = 200)
        }
        compose.waitForIdle()
        assertNotEquals(before, settings.customAccent)
        assertTrue("a color was made", settings.customAccent != 0)
    }

    /** The settings have a title that stays put above the page. */
    private fun scrollTo(tag: String) = scrollIntoView(tag, topMargin = 0.2f)
}
