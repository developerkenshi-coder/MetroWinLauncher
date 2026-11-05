package com.metrowin.launcher

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Every language has every string of the default ones, no more, and with the same placeholders. */
class TranslationsTest {
    private val res = File("src/main/res")
    private val languages = listOf("de", "es", "fr", "it", "pt")

    private class Resources(val strings: Map<String, String>, val plurals: Set<String>, val arrays: Set<String>)

    private fun read(folder: String): Resources {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/strings.xml")).documentElement
        fun named(tag: String) = (0 until root.getElementsByTagName(tag).length).map { root.getElementsByTagName(tag).item(it) as Element }
            .filter { it.getAttribute("translatable") != "false" }
        return Resources(
            named("string").associate { it.getAttribute("name") to it.textContent },
            named("plurals").map { it.getAttribute("name") }.toSet(),
            named("string-array").map { it.getAttribute("name") }.toSet(),
        )
    }

    /** The placeholders by position: "%1$d" and a plain "%d" in first place are the same one. "%%" is a percent sign, not a placeholder. */
    private fun placeholders(text: String): Map<Int, String> {
        val found = Regex("%(?:(\\d+)\\$)?[-#+0,(]*\\d*(?:\\.\\d+)?([sSdfxXeEgGcb])").findAll(text.replace("%%", "")).toList()
        return found.withIndex().associate { (i, m) -> (m.groupValues[1].toIntOrNull() ?: (i + 1)) to m.groupValues[2] }
    }

    @Test
    fun everyLanguageHasTheSameStringsAsTheDefaultOnes() {
        val base = read("values")
        val problems = mutableListOf<String>()
        for (language in languages) {
            val other = read("values-$language")
            (base.strings.keys - other.strings.keys).forEach { problems += "$language: missing $it" }
            (other.strings.keys - base.strings.keys).forEach { problems += "$language: not in the default strings: $it" }
            (base.plurals.symmetricDifference(other.plurals)).forEach { problems += "$language: plurals differ: $it" }
            (base.arrays.symmetricDifference(other.arrays)).forEach { problems += "$language: arrays differ: $it" }
            base.strings.forEach { (key, text) ->
                val translated = other.strings[key] ?: return@forEach
                if (placeholders(text) != placeholders(translated)) problems += "$language: $key has other placeholders ($text / $translated)"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private fun <T> Set<T>.symmetricDifference(other: Set<T>) = (this - other) + (other - this)
}
