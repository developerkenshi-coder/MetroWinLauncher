package com.metrowin.launcher.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import java.io.File
import java.net.URI
import java.time.LocalDate
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/** What the tiles that are not read from the usual live sources show: steps, the place, the latest of a site, the last message of a person. */
@Immutable
data class TileExtras(
    /** The steps taken today, or null while they are not known (no permission, no sensor). */
    val steps: Int? = null,
    /** The street where the phone was last found. */
    val place: String? = null,
    /** The newest headline of each pinned site, by the id of its tile. */
    val sites: Map<String, NewsItem> = emptyMap(),
    /** The latest message of each pinned person, by the id of the tile. */
    val messages: Map<String, String> = emptyMap(),
    /** How many cards the wallet holds and how many recordings there are. */
    val cards: Int = 0,
    val recordings: Int = 0,
    /** Whether each switch kept on Start is on, by the id of its quick action; a switch that cannot tell is not here. */
    val toggles: Map<String, Boolean> = emptyMap(),
    /** The steps of the days before, by day number (see [Steps.record]), and the car that was parked, if one was. */
    val stepDays: Map<Long, Int> = emptyMap(),
    val parked: ParkedCar? = null,
    /** What the Apps, Battery, Data usage and Memory tiles show; null while it is not known. [usageAllowed] says whether the usage access is given. */
    val apps: AppsStats? = null,
    val battery: BatteryInfo? = null,
    val dataUsage: DataUsage? = null,
    val memory: MemoryStats? = null,
    val usageAllowed: Boolean = false,
)

/** The steps of a day, from the counter of the sensor, which only goes up since the phone started. */
object Steps {
    const val GOAL = 10_000

    /** The steps taken on [day], and the counter value that day began at: it is kept for the next reading. */
    fun today(counter: Float, day: Long, savedDay: Long, savedBase: Float): Pair<Int, Float> {
        // A new day starts counting from the sensor as it stands; a counter lower than the base means the phone started again.
        val base = when {
            day != savedDay -> counter
            counter < savedBase -> 0f
            else -> savedBase
        }
        return (counter - base).toInt().coerceAtLeast(0) to base
    }

    /** How many days of steps are kept. */
    const val KEEP_DAYS = 60

    /** [history] with [steps] noted for [day]: the most of the day, as a phone that started again counts from zero. Old days drop out. */
    fun record(history: Map<Long, Int>, day: Long, steps: Int, keep: Int = KEEP_DAYS): Map<Long, Int> =
        (history + (day to maxOf(history[day] ?: 0, steps))).filterKeys { it > day - keep }

    /** "day:steps,day:steps", oldest first. */
    fun encodeHistory(history: Map<Long, Int>): String = history.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }

    fun decodeHistory(text: String?): Map<Long, Int> = text.orEmpty().split(',').mapNotNull { part ->
        val bits = part.split(':')
        val day = bits.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        val steps = bits.getOrNull(1)?.toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
        day to steps
    }.toMap()

    /** The days in a row that reached [goal], counting back from [today] (or from yesterday while today is still short of it). */
    fun streak(history: Map<Long, Int>, goal: Int, today: Long): Int {
        if (goal <= 0) return 0
        var day = if ((history[today] ?: 0) >= goal) today else today - 1
        var count = 0
        while ((history[day] ?: 0) >= goal) {
            count++
            day--
        }
        return count
    }

    /** The steps kept for the days before, from the phone. */
    fun history(context: Context): Map<Long, Int> =
        decodeHistory(context.getSharedPreferences("metrowin", Context.MODE_PRIVATE).getString("steps_history", null))

    fun permission(): String? = if (Build.VERSION.SDK_INT >= 29) Manifest.permission.ACTIVITY_RECOGNITION else null

    fun allowed(context: Context): Boolean = permission()?.let { LivePermissions.isGranted(context, it) } ?: true

    /** Blocking-free: the steps of today, or null when the permission or the sensor is missing. */
    suspend fun read(context: Context, day: Long = LocalDate.now().toEpochDay()): Int? {
        if (!allowed(context)) return null
        val manager = context.getSystemService(SensorManager::class.java) ?: return null
        val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        // The sensor reports its value when it is listened to: one report is all that is needed.
        val counter = withTimeoutOrNull(3_000L) {
            suspendCancellableCoroutine<Float> { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        manager.unregisterListener(this)
                        if (cont.isActive) cont.resume(event.values[0])
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { manager.unregisterListener(listener) }
            }
        } ?: return null
        val prefs = context.getSharedPreferences("metrowin", Context.MODE_PRIVATE)
        val (steps, base) = today(counter, day, prefs.getLong("steps_day", -1L), prefs.getFloat("steps_base", 0f))
        prefs.edit {
            putLong("steps_day", day)
            putFloat("steps_base", base)
            putString("steps_history", encodeHistory(record(decodeHistory(prefs.getString("steps_history", null)), day, steps)))
        }
        return steps
    }
}

