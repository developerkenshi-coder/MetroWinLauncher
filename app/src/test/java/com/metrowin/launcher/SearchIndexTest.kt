package com.metrowin.launcher

import com.metrowin.launcher.data.SearchEntry
import com.metrowin.launcher.data.SearchIndex
import com.metrowin.launcher.data.SearchText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIndexTest {
    private fun entry(title: String, vararg keywords: String, boost: Int = 0) = SearchEntry(title, title, keywords.toList(), boost)

    private fun indexOf(vararg titles: String) = SearchIndex(titles.map { entry(it) })

    private val apps = indexOf(
        "Calculator", "Calendar", "Camera", "Google Calendar", "Google Maps", "Google Docs", "Gmail", "Notes",
        "Scalable", "YouTube Music", "YouTube", "Wi-Fi", "Fotocàmera",
    )

    // --- how well a word matches

    @Test
    fun theSameWordComesBeforeOneThatStartsWithItAndThatBeforeOneThatHasItInside() {
        val index = indexOf("Scalable", "Calendar Pro", "Cal", "Local")
        assertEquals(listOf("Cal", "Calendar Pro", "Scalable", "Local"), index.search("cal"))
    }

    @Test
    fun aNameThatStartsWithTheQueryComesBeforeAWordInTheMiddle() {
        assertEquals(listOf("Calculator", "Calendar", "Google Calendar", "Scalable"), apps.search("cal"))
    }

    @Test
    fun caseAccentsAndPunctuationDoNotMatter() {
        assertEquals(listOf("Fotocàmera"), apps.search("FOTOCAMERA"))
        assertEquals(listOf("Camera", "Fotocàmera"), apps.search("càm"))
        assertEquals(listOf("Wi-Fi"), apps.search("wi fi"))
        assertEquals(listOf("Wi-Fi"), apps.search("wi-fi"))
    }

    @Test
    fun amongEqualMatchesTheOrderOfTheListIsKept() {
        assertEquals(listOf("Calculator", "Calendar", "Camera"), apps.search("ca").take(3))
    }

    @Test
    fun nothingTypedAndNothingFoundGiveNothing() {
        assertTrue(apps.search("").isEmpty())
        assertTrue(apps.search("   ").isEmpty())
        assertTrue(apps.search("---").isEmpty())
        assertTrue(apps.search("zzzzzz").isEmpty())
    }

    // --- several words

    @Test
    fun everyWordTypedHasToBeFoundInAnyOrder() {
        assertEquals(listOf("Google Maps"), apps.search("google ma"))
        assertEquals(listOf("Google Maps"), apps.search("maps google"))
        assertEquals(listOf("Google Calendar", "Google Maps", "Google Docs"), apps.search("google").filter { it.startsWith("Google") })
        assertTrue(apps.search("google zebra").isEmpty())
    }

    @Test
    fun theWholeNameTypedComesFirst() {
        val index = indexOf("YouTube", "YouTube Music", "Music for YouTube")
        assertEquals("YouTube", index.search("youtube").first())
        assertEquals("YouTube Music", index.search("youtube music").first())
    }

    // --- the first letters of a name

    @Test
    fun theFirstLettersOfTheWordsFindAName() {
        // Gmail starts with "gm", so it is first; Google Maps is gm too, and Google Docs and Google Calendar are not.
        assertEquals(listOf("Gmail", "Google Maps"), apps.search("gm"))
    }

    @Test
    fun aCapitalLetterInTheMiddleStartsAPartForTheFirstLetters() {
        // "YouTube" is You, Tube: "yt"; "YouTube Music" is You, Tube, Music: "ytm".
        assertEquals(listOf("YouTube Music"), apps.search("ytm"))
        assertTrue(apps.search("yt").containsAll(listOf("YouTube", "YouTube Music")))
    }

    @Test
    fun aNameOfOneWordHasNoFirstLetters() {
        assertTrue(indexOf("Notes").search("nn").isEmpty())
    }

    // --- keywords

    @Test
    fun aKeywordFindsAnEntryButAfterTheNames() {
        val index = SearchIndex(
            listOf(
                entry("Photos", "gallery", "foto"),
                entry("Gallery Pro"),
                entry("Camera", "foto"),
            ),
        )
        assertEquals(listOf("Gallery Pro", "Photos"), index.search("gallery"))
        assertEquals(listOf("Photos", "Camera"), index.search("foto"))
    }

    @Test
    fun aKeywordIsFoundByItsStartOnlyFromTheLettersAskedFor() {
        val items = listOf(entry("Phone", "call", "calls"), entry("Clock", "cal"), entry("Calendar"))
        val strict = SearchIndex(items, keywordPrefixFrom = 4)
        // Three letters are the start of a name, not of "call"; but a keyword that is exactly those letters is found.
        assertEquals(listOf("Calendar", "Clock"), strict.search("cal"))
        assertEquals("four letters are enough", listOf("Phone"), strict.search("call"))
        val loose = SearchIndex(items)
        assertEquals(listOf("Calendar", "Clock", "Phone"), loose.search("cal"))
    }

    // --- mistakes

    @Test
    fun aMistakeIsForgivenOnlyWhenNothingElseMatches() {
        assertEquals(listOf("Calendar", "Google Calendar"), apps.search("calnedar"))
        assertEquals("Camera", apps.search("camra").first())
        // "cal" has direct matches, so "calx"-like near matches do not join them.
        assertEquals(listOf("Calculator", "Calendar", "Google Calendar", "Scalable"), apps.search("cal"))
    }

    @Test
    fun aShortWordIsNeverGuessed() {
        assertTrue(apps.search("cxl").isEmpty())
    }

    @Test
    fun theTypedStartOfALongWordIsForgivenToo() {
        // "calne" is "calen" with two letters swapped: the start of "calendar" is one slip away.
        assertTrue("Calendar" in apps.search("calne"))
    }

    // --- boost

    @Test
    fun theAppsOpenedMostComeFirstAmongEqualMatches() {
        val index = SearchIndex(listOf(entry("Camera"), entry("Calendar", boost = 5), entry("Calculator", boost = 2)))
        assertEquals(listOf("Calendar", "Calculator", "Camera"), index.search("ca"))
    }

    @Test
    fun aBoostNeverLiftsAnEntryOverABetterMatch() {
        val index = SearchIndex(listOf(entry("Scalable", boost = 9), entry("Calendar")))
        assertEquals(listOf("Calendar", "Scalable"), index.search("cal"))
    }

    @Test
    fun aBoostOutOfRangeIsHeld() {
        val index = SearchIndex(listOf(entry("Scalable", boost = 1_000), entry("Calendar", boost = -5)))
        assertEquals(listOf("Calendar", "Scalable"), index.search("cal"))
    }

    // --- scale

    @Test
    fun aBigListIsSearchedFast() {
        val syllables = listOf("ka", "lo", "mi", "ra", "tu", "se", "no", "vi", "pa", "de")
        val titles = (0 until 3_000).map { i -> "${syllables[i % 10]}${syllables[(i / 10) % 10]}${syllables[(i / 100) % 10]}${syllables[(i / 1000) % 10]} app" }
        val index = SearchIndex(titles.map { entry(it) })
        val start = System.nanoTime()
        repeat(200) { index.search("kal") ; index.search("ka lo") ; index.search("kolotu") }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("600 searches of 3000 names took ${ms}ms", ms < 5_000)
    }

    // --- the pieces

    @Test
    fun wordsAreLettersAndDigitsFolded() {
        assertEquals(listOf("cafe", "wi", "fi", "2024"), SearchText.words("  Café, Wi-Fi 2024!! "))
        assertTrue(SearchText.words("...").isEmpty())
    }

    @Test
    fun partsStartAtACapitalLetter() {
        assertEquals(listOf("You", "Tube", "Music"), SearchText.parts("YouTube Music"))
        assertEquals(listOf("Wi", "Fi"), SearchText.parts("Wi-Fi"))
        assertEquals(listOf("Whats", "App"), SearchText.parts("WhatsApp"))
        assertEquals(listOf("ABC"), SearchText.parts("ABC"))
    }

    @Test
    fun initialsNeedTwoParts() {
        assertEquals("gm", SearchText.initials(listOf("Google", "Maps")))
        assertEquals("ea", SearchText.initials(listOf("Élan", "Alpha")))
        assertNull(SearchText.initials(listOf("Maps")))
    }

    @Test
    fun theDistanceCountsAnInsertionADeletionASubstitutionAndASwap() {
        assertEquals(0, SearchText.distance("maps", "maps", 2))
        assertEquals(1, SearchText.distance("maps", "map", 2))
        assertEquals(1, SearchText.distance("maps", "mapps", 2))
        assertEquals(1, SearchText.distance("maps", "mops", 2))
        assertEquals("a swap is one", 1, SearchText.distance("maps", "mpas", 2))
        assertEquals(3, SearchText.distance("maps", "xyzs", 2))
        assertTrue(SearchText.within("calendar", "calnedar", 1))
        assertTrue(!SearchText.within("calendar", "kalenda", 1))
    }
}
