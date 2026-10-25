package com.metrowin.launcher

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.AppRepository
import com.metrowin.launcher.data.Countdown
import com.metrowin.launcher.data.ShortcutCodec
import com.metrowin.launcher.data.ShortcutDef
import com.metrowin.launcher.data.ShortcutKind
import com.metrowin.launcher.data.StartEntries
import com.metrowin.launcher.data.TileCodec
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Folder
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.Tile
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
import java.time.LocalDate

/** The color of a tile, notes, countdowns, speed dials and settings on Start, and undoing a change. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartExtrasTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val red = 0xFFE51400.toInt()

    // --- the color of a tile

    @Test
    fun aTileAndAFolderCanHaveTheirOwnColorAndGetTheThemesBack() {
        val entries = listOf(Tile("a", TileSize.Medium), Folder("f", "Games", TileSize.Medium, listOf("x", "y")), Tile("b", TileSize.Small))
        val colored = StartEntries.setColor(StartEntries.setColor(entries, "a", red), "f", 0xFF0050EF.toInt())
        assertEquals(red, colored[0].color)
        assertEquals(0xFF0050EF.toInt(), colored[1].color)
        assertEquals("the others keep the theme's", 0, colored[2].color)
        assertEquals(0, StartEntries.setColor(colored, "a", 0)[0].color)
        assertEquals("an id that is not there changes nothing", colored, StartEntries.setColor(colored, "nope", red))
    }

    @Test
    fun theColorSurvivesResizingAndRestyling() {
        val entries = StartEntries.setColor(listOf(Tile("a", TileSize.Medium)), "a", red)
        assertEquals(red, StartEntries.resize(entries, "a")[0].color)
        assertEquals(red, StartEntries.setSize(entries, "a", TileSize.Wide)[0].color)
        assertEquals(red, StartEntries.setStyle(entries, "a", com.metrowin.launcher.model.TileStyle.Iconic)[0].color)
    }

    @Test
    fun theColorIsSavedWithTheTileAndOnlyWhenThereIsOne() {
        val entries = listOf(Tile("a", TileSize.Medium, color = red), Folder("f", "G", TileSize.Wide, listOf("x", "y"), color = 0xFF0050EF.toInt()), Tile("b", TileSize.Small))
        val json = TileCodec.encode(entries)
        assertEquals(entries, TileCodec.decode(json))
        assertEquals("a tile with no color writes none", 2, Regex("\"color\"").findAll(json).count())
    }

    @Test
    fun aSavedColorIsMadeOpaqueAndOldFilesHaveNone() {
        val translucent = TileCodec.decode("""[{"id":"a","size":"m","color":${0x11E51400}}]""")!!.single()
        assertEquals(0xFFE51400.toInt(), translucent.color)
        assertEquals(0, TileCodec.decode("""[{"id":"a","size":"m"}]""")!!.single().color)
    }

    // --- shortcuts that are not apps

    @Test
    fun aNoteIsATileThatShowsItsTextAndOpensItsEditor() {
        val item = ShortcutDef("n", ShortcutKind.Note, "Note", text = "buy milk").toItem(null)
        assertEquals("buy milk", item.note)
        assertEquals(Launch.Internal(AppRepository.ROUTE_NOTE), item.launch)
        assertTrue(item.pinOnly)
        assertNull("an app shows no note", AppItem("x", "x", Launch.Action("x")).note)
    }

    @Test
    fun aCountdownKeepsItsDate() {
        val day = LocalDate.of(2026, 12, 25).toEpochDay()
        val item = ShortcutDef("c", ShortcutKind.Countdown, "Christmas", epochDay = day).toItem(null)
        assertEquals(day, item.targetDay)
        assertEquals(Launch.Internal(AppRepository.ROUTE_COUNTDOWN), item.launch)
    }

    @Test
    fun aSettingTileOpensItsScreenWithItsGlyph() {
        val item = ShortcutDef("s", ShortcutKind.Setting, "Wi-Fi", "android.settings.WIFI_SETTINGS").toItem(null)
        assertEquals(Launch.Action("android.settings.WIFI_SETTINGS"), item.launch)
        assertEquals(Glyph.Wifi, item.glyph)
        assertEquals("an unknown screen has the glyph of the settings", Glyph.Settings, ShortcutDef("s", ShortcutKind.Setting, "x", "some.ACTION").toItem(null).glyph)
    }

    @Test
    fun aSpeedDialIsALinkToTheNumber() {
        val item = ShortcutDef("p", ShortcutKind.Call, "Anna", "tel:+393331234567").toItem("/photo.jpg")
        assertEquals(Launch.Link("tel:+393331234567"), item.launch)
        assertEquals(Glyph.Phone, item.glyph)
        assertEquals("/photo.jpg", item.photoPath)
    }

    @Test
    fun theNewKindsSurviveSavingAndRestoring() {
        val all = listOf(
            ShortcutDef("n", ShortcutKind.Note, "Note", text = "line one\nline two"),
            ShortcutDef("c", ShortcutKind.Countdown, "Trip", epochDay = 20_000L),
            ShortcutDef("s", ShortcutKind.Setting, "Battery", "android.settings.BATTERY_SAVER_SETTINGS"),
            ShortcutDef("p", ShortcutKind.Call, "Anna", "tel:+39333", photoFile = "p.jpg"),
        )
        assertEquals(all, ShortcutCodec.decode(ShortcutCodec.encode(all)))
        // A backup keeps them, without the photo file that belongs to this phone.
        assertEquals(all.map { it.copy(photoFile = null) }, ShortcutCodec.portable(all))
    }

    @Test
    fun aTelLinkCallsAtOnceOnlyWithThePhonePermission() {
        val repo = AppRepository(app)
        val tile = AppItem("p", "Anna", Launch.Link("tel:+393331234567"), pinOnly = true)
        assertTrue(repo.launch(tile, null))
        val dialer = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_DIAL, dialer.action)
        assertEquals("+393331234567", dialer.data!!.schemeSpecificPart)

        shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE)
        assertTrue(repo.launch(tile, null))
        assertEquals(Intent.ACTION_CALL, shadowOf(app).nextStartedActivity.action)

        // Any other link is opened as before.
        assertTrue(repo.launch(AppItem("w", "Web", Launch.Link("https://example.com"), pinOnly = true), null))
        assertEquals(Intent.ACTION_VIEW, shadowOf(app).nextStartedActivity.action)
    }

    // --- countdown

    @Test
    fun theDaysLeftAreCountedFromToday() {
        val today = LocalDate.of(2026, 3, 1)
        assertEquals(10L, Countdown.daysLeft(today.plusDays(10).toEpochDay(), today))
        assertEquals(0L, Countdown.daysLeft(today.toEpochDay(), today))
        assertEquals(-3L, Countdown.daysLeft(today.minusDays(3).toEpochDay(), today))
        assertEquals("a week from today is where a new one starts", today.plusDays(7).toEpochDay(), Countdown.defaultDay(today))
    }

    @Test
    fun theDateMovesByADayAMonthOrAYear() {
        val date = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 1), Countdown.step(date, Countdown.Field.Day, 1))
        assertEquals(LocalDate.of(2026, 2, 28), Countdown.step(date, Countdown.Field.Month, 1))
        assertEquals(LocalDate.of(2025, 1, 31), Countdown.step(date, Countdown.Field.Year, -1))
        assertEquals(LocalDate.of(2024, 2, 29), Countdown.step(LocalDate.of(2024, 2, 28), Countdown.Field.Day, 1))
    }

    // --- undoing a change (the view model)

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun removingATileCanBeUndoneWithItsNote() {
        val vm = LauncherViewModel(app)
        idle()
        val id = vm.addNote(0)
        vm.saveNote(id, "remember")
        idle()
        assertEquals("remember", vm.items.value.first { it.id == id }.note)
        assertNull("adding is not an undoable change", vm.undo.value)

        vm.unpin(id)
        idle()
        assertTrue(vm.items.value.none { it.id == id })
        assertNotNull(vm.undo.value)

        vm.undoLast()
        idle()
        assertEquals("remember", vm.items.value.first { it.id == id }.note)
        assertTrue(vm.pages.value.flatten().any { it.id == id })
        assertNull("it is used up", vm.undo.value)
    }

    @Test
    fun aResizeAndAColorCanBeUndoneAndTheLastChangeIsTheOneKept() {
        val vm = LauncherViewModel(app)
        idle()
        val id = vm.addNote(0)
        idle()
        val before = vm.pages.value.flatten().first { it.id == id }
        assertEquals(TileSize.Medium, before.size)

        vm.resize(id)
        vm.setColor(id, 0xFFE51400.toInt())
        val after = vm.pages.value.flatten().first { it.id == id }
        assertEquals(0xFFE51400.toInt(), after.color)
        assertEquals(TileSize.Wide, after.size)

        vm.undoLast() // only the color goes back
        val undone = vm.pages.value.flatten().first { it.id == id }
        assertEquals(0, undone.color)
        assertEquals(TileSize.Wide, undone.size)
    }

    @Test
    fun theUndoCanBeDismissedAndDoesNothingThen() {
        val vm = LauncherViewModel(app)
        idle()
        val id = vm.addNote(0)
        vm.setColor(id, 0xFFE51400.toInt())
        assertNotNull(vm.undo.value)
        vm.dismissUndo()
        assertNull(vm.undo.value)
        vm.undoLast()
        assertEquals(0xFFE51400.toInt(), vm.pages.value.flatten().first { it.id == id }.color)
    }

    @Test
    fun aNoteAndACountdownCanBeEditedInPlace() {
        val vm = LauncherViewModel(app)
        idle()
        val note = vm.addNote(0)
        vm.saveNote(note, "  milk and eggs  ")
        val countdown = vm.addCountdown("Trip", 20_000L, 0)
        idle()
        assertEquals("milk and eggs", vm.items.value.first { it.id == note }.note)
        assertEquals(20_000L, vm.items.value.first { it.id == countdown }.targetDay)
        vm.saveCountdown(countdown, "  ", 20_010L)
        idle()
        val edited = vm.items.value.first { it.id == countdown }
        assertEquals(20_010L, edited.targetDay)
        assertFalse("a blank name gets the default one", edited.label.isBlank())
    }
}
