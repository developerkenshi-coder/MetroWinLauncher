package com.metrowin.launcher

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.AppRepository
import com.metrowin.launcher.data.Birthday
import com.metrowin.launcher.data.Birthdays
import com.metrowin.launcher.data.Checklist
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.MoonPhase
import com.metrowin.launcher.data.SettingsCodec
import com.metrowin.launcher.data.ShortcutCodec
import com.metrowin.launcher.data.ShortcutDef
import com.metrowin.launcher.data.ShortcutKind
import com.metrowin.launcher.data.TileSpacing
import com.metrowin.launcher.data.WorldCities
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.LiveKind
import com.metrowin.launcher.model.TileData
import com.metrowin.launcher.model.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The counter, the list, the world clock, the moon, the birthdays and the other additions to Start of the second round. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartMoreTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    // --- the list of things to do

    @Test
    fun aListIsSavedAsLinesAndReadBack() {
        val items = listOf(Checklist.Item("milk", true), Checklist.Item("eggs", false))
        val raw = Checklist.serialize(items)
        assertEquals("[x] milk\n[ ] eggs", raw)
        assertEquals(items, Checklist.parse(raw))
    }

    @Test
    fun plainLinesAndBlankLinesAreRead() {
        assertEquals(listOf(Checklist.Item("one", false), Checklist.Item("two", false)), Checklist.parse("one\n\n  \n[ ] two"))
        assertTrue(Checklist.parse("").isEmpty())
    }

    @Test
    fun anItemIsAddedTickedAndRemoved() {
        var list = Checklist.add(emptyList(), "  bread  ")
        assertEquals(listOf(Checklist.Item("bread", false)), list)
        assertEquals("blank text adds nothing", list, Checklist.add(list, "   "))
        list = Checklist.add(list, "jam")
        list = Checklist.toggle(list, 1)
        assertEquals(1 to 2, Checklist.progress(list))
        assertEquals(listOf("bread"), Checklist.pending(list, 4))
        list = Checklist.toggle(list, 1)
        assertEquals(0 to 2, Checklist.progress(list))
        assertEquals(listOf(Checklist.Item("jam", false)), Checklist.remove(list, 0))
        assertEquals("an index that is not there changes nothing", list, Checklist.remove(list, 9))
    }

    @Test
    fun theDoneOnesAreClearedAtOnce() {
        val list = Checklist.parse("[x] a\n[ ] b\n[x] c")
        assertEquals(listOf(Checklist.Item("b", false)), Checklist.clearDone(list))
    }

    @Test
    fun aListHasALimitAndAnItemIsOneLine() {
        var list = emptyList<Checklist.Item>()
        repeat(Checklist.MAX_ITEMS + 5) { list = Checklist.add(list, "item $it") }
        assertEquals(Checklist.MAX_ITEMS, list.size)
        assertEquals(Checklist.MAX_TEXT, Checklist.add(emptyList(), "x".repeat(200)).single().text.length)
        assertEquals("two words", Checklist.add(emptyList(), "two\nwords").single().text)
        assertFalse(Checklist.serialize(Checklist.add(emptyList(), "a\nb")).contains("\n"))
    }

    @Test
    fun pendingIsLimitedToWhatFitsOnTheTile() {
        val list = Checklist.parse("a\nb\nc\nd\ne")
        assertEquals(listOf("a", "b", "c"), Checklist.pending(list, 3))
    }

    // --- the moon

    @Test
    fun theMoonIsNewAndFullOnKnownDates() {
        // New moon 11 January 2024, full moon 25 January 2024, first quarter 18 January 2024.
        assertEquals(MoonPhase.Name.Full, MoonPhase.name(LocalDate.of(2024, 1, 25)))
        assertEquals(MoonPhase.Name.FirstQuarter, MoonPhase.name(LocalDate.of(2024, 1, 18)))
        assertEquals(MoonPhase.Name.LastQuarter, MoonPhase.name(LocalDate.of(2024, 2, 2)))
    }

    @Test
    fun theLitPartGrowsToFullAndShrinksAgain() {
        assertTrue(MoonPhase.illumination(LocalDate.of(2024, 1, 11)) < 0.05)
        assertTrue(MoonPhase.illumination(LocalDate.of(2024, 1, 25)) > 0.95)
        assertEquals(0.5, MoonPhase.illumination(LocalDate.of(2024, 1, 18)), 0.12)
        assertTrue(MoonPhase.waxing(LocalDate.of(2024, 1, 15)))
        assertFalse(MoonPhase.waxing(LocalDate.of(2024, 1, 29)))
    }

    @Test
    fun theAgeIsAlwaysInsideTheMonth() {
        var day = LocalDate.of(1990, 1, 1)
        repeat(400) {
            val age = MoonPhase.age(day)
            assertTrue("age $age on $day", age >= 0.0 && age < 29.6)
            val lit = MoonPhase.illumination(day)
            assertTrue(lit in 0.0..1.0)
            day = day.plusDays(37)
        }
        assertTrue("dates before the reference give an age too", MoonPhase.age(LocalDate.of(1970, 1, 1)) in 0.0..29.6)
    }

    // --- the world clock

    @Test
    fun aCityIsFoundByItsEnglishOrItalianNameWithoutAccentsOrCapitals() {
        assertEquals("Europe/London", WorldCities.find("Londra")!!.zone)
        assertEquals("Europe/London", WorldCities.find("  LONDON ")!!.zone)
        assertEquals("Asia/Tokyo", WorldCities.find("tokyo")!!.zone)
        assertNull(WorldCities.find("atlantis"))
        assertNull(WorldCities.find(""))
    }

    @Test
    fun theCitiesAreSearchedByThePartOfAWord() {
        assertTrue(WorldCities.search("new").any { it.name == "New York" })
        assertTrue("a later word of the name counts", WorldCities.search("york").any { it.name == "New York" })
        assertEquals("an empty search gives them all", WorldCities.all.size, WorldCities.search("").size)
        assertTrue(WorldCities.search("zzzz").isEmpty())
        val names = WorldCities.search("").map { it.name }
        assertEquals("in alphabetical order", names.sortedBy { it.lowercase() }, names)
    }

    @Test
    fun everyCityHasARealTimeZoneAndANameOfItsOwn() {
        WorldCities.all.forEach { city -> assertNotNull(city.name, runCatching { ZoneId.of(city.zone) }.getOrNull()) }
        assertEquals(WorldCities.all.size, WorldCities.all.map { it.name }.toSet().size)
    }

    @Test
    fun theTimeThereComesWithTheDayItIs() {
        val rome = ZoneId.of("Europe/Rome")
        val instant = Instant.parse("2026-01-15T22:30:00Z") // 23:30 in Rome
        val tokyo = WorldCities.timeIn(ZoneId.of("Asia/Tokyo"), instant, rome)
        assertEquals(7, tokyo.time.hour)
        assertEquals(30, tokyo.time.minute)
        assertEquals("tomorrow there", 1, tokyo.dayOffset)
        val newYork = WorldCities.timeIn(ZoneId.of("America/New_York"), instant, rome)
        assertEquals(17, newYork.time.hour)
        assertEquals(0, newYork.dayOffset)
        val honolulu = WorldCities.timeIn(ZoneId.of("Pacific/Honolulu"), Instant.parse("2026-01-15T05:00:00Z"), rome)
        assertEquals("yesterday there", -1, honolulu.dayOffset)
    }

    // --- the birthdays

    @Test
    fun aBirthdayIsReadWithOrWithoutTheYear() {
        assertEquals(Birthday("Anna", 3, 5, 1990), Birthdays.parse("Anna", "1990-03-05"))
        assertEquals(Birthday("Anna", 3, 5, null), Birthdays.parse("Anna", "--03-05"))
        assertEquals(Birthday("Leap", 2, 29, null), Birthdays.parse("Leap", "--02-29"))
    }

    @Test
    fun anImpossibleOrUnreadableBirthdayIsLeftOut() {
        assertNull(Birthdays.parse("A", null))
        assertNull(Birthdays.parse("A", ""))
        assertNull(Birthdays.parse("A", "05/03/1990"))
        assertNull(Birthdays.parse("A", "1990-13-01"))
        assertNull(Birthdays.parse("A", "1990-02-30"))
        assertNull(Birthdays.parse("A", "--04-31"))
        assertNull(Birthdays.parse("A", "1990-00-10"))
    }

    @Test
    fun theNextBirthdayIsThisYearOrTheNext() {
        val anna = Birthday("Anna", 3, 5, 1990)
        assertEquals(LocalDate.of(2026, 3, 5), Birthdays.nextDate(anna, LocalDate.of(2026, 1, 1)))
        assertEquals("on the day itself", LocalDate.of(2026, 3, 5), Birthdays.nextDate(anna, LocalDate.of(2026, 3, 5)))
        assertEquals(LocalDate.of(2027, 3, 5), Birthdays.nextDate(anna, LocalDate.of(2026, 3, 6)))
    }

    @Test
    fun the29thOfFebruaryIsTheEndOfFebruaryInOtherYears() {
        val leap = Birthday("Leap", 2, 29, 2000)
        assertEquals(LocalDate.of(2026, 2, 28), Birthdays.nextDate(leap, LocalDate.of(2026, 1, 10)))
        assertEquals(LocalDate.of(2028, 2, 29), Birthdays.nextDate(leap, LocalDate.of(2027, 3, 1)))
    }

    @Test
    fun theSoonestBirthdaysComeFirstWithTheAgeTheyTurn() {
        val today = LocalDate.of(2026, 6, 10)
        val list = listOf(
            Birthday("Zoe", 6, 12, 2000),
            Birthday("Anna", 6, 10, 1990),
            Birthday("Marco", 6, 12, null),
            Birthday("Luca", 1, 2, 1980),
        )
        val next = Birthdays.upcoming(list, today, 3)
        assertEquals(listOf("Anna", "Marco", "Zoe"), next.map { it.name })
        assertEquals(listOf(0, 2, 2), next.map { it.daysAway })
        assertEquals("turns 36 today", 36, next[0].turning)
        assertNull("no year, no age", next[1].turning)
        assertEquals(26, next[2].turning)
        assertEquals(4, Birthdays.upcoming(list, today).size)
        assertTrue(Birthdays.upcoming(emptyList(), today).isEmpty())
    }

    // --- the tiles as shortcuts

    @Test
    fun theNewTilesAreMadeOfTheirData() {
        val counter = ShortcutDef("c", ShortcutKind.Counter, "Water", number = 7L).toItem(null)
        assertEquals(TileData.Counter(7), counter.data)
        assertEquals(Launch.Internal(AppRepository.ROUTE_COUNTER), counter.launch)
        assertTrue(counter.pinOnly)

        val list = ShortcutDef("l", ShortcutKind.Checklist, "Shop", text = "[x] a\n[ ] b").toItem(null)
        assertEquals(TileData.Checklist("[x] a\n[ ] b"), list.data)
        assertEquals(Launch.Internal(AppRepository.ROUTE_CHECKLIST), list.launch)

        val clock = ShortcutDef("w", ShortcutKind.WorldClock, "Tokyo", "Asia/Tokyo").toItem(null)
        assertEquals(TileData.WorldClock("Asia/Tokyo"), clock.data)
        assertEquals(Launch.Internal(AppRepository.ROUTE_NONE), clock.launch)

        val moon = ShortcutDef("m", ShortcutKind.Moon, "Moon").toItem(null)
        assertEquals(TileData.Moon, moon.data)
        assertEquals(Launch.Internal(AppRepository.ROUTE_NONE), moon.launch)

        val birthdays = ShortcutDef("b", ShortcutKind.Birthdays, "Birthdays").toItem(null)
        assertEquals(LiveKind.Birthdays, birthdays.live)
        assertEquals(Launch.Link(ShortcutDef.CONTACTS_ADDRESS), birthdays.launch)
        assertNull(birthdays.data)
    }

    @Test
    fun anAppShortcutTileCarriesItsAppAndItsId() {
        val uri = ShortcutDef.appShortcutUri("com.example.mail", 10L, "compose:new")
        assertEquals("appshortcut:com.example.mail:10:compose:new", uri)
        assertEquals(Triple("com.example.mail", 10L, "compose:new"), ShortcutDef.appShortcutParts(uri))
        assertNull(ShortcutDef.appShortcutParts("https://example.com"))
        assertNull(ShortcutDef.appShortcutParts("appshortcut:pkg:notanumber:id"))
        assertNull(ShortcutDef.appShortcutParts("appshortcut:pkg"))

        val item = ShortcutDef("s", ShortcutKind.AppShortcut, "Write", uri).toItem(null)
        assertEquals("com.example.mail", item.packageName)
        assertEquals(Launch.Link(uri), item.launch)
        assertNull("the icon is the one of the app", item.glyph)
        assertNull("other tiles have no app", ShortcutDef("n", ShortcutKind.Note, "n").toItem(null).packageName)
    }

    @Test
    fun theNewKindsAndTheCountSurviveSavingAndBackups() {
        val all = listOf(
            ShortcutDef("c", ShortcutKind.Counter, "Water", number = 12L),
            ShortcutDef("l", ShortcutKind.Checklist, "Shop", text = "[ ] bread\n[x] milk"),
            ShortcutDef("w", ShortcutKind.WorldClock, "Tokyo", "Asia/Tokyo"),
            ShortcutDef("m", ShortcutKind.Moon, "Moon"),
            ShortcutDef("b", ShortcutKind.Birthdays, "Birthdays"),
            ShortcutDef("s", ShortcutKind.AppShortcut, "Write", "appshortcut:com.example.mail:0:new"),
        )
        assertEquals(all, ShortcutCodec.decode(ShortcutCodec.encode(all)))
        assertEquals(all, ShortcutCodec.portable(all))
    }

    // --- the settings

    @Test
    fun theNamesAndTheSpacingOfTheTilesAreSaved() {
        val custom = LauncherSettings(showTileNames = false, tileSpacing = TileSpacing.Wide)
        val back = SettingsCodec.decode(SettingsCodec.encode(custom))!!
        assertFalse(back.showTileNames)
        assertEquals(TileSpacing.Wide, back.tileSpacing)
        assertEquals(custom, SettingsCodec.decode(SettingsCodec.encode(custom, portable = true)))
    }

    @Test
    fun oldFilesAndUnknownSpacingGiveTheDefaults() {
        val old = SettingsCodec.decode("{}")!!
        assertTrue(old.showTileNames)
        assertEquals(TileSpacing.Normal, old.tileSpacing)
        assertEquals(TileSpacing.Normal, SettingsCodec.decode("""{"tileSpacing":"huge"}""")!!.tileSpacing)
        assertTrue(TileSpacing.Tight.factor < TileSpacing.Normal.factor && TileSpacing.Normal.factor < TileSpacing.Wide.factor)
        assertEquals(1f, TileSpacing.Normal.factor, 0f)
    }

    // --- the view model

    @Test
    fun aCounterCountsUpDownAndResets() {
        val vm = LauncherViewModel(app)
        idle()
        val id = vm.addCounter(0)
        idle()
        assertEquals(TileData.Counter(0), vm.items.value.first { it.id == id }.data)
        assertEquals(TileSize.Medium, vm.pages.value.flatten().first { it.id == id }.size)

        vm.countStep(id, 1)
        vm.countStep(id, 1)
        vm.countStep(id, 1)
        idle()
        assertEquals(TileData.Counter(3), vm.items.value.first { it.id == id }.data)
        vm.countStep(id, -1)
        idle()
        assertEquals(TileData.Counter(2), vm.items.value.first { it.id == id }.data)

        repeat(5) { vm.countStep(id, -1) }
        idle()
        assertEquals("never below zero", TileData.Counter(0), vm.items.value.first { it.id == id }.data)

        vm.countStep(id, 4)
        vm.saveCounter(id, "  Water  ", reset = false)
        idle()
        val kept = vm.items.value.first { it.id == id }
        assertEquals("Water", kept.label)
        assertEquals(TileData.Counter(4), kept.data)
        vm.saveCounter(id, "", reset = true)
        idle()
        val cleared = vm.items.value.first { it.id == id }
        assertEquals(TileData.Counter(0), cleared.data)
        assertFalse("a blank name gets the default one", cleared.label.isBlank())

        vm.countStep("nothing", 1) // an id that is not there changes nothing
    }

    @Test
    fun aCounterStopsAtTheMaximum() {
        val vm = LauncherViewModel(app)
        idle()
        val id = vm.addCounter(0)
        vm.countStep(id, 99_999)
        vm.countStep(id, 1)
        idle()
        assertEquals(TileData.Counter(99_999), vm.items.value.first { it.id == id }.data)
    }

    @Test
    fun aListIsKeptOnItsTile() {
        val vm = LauncherViewModel(app)
        idle()
        val id = vm.addChecklist(0)
        vm.saveChecklist(id, Checklist.serialize(Checklist.parse("[x] a\n[ ] b")))
        idle()
        assertEquals(TileData.Checklist("[x] a\n[ ] b"), vm.items.value.first { it.id == id }.data)
        assertEquals(TileSize.Medium, vm.pages.value.flatten().first { it.id == id }.size)
    }

    @Test
    fun theWorldClockTheMoonAndTheBirthdaysAreAddedToTheScreenAsked() {
        val vm = LauncherViewModel(app)
        idle()
        vm.addWorldClock(WorldCities.find("Tokyo")!!, 0)
        vm.addMoon(0)
        vm.addBirthdays(0)
        idle()
        val items = vm.items.value
        val clock = items.first { it.data is TileData.WorldClock }
        assertEquals("Tokyo", clock.label)
        assertEquals("Asia/Tokyo", (clock.data as TileData.WorldClock).zone)
        assertTrue(items.any { it.data == TileData.Moon })
        val birthdaysId = items.first { it.live == LiveKind.Birthdays }.id
        assertEquals(TileSize.Wide, vm.pages.value.flatten().first { it.id == birthdaysId }.size)
        assertEquals(3, vm.shortcuts.value.size)
    }

    @Test
    fun restoringStartTakesAwayWhatWasAddedAndCanBeUndone() {
        val vm = LauncherViewModel(app)
        idle()
        val before = vm.pages.value.flatten().map { it.id }
        val note = vm.addNote(0)
        val counter = vm.addCounter(0)
        idle()
        assertTrue(vm.pages.value.flatten().any { it.id == note })

        vm.resetStart()
        idle()
        val after = vm.pages.value.flatten().map { it.id }
        assertTrue("the shortcuts are gone", note !in after && counter !in after)
        assertTrue(vm.shortcuts.value.isEmpty())
        assertTrue("what Start had at the start is still there", before.filterNot { it.startsWith("shortcut:") }.all { it in after })
        assertNotNull(vm.undo.value)

        vm.undoLast()
        idle()
        val restored = vm.pages.value.flatten().map { it.id }
        assertTrue(note in restored && counter in restored)
        assertEquals(2, vm.shortcuts.value.size)
    }

    @Test
    fun restoringStartKeepsTheNumberOfScreensTheUserChose() {
        val vm = LauncherViewModel(app)
        idle()
        vm.updateSettings { it.copy(startPages = 3) }
        idle()
        vm.resetStart()
        idle()
        assertEquals(3, vm.pages.value.size)
        assertTrue("only the first screen has tiles again", vm.pages.value.drop(1).all { it.isEmpty() })
    }
}
