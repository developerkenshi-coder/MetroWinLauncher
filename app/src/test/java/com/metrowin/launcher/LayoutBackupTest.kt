package com.metrowin.launcher

import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LayoutBackup
import com.metrowin.launcher.data.LayoutBackupCodec
import com.metrowin.launcher.data.ShortcutCodec
import com.metrowin.launcher.data.ShortcutDef
import com.metrowin.launcher.data.ShortcutKind
import com.metrowin.launcher.data.ThemeMode
import com.metrowin.launcher.model.Folder
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.StartEntry
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.model.TileStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutBackupTest {
    private val pages = listOf(
        listOf<StartEntry>(Tile("a", TileSize.Wide, TileStyle.Iconic), Folder("f", "Games", TileSize.Medium, listOf("b", "c"))),
        listOf<StartEntry>(Tile("shortcut:site:1", TileSize.Medium)),
    )
    private val site = ShortcutDef("shortcut:site:1", ShortcutKind.Site, "Example", "https://example.com")
    private val contact = ShortcutDef("shortcut:contact:2", ShortcutKind.Contact, "Giulia", "content://contacts/lookup/xyz", photoFile = "giulia.jpg")
    private val widget = ShortcutDef("shortcut:widget:3", ShortcutKind.Widget, "Clock", widgetProvider = "com.example/.W", widgetId = 42)
    private val mine = LauncherSettings(
        themeMode = ThemeMode.Light, accentIndex = 9, startPages = 2, hiddenApps = setOf("app:x"),
        backupFolder = "content://tree/mine", syncPassphrase = "mine", deviceId = "this-phone", kidsMode = false,
    )

    private fun encode(backup: LayoutBackup) = LayoutBackupCodec.encode(backup, exportedAt = 1_700_000_000_000L)

    @Test
    fun aBackupRestoresPagesStylesFoldersAndSettings() {
        val backup = LayoutBackup(pages, listOf(site), mine.copy(accentIndex = 3))
        val restored = LayoutBackupCodec.decode(encode(backup), current = LauncherSettings())!!
        assertEquals(pages, restored.pages)
        assertEquals(3, restored.settings.accentIndex)
        assertEquals(ThemeMode.Light, restored.settings.themeMode)
        assertEquals(setOf("app:x"), restored.settings.hiddenApps)
        assertEquals(listOf(site), restored.shortcuts)
    }

    @Test
    fun aWidgetIsNotCarriedBecauseItsIdBelongsToThisPhone() {
        val restored = LayoutBackupCodec.decode(encode(LayoutBackup(pages, listOf(site, widget), mine)), LauncherSettings())!!
        assertEquals(listOf(site), restored.shortcuts)
    }

    @Test
    fun aContactKeepsItsNameAndAddressButNotItsPhoto() {
        val restored = LayoutBackupCodec.decode(encode(LayoutBackup(pages, listOf(contact), mine)), LauncherSettings())!!
        val back = restored.shortcuts.single()
        assertEquals("Giulia", back.label)
        assertEquals(contact.uri, back.uri)
        assertNull(back.photoFile)
    }

    @Test
    fun whatBelongsToThisPhoneStaysWhenALayoutIsRestored() {
        val fromAnotherPhone = mine.copy(accentIndex = 15, backupFolder = "content://tree/other", syncPassphrase = "theirs", deviceId = "other")
        val text = encode(LayoutBackup(pages, emptyList(), fromAnotherPhone))
        assertFalse("the passphrase is not in the file", text.contains("theirs"))
        assertFalse(text.contains("content://tree/other"))
        val restored = LayoutBackupCodec.decode(text, current = mine)!!
        assertEquals(15, restored.settings.accentIndex)
        assertEquals("content://tree/mine", restored.settings.backupFolder)
        assertEquals("mine", restored.settings.syncPassphrase)
        assertEquals("this-phone", restored.settings.deviceId)
    }

    @Test
    fun aBackupNeverTurnsOnTheKidsCorner() {
        val text = encode(LayoutBackup(pages, emptyList(), mine.copy(kidsMode = true)))
        val restored = LayoutBackupCodec.decode(text, current = mine.copy(kidsMode = false))!!
        assertFalse(restored.settings.kidsMode)
    }

    @Test
    fun otherJsonAndOtherVersionsAreRefused() {
        assertNull(LayoutBackupCodec.decode("""{"hello": "world"}""", LauncherSettings()))
        assertNull(LayoutBackupCodec.decode("not json", LauncherSettings()))
        assertNull(LayoutBackupCodec.decode("""{"format": "something-else", "version": 1, "pages": []}""", LauncherSettings()))
        assertNull(
            "a file from a newer version could mean anything",
            LayoutBackupCodec.decode("""{"format": "metrowin-layout", "version": 99, "pages": []}""", LauncherSettings()),
        )
        assertNull(LayoutBackupCodec.decode("""{"format": "metrowin-layout", "version": 1}""", LauncherSettings()))
    }

    @Test
    fun aFileWithoutSettingsOrShortcutsKeepsTheCurrentOnes() {
        val restored = LayoutBackupCodec.decode("""{"format": "metrowin-layout", "version": 1, "pages": [[]]}""", mine)!!
        assertEquals(mine, restored.settings)
        assertTrue(restored.shortcuts.isEmpty())
        assertNotNull(restored.pages)
    }

    // --- shortcuts

    @Test
    fun shortcutsSurviveSaving() {
        val all = listOf(site, contact, widget)
        assertEquals(all, ShortcutCodec.decode(ShortcutCodec.encode(all)))
    }

    @Test
    fun aShortcutOfAKindWeDoNotKnowIsSkippedNotAnError() {
        val json = """[{"id":"a","kind":"hologram","label":"x"},{"id":"b","kind":"site","label":"Site","uri":"https://example.com"}]"""
        assertEquals(listOf("b"), ShortcutCodec.decode(json)!!.map { it.id })
    }

    @Test
    fun aShortcutIsATileThatIsNotInTheAppList() {
        val item = site.toItem(photoPath = null)
        assertTrue(item.pinOnly)
        assertEquals(Launch.Link("https://example.com"), item.launch)
        assertEquals(Glyph.Globe, item.glyph)
        assertEquals(Launch.Widget(42), widget.toItem(null).launch)
        assertEquals(Glyph.People, contact.toItem("/photos/giulia.jpg").glyph)
        assertEquals("/photos/giulia.jpg", contact.toItem("/photos/giulia.jpg").photoPath)
    }

    @Test
    fun twoShortcutsGetDifferentIds() {
        val one = ShortcutDef.newId(ShortcutKind.Site, "https://example.com")
        val two = ShortcutDef.newId(ShortcutKind.Site, "https://example.com")
        assertTrue(one != two)
        assertTrue(one.startsWith("shortcut:site:"))
    }
}
