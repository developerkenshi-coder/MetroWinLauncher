package com.metrowin.launcher

import android.media.AudioManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.BrightnessController
import com.metrowin.launcher.data.GestureAction
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.Levels
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.data.RingerMode
import com.metrowin.launcher.data.SettingsCodec
import com.metrowin.launcher.data.SoundController
import com.metrowin.launcher.gestures.LockAccessibilityService
import com.metrowin.launcher.gestures.SystemAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The new gestures, the levels of the sliders of the Action Center, and the sound. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GestureAndPanelTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    // --- the gestures

    @Test
    fun everyGestureHasItsOwnCodeAndTheNewOnesAreKnown() {
        assertEquals(GestureAction.entries.size, GestureAction.entries.map { it.code }.distinct().size)
        assertEquals(GestureAction.Screenshot, GestureAction.fromCode("screenshot"))
        assertEquals(GestureAction.OpenApp, GestureAction.fromCode("app"))
        assertEquals(GestureAction.PowerMenu, GestureAction.fromCode("power"))
        assertEquals(GestureAction.None, GestureAction.fromCode("nonsense"))
    }

    @Test
    fun theGesturesOfTheServiceMapToItsActionsAndTheOthersDoNot() {
        assertEquals(SystemAction.Screenshot, GestureAction.Screenshot.system)
        assertEquals(SystemAction.PowerMenu, GestureAction.PowerMenu.system)
        assertEquals(SystemAction.SplitScreen, GestureAction.SplitScreen.system)
        assertEquals(SystemAction.Notifications, GestureAction.AndroidNotifications.system)
        assertEquals(SystemAction.QuickSettings, GestureAction.AndroidQuickSettings.system)
        listOf(GestureAction.None, GestureAction.LockScreen, GestureAction.OpenApp, GestureAction.Recents, GestureAction.Search)
            .forEach { assertNull(it.name, it.system) }
    }

    @Test
    fun withTheServiceOffNothingIsDone() {
        SystemAction.entries.forEach { assertFalse(it.name, LockAccessibilityService.perform(it)) }
    }

    @Test
    fun theNewSettingsSurviveSaving() {
        val settings = LauncherSettings(
            doubleTapAction = GestureAction.OpenApp, doubleTapApp = "app:a", twoFingerDownAction = GestureAction.Screenshot,
            twoFingerUpAction = GestureAction.OpenApp, twoFingerUpApp = "app:b", twoFingerDownApp = "app:c",
            actionSliders = false, clockTaps = false,
        )
        assertEquals(settings, SettingsCodec.decode(SettingsCodec.encode(settings)))
        val defaults = LauncherSettings()
        assertTrue(defaults.actionSliders && defaults.clockTaps)
        assertEquals("", defaults.doubleTapApp)
    }

    @Test
    fun theScreenshotIsAButtonOfTheActionCenterToo() {
        assertNotNull(QuickAction.fromId("screenshot"))
        assertEquals(QuickAction.SLOTS, QuickAction.slots(listOf("screenshot", "wifi")).size)
        assertEquals(QuickAction.Screenshot, QuickAction.slots(listOf("screenshot")).first())
    }

    // --- levels

    @Test
    fun aLevelIsAFractionOfItsScaleAndBack() {
        assertEquals(0.5f, Levels.fraction(7, 14), 1e-6f)
        assertEquals(0f, Levels.fraction(-3, 15), 0f)
        assertEquals(1f, Levels.fraction(20, 15), 0f)
        assertEquals("no scale means nothing", 0f, Levels.fraction(5, 0), 0f)
        assertEquals(8, Levels.level(0.5f, 15))
        assertEquals(15, Levels.level(2f, 15))
        assertEquals(0, Levels.level(-1f, 15))
        assertEquals("the brightness never goes below the floor", 8, Levels.level(0f, 255, min = 8))
        assertEquals(255, Levels.level(1f, 255, min = 8))
    }

    // --- the sound

    @Test
    fun theMediaVolumeIsReadAndSetAsAFraction() {
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val sound = SoundController(context)
        sound.setVolume(1f)
        assertEquals(max, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertEquals(1f, sound.volume()!!, 1e-6f)
        sound.setVolume(0f)
        assertEquals(0f, sound.volume()!!, 1e-6f)
    }

    @Test
    fun theSoundModeGoesFromSoundToVibrationWithNoPermission() {
        val sound = SoundController(context)
        assertTrue(sound.setRinger(RingerMode.Vibrate))
        assertEquals(RingerMode.Vibrate, sound.ringer())
        assertTrue(sound.setRinger(RingerMode.Normal))
        assertEquals(RingerMode.Normal, sound.ringer())
    }

    @Test
    fun silentNeedsTheDoNotDisturbAccessAndSaysNoWithoutIt() {
        val sound = SoundController(context)
        assertFalse(sound.setRinger(RingerMode.Silent))
        assertEquals("the mode did not change", RingerMode.Normal, sound.ringer())
    }

    @Test
    fun theBrightnessCannotBeSetWithoutTheAccessTheUserGives() {
        val brightness = BrightnessController(context)
        // Whatever the access is, setting the brightness works exactly when it has been given.
        val allowed = brightness.canWrite()
        assertEquals(allowed, brightness.set(0.5f))
        if (allowed) assertEquals(0.5f, brightness.brightness()!!, 0.01f)
    }
}
