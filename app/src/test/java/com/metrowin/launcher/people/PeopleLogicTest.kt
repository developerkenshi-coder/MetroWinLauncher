package com.metrowin.launcher.people

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import java.time.LocalDate
import javax.xml.parsers.DocumentBuilderFactory

/** How the People app lists, finds and compares contacts, its own choices, and that its strings are in all six languages. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PeopleLogicTest {
    private fun person(name: String, vararg numbers: String, key: String = name, emails: List<String> = emptyList(), birthday: String? = null) =
        Person(key, name, false, null, numbers.toList(), emails, "", 1, emptySet(), birthday, PeopleBook.haystackOf(name, numbers.toList(), emails, ""))

    @Test
    fun initialIsALetterNotHalfAnEmoji() {
        assertEquals("E", PeopleBook.initial("Émile"))
        assertEquals("#", PeopleBook.initial("3 Pizza"))
        assertEquals("#", PeopleBook.initial("😀 Fun"))
        assertEquals("Ж", PeopleBook.initial("жанна"))
        assertEquals("Α", PeopleBook.initial("Αλέξης"))
        assertEquals("#", PeopleBook.initial("  "))
    }

    @Test
    fun avatarIsTheFirstLetterWhole() {
        assertEquals("P", PeopleBook.avatar("3 Pizza"))
        assertEquals("#", PeopleBook.avatar("123"))
        assertEquals("𝒜", PeopleBook.avatar("𝒜lice"))
    }

    @Test
    fun otherAlphabetsCome_afterLatinAndBeforeHash() {
        val order = PeopleBook.grouped(listOf("Zoe", "жанна", "3x", "Anna", "Αλέξης").map { person(it) }).map { it.first }
        assertEquals(listOf("A", "Z", "Α", "Ж", "#"), order)
    }

    @Test
    fun aNumberIsTheSameLineHoweverItIsWritten() {
        assertTrue(PeopleBook.sameNumber("+39 333 1234567", "3331234567"))
        assertTrue(PeopleBook.sameNumber("0039 333 1234567", "+39 333 1234567"))
        assertFalse(PeopleBook.sameNumber("3331234567", "3331234568"))
        assertFalse(PeopleBook.sameNumber("", "3331234567"))
    }

    @Test
    fun twoCountriesWithTheSameEndAreNotTheSameLine() {
        assertFalse(PeopleBook.sameNumber("+44 7911 123456", "+39 7911 123456"))
        assertTrue(PeopleBook.sameNumber("+44 7911 123456", "+447911123456"))
    }

    @Test
    fun searchFindsByNameWithoutAccentsAndByNumberWithoutSpaces() {
        val p = person("Émile Zola", "+39 333 123 4567", emails = listOf("e.zola@example.com"))
        assertTrue(PeopleBook.matches(p, "emile"))
        assertTrue(PeopleBook.matches(p, "zola emile"))
        assertTrue(PeopleBook.matches(p, "3331234"))
        assertTrue(PeopleBook.matches(p, "example.com"))
        assertFalse(PeopleBook.matches(p, "anna"))
        assertTrue(PeopleBook.matches(p, "  "))
    }

    @Test
    fun birthdaysAreRead_withAndWithoutYear() {
        assertEquals(5 to 17, PeopleBook.birthdayOf("1980-05-17"))
        assertEquals(5 to 17, PeopleBook.birthdayOf("--05-17"))
        assertNull(PeopleBook.birthdayOf("soon"))
        assertNull(PeopleBook.birthdayOf("1980-13-01"))
        assertEquals(1980, PeopleBook.birthYear("1980-05-17"))
        assertNull(PeopleBook.birthYear("--05-17"))
    }

    @Test
    fun daysToABirthdayCountTodayAsZeroAndKeepTheLeapDay() {
        val day = LocalDate.of(2026, 11, 14)
        assertEquals(0, PeopleBook.daysUntil(11, 14, day))
        assertEquals(1, PeopleBook.daysUntil(11, 15, day))
        assertEquals(365 - 1, PeopleBook.daysUntil(11, 13, day))
        // 2027 has no 29 February: it is kept on the 28th.
        assertEquals(106, PeopleBook.daysUntil(2, 29, day))
    }

    @Test
    fun upcomingBirthdaysAreTheNearestFirstAndWithinTheLimit() {
        val day = LocalDate.of(2026, 11, 14)
        val list = PeopleBook.upcomingBirthdays(listOf(person("Far", birthday = "--06-01"), person("Soon", birthday = "1990-11-20"), person("Today", birthday = "--11-14"), person("None")), day)
        assertEquals(listOf("Today" to 0, "Soon" to 6), list.map { it.first.name to it.second })
    }

    @Test
    fun duplicatesShareANameOrANumber() {
        val a = person("Anna Rossi", "3331234567", key = "a")
        val b = person("anna rossi", key = "b")
        val c = person("Anna R.", "+39 333 1234567", key = "c")
        val d = person("Luca", "3339999999", key = "d")
        val groups = PeopleBook.duplicates(listOf(a, b, c, d)).map { g -> g.map { it.key }.sorted() }
        assertTrue(listOf("a", "b") in groups)
        assertTrue(listOf("a", "c") in groups)
        assertEquals(2, groups.size)
    }

    @Test
    fun theLookupKeyComesOutOfTheAddress() {
        assertEquals("0r12-ABC", PeopleBook.lookupKey("content://com.android.contacts/contacts/lookup/0r12-ABC/12"))
        assertNull(PeopleBook.lookupKey("content://com.android.contacts/contacts/12"))
    }

    @Test
    fun theSnapshotFindsAPersonByAnyWritingOfTheNumber() {
        val snapshot = PeopleSnapshot(listOf(person("Anna", "+39 333 1234567"), person("Bob", "+44 7911 123456")), emptyMap())
        assertEquals("Anna", snapshot.personOf("333 1234567")?.name)
        assertNull(snapshot.personOf("+39 7911 123456"))
    }

    // --- its own choices

    @Test
    fun theOwnChoicesStartPrivateAndAreKept() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val prefs = PeoplePrefs(context)
        assertFalse(prefs.newsText)
        assertFalse(prefs.historyNotifications)
        assertTrue(prefs.birthdays)
        prefs.newsText = true
        prefs.toggleFavourite("k")
        prefs.toggleCallBack("k", 5L)
        val again = PeoplePrefs(context)
        assertTrue(again.newsText)
        assertTrue(again.isFavourite("k"))
        assertEquals(mapOf("k" to 5L), again.callBack)
        again.toggleCallBack("k")
        assertTrue(again.callBack.isEmpty())
    }

    @Test
    fun aPageMovesRightAndTheLastWrapsToTheFirst() {
        val prefs = PeoplePrefs(ApplicationProvider.getApplicationContext<android.app.Application>())
        prefs.order = "012"
        prefs.moveRight(0)
        assertEquals("102", prefs.order)
        prefs.moveRight(2)
        assertEquals("210", prefs.order)
    }

    // --- the strings of the app

    private fun keys(folder: String): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$folder/strings_persone.xml")).documentElement
        return listOf("string", "plurals").flatMap { tag -> (0 until root.getElementsByTagName(tag).length).map { root.getElementsByTagName(tag).item(it) as Element } }
            .associate { it.getAttribute("name") to it.textContent.filter { c -> c == '%' || c.isDigit() || c == '$' || c == 'd' || c == 's' } }
    }

    @Test
    fun everyLanguageHasEveryStringOfTheApp() {
        val english = keys("values")
        assertTrue(english.size > 50)
        listOf("values-it", "values-de", "values-es", "values-fr", "values-pt").forEach { folder ->
            val other = keys(folder)
            assertEquals("$folder has the same names", english.keys, other.keys)
        }
    }
}
