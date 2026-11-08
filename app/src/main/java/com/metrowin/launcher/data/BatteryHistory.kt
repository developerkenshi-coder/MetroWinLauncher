package com.metrowin.launcher.data

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.edit
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.abs

/** One reading of the battery. */
class BatterySample(
    val time: Long,
    val level: Int,
    /** Tenths of a degree Celsius. */
    val tenths: Int,
    val millivolts: Int,
    val microAmps: Long,
    val microAmpHours: Long,
    val charging: Boolean,
    val cycles: Int,
) {
    fun line() = "$time,$level,$tenths,$millivolts,$microAmps,$microAmpHours,${if (charging) 1 else 0},$cycles"

    /** The capacity this reading implies (mAh), or null when the phone gives no charge counter or the level is too low to trust. */
    val capacityMah: Float? get() = if (level >= MIN_LEVEL_FOR_CAPACITY && microAmpHours > 0) microAmpHours * 100f / level / 1000f else null

    companion object {
        private const val MIN_LEVEL_FOR_CAPACITY = 30

        fun parse(line: String): BatterySample? {
            val p = line.split(',')
            if (p.size < 8) return null
            return runCatching {
                BatterySample(p[0].toLong(), p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4].toLong(), p[5].toLong(), p[6] == "1", p[7].toInt())
            }.getOrNull()
        }
    }
}

/** What the readings say, for the page of the battery doctor. Null where the readings do not say. */
class BatterySummary(
    val drainPerHour: Float?,
    val chargePerHour: Float?,
    val hoursOnFull: Float?,
    val peakWatts: Float?,
    val charges: Int,
    val tempMax: Float?,
    val tempAvg: Float?,
    val capacityMah: Float?,
)

/**
 * The history of the battery: a reading every 15 minutes (a job of Android, planned while the user keeps it on) and one whenever the doctor
 * opens. Plain lines in one file of the launcher, 30 days of them; nothing leaves the phone.
 */
object BatteryHistory {
    private const val FILE = "battery_history.csv"
    private const val JOB_ID = 7305
    private const val KEEP_MS = 30L * 24 * 3_600_000
    private const val PRUNE_BYTES = 150_000L
    private const val GAP_MS = 3L * 3_600_000
    private const val TAIL_BYTES = 1024
    private const val ON = "history"

    private fun file(context: Context) = File(context.filesDir, FILE)

    private fun prefs(context: Context) = Prefs.open(context, Prefs.SHIZUKU)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(ON, true)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit { putBoolean(ON, on) }
        schedule(context)
    }

    /** Plans the job while the history is on, takes it away when it is off. Asking again when it is planned already changes nothing. */
    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (!enabled(context)) {
            scheduler.cancel(JOB_ID)
            return
        }
        if (scheduler.getPendingJob(JOB_ID) != null) return
        runCatching {
            scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, BatteryLogJob::class.java)).setPeriodic(15 * 60_000L).setPersisted(true).build())
        }
    }

    /** The battery now, from the sticky broadcast and the battery service; null when the phone gives no level. */
    fun now(context: Context): BatterySample? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return null
        val manager = context.getSystemService(BatteryManager::class.java)
        fun property(id: Int) = runCatching { manager.getLongProperty(id) }.getOrNull()?.takeIf { it != Long.MIN_VALUE } ?: 0L
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return BatterySample(
            time = System.currentTimeMillis(),
            level = level * 100 / scale,
            tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0),
            millivolts = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0),
            microAmps = property(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            microAmpHours = property(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            cycles = if (Build.VERSION.SDK_INT >= 34) intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, 0) else 0,
        )
    }

    /** Writes a reading unless the last one is newer than [minGapMs]. Blocking: off the main thread. */
    @Synchronized
    fun record(context: Context, minGapMs: Long = 0L) {
        val sample = now(context) ?: return
        val f = file(context)
        val last = lastSample(f)
        if (last != null && sample.time - last.time < minGapMs) return
        f.appendText(sample.line() + "\n")
        if (f.length() > PRUNE_BYTES) f.writeText(readAll(context).filter { it.time >= sample.time - KEEP_MS }.joinToString("") { it.line() + "\n" })
    }

    /** The last reading of [f] from its last bytes only (a line is about 60 bytes): the file is not read whole for each new reading. */
    internal fun lastSample(f: File): BatterySample? {
        if (!f.exists() || f.length() == 0L) return null
        return java.io.RandomAccessFile(f, "r").use { raf ->
            val size = minOf(raf.length(), TAIL_BYTES.toLong()).toInt()
            raf.seek(raf.length() - size)
            val tail = ByteArray(size).also { raf.readFully(it) }
            // The first line of a tail cut in the middle of a line is half a line: it is dropped, and there are always whole lines after it.
            String(tail, Charsets.UTF_8).lines().let { if (size < raf.length()) it.drop(1) else it }.mapNotNull(BatterySample::parse).lastOrNull()
        }
    }

    @Synchronized
    fun readAll(context: Context): List<BatterySample> {
        val f = file(context)
        return if (f.exists()) f.readLines().mapNotNull(BatterySample::parse) else emptyList()
    }

    fun read(context: Context, sinceMs: Long): List<BatterySample> = readAll(context).filter { it.time >= sinceMs }

    @Synchronized
    fun clear(context: Context) {
        file(context).delete()
    }

    /** Pairs of neighbouring readings less than three hours apart: a gap (phone off, history off) is not a slope. */
    private fun pairs(samples: List<BatterySample>) = samples.zipWithNext().filter { (a, b) -> b.time - a.time in 1..GAP_MS }

    fun summary(samples: List<BatterySample>): BatterySummary {
        fun rate(charging: Boolean, sign: Int): Float? {
            val steps = pairs(samples).filter { (a, b) -> a.charging == charging && b.charging == charging && (b.level - a.level) * sign >= 0 }
            val hours = steps.sumOf { (a, b) -> b.time - a.time } / 3_600_000f
            val change = steps.sumOf { (a, b) -> abs(b.level - a.level) }
            return if (hours >= 0.5f && change > 0) change / hours else null
        }
        val drain = rate(false, -1)
        val temps = samples.map { it.tenths / 10f }
        val capacities = samples.mapNotNull { it.capacityMah }.sorted()
        return BatterySummary(
            drainPerHour = drain,
            chargePerHour = rate(true, 1),
            hoursOnFull = drain?.let { 100f / it },
            peakWatts = samples.filter { it.charging }.maxOfOrNull { abs(it.microAmps) / 1000f * it.millivolts / 1_000_000f }?.takeIf { it > 0f },
            charges = samples.zipWithNext().count { (a, b) -> !a.charging && b.charging },
            tempMax = temps.maxOrNull(),
            tempAvg = temps.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            capacityMah = capacities.takeIf { it.isNotEmpty() }?.get(capacities.size / 2),
        )
    }

    /** One value per day (the middle one of the day's readings of the capacity), oldest first. */
    fun capacityByDay(samples: List<BatterySample>): List<Pair<Long, Float>> =
        samples.filter { it.capacityMah != null }.groupBy { it.time / 86_400_000L }.toSortedMap().map { (day, list) ->
            val values = list.mapNotNull { it.capacityMah }.sorted()
            day * 86_400_000L + 43_200_000L to values[values.size / 2]
        }

    /** Where the line of a chart breaks: neighbouring readings further apart than this are not joined. */
    const val GAP = GAP_MS
}

/** Takes the reading when Android calls it, every 15 minutes. */
class BatteryLogJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        thread(name = "battery-log") {
            runCatching { BatteryHistory.record(applicationContext) }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters) = true
}
