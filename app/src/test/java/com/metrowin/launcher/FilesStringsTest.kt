package com.metrowin.launcher

import com.metrowin.launcher.files.FilesPrefs
import com.metrowin.launcher.files.Opt
import com.metrowin.launcher.files.Sort
import com.metrowin.launcher.files.SortKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.regex.Pattern

@RunWith(RobolectricTestRunner::class)
class FilesStringsTest {
    private fun strings(dir: String): Map<String, String> {
        val text = File("src/main/res/$dir/strings_file.xml").readText()
        return Regex("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL).findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun placeholders(text: String): List<String> =
        Pattern.compile("%(\\d\\$)?[sd]").matcher(text).let { m -> buildList { while (m.find()) add(m.group().replace(Regex("^%\\d\\$"), "%")) } }.sorted()

    @Test
    fun `every word of the file manager is in the six languages with the same placeholders`() {
        val english = strings("values")
        assertTrue(english.isNotEmpty())
        listOf("values-it", "values-de", "values-es", "values-fr", "values-pt").forEach { dir ->
            val other = strings(dir)
            assertEquals(dir, english.keys, other.keys)
            english.forEach { (name, text) -> assertEquals("$dir $name", placeholders(text), placeholders(other.getValue(name))) }
        }
    }

    @Test
    fun `an apostrophe is always escaped`() {
        listOf("values", "values-it", "values-de", "values-es", "values-fr", "values-pt").forEach { dir ->
            strings(dir).forEach { (name, text) -> assertFalse("$dir $name", Regex("(?<!\\\\)'").containsMatchIn(text)) }
        }
    }

    @Test
    fun `every option has a word and the switches are kept`() {
        val english = strings("values")
        val res = org.robolectric.RuntimeEnvironment.getApplication()
        Opt.entries.forEach { assertTrue(it.name, it.label != 0 && english.keys.any { k -> res.resources.getIdentifier(k, "string", res.packageName) == it.label }) }
        val prefs = FilesPrefs(res)
        Opt.entries.forEach { assertEquals(it.name, it.default, prefs.flag(it)) }
        prefs.setFlag(Opt.Duplicates, true)
        assertTrue(FilesPrefs(res).flag(Opt.Duplicates))
        assertFalse("what touches the battery or reads many files is off until chosen", Opt.RichThumbs.default || Opt.Duplicates.default || Opt.ContentSearch.default)
    }

    @Test
    fun `the order per folder, the tags and the tile size are remembered`() {
        val prefs = FilesPrefs(org.robolectric.RuntimeEnvironment.getApplication())
        prefs.folderSorts = mapOf("/a" to Sort(SortKey.Size, true))
        assertEquals(Sort(SortKey.Size, true), FilesPrefs(org.robolectric.RuntimeEnvironment.getApplication()).folderSorts["/a"])
        prefs.tags = mapOf("/a/x" to 3)
        assertEquals(3, prefs.tags["/a/x"])
        prefs.tileSide = 140
        assertEquals(140, prefs.tileSide)
        prefs.tags = (1..600).associate { "/p$it" to 1 }
        assertEquals(500, prefs.tags.size)
    }
}
