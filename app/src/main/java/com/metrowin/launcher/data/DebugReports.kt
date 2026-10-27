package com.metrowin.launcher.data

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.pm.PackageInfoCompat
import com.metrowin.launcher.gestures.LockAccessibilityService
import java.io.BufferedOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The reports of the debug page, each a text file. */
enum class DebugReport(val fileStem: String) {
    AppBattery("metrowin-battery-app"),
    DeviceBattery("metrowin-battery-device"),
    DeviceLog("metrowin-log-device"),
    AppLog("metrowin-log-app"),
}

/**
 * What Android lets the reports read. The system dumps need DUMP and the usage access, the log of the other apps needs READ_LOGS: DUMP
 * and READ_LOGS can only be given through adb (`pm grant`), the usage access in the settings of the phone.
 */
data class DebugAccess(val dump: Boolean, val readLogs: Boolean, val usage: Boolean)

/**
 * Writes the reports of the debug page. A report never fails for a permission or a tool that is missing: it says so inside, with what
 * was read all the same, so that the file always tells what it holds and what it lacks.
 */
object DebugReports {
    const val DUMP = "android.permission.DUMP"
    const val READ_LOGS = "android.permission.READ_LOGS"

    /** The commands that give the app the permissions only adb can give. */
    fun adbGrants(packageName: String): List<String> = listOf(DUMP, READ_LOGS).map { "adb shell pm grant $packageName $it" }

    fun access(context: Context): DebugAccess = DebugAccess(
        dump = context.checkSelfPermission(DUMP) == PackageManager.PERMISSION_GRANTED,
        readLogs = context.checkSelfPermission(READ_LOGS) == PackageManager.PERMISSION_GRANTED,
        usage = usageAccess(context),
    )

    @Suppress("DEPRECATION")
    private fun usageAccess(context: Context): Boolean = runCatching {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun fileName(kind: DebugReport, millis: Long): String =
        "${kind.fileStem}-${FILE_TIME.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))}.txt"

    /** Writes the report to [out] and says how many bytes it has. */
    suspend fun write(context: Context, kind: DebugReport, out: OutputStream, now: Long = System.currentTimeMillis()): Long = withContext(Dispatchers.IO) {
        val counted = Counting(BufferedOutputStream(out))
        val text = PrintStream(counted, false, "UTF-8")
        val report = Report(context.applicationContext, text, counted)
        report.header(kind, now)
        when (kind) {
            DebugReport.AppBattery -> report.appBattery()
            DebugReport.DeviceBattery -> report.deviceBattery()
            DebugReport.DeviceLog -> report.deviceLog()
            DebugReport.AppLog -> report.appLog(now)
        }
        text.flush()
        counted.flush()
        counted.count
    }

    private val FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)
    private val READABLE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX", Locale.ROOT)

