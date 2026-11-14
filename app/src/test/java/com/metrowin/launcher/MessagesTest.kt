package com.metrowin.launcher

import com.metrowin.launcher.messages.DraftKeys
import com.metrowin.launcher.messages.MessagesLogic
import com.metrowin.launcher.messages.MmsPdu
import com.metrowin.launcher.messages.Sms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagesTest {
    private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun z(s: String) = s.toByteArray() + 0

    private fun notification(): ByteArray {
        val from = byteArrayOf(0x80.toByte()) + z("+39333/TYPE=PLMN")
        return b(0x8C, 0x82) + b(0x98) + z("T1") + b(0x8D, 0x92) + b(0x89, from.size) + from + b(0x8E, 0x02, 0x04, 0x00) + b(0x83) + z("http://mmsc/x") + b(0x96) + z("hi")
    }

    @Test fun notificationGivesWhereToFetch() {
        val n = MmsPdu.notification(notification())!!
        assertEquals("T1", n.transactionId)
        assertEquals("http://mmsc/x", n.location)
        assertEquals("+39333", n.from)
        assertEquals(1024L, n.size)
        assertEquals("hi", n.subject)
    }

    @Test fun brokenNotificationIsNullNeverHalf() {
        val pdu = notification()
        assertNull(MmsPdu.notification(pdu.copyOf(pdu.size - 12)))
        assertNull(MmsPdu.notification(b(0x8C, 0x84) + b(0x98) + z("T")))
        assertNull(MmsPdu.notification(ByteArray(0)))
    }

    @Test fun retrievedMessageGivesItsParts() {
        val from = byteArrayOf(0x80.toByte()) + z("+39333/TYPE=PLMN")
        val head = b(0x8C, 0x84) + b(0x98) + z("T1") + b(0x8D, 0x90) + b(0x89, from.size) + from + b(0x97) + z("+39444/TYPE=PLMN") + b(0x84, 0xB3)
        val text = b(0x03, 0x83, 0x81, 0xEA) // text/plain; charset=utf-8
        val image = b(0x9E)
        val body = b(2) + b(text.size, 5) + text + "hello".toByteArray() + b(image.size, 3) + image + b(1, 2, 3)
        val m = MmsPdu.retrieved(head + body)!!
        assertEquals("+39333", m.from)
        assertEquals(listOf("+39444"), m.to)
        assertEquals(listOf("text/plain", "image/jpeg"), m.parts.map { it.type })
        assertEquals(106, m.parts[0].charset)
        assertEquals("hello", String(m.parts[0].data))
        assertEquals(listOf<Byte>(1, 2, 3), m.parts[1].data.toList())
    }

    @Test fun retrievedWithShortBodyIsNull() {
        val head = b(0x8C, 0x84, 0x84, 0xB3)
        assertNull(MmsPdu.retrieved(head + b(2) + b(1, 99) + b(0x83) + b(1)))
    }

    @Test fun codeNeedsAWordAndDigits() {
        assertEquals("123456", MessagesLogic.otpCode("Il tuo codice è 123456, non condividerlo"))
        assertNull(MessagesLogic.otpCode("Ci vediamo alle 1830"))
        assertNull(MessagesLogic.otpCode("code " + "x".repeat(300) + " 1234"))
    }

    @Test fun linksStopBeforeTheFullStop() {
        val text = "Guarda www.example.com. Scrivi a a@b.it o chiama +39 333 1234567"
        val urls = MessagesLogic.links(text).map { it.url }
        assertEquals(listOf("https://www.example.com", "mailto:a@b.it", "tel:+393331234567"), urls)
        assertEquals("www.example.com", text.substring(MessagesLogic.links(text)[0].start, MessagesLogic.links(text)[0].end))
        assertTrue(MessagesLogic.links("2024").isEmpty())
    }

    @Test fun partsCountBySevenBitOrUnicode() {
        assertEquals(1 to 160, MessagesLogic.parts(""))
        assertEquals(1 to 0, MessagesLogic.parts("a".repeat(160)))
        assertEquals(2 to 145, MessagesLogic.parts("a".repeat(161)))
        assertEquals(1 to 67, MessagesLogic.parts("è€☺"))
    }

    @Test fun runsOfBubblesGroupBySideAndTime() {
        val a = Sms(1, "a", 1_000_000_000_000, true)
        assertTrue(MessagesLogic.continues(a, a.copy(id = 2, date = a.date + 60_000)))
        assertFalse(MessagesLogic.continues(a, a.copy(id = 2, date = a.date + 60_000, outgoing = false)))
        assertFalse(MessagesLogic.continues(a, a.copy(id = 2, date = a.date + MessagesLogic.GROUP_MS + 1)))
        assertFalse(MessagesLogic.continues(null, a))
    }

    @Test fun draftKeyHidesNumberAndIgnoresPrefix() {
        assertEquals(DraftKeys.of("+39 333 1234567"), DraftKeys.of("3331234567"))
        assertNotEquals(DraftKeys.of("3331234567"), DraftKeys.of("3331234568"))
        assertFalse(DraftKeys.of("3331234567").contains("333"))
        assertNotNull(DraftKeys.of("a,b"))
    }
}
