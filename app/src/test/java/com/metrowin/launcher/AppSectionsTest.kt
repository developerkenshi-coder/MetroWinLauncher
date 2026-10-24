package com.metrowin.launcher

import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppSectionsTest {
    private fun app(label: String) = AppItem(id = "app:$label", label = label, launch = Launch.Action("test"))

    @Test
    fun accentsAndCaseDoNotChangeTheGroup() {
        assertEquals("E", AppSections.keyOf("Élan"))
        assertEquals("E", AppSections.keyOf("elan"))
        assertEquals("A", AppSections.keyOf("  Alarms"))
    }

    @Test
    fun numbersAndSymbolsGoUnderHash() {
        assertEquals("#", AppSections.keyOf("1Password"))
        assertEquals("#", AppSections.keyOf("(Secret)"))
        assertEquals("#", AppSections.keyOf(""))
    }

    @Test
    fun foldMakesLabelsComparableAndSearchable() {
        // The rule that merges a stock app with the Android app of the same name ("Calculator").
        assertEquals(AppSections.fold("Calculator"), AppSections.fold("  calculator "))
        assertEquals(AppSections.fold("Fotocamera"), AppSections.fold("FOTOCÁMERA"))
        assertEquals("elan", AppSections.fold("Élan"))
    }

    @Test
    fun otherScriptsGetTheirOwnGroup() {
        assertEquals("Я", AppSections.keyOf("яндекс"))
    }

    @Test
    fun sortIsAlphabeticWithSymbolsFirstAndOtherScriptsLast() {
        val sorted = AppSections.sort(
            listOf("Zeta", "Яндекс", "beta", "Alpha", "1Password", "éclair", "Calculator").map(::app),
            Locale.ENGLISH,
        )
        assertEquals(
            listOf("1Password", "Alpha", "beta", "Calculator", "éclair", "Zeta", "Яндекс"),
            sorted.map { it.label },
        )
    }

    @Test
    fun groupsKeepTheSortedOrder() {
        val sections = AppSections.group(
            AppSections.sort(listOf("Phone", "People", "Alarms", "Photos", "7zip").map(::app), Locale.ENGLISH),
        )
        assertEquals(listOf("#", "A", "P"), sections.map { it.key })
        assertEquals(listOf("People", "Phone", "Photos"), sections.last().items.map { it.label })
    }

    @Test
    fun jumpListAlwaysShowsEveryLetter() {
        val sections = AppSections.group(AppSections.sort(listOf(app("Phone"), app("Яндекс")), Locale.ENGLISH))
        val keys = AppSections.jumpKeys(sections)
        assertEquals("#", keys.first())
        assertEquals(27, keys.indexOf("Я"))
        assertEquals(28, keys.size)
    }
}
