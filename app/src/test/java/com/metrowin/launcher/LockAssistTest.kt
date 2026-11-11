package com.metrowin.launcher

import com.metrowin.launcher.gestures.LockAssist
import com.metrowin.launcher.gestures.LockAssist.Way
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LockAssistTest {
    private val hour = 60L * 60 * 1000

    private fun way(secure: Boolean = true, finger: Boolean = true, since: Long? = 1 * hour, sim: Boolean = false) =
        LockAssist.decide(secure, finger, since, sim).way

    @Test
    fun `a fingerprint is promised only while Android takes it`() {
        assertEquals(Way.Fingerprint, way(since = 5 * hour))
        assertEquals("71 hours: still", Way.Fingerprint, way(since = 71 * hour))
        assertEquals("72 hours without the PIN: the PIN is asked", Way.Pin, way(since = 72 * hour))
        assertEquals(Way.Pin, way(since = 200 * hour))
    }

    @Test
    fun `when the time of the last PIN is not known nothing is promised but the spot stays`() {
        val decision = LockAssist.decide(secure = true, fingerprintSaved = true, sinceCredentialMs = null, simLocked = false)
        assertEquals(Way.Unsure, decision.way)
        assertTrue(decision.fingerprintSpot)
    }

    @Test
    fun `no lock or no fingerprint means a plain swipe`() {
        assertEquals(Way.Swipe, way(secure = false))
        assertEquals(Way.Swipe, way(finger = false))
        assertFalse(LockAssist.decide(true, true, 80 * hour, false).fingerprintSpot)
    }

    @Test
    fun `the screen of the SIM PIN is never covered`() {
        assertFalse(LockAssist.decide(true, true, hour, simLocked = true).showOverlay)
        assertTrue(LockAssist.decide(true, true, hour, simLocked = false).showOverlay)
    }

    @Test
    fun `the time since the PIN comes from the clock of Android`() {
        assertEquals(500L, LockAssist.sinceCredential(last = 1_000, now = 1_500))
        assertNull("never since boot", LockAssist.sinceCredential(last = -1, now = 1_500))
        assertNull("a time in the future cannot be", LockAssist.sinceCredential(last = 2_000, now = 1_500))
    }
}
