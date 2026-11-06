package com.metrowin.launcher

import com.metrowin.launcher.calls.DialerRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullScreenGrantTest {
    @Test fun noteShowsOnlyForThePhoneAppOnAndroid14WithoutTheRight() {
        assertTrue(DialerRole.needsFullScreenGrant(isDefault = true, sdk = 34, canUse = false))
        assertTrue(DialerRole.needsFullScreenGrant(isDefault = true, sdk = 36, canUse = false))
        assertFalse(DialerRole.needsFullScreenGrant(isDefault = true, sdk = 34, canUse = true))
        assertFalse(DialerRole.needsFullScreenGrant(isDefault = false, sdk = 34, canUse = false))
        assertFalse(DialerRole.needsFullScreenGrant(isDefault = true, sdk = 33, canUse = false))
    }
}
