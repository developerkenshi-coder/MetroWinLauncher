package com.metrowin.launcher

import android.content.pm.ActivityInfo
import com.metrowin.launcher.data.OrientationMode
import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationModeTest {
    @Test
    fun eachModeAsksForItsOrientation() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, OrientationMode.Portrait.requested)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, OrientationMode.Auto.requested)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, OrientationMode.Landscape.requested)
    }

    @Test
    fun codesRoundTripAndUnknownOnesAreVertical() {
        OrientationMode.entries.forEach { assertEquals(it, OrientationMode.fromCode(it.code)) }
        assertEquals(OrientationMode.Portrait, OrientationMode.fromCode("sideways"))
        assertEquals(OrientationMode.Portrait, OrientationMode.fromCode(null))
    }
}
