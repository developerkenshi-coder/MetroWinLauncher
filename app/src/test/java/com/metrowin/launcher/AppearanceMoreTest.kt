package com.metrowin.launcher

import android.app.Application
import android.os.Looper
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.AppLanguages
import com.metrowin.launcher.data.CalendarSearch
import com.metrowin.launcher.data.GestureAction
import com.metrowin.launcher.data.HomePresses
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LiveEvent
import com.metrowin.launcher.data.LockInfoSource
import com.metrowin.launcher.data.LockText
import com.metrowin.launcher.data.OpenMeteo
import com.metrowin.launcher.data.SettingsCodec
import com.metrowin.launcher.data.ThemeMode
import com.metrowin.launcher.data.WeatherCondition
import com.metrowin.launcher.data.WeatherData
import com.metrowin.launcher.data.WeatherRepository
import com.metrowin.launcher.ui.theme.ClassicStyle
import com.metrowin.launcher.ui.theme.DroidStyle
import com.metrowin.launcher.ui.theme.GlassStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** Bold text and the system font, the dark theme from sunset to sunrise, the language, Home pressed twice, and the lock screen's weather and event. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppearanceMoreTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    // --- bold text and the font

    @Test
    fun boldTextMakesEveryWeightThreeStepsHeavier() {
        val bold = ClassicStyle.withType(bold = true, systemFont = false)
        assertEquals(600, bold.weight(FontWeight.Light).weight)
        assertEquals(700, bold.weight(FontWeight.Normal).weight)
        assertEquals(900, bold.weight(FontWeight.SemiBold).weight)
        assertEquals("never past the heaviest", 900, bold.weight(FontWeight.Bold).weight)
        assertEquals("the plain style is untouched", 300, ClassicStyle.weight(FontWeight.Light).weight)
    }

    @Test
    fun theLooksOwnWeightsComeFirstAndThenTheBoldOnes() {
        // Modern Glass draws Light as Normal; bold then makes that 700.
        assertEquals(400, GlassStyle.weight(FontWeight.Light).weight)
        assertEquals(700, GlassStyle.withType(bold = true, systemFont = false).weight(FontWeight.Light).weight)
        assertEquals(400, DroidStyle.weight(FontWeight.Light).weight)
        assertEquals(700, DroidStyle.withType(true, false).weight(FontWeight.Light).weight)
    }

    @Test
    fun theSystemFontReplacesTheFontOfTheLookAndNothingElse() {
        val system = ClassicStyle.withType(bold = false, systemFont = true)
        assertSame(FontFamily.Default, system.font)
        assertEquals(ClassicStyle.kind, system.kind)
        assertSame(ClassicStyle.tileShape, system.tileShape)
        assertEquals(300, system.weight(FontWeight.Light).weight)
        assertTrue("the look keeps its own font unless asked", ClassicStyle.font !== FontFamily.Default)
    }

    @Test
    fun withNeitherChoiceTheStyleIsTheSameObject() {
        assertSame(ClassicStyle, ClassicStyle.withType(bold = false, systemFont = false))
        assertSame(GlassStyle, GlassStyle.withType(false, false))
    }

    @Test
    fun boldOnABoldStyleStaysOneStep() {
        val both = ClassicStyle.withType(true, false).withType(false, true)
        assertEquals("the bold of the first call is kept by the second", 600, both.weight(FontWeight.Light).weight)
        assertSame(FontFamily.Default, both.font)
    }

    // --- dark from sunset to sunrise

    private fun dark(minute: Int, rise: Int? = 6 * 60 + 30, set: Int? = 19 * 60 + 45, from: Int = 20, to: Int = 7) =
        ThemeMode.isDark(ThemeMode.Sun, systemDark = false, hour = minute / 60, fromHour = from, toHour = to, minuteOfDay = minute, sunriseMinute = rise, sunsetMinute = set)

    @Test
    fun theThemeIsDarkBetweenSunsetAndSunrise() {
        assertTrue(dark(0))
        assertTrue(dark(5 * 60))
        assertTrue("a minute before sunrise", dark(6 * 60 + 29))
        assertFalse("at sunrise", dark(6 * 60 + 30))
        assertFalse(dark(12 * 60))
        assertFalse("a minute before sunset", dark(19 * 60 + 44))
        assertTrue("at sunset", dark(19 * 60 + 45))
        assertTrue(dark(23 * 60 + 59))
    }

    @Test
    fun withNoSunTimesTheHoursAreUsed() {
        assertTrue(dark(21 * 60, rise = null, set = null))
        assertFalse(dark(12 * 60, rise = null, set = null))
        assertTrue("one of the two is not enough", dark(21 * 60, rise = 6 * 60, set = null))
        assertFalse(dark(12 * 60, rise = null, set = 19 * 60))
    }

    @Test
    fun aSunriseAfterTheSunsetInTheSameDayCountsTheOtherWay() {
        // Times read in another zone can put the sunrise after the sunset: dark is then between them.
        assertTrue(dark(10 * 60, rise = 22 * 60, set = 8 * 60))
        assertFalse(dark(5 * 60, rise = 22 * 60, set = 8 * 60))
        assertTrue("the same minute is no sun at all, so the hours decide", dark(21 * 60, rise = 600, set = 600))
    }

    @Test
    fun theOtherModesIgnoreTheSun() {
        val noon = 12 * 60
        assertTrue(ThemeMode.isDark(ThemeMode.Dark, false, 12, 20, 7, noon, 6 * 60, 19 * 60))
        assertFalse(ThemeMode.isDark(ThemeMode.Light, true, 23, 20, 7, 23 * 60, 6 * 60, 19 * 60))
        assertTrue(ThemeMode.isDark(ThemeMode.System, true, 12, 20, 7, noon, 6 * 60, 19 * 60))
        assertFalse(ThemeMode.isDark(ThemeMode.Schedule, false, 12, 20, 7, noon, 6 * 60, 19 * 60))
        assertTrue(ThemeMode.isDark(ThemeMode.Schedule, false, 23, 20, 7))
    }

    @Test
    fun theSunModeIsSavedAndTheOldModesAreStill() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, SettingsCodec.decode(SettingsCodec.encode(LauncherSettings(themeMode = mode)))!!.themeMode)
        }
        assertEquals(ThemeMode.Sun, ThemeMode.fromCode("sun"))
        assertEquals(ThemeMode.Dark, ThemeMode.fromCode("moon"))
        assertEquals(5, ThemeMode.entries.size)
    }

    // --- the new settings

    @Test
    fun theNewSettingsAreSavedAndOffByDefault() {
        val defaults = LauncherSettings()
        assertFalse(defaults.boldText)
        assertFalse(defaults.systemFont)
        assertEquals(GestureAction.None, defaults.homeDoubleAction)
        assertTrue(defaults.lockShowWeather)
        assertFalse("a calendar title on a lock screen is not shown unless asked", defaults.lockShowEvent)
        val custom = LauncherSettings(
            boldText = true, systemFont = true, homeDoubleAction = GestureAction.OpenApp, homeDoubleApp = "pkg/cls",
            lockShowWeather = false, lockShowEvent = true,
        )
        assertEquals(custom, SettingsCodec.decode(SettingsCodec.encode(custom)))
        assertEquals(custom, SettingsCodec.decode(SettingsCodec.encode(custom, portable = true)))
        assertEquals(defaults, SettingsCodec.decode("{}"))
        assertEquals(GestureAction.None, SettingsCodec.decode("""{"homeDoubleAction":"nonsense"}""")!!.homeDoubleAction)
    }

    // --- Home pressed twice

    @Test
    fun twoPressesCloseTogetherAreADoubleOne() {
        assertTrue(HomePresses.isDouble(previous = 1_000, now = 1_300))
        assertTrue(HomePresses.isDouble(1_000, 1_000 + HomePresses.DOUBLE_MS))
        assertFalse(HomePresses.isDouble(1_000, 1_000 + HomePresses.DOUBLE_MS + 1))
        assertFalse("the first press ever", HomePresses.isDouble(0, 5_000))
        assertFalse("a clock that went back", HomePresses.isDouble(2_000, 1_000))
    }

    @Test
    fun theViewModelMarksTheSecondPressAndStartsOverAfterIt() {
        val vm = LauncherViewModel(app)
        idle()
        vm.onHomePressed(fromBackground = false, now = 10_000)
        assertFalse(vm.homePresses.value.double)
        vm.onHomePressed(false, now = 10_200)
        assertTrue(vm.homePresses.value.double)
        assertEquals(2, vm.homePresses.value.count)
        // A third press right after is the first of a new pair, not a second double.
        vm.onHomePressed(false, now = 10_350)
        assertFalse(vm.homePresses.value.double)
        vm.onHomePressed(false, now = 10_500)
        assertTrue(vm.homePresses.value.double)
        vm.onHomePressed(false, now = 12_000)
        assertFalse("a slow second press", vm.homePresses.value.double)
    }

    // --- the language

    @Test
    fun theLanguageOfThePhoneAndTheTwoOfTheLauncherAreTold() {
        assertEquals("it", AppLanguages.normalize("it"))
        assertEquals("en", AppLanguages.normalize("EN-gb"))
        assertEquals("", AppLanguages.normalize("fr"))
        assertEquals("", AppLanguages.normalize(null))
        assertEquals(listOf("", "en", "it"), AppLanguages.tags)
    }

    @Test
    fun theLanguageCanBeSwitchedAndPutBack() {
        val vm = LauncherViewModel(app)
        idle()
        assertEquals("", vm.appLanguage())
        vm.setAppLanguage("it")
        assertEquals("it", vm.appLanguage())
        vm.setAppLanguage("en")
        assertEquals("en", vm.appLanguage())
        vm.setAppLanguage("")
        assertEquals("", vm.appLanguage())
    }

    // --- the lock screen

    private val utc = ZoneId.of("UTC")
    private fun millis(day: Int, hour: Int, minute: Int) = ZonedDateTime.of(2026, 3, day, hour, minute, 0, 0, utc).toInstant().toEpochMilli()

    @Test
    fun anEventOfTodayHasItsTimeAndAnotherDayItsWeekday() {
        val now = millis(5, 14, 5)
        assertEquals("Dentist · 15:30", LockText.event(LiveEvent("Dentist", millis(5, 15, 30), false), now, utc, true, Locale.US))
        assertEquals("Dentist · Fri 09:00", LockText.event(LiveEvent("Dentist", millis(6, 9, 0), false), now, utc, true, Locale.US))
        assertEquals("Dentist · 3:30 PM", LockText.event(LiveEvent("Dentist", millis(5, 15, 30), false), now, utc, false, Locale.US))
    }

    @Test
    fun anEventOfTheWholeDayHasNoTime() {
        val now = millis(5, 14, 5)
        assertEquals("Holiday", LockText.event(LiveEvent("Holiday", millis(5, 0, 0), true), now, utc, true, Locale.US))
        assertEquals("Fri Holiday", LockText.event(LiveEvent("Holiday", millis(6, 0, 0), true), now, utc, true, Locale.US))
    }

    @Test
    fun theLockInfoCarriesTheWeatherKeptAndNoEventWithoutPermission() {
        val source = LockInfoSource(app)
        assertNull(source.read().weather)
        val weather = WeatherData(18, WeatherCondition.Clear, true, emptyList(), false, fetchedAt = 1L)
        WeatherRepository(app).save(weather)
        val info = source.read(withEvent = true)
        assertEquals(18, info.weather!!.temperature)
        assertEquals(WeatherCondition.Clear, info.weather!!.condition)
        assertNull("no calendar permission", info.nextEvent)
        assertNull(source.read().nextEvent)
        assertNull(CalendarSearch(app).next())
    }
}
