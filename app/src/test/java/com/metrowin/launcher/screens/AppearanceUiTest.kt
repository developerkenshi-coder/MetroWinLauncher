package com.metrowin.launcher.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.height
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.ui.theme.MetroTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.ClockHours
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.settings.SettingsEnv
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.settings.SettingsSection
import com.metrowin.launcher.ui.start.ClockHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Text size, the clock under its options, the status bar and the framing of the photo, in the settings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class AppearanceUiTest : ScreenshotBase() {
    @Test
    fun theThemeScalesTheTextOfEverythingInsideIt() {
        // What a screen reads when it turns sp into dp: the scale of the phone times the one the user chose.
        var plain = 0f
        var bigger = 0f
        var smaller = 0f
        show {
            plain = androidx.compose.ui.platform.LocalDensity.current.fontScale
            MetroTheme(textScale = 1.5f) { bigger = androidx.compose.ui.platform.LocalDensity.current.fontScale }
            MetroTheme(textScale = 0.85f) { smaller = androidx.compose.ui.platform.LocalDensity.current.fontScale }
        }
        advance(300)
        assertEquals(1.5f, bigger / plain, 1e-4f)
        assertEquals(0.85f, smaller / plain, 1e-4f)
    }

    @Test
    fun theDensityOfTheScreenStaysTheSameWhenOnlyTheTextGrows() {
        var before = 0f
        var after = 0f
        show {
            before = androidx.compose.ui.platform.LocalDensity.current.density
            MetroTheme(textScale = 1.3f) { after = androidx.compose.ui.platform.LocalDensity.current.density }
        }
        advance(300)
        assertEquals(before, after, 0f)
    }

    private var current = LauncherSettings(showClock = true)
    private var photo: ImageBitmap? = null

    @Composable
    private fun Page(section: SettingsSection = SettingsSection.StartTheme) {
        var settings by remember { mutableStateOf(current) }
        SettingsScreen(
            env = SettingsEnv(settings = settings, onChange = { change -> settings = change(settings); current = settings }, startBackground = photo),
            section = section, onSectionChange = {}, sectionAway = remember { Animatable(0f) },
        )
    }

    private fun tap(tag: String) {
        scrollIntoView(tag, topMargin = 0.2f)
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
        advance(100)
    }

    @Test
    fun theTextSizeStepsThroughTheListAndStopsAtTheEnds() {
        current = LauncherSettings(textScale = 120)
        show { Page(SettingsSection.Accessibility) }
        advance(500)
        tap("access:textsize:plus")
        assertEquals(130, current.textScale)
        tap("access:textsize:plus")
        assertEquals("the end of the list", 130, current.textScale)
        tap("access:textsize:minus")
        tap("access:textsize:minus")
        assertEquals(110, current.textScale)
    }

    @Test
    fun theClockOptionsAndTheStatusBarSwitchInTheSettings() {
        current = LauncherSettings(showClock = true)
        show { Page(SettingsSection.Start) }
        advance(500)
        tap("start:clockalarm")
        assertTrue(current.clockAlarm)
        tap("start:clockweek")
        assertTrue(current.clockWeek)
        tap("start:hidestatus")
        assertTrue(current.hideStatusBar)
        tap("start:hours:header")
        tap("start:hours:${ClockHours.entries.indexOf(ClockHours.H12)}")
        assertEquals(ClockHours.H12, current.clockHours)
    }

    @Test
    fun theClockOptionsAreOffWhileThereIsNoClock() {
        current = LauncherSettings(showClock = false)
        show { Page(SettingsSection.Start) }
        advance(500)
        tap("start:clockalarm")
        assertFalse("a switch that is off does nothing", current.clockAlarm)
    }

    @Test
    fun theFramingOfThePhotoOnlyShowsWithAPhotoAndCanBeReset() {
        current = LauncherSettings(backgroundRevision = 0)
        show { Page() }
        advance(500)
        compose.onAllNodesWithTag("theme:photozoom").assertCountEquals(0)
    }

    @Test
    fun withAPhotoTheFramingHasItsSlidersAndAReset() {
        current = LauncherSettings(backgroundRevision = 1, startPhotoZoom = 220, startPhotoX = 10, startPhotoY = 90)
        photo = android.graphics.Bitmap.createBitmap(32, 48, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF2255AA.toInt()) }.asImageBitmap()
        show { Page() }
        advance(500)
        scrollIntoView("theme:photozoom", topMargin = 0.2f)
        capture("settings_photo_framing")
        compose.onNodeWithTag("theme:photozoom").assertExists()
        compose.onNodeWithTag("theme:photox").assertExists()
        compose.onNodeWithTag("theme:photoy").assertExists()
        compose.onAllNodesWithTag("theme:photopreview").assertCountEquals(2) // the photo block and the framing block
        tap("theme:photoreset")
        assertEquals(100, current.startPhotoZoom)
        assertEquals(50, current.startPhotoX)
        assertEquals(50, current.startPhotoY)
        photo = null
    }

    // --- the clock under its options

    @Test
    fun theDateHasTheWeekNumberOnlyWhenAsked() {
        show { Column { ClockHeader(Modifier.testTag("with"), onDateClick = {}, showWeek = true) } }
        advance(300)
        compose.onNodeWithTag("clock:date").assertTextContains("week", substring = true)
    }

    @Test
    fun theDateAloneHasNoWeek() {
        show { ClockHeader(onDateClick = {}, showWeek = false) }
        advance(300)
        compose.onAllNodes(hasTestTag("clock:date") and hasText("week", substring = true)).assertCountEquals(0)
        compose.onNodeWithTag("clock:date").assertExists()
    }

    @Test
    fun theNextAlarmShowsUnderTheDateWhenThereIsOne() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val alarms = context.getSystemService(android.app.AlarmManager::class.java)
        val pending = android.app.PendingIntent.getBroadcast(context, 0, android.content.Intent("x"), android.app.PendingIntent.FLAG_IMMUTABLE)
        alarms.setAlarmClock(android.app.AlarmManager.AlarmClockInfo(System.currentTimeMillis() + 3 * 3_600_000L, pending), pending)
        show { ClockHeader(showAlarm = true) }
        advance(300)
        compose.onNodeWithTag("clock:alarm").assertExists()
    }

    @Test
    fun withNoAlarmOrNoOptionThereIsNoAlarmLine() {
        show { ClockHeader(showAlarm = true) }
        advance(300)
        compose.onAllNodesWithTag("clock:alarm").assertCountEquals(0)
    }
}
