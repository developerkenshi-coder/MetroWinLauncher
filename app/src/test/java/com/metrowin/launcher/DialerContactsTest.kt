package com.metrowin.launcher

import com.metrowin.launcher.data.ContactSource
import com.metrowin.launcher.data.DialerContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DialerContactsTest {
    @Test
    fun theAccountSaysWhereAContactIsKept() {
        assertEquals(ContactSource.Google, ContactSource.of("com.google"))
        assertEquals(ContactSource.Sim, ContactSource.of("com.android.contacts.sim"))
        assertEquals(ContactSource.Sim, ContactSource.of("vnd.sec.contact.sim"))
        assertEquals(ContactSource.Phone, ContactSource.of(null))
        assertEquals(ContactSource.Phone, ContactSource.of(""))
        assertEquals(ContactSource.Phone, ContactSource.of("vnd.sec.contact.phone"))
        assertEquals(ContactSource.Phone, ContactSource.of("com.android.localphone"))
        assertEquals(ContactSource.Other, ContactSource.of("com.whatsapp"))
        assertEquals(ContactSource.Other, ContactSource.of("com.microsoft.office.outlook.USER_ACCOUNT"))
    }

    @Test
    fun aContactShowsWhenAnyOfItsPlacesIsChosen() {
        val both = DialerContact("Anna", "1", "u", ContactSource.Google.bit or ContactSource.Sim.bit)
        assertTrue(both.shownIn(ContactSource.Sim.bit))
        assertTrue(both.shownIn(ContactSource.ALL))
        assertFalse(both.shownIn(ContactSource.Phone.bit or ContactSource.Other.bit))
        assertFalse(both.shownIn(0))
    }

    @Test
    fun theMaskTogglesOnePlaceAtATime() {
        var mask = ContactSource.ALL
        mask = ContactSource.toggle(mask, ContactSource.Sim)
        assertFalse(ContactSource.has(mask, ContactSource.Sim))
        assertTrue(ContactSource.has(mask, ContactSource.Google))
        assertEquals(ContactSource.ALL, ContactSource.toggle(mask, ContactSource.Sim))
    }
}
