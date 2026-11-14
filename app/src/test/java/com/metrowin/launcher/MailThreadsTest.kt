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

    private fun ided(id: String, subject: String, messageId: String, refs: String = "") =
        MailMessage(id, "a", "n", "n@x", subject, "", 0, false, bodyText = "", messageId = messageId, refs = refs)

    @Test fun repliesMeetTheirOriginalByHeadersAndUnrelatedMailsWithTheSameTitleDoNot() {
        val groups = MailThreads.group(
            listOf(
                ided("4", "Re: Hello", "<d>", "<a> <c>"), ided("3", "Hello", "<c>"), ided("2", "Hello", "<b>"), ided("1", "Hello", "<a>"),
            ),
        )
        // 4 answers 3 and 1 (which is also an ancestor): they meet; 2 has the same title and nothing in common.
        assertEquals(listOf(listOf("4", "3", "1"), listOf("2")), groups.map { g -> g.map { it.id } })
    }

    @Test fun twoRepliesToAMessageThatIsNotInTheListMeet() {
        val groups = MailThreads.group(listOf(ided("2", "Re: x", "<b>", "<a>"), ided("1", "Re: x", "<c>", "<a>")))
        assertEquals(listOf(listOf("2", "1")), groups.map { g -> g.map { it.id } })
    }

    @Test fun answersDoNotPileUpPrefixes() {
        assertEquals("Re: plan", MailThreads.reply("plan"))
        assertEquals("Re: plan", MailThreads.reply("Re: plan"))
        assertEquals("AW: plan", MailThreads.reply("AW: plan"))
        assertEquals("Fwd: plan", MailThreads.forward("plan"))
        assertEquals("Fwd: plan", MailThreads.forward("Fwd: plan"))
    }
}
