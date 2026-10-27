package com.metrowin.launcher

import com.metrowin.launcher.data.DominantColor
import com.metrowin.launcher.data.ClockHands
import com.metrowin.launcher.data.IconShape
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.ParkedCar
import com.metrowin.launcher.data.ParkedCarStore
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.data.QuickAnswer
import com.metrowin.launcher.data.Recordings
import com.metrowin.launcher.data.RingerMode
import com.metrowin.launcher.data.SectionReset
import com.metrowin.launcher.data.SettingsCodec
import com.metrowin.launcher.data.Steps
import com.metrowin.launcher.data.SystemCommands
import com.metrowin.launcher.data.Tips
import com.metrowin.launcher.data.VolumeProfile
import com.metrowin.launcher.data.VolumeProfiles
import java.time.LocalDate
import java.time.LocalTime
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The pure parts of the sixth round: steps, the car, the clock, the colour of an icon, presets, commands, tips, resetting a page, recordings. */
@RunWith(RobolectricTestRunner::class)
class SixthRoundMoreTest {
    // --- steps: history, goal, streak

    @Test
    fun theStepsOfADayOnlyGoUpAndOldDaysDropOut() {
        var history = Steps.record(emptyMap(), day = 100, steps = 4000)
        // A phone that started again counts from zero: the most of the day stays.
        history = Steps.record(history, day = 100, steps = 150)
        assertEquals(4000, history[100])
        history = Steps.record(history, day = 100L + Steps.KEEP_DAYS, steps = 10)
        assertNull(history[100])
        assertEquals(10, history[100 + Steps.KEEP_DAYS.toLong()])
    }

    @Test
    fun theHistoryOfStepsSurvivesTheFileAndAnOddOneDoesNot() {
        val history = mapOf(5L to 100, 6L to 9000)
        assertEquals(history, Steps.decodeHistory(Steps.encodeHistory(history)))
        assertEquals(mapOf(7L to 3), Steps.decodeHistory("x:1,7:3,8:-4,9"))
        assertTrue(Steps.decodeHistory(null).isEmpty())
    }

    @Test
    fun theStreakCountsDaysAtTheGoalAndTodayWaitsForItsTurn() {
        val goal = 8000
        val days = mapOf(10L to 9000, 11L to 8000, 12L to 8500, 13L to 100)
        // Today (13) is still short: the run is the three days before it.
        assertEquals(3, Steps.streak(days, goal, today = 13))
        assertEquals(4, Steps.streak(days + (13L to 8001), goal, today = 13))
        // A day missing breaks the run; no goal, no streak.
        assertEquals(1, Steps.streak(mapOf(12L to 9000, 10L to 9000), goal, today = 12))
        assertEquals(0, Steps.streak(days, 0, today = 13))
    }

    // --- the car

    @Test
    fun theParkedCarIsKeptAsItWasAndABadFileIsNoCar() {
        val car = ParkedCar(45.0702, 7.6869, "Via Roma 1, Torino", 1_700_000_000_000L)
        assertEquals(car, ParkedCarStore.decode(ParkedCarStore.encode(car)))
        val noStreet = car.copy(address = null)
        assertEquals(noStreet, ParkedCarStore.decode(ParkedCarStore.encode(noStreet)))
        assertNull(ParkedCarStore.decode("nonsense"))
    }

    @Test
    fun theWalkToTheCarUsesDotsWhateverTheLanguage() {
        java.util.Locale.setDefault(java.util.Locale.ITALY)
        try {
            val car = ParkedCar(45.07021, 7.68692, null, 0L)
            assertEquals("google.navigation:q=45.070210,7.686920&mode=w", ParkedCarStore.walkUri(car))
            assertEquals("geo:45.070210,7.686920?q=45.070210,7.686920", ParkedCarStore.mapUri(car))
        } finally {
            java.util.Locale.setDefault(java.util.Locale.US)
        }
    }

    // --- the clock

    @Test
    fun theHandsOfTheClockAreWhereTheTimeSaysAndTheHourHandMovesOn() {
        val (hour, minute) = ClockHands.angles(LocalTime.of(3, 0))
        assertEquals(90f, hour, 0.01f)
        assertEquals(0f, minute, 0.01f)
        val (h2, m2) = ClockHands.angles(LocalTime.of(14, 30))
        assertEquals(75f, h2, 0.01f)
        assertEquals(180f, m2, 0.01f)
        assertEquals(0f, ClockHands.angles(LocalTime.of(12, 0)).first, 0.01f)
    }

    // --- the colour of an icon

