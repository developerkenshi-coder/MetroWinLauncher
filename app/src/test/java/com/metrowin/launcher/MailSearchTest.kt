package com.metrowin.launcher

import com.metrowin.launcher.mail.MailClient
import java.util.Properties
import javax.mail.Message
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailSearchTest {
    private fun mail(from: String, subject: String) = MimeMessage(Session.getInstance(Properties())).apply {
        setFrom(InternetAddress(from))
        setRecipients(Message.RecipientType.TO, "me@example.com")
        setSubject(subject)
    }

    @Test fun searchMatchesSubjectAndSenderIgnoringCase() {
        val term = MailClient.searchTerm("invoice")
        assertTrue(term.match(mail("a@example.com", "Your INVOICE is ready")))
        assertTrue(term.match(mail("invoice@shop.com", "Hello")))
        assertFalse(term.match(mail("a@example.com", "Hello")))
    }

    @Test fun searchAlsoMatchesTheRecipient() {
        assertTrue(MailClient.searchTerm("example.com").match(mail("a@other.org", "Hello")))
        assertFalse(MailClient.searchTerm("nobody").match(mail("a@other.org", "Hello")))
    }

    @Test fun aListAskedForTheUnreadNeverShowsWhatAnotherProgramMarkedDeleted() {
        val read = mail("a@x.com", "Hello")
        val unread = mail("a@x.com", "Hello")
        val gone = mail("a@x.com", "Hello").apply { setFlag(javax.mail.Flags.Flag.DELETED, true) }
        val term = MailClient.listTerm("", com.metrowin.launcher.mail.MailFilter.Unseen)
        read.setFlag(javax.mail.Flags.Flag.SEEN, true)
        assertTrue(term.match(unread))
        assertFalse(term.match(read))
        assertFalse(term.match(gone))
    }
}
