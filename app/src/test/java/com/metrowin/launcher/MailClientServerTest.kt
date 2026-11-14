package com.metrowin.launcher

import com.icegreen.greenmail.junit4.GreenMailRule
import com.icegreen.greenmail.user.GreenMailUser
import com.icegreen.greenmail.util.ServerSetup
import com.metrowin.launcher.mail.MailAccount
import com.metrowin.launcher.mail.MailClient
import com.metrowin.launcher.mail.MailFilter
import com.metrowin.launcher.mail.NoTrashException
import java.util.Properties
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Session
import javax.mail.Store
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * [MailClient] against a mail server that lives in the test (GreenMail): no real account, nothing leaves the machine. What matters most is
 * what the client must never do to a mailbox: expunge what another program marked, delete for good without being told, report a change
 * that did not happen.
 */
class MailClientServerTest {
    @get:Rule val green = GreenMailRule(
        arrayOf(ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_IMAP), ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP)),
    )
    private lateinit var user: GreenMailUser
    private lateinit var client: MailClient
    private val address = "me@localhost"

    @Before fun setUp() {
        user = green.setUser(address, "pw")
        val account = MailAccount("t", address, "Test", "127.0.0.1", green.imap.port, "127.0.0.1", green.smtp.port, requiresAppPassword = false)
        // The server of the test speaks no TLS.
        val plain = Properties().apply {
            put("mail.imap.starttls.enable", "false"); put("mail.imap.starttls.required", "false"); put("mail.imap.ssl.checkserveridentity", "false")
            put("mail.smtp.starttls.enable", "false"); put("mail.smtp.starttls.required", "false"); put("mail.smtp.ssl.checkserveridentity", "false")
        }
        client = MailClient(account, plain) { "pw" }
    }

    @After fun tearDown() = client.close()

    private fun deliver(subject: String, extra: (MimeMessage) -> Unit = {}) {
        // The headers the test sets stay as they are: the server of the test would make a new Message-ID otherwise.
        val m = object : MimeMessage(Session.getInstance(Properties())) {
            override fun updateMessageID() {}
        }.apply {
            setFrom(InternetAddress("a@example.com")); setRecipients(Message.RecipientType.TO, address); setSubject(subject); setText("text of $subject")
            extra(this)
        }
        user.deliver(m)
    }

    /** What the server really holds, seen by a second login that is not the client under test; \Deleted marks included. */
    private fun raw(path: String = "INBOX"): List<Pair<String, Boolean>> {
        val props = Properties().apply { put("mail.imap.starttls.enable", "false") }
        val store: Store = Session.getInstance(props).getStore("imap")
        store.connect("127.0.0.1", green.imap.port, address, "pw")
        try {
            val folder = store.getFolder(path)
            folder.open(Folder.READ_ONLY)
            return folder.messages.map { it.subject to it.isSet(Flags.Flag.DELETED) }.also { folder.close(false) }
        } finally {
            store.close()
        }
    }

    private fun uidOf(subject: String): Long = client.messages("INBOX", 50).first { it.subject == subject }.id.toLong()

    @Test fun readingAMessageDoesNotRemoveWhatAnotherProgramMarkedDeleted() {
        deliver("one"); deliver("two"); deliver("three")
        client.setFlag("INBOX", uidOf("two"), Flags.Flag.DELETED, true)
        val body = client.body("INBOX", uidOf("three"))
        assertEquals("text of three", body.text.trim())
        // The old code closed the folder with expunge: "two" would be gone from the server now.
        assertEquals(listOf("one" to false, "two" to true, "three" to false), raw())
        assertFalse(client.messages("INBOX", 50).any { it.subject == "two" })
    }

    @Test fun readingMarksTheMessageAsRead() {
        deliver("one")
        assertTrue(client.messages("INBOX", 50).single().isUnread)
        client.body("INBOX", uidOf("one"))
        assertFalse(client.messages("INBOX", 50).single().isUnread)
    }

    @Test fun deleteWithoutATrashAsksFirstAndKeepsTheMessage() {
        deliver("one")
        try {
            client.delete("INBOX", uidOf("one"))
            fail("must not delete for good without being told")
        } catch (_: NoTrashException) {
        }
        assertEquals(listOf("one" to false), raw())
        client.delete("INBOX", uidOf("one"), permanent = true)
        assertTrue(raw().none { !it.second })
    }

    @Test fun deleteGoesToTheTrashAndLeavesOtherMarksAlone() {
        green.managers.imapHostManager.createMailbox(user, "Trash")
        deliver("one"); deliver("two"); deliver("three")
        client.setFlag("INBOX", uidOf("one"), Flags.Flag.DELETED, true)
        client.delete("INBOX", uidOf("three"))
        assertEquals(listOf("three"), raw("Trash").map { it.first })
        // "one" was marked by someone else: still there, still marked. "three" left the list.
        assertTrue(raw().contains("one" to true))
        assertEquals(listOf("two"), client.messages("INBOX", 50).map { it.subject })
    }

    @Test fun flagChangeOnAMessageThatIsGoneFailsInsteadOfPretending() {
        deliver("one")
        val uid = uidOf("one")
        client.delete("INBOX", uid, permanent = true)
        try {
            client.setFlag("INBOX", uid, Flags.Flag.FLAGGED, true)
            fail("the caller must learn that nothing changed")
        } catch (_: IllegalStateException) {
        }
    }

    @Test fun theUnreadAndFlaggedListsComeFromTheServer() {
        (1..5).forEach { deliver("m$it") }
        client.body("INBOX", uidOf("m2"))
        client.setFlag("INBOX", uidOf("m4"), Flags.Flag.FLAGGED, true)
        assertEquals(listOf("m5", "m4", "m3", "m1"), client.messages("INBOX", 50, filter = MailFilter.Unseen).map { it.subject })
        assertEquals(listOf("m4"), client.messages("INBOX", 50, filter = MailFilter.Flagged).map { it.subject })
        // A page after the first one of a filtered list.
        assertEquals(listOf("m3", "m1"), client.messages("INBOX", 2, filter = MailFilter.Unseen, skip = 2).map { it.subject })
    }

    @Test fun searchFallsBackToTheSubjectAndSenderWhenTheServerCannotSearchTheText() {
        // The server of the test refuses a search in the text, as some real ones do: the search must not fail for that.
        deliver("plain subject")
        deliver("other")
        assertEquals(listOf("plain subject"), client.messages("INBOX", 50, "plain").map { it.subject })
    }

    @Test fun theHeadersThatBuildConversationsAreFetched() {
        deliver("Re: hi") { it.setHeader("Message-ID", "<b@x>"); it.setHeader("In-Reply-To", "<a@x>"); it.setHeader("References", "<z@x> <a@x>") }
        val m = client.messages("INBOX", 50).single()
        assertEquals("<b@x>", m.messageId)
        assertEquals("<a@x> <z@x> <a@x>", m.refs)
    }

    @Test fun anAnswerCarriesTheHeadersOfWhatItAnswers() {
        client.send(address, "", "Re: hi", "ok", inReplyTo = "<a@x>", references = "<z@x>")
        assertTrue(green.waitForIncomingEmail(5000, 1))
        val got = green.receivedMessages.single()
        assertEquals("<a@x>", got.getHeader("In-Reply-To").single())
        assertEquals("<z@x> <a@x>", got.getHeader("References").single())
    }

    @Test fun foldersTellTheUnreadOfEachAndANewMessageWithoutTrashIsKnown() {
        deliver("one")
        val inbox = client.folders().first { it.path == "INBOX" }
        assertEquals(1, inbox.unread)
        assertFalse(client.hasTrash("INBOX"))
    }
}
