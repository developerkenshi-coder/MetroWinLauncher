package com.metrowin.launcher

import com.metrowin.launcher.data.BatteryHistory
import com.metrowin.launcher.data.BatterySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryHistoryTest {
    private val hour = 3_600_000L

    private fun s(hours: Double, level: Int, charging: Boolean = false, tenths: Int = 300, uah: Long = 0, ua: Long = 0, mv: Int = 4000) =
        BatterySample((hours * hour).toLong(), level, tenths, mv, ua, uah, charging, 0)

    @Test
    fun aReadingSurvivesItsLine() {
        val a = s(1.0, 80, true, 312, 3_200_000, -1_500_000, 4300)
        val b = BatterySample.parse(a.line())!!
        assertEquals(a.line(), b.line())
        assertNull(BatterySample.parse("1,2,3"))
        assertNull(BatterySample.parse("a,b,c,d,e,f,g,h"))
    }

    @Test
    fun drainIsTheLevelLostPerHourOnBatteryOnly() {
        // 100 -> 90 in an hour, then a charge (ignored), then 80 -> 70 in an hour: 10 % an hour.
        val list = listOf(s(0.0, 100), s(1.0, 90), s(1.5, 90, true), s(2.0, 95, true), s(3.0, 80), s(4.0, 70))
        val summary = BatteryHistory.summary(list)
        assertEquals(10f, summary.drainPerHour!!, 0.01f)
        assertEquals(10f, summary.hoursOnFull!!, 0.01f)
        assertEquals(1, summary.charges)
    }

    @Test
    fun aGapIsNotASlope() {
        // The phone was off for ten hours: no rate comes out of two readings that far apart.
        assertNull(BatteryHistory.summary(listOf(s(0.0, 90), s(10.0, 40))).drainPerHour)
    }

    @Test
    fun peakPowerAndTemperatureAndCapacity() {
        val list = listOf(
            s(0.0, 50, true, 350, 2_500_000, -2_000_000, 4000), s(0.5, 60, true, 380, 3_000_000, -1_000_000, 4000), s(1.0, 20, false, 290, 1_000_000),
        )
        val summary = BatteryHistory.summary(list)
        assertEquals(8f, summary.peakWatts!!, 0.01f)
        assertEquals(38f, summary.tempMax!!, 0.01f)
        // Only readings at 30 % or more count: 2.5 Ah at 50 % and 3.0 Ah at 60 % both say 5000 mAh.
        assertEquals(5000f, summary.capacityMah!!, 0.5f)
        assertNotNull(BatteryHistory.capacityByDay(list).firstOrNull())
    }

    @Test
    fun withNoReadingsNothingIsSaid() {
        val summary = BatteryHistory.summary(emptyList())
        assertNull(summary.drainPerHour)
        assertNull(summary.tempMax)
        assertTrue(summary.charges == 0)
    }

    @Test
    fun theLastReadingComesFromTheTailOfTheFile() {
        val file = java.io.File.createTempFile("history", ".csv")
        try {
            assertNull(BatteryHistory.lastSample(file))
            file.writeText((0..200).joinToString("") { s(it.toDouble(), 100 - it / 4).line() + "\n" })
            assertEquals(s(200.0, 50).line(), BatteryHistory.lastSample(file)!!.line())
            // A line cut by a crash is skipped for the whole one before it.
            file.appendText("999,5")
            assertEquals(s(200.0, 50).line(), BatteryHistory.lastSample(file)!!.line())
        } finally {
            file.delete()
        }
    }
}
