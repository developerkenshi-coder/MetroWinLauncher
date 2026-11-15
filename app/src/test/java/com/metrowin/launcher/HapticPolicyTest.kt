package com.metrowin.launcher

import com.metrowin.launcher.ui.anim.HapticPolicy
import com.metrowin.launcher.ui.anim.HapticRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class HapticPolicyTest {
    @Test
    fun noStrengthMeansNoVibrationWhateverTheSwitchSays() {
        assertEquals(HapticRoute.None, HapticPolicy.route(own = true, strength = 0))
        assertEquals(HapticRoute.None, HapticPolicy.route(own = false, strength = 0))
    }

    @Test
    fun theTapSwitchMakesAGestureTheSameTapAndOtherwiseTheSystemDecides() {
        assertEquals(HapticRoute.Own, HapticPolicy.route(own = true, strength = 100))
        assertEquals(HapticRoute.System, HapticPolicy.route(own = false, strength = 100))
    }
}
