package com.metrowin.launcher

import com.metrowin.launcher.data.MiniApps
import com.metrowin.launcher.data.QuickAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickMiniActionsTest {
    @Test fun everyMiniButtonNamesAMiniAppThatExists() {
        val minis = QuickAction.entries.filter { QuickAction.miniApp(it) != null }
        assertEquals(listOf(QuickAction.Recorder, QuickAction.Notes, QuickAction.Calculator), minis)
        minis.forEach { assertNotNull(it.id, MiniApps.activities[QuickAction.miniApp(it)]) }
    }

    @Test fun otherButtonsAreNotMiniApps() {
        assertNull(QuickAction.miniApp(QuickAction.Wifi))
        assertNull(QuickAction.miniApp(QuickAction.Camera))
    }

    @Test fun aMiniButtonCanBeChosenForTheTop() {
        assertTrue(QuickAction.Calculator in QuickAction.slots(listOf("calculator", "wifi")))
        assertEquals(QuickAction.Recorder, QuickAction.slots(listOf("recorder")).first())
    }
}
