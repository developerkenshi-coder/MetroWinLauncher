package com.metrowin.launcher

import com.metrowin.launcher.mail.MailParts
import javax.mail.Session
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MailPartsTest {
    private fun message(): MimeMessage {
        val related = MimeMultipart("related").apply {
            addBodyPart(MimeBodyPart().apply { setContent("<p>Ciao <img src=\"cid:logo\"></p>", "text/html; charset=utf-8") })
            addBodyPart(
                MimeBodyPart().apply {
                    dataHandler = javax.activation.DataHandler(ByteArrayDataSource(byteArrayOf(1, 2, 3), "image/png"))
                    setContentID("<logo>")
                    disposition = "inline"
                },
            )
        }
        val mixed = MimeMultipart("mixed").apply {
            addBodyPart(MimeBodyPart().apply { setContent(related) })
            addBodyPart(
                MimeBodyPart().apply {
                    dataHandler = javax.activation.DataHandler(ByteArrayDataSource("contratto".toByteArray(), "application/pdf"))
                    fileName = "contratto.pdf"
                    disposition = "attachment"
                },
            )
        }
        val session = Session.getInstance(java.util.Properties())
        val built = MimeMessage(session).apply { setContent(mixed); saveChanges() }
        // Through the bytes, as it comes from the server: the parts are then read, not built.
        val wire = java.io.ByteArrayOutputStream().also { built.writeTo(it) }.toByteArray()
        return MimeMessage(session, wire.inputStream())
    }

    @Test
    fun theTextThePictureInsideAndTheFileAreToldApart() {
        val found = MailParts.Collected()
        MailParts.walk(message(), found)
        assertTrue(found.html.toString().contains("<p>Ciao"))
        assertEquals(setOf("logo"), found.inline.keys)
        assertTrue(found.inline.getValue("logo").startsWith("data:image/png;base64,"))
        assertEquals(listOf("contratto.pdf"), found.attachments.map { it.name })
        assertEquals("application/pdf", found.attachments.single().mime)
    }

    @Test
    fun theFileAskedForIsTheOneThatComesBack() {
        val found = MailParts.Collected(wantedIndex = 0)
        MailParts.walk(message(), found)
        assertEquals("contratto", String(found.wanted!!.inputStream.readBytes()))
    }

    @Test
    fun aLineOfTheListReadsOnlyTheFirstTextAndNotTheHugePageThatRepeatsIt() {
        val alternative = MimeMultipart("alternative").apply {
            addBodyPart(MimeBodyPart().apply { setContent("Ciao a tutti", "text/plain; charset=utf-8") })
            addBodyPart(MimeBodyPart().apply { setContent("<p>" + "x".repeat(100_000) + "</p>", "text/html; charset=utf-8") })
        }
        val session = Session.getInstance(java.util.Properties())
        val wire = java.io.ByteArrayOutputStream().also { MimeMessage(session).apply { setContent(alternative); saveChanges() }.writeTo(it) }.toByteArray()
        val found = MailParts.Collected(light = true)
        MailParts.walk(MimeMessage(session, wire.inputStream()), found)
        assertEquals("Ciao a tutti", found.plain.toString())
        assertEquals("", found.html.toString())
    }
}
