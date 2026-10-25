package com.metrowin.launcher.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.metrowin.launcher.data.GestureAction
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.data.RingerMode
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.ui.actioncenter.ActionCenter
import com.metrowin.launcher.ui.actioncenter.ActionControls
import com.metrowin.launcher.ui.gesture.rememberPullPanel
import com.metrowin.launcher.ui.settings.SettingsEnv
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.settings.SettingsSection
import com.metrowin.launcher.ui.start.ClockHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The sliders and the sound buttons of the Action Center, the app chosen for a gesture, and a touch on the clock. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class PanelControlsUiTest : ScreenshotBase() {
    private val log = mutableListOf<String>()

    @Composable
    private fun Center(controls: ActionControls?) {
        ActionCenter(
            slots = listOf(QuickAction.Wifi, QuickAction.Bluetooth, QuickAction.Screenshot, QuickAction.Flashlight),
            states = emptyMap(), notifications = emptyList(), appsByPackage = emptyMap(), hasNotificationAccess = true,
            panel = rememberPullPanel(initialProgress = 1f),
            onAction = {}, onAllSettings = {}, onOpenNotification = {}, onDismissNotification = {},
            onClearAll = {}, onTurnOnNotificationAccess = {}, onClose = {}, controls = controls,
        )
    }

    private fun controls(canSetBrightness: Boolean = true, ringer: RingerMode? = RingerMode.Normal) = ActionControls(
        volume = 0.4f, ringer = ringer, brightness = 0.6f, canSetBrightness = canSetBrightness,
        onVolume = { log += "volume:$it" }, onRinger = { log += "ringer:${it.name}" }, onBrightness = { log += "brightness:$it" },
        onAllowBrightness = { log += "allow" },
    )

    @Test
    fun theActionCenterHasTheSlidersAndTheSoundButtons() {
        log.clear()
        show { Center(controls()) }
        advance(500)
        capture("action_center_controls")
        compose.onNodeWithTag("ac:volume").assertExists()
        compose.onNodeWithTag("ac:brightness").assertExists()
        compose.onNodeWithTag("ac:ringer:Normal").assertExists()
        compose.onNodeWithTag("ac:ringer:Vibrate").performClick()
        compose.onNodeWithTag("ac:ringer:Silent").performClick()
        assertEquals(listOf("ringer:Vibrate", "ringer:Silent"), log)
    }

    @Test
    fun withoutTheAccessTheBrightnessAsksForItInsteadOfASlider() {
        log.clear()
        show { Center(controls(canSetBrightness = false)) }
        advance(500)
        capture("action_center_brightness_access")
        compose.onAllNodesWithTag("ac:brightness").assertCountEquals(0)
        compose.onNodeWithTag("ac:brightness:allow").performClick()
        assertEquals(listOf("allow"), log)
    }

    @Test
    fun withNoControlsThePanelIsAsItWas() {
        show { Center(null) }
        advance(500)
        compose.onAllNodesWithTag("ac:controls").assertCountEquals(0)
        compose.onNodeWithTag("quick:screenshot").assertExists()
    }

    @Test
    fun aPhoneWithoutASoundModeShowsNoButtons() {
        show { Center(controls(ringer = null)) }
        advance(500)
        compose.onAllNodesWithTag("ac:ringer:Normal").assertCountEquals(0)
        compose.onNodeWithTag("ac:volume").assertExists()
    }

    // --- the settings

    private var current = LauncherSettings()
    private val apps = listOf("Alpha", "Bravo", "Charlie").map { AppItem("app:$it", it, Launch.Action("x")) }

    @Composable
    private fun Gestures(section: SettingsSection = SettingsSection.Gestures) {
        var settings by remember { mutableStateOf(current) }
        SettingsScreen(
            env = SettingsEnv(settings = settings, onChange = { change -> settings = change(settings); current = settings }, apps = apps),
            section = section, onSectionChange = {}, sectionAway = remember { Animatable(0f) },
        )
    }

    private fun tap(tag: String) {
        scrollIntoView(tag, topMargin = 0.2f)
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
        advance(100)
    }

    @Test
    fun aGestureSetToOpenAnAppAsksWhichOne() {
        current = LauncherSettings()
        show { Gestures() }
        advance(500)
        compose.onAllNodesWithTag("gestures:doubletap:app:header").assertCountEquals(0)
        tap("gestures:doubletap:header")
        val openApp = GestureAction.entries.indexOf(GestureAction.OpenApp)
        tap("gestures:doubletap:$openApp")
        assertEquals(GestureAction.OpenApp, current.doubleTapAction)
        capture("gestures_open_app")
        tap("gestures:doubletap:app:header")
        tap("gestures:doubletap:app:app:Bravo")
        assertEquals("app:Bravo", current.doubleTapApp)
    }

    @Test
    fun theAppListOfAGestureCanBeSearched() {
        current = LauncherSettings(twoFingerUpAction = GestureAction.OpenApp)
        show { Gestures() }
        advance(500)
        tap("gestures:twoup:app:header")
        compose.onNode(
            androidx.compose.ui.test.hasSetTextAction() and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("gestures:twoup:app:query")),
        ).performTextInput("char")
        compose.waitForIdle()
        advance(200)
        compose.onNodeWithTag("gestures:twoup:app:app:Charlie").assertExists()
        compose.onAllNodesWithTag("gestures:twoup:app:app:Alpha").assertCountEquals(0)
    }

    @Test
    fun theSlidersAndTheClockTouchCanBeSwitchedOff() {
        current = LauncherSettings(showClock = true)
        show { Gestures(SettingsSection.Actions) }
        advance(500)
        tap("actions:sliders")
        assertFalse(current.actionSliders)
        tap("actions:sliders")
        assertTrue(current.actionSliders)
    }

    // --- the clock

    @Test
    fun aTouchOnTheTimeOrTheDateCallsItsHandler() {
        log.clear()
        show { ClockHeader(onTimeClick = { log += "time" }, onDateClick = { log += "date" }) }
        advance(300)
        compose.onNodeWithTag("clock:time").performClick()
        compose.onNodeWithTag("clock:date").performClick()
        assertEquals(listOf("time", "date"), log)
    }

    @Test
    fun withoutHandlersTheClockIsPlainText() {
        show { ClockHeader() }
        advance(300)
        compose.onAllNodesWithTag("clock:time").assertCountEquals(0)
        compose.onAllNodesWithTag("clock:date").assertCountEquals(0)
    }
}