/** The voice recordings, kept in the storage of the app, one file each. */
class Recordings(context: Context) {
    private val dir = File(context.filesDir, "recordings").apply { mkdirs() }

    fun list(): List<File> = dir.listFiles { f -> f.extension == "m4a" }.orEmpty().sortedByDescending { it.name }

    fun newFile(now: java.time.LocalDateTime = java.time.LocalDateTime.now()): File =
        File(dir, "%04d%02d%02d-%02d%02d%02d.m4a".format(now.year, now.monthValue, now.dayOfMonth, now.hour, now.minute, now.second))

    fun delete(file: File) {
        if (file.parentFile == dir) file.delete()
    }

    /**
     * Gives [file] the name [name] after its date ("20261026-183005 Lesson.m4a"), so the list keeps its order. The new file, or null when
     * the file is not one of the recordings, the name is taken or the rename failed. A blank name takes the name away.
     */
    fun rename(file: File, name: String): File? {
        if (file.parentFile != dir) return null
        val stamp = STAMP.find(file.name)?.value ?: return null
        val clean = cleanName(name)
        val target = File(dir, stamp + (if (clean.isEmpty()) "" else " $clean") + ".m4a")
        if (target == file) return file
        if (target.exists()) return null
        return if (file.renameTo(target)) target else null
    }

    companion object {
        private val STAMP = Regex("""\d{8}-\d{6}""")

        /** A name a file can carry: letters, digits and a few marks, one space at a time, at most 40 characters. */
        fun cleanName(raw: String): String = raw.filter { it.isLetterOrDigit() || it in " -_().,'" }.replace(Regex("\\s+"), " ").trim().trimStart('.').trim().take(40)

        /** The name after the date, or empty for a recording that has none. */
        fun nameOf(fileName: String): String = STAMP.find(fileName)?.let { fileName.substring(it.range.last + 1).removeSuffix(".m4a").trim() }.orEmpty()

        /** "2026-10-26 18:30:05" from the name of a file, and " · Lesson" when the recording has a name. */
        fun title(name: String): String {
            val m = Regex("""(\d{4})(\d{2})(\d{2})-(\d{2})(\d{2})(\d{2})""").find(name) ?: return name.removeSuffix(".m4a")
            val (y, mo, d, h, mi, s) = m.destructured
            val given = nameOf(name)
            return "$y-$mo-$d $h:$mi:$s" + if (given.isEmpty()) "" else " · $given"
        }
    }
}

/** A card of the wallet: a loyalty card or a ticket, drawn as the barcode [code] says. */
@Immutable
data class WalletCard(val id: String, val name: String, val code: String)

class WalletStore(context: Context) {
    private val prefs = context.getSharedPreferences("metrowin", Context.MODE_PRIVATE)

    fun load(): List<WalletCard> = decode(prefs.getString(KEY, null))

    fun save(cards: List<WalletCard>) = prefs.edit { putString(KEY, encode(cards)) }

    companion object {
        private const val KEY = "wallet"
        const val MAX_CARDS = 40
        const val MAX_CODE = 48

        fun encode(cards: List<WalletCard>): String = JSONArray().apply {
            cards.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("code", it.code)) }
        }.toString()

        fun decode(json: String?): List<WalletCard> = runCatching {
            val array = JSONArray(json ?: return emptyList())
            (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                val code = o.optString("code")
                if (Code128.valid(code)) WalletCard(o.optString("id"), o.optString("name"), code) else null
            }
        }.getOrDefault(emptyList())
    }
}

/** Code 128, set B: the bars of a text of printable ASCII, which the scanners of shops and tickets read. */
object Code128 {
    private val PATTERNS = listOf(
        "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212", "221213", "221312", "231212",
        "112232", "122132", "122231", "113222", "123122", "123221", "223211", "221132", "221231", "213212", "223112", "312131",
        "311222", "321122", "321221", "312212", "322112", "322211", "212123", "212321", "232121", "111323", "131123", "131321",
        "112313", "132113", "132311", "211313", "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121",
        "313121", "211331", "231131", "213113", "213311", "213131", "311123", "311321", "331121", "312113", "312311", "332111",
        "314111", "221411", "431111", "111224", "111422", "121124", "121421", "141122", "141221", "112214", "112412", "122114",
        "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111", "111242", "121142", "121241", "114212",
        "124112", "124211", "411212", "421112", "421211", "212141", "214121", "412121", "111143", "111341", "131141", "114113",
        "114311", "411113", "411311", "113141", "114131", "311141", "411131", "211412", "211214", "211232", "2331112",
    )
    private const val START_B = 104
    private const val STOP = 106

