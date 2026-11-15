package com.metrowin.launcher

import android.util.Xml
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.AppFilterXml
import com.metrowin.launcher.data.IconPack
import com.metrowin.launcher.data.IconPackMap
import com.metrowin.launcher.data.IconPick
import com.metrowin.launcher.data.IconPicks
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.SectionReset
import com.metrowin.launcher.data.SettingsCodec
import com.metrowin.launcher.data.ThemeCodec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.StringReader

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IconPicksTest {
    // --- the choice of one app

    @Test
    fun aChoiceIsPackAndMaybeADrawable() {
        assertEquals(IconPick("com.p", "chrome"), IconPick.parse("com.p|chrome"))
        assertEquals(IconPick("com.p"), IconPick.parse("com.p"))
        assertEquals(IconPick(""), IconPick.parse(""))
        // No pack means the icon of the app: a drawable beside it is nonsense and goes.
        assertEquals(IconPick(""), IconPick.parse("|chrome"))
        assertEquals(IconPick("com.p"), IconPick.parse("com.p|"))
        assertNull(IconPick.parse(null))
        assertEquals("com.p|chrome", IconPick("com.p", "chrome").encode())
        assertEquals("com.p", IconPick("com.p").encode())
    }

    @Test
    fun aChoiceIsGivenToManyAppsAndTakenAwayFromThem() {
        val set = IconPicks.with(mapOf("app:z" to "com.q"), listOf("app:a", "app:b"), IconPick("com.p", "x"))
        assertEquals(mapOf("app:z" to "com.q", "app:a" to "com.p|x", "app:b" to "com.p|x"), set)
        // Back to the default: only those apps lose their choice.
        assertEquals(mapOf("app:z" to "com.q", "app:b" to "com.p|x"), IconPicks.with(set, listOf("app:a"), null))
        // A new choice replaces the old one.
        assertEquals("com.q", IconPicks.with(set, listOf("app:a"), IconPick("com.q"))["app:a"])
    }

    // --- a pack that goes away

    @Test
    fun theAppsOfAPackThatWasRemovedFallBackToTheDefault() {
        val picks = mapOf("app:a" to "com.gone|x", "app:b" to "com.here", "app:c" to "", "app:d" to "com.here|y")
        assertEquals(
            mapOf("app:b" to "com.here", "app:c" to "", "app:d" to "com.here|y"),
            IconPicks.withoutMissing(picks, setOf("com.here")),
        )
        assertEquals(mapOf("app:c" to ""), IconPicks.withoutMissing(picks, emptySet()))
    }

    @Test
    fun thePackThatDressesAnAppIsTheChosenOneOrTheDefault() {
        val opened = mapOf("com.p" to "P")
        assertEquals("D", IconPicks.packFor(null, "D", opened))
        assertEquals("P", IconPicks.packFor(IconPick("com.p", "x"), "D", opened))
        // The pack of the choice is not open (it is gone): the default one.
        assertEquals("D", IconPicks.packFor(IconPick("com.gone"), "D", opened))
        // "": the icon of the app, with no pack, even when there is a default pack.
        assertNull(IconPicks.packFor(IconPick(""), "D", opened))
        assertNull(IconPicks.packFor(null, null, opened))
    }

    @Test
    fun theOpenPacksAreTheOnesTheChoicesName() {
        assertEquals(setOf("com.p", "com.q"), IconPicks.packsOf(mapOf("a" to "com.p|x", "b" to "com.q", "c" to "", "d" to "com.p")))
    }

    // --- the list of icons of a pack

    private fun drawables(xml: String) = AppFilterXml.parseDrawables(Xml.newPullParser().apply { setInput(StringReader(xml)) })

    @Test
    fun theDrawableXmlListsTheIconsOfAPack() {
        val names = drawables(
            """
            <resources>
              <category title="Social" />
              <item drawable="chat" />
              <item drawable="@drawable/mail.png" />
              <item drawable="chat" />
              <item drawable="" />
              <item />
            </resources>
            """.trimIndent(),
        )
        assertEquals(listOf("chat", "mail"), names)
    }

    @Test
    fun aPackListsTheIconsOfItsAppfilterAndItsDrawableXmlOnceEachInOrder() {
        val map = IconPackMap(icons = mapOf("com.a/.A" to "zebra", "com.b/.B" to "Apple", "com.c/.C" to "zebra"))
        val pack = IconPack(ApplicationProvider.getApplicationContext<android.content.Context>().resources, "com.p", map, listOf("banana", "Apple"))
        assertEquals(listOf("Apple", "banana", "zebra"), pack.names)
        assertTrue(IconPack(ApplicationProvider.getApplicationContext<android.content.Context>().resources, "com.p", IconPackMap()).names.isEmpty())
    }

    @Test
    fun theSearchByNameIgnoresCaseAndSeparators() {
        val names = listOf("ic_chrome_beta", "Chrome", "gmail-new", "maps")
        assertEquals(listOf("ic_chrome_beta", "Chrome"), IconPicks.filterNames(names, "CHROME"))
        assertEquals(listOf("ic_chrome_beta"), IconPicks.filterNames(names, "chrome beta"))
        assertEquals(listOf("gmail-new"), IconPicks.filterNames(names, "gmail new"))
        assertEquals(names, IconPicks.filterNames(names, "  "))
        assertTrue(IconPicks.filterNames(names, "zzz").isEmpty())
    }

    // --- saving

    @Test
    fun theChoicesSurviveAnEncodeAndDecode() {
        val s = LauncherSettings(iconPack = "com.d", iconPicks = mapOf("app:a" to "com.p|x", "app:b" to "com.q", "app:c" to ""))
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
        assertEquals(s.iconPicks, SettingsCodec.decode(SettingsCodec.encode(s, portable = true))!!.iconPicks)
    }

    @Test
    fun aFileWithoutTheChoicesKeepsWhatThePhoneHas() {
        val old = SettingsCodec.decode("""{"iconPack":"com.d"}""")!!
        assertEquals(emptyMap<String, String>(), old.iconPicks)
        val kept = SettingsCodec.decode("""{"iconPack":"com.d"}""", base = LauncherSettings(iconPicks = mapOf("app:a" to "com.p")))!!
        assertEquals(mapOf("app:a" to "com.p"), kept.iconPicks)
        // A file that has them, even none, is the user's choice.
        assertEquals(emptyMap<String, String>(), SettingsCodec.decode("""{"iconPicks":{}}""", base = kept)!!.iconPicks)
    }

    @Test
    fun aBrokenChoiceInAFileIsSkipped() {
        val read = SettingsCodec.decode("""{"iconPicks":{"app:a":"com.p|x","app:b":null,"app:c":5}}""")!!
        assertEquals("com.p|x", read.iconPicks["app:a"])
        assertFalse(read.iconPicks.containsKey("app:b"))
    }

    @Test
    fun theThemeFileCarriesNoChoicesOfApps() {
        val theme = JSONObject(ThemeCodec.encode(LauncherSettings(iconPicks = mapOf("app:a" to "com.p")))).getJSONObject("theme")
        assertFalse(theme.has("iconPicks"))
    }

    @Test
    fun resettingTheLookPutsTheChoicesBack() {
        val changed = LauncherSettings(iconPicks = mapOf("app:a" to "com.p"))
        assertTrue(SectionReset.changed(changed, "StartTheme"))
        assertTrue(SectionReset.reset(changed, "StartTheme").iconPicks.isEmpty())
    }
}
