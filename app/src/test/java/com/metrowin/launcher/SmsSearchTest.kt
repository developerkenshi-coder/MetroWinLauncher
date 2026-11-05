package com.metrowin.launcher

import com.metrowin.launcher.messages.likeEscape
import com.metrowin.launcher.messages.recipientNumbers
import org.junit.Test
import org.junit.Assert.assertEquals

class SmsSearchTest {
    @Test fun wildcardsStandForThemselves() {
        assertEquals("100\\% sure\\_ok", likeEscape("100% sure_ok"))
        assertEquals("a\\\\b", likeEscape("a\\b"))
        assertEquals("plain", likeEscape("plain"))
    }

    @Test fun groupMembersComeFromTheirIds() {
        val numbers = mapOf(1L to "+39 333", 2L to "", 7L to "+39 347")
        assertEquals(listOf("+39 333", "+39 347"), recipientNumbers("1 2 7 99", numbers))
        assertEquals(emptyList<String>(), recipientNumbers("", numbers))
    }
}