    private fun time(millis: Long) = READABLE_TIME.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    private class Counting(out: OutputStream) : FilterOutputStream(out) {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }
    }

    private class Report(private val context: Context, private val text: PrintStream, private val raw: OutputStream) {
        private val packageName = context.packageName

        fun header(kind: DebugReport, now: Long) {
            val info = runCatching { context.packageManager.getPackageInfo(packageName, 0) }.getOrNull()
            text.println("MetroWin debug report: ${kind.name}")
            text.println("generated: ${time(now)}")
            text.println("app: $packageName ${info?.versionName.orEmpty()} (${info?.let(PackageInfoCompat::getLongVersionCode) ?: 0})")
            text.println("device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            text.println("build: ${Build.FINGERPRINT}")
            text.println("locale: ${Locale.getDefault().toLanguageTag()}, time zone: ${ZoneId.systemDefault()}")
            val access = access(context)
            text.println("access: DUMP=${access.dump} READ_LOGS=${access.readLogs} usage access=${access.usage}")
            if (!access.dump || !access.readLogs) {
                text.println("missing permissions, to give from a computer with the phone connected:")
                adbGrants(packageName).forEach { text.println("  $it") }
            }
            if (!access.usage) text.println("usage access is given in Android's settings: Special access > Usage access > MetroWin")
        }

        // --- the reports

        fun appBattery() {
            batteryNow()
            power()
            process()
            exits()
            launcher()
            command("dumpsys batterystats of this app", "dumpsys", "batterystats", packageName)
        }

        fun deviceBattery() {
            batteryNow()
            power()
            command("dumpsys battery", "dumpsys", "battery")
            command("dumpsys batterystats --usage", "dumpsys", "batterystats", "--usage")
            command("dumpsys batterystats", "dumpsys", "batterystats")
        }

        fun deviceLog() {
            if (!access(context).readLogs) {
                text.println("\nREAD_LOGS is not given: the lines below are only those of this app, Android hides the others.")
            }
            command("logcat -b all", "logcat", "-d", "-b", "all", "-v", "threadtime")
        }

        fun appLog(now: Long) {
            val started = now - (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime())
            text.println("\nprocess ${Process.myPid()} started: ${time(started)}")
            text.println("The buffers of Android are circular: if the first line below is later than the start, the older ones are gone.")
            command("logcat -b all --pid=${Process.myPid()}", "logcat", "-d", "-b", "all", "-v", "threadtime", "--pid=${Process.myPid()}")
            exits()
        }

        // --- the parts

        private fun part(title: String, body: () -> Unit) {
            text.println("\n===== $title =====")
            runCatching(body).onFailure { text.println("(could not read: ${it.javaClass.simpleName}: ${it.message})") }
        }

        private fun batteryNow() = part("battery now") {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let {
                text.println("level: ${it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)} of ${it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)}")
                text.println("status: ${it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)}, plugged: ${it.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)}, health: ${it.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)}")
                text.println("temperature: ${it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0} C, voltage: ${it.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)} mV, technology: ${it.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)}")
            }
            val battery = context.getSystemService(BatteryManager::class.java)
            fun property(name: String, id: Int) = text.println("$name: ${battery.getLongProperty(id)}")
            property("charge counter (uAh)", BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            property("current now (uA)", BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            property("current average (uA)", BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            property("capacity (%)", BatteryManager.BATTERY_PROPERTY_CAPACITY)
            property("energy counter (nWh)", BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
        }

        private fun power() = part("power") {
            val power = context.getSystemService(PowerManager::class.java)
            text.println("power save mode: ${power.isPowerSaveMode}, interactive: ${power.isInteractive}, device idle: ${power.isDeviceIdleMode}")
            text.println("ignoring battery optimizations: ${power.isIgnoringBatteryOptimizations(packageName)}")
            if (Build.VERSION.SDK_INT >= 29) text.println("thermal status: ${power.currentThermalStatus}")
            if (Build.VERSION.SDK_INT >= 28) {
                val bucket = context.getSystemService(UsageStatsManager::class.java).appStandbyBucket
                text.println("standby bucket: $bucket (${standbyBucketName(bucket)})")
            }
        }

        private fun process() = part("process") {
            text.println("pid: ${Process.myPid()}, uid: ${Process.myUid()}")
            text.println("cpu time of the process: ${Process.getElapsedCpuTime()} ms, uptime of the process: ${SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()} ms")
            val runtime = Runtime.getRuntime()
            text.println("java heap: ${(runtime.totalMemory() - runtime.freeMemory()) / 1024} KB used of ${runtime.maxMemory() / 1024} KB")
            File("/proc/self/status").readLines().filter { it.startsWith("Threads") || it.startsWith("VmRSS") || it.startsWith("VmSwap") }.forEach(text::println)
        }

        private fun exits() = part("how the process was stopped before (newest first)") {
            if (Build.VERSION.SDK_INT < 30) {
                text.println("not available before Android 11")
                return@part
            }
            val manager = context.getSystemService(ActivityManager::class.java)
            manager.getHistoricalProcessExitReasons(packageName, 0, 10).forEach {
                text.println("${time(it.timestamp)} pid ${it.pid}: reason ${it.reason} (${exitReasonName(it.reason)}), status ${it.status}, importance ${it.importance}, ${it.description.orEmpty()}")
            }
        }

        private fun launcher() = part("what the launcher has on") {
            val s = SettingsStore(context).load()
            text.println("battery saving: ${s.batterySaving}, 120 Hz request: ${s.highRefreshRate}, theme: ${s.themeStyle}, OLED black: ${s.oledMode}")
            text.println("live content of other apps: ${s.notificationPeek}, pictures: ${s.livePictures}, now playing: ${s.liveNowPlaying}")
            text.println("banner: ${s.toastBanner}, banner over other apps: ${s.overlayBanner}, hide Android's notification: ${s.hideSystemNotifications}, Action Center over other apps: ${s.edgeActionCenter}")
            text.println("sync: ${s.syncEnabled}, kids corner: ${s.kidsMode}")
            text.println("notification access: ${NotificationManagerCompat.getEnabledListenerPackages(context).contains(packageName)}, screen lock service: ${LockAccessibilityService.isEnabled(context)}")
            text.println("display over other apps: ${Settings.canDrawOverlays(context)}")
            if (Build.VERSION.SDK_INT >= 31) text.println("exact alarms: ${context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()}")
        }

        /** Runs a command and copies what it prints (its errors too) into the report, as it comes. A command that hangs is stopped. */
        private fun command(title: String, vararg command: String) {
            text.println("\n===== $title =====")
            text.flush()
            runCatching {
                val process = ProcessBuilder(*command).redirectErrorStream(true).start()
                val guard = CoroutineScope(Dispatchers.Default).launch {
                    delay(COMMAND_TIMEOUT_MS)
                    process.destroyForcibly()
                }
                process.inputStream.use { it.copyTo(raw) }
                guard.cancel()
                process.waitFor()
            }.onFailure { text.println("(could not run: ${it.javaClass.simpleName}: ${it.message})") }
        }
    }

    private const val COMMAND_TIMEOUT_MS = 120_000L

    private fun standbyBucketName(bucket: Int) = when (bucket) {
        10 -> "active"
        20 -> "working set"
        30 -> "frequent"
        40 -> "rare"
        45 -> "restricted"
        50 -> "never"
        else -> "other"
    }

    private fun exitReasonName(reason: Int) = when (reason) {
        1 -> "exit self"
        2 -> "signaled"
        3 -> "low memory"
        4 -> "crash"
        5 -> "native crash"
        6 -> "ANR"
        7 -> "initialization failure"
        8 -> "permission change"
        9 -> "excessive resource usage"
        10 -> "user requested"
        11 -> "user stopped"
        12 -> "dependency died"
        13 -> "other"
        14 -> "freezer"
        15 -> "package state change"
        16 -> "package updated"
        else -> "unknown"
    }
}