    @Test
    fun aLightIconColourIsDarkenedToCarryWhiteText() {
        fun luma(argb: Int) = (0.2126f * ((argb shr 16) and 0xFF) + 0.7152f * ((argb shr 8) and 0xFF) + 0.0722f * (argb and 0xFF)) / 255f
        val yellow = 0xFFFFE000.toInt()
        assertTrue(luma(DominantColor.forTile(yellow)) <= 0.46f)
        // A dark colour is left as it is.
        val navy = 0xFF102060.toInt()
        assertEquals(navy, DominantColor.forTile(navy))
        // The hue stays: the red of the darkened yellow is still above its blue.
        val shaded = DominantColor.forTile(yellow)
        assertTrue(((shaded shr 16) and 0xFF) > (shaded and 0xFF))
    }

    // --- the Action Center

    @Test
    fun aButtonMovesAlongTheRowAndTheOthersMoveOver() {
        val row = listOf(QuickAction.Wifi, QuickAction.Bluetooth, QuickAction.Flashlight, QuickAction.Rotation)
        assertEquals(
            listOf(QuickAction.Bluetooth, QuickAction.Flashlight, QuickAction.Rotation, QuickAction.Wifi),
            QuickAction.move(row, 0, 3),
        )
        assertEquals(
            listOf(QuickAction.Rotation, QuickAction.Wifi, QuickAction.Bluetooth, QuickAction.Flashlight),
            QuickAction.move(row, 3, 0),
        )
        assertEquals(row, QuickAction.move(row, 1, 1))
        // A place that is not in the row changes nothing.
        assertEquals(row, QuickAction.move(row, 0, 9))
    }

    @Test
    fun aVolumePresetIsKeptUnderANameAndTheSameNameReplacesIt() {
        val loud = VolumeProfiles.capture("  Loud  ", ring = 1f, media = 0.8f, alarm = 1f)!!
        assertEquals(VolumeProfile("Loud", 100, 80, 100), loud)
        assertNull(VolumeProfiles.capture("   ", 1f, 1f, 1f))
        val quieter = VolumeProfiles.capture("loud", 0.5f, 0.5f, 0.5f)!!
        assertEquals(listOf(quieter), VolumeProfiles.add(listOf(loud), quieter))
        assertEquals(VolumeProfiles.MAX, VolumeProfiles.add(List(VolumeProfiles.MAX) { VolumeProfile("p$it", 1, 1, 1) }, VolumeProfile("extra", 1, 1, 1)).size)
        assertEquals(listOf(loud), VolumeProfiles.decode(VolumeProfiles.encode(listOf(loud))))
        assertTrue(VolumeProfiles.decode("nonsense").isEmpty())
    }

    // --- the search box

    @Test
    fun theSearchBoxKnowsTheSwitchesByNameInBothLanguages() {
        assertEquals(QuickAction.Flashlight, SystemCommands.of("torch")?.action)
        assertEquals(QuickAction.Flashlight, SystemCommands.of("  Accendi la Torcia ")?.action)
        assertEquals(QuickAction.Wifi, SystemCommands.of("turn off Wi-Fi")?.action)
        assertEquals(QuickAction.Airplane, SystemCommands.of("modalità aereo")?.action)
        assertEquals(RingerMode.Silent, SystemCommands.of("silent")?.ringer)
        assertEquals(RingerMode.Vibrate, SystemCommands.of("vibra")?.ringer)
        assertEquals(QuickAction.QuietHours, SystemCommands.of("do not disturb")?.action)
    }

    @Test
    fun aWordThatOnlyContainsACommandIsNotOne() {
        assertNull(SystemCommands.of("torch app for kids"))
        assertNull(SystemCommands.of("wifi password"))
        assertNull(SystemCommands.of(""))
        assertNull(SystemCommands.of("a"))
    }

    @Test
    fun onlyTheSwitchesAndroidLetsUsChangeAreDoneOnTheSpot() {
        assertTrue(SystemCommands.inPlace(SystemCommands.of("torch")!!))
        assertTrue(SystemCommands.inPlace(SystemCommands.of("silent")!!))
        assertFalse(SystemCommands.inPlace(SystemCommands.of("wifi")!!))
        assertFalse(SystemCommands.inPlace(QuickAnswer.Command(QuickAction.Bluetooth, null)))
    }

    // --- tips

    @Test
    fun theTipChangesEveryDayAndComesAroundAfterTheList() {
        val day = LocalDate.of(2026, 10, 28)
        assertEquals((Tips.indexFor(day) + 1) % Tips.COUNT, Tips.indexFor(day.plusDays(1)))
        assertEquals(Tips.indexFor(day), Tips.indexFor(day.plusDays(Tips.COUNT.toLong())))
        // Days before 1970 still give a place in the list.
        assertTrue(Tips.indexFor(LocalDate.of(1960, 5, 5)) in 0 until Tips.COUNT)
    }

    // --- putting back one page of the settings

    @Test
    fun everyNameOfAPageIsInTheSavedFile() {
        val saved = JSONObject(SettingsCodec.encode(LauncherSettings()))
        SectionReset.keys.forEach { (section, names) ->
            names.forEach { name -> assertTrue("$section: $name is not in the saved settings", saved.has(name)) }
        }
    }

