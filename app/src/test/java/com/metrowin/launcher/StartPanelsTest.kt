package com.metrowin.launcher

import com.metrowin.launcher.data.StartPanels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartPanelsTest {
    @Test fun aTabletInLandscapeHasPanels() = assertTrue(StartPanels.active(true, 1280f, 800f))

    @Test fun aPhoneUprightOrOnItsSideKeepsThePages() {
        assertFalse(StartPanels.active(true, 360f, 800f))
        assertFalse(StartPanels.active(true, 915f, 412f))
    }

    @Test fun aMediumWindowKeepsThePages() = assertFalse(StartPanels.active(true, 700f, 1000f))

    @Test fun theSettingTurnsThemOff() = assertFalse(StartPanels.active(false, 1280f, 800f))

    @Test fun tilesGetHalfTheWidthOnlyWithPanels() {
        assertEquals(640f, StartPanels.startWidthDp(true, 1280f), 0f)
        assertEquals(1280f, StartPanels.startWidthDp(false, 1280f), 0f)
    }
}
