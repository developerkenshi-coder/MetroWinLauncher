package com.metrowin.launcher

import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.SettingsCodec
import com.metrowin.launcher.ui.gesture.panoramaVisibility
import org.junit.Assert.assertEquals
import org.junit.Test

class PanoramaAndHomeTest {
    @Test
    fun aTitleIsSeenOnItsPagesAndGoneBeforeTheNextOneArrives() {
        // "start" over screens 0 and 1, "apps" over page 2.
        assertEquals(1f, panoramaVisibility(0, 1, 1f), 0f)
        assertEquals(0f, panoramaVisibility(2, 2, 1f), 0f)
        assertEquals(0.5f, panoramaVisibility(2, 2, 1.75f), 0.001f)
        assertEquals(0f, panoramaVisibility(0, 1, 1.5f), 0f)
        // Halfway between two pages neither shows, so the two can never cross.
        assertEquals(0f, panoramaVisibility(0, 0, 0.5f) + panoramaVisibility(1, 1, 0.5f), 0f)
    }

    @Test
    fun theHomeScreenSurvivesABackup() {
        val back = SettingsCodec.decode(SettingsCodec.encode(LauncherSettings(startPages = 3, homePage = 1)))!!
        assertEquals(1, back.homePage)
        assertEquals(0, LauncherSettings().homePage)
    }

    @Test
    fun largeTileIconsAreOffUntilTurnedOnAndSurviveABackup() {
        assertEquals(false, LauncherSettings().largeTileIcons)
        val back = SettingsCodec.decode(SettingsCodec.encode(LauncherSettings(largeTileIcons = true)))!!
        assertEquals(true, back.largeTileIcons)
    }

    @Test
    fun theNewSwitchesSurviveABackupAndTheHoldTimeStaysInItsRange() {
        val on = LauncherSettings(navHoldMs = 900, appListLeft = true, antiBurnIn = false, lockOverlay = false)
        val back = SettingsCodec.decode(SettingsCodec.encode(on))!!
        assertEquals(on.copy(), back.copy(homePage = on.homePage))
        // A time outside 0.3 to 1.5 seconds (a backup of somewhere else) is brought into it.
        val wild = SettingsCodec.decode("{\"navHoldMs\":50}")!!
        assertEquals(LauncherSettings.NAV_HOLD_MIN_MS, wild.navHoldMs)
        assertEquals(LauncherSettings.NAV_HOLD_MAX_MS, SettingsCodec.decode("{\"navHoldMs\":99999}")!!.navHoldMs)
        assertEquals(true, LauncherSettings().antiBurnIn)
    }

    @Test
    fun theBurnInRingComesBackToItsStartAndTheTitlesGoTheOtherWayWhenTheListIsOnTheLeft() {
        assertEquals(com.metrowin.launcher.ui.components.BurnIn.at(0), com.metrowin.launcher.ui.components.BurnIn.at(com.metrowin.launcher.ui.components.BurnIn.steps.size))
        val right = com.metrowin.launcher.ui.gesture.panoramaOffset(anchor = 0, position = 1f, pageWidth = 360f)
        val left = com.metrowin.launcher.ui.gesture.panoramaOffset(anchor = 0, position = 1f, pageWidth = 360f, reverse = true)
        assertEquals(-right, left, 0f)
    }
}
