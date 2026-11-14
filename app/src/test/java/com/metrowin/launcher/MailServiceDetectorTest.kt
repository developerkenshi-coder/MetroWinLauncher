package com.metrowin.launcher

import com.metrowin.launcher.mail.MailAutoconfig
import com.metrowin.launcher.mail.MailServiceDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MailServiceDetectorTest {
    @Test fun yahooIsYahooAndNotEveryDomainWithTheWordInIt() {
        assertEquals("imap.mail.yahoo.com", MailServiceDetector.detect("a@yahoo.it").imapHost)
        assertEquals("imap.mail.yahoo.com", MailServiceDetector.detect("a@ymail.com").imapHost)
        val other = MailServiceDetector.detect("a@notyahoo.example")
        assertEquals("imap.notyahoo.example", other.imapHost)
        assertTrue(other.generic)
    }

    @Test fun anUnknownDomainIsMarkedAsAGuess() {
        assertTrue(MailServiceDetector.detect("a@mycompany.org").generic)
        assertEquals(false, MailServiceDetector.detect("a@gmail.com").generic)
    }

    private val xml = """<clientConfig><emailProvider id="x">
        <incomingServer type="imap"><hostname>mail.%EMAILDOMAIN%</hostname><port>143</port><socketType>STARTTLS</socketType></incomingServer>
        <incomingServer type="pop3"><hostname>pop.x</hostname><port>995</port><socketType>SSL</socketType></incomingServer>
        <outgoingServer type="smtp"><hostname>smtp.x.org</hostname><port>587</port><socketType>STARTTLS</socketType></outgoingServer>
        </emailProvider></clientConfig>"""

    @Test fun autoconfigGivesTheServersOfTheProvider() {
        val found = MailAutoconfig.parse(xml, "x.org")
        assertNotNull(found)
        assertEquals("mail.x.org", found!!.imapHost)
        assertEquals(143, found.imapPort)
        assertEquals("smtp.x.org", found.smtpHost)
        assertEquals(587, found.smtpPort)
    }

    @Test fun autoconfigRefusesServersWithoutTlsAndFilesWithADoctype() {
        assertNull(MailAutoconfig.parse(xml.replace("STARTTLS", "plain"), "x.org"))
        assertNull(MailAutoconfig.parse("<!DOCTYPE a [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>$xml", "x.org"))
    }
}
