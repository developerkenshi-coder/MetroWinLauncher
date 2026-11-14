package com.metrowin.launcher

import com.metrowin.launcher.mail.MailError
import com.metrowin.launcher.mail.MailErrors
import com.metrowin.launcher.mail.NoTrashException
import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException
import org.junit.Assert.assertEquals
import org.junit.Test

class MailErrorsTest {
    @Test fun theFailuresOfJavaMailAreToldApart() {
        assertEquals(MailError.Login, MailErrors.kind(AuthenticationFailedException("[AUTHENTICATIONFAILED] Invalid credentials")))
        assertEquals(MailError.TooBig, MailErrors.kind(MessagingException("552 5.3.4 Message size exceeds fixed limit")))
        assertEquals(MailError.Recipient, MailErrors.kind(MessagingException("550 5.1.1 User unknown")))
        assertEquals(MailError.Network, MailErrors.kind(MessagingException("Couldn\'t connect to host", java.net.UnknownHostException("imap.x"))))
        assertEquals(MailError.Certificate, MailErrors.kind(MessagingException("failed", javax.net.ssl.SSLHandshakeException("PKIX path building failed"))))
        assertEquals(MailError.NoTrash, MailErrors.kind(NoTrashException()))
        assertEquals(MailError.Other, MailErrors.kind(IllegalStateException("odd")))
    }

    @Test fun theRealReasonHangsOnNextException() {
        val e = MessagingException("Sending failed", MessagingException("535 5.7.8 authentication failed"))
        assertEquals(MailError.Login, MailErrors.kind(e))
    }
}
