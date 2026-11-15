package com.metrowin.launcher

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.ui.settings.SettingsCategory
import com.metrowin.launcher.ui.settings.SettingsIndex
import com.metrowin.launcher.ui.settings.SettingsInfo
import com.metrowin.launcher.ui.settings.SettingsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale
import java.util.regex.Pattern

/**
 * The infobox: every category and every setting of the index says what it is for, in the six languages (en, it, de, es, fr, pt), from
 * `strings_settings_info.xml`. The files are read as text, because a missing translation of Android falls back to English without a sound.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsInfoTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val dirs = mapOf("en" to "values", "it" to "values-it", "de" to "values-de", "es" to "values-es", "fr" to "values-fr", "pt" to "values-pt")

    private fun infoFile(dir: String): Map<String, String> {
        val text = File("src/main/res/$dir/strings_settings_info.xml").readText()
        return Regex("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL).findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun placeholders(text: String): List<String> =
        Pattern.compile("%(\\d\\$)?[sd]").matcher(text).let { m -> buildList { while (m.find()) add(m.group().replace(Regex("^%\\d\\$"), "%")) } }.sorted()

    private fun localized(tag: String): Context =
        context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })

    @Test
    fun theInfoStringsAreInAllSixLanguagesWithTheSamePlaceholders() {
        val english = infoFile("values")
        assertTrue("there are many", english.size > 200)
        dirs.forEach { (lang, dir) ->
            val strings = infoFile(dir)
            assertEquals("$lang has the same names as English", english.keys, strings.keys)
            english.forEach { (name, text) ->
                assertEquals("$lang placeholders of $name", placeholders(text), placeholders(strings.getValue(name)))
                assertTrue("$lang $name is not empty", strings.getValue(name).isNotBlank())
            }
        }
    }

    @Test
    fun theLongTextsAreTranslatedNotCopied() {
        val english = infoFile("values")
        dirs.filterKeys { it != "en" }.forEach { (lang, dir) ->
            val strings = infoFile(dir)
            english.filterKeys { it.startsWith("info_") && english.getValue(it).length > 60 }.forEach { (name, text) ->
                assertTrue("$lang $name is translated", strings.getValue(name) != text)
            }
        }
    }

    @Test
    fun everySettingOfTheIndexHasAnInfoText() {
        SettingsIndex.entries.forEach { entry ->
            assertTrue("${entry.id} has no infobox text", SettingsInfo.of(entry.id) != null)
        }
    }

    @Test
    fun everyInfoTextIsUsedBySomeSetting() {
        val used = SettingsIndex.entries.mapNotNull { SettingsInfo.of(it.id) }.toSet() + SettingsCategory.entries.map { it.info }
        val resources = context.resources
        val unused = infoFile("values").keys.filter { it.startsWith("info_") && resources.getIdentifier(it, "string", context.packageName) !in used }
        // The weather block and the mini app switch are written once for a whole family: they are reached by the start of the id.
        assertEquals("texts no setting shows", emptyList<String>(), unused)
    }

    @Test
    fun everyCategoryHasItsWordsInEveryLanguage() {
        val keys = SettingsCategory.entries.flatMap { listOf(it.title, it.description, it.words, it.info) }
        dirs.keys.forEach { lang ->
            val c = localized(lang)
            keys.forEach { assertTrue("$lang string $it", c.getString(it).isNotBlank()) }
            SettingsSection.entries.forEach { section ->
                assertTrue("$lang ${section.name}", c.getString(section.title).isNotBlank() && c.getString(section.description).isNotBlank() && c.getString(section.words).isNotBlank())
            }
        }
        // The names of the categories are written in the language, not copied from English (except the names that are the same everywhere).
        val en = localized("en")
        val it = localized("it")
        val same = SettingsCategory.entries.count { c -> en.getString(c.title) == it.getString(c.title) }
        assertTrue("most Italian category names differ from English ($same alike)", same <= 3)
    }

    @Test
    fun theSettingsPagesHaveTheSixLanguagesToo() {
        val newPages = listOf("sec_navbar", "sec_feel", "sec_icons", "sec_privacy", "kw_navbar", "kw_feel", "kw_icons", "kw_privacy", "settings_advanced")
        val english = infoFile("values")
        newPages.forEach { assertTrue("$it is in the info file", it in english) }
    }
}
