package com.metrowin.launcher

import com.metrowin.launcher.data.AddMenuIndex
import com.metrowin.launcher.data.AppListBackup
import com.metrowin.launcher.data.Code128
import com.metrowin.launcher.data.FileSearch
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.MetroSound
import com.metrowin.launcher.data.MetroSounds
import com.metrowin.launcher.data.OpenMeteo
import com.metrowin.launcher.data.Recordings
import com.metrowin.launcher.data.SavedApp
import com.metrowin.launcher.data.SavedCities
import com.metrowin.launcher.data.SavedCity
import com.metrowin.launcher.data.SettingsCodec
import com.metrowin.launcher.data.SiteHeadlines
import com.metrowin.launcher.data.Steps
import com.metrowin.launcher.data.WalletCard
import com.metrowin.launcher.data.WalletStore
import com.metrowin.launcher.data.WeatherApps
import com.metrowin.launcher.ui.components.DateMath
import com.metrowin.launcher.ui.components.TimeMath
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtraTilesTest {
    // --- the barcode of the wallet

    @Test
    fun everyPatternOfCode128AddsUpToElevenModules() {
        val patterns = Code128.patterns
        assertEquals(107, patterns.size)
        patterns.take(106).forEachIndexed { i, p -> assertEquals("pattern $i", 11, p.sumOf { it.digitToInt() }) }
        assertEquals(13, patterns[106].sumOf { it.digitToInt() })
    }

    @Test
    fun aKnownTextHasTheStartTheCheckAndTheStop() {
        // "A" is value 33; the check is (104 + 33 * 1) % 103 = 34.
        val expected = (Code128.patterns[104] + Code128.patterns[33] + Code128.patterns[34] + Code128.patterns[106]).map { it.digitToInt() }
        assertEquals(expected, Code128.widths("A"))
    }

    @Test
    fun whatCannotBeDrawnGivesNoBars() {
        assertTrue(Code128.widths("").isEmpty())
        assertTrue(Code128.widths("caffè").isEmpty())
        assertTrue(Code128.widths("1".repeat(WalletStore.MAX_CODE + 1)).isEmpty())
        assertFalse(Code128.widths("123456789012").isEmpty())
    }

    @Test
    fun theWalletKeepsItsCardsAndDropsWhatIsBroken() {
        val cards = listOf(WalletCard("a", "Coop", "1234567890"), WalletCard("b", "Gym", "AB-12"))
        assertEquals(cards, WalletStore.decode(WalletStore.encode(cards)))
        assertTrue(WalletStore.decode("not json").isEmpty())
        assertTrue(WalletStore.decode(null).isEmpty())
        assertEquals(1, WalletStore.decode("""[{"id":"x","name":"n","code":"ok"},{"id":"y","name":"n","code":"è"}]""").size)
    }

    // --- steps

    @Test
    fun stepsAreCountedFromTheCounterOfTheMorning() {
        assertEquals(500 to 1000f, Steps.today(1500f, day = 10, savedDay = 10, savedBase = 1000f))
        // A new day starts from where the counter stands.
        assertEquals(0 to 4000f, Steps.today(4000f, day = 11, savedDay = 10, savedBase = 1000f))
        // The phone started again: the counter began from zero.
        assertEquals(200 to 0f, Steps.today(200f, day = 10, savedDay = 10, savedBase = 1000f))
    }

    // --- recordings and sites

    @Test
    fun aRecordingIsNamedByWhenItWasMade() {
        assertEquals("2026-10-26 18:30:05", Recordings.title("20261026-183005.m4a"))
        assertEquals("odd", Recordings.title("odd.m4a"))
    }

    @Test
    fun theFeedOfAPageIsFoundFromItsLinks() {
        val html = """<html><head>
            <link rel="stylesheet" href="/a.css">
            <link rel="alternate" type="application/rss+xml" title="News" href="/feed.xml">
            <link href='https://example.org/atom?x=1&amp;y=2' type='application/atom+xml' rel='alternate'>
            </head></html>"""
        assertEquals(listOf("https://example.org/feed.xml", "https://example.org/atom?x=1&y=2"), SiteHeadlines.feedLinks(html, "https://example.org/blog/"))
        assertTrue(SiteHeadlines.feedLinks("<html></html>", "https://example.org").isEmpty())
    }

    // --- files and the list of apps

    @Test
    fun theWildcardsOfALikeAreLetters() {
        assertEquals("100\\%\\_a\\\\b", FileSearch.like("100%_a\\b"))
        assertEquals("2.4 MB", FileSearch.sizeText(2_400_000))
        assertEquals("310 KB", FileSearch.sizeText(310_000))
        assertEquals("12 B", FileSearch.sizeText(12))
    }

    @Test
    fun theListOfAppsGoesToAFileAndComesBack() {
        val apps = listOf(SavedApp("com.whatsapp", "WhatsApp"), SavedApp("org.example.app", "Example"))
        assertEquals(apps, AppListBackup.decode(AppListBackup.encode(apps, 1L)))
        assertNull(AppListBackup.decode("""{"kind":"other","apps":[]}"""))
        assertNull(AppListBackup.decode("nonsense"))
        // A name that is not a package, and a repeated one, are left out.
        assertEquals(
            listOf(SavedApp("a.b", "a.b")),
            AppListBackup.decode("""{"kind":"metrowin-apps","apps":[{"p":"../etc","l":"x"},{"p":"a.b"},{"p":"a.b","l":"again"}]}"""),
        )
    }

    @Test
    fun whatTheNewPhoneLacksIsListed() {
        val saved = listOf(SavedApp("a.one", "One"), SavedApp("a.two", "Two"))
        assertEquals(listOf(SavedApp("a.two", "Two")), AppListBackup.missing(saved, setOf("a.one", "x.y")))
        assertEquals("market://details?id=a.two", AppListBackup.storeUri("a.two"))
    }

    // --- the weather

    @Test
    fun theNextHoursComeFromTheForecast() {
        val json = """{"current":{"temperature_2m":12.4,"weather_code":3,"is_day":1},
            "hourly":{"time":["2026-10-26T15:00","2026-10-26T16:00"],"temperature_2m":[12.6,11.2],"weather_code":[3,61],"precipitation_probability":[10,null]}}"""
        val weather = OpenMeteo.parseForecast(json, false, 0L)!!
        assertEquals(2, weather.hours.size)
        assertEquals(LocalTime.of(15, 0), weather.hours[0].time)
        assertEquals(13, weather.hours[0].temperature)
        assertEquals(10, weather.hours[0].rainChance)
        assertNull(weather.hours[1].rainChance)
        // And they survive the copy kept on the phone.
        assertEquals(weather.hours, OpenMeteo.decode(OpenMeteo.encode(weather))!!.hours)
        assertTrue(OpenMeteo.forecastUrl(45.0, 7.0, false).contains("hourly="))
    }

    @Test
    fun aForecastWithoutHoursStillReads() {
        val weather = OpenMeteo.parseForecast("""{"current":{"temperature_2m":5.0}}""", false, 0L)
        assertNotNull(weather)
        assertTrue(weather!!.hours.isEmpty())
    }

    @Test
    fun theCitiesYouFollowAreKeptUpToTheLimit() {
        val cities = (1..8).map { SavedCity("City $it", it.toDouble(), 2.0) }
        val back = SavedCities.decode(SavedCities.encode(cities))
        assertEquals(SavedCities.MAX, back.size)
        assertEquals(cities.first(), back.first())
        assertTrue(SavedCities.decode("garbage").isEmpty())
    }

    @Test
    fun anyWeatherAppIsRecognised() {
        assertTrue(WeatherApps.matches("com.google.android.apps.weather"))
        assertTrue(WeatherApps.matches("org.example.WeatherNow"))
        assertTrue(WeatherApps.matches("it.ilmeteo.app"))
        assertFalse(WeatherApps.matches("com.whatsapp"))
    }

    @Test
    fun theNewSettingsSurviveTheCodec() {
        val s = LauncherSettings(
            clockZone = "Asia/Tokyo", weatherCities = listOf(SavedCity("Tokyo", 35.6, 139.7)),
            searchFileTypes = setOf("images", "documents"),
        )
        val back = SettingsCodec.decode(SettingsCodec.encode(s))!!
        assertEquals("Asia/Tokyo", back.clockZone)
        assertEquals(s.weatherCities, back.weatherCities)
        assertEquals(setOf("images", "documents"), back.searchFileTypes)
        // A time zone that does not exist is not kept, and a kind of file nobody knows is dropped.
        val odd = SettingsCodec.decode("""{"clockZone":"Mars/Base","searchFileTypes":["images","gifs"]}""")!!
        assertEquals("", odd.clockZone)
        assertEquals(setOf("images"), odd.searchFileTypes)
    }

    // --- the add menu, the sounds and the pickers

    @Test
    fun theAddMenuFindsAnEntryByAnyOfItsWords() {
        val steps = AddMenuIndex.kind("steps")
        assertTrue(AddMenuIndex.matches(steps, "Steps", "pedometer"))
        assertTrue(AddMenuIndex.matches(steps, "Passi", "CONTAPASSI"))
        assertFalse(AddMenuIndex.matches(steps, "Steps", "camera"))
        assertTrue(AddMenuIndex.matches(steps, "Steps", ""))
        assertEquals("every entry has one id", AddMenuIndex.kinds.size, AddMenuIndex.kinds.map { it.id }.toSet().size)
    }

    @Test
    fun everySoundIsAShortNonSilentWave() {
        MetroSound.entries.forEach { sound ->
            val pcm = MetroSounds.pcm(sound)
            assertTrue("$sound is not empty", pcm.isNotEmpty())
            assertTrue("$sound is under a second and a half", pcm.size < 22_050 * 3 / 2 * 2)
            assertTrue("$sound is heard", pcm.any { it.toInt() != 0 })
            assertTrue(MetroSounds.wav(sound).size > pcm.size)
        }
    }

    @Test
    fun theWheelsKeepTheDayInAShorterMonth() {
        assertEquals(LocalDate.of(2026, 2, 28), DateMath.withMonth(LocalDate.of(2026, 1, 31), 2))
        assertEquals(LocalDate.of(2027, 2, 28), DateMath.withYear(LocalDate.of(2028, 2, 29), 2027))
        assertEquals(12, TimeMath.hour12(0))
        assertEquals(1, TimeMath.hour12(13))
        assertEquals(0, TimeMath.hour24(12, pm = false))
        assertEquals(12, TimeMath.hour24(12, pm = true))
        assertEquals(23, TimeMath.hour24(11, pm = true))
    }
}