    @Test
    fun aPageThatIsAsAtTheStartHasNothingToPutBack() {
        SectionReset.keys.keys.forEach { assertFalse(it, SectionReset.changed(LauncherSettings(), it)) }
        assertFalse(SectionReset.canReset("About"))
    }

    @Test
    fun resettingAPageTouchesThatPageOnly() {
        val changed = LauncherSettings(
            lockStart = true, startTopAfterMin = 10, stepGoal = 5000, // Start
            iconShape = IconShape.Circle, // Icons
            searchWeb = false, // Search
            hiddenApps = setOf("com.example.hidden"), startPages = 3, // not on any page that can be reset
        )
        assertTrue(SectionReset.changed(changed, "Start"))
        val back = SectionReset.reset(changed, "Start")
        assertFalse(back.lockStart)
        assertEquals(0, back.startTopAfterMin)
        assertEquals(Steps.GOAL, back.stepGoal)
        // The other pages and the data of the user stay.
        assertEquals(IconShape.Circle, back.iconShape)
        assertFalse(back.searchWeb)
        assertEquals(setOf("com.example.hidden"), back.hiddenApps)
        assertEquals(3, back.startPages)
        assertFalse(SectionReset.changed(back, "Start"))
        assertTrue(SectionReset.changed(back, "Icons"))
    }

    // --- the new settings in the saved file

    @Test
    fun theSettingsOfTheSixthRoundSurviveTheFile() {
        val s = LauncherSettings(
            stepGoal = 7500, startTopAfterMin = 5, lockStart = true, startLowerPercent = 25, iconShape = IconShape.Squircle, iconTileColors = true,
            accentNightIndex = 7, volumeProfiles = listOf(VolumeProfile("Night", 0, 20, 60)), volumeProfilesShow = false, torchAutoOffMin = 5,
            searchCommands = false, tipOfDay = false, undoSteps = 5,
        )
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
        // A file from before the round has none of them: the settings start as they are.
        val old = SettingsCodec.decode("{}")!!
        assertEquals(LauncherSettings(), old)
        assertEquals(1, old.undoSteps)
        assertEquals(-1, old.accentNightIndex)
        // Numbers out of range are brought back in.
        val odd = SettingsCodec.decode("""{"startLowerPercent":99,"undoSteps":0,"accentNightIndex":77}""")!!
        assertEquals(60, odd.startLowerPercent)
        assertEquals(1, odd.undoSteps)
        assertEquals(19, odd.accentNightIndex)
    }

    // --- recordings: names

    @Test
    fun aRecordingKeepsItsDateAndGainsAName() {
        assertEquals("2026-10-26 18:30:05", Recordings.title("20261026-183005.m4a"))
        assertEquals("2026-10-26 18:30:05 · Lesson 2", Recordings.title("20261026-183005 Lesson 2.m4a"))
        assertEquals("Lesson 2", Recordings.nameOf("20261026-183005 Lesson 2.m4a"))
        assertEquals("", Recordings.nameOf("20261026-183005.m4a"))
    }

    @Test
    fun aNameForARecordingHasNothingOddInIt() {
        assertEquals("a b", Recordings.cleanName("  a   b  "))
        assertEquals("secret", Recordings.cleanName("../se/cr:et*"))
        assertEquals(40, Recordings.cleanName("x".repeat(100)).length)
        assertEquals("", Recordings.cleanName("///"))
    }

    @Test
    fun aRecordingIsRenamedOnTheDiskAndABusyNameIsRefused() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val store = Recordings(context)
        val first = store.newFile(java.time.LocalDateTime.of(2026, 10, 26, 18, 30, 5)).also { it.writeText("a") }
        val second = store.newFile(java.time.LocalDateTime.of(2026, 10, 26, 18, 31, 5)).also { it.writeText("b") }
        try {
            val named = store.rename(first, "Lesson")
            assertNotNull(named)
            assertEquals("20261026-183005 Lesson.m4a", named!!.name)
            assertFalse(first.exists())
            // The same name again is not a change.
            assertEquals(named, store.rename(named, "Lesson"))
            // A name that another recording of the same second has is refused.
            val twin = java.io.File(named.parentFile, "20261026-183105 Lesson.m4a").also { it.writeText("c") }
            assertNull(store.rename(second, "Lesson"))
            assertTrue(second.exists())
            twin.delete()
            // A blank name takes the name away.
            assertEquals("20261026-183005.m4a", store.rename(named, " ")!!.name)
            // A file that is not one of the recordings is not touched.
            assertNull(store.rename(java.io.File(context.cacheDir, "other.m4a"), "x"))
        } finally {
            store.list().forEach { store.delete(it) }
        }
    }
}
