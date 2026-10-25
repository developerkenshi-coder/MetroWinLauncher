package com.metrowin.launcher.screens

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.gestures.LockAccessibilityService
import com.metrowin.launcher.ui.actioncenter.ActionCenter
import com.metrowin.launcher.ui.gesture.rememberPullPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The recent apps at once: a button in the Action Center and an action for the gestures. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class RecentsTest : ScreenshotBase() {
    private val pressed = mutableListOf<QuickAction>()

    @Test
    fun theActionCenterCanHaveARecentAppsButton() {
        show {
            ActionCenter(
                slots = QuickAction.slots(listOf("wifi", "bluetooth", "flashlight", "recents")),
                states = emptyMap(), notifications = emptyList(), appsByPackage = emptyMap(),
                hasNotificationAccess = true, panel = rememberPullPanel(initialProgress = 1f),
                onAction = { pressed += it }, onAllSettings = {}, onOpenNotification = {}, onDismissNotification = {},
                onClearAll = {}, onTurnOnNotificationAccess = {}, onClose = {},
            )
        }
        advance(300)
        capture("action_center_recents_button")
        compose.onNodeWithTag("quick:recents").assertExists()
        compose.onNodeWithTag("quick:recents").performClick()
        assertEquals(listOf(QuickAction.Recents), pressed)
    }

    @Test
    fun theButtonIsOneOfTheChoicesAndNotOneOfTheDefaults() {
        assertNotNull(QuickAction.fromId("recents"))
        assertFalse(QuickAction.Recents in QuickAction.defaults)
        assertTrue(QuickAction.Recents in QuickAction.slots(listOf("recents")))
        // It has no state to show, as the launcher's own settings button.
        assertEquals(4, QuickAction.slots(listOf("recents", "wifi")).size)
    }

    @Test
    fun withoutTheServiceTheRecentAppsDoNotOpenAndSaySo() {
        // The launcher then asks the user to turn the service on; here there is no service to turn on.
        assertFalse(LockAccessibilityService.recents())
        assertFalse(LockAccessibilityService.lock())
    }
}
