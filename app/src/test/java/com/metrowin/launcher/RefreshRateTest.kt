package com.metrowin.launcher

import com.metrowin.launcher.data.DisplayModeInfo
import com.metrowin.launcher.data.RefreshRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefreshRateTest {
    private val fhd60 = DisplayModeInfo(1, 1080, 2400, 60f)
    private val fhd90 = DisplayModeInfo(2, 1080, 2400, 90f)
    private val fhd120 = DisplayModeInfo(3, 1080, 2400, 120f)
    private val qhd120 = DisplayModeInfo(4, 1440, 3200, 120f)
    private val qhd144 = DisplayModeInfo(5, 1440, 3200, 144f)

    @Test
    fun picksTheFastestModeAtTheCurrentResolution() {
        assertEquals(fhd120, RefreshRate.best(fhd60, listOf(fhd60, fhd90, fhd120)))
    }

    @Test
    fun neverChangesTheResolution() {
        // A faster mode exists, but only at another resolution.
        assertEquals(fhd120, RefreshRate.best(fhd60, listOf(fhd60, fhd120, qhd144)))
        assertEquals(qhd120.copy(), RefreshRate.best(qhd120, listOf(fhd120, qhd120)))
    }

    @Test
    fun keepsTheCurrentModeWhenItIsAlreadyTheFastest() {
        assertEquals(fhd120, RefreshRate.best(fhd120, listOf(fhd60, fhd120)))
    }

    @Test
    fun aSixtyHertzPanelStaysAtSixty() {
        assertEquals(fhd60, RefreshRate.best(fhd60, listOf(fhd60)))
    }

    @Test
    fun noModeAtTheCurrentResolutionMeansNoRequest() {
        assertNull(RefreshRate.best(fhd60, listOf(qhd120)))
    }
}
