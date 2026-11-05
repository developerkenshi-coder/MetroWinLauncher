package com.metrowin.launcher

import com.metrowin.launcher.mail.MailMessage
import com.metrowin.launcher.mail.MailThreads
import org.junit.Assert.assertEquals
import org.junit.Test

class MailThreadsTest {
    private fun mail(id: String, subject: String) = MailMessage(id, "a", "n", "n@x", subject, "", 0, false, bodyText = "")

    @Test fun prefixesAndCaseAreTakenOff() {
        assertEquals("lunch friday", MailThreads.normalize("  RE: Re[2]: Fwd:  Lunch   Friday "))
        assertEquals("report", MailThreads.normalize("R: report"))
        assertEquals("restaurant", MailThreads.normalize("restaurant"))
    }

    @Test fun conversationsKeepTheOrderOfTheirFirstMessage() {
        val groups = MailThreads.group(listOf(mail("5", "Re: Plan"), mail("4", "Other"), mail("3", "plan"), mail("2", ""), mail("1", "")))
        assertEquals(listOf(listOf("5", "3"), listOf("4"), listOf("2"), listOf("1")), groups.map { g -> g.map { it.id } })
    }
}
