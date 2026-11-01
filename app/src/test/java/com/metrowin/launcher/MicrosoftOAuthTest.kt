package com.metrowin.launcher

import com.metrowin.launcher.mail.MailServiceDetector
import com.metrowin.launcher.mail.MicrosoftOAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MicrosoftOAuthTest {
    @Test
    fun theChallengeIsTheOneOfTheStandard() {
        // The example of RFC 7636, appendix B.
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", MicrosoftOAuth.challengeOf("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test
    fun theSignInPageAsksForTheCodeFlowWithPkceAndKeepsWhatItNeedsToComeBack() {
        val uri = MicrosoftOAuth.authorizationUri("pablo912@hotmail.it")
        val waiting = MicrosoftOAuth.pending!!
        assertEquals("login.microsoftonline.com", uri.host)
        assertEquals("code", uri.getQueryParameter("response_type"))
        assertEquals("S256", uri.getQueryParameter("code_challenge_method"))
        assertEquals(MicrosoftOAuth.challengeOf(waiting.verifier), uri.getQueryParameter("code_challenge"))
        assertEquals(waiting.state, uri.getQueryParameter("state"))
        assertEquals(MicrosoftOAuth.REDIRECT, uri.getQueryParameter("redirect_uri"))
        assertEquals("pablo912@hotmail.it", uri.getQueryParameter("login_hint"))
    }

    @Test
    fun onlyTheAccountsOfMicrosoftSignInOnItsPage() {
        assertEquals("microsoft", MailServiceDetector.detect("a@hotmail.it").oauth)
        assertEquals("microsoft", MailServiceDetector.detect("a@outlook.com").oauth)
        assertNull(MailServiceDetector.detect("a@gmail.com").oauth)
        assertNull(MailServiceDetector.detect("a@icloud.com").oauth)
    }
}
