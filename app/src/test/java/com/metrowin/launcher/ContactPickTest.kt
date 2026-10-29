package com.metrowin.launcher

import com.metrowin.launcher.data.ContactMatch
import com.metrowin.launcher.data.ContactPick
import com.metrowin.launcher.data.ContactSearch
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactPickTest {
    private fun c(name: String) = ContactMatch(name, "lookup:$name")

    private fun one(query: String, vararg names: String): String? =
        (ContactSearch.pick(query, names.map(::c)) as? ContactPick.One)?.contact?.name

    @Test
    fun theExactNameWinsOverLongerOnes() {
        assertEquals("Mario", one("mario", "Annamaria Mario Rossi", "Mario", "Mario Bianchi"))
        assertEquals("Mario Rossi", one("Mario Rossi", "Mario Bianchi", "Mario Rossi"))
    }

    @Test
    fun accentsAndCapitalsDoNotMatter() {
        assertEquals("Niccolò Verdi", one("niccolo", "Niccolò Verdi"))
        assertEquals("Zoë Bianchi", one("ZOE bianchi", "Zoë Bianchi"))
    }

    @Test
    fun aContactFoundOnlyByItsNumberOrAMisheardNameIsNotTaken() {
        // The provider lists a contact whose number has the digits, or whose e-mail has the word.
        assertEquals(ContactPick.None, ContactSearch.pick("mario", listOf(c("Pizzeria Da Luigi"))))
        assertEquals(ContactPick.None, ContactSearch.pick("maria", listOf(c("Mario Rossi"))))
        assertEquals(ContactPick.None, ContactSearch.pick("", listOf(c("Mario Rossi"))))
    }

    @Test
    fun aNamePrefixIsTakenWhenNothingIsCloser() {
        assertEquals("Gianluca Neri", one("gian", "Gianluca Neri"))
    }

    @Test
    fun severalThatFitEquallyAreAskedAboutAndNotGuessed() {
        val pick = ContactSearch.pick("mario", listOf(c("Mario Rossi"), c("Mario Bianchi"), c("Mario Rossi")))
        assertEquals(listOf("Mario Rossi", "Mario Bianchi"), (pick as ContactPick.Ambiguous).contacts.map { it.name })
    }
}
