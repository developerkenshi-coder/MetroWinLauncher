package com.metrowin.launcher

import android.content.Intent
import com.metrowin.launcher.gestures.StatusBarOverlay
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusBarWatchTest {
    @Test
    fun onlyTheScreenGoingOffStopsTheBarFromReadingThePhone() {
        assertFalse(StatusBarOverlay.shouldWatch(Intent.ACTION_SCREEN_OFF))
        assertTrue(StatusBarOverlay.shouldWatch(Intent.ACTION_SCREEN_ON))
        // The unlock comes after "screen on": it must not stop the clock and the battery for the rest of the time the phone is used.
        assertTrue(StatusBarOverlay.shouldWatch(Intent.ACTION_USER_PRESENT))
    }
}