    internal val patterns: List<String> get() = PATTERNS

    fun valid(text: String) = text.isNotEmpty() && text.length <= WalletStore.MAX_CODE && text.all { it.code in 32..126 }

    /** The widths of the bars and spaces, starting with a bar, or an empty list when [text] cannot be drawn. */
    fun widths(text: String): List<Int> {
        if (!valid(text)) return emptyList()
        val values = text.map { it.code - 32 }
        val check = (START_B + values.withIndex().sumOf { (i, v) -> v * (i + 1) }) % 103
        return (listOf(START_B) + values + check + STOP).flatMap { PATTERNS[it].map(Char::digitToInt) }
    }
}

/** The place the phone is at, said as a street and as numbers. */
@Immutable
data class WhereAmI(val latitude: Double, val longitude: Double, val accuracy: Float, val address: String?)

object PlaceFinder {
    fun allowed(context: Context) = LivePermissions.isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
        LivePermissions.isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** The position of the phone and the street it is in; null with no permission, no position or no way to tell. Blocking for the street. */
    @SuppressLint("MissingPermission")
    suspend fun find(context: Context): WhereAmI? {
        if (!allowed(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val location = locate(manager) ?: return null
        val address = runCatching {
            @Suppress("DEPRECATION") // the listener form needs Android 13; this one is what every version supports
            Geocoder(context, Locale.getDefault()).getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
                ?.let { a -> (0..a.maxAddressLineIndex).mapNotNull(a::getAddressLine).joinToString(", ").ifBlank { null } }
        }.getOrNull()
        return WhereAmI(location.latitude, location.longitude, location.accuracy, address)
    }

    /** A position under a minute old, else a new one (waited for at most 8 seconds), else the last one known if it is less than a day old. */
    @SuppressLint("MissingPermission")
    private suspend fun locate(manager: LocationManager): Location? {
        val providers = listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        val known = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        val age = known?.let { System.currentTimeMillis() - it.time } ?: Long.MAX_VALUE
        if (known != null && age < 60_000L) return known
        val provider = providers.firstOrNull()
        val fresh = if (provider == null || Build.VERSION.SDK_INT < 30) null else withTimeoutOrNull(8_000L) {
            suspendCancellableCoroutine<Location?> { cont ->
                val cancel = android.os.CancellationSignal()
                cont.invokeOnCancellation { cancel.cancel() }
                manager.getCurrentLocation(provider, cancel, { it.run() }) { cont.resume(it) }
            }
        }
        return fresh ?: known?.takeIf { age < 24 * 60 * 60_000L }
    }

    fun mapsUri(w: WhereAmI) = "geo:${w.latitude},${w.longitude}?q=${w.latitude},${w.longitude}"

    fun shareText(w: WhereAmI) = listOfNotNull(w.address, "https://maps.google.com/?q=${w.latitude},${w.longitude}").joinToString("\n")
}

/** The newest headline of a web page: its feed, found from the page itself, read through [SafeHttp] on the host of the page only. */
object SiteHeadlines {
    private val LINK = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val FEED_TYPE = Regex("""type\s*=\s*["']application/(rss|atom)\+xml["']""", RegexOption.IGNORE_CASE)
    private val HREF = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    /** The addresses of the feeds [html] announces, resolved against [base], in the order the page lists them. */
    fun feedLinks(html: String, base: String): List<String> = LINK.findAll(html).map { it.value }
        .filter { it.contains("alternate", ignoreCase = true) && FEED_TYPE.containsMatchIn(it) }
        .mapNotNull { tag -> HREF.find(tag)?.groupValues?.get(1) }
        .mapNotNull { href -> runCatching { URI(base).resolve(href.replace("&amp;", "&")).toString() }.getOrNull() }
        .toList()

    /** The newest headline of the site at [address], or null (no feed, no connection, not HTTPS). Blocking. */
    fun latest(address: String): NewsItem? {
        val host = runCatching { URI(address).host }.getOrNull()?.lowercase(Locale.ROOT) ?: return null
        val hosts = setOf(host, if (host.startsWith("www.")) host.removePrefix("www.") else "www.$host")
        val secure = address.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
        val page = SafeHttp.get(secure, hosts) ?: return null
        val direct = Feeds.parse(page)
        val parsed = direct ?: feedLinks(page, secure).firstNotNullOfOrNull { link ->
            SafeHttp.get(link, hosts)?.let(Feeds::parse)
        }
        return parsed?.items?.maxByOrNull { it.publishedAt }
    }
}
