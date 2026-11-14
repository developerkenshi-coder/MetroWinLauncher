package com.metrowin.launcher

import com.metrowin.launcher.datasense.AppData
import com.metrowin.launcher.datasense.BigFile
import com.metrowin.launcher.datasense.DataBucket
import com.metrowin.launcher.datasense.DataMath
import com.metrowin.launcher.datasense.DataPeriod
import com.metrowin.launcher.datasense.DataPeriods
import com.metrowin.launcher.datasense.Span
import com.metrowin.launcher.datasense.StorageScan
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSenseMathTest {
    @Test fun limitIsReadInGbByDefaultAndWithUnits() {
        assertEquals(5000L, DataMath.parseLimitMb("5"))
        assertEquals(1500L, DataMath.parseLimitMb("1,5 GB"))
        assertEquals(500L, DataMath.parseLimitMb("500 mb"))
        assertEquals(2147L, DataMath.parseLimitMb("2 GiB"))
        assertEquals(0L, DataMath.parseLimitMb("0"))
        assertNull(DataMath.parseLimitMb("abc"))
        assertNull(DataMath.parseLimitMb("5 parsec"))
        assertEquals("5", DataMath.limitText(5000))
        assertEquals("500 MB", DataMath.limitText(500))
    }

    @Test fun aSpanOverTwoBarsIsSharedByTime() {
        val bars = listOf(DataBucket("a", 0, 100), DataBucket("b", 100, 200))
        val out = DataMath.distribute(listOf(Span(50, 150, 1000), Span(0, 100, 200)), bars)
        assertEquals(700L, out[0])
        assertEquals(500L, out[1])
    }

    @Test fun theBarsOfADayWithTheClockChangedStillCoverTheDay() {
        val zone = ZoneId.of("Europe/Rome")
        // 2026-10-25 has 25 hours in Rome: the daily bars follow the days, not 24-hour steps.
        val now = ZonedDateTime.of(2026, 10, 26, 12, 0, 0, 0, zone)
        val bars = DataPeriods.buckets(DataPeriod.Week, now)
        val night = bars.first { it.label == "25" }
        assertEquals(25 * 3_600_000L, night.to - night.from)
        bars.zipWithNext().forEach { (a, b) -> assertEquals(a.to, b.from) }
    }

    @Test fun projectionStretchesThePace() {
        assertEquals(300L, DataMath.projection(100, 10, 30))
        assertEquals(100L, DataMath.projection(100, 0, 30))
        assertEquals(100L, DataMath.projection(100, 30, 30))
    }

    @Test fun trendAndUnusualUse() {
        assertEquals(50, DataMath.trendPercent(150, 100))
        assertEquals(-50, DataMath.trendPercent(50, 100))
        assertNull(DataMath.trendPercent(50, 0))
        assertEquals(4, DataMath.unusualFactor(400_000_000, listOf(100_000_000, 100_000_000, 100_000_000)))
        assertNull(DataMath.unusualFactor(150_000_000, listOf(100_000_000, 100_000_000, 100_000_000)))
        assertNull(DataMath.unusualFactor(400_000_000, listOf(100_000_000)))
        assertNull(DataMath.unusualFactor(10_000_000, listOf(1, 1, 1)))
    }

    @Test fun theLimitAlertFollowsTheShareChosenAndRestartsWhenAnnouncedIsCleared() {
        assertEquals(0, DataPeriods.alertLevel(599, 1000, 0, 60))
        assertEquals(60, DataPeriods.alertLevel(600, 1000, 0, 60))
        assertEquals(0, DataPeriods.alertLevel(700, 1000, 60, 60))
        // Raised limit: the cycle starts again with nothing announced, and the new share warns again.
        assertEquals(80, DataPeriods.alertLevel(1600, 2000, 0))
        assertEquals(50, DataPeriods.alertLevel(500, 1000, 0, 10))
    }

    @Test fun previousPeriodStartsWhereItShould() {
        val now = ZonedDateTime.of(2026, 11, 20, 10, 0, 0, 0, ZoneId.of("UTC"))
        assertEquals(19, DataMath.previousStart(DataPeriod.Today, DataPeriods.start(DataPeriod.Today, now)).dayOfMonth)
        assertEquals(10, DataMath.previousStart(DataPeriod.ThisMonth, DataPeriods.start(DataPeriod.ThisMonth, now, 15)).monthValue)
    }

    @Test fun overageCostIsOnlyAboveTheLimit() {
        assertEquals(0.0, DataMath.overageCost(900, 1000, 2.0), 0.0)
        assertEquals(4.0, DataMath.overageCost(3_000_000_000, 1_000_000_000, 2.0), 1e-9)
    }

    @Test fun csvQuotesWhatNeedsIt() {
        val csv = DataMath.csv(listOf(Triple(DataBucket("1", 0, 10), 5L, null)), listOf(AppData(1, "A, \"B\"", "p", 1, 2, 3, 4)))
        assertTrue(csv.contains("1,0,10,5,\n"))
        assertTrue(csv.contains("\"A, \"\"B\"\"\",p,1,2,3,4"))
    }

    @Test fun treemapFillsTheWholeInProportion() {
        val cells = DataMath.treemap(listOf(500, 300, 200))
        assertEquals(1.0, cells.sumOf { (it.w * it.h).toDouble() }, 1e-4)
        assertEquals(0.5, (cells[0].w * cells[0].h).toDouble(), 1e-4)
        assertEquals(0.2, (cells[2].w * cells[2].h).toDouble(), 1e-4)
        assertTrue(DataMath.treemap(emptyList()).isEmpty())
    }

    @Test fun historyKeepsTheLatestDaysAndTheFreshOnesWin() {
        val merged = DataMath.mergeDaily("1:10:1\n2:20:2", mapOf(2L to (99L to 9L), 3L to (30L to 3L)), keep = 2)
        assertEquals(listOf(Triple(2L, 99L, 9L), Triple(3L, 30L, 3L)), DataMath.parseDaily(merged))
        assertEquals(listOf(5L to 70L), DataMath.parseFree(DataMath.mergeFree("5:60", 5, 70)))
    }

    @Test fun duplicatesShareNameAndSizeAndAreBig() {
        val uri = android.net.Uri.EMPTY
        val files = listOf(BigFile("a.mp4", 5_000_000, uri), BigFile("a.mp4", 5_000_000, uri), BigFile("b.jpg", 10, uri), BigFile("b.jpg", 10, uri), BigFile("c.mp4", 5_000_000, uri))
        assertEquals(1, StorageScan.duplicatesOf(files).size)
    }
}
