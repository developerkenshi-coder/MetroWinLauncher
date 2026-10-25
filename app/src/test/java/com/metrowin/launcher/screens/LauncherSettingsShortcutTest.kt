package com.metrowin.launcher.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.ui.actioncenter.ActionCenter
import com.metrowin.launcher.ui.gesture.rememberPullPanel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class LauncherSettingsShortcutTest : ScreenshotBase() {
    private val pressed = mutableListOf<QuickAction>()

    @Composable
    private fun Center() {
        ActionCenter(
            slots = QuickAction.defaults, states = emptyMap(), notifications = emptyList(), appsByPackage = emptyMap(),
            hasNotificationAccess = true, panel = rememberPullPanel(initialProgress = 1f),
            onAction = { pressed += it }, onAllSettings = {}, onOpenNotification = {}, onDismissNotification = {},
            onClearAll = {}, onTurnOnNotificationAccess = {}, onClose = {},
        )
    }

    @Test
    fun theActionCenterHasAButtonForTheLauncherSettingsAmongTheFirstFour() {
        show { Center() }
        advance(300)
        capture("action_center_launcher_button")
        compose.onNodeWithTag("quick:launcher").assertExists()
        compose.onNodeWithTag("quick:launcher").performClick()
        assertEquals(listOf(QuickAction.LauncherSettings), pressed)
    }
}
