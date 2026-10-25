package com.metrowin.launcher

import com.metrowin.launcher.data.AppSearch
import com.metrowin.launcher.data.SearchAliases
import com.metrowin.launcher.data.TileSearch
import com.metrowin.launcher.model.AppCategory
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.TileData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The index of the app list (names, what an app is for, its package, how often it is opened) and the search of the tiles of Start. */
class AppIndexTest {
    private fun app(
        label: String, packageName: String? = null, glyph: Glyph? = null, category: AppCategory? = null,
        work: Boolean = false, private: Boolean = false, id: String = "app:$label",
    ) = AppItem(id, label, Launch.Action("x"), glyph = glyph, packageName = packageName, category = category, isWork = work, isPrivate = private)

    private fun labels(items: List<AppItem>) = items.map { it.label }

    private val list = listOf(
        app("Chrome", "com.android.chrome"),
        app("Internet", "com.android.chrome", Glyph.Browser, id = "stock:ie"),
        app("Posta", "com.google.android.gm", Glyph.Mail),
        app("Mail", "com.microsoft.office.outlook", Glyph.Mail),
        app("Mappe", "com.google.android.apps.maps", Glyph.Maps),
        app("Google Maps Go", "com.google.android.apps.mapslite"),
        app("Telefono", "com.google.android.dialer", Glyph.Phone),
        app("Calendario", "com.google.android.calendar", Glyph.Calendar),
        app("Subway Surfers", "com.kiloo.subwaysurf", category = AppCategory.Game),
        app("Slack", "com.slack", work = true),
    )

    private fun search(query: String, usage: Map<String, Int> = emptyMap()) = labels(
        AppSearch.index(
            list, usage,
            categoryLabel = { if (it == AppCategory.Game) "giochi" else null },
            profileWords = AppSearch.ProfileWords("lavoro", "privato"),
        ).search(query),
    )

    // --- what an app is for

    @Test
    fun anAppIsFoundByWhatItIsForInBothLanguages() {
        assertTrue("Posta" in search("email"))
        assertTrue("Mail" in search("email"))
        assertEquals("a stock app answers to the word of the other language", listOf("Mappe"), search("navigation"))
        assertTrue("Telefono" in search("chiamate") && "Telefono" in search("dialer"))
        assertEquals(listOf("Calendario"), search("appuntamenti"))
    }

    @Test
    fun theNameComesBeforeTheWordsThatSayWhatItIsFor() {
        // "Mail" is a name; "Posta" is only a mail app.
        assertEquals(listOf("Mail", "Posta"), search("mail"))
    }

    // --- the package

    @Test
    fun thePackageWordsFindAppsWhoseNameDoesNotSayIt() {
        assertTrue("the real name of an app a stock app stands for", "Internet" in search("chrome"))
        assertTrue("Chrome" in search("chrome"))
        val google = search("google")
        assertTrue(google.containsAll(listOf("Posta", "Mappe", "Telefono", "Calendario", "Google Maps Go")))
        assertFalse("Slack" in google)
    }

    @Test
    fun thePackageIsNotCutIntoNoise() {
        assertEquals(listOf("google", "photos"), AppSearch.packageWords("com.google.android.apps.photos"))
        assertEquals(emptyList<String>(), AppSearch.packageWords(null))
        assertEquals(listOf("kiloo", "subwaysurf"), AppSearch.packageWords("com.kiloo.subwaysurf"))
    }

    // --- category and profile

    @Test
    fun theCategoryAndTheProfileAreWordsToo() {
        assertEquals(listOf("Subway Surfers"), search("giochi"))
        assertEquals(listOf("Slack"), search("lavoro"))
    }

    // --- first letters, many words, mistakes

    @Test
    fun thefirstLettersAndSeveralWordsFindTheApp() {
        assertEquals("Google Maps Go", search("gmg").first())
        assertEquals(listOf("Google Maps Go"), search("maps go"))
        assertTrue("Subway Surfers" in search("ss"))
    }

    @Test
    fun aMistakeStillFindsTheApp() {
        assertEquals(listOf("Calendario"), search("calenadrio"))
    }

    @Test
    fun threeLettersAreTheStartOfANameAndNotOfAWordForAnApp() {
        // "cal" is Calendario; "call" (the English word for the phone) is not the start of what "cal" brings in.
        assertEquals(listOf("Calendario"), search("cal"))
        assertEquals(listOf("Telefono"), search("call"))
    }

    // --- how often

    @Test
    fun theAppsOpenedMostComeFirstAmongTheSameMatches() {
        val plain = search("m")
        val boosted = search("m", mapOf("app:Mappe" to 12))
        assertEquals("Mappe", boosted.first())
        assertEquals("the apps are the same, only the order moves", plain.toSet(), boosted.toSet())
    }

    @Test
    fun theBoostGrowsSlowlyAndStopsAtNine() {
        assertEquals(0, AppSearch.boost(0))
        assertEquals(0, AppSearch.boost(-3))
        assertTrue(AppSearch.boost(1) in 1..3)
        assertTrue(AppSearch.boost(3) >= AppSearch.boost(1))
        assertEquals(9, AppSearch.boost(10_000))
    }

