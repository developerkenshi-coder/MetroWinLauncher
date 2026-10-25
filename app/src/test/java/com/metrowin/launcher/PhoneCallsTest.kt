package com.metrowin.launcher

import android.content.Intent
import com.metrowin.launcher.data.PhoneCalls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneCallsTest {
    @Test
    fun aNumberKeepsOnlyWhatTheDialerUnderstands() {
        assertEquals("+393331234567", PhoneCalls.clean("+39 333 123-4567"))
        assertEquals("*21#", PhoneCalls.clean("*21#"))
        assertEquals("0114567890", PhoneCalls.clean("(011) 456 78 90"))
        assertEquals("", PhoneCalls.clean("ext."))
    }

    @Test
    fun withTheLetterOfThePermissionTheCallIsPlacedAtOnce() {
        val intent = PhoneCalls.intent("+39 333 1234567", direct = true)
        assertEquals(Intent.ACTION_CALL, intent.action)
        assertEquals("tel", intent.data!!.scheme)
        assertEquals("+393331234567", intent.data!!.schemeSpecificPart) // the plus is encoded in the address, decoded by the dialer
    }

    @Test
    fun withoutItTheDialerOpensWithTheNumberWrittenIn() {
        val intent = PhoneCalls.intent("333 1234567", direct = false)
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("3331234567", intent.data!!.schemeSpecificPart)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun onlyAnAddressStartingWithTelIsANumber() {
        assertEquals("+3901", PhoneCalls.numberOf("tel:+39 01"))
        assertNull(PhoneCalls.numberOf("https://example.com"))
        assertNull(PhoneCalls.numberOf("content://com.android.contacts/contacts/lookup/1"))
        assertNull(PhoneCalls.numberOf("tel:"))
    }
}
