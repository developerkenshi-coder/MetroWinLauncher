package com.metrowin.launcher

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The strings of the To do app in strings_todo.xml: the six languages have the same ones, with the same placeholders, and none is in strings.xml too. */
class TodoStringsTest {
    private val res = File("src/main/res")

    private fun read(file: File): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val nodes = root.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }

    private fun placeholders(text: String) = Regex("%(?:\\d+\\$)?[sd]").findAll(text).map { it.value }.toList()

    @Test
    fun `every language has the same strings as the default ones and none is doubled`() {
        val base = read(File(res, "values/strings_todo.xml"))
        val main = read(File(res, "values/strings.xml")).keys
        val problems = mutableListOf<String>()
        base.keys.filter { it in main }.forEach { problems += "also in strings.xml: $it" }
        for (language in listOf("it", "de", "es", "fr", "pt")) {
            val other = read(File(res, "values-$language/strings_todo.xml"))
            (base.keys - other.keys).forEach { problems += "$language: missing $it" }
            (other.keys - base.keys).forEach { problems += "$language: not in the default: $it" }
            base.forEach { (k, v) -> other[k]?.let { if (placeholders(v) != placeholders(it)) problems += "$language: $k placeholders" } }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
