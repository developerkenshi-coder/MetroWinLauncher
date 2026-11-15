package com.metrowin.launcher

import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.SectionReset
import com.metrowin.launcher.data.SettingsCodec
import com.metrowin.launcher.ui.settings.SettingsCategory
import com.metrowin.launcher.ui.settings.SettingsIndex
import com.metrowin.launcher.ui.settings.SettingsSection
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

/**
 * `docs/impostazioni-mappa.md` says where every setting is. It is checked against the code here, so a setting that moves, or a page that
 * changes its name, cannot leave the map wrong; and the reset of a page is checked against the same pages.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsMapTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>().let {
        it.createConfigurationContext(Configuration(it.resources.configuration).apply { setLocale(Locale.ITALY) })
    }
    private val doc = File("../docs/impostazioni-mappa.md").readText()

    /** The id and the "new page" cell of each row of the tables of settings (the first tables, not the table of moves). */
    private val rows: Map<String, String> = doc.substringAfter("## Impostazioni, una per una").substringBefore("## Spostamenti")
        .lines().filter { it.startsWith("| ") && it.contains("`") }
        .associate { line ->
            val cells = line.trim('|').split('|').map { it.trim() }
            cells[1].trim('`') to cells[3]
        }

    @Test
    fun everySettingOfTheIndexIsInTheMapOnItsPage() {
        SettingsIndex.entries.filter { !it.id.startsWith("mini.") && !it.id.startsWith("live.wx.") }.forEach { entry ->
            val expected = "${context.getString(entry.section.category.title)} › ${context.getString(entry.section.title)}"
            assertEquals("${entry.id} in docs/impostazioni-mappa.md", expected, rows[entry.id])
        }
    }

    @Test
    fun theMapNamesNoSettingThatIsGone() {
        val ids = SettingsIndex.entries.map { it.id }.toSet()
        // The two rows that stand for a whole family (the switches of the mini apps, the blocks of the weather) have a pattern for an id.
        assertEquals("rows of the map with no setting", emptySet<String>(), rows.keys.filter { '<' !in it }.toSet() - ids)
    }

    @Test
    fun theMapListsEveryCategoryAndEveryPage() {
        SettingsCategory.entries.forEach { category ->
            assertTrue("${category.name} ### heading", doc.contains("### ${context.getString(category.title)}\n"))
            category.pages.forEach { assertTrue("${it.name} in the structure table", doc.contains(context.getString(it.title))) }
        }
    }

    // --- the reset of a page follows the pages

    @Test
    fun everyResettablePageIsARealPage() {
        assertEquals(emptySet<String>(), SectionReset.keys.keys - SettingsSection.entries.map { it.name }.toSet())
    }

    @Test
    fun aSettingIsResetByOnePageOnly() {
        val seen = HashMap<String, String>()
        SectionReset.keys.forEach { (page, keys) ->
            keys.forEach { key ->
                val other = seen.put(key, page)
                assertTrue("$key is reset by $other and by $page", other == null)
            }
        }
    }

    @Test
    fun resettingAPagePutsBackItsSettingsAndNothingOfAnotherPage() {
        // Something changed on three pages that moved: touch and feel, the navigation bar, the status bar.
        val changed = LauncherSettings(
            tapSound = true, animationSpeed = 200, hideBarsInGames = false, navSpacing = 110, hideStatusBar = true, edgeActionCenter = true,
            bankSafe = false,
        )
        val feel = SectionReset.reset(changed, "Feel")
        assertEquals(LauncherSettings().tapSound, feel.tapSound)
        assertEquals(LauncherSettings().animationSpeed, feel.animationSpeed)
        assertEquals("the navigation bar is not on this page", 110, feel.navSpacing)
        assertTrue("nor the status bar", feel.hideStatusBar)
        val bars = SectionReset.reset(changed, "NavBar")
        assertEquals(LauncherSettings().navSpacing, bars.navSpacing)
        assertEquals(LauncherSettings().hideBarsInGames, bars.hideBarsInGames)
        assertEquals("touch and feel stays", 200, bars.animationSpeed)
        val status = SectionReset.reset(changed, "StatusBar")
        assertFalse(status.hideStatusBar)
        assertEquals("the Action Center is on its own page", true, status.edgeActionCenter)
        val actions = SectionReset.reset(changed, "Actions")
        assertEquals(LauncherSettings().edgeActionCenter, actions.edgeActionCenter)
        assertEquals("the banking apps are on the page of the protection", false, actions.bankSafe)
        assertEquals(LauncherSettings().bankSafe, SectionReset.reset(changed, "Privacy").bankSafe)
    }

    @Test
    fun noSettingIsLostByTheMove() {
        // The saved keys do not change, whatever the page that shows them: the codec writes every key it wrote before.
        val saved = JSONObject(SettingsCodec.encode(LauncherSettings())).keys().asSequence().toSet()
        listOf("tapSound", "navSpacing", "hideStatusBar", "edgeActionCenter", "actionCenterEnabled", "bankSafe", "lockedApps", "iconPack", "iconShape", "metroNavBar").forEach {
            assertTrue("$it is still saved", it in saved)
        }
    }
}
