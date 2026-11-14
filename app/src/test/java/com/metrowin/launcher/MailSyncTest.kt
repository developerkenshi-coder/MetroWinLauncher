package com.metrowin.launcher

import com.metrowin.launcher.mail.MailMessage
import com.metrowin.launcher.mail.MailSync
import org.junit.Assert.assertEquals
import org.junit.Test

class MailSyncTest {
    private fun mail(uid: Long) = MailMessage(uid.toString(), "a", "n", "n@x.com", "s", "", 0L, true, false, "", false)

    @Test fun firstLookTellsNothing() {
        assertEquals(emptyList<MailMessage>(), MailSync.toNotify(null, listOf(mail(5), mail(4))))
    }

    @Test fun onlyMessagesAboveTheLastLookAreTold() {
        assertEquals(listOf("9", "8"), MailSync.toNotify(7, listOf(mail(9), mail(8), mail(7))).map { it.id })
    }

    @Test fun theSumOfTheCountsLeavesOutItsOwnKey() {
        // Typed as Any: with a Long among them the literals would all become Longs, which a preference file never holds for a count.
        val all: Map<String, Any> = mapOf("unread_a" to Integer.valueOf(2), "unread_b" to Integer.valueOf(3), "unread_total" to Integer.valueOf(5), "uid_a" to 9L)
        assertEquals(5, MailSync.sum(all))
    }
}