    @Test
    fun anAppOpenedAThousandTimesDoesNotBeatTheRightName() {
        // "ma" starts the names Mail and Mappe; Google Maps Go only has a word that starts with it.
        assertEquals(listOf("Mail", "Mappe"), search("ma", mapOf("app:Google Maps Go" to 1_000)).take(2))
        // And for a word that a name has and another app only has as a keyword.
        assertEquals("Mail", search("mail", mapOf("app:Posta" to 1_000)).first())
    }

    // --- the old way of calling it

    @Test
    fun filterKeepsTheListForAnEmptyQueryAndTheOrderForTheRest() {
        assertEquals(list, AppSearch.filter(list, "  "))
        assertEquals("Calendario", labels(AppSearch.filter(list, "calen")).first())
    }

    @Test
    fun everyGlyphHasWordsInBothLanguagesAndTheNeutralOnesAreEmpty() {
        assertTrue(SearchAliases.forGlyph(null).isEmpty())
        Glyph.entries.forEach { assertTrue("$it has words", SearchAliases.forGlyph(it).isNotEmpty()) }
    }

    // --- Start

    private fun tile(label: String, note: String? = null, data: TileData? = null, targetDay: Long = 0L, launch: Launch = Launch.Action("x")) =
        AppItem("shortcut:$label", label, launch, pinOnly = true, note = note, data = data, targetDay = targetDay)

    @Test
    fun onlyTheTilesThatOpenSomethingAreSearched() {
        val tiles = listOf(
            tile("Spesa", note = "latte\npane"),
            tile("Cose da fare", data = TileData.Checklist("[ ] chiamare Anna\n[x] pagare bolletta")),
            tile("Vacanze", targetDay = 20_000L),
            tile("GitHub", launch = Launch.Link("https://github.com/x")),
            tile("Contatore", data = TileData.Counter(3)),
            tile("Londra", data = TileData.WorldClock("Europe/London")),
            tile("Luna", data = TileData.Moon),
            tile("Anna", launch = Launch.Link("content://com.android.contacts/contacts/lookup/1")),
            tile("Wi-Fi", launch = Launch.Action("android.settings.WIFI_SETTINGS")),
            tile("Widget", launch = Launch.Widget(3)),
            app("Calendario"),
        )
        assertEquals(listOf("Spesa", "Cose da fare", "Vacanze", "GitHub"), labels(TileSearch.searchable(tiles)))
    }

    @Test
    fun aNoteIsFoundByWhatIsWrittenInIt() {
        val tiles = listOf(tile("Spesa", note = "latte\npane\nuova"), tile("Idee", note = "comprare una chitarra"))
        val index = TileSearch.index(tiles)
        assertEquals(listOf("Spesa"), labels(index.search("pane")))
        assertEquals(listOf("Idee"), labels(index.search("chitarra")))
        assertEquals("the word for a note finds them all", 2, index.search("nota").size)
    }

    @Test
    fun aListIsFoundByItsItemsAndTheDoneOnesCountToo() {
        val tiles = listOf(tile("Cose da fare", data = TileData.Checklist("[ ] chiamare Anna\n[x] pagare bolletta")))
        val index = TileSearch.index(tiles)
        assertEquals(1, index.search("bolletta").size)
        assertEquals(1, index.search("lista").size)
    }

    @Test
    fun aSiteIsFoundByItsAddressAndACountdownByItsName() {
        val tiles = listOf(tile("Codice", launch = Launch.Link("https://www.github.com/x/y")), tile("Vacanze", targetDay = 20_000L))
        val index = TileSearch.index(tiles)
        assertEquals(listOf("Codice"), labels(index.search("github")))
        assertEquals(listOf("Vacanze"), labels(index.search("vacan")))
        assertEquals(listOf("Vacanze"), labels(index.search("giorni")))
    }

    @Test
    fun theSnippetIsTheLineThatMatched() {
        val note = tile("Spesa", note = "latte\npane integrale\nuova")
        assertEquals("pane integrale", TileSearch.snippet(note, "pane"))
        assertEquals("the first line when only the name matched", "latte", TileSearch.snippet(note, "spesa"))
        val list = tile("Cose", data = TileData.Checklist("[ ] chiamare Anna\n[x] pagare bolletta"))
        assertEquals("pagare bolletta", TileSearch.snippet(list, "bolletta"))
        assertEquals(null, TileSearch.snippet(tile("Sito", launch = Launch.Link("https://a.it")), "sito"))
    }

    @Test
    fun aLongLineIsCut() {
        val note = tile("Lungo", note = "parola ".repeat(30))
        val snippet = TileSearch.snippet(note, "parola")!!
        assertEquals(TileSearch.MAX_SNIPPET, snippet.length)
        assertTrue(snippet.endsWith("…"))
    }
}
