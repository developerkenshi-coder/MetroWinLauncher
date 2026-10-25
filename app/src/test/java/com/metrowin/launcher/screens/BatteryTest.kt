package com.metrowin.launcher.screens

import android.app.Application
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.LauncherViewModel
import com.metrowin.launcher.R
import com.metrowin.launcher.data.BatterySaving
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.ui.anim.FlipContainer
import com.metrowin.launcher.ui.anim.LaunchedWhileStarted
import com.metrowin.launcher.ui.anim.LocalPowerSaving
import com.metrowin.launcher.ui.settings.SettingsEnv
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.settings.SettingsSection
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** What the launcher does, and above all does not do, to spare the battery. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class BatteryTest : ScreenshotBase() {
    private fun moveTo(state: Lifecycle.State) {
        compose.activityRule.scenario.moveToState(state)
    }

    /** The face the tile showed when it was last composed. Read from here, not from the screen: a stopped activity has no hierarchy to look at. */
    private var faceShown = -1

    @androidx.compose.runtime.Composable
    private fun Tile() = FlipContainer(faces = 2, seed = 0) { face ->
        faceShown = face
        Box(Modifier.testTag("face:$face"))
    }

    // --- timers that stop

    @Test
    fun aTileFlipsByItselfWhileTheLauncherIsOnScreen() {
        show { Tile() }
        advance(500)
        assertEquals(0, faceShown)
        advance(3_500)
        assertEquals("flipped once", 1, faceShown)
    }

    @Test
    fun aTileDoesNotFlipWhileSavingBattery() {
        show { CompositionLocalProvider(LocalPowerSaving provides true) { Tile() } }
        advance(60_000)
        assertEquals(0, faceShown)
    }

    @Test
    fun aTileStopsFlippingWhenAnotherAppIsInFrontAndGoesOnWhenItIsBack() {
        show { Tile() }
        advance(500)
        moveTo(Lifecycle.State.CREATED)
        advance(60_000)
        assertEquals("nothing moved while the launcher was off screen", 0, faceShown)
        moveTo(Lifecycle.State.RESUMED)
        advance(4_000)
        assertEquals("and it flips again once it is back", 1, faceShown)
    }

    @Test
    fun aTimerInWhileStartedDoesNotTickOffScreenAndStartsOverWhenBack() {
        var ticks by mutableIntStateOf(0)
        var starts by mutableIntStateOf(0)
        show {
            LaunchedWhileStarted(Unit) {
                starts++
                while (true) {
                    delay(1_000)
                    ticks++
                }
            }
        }
        advance(3_500)
        assertEquals(3, ticks)
        moveTo(Lifecycle.State.CREATED)
        val before = ticks
        advance(30_000)
        assertEquals("not a tick while off screen", before, ticks)
        moveTo(Lifecycle.State.RESUMED)
        advance(2_500)
        assertEquals(before + 2, ticks)
        assertEquals("it began again from the top", 2, starts)
    }

    // --- the receivers

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun listening(action: String) =
        shadowOf(app).registeredReceivers.any { it.intentFilter.hasAction(action) }

    @Test
    fun theBatteryAndPowerSaverListenersAreOnlyRegisteredWhileTheLauncherIsOnScreen() {
        val vm = LauncherViewModel(app)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("asleep before it is shown", listening(Intent.ACTION_BATTERY_CHANGED))
        assertFalse(listening(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))

        vm.onStart()
        assertTrue("listening while on screen", listening(Intent.ACTION_BATTERY_CHANGED))
        assertTrue(listening(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))

        vm.onStop()
        assertFalse("and asleep again behind another app", listening(Intent.ACTION_BATTERY_CHANGED))
        assertFalse(listening(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))

        // Stopping twice, or starting twice, is harmless and never registers a listener twice.
        vm.onStop()
        vm.onStart()
        vm.onStart()
        assertEquals(1, shadowOf(app).registeredReceivers.count { it.intentFilter.hasAction(Intent.ACTION_BATTERY_CHANGED) })
        vm.onStop()
        assertFalse(listening(Intent.ACTION_BATTERY_CHANGED))
    }

    @Test
    fun theModeInTheSettingsDecidesWhetherTheLauncherSaves() {
        val vm = LauncherViewModel(app)
        shadowOf(Looper.getMainLooper()).idle()
        vm.updateSettings { it.copy(batterySaving = BatterySaving.Never) }
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(vm.powerSaving.value)
        vm.updateSettings { it.copy(batterySaving = BatterySaving.Always) }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(vm.powerSaving.value)
        vm.updateSettings { it.copy(batterySaving = BatterySaving.Auto) }
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("a normal charge and no battery saver", vm.powerSaving.value)
    }

    // --- the settings page

    private var settings by mutableStateOf(LauncherSettings())

    @Test
    fun theBatteryPageOffersTheThreeModesAndSaysWhetherItIsSavingNow() {
        settings = LauncherSettings()
        var saving by mutableStateOf(false)
        show {
            SettingsScreen(
                SettingsEnv(settings, onChange = { change -> settings = change(settings) }, powerSaving = saving),
                SettingsSection.Battery, onSectionChange = {}, sectionAway = remember { Animatable(0f) },
            )
        }
        advance(500)
        capture("settings_battery")
        compose.onNodeWithTag("battery:saving:0").assertExists()
        compose.onNodeWithTag("battery:saving:2").performClick()
        compose.waitForIdle()
        assertEquals(BatterySaving.Never, settings.batterySaving)
        compose.onNodeWithTag("battery:saving:1").performClick()
        compose.waitForIdle()
        assertEquals(BatterySaving.Always, settings.batterySaving)

        advance(100)
        compose.onNodeWithTag("battery:now").assertTextEquals(context.getString(R.string.saving_now_off))
        saving = true
        compose.waitForIdle()
        advance(100)
        compose.onNodeWithTag("battery:now").assertTextEquals(context.getString(R.string.saving_now_on))
        capture("settings_battery_saving")
    }
}
