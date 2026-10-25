package com.metrowin.launcher

import com.metrowin.launcher.data.DisplayModeInfo
import com.metrowin.launcher.data.RefreshRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // --- the high rate only while the screen is used

    @Test
    fun theHighRateIsAskedForWhileTheScreenIsBeingUsedAndNotLonger() {
        assertTrue(RefreshRate.wantsHigh(wanted = true, saving = false, lastTouchAt = 1_000, now = 1_000))
        assertTrue(RefreshRate.wantsHigh(true, false, lastTouchAt = 1_000, now = 1_000 + RefreshRate.IDLE_MS - 1))
        assertFalse("three seconds without a touch", RefreshRate.wantsHigh(true, false, lastTouchAt = 1_000, now = 1_000 + RefreshRate.IDLE_MS))
        assertFalse("never touched", RefreshRate.wantsHigh(true, false, lastTouchAt = 0, now = 60_000))
    }

    @Test
    fun theSettingAndTheSavingStillDecideFirst() {
        assertFalse("turned off in the settings", RefreshRate.wantsHigh(wanted = false, saving = false, lastTouchAt = 1_000, now = 1_100))
        assertFalse("saving battery", RefreshRate.wantsHigh(wanted = true, saving = true, lastTouchAt = 1_000, now = 1_100))
    }

    @Test
    fun aTimeThatWentBackIsNotATouchFromTheFuture() {
        assertFalse(RefreshRate.wantsHigh(true, false, lastTouchAt = 5_000, now = 1_000))
    }

    @Test
    fun theRateIsOnlyTakenAwayWhereTheScreenCanChangeCleanly() {
        val seamless120 = DisplayModeInfo(3, 1080, 2400, 120f, alternatives = listOf(60f))
        val locked120 = DisplayModeInfo(3, 1080, 2400, 120f)
        assertTrue("it says it can go to 60 without a flash", RefreshRate.canLeave(seamless120))
        assertFalse("it says nothing: the rate stays as asked", RefreshRate.canLeave(locked120))
    }

    // --- the slowest rate that still looks smooth, asked for at rest

    @Test
    fun atRestTheSlowestRateOfSixtyOrMoreIsAsked() {
        val from120 = fhd120.copy(alternatives = listOf(60f, 90f))
        assertEquals(fhd60, RefreshRate.atRest(from120, listOf(fhd60, fhd90, from120)))
    }

    @Test
    fun atRestARateThatCannotBeReachedCleanlyIsNotAsked() {
        // 60 exists, but the screen only lists 90 as a clean switch from 120.
        val from120 = fhd120.copy(alternatives = listOf(90f))
        assertEquals(fhd90, RefreshRate.atRest(from120, listOf(fhd60, fhd90, from120)))
    }

    @Test
    fun atRestAScreenThatSaysNothingKeepsItsMode() {
        // Before Android 12 nothing is known to be clean: only the current mode qualifies.
        assertEquals(fhd120, RefreshRate.atRest(fhd120, listOf(fhd60, fhd90, fhd120)))
    }

    @Test
    fun atRestTheRateNeverGoesBelowSixty() {
        // Phones list 45, 30 or 20 as divisors of a physical mode: tiles that turn by themselves would show steps.
        val from120 = fhd120.copy(alternatives = listOf(30f, 40f, 45f))
        val slow = DisplayModeInfo(9, 1080, 2400, 30f)
        assertEquals("nothing qualifies but the mode it is in", from120, RefreshRate.atRest(from120, listOf(slow, from120)))
        assertNull(RefreshRate.atRest(slow.copy(alternatives = listOf(120f)), listOf(slow)))
    }

    @Test
    fun atRestAPanelThatReports59Point94IsStillSixty() {
        val ntsc = DisplayModeInfo(1, 1080, 2400, 59.94f)
        val from120 = fhd120.copy(alternatives = listOf(59.94f))
        assertEquals(ntsc, RefreshRate.atRest(from120, listOf(ntsc, from120)))
    }

    @Test
    fun atRestTheResolutionNeverChanges() {
        val qhd60 = DisplayModeInfo(6, 1440, 3200, 60f)
        val from120 = fhd120.copy(alternatives = listOf(60f))
        assertEquals("the 60 Hz mode of another resolution is left alone", from120, RefreshRate.atRest(from120, listOf(qhd60, from120)))
    }

    @Test
    fun atRestAScreenAlreadyAtSixtyStaysThere() {
        val at60 = fhd60.copy(alternatives = listOf(90f, 120f))
        assertEquals(at60, RefreshRate.atRest(at60, listOf(at60, fhd90, fhd120)))
    }

    @Test
    fun atRestOnThePhoneItWasMeasuredOn() {
        // vivo V2514, Android 17: three physical rates in two resolutions, modes of the same rate in several groups, no variable rate.
        // The screen stayed at 120 Hz with the launcher in front and untouched, because nothing asked for less.
        fun mode(id: Int, w: Int, h: Int, hz: Float, alt: List<Float> = emptyList()) = DisplayModeInfo(id, w, h, hz, alt)
        val current = mode(9, 1080, 2400, 120f, listOf(60f, 90f))
        val modes = listOf(
            mode(1, 1260, 2800, 120f), mode(3, 1260, 2800, 90f), mode(5, 1260, 2800, 60f),
            mode(7, 1080, 2400, 120f), mode(8, 1080, 2400, 120f), current,
            mode(10, 1080, 2400, 90f), mode(11, 1080, 2400, 90f), mode(12, 1080, 2400, 60f), mode(13, 1080, 2400, 60f),
        )
        val rest = RefreshRate.atRest(current, modes)!!
        assertEquals(60f, rest.refreshRate)
        assertEquals(1080, rest.width)
        assertEquals(2400, rest.height)
        // and back up at a touch, at the same resolution
        val high = RefreshRate.best(rest.copy(alternatives = listOf(90f, 120f)), modes)!!
        assertEquals(120f, high.refreshRate)
        assertEquals(1080, high.width)
    }
}
