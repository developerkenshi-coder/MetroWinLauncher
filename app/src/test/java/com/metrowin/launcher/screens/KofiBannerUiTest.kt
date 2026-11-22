package com.metrowin.launcher.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.metrowin.launcher.ui.KofiBanner
import com.metrowin.launcher.ui.KofiBannerDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The invitation to follow the author on Ko-fi: shown once, after the update to 26.10.5S, with the sign, the words and the two buttons. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class KofiBannerUiTest : ScreenshotBase() {
    private val hour = 3_600_000L
    private val installed = 1_000_000_000_000L

    @Test
    fun anUpdateToTheVersionAlwaysGetsItAtOnceWhateverElseIsTrue() {
        // Installed long ago and updated just now (last update > first install): due at the first look, with no wait and no tour condition.
        assertTrue(KofiBanner.due(seen = false, versionCode = 78, firstInstall = installed, lastUpdate = installed + 300 * hour, now = installed + 300 * hour))
        assertTrue(KofiBanner.due(seen = false, versionCode = 79, firstInstall = installed, lastUpdate = installed + 2 * hour, now = installed + 2 * hour))
    }

    @Test
    fun aFreshInstallWaitsFortyEightHours() {
        // First install and last update are the same moment.
        assertFalse(KofiBanner.due(seen = false, versionCode = 78, firstInstall = installed, lastUpdate = installed, now = installed))
        assertFalse(KofiBanner.due(seen = false, versionCode = 78, firstInstall = installed, lastUpdate = installed, now = installed + 47 * hour))
        assertTrue(KofiBanner.due(seen = false, versionCode = 78, firstInstall = installed, lastUpdate = installed, now = installed + 48 * hour))
        assertTrue(KofiBanner.due(seen = false, versionCode = 78, firstInstall = installed, lastUpdate = installed, now = installed + 500 * hour))
    }

    @Test
    fun onceOnlyAndNotBeforeTheVersion() {
        assertFalse(KofiBanner.due(seen = true, versionCode = 78, firstInstall = installed, lastUpdate = installed + 300 * hour, now = installed + 300 * hour))
        assertFalse(KofiBanner.due(seen = true, versionCode = 78, firstInstall = installed, lastUpdate = installed, now = installed + 500 * hour))
        assertFalse(KofiBanner.due(seen = false, versionCode = 77, firstInstall = installed, lastUpdate = installed + 300 * hour, now = installed + 300 * hour))
    }

    @Test
    fun theAddressIsThePageOfTheAuthor() {
        assertEquals("https://ko-fi.com/kenshi1299", KofiBanner.URL)
    }

    @Test
    fun theBannerShowsTheSignTheTextAndTheTwoButtons() {
        var opened = 0
        var closed = 0
        show { KofiBannerDialog(onOpen = { opened++ }, onClose = { closed++ }) }
        advance(600)
        compose.onNodeWithTag("kofi:image").assertIsDisplayed()
        compose.onNodeWithTag("kofi:text").assertIsDisplayed()
        compose.onNodeWithTag("kofi:open").assertIsDisplayed()
        compose.onNodeWithTag("kofi:open").performClick()
        assertEquals(1, opened)
        compose.onNodeWithTag("kofi:close").performClick()
        advance(600)
        assertEquals(1, closed)
    }
}
