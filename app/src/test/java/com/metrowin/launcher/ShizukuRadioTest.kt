package com.metrowin.launcher

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.data.QuickActionsController
import com.metrowin.launcher.data.ShizukuRadio
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShizukuRadioTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun shizukuIsUsedOnlyWhenAskedForRunningAndAllowed() {
        assertTrue(ShizukuRadio.route(useShizuku = true, running = true, granted = true))
        assertFalse(ShizukuRadio.route(useShizuku = false, running = true, granted = true))
        assertFalse(ShizukuRadio.route(useShizuku = true, running = false, granted = true))
        assertFalse(ShizukuRadio.route(useShizuku = true, running = true, granted = false))
        assertFalse(ShizukuRadio.route(useShizuku = true, running = false, granted = false))
    }

    @Test
    fun theCommandsAreTheOnesOfTheShell() {
        assertArrayEquals(arrayOf("svc", "wifi", "enable"), ShizukuRadio.command(QuickAction.Wifi, true))
        assertArrayEquals(arrayOf("svc", "wifi", "disable"), ShizukuRadio.command(QuickAction.Wifi, false))
        assertArrayEquals(arrayOf("svc", "bluetooth", "enable"), ShizukuRadio.command(QuickAction.Bluetooth, true))
        assertNull(ShizukuRadio.command(QuickAction.Flashlight, true))
    }

    @Test
    fun withoutShizukuRunningNothingIsThereToAsk() {
        assertFalse(ShizukuRadio.running())
        assertFalse(ShizukuRadio.granted())
    }

    @Test
    fun withTheSwitchOnButNoShizukuTheButtonsOpenThePanelsAsBefore() {
        val controller = QuickActionsController(app)
        assertFalse(controller.activate(QuickAction.Wifi, useShizuku = true))
        assertEquals("android.settings.panel.action.WIFI", shadowOf(app).nextStartedActivity?.action)
        assertFalse(controller.activate(QuickAction.Bluetooth, useShizuku = true))
        assertEquals("android.settings.BLUETOOTH_SETTINGS", shadowOf(app).nextStartedActivity?.action)
    }
}
