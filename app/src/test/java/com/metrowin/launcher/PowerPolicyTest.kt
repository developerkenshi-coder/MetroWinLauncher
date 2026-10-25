package com.metrowin.launcher

import com.metrowin.launcher.data.BatteryInfo
import com.metrowin.launcher.data.BatterySaving
import com.metrowin.launcher.data.BatteryState
import com.metrowin.launcher.data.LiveSources
import com.metrowin.launcher.data.OpenMeteo
import com.metrowin.launcher.data.PowerPolicy
import com.metrowin.launcher.data.Throttle
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Folder
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.LiveKind
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerPolicyTest {
    private fun battery(percent: Int, state: BatteryState = BatteryState.Discharging) = BatteryInfo(percent, state)

    // --- when it saves

    @Test
    fun neverAndAlwaysIgnoreEverythingElse() {
        assertFalse(PowerPolicy.saving(BatterySaving.Never, androidBatterySaver = true, battery = battery(3)))
        assertTrue(PowerPolicy.saving(BatterySaving.Always, androidBatterySaver = false, battery = battery(100, BatteryState.Full)))
        assertTrue(PowerPolicy.saving(BatterySaving.Always, androidBatterySaver = false, battery = null))
    }

    @Test
    fun automaticFollowsAndroidsBatterySaver() {
        assertTrue(PowerPolicy.saving(BatterySaving.Auto, androidBatterySaver = true, battery = battery(90)))
        assertFalse(PowerPolicy.saving(BatterySaving.Auto, androidBatterySaver = false, battery = battery(90)))
    }

    @Test
    fun automaticAlsoSavesWhenTheBatteryIsLowAndNotCharging() {
        assertTrue(PowerPolicy.saving(BatterySaving.Auto, false, battery(PowerPolicy.LOW_BATTERY_PERCENT)))
        assertTrue(PowerPolicy.saving(BatterySaving.Auto, false, battery(5)))
        assertFalse("just above the limit", PowerPolicy.saving(BatterySaving.Auto, false, battery(PowerPolicy.LOW_BATTERY_PERCENT + 1)))
        assertFalse("charging, however low", PowerPolicy.saving(BatterySaving.Auto, false, battery(5, BatteryState.Charging)))
        assertFalse("no reading, no saving", PowerPolicy.saving(BatterySaving.Auto, false, null))
    }

    @Test
    fun theModeSurvivesItsCodeAndAnUnknownCodeIsAutomatic() {
        BatterySaving.entries.forEach { assertEquals(it, BatterySaving.fromCode(it.code)) }
        assertEquals(BatterySaving.Auto, BatterySaving.fromCode("turbo"))
        assertEquals(BatterySaving.Auto, BatterySaving.fromCode(null))
    }

    // --- what it changes

    @Test
    fun savingRefreshesLessOften() {
        LiveKind.entries.forEach { assertTrue("$it", PowerPolicy.liveMaxAgeMs(it, true) > PowerPolicy.liveMaxAgeMs(it, false)) }
        assertTrue(PowerPolicy.weatherRetryMs(true) > PowerPolicy.weatherRetryMs(false))
        assertTrue(PowerPolicy.weatherMaxAgeMs(true) > PowerPolicy.weatherMaxAgeMs(false))
        assertTrue(PowerPolicy.packagesMaxAgeMs(true) > PowerPolicy.packagesMaxAgeMs(false))
        assertEquals(240_000L, PowerPolicy.syncGapMs(60_000L, true))
        assertEquals(60_000L, PowerPolicy.syncGapMs(60_000L, false))
        // Normally the weather keeps the age it always had.
        assertEquals(OpenMeteo.MAX_AGE_MILLIS, PowerPolicy.weatherMaxAgeMs(false))
    }

    @Test
    fun savingStopsTheIdleAnimationsAndTheHighRefreshRate() {
        assertTrue(PowerPolicy.idleAnimations(false))
        assertFalse(PowerPolicy.idleAnimations(true))
        assertTrue(PowerPolicy.highRefreshRate(wanted = true, saving = false))
        assertFalse(PowerPolicy.highRefreshRate(wanted = true, saving = true))
        assertFalse("not wanted is not wanted", PowerPolicy.highRefreshRate(wanted = false, saving = false))
    }

    // --- what Start asks the phone for

    private fun app(id: String, live: LiveKind?) = AppItem(id, id, Launch.Action("x"), live = live)

    private val items = listOf(
        app("photos", LiveKind.Photos), app("people", LiveKind.People), app("calendar", LiveKind.Calendar),
        app("phone", LiveKind.Phone), app("weather", LiveKind.Weather), app("battery", LiveKind.Battery),
        app("plain", null),
    )

    @Test
    fun onlyThePinnedLiveTilesThatReadThePhoneAreWanted() {
        val pages = listOf(
            listOf<com.metrowin.launcher.model.StartEntry>(Tile("photos", TileSize.Wide), Tile("weather", TileSize.Medium), Tile("plain", TileSize.Small)),
            listOf(Tile("phone", TileSize.Medium)),
        )
        // Weather has its own fetch and the battery its own broadcast: neither reads the phone's data.
        assertEquals(setOf(LiveKind.Photos, LiveKind.Phone), LiveSources.wanted(pages, items))
    }

    @Test
    fun anAppInsideAFolderStillCounts() {
        val pages = listOf(listOf<com.metrowin.launcher.model.StartEntry>(Folder("f", "F", TileSize.Medium, listOf("calendar", "plain"))))
        assertEquals(setOf(LiveKind.Calendar), LiveSources.wanted(pages, items))
    }

    @Test
    fun nothingPinnedMeansNothingIsRead() {
        assertEquals(emptySet<LiveKind>(), LiveSources.wanted(listOf(emptyList()), items))
    }

    // --- throttle

    @Test
    fun workIsDueTheFirstTimeAndAgainAfterItsGap() {
        var now = 1_000_000L
        val throttle = Throttle { now }
        assertTrue(throttle.due("live", 120_000))
        now += 119_999
        assertFalse("not yet", throttle.due("live", 120_000))
        now += 1
        assertTrue("the gap is over", throttle.due("live", 120_000))
        now += 10
        assertFalse("and counting again from there", throttle.due("live", 120_000))
    }

    @Test
    fun eachKindOfWorkHasItsOwnClockAndTheGapCanBeResetOrTheClockGoBack() {
        var now = 5_000_000L
        val throttle = Throttle { now }
        assertTrue(throttle.due("a", 60_000))
        assertTrue("b is not a", throttle.due("b", 60_000))
        throttle.reset("a")
        assertTrue(throttle.due("a", 60_000))
        now -= 3_600_000
        assertTrue("the clock was set back: do the work again", throttle.due("a", 60_000))
    }
}
