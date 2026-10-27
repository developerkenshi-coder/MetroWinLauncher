package com.metrowin.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.DebugReport
import com.metrowin.launcher.data.DebugReports
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DebugReportsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun report(kind: DebugReport): Pair<String, Long> {
        val out = ByteArrayOutputStream()
        val bytes = runBlocking { DebugReports.write(context, kind, out, now = 1_700_000_000_000L) }
        return out.toString("UTF-8") to bytes
    }

    @Test
    fun everyReportHasItsHeaderAndSaysWhatItLacks() {
        DebugReport.entries.forEach { kind ->
            val (text, bytes) = report(kind)
            assertTrue("${kind.name} header", text.startsWith("MetroWin debug report: ${kind.name}"))
            assertEquals("the count is the size of the file", text.toByteArray().size.toLong(), bytes)
            // The permissions only adb can give are missing here, and the report names the commands.
            assertTrue(text.contains("adb shell pm grant ${context.packageName} android.permission.DUMP"))
            assertTrue(text.contains("adb shell pm grant ${context.packageName} android.permission.READ_LOGS"))
        }
    }

    @Test
    fun theAppBatteryReportHoldsTheStateOfTheAppAndAsksTheSystemForItsStatistics() {
        val (text, _) = report(DebugReport.AppBattery)
        listOf("battery now", "power", "process", "how the process was stopped before", "what the launcher has on", "dumpsys batterystats of this app")
            .forEach { assertTrue("part '$it'", text.contains("===== $it")) }
        // The passphrase of the sync is in the settings: only named switches go in.
        assertTrue(!text.contains("passphrase", ignoreCase = true))
    }

    @Test
    fun theDeviceReportsAskTheSystemAndALackOfToolOrPermissionIsWrittenNotThrown() {
        val battery = report(DebugReport.DeviceBattery).first
        listOf("dumpsys battery", "dumpsys batterystats --usage", "dumpsys batterystats").forEach { assertTrue(it, battery.contains("===== $it")) }
        val log = report(DebugReport.DeviceLog).first
        assertTrue(log.contains("READ_LOGS is not given"))
        assertTrue(log.contains("===== logcat -b all"))
        val own = report(DebugReport.AppLog).first
        assertTrue(own.contains("started:"))
        assertTrue(own.contains("circular"))
    }

    @Test
    fun theFileNameSaysWhatAndWhen() {
        val name = DebugReports.fileName(DebugReport.DeviceLog, 1_700_000_000_000L)
        assertTrue(name, Regex("""metrowin-log-device-[0-9]{8}-[0-9]{6}[.]txt""").matches(name))
        assertEquals(listOf("metrowin-battery-app", "metrowin-battery-device", "metrowin-log-device", "metrowin-log-app"), DebugReport.entries.map { it.fileStem })
    }
}
