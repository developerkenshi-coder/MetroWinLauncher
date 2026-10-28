package com.metrowin.launcher.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.ui.actioncenter.ActionCenter
import com.metrowin.launcher.ui.actioncenter.ActionControls
import com.metrowin.launcher.ui.gesture.rememberPullPanel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Arranging the buttons in the panel, the strength of the flashlight, and the settings of Android next to those of the launcher. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class ActionCenterRoundFiveUiTest : ScreenshotBase() {
    private val held = mutableListOf<QuickAction>()
    private val torch = mutableListOf<Float>()
    private var allSettings = 0

    private fun controls(levels: Int) = ActionControls(
        volume = 0.5f, ringer = null, brightness = 0.5f, canSetBrightness = true,
        onVolume = {}, onRinger = {}, onBrightness = {}, onAllowBrightness = {},
        onTorch = { torch += it }, torchLevels = levels, torchPreferred = 0.6f,
    )

    @Composable
    private fun Center(order: List<QuickAction>, onOrder: ((List<QuickAction>) -> Unit)?, levels: Int = 5) {
        ActionCenter(
            slots = order.take(QuickAction.SLOTS), order = order, onOrder = onOrder,
            states = emptyMap(), notifications = emptyList(), appsByPackage = emptyMap(),
            hasNotificationAccess = true, panel = rememberPullPanel(initialProgress = 1f),
            onAction = {}, onAllSettings = { allSettings++ }, onOpenNotification = {}, onDismissNotification = {},
            onClearAll = {}, onTurnOnNotificationAccess = {}, onClose = {},
            onHoldAction = { held += it }, controls = controls(levels),
        )
    }

    @Test
    fun aButtonIsMovedWithTwoTouchesInTheArrangeMode() {
        var saved: List<QuickAction>? = null
        val start = QuickAction.order(emptyList())
        show { Center(start, onOrder = { saved = it }) }
        advance(300)
        compose.onNodeWithTag("ac:arrange").performClick()
        advance(500)
        capture("action_center_arrange")
        compose.onNodeWithTag("ac:arrange:hint").assertExists()
        // The last button goes to the first place.
        compose.onNodeWithTag("quick:${start.last().id}").performClick()
        compose.onNodeWithTag("quick:${start.first().id}").performClick()
        assertEquals(QuickAction.move(start, start.lastIndex, 0), saved)
    }

    @Test
    fun withoutAWayToKeepAnOrderThereIsNoArrangeButton() {
        show { Center(QuickAction.order(emptyList()), onOrder = null) }
        advance(300)
        compose.onAllNodesWithTag("ac:arrange").assertCountEquals(0)
    }

    @Test
    fun aLongPressOnTheFlashlightOpensItsStrengthAndNotTheCameraWhenTheLightHasSteps() {
        show { Center(listOf(QuickAction.Flashlight, QuickAction.Wifi, QuickAction.Bluetooth, QuickAction.Airplane), onOrder = {}) }
        advance(300)
        compose.onNodeWithTag("quick:flashlight").performTouchInput { longClick() }
        advance(600)
        capture("action_center_torch_strength")
        compose.onNodeWithTag("ac:torchpanel").assertExists()
        assertEquals(emptyList<QuickAction>(), held)
    }

    @Test
    fun aFlashlightWithOneStrengthKeepsTheScreenOfItsOwn() {
        show { Center(listOf(QuickAction.Flashlight, QuickAction.Wifi, QuickAction.Bluetooth, QuickAction.Airplane), onOrder = {}, levels = 1) }
        advance(300)
        compose.onNodeWithTag("quick:flashlight").performTouchInput { longClick() }
        advance(300)
        compose.onAllNodesWithTag("ac:torchpanel").assertCountEquals(0)
        assertEquals(listOf(QuickAction.Flashlight), held)
    }

    @Test
    fun theSettingsOfAndroidHaveTheirOwnLinkWithAPictureAndTheirOwnButton() {
        show { Center(QuickAction.order(emptyList()), onOrder = {}) }
        advance(300)
        compose.onNodeWithTag("ac:allsettings").performClick()
        assertEquals(1, allSettings)
        assertEquals(true, QuickAction.DeviceSettings.glyph != QuickAction.LauncherSettings.glyph)
        assertEquals(true, QuickAction.DeviceSettings in QuickAction.order(emptyList()))
    }
}
