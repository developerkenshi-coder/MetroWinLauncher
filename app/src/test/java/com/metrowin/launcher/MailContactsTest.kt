package com.metrowin.launcher

import com.metrowin.launcher.mail.MailAddress
import com.metrowin.launcher.mail.MailContacts
import org.junit.Assert.assertEquals
import org.junit.Test

class MailContactsTest {
    @Test fun theWordBeingTypedIsTheLastOne() {
        assertEquals("ma", MailContacts.lastPart("a@x.com, b@y.org,  ma"))
        assertEquals("ma", MailContacts.lastPart("ma"))
    }

    @Test fun aChosenAddressReplacesOnlyTheWordBeingTyped() {
        val mario = MailAddress("Mario Rossi", "mario@x.com")
        assertEquals("a@x.com, Mario Rossi <mario@x.com>, ", MailContacts.complete("a@x.com, ma", mario))
        assertEquals("mario@x.com, ", MailContacts.complete("ma", MailAddress("", "mario@x.com")))
    }
}
