package com.metrowin.launcher

import com.metrowin.launcher.data.BatteryDumps
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.data.ShizukuButtons
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuTerminalTest {
    private fun first(plan: ShizukuButtons.Plan?) = plan!!.commands.first()

    @Test
    fun aToggleGoesToTheOtherState() {
        val off = ShizukuButtons.plan(QuickAction.Wifi, on = true, timeoutMs = null, brightness = null)!!
        assertArrayEquals(arrayOf("svc", "wifi", "disable"), off.commands.single())
        assertEquals(false, off.becomes)
        assertArrayEquals(arrayOf("cmd", "connectivity", "airplane-mode", "enable"), first(ShizukuButtons.plan(QuickAction.Airplane, null, null, null)))
        assertArrayEquals(arrayOf("cmd", "location", "set-location-enabled", "true"), first(ShizukuButtons.plan(QuickAction.Location, false, null, null)))
        assertArrayEquals(arrayOf("svc", "data", "disable"), first(ShizukuButtons.plan(QuickAction.MobileData, true, null, null)))
    }

    @Test
    fun rotationOnMeansLockedSoTheNextStateUnlocksIt() {
        assertArrayEquals(arrayOf("settings", "put", "system", "accelerometer_rotation", "1"), first(ShizukuButtons.plan(QuickAction.Rotation, true, null, null)))
        assertArrayEquals(arrayOf("settings", "put", "system", "accelerometer_rotation", "0"), first(ShizukuButtons.plan(QuickAction.Rotation, false, null, null)))
    }

    @Test
    fun stepsGoOnAndWrapAround() {
        assertArrayEquals(arrayOf("settings", "put", "system", "screen_off_timeout", "120000"), first(ShizukuButtons.plan(QuickAction.ScreenTimeout, null, 60_000, null)))
        val up = ShizukuButtons.plan(QuickAction.Brightness, null, null, 128)!!.commands.last()
        assertEquals("179", up.last())
        assertEquals("26", ShizukuButtons.plan(QuickAction.Brightness, null, null, 255)!!.commands.last().last())
    }

    @Test
    fun theButtonsAndroidGivesNoCommandForHaveNoPlan() {
        ShizukuButtons.none.forEach { assertNull(ShizukuButtons.plan(it, null, null, null)) }
        ShizukuButtons.direct.forEach { assertTrue(it.id, ShizukuButtons.plan(it, null, null, null) != null) }
        assertFalse(QuickAction.Hotspot in ShizukuButtons.direct)
    }

    @Test
    fun dumpsysBatteryLinesBecomeKeysAndValues() {
        val text = """
            Current Battery Service state:
              AC powered: false
              Max charging current: 0
             Time when the latest updated value of the Max charging current was sent via battery changed broadcast: +4s72ms
              level: 100
              technology: Li-ion
        """.trimIndent()
        val map = BatteryDumps.dumpsysBattery(text)
        assertEquals(listOf("AC powered", "Max charging current", "level", "technology"), map.keys.toList())
        assertEquals("Li-ion", map["technology"])
    }

    @Test
    fun sysfsFilesAreGroupedBySupplyAndTheUnreadableOnesDropped() {
        val text = "## /sys/class/power_supply/ac/uevent\n## /sys/class/power_supply/battery/uevent\nPOWER_SUPPLY_NAME=battery\nPOWER_SUPPLY_CYCLE_COUNT=312\n"
        val out = BatteryDumps.sysfs(text)
        assertEquals(setOf("battery"), out.keys)
        assertEquals("312", out["battery"]!!["CYCLE_COUNT"])
    }

    @Test
    fun batterystatsGivesPartsAndAppsBiggestFirst() {
        val text = """
            Capacity: 3000, Computed drain: 0, actual drain: 0
            screen: 2.05 apps: 2.05
            cpu: 1.60 apps: 1.60 duration: 16h 2m
            audio: 0.00 apps: 0.00 duration: 1m
            (on battery, screen off/doze)
            cpu: 0.000519 apps: 0.000495 duration: 29s
            UID u0a195: 1.16 fg: 0.006 (3m)
            UID 1000: 3.5 fg: 0.1 (3m)
            Time on battery: 14h 29m (99.9%) realtime
        """.trimIndent()
        assertEquals(listOf("screen", "cpu"), BatteryDumps.components(text).map { it.first })
        assertEquals(1.60, BatteryDumps.components(text).first { it.first == "cpu" }.second, 0.0)
        assertEquals(listOf("1000", "u0a195"), BatteryDumps.uids(text).map { it.first })
        assertEquals(10_195, BatteryDumps.uidNumber("u0a195"))
        assertEquals(1000, BatteryDumps.uidNumber("1000"))
        assertNull(BatteryDumps.uidNumber("u10a5"))
        assertTrue(BatteryDumps.statLines(text)["Capacity"]!!.startsWith("3000"))
        assertTrue(BatteryDumps.statLines(text)["Time on battery"]!!.startsWith("14h"))
    }

    @Test
    fun temperaturesAndWakeLocksAreRead() {
        val thermal = "\tTemperature{mValue=30.2, mType=2, mName=battery, mStatus=0}\n\tTemperature{mValue=30.1, mType=3, mName=skin, mStatus=0}\n\tTemperature{mValue=31.0, mType=2, mName=battery, mStatus=0}"
        assertEquals(mapOf("battery" to 30.2, "skin" to 30.1), BatteryDumps.temperatures(thermal))
        val power = "  PARTIAL_WAKE_LOCK              'RILJ' ACQ=-5s (uid=1001 pid=1)\n  SCREEN_BRIGHT_WAKE_LOCK  'x' ACQ=0"
        assertEquals(listOf("PARTIAL_WAKE_LOCK 'RILJ'", "SCREEN_BRIGHT_WAKE_LOCK 'x'"), BatteryDumps.wakeLocks(power))
    }
}
