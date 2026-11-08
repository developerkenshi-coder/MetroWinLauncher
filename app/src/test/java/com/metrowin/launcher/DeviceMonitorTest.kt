package com.metrowin.launcher

import com.metrowin.launcher.data.DeviceMonitor
import com.metrowin.launcher.data.DeviceReaders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceMonitorTest {
    @Test
    fun loadIsTheBusyShareOfTheTicksBetweenTwoReadings() {
        val before = DeviceMonitor.ticks("cpu  100 0 100 800 0 0 0 0 0 0\ncpu0 50 0 50 400 0 0 0 0 0 0\nintr 5")
        val after = DeviceMonitor.ticks("cpu  200 0 200 1000 0 0 0 0 0 0\ncpu0 100 0 100 500 0 0 0 0 0 0")
        val usage = DeviceMonitor.usage(before, after)
        assertEquals(2, usage.size)
        // 200 busy of 400 in all.
        assertEquals(50f, usage[0], 0.01f)
        assertEquals(50f, usage[1], 0.01f)
        assertEquals(0f, DeviceMonitor.usage(after, after)[0], 0f)
    }

    @Test
    fun coresAreReadWithTheirFrequenciesAndAnOfflineOneHasNone() {
        val text = "cpu0 1800000 300000 2000000 schedutil 1\ncpu1 - - - - 0\nnoise"
        val cores = DeviceMonitor.cores(text)
        assertEquals(2, cores.size)
        assertEquals(1_800_000L, cores[0].cur)
        assertEquals("schedutil", cores[0].governor)
        assertTrue(cores[0].online)
        assertNull(cores[1].cur)
        assertFalse(cores[1].online)
    }

    @Test
    fun memoryLinesAndProcessesAndServicesAreRead() {
        assertEquals(8_000_000L, DeviceMonitor.memInfo("MemTotal:        8000000 kB\nMemAvailable:    1200 kB")["MemTotal"])
        val meminfo = "Total PSS by process:\n    310,045K: system (pid 641)\n     96,953K: com.metrowin.launcher (pid 11214 / activities)\n\nTotal PSS by OOM"
        assertEquals(listOf("system" to 310_045L, "com.metrowin.launcher" to 96_953L), DeviceMonitor.processMemory(meminfo))
        val services = "  * ServiceRecord{37ec674 u0 com.google.android.gms/.nearby.Life c:com.google.android.gms}\n  * ServiceRecord{26 u0 com.google.android.gms/.play.Api c:android}\n  * ServiceRecord{be u0 com.metrowin.launcher/.notifications.NotificationService c:x}"
        assertEquals(listOf("com.google.android.gms" to 2, "com.metrowin.launcher" to 1), DeviceMonitor.servicesByApp(services))
    }

    @Test
    fun crashesAndBlockedAppsComeFromTheEventLog() {
        val log = "11-07 09:20:01.123 I/am_crash( 1234): [0,5678,com.example.app,952745541,java.lang.NullPointerException,Attempt to invoke,Foo.java,42]\n" +
            "11-07 09:25:10.000 I/am_anr( 1234): [0,999,com.other.app,1,Input dispatching timed out]\nnoise"
        val faults = DeviceMonitor.faults(log)
        assertEquals(2, faults.size)
        assertFalse(faults[0].anr)
        assertEquals("com.example.app", faults[0].packageName)
        assertEquals("java.lang.NullPointerException", faults[0].detail)
        assertTrue(faults[1].anr)
        assertEquals("Input dispatching timed out", faults[1].detail)
    }

    @Test
    fun propsAndBootState() {
        val props = DeviceMonitor.props("[ro.boot.verifiedbootstate]: [green]\n[ro.boot.flash.locked]: [1]\n[x]: []")
        assertEquals("green", props["ro.boot.verifiedbootstate"])
        assertEquals("green (locked, original system)", DeviceMonitor.bootState("green", "1"))
        assertEquals("orange (bootloader unlocked)", DeviceMonitor.bootState("orange", "0"))
        assertNull(DeviceMonitor.bootState(null, null))
    }

    @Test
    fun wifiChannelsFromFrequencies() {
        assertEquals(1, DeviceReaders.channel(2412))
        assertEquals(14, DeviceReaders.channel(2484))
        assertEquals(36, DeviceReaders.channel(5180))
        assertEquals(1, DeviceReaders.channel(5955))
    }
}
