package com.metrowin.launcher

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.data.QuickActionsController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuickActionPanelsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun started(action: QuickAction): String? {
        val done = QuickActionsController(app).activate(action)
        assertFalse("a radio is never switched on the spot", done)
        return shadowOf(app).nextStartedActivity?.action
    }

    @Test
    fun wifiOpensThePanelOfAndroidNotTheWholeSettings() {
        assertEquals("android.settings.panel.action.WIFI", started(QuickAction.Wifi))
    }

    @Test
    fun mobileDataAndFlightModeOpenThePanelThatHasBothSwitches() {
        assertEquals("android.settings.panel.action.INTERNET_CONNECTIVITY", started(QuickAction.MobileData))
        assertEquals("android.settings.panel.action.INTERNET_CONNECTIVITY", started(QuickAction.Airplane))
    }

    @Test
    fun mobileDataIsAButtonThatCanBeKeptOnStartAndCalledByName() {
        assertTrue(QuickAction.MobileData in QuickAction.toggles)
        assertEquals("mobiledata", QuickAction.MobileData.id)
        assertEquals(QuickAction.MobileData, com.metrowin.launcher.data.SystemCommands.of("mobile data")?.action)
        assertEquals(QuickAction.MobileData, com.metrowin.launcher.data.SystemCommands.of("spegni dati mobili")?.action)
    }
}
