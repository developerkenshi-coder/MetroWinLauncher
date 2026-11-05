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
}
