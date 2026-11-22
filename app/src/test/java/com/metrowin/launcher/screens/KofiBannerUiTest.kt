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
    @Test
    fun onlyAnUpdateOfTheRightVersionThatWasNotSeenShowsIt() {
        // Updating from before: the tour was done, the banner not seen yet, this is 26.10.5S (78) or later.
        assertTrue(KofiBanner.due(seen = false, tutorialDone = true, versionCode = 78))
        assertTrue(KofiBanner.due(seen = false, tutorialDone = true, versionCode = 79))
        // Once: never again.
        assertFalse(KofiBanner.due(seen = true, tutorialDone = true, versionCode = 78))
        // A fresh install has the tour, not this.
        assertFalse(KofiBanner.due(seen = false, tutorialDone = false, versionCode = 78))
        // An older version does not have it.
        assertFalse(KofiBanner.due(seen = false, tutorialDone = true, versionCode = 77))
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
