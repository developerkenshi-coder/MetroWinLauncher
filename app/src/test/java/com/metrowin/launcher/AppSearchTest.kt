package com.metrowin.launcher

import com.metrowin.launcher.data.AppSearch
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSearchTest {
    private fun apps(vararg labels: String) = labels.map { AppItem("app:$it", it, Launch.Action("x")) }
    private fun labels(items: List<AppItem>) = items.map { it.label }

    private val list = apps("Calculator", "Calendar", "Camera", "Google Calendar", "Notes", "Scalable", "Fotocàmera")

    @Test
    fun anEmptyQueryShowsEverything() {
        assertEquals(list, AppSearch.filter(list, "  "))
    }

    @Test
    fun namesThatStartWithTheQueryComeFirst() {
        // "Calendar" starts with it, "Google Calendar" has a word that does, "Scalable" only contains it.
        assertEquals(listOf("Calculator", "Calendar", "Google Calendar", "Scalable"), labels(AppSearch.filter(list, "cal")))
    }

    @Test
    fun caseAndAccentsDoNotMatter() {
        assertEquals(listOf("Fotocàmera"), labels(AppSearch.filter(list, "FOTOCAMERA")))
        // "càm" is "cam": Camera starts with it, Fotocàmera only contains it.
        assertEquals(listOf("Camera", "Fotocàmera"), labels(AppSearch.filter(list, "càm")))
    }

    @Test
    fun nothingMatchesNothing() {
        assertEquals(emptyList<AppItem>(), AppSearch.filter(list, "zzz"))
    }

    @Test
    fun theOrderWithinARankStaysAlphabetic() {
        assertEquals(listOf("Calculator", "Calendar", "Camera"), labels(AppSearch.filter(list, "ca")).take(3))
    }
}
