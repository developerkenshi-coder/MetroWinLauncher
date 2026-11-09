package com.metrowin.launcher

import com.metrowin.launcher.gestures.ChargeAnnounce
import com.metrowin.launcher.gestures.ChargeAnnounce.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeAnnounceTest {
    @Test fun firstCableIsAnnounced() = assertTrue(ChargeAnnounce.due(true, 1_000L, null))

    @Test fun offSettingAnnouncesNothing() = assertFalse(ChargeAnnounce.due(false, 1_000L, null))

    @Test fun looseCableIsAnnouncedOnce() {
        assertFalse(ChargeAnnounce.due(true, 5_000L, 1_000L))
        assertTrue(ChargeAnnounce.due(true, 1_000L + ChargeAnnounce.DEBOUNCE_MS, 1_000L))
    }

    @Test fun launcherInFrontShowsInTheLauncher() = assertEquals(Place.Launcher, ChargeAnnounce.place(true, true, false, false, true))

    @Test fun otherAppShowsOverIt() = assertEquals(Place.Overlay, ChargeAnnounce.place(false, true, false, false, true))

    @Test fun otherAppWithoutWindowPermissionShowsNothing() = assertEquals(Place.None, ChargeAnnounce.place(false, true, false, false, false))

    @Test fun screenOffLockedOrBankShowNothing() {
        assertEquals(Place.None, ChargeAnnounce.place(true, false, false, false, true))
        assertEquals(Place.None, ChargeAnnounce.place(true, true, true, false, true))
        assertEquals(Place.None, ChargeAnnounce.place(false, true, false, true, true))
    }
}
