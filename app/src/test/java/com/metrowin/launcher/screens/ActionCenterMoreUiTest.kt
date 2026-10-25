package com.metrowin.launcher.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.notifications.NotificationEntry
import com.metrowin.launcher.notifications.Snooze
import com.metrowin.launcher.ui.actioncenter.ActionCenter
import com.metrowin.launcher.ui.actioncenter.ActionControls
import com.metrowin.launcher.ui.gesture.rememberPullPanel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The slider of the flashlight, and what a long press on a notification offers: snooze and the settings of its app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class ActionCenterMoreUiTest : ScreenshotBase() {
    private val entry = NotificationEntry("k1", "com.example.chat", "Anna", "See you at eight?", System.currentTimeMillis() - 120_000L, null, false)
    private val opened = mutableListOf<String>()

    @Composable
    private fun Center(
        torch: Float? = null,
        onTorch: (Float) -> Unit = {},
        onSnooze: ((NotificationEntry, Snooze.Option) -> Unit)? = null,
        onSettings: ((NotificationEntry) -> Unit)? = null,
    ) {
        ActionCenter(
            slots = listOf(QuickAction.Wifi, QuickAction.Bluetooth, QuickAction.QuietHours, QuickAction.Flashlight),
            states = emptyMap(), notifications = listOf(entry), appsByPackage = emptyMap(), hasNotificationAccess = true,
            panel = rememberPullPanel(initialProgress = 1f),
            onAction = {}, onAllSettings = {}, onOpenNotification = { opened += it.key }, onDismissNotification = {},
            onClearAll = {}, onTurnOnNotificationAccess = {}, onClose = {},
            controls = ActionControls(
                volume = 0.4f, ringer = null, brightness = null, canSetBrightness = true,
                onVolume = {}, onRinger = {}, onBrightness = {}, onAllowBrightness = {}, torch = torch, onTorch = onTorch,
            ),
            onSnooze = onSnooze, onAppNotificationSettings = onSettings,
        )
    }

    private fun longPress() {
        // On the line of the title: once the choices are open the notification is taller and its middle is not the title any more.
        compose.onNodeWithTag("notification:k1").performTouchInput { longClick(androidx.compose.ui.geometry.Offset(centerX, 40f)) }
        compose.waitForIdle()
        advance(300)
    }

    @Test
    fun theFlashlightHasASliderOnlyWhileItHasOne() {
        show { Center(torch = 0.5f) }
        advance(500)
        capture("action_center_torch")
        compose.onNodeWithTag("ac:torch").assertExists()
    }

    @Test
    fun withoutAStrengthThereIsNoTorchSlider() {
        show { Center(torch = null) }
        advance(500)
        compose.onAllNodesWithTag("ac:torch").assertCountEquals(0)
    }

    @Test
    fun aLongPressOnANotificationOffersSnoozeAndTheSettingsOfItsApp() {
        val snoozed = mutableListOf<Pair<String, Snooze.Option>>()
        val settings = mutableListOf<String>()
        show { Center(onSnooze = { e, o -> snoozed += e.key to o }, onSettings = { settings += it.packageName }) }
        advance(500)
        compose.onAllNodesWithTag("notification:more:k1").assertCountEquals(0)
        longPress()
        capture("notification_more")
        compose.onNodeWithTag("notification:more:k1").assertExists()
        Snooze.Option.entries.forEach { compose.onNodeWithTag("notification:snooze:${it.name}:k1").assertExists() }
        compose.onNodeWithTag("notification:snooze:Hour:k1").performClick()
        compose.onNodeWithTag("notification:appsettings:k1").performClick()
        assertEquals(listOf("k1" to Snooze.Option.Hour), snoozed)
        assertEquals(listOf("com.example.chat"), settings)
        assertEquals("a long press does not open the notification", emptyList<String>(), opened)
    }

    @Test
    fun aSecondLongPressPutsTheChoicesAway() {
        show { Center(onSnooze = { _, _ -> }, onSettings = {}) }
        advance(500)
        longPress()
        compose.onNodeWithTag("notification:more:k1").assertExists()
        longPress()
        compose.onAllNodesWithTag("notification:more:k1").assertCountEquals(0)
    }

    @Test
    fun withNothingToOfferALongPressDoesNothing() {
        show { Center() }
        advance(500)
        longPress()
        compose.onAllNodesWithTag("notification:more:k1").assertCountEquals(0)
    }

    @Test
    fun aTapStillOpensTheNotification() {
        opened.clear()
        show { Center(onSnooze = { _, _ -> }, onSettings = {}) }
        advance(500)
        compose.onNodeWithTag("notification:k1").performClick()
        assertEquals(listOf("k1"), opened)
    }
}
