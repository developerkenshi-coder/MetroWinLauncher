package com.metrowin.launcher

import com.metrowin.launcher.data.ClockFormat
import com.metrowin.launcher.data.ClockHours
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.PhotoFraming
import com.metrowin.launcher.data.SettingsCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** Text size, the hours of the clock, the week, the framing of the photo. */
class AppearanceTest {
    // --- the clock

    @Test
    fun theHoursFollowThePhoneUnlessTheUserChoseOne() {
        assertTrue(ClockHours.System.is24(system = true))
        assertFalse(ClockHours.System.is24(system = false))
        assertFalse(ClockHours.H12.is24(system = true))
        assertTrue(ClockHours.H24.is24(system = false))
        assertEquals(ClockHours.H24, ClockHours.fromCode("24"))
        assertEquals(ClockHours.System, ClockHours.fromCode("nope"))
        assertEquals(ClockHours.entries.size, ClockHours.entries.map { it.code }.distinct().size)
    }

    @Test
    fun theWeekIsCountedAsTheLanguageCountsIt() {
        // 5 March 2026 is a Thursday: the 10th ISO week, the 10th week in the US too (a Sunday start).
        val thursday = LocalDate.of(2026, 3, 5)
        assertEquals(10, ClockFormat.week(thursday, Locale.ITALY))
        assertEquals(10, ClockFormat.week(thursday, Locale.US))
        // The first days of January belong to the last week of the year before, or to the first, by the rules of the country.
        val newYear = LocalDate.of(2027, 1, 1)
        assertTrue(ClockFormat.week(newYear, Locale.ITALY) in setOf(1, 52, 53))
        // 1 January 2026 is a Thursday, so the week of Monday 29 December is the first of 2026, and 8 January is in the second.
        assertEquals(1, ClockFormat.week(LocalDate.of(2026, 1, 1), Locale.ITALY))
        assertEquals(2, ClockFormat.week(LocalDate.of(2026, 1, 8), Locale.ITALY))
    }

    // --- the framing of the photo

    @Test
    fun theZoomRunsFromOneToThreeTimes() {
        assertEquals(100, PhotoFraming.zoomOf(0f))
        assertEquals(200, PhotoFraming.zoomOf(0.5f))
        assertEquals(300, PhotoFraming.zoomOf(1f))
        assertEquals(100, PhotoFraming.zoomOf(-1f))
        assertEquals(300, PhotoFraming.zoomOf(5f))
        assertEquals(0.5f, PhotoFraming.fractionOfZoom(200), 1e-6f)
        assertEquals(0f, PhotoFraming.fractionOfZoom(50), 0f)
        assertEquals(1f, PhotoFraming.fractionOfZoom(999), 0f)
    }

    @Test
    fun thePositionIsAPercentAndABias() {
        assertEquals(50, PhotoFraming.percentOf(0.5f))
        assertEquals(0, PhotoFraming.percentOf(-2f))
        assertEquals(100, PhotoFraming.percentOf(2f))
        assertEquals(0.25f, PhotoFraming.fractionOf(25), 1e-6f)
        assertEquals(-1f, PhotoFraming.bias(0), 0f)
        assertEquals(0f, PhotoFraming.bias(50), 0f)
        assertEquals(1f, PhotoFraming.bias(100), 0f)
        assertEquals(1f, PhotoFraming.bias(500), 0f)
    }

    // --- the settings

    @Test
    fun theNewSettingsSurviveSaving() {
        val settings = LauncherSettings(
            clockHours = ClockHours.H24, clockAlarm = true, clockWeek = true, textScale = 120, hideStatusBar = true,
            startPhotoZoom = 180, startPhotoX = 20, startPhotoY = 80, backgroundRevision = 3,
        )
        assertEquals(settings, SettingsCodec.decode(SettingsCodec.encode(settings)))
        val defaults = LauncherSettings()
        assertEquals(100, defaults.textScale)
        assertFalse(defaults.hideStatusBar || defaults.clockAlarm || defaults.clockWeek)
        assertEquals(100, defaults.startPhotoZoom)
    }

    @Test
    fun aTextSizeThatIsNotOnTheListGoesToTheNearestOne() {
        assertEquals(110, SettingsCodec.decode("""{"textScale":108}""")!!.textScale)
        assertEquals(130, SettingsCodec.decode("""{"textScale":500}""")!!.textScale)
        assertEquals(85, SettingsCodec.decode("""{"textScale":1}""")!!.textScale)
        assertEquals(100, SettingsCodec.decode("""{}""")!!.textScale)
    }

    @Test
    fun theFramingIsKeptInRangeWhateverTheFileSays() {
        val d = SettingsCodec.decode("""{"startPhotoZoom":900,"startPhotoX":-5,"startPhotoY":300}""")!!
        assertEquals(300, d.startPhotoZoom)
        assertEquals(0, d.startPhotoX)
        assertEquals(100, d.startPhotoY)
    }

    @Test
    fun theFramingOfThePhotoDoesNotTravelInABackupBecauseThePhotoDoesNot() {
        val settings = LauncherSettings(startPhotoZoom = 250, startPhotoX = 10, backgroundRevision = 2, textScale = 120)
        val portable = SettingsCodec.decode(SettingsCodec.encode(settings, portable = true))!!
        assertEquals(100, portable.startPhotoZoom)
        assertEquals(50, portable.startPhotoX)
        assertEquals("the text size is a preference, it does travel", 120, portable.textScale)
    }
}
