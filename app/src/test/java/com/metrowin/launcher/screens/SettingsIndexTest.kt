package com.metrowin.launcher.screens

import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.SemanticsMatcher
import com.metrowin.launcher.data.IconPackInfo
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.ProfileInfo
import com.metrowin.launcher.data.ProfileType
import com.metrowin.launcher.data.ThemeMode
import com.metrowin.launcher.data.ThemeStyle
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.ui.settings.SettingAnchorKey
import com.metrowin.launcher.ui.settings.SettingsEnv
import com.metrowin.launcher.ui.settings.SettingsGroup
import com.metrowin.launcher.ui.settings.SettingsIndex
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.settings.SettingsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The map of the settings and the settings themselves have to say the same: every setting on a page is in the index, and every
 * setting in the index is on its page. A new setting that is not added to the index (or an index entry for a setting that was
 * moved) fails here, so the search can never lead to a page that does not have what it promised.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class SettingsIndexTest : ScreenshotBase() {
    private fun localized(locale: Locale): Context =
        context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale) })

    // --- the index by itself

    @Test
    fun everySettingHasAUniqueIdAndAPlace() {
        val ids = SettingsIndex.entries.map { it.id }
        assertEquals("ids are unique", ids.size, ids.toSet().size)
        assertTrue(SettingsIndex.entries.all { it.keywords.isNotEmpty() })
        SettingsSection.entries.forEach { section ->
            assertTrue("${section.name} has settings", SettingsIndex.of(section).isNotEmpty())
        }
        SettingsGroup.entries.forEach { group ->
            assertTrue("${group.name} has sections", SettingsSection.entries.any { it.group == group })
        }
    }

    @Test
    fun everyIdSaysWhichSectionItIsIn() {
        val prefixes = mapOf(
            SettingsSection.StartTheme to "theme", SettingsSection.Start to "start", SettingsSection.Icons to "icons",
            SettingsSection.Accessibility to "access", SettingsSection.Lock to "lock", SettingsSection.Screen to "screen",
            SettingsSection.Gestures to "gest", SettingsSection.Actions to "ac", SettingsSection.Voice to "voice",
            SettingsSection.Battery to "battery", SettingsSection.Apps to "apps", SettingsSection.Search to "search",
            SettingsSection.LiveTiles to "live", SettingsSection.Profiles to "profile", SettingsSection.Backup to "backup",
            SettingsSection.Kids to "kids", SettingsSection.About to "about", SettingsSection.Debug to "debug",
        )
        SettingsIndex.entries.forEach { entry ->
            val prefix = entry.id.substringBefore('.')
            val expected = prefixes.getValue(entry.section)
            // The sync switches live on the backup page and say so by their name.
            val ok = prefix == expected || (entry.section == SettingsSection.Backup && prefix == "sync")
            assertTrue("${entry.id} is in ${entry.section}", ok)
        }
    }

    @Test
    fun everyTitleIsWrittenInBothLanguages() {
        val en = localized(Locale.US)
        val it = localized(Locale.ITALY)
        SettingsIndex.entries.forEach { entry ->
            assertTrue("${entry.id} in English", en.getString(entry.title).isNotBlank())
            assertTrue("${entry.id} in Italian", it.getString(entry.title).isNotBlank())
        }
        SettingsSection.entries.forEach { section ->
            assertTrue(en.getString(section.title).isNotBlank() && it.getString(section.title).isNotBlank())
            assertTrue(en.getString(section.description).isNotBlank() && it.getString(section.description).isNotBlank())
        }
        SettingsGroup.entries.forEach { group -> assertTrue(en.getString(group.title).isNotBlank() && it.getString(group.title).isNotBlank()) }
    }

    @Test
    fun aSettingIsFoundByItsNameItsKeywordsAndItsSection() {
        val index = SettingsIndex.searchIndex(localized(Locale.US))
        fun first(query: String) = index.search(query).firstOrNull()
        assertEquals("theme.oled", first("oled")?.setting?.id)
        assertEquals("access.textsize", first("text size")?.setting?.id)
        assertEquals("a keyword", "start.reset", first("restore")?.setting?.id)
        assertEquals("the section by its name", SettingsSection.Gestures, first("gestures")?.section)
        assertEquals("and no setting for a section", null, first("gestures")?.setting)
        assertTrue("then its settings", index.search("gestures").drop(1).any { it.setting?.id == "gest.edgepull" })
        assertTrue("a section lists what it holds", index.search("search").any { it.setting?.id == "search.engine" })
        assertTrue(index.search("zzzzzz").isEmpty())
        assertTrue(index.search("   ").isEmpty())
    }

    @Test
    fun theSearchSpeaksItalianToo() {
        val index = SettingsIndex.searchIndex(localized(Locale.ITALY))
        assertEquals("start.clock", index.search("orologio").first().setting?.id)
        assertTrue(index.search("scuro").any { it.setting?.id == "theme.mode" })
        assertEquals("access.contrast", index.search("contrasto").first().setting?.id)
        // An English word finds it in the Italian phone as well.
        assertTrue(index.search("battery").any { it.setting?.id == "battery.saving" })
    }

    @Test
    fun aMistakeInTheWordStillFindsTheSetting() {
        val index = SettingsIndex.searchIndex(localized(Locale.US))
        assertTrue(index.search("parallx").any { it.setting?.id == "theme.parallax" })
    }

    // --- the pages agree with the index

    private val apps = listOf("Alpha", "Beta", "Gamma").map { AppItem("app:$it", it, Launch.Action("x"), packageName = "com.example.${it.lowercase()}") }

    private fun anchorsOf(section: SettingsSection, settings: LauncherSettings = LauncherSettings(), notificationAccess: Boolean = true): List<String> {
        show {
            SettingsScreen(
                env = SettingsEnv(
                    settings = settings, onChange = {}, apps = apps, onLanguage = {}, onAddRecentsTile = {}, notificationAccess = notificationAccess,
                    backupFolderName = "Documents/MetroWin", profiles = listOf(ProfileInfo(10, ProfileType.Work, false)),
                    iconPacks = listOf(IconPackInfo("com.example.pack", "Example pack")), lockServiceEnabled = false,
                ),
                section = section, onSectionChange = {}, sectionAway = remember { Animatable(0f) },
            )
        }
        advance(500)
        return compose.onAllNodes(SemanticsMatcher.keyIsDefined(SettingAnchorKey), useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.config[SettingAnchorKey] }
    }

    private fun assertPageHasExactly(section: SettingsSection, found: List<String>, expected: List<String> = SettingsIndex.of(section).map { it.id }) {
        assertEquals("a setting is on the page twice: ${found.groupBy { it }.filter { it.value.size > 1 }.keys}", found.size, found.toSet().size)
        assertEquals("$section: only on the page / only in the index", expected.sorted(), found.sorted())
    }

    @Test fun theStartThemePageIsTheIndex() = assertPageHasExactly(
        SettingsSection.StartTheme,
        anchorsOf(
            SettingsSection.StartTheme,
            // The hours, the colors of the phone and the framing of the photo only show in these cases.
            LauncherSettings(themeMode = ThemeMode.Schedule, themeStyle = ThemeStyle.Droid, backgroundRevision = 1),
        ),
    )

    @Test fun theStartPageIsTheIndex() = assertPageHasExactly(SettingsSection.Start, anchorsOf(SettingsSection.Start))
    @Test fun theIconsPageIsTheIndex() = assertPageHasExactly(SettingsSection.Icons, anchorsOf(SettingsSection.Icons))
    @Test fun theAccessibilityPageIsTheIndex() = assertPageHasExactly(SettingsSection.Accessibility, anchorsOf(SettingsSection.Accessibility))
    @Test fun theLockPageIsTheIndex() = assertPageHasExactly(SettingsSection.Lock, anchorsOf(SettingsSection.Lock))
    @Test fun theScreenPageIsTheIndex() = assertPageHasExactly(SettingsSection.Screen, anchorsOf(SettingsSection.Screen))
    @Test fun theGesturesPageIsTheIndex() = assertPageHasExactly(SettingsSection.Gestures, anchorsOf(SettingsSection.Gestures))
    @Test fun theActionsPageIsTheIndex() = assertPageHasExactly(SettingsSection.Actions, anchorsOf(SettingsSection.Actions))
    @Test fun theVoicePageIsTheIndex() = assertPageHasExactly(SettingsSection.Voice, anchorsOf(SettingsSection.Voice))
    @Test fun theBatteryPageIsTheIndex() = assertPageHasExactly(SettingsSection.Battery, anchorsOf(SettingsSection.Battery))
    @Test fun theAppsPageIsTheIndex() = assertPageHasExactly(SettingsSection.Apps, anchorsOf(SettingsSection.Apps))
    @Test fun theSearchPageIsTheIndex() = assertPageHasExactly(SettingsSection.Search, anchorsOf(SettingsSection.Search))
    // The button that asks for the access to the notifications is only there while the access is missing.
    @Test fun theLiveTilesPageIsTheIndex() = assertPageHasExactly(
        SettingsSection.LiveTiles, anchorsOf(SettingsSection.LiveTiles, notificationAccess = false),
    )
    @Test fun theProfilesPageIsTheIndex() = assertPageHasExactly(SettingsSection.Profiles, anchorsOf(SettingsSection.Profiles))
    @Test fun theKidsPageIsTheIndex() = assertPageHasExactly(SettingsSection.Kids, anchorsOf(SettingsSection.Kids))
    @Test fun theAboutPageIsTheIndex() = assertPageHasExactly(SettingsSection.About, anchorsOf(SettingsSection.About))

    @Test
    fun theBackupPageIsTheIndex() = assertPageHasExactly(
        SettingsSection.Backup,
        anchorsOf(SettingsSection.Backup, LauncherSettings(backupFolder = "content://tree/drive", syncEnabled = true)),
    )
}
