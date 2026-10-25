package com.metrowin.launcher.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.metrowin.launcher.data.GestureAction
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LiveEvent
import com.metrowin.launcher.data.LockInfo
import com.metrowin.launcher.data.ThemeMode
import com.metrowin.launcher.data.WeatherCondition
import com.metrowin.launcher.data.WeatherData
import com.metrowin.launcher.ui.lock.LockOptions
import com.metrowin.launcher.ui.lock.MetroLockScreen
import com.metrowin.launcher.ui.settings.SettingsEnv
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.settings.SettingsSection
import com.metrowin.launcher.ui.theme.LocalStyle
import com.metrowin.launcher.ui.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** Bold text and the font, the sun theme, Home twice, the language and the lock screen lines, in the screens that have them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class AppearanceMoreUiTest : ScreenshotBase() {
    private var current = LauncherSettings()
    private var language = ""
    private var languages = mutableListOf<String>()

    @Composable
    private fun Page(section: SettingsSection, canSwitchLanguage: Boolean = false) {
        var settings by remember { mutableStateOf(current) }
        SettingsScreen(
            env = SettingsEnv(
                settings = settings, onChange = { change -> settings = change(settings); current = settings },
                language = language, onLanguage = if (canSwitchLanguage) ({ languages += it; language = it }) else null,
            ),
            section = section, onSectionChange = {}, sectionAway = remember { Animatable(0f) },
        )
    }

    private fun tap(tag: String) {
        scrollIntoView(tag, topMargin = 0.2f)
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
        advance(100)
    }

    // --- the theme

    @Test
    fun theThemeAppliesBoldTextAndTheSystemFontToEverythingInsideIt() {
        var plain = 0
        var bold = 0
        var system: FontFamily? = null
        var normalFont: FontFamily? = null
        show {
            plain = LocalStyle.current.weight(FontWeight.Light).weight
            normalFont = LocalStyle.current.font
            MetroTheme(boldText = true) { bold = LocalStyle.current.weight(FontWeight.Light).weight }
            MetroTheme(systemFont = true) { system = LocalStyle.current.font }
        }
        advance(300)
        assertEquals(300, plain)
        assertEquals(600, bold)
        assertSame(FontFamily.Default, system)
        assertFalse(normalFont === FontFamily.Default)
    }

    @Test
    fun theSettingsSwitchBoldTextAndTheFont() {
        current = LauncherSettings()
        show { Page(SettingsSection.Accessibility) }
        advance(500)
        tap("access:bold")
        assertTrue(current.boldText)
        tap("access:systemfont")
        assertTrue(current.systemFont)
        tap("access:bold")
        assertFalse(current.boldText)
    }

    @Test
    fun theSunModeIsOneOfTheFiveAndExplainsItself() {
        current = LauncherSettings(themeMode = ThemeMode.Dark)
        show { Page(SettingsSection.StartTheme) }
        advance(500)
        compose.onAllNodesWithTag("theme:sunhint").assertCountEquals(0)
        tap("theme:mode:${ThemeMode.Sun.ordinal}")
        assertEquals(ThemeMode.Sun, current.themeMode)
        capture("settings_theme_sun")
        compose.onNodeWithTag("theme:sunhint").assertExists()
        // The hours stay, for the days the weather does not know the sun.
        compose.onNodeWithTag("theme:from").assertExists()
        compose.onNodeWithTag("theme:to").assertExists()
    }

    // --- gestures and language

    @Test
    fun homePressedTwiceHasItsActionAndItsApp() {
        current = LauncherSettings()
        show { Page(SettingsSection.Gestures) }
        advance(500)
        capture("settings_home_double")
        compose.onAllNodesWithTag("gestures:homedouble:app:header").assertCountEquals(0)
        tap("gestures:homedouble:header")
        tap("gestures:homedouble:${GestureAction.entries.indexOf(GestureAction.Torch)}")
        assertEquals(GestureAction.Torch, current.homeDoubleAction)
        tap("gestures:homedouble:header")
        tap("gestures:homedouble:${GestureAction.entries.indexOf(GestureAction.OpenApp)}")
        assertEquals(GestureAction.OpenApp, current.homeDoubleAction)
        compose.onNodeWithTag("gestures:homedouble:app:header").assertExists()
    }

    @Test
    fun theLanguageIsChosenWhereTheLauncherCanSwitchIt() {
        current = LauncherSettings()
        languages = mutableListOf()
        language = ""
        show { Page(SettingsSection.Screen, canSwitchLanguage = true) }
        advance(500)
        capture("settings_language")
        tap("screen:language:1")
        tap("screen:language:2")
        assertEquals(listOf("en", "it"), languages)
    }

    @Test
    fun withoutTheSwitchThereIsNoLanguageChoice() {
        current = LauncherSettings()
        show { Page(SettingsSection.Screen, canSwitchLanguage = false) }
        advance(500)
        compose.onAllNodesWithTag("screen:language").assertCountEquals(0)
    }

    // --- the lock screen

    private val utc = ZoneId.of("UTC")
    private fun millis(day: Int, hour: Int, minute: Int) = ZonedDateTime.of(2026, 3, day, hour, minute, 0, 0, utc).toInstant().toEpochMilli()
    private val info = LockInfo(
        nowMillis = millis(5, 14, 5),
        weather = WeatherData(18, WeatherCondition.Clear, true, emptyList(), false, fetchedAt = 1L),
        nextEvent = LiveEvent("Dentist", millis(5, 15, 30), false),
    )

    private fun lock(options: LockOptions, info: LockInfo = this.info) = show {
        MetroLockScreen(info = info, photo = null, options = options, is24Hour = true, zone = utc, locale = Locale.US)
    }

    @Test
    fun theLockScreenShowsTheWeatherAndTheNextEvent() {
        lock(LockOptions())
        advance(300)
        capture("lock_weather_event")
        compose.onNodeWithTag("lock:weather").assertExists()
        compose.onNodeWithTag("lock:event").assertExists()
        compose.onAllNodesWithTag("lock:weather").assertCountEquals(1)
    }

    @Test
    fun theLinesAreLeftOutWhenTheOptionsSaySoOrThereIsNothingToShow() {
        lock(LockOptions(showWeather = false, showEvent = false))
        advance(300)
        compose.onAllNodesWithTag("lock:weather").assertCountEquals(0)
        compose.onAllNodesWithTag("lock:event").assertCountEquals(0)
    }

    @Test
    fun withNoWeatherAndNoEventThereAreNoLines() {
        lock(LockOptions(), info = LockInfo(nowMillis = millis(5, 14, 5)))
        advance(300)
        compose.onAllNodesWithTag("lock:weather").assertCountEquals(0)
        compose.onAllNodesWithTag("lock:event").assertCountEquals(0)
    }

    @Test
    fun theLockSettingsSwitchTheWeatherAndTheEvent() {
        current = LauncherSettings()
        show { Page(SettingsSection.Lock) }
        advance(500)
        capture("settings_lock_more")
        tap("lock:weather")
        assertFalse(current.lockShowWeather)
        tap("lock:event")
        assertTrue(current.lockShowEvent)
    }
}
