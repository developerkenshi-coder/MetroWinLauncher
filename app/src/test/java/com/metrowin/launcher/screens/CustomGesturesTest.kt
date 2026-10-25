package com.metrowin.launcher.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.GestureAction
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.gesture.twoFingerSwipe
import com.metrowin.launcher.ui.settings.SettingsEnv
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.settings.SettingsSection
import com.metrowin.launcher.ui.start.StartActions
import com.metrowin.launcher.ui.start.StartScreen
import com.metrowin.launcher.ui.start.buildStartCells
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The two-finger swipes, the double tap on Start, and the settings that choose what they do. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class CustomGesturesTest : ScreenshotBase() {
    private var down = 0
    private var up = 0

    private fun showSwipeArea(enabled: Boolean = true) = show {
        Box(
            Modifier
                .size(360.dp, 760.dp)
                .twoFingerSwipe(enabled, thresholdPx = 150f, onDown = { down++ }, onUp = { up++ })
                .background(Color.DarkGray),
        )
    }

    private fun twoFingers(dy0: Float, dy1: Float, dx0: Float = 0f, dx1: Float = 0f) {
        compose.onRoot().performTouchInput {
            down(0, Offset(300f, 1000f))
            down(1, Offset(700f, 1000f))
            moveBy(0, Offset(dx0, dy0))
            moveBy(1, Offset(dx1, dy1))
            up(0)
            up(1)
        }
        advance(200)
    }

    // --- two fingers

    @Test
    fun twoFingersSwipingDownTogetherCallTheDownAction() {
        showSwipeArea()
        advance(300)
        twoFingers(300f, 300f)
        assertEquals(1, down)
        assertEquals(0, up)
    }

    @Test
    fun twoFingersSwipingUpTogetherCallTheUpAction() {
        showSwipeArea()
        advance(300)
        twoFingers(-300f, -320f)
        assertEquals(0, down)
        assertEquals(1, up)
    }

    @Test
    fun theActionIsCalledOnceEvenIfTheSwipeGoesOn() {
        showSwipeArea()
        advance(300)
        compose.onRoot().performTouchInput {
            down(0, Offset(300f, 600f))
            down(1, Offset(700f, 600f))
            moveBy(0, Offset(0f, 300f))
            moveBy(1, Offset(0f, 300f))
            moveBy(0, Offset(0f, 300f))
            moveBy(1, Offset(0f, 300f))
            up(0)
            up(1)
        }
        advance(200)
        assertEquals(1, down)
    }

    @Test
    fun oneFingerNeverMakesATwoFingerSwipe() {
        showSwipeArea()
        advance(300)
        compose.onRoot().performTouchInput {
            down(0, Offset(500f, 600f))
            moveBy(0, Offset(0f, 800f))
            up(0)
        }
        advance(200)
        assertEquals(0, down + up)
    }

    @Test
    fun aPinchIsNotASwipe() {
        showSwipeArea()
        advance(300)
        twoFingers(dy0 = 0f, dy1 = 0f, dx0 = -300f, dx1 = 300f)
        assertEquals(0, down + up)
    }

    @Test
    fun twoFingersThatBarelyMoveAreNotASwipe() {
        showSwipeArea()
        advance(300)
        twoFingers(60f, 70f)
        assertEquals(0, down + up)
    }

    @Test
    fun whenItIsOffNothingIsWatched() {
        showSwipeArea(enabled = false)
        advance(300)
        twoFingers(300f, 300f)
        assertEquals(0, down + up)
    }

    // --- double tap on Start

    private val items = listOf("A", "B").map { AppItem(it, "App $it", Launch.Action("x"), Glyph.entries[it[0] - 'A' + 5]) }
    private var backgroundTaps = 0
    private var doubleTaps = 0

    private fun showStart(withDoubleTap: Boolean) = show {
        StartScreen(
            cells = buildStartCells(items.map { Tile(it.id, TileSize.Medium) }, items, null),
            live = LiveTileData(), badges = emptyMap(), columns = 4, background = null, enterToken = 0, exitRequest = null,
            editingKey = null, scrollState = rememberScrollState(),
            actions = StartActions(
                onClick = {}, onLongClick = {}, onUnpin = {}, onResize = {}, onRename = {},
                onMove = { _, _ -> }, onMerge = { _, _ -> }, onBackgroundTap = { backgroundTaps++ },
                onDoubleTap = if (withDoubleTap) ({ doubleTaps++ }) else null,
                onNextPage = {},
            ),
        )
    }

    @Test
    fun aDoubleTapOnTheEmptyPartOfStartCallsItsAction() {
        showStart(withDoubleTap = true)
        advance(2_000)
        compose.onRoot().performTouchInput { doubleClick(Offset(540f, 1700f)) }
        advance(600)
        assertEquals(1, doubleTaps)
        assertEquals("a double tap is not also a tap", 0, backgroundTaps)
    }

    @Test
    fun withADoubleTapSetASingleTapIsUnderstoodAfterAMoment() {
        showStart(withDoubleTap = true)
        advance(2_000)
        compose.onRoot().performTouchInput { click(Offset(540f, 1700f)) }
        advance(50)
        assertEquals("not yet: it could be the first half of a double tap", 0, backgroundTaps)
        advance(600)
        assertEquals(1, backgroundTaps)
        assertEquals(0, doubleTaps)
    }

    @Test
    fun withoutADoubleTapASingleTapIsImmediate() {
        showStart(withDoubleTap = false)
        advance(2_000)
        compose.onRoot().performTouchInput { click(Offset(540f, 1700f)) }
        advance(30)
        assertEquals(1, backgroundTaps)
    }

    // --- the settings

    private var current = LauncherSettings()

    @Composable
    private fun GesturesSettings(lockEnabled: Boolean = false, onAccessibility: () -> Unit = {}) {
        var settings by remember { mutableStateOf(current) }
        SettingsScreen(
            env = SettingsEnv(
                settings = settings,
                onChange = { change ->
                    settings = change(settings)
                    current = settings
                },
                lockServiceEnabled = lockEnabled,
                onOpenAccessibilitySettings = onAccessibility,
            ),
            section = SettingsSection.Gestures,
            onSectionChange = {},
            sectionAway = remember { Animatable(0f) },
        )
    }

    @Test
    fun eachGestureHasAChoiceOfActionsThatUnfoldsWhenTouched() {
        current = LauncherSettings()
        show { GesturesSettings() }
        advance(500)
        capture("settings_gestures_actions")
        compose.onNodeWithTag("gestures:doubletap:header").performClick()
        advance(100)
        compose.onNodeWithTag("gestures:doubletap:${GestureAction.LockScreen.ordinal}").performClick()
        assertEquals(GestureAction.LockScreen, current.doubleTapAction)
        advance(100)
        compose.onAllNodesWithTag("gestures:doubletap:0").assertCountEquals(0) // folded again
    }

    @Test
    fun theRecentAppsAreOneOfTheActionsAGestureCanHave() {
        current = LauncherSettings()
        show { GesturesSettings() }
        advance(500)
        compose.onNodeWithTag("gestures:twoup:header").performClick()
        advance(100)
        // The list is longer than the screen: scroll to the option before touching it.
        scrollIntoView("gestures:twoup:${GestureAction.Recents.ordinal}", topMargin = 0.2f)
        compose.onNodeWithTag("gestures:twoup:${GestureAction.Recents.ordinal}").performClick()
        compose.waitForIdle()
        assertEquals(GestureAction.Recents, current.twoFingerUpAction)
    }

    @Test
    fun theThreeGesturesAreSetApart() {
        current = LauncherSettings()
        show { GesturesSettings() }
        advance(500)
        compose.onNodeWithTag("gestures:twodown:header").performClick()
        advance(100)
        scrollIntoView("gestures:twodown:${GestureAction.ActionCenter.ordinal}", topMargin = 0.2f)
        compose.onNodeWithTag("gestures:twodown:${GestureAction.ActionCenter.ordinal}").performClick()
        compose.waitForIdle()
        advance(100)
        scrollIntoView("gestures:twoup:header", topMargin = 0.2f)
        compose.onNodeWithTag("gestures:twoup:header").performClick()
        advance(100)
        scrollIntoView("gestures:twoup:${GestureAction.AppList.ordinal}", topMargin = 0.2f)
        compose.onNodeWithTag("gestures:twoup:${GestureAction.AppList.ordinal}").performClick()
        compose.waitForIdle()
        assertEquals(GestureAction.None, current.doubleTapAction)
        assertEquals(GestureAction.ActionCenter, current.twoFingerDownAction)
        assertEquals(GestureAction.AppList, current.twoFingerUpAction)
    }
}
