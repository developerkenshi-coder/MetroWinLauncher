package com.metrowin.launcher.datasense

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import android.telephony.TelephonyManager
import com.metrowin.launcher.R
import com.metrowin.launcher.data.BatteryInfo

/** Bytes of one app (one user id) in a period, by network and by whether the app was in front. */
data class AppData(
    val uid: Int,
    val label: String,
    val packageName: String?,
    val wifi: Long,
    val mobile: Long,
    val foreground: Long,
    val background: Long,
) {
    val total: Long get() = wifi + mobile
    val tethering: Boolean get() = uid == NetworkStats.Bucket.UID_TETHERING
}

/** The size an app takes on the phone: the app itself, what it keeps and its cache. */
data class AppSize(val label: String, val packageName: String, val app: Long, val data: Long, val cache: Long) {
    val total: Long get() = app + data + cache
}

/** How long an app was on screen in a period. */
data class AppTime(val label: String, val packageName: String, val millis: Long, val lastUsed: Long)

data class BatteryReading(
    val percent: Int,
    val charging: Boolean,
    /** One of the BatteryManager.BATTERY_PLUGGED_* values, 0 when not plugged. */
    val plugged: Int,
    /** Minutes left on the battery or to a full one, as Android predicts them; null where it does not. */
    val minutesLeft: Int?,
    val minutesToFull: Int?,
    val celsius: Float,
    val millivolts: Int,
    val health: Int,
    val technology: String,
    val microAmps: Long?,
    val microAmpHours: Long?,
)

enum class Transport { WiFi, Mobile, Ethernet, Bluetooth, Unknown }

data class NetworkReading(
    val transport: Transport,
    val metered: Boolean,
    val downKbps: Int,
    val upKbps: Int,
    val operator: String,
    val roaming: Boolean,
    val vpn: Boolean,
    val validated: Boolean,
)

/** Everything the data page reads from Android (usage access needed for most). Blocking: call it off the main thread. */
class UsageReader(private val context: Context) {
    private val pm = context.packageManager

    companion object {
        /** How many apps each list shows. */
        const val TOP_DATA = 60
        const val TOP_TIME = 30
        const val TOP_SIZES = 20
    }

    private fun label(uid: Int): Pair<String, String?> {
        when (uid) {
            NetworkStats.Bucket.UID_REMOVED -> return context.getString(R.string.dsx_removed) to null
            NetworkStats.Bucket.UID_TETHERING -> return context.getString(R.string.dsx_hotspot) to null
            Process.SYSTEM_UID -> return context.getString(R.string.dsx_system) to null
        }
        val pkg = runCatching { pm.getPackagesForUid(uid)?.firstOrNull() }.getOrNull() ?: return context.getString(R.string.dsx_uid, uid) to null
        val name = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        return name to pkg
    }

    /** The bytes used by everything on the phone between [from] and [to]: Wi-Fi first, then mobile (null where Android cannot say). */
    fun device(from: Long, to: Long): Pair<Long, Long?> {
        val stats = context.getSystemService(NetworkStatsManager::class.java) ?: return 0L to null
        fun sum(type: Int): Long? = runCatching {
            if (type == ConnectivityManager.TYPE_MOBILE && Build.VERSION.SDK_INT < 29) return@runCatching null
            stats.querySummaryForDevice(type, null, from, to).let { it.rxBytes + it.txBytes }
        }.getOrNull()
        return (sum(ConnectivityManager.TYPE_WIFI) ?: 0L) to sum(ConnectivityManager.TYPE_MOBILE)
    }

    /** What Android counted for the whole phone on [type], in the stretches it keeps (about an hour each), from [from] to [to]. Empty when it cannot say. */
    private fun spans(type: Int, from: Long, to: Long): List<Span>? {
        if (type == ConnectivityManager.TYPE_MOBILE && Build.VERSION.SDK_INT < 29) return null
        val stats = context.getSystemService(NetworkStatsManager::class.java) ?: return null
        return runCatching {
            stats.queryDetails(type, null, from, to).use { result ->
                val bucket = NetworkStats.Bucket()
                buildList {
                    while (result.hasNextBucket()) {
                        result.getNextBucket(bucket)
                        add(Span(bucket.startTimeStamp, bucket.endTimeStamp, bucket.rxBytes + bucket.txBytes))
                    }
                }
            }
        }.getOrNull()
    }

    /**
     * The bytes of each of [buckets]: Wi-Fi, then mobile (null where Android cannot say). Two queries for all the bars; if Android refuses
     * them, one query for each bar as before.
     */
    fun perBucket(buckets: List<DataBucket>): List<Pair<Long, Long?>> {
        if (buckets.isEmpty()) return emptyList()
        val from = buckets.first().from
        val to = buckets.last().to
        val wifi = spans(ConnectivityManager.TYPE_WIFI, from, to)
        if (wifi == null) return buckets.map { device(it.from, it.to) }
        val mobile = spans(ConnectivityManager.TYPE_MOBILE, from, to)
        val w = DataMath.distribute(wifi, buckets)
        val m = mobile?.let { DataMath.distribute(it, buckets) }
        return buckets.indices.map { w[it] to m?.get(it) }
    }

    /** The bytes of the apps between [from] and [to], the biggest first. */
    fun apps(from: Long, to: Long, limit: Int = TOP_DATA): List<AppData> {
        val stats = context.getSystemService(NetworkStatsManager::class.java) ?: return emptyList()
        class Sum { var wifi = 0L; var mobile = 0L; var fg = 0L; var bg = 0L }
        val byUid = HashMap<Int, Sum>()
        fun collect(type: Int) {
            if (type == ConnectivityManager.TYPE_MOBILE && Build.VERSION.SDK_INT < 29) return
            runCatching {
                stats.querySummary(type, null, from, to).use { result ->
                    val bucket = NetworkStats.Bucket()
                    while (result.hasNextBucket()) {
                        result.getNextBucket(bucket)
                        val bytes = bucket.rxBytes + bucket.txBytes
                        val sum = byUid.getOrPut(bucket.uid) { Sum() }
                        if (type == ConnectivityManager.TYPE_WIFI) sum.wifi += bytes else sum.mobile += bytes
                        if (bucket.state == NetworkStats.Bucket.STATE_FOREGROUND) sum.fg += bytes else sum.bg += bytes
                    }
                }
            }
        }
        collect(ConnectivityManager.TYPE_WIFI)
        collect(ConnectivityManager.TYPE_MOBILE)
        return byUid.entries.filter { it.value.wifi + it.value.mobile > 0 }
            .map { (uid, s) -> label(uid).let { (name, pkg) -> AppData(uid, name, pkg, s.wifi, s.mobile, s.fg, s.bg) } }
            // Apps that share a name (an app and its services) are shown once.
            .groupBy { it.label }
            .map { (_, rows) -> rows.reduce { a, b -> a.copy(wifi = a.wifi + b.wifi, mobile = a.mobile + b.mobile, foreground = a.foreground + b.foreground, background = a.background + b.background) } }
            .sortedByDescending { it.total }
            .take(limit)
    }

    /**
     * The apps that take most room on the phone. Needs the usage access; empty without it. One call to Android for each app, so [active] is
     * asked between them: false (the page was left) stops the work.
     */
    fun appSizes(limit: Int = TOP_SIZES, active: () -> Boolean = { true }): List<AppSize> {
        val storage = context.getSystemService(StorageStatsManager::class.java) ?: return emptyList()
        val user = Process.myUserHandle()
        return runCatching {
            pm.getInstalledApplications(0).mapNotNull { info ->
                if (!active()) return emptyList()
                runCatching {
                    val s = storage.queryStatsForPackage(StorageManager.UUID_DEFAULT, info.packageName, user)
                    AppSize(pm.getApplicationLabel(info).toString(), info.packageName, s.appBytes, s.dataBytes, s.cacheBytes)
                }.getOrNull()
            }.sortedByDescending { it.total }.take(limit)
        }.getOrDefault(emptyList())
    }

    /** The package of the home screen in use (this launcher, most of the time): its time on screen says nothing, so the page can leave it out. */
    fun homePackage(): String? = runCatching {
        pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }.getOrNull()

    /** The time each app was on screen between [from] and [to], the longest first, without the packages in [leaveOut]. */
    fun screenTime(from: Long, to: Long, limit: Int = TOP_TIME, leaveOut: Set<String> = emptySet()): List<AppTime> = runCatching {
        val manager = context.getSystemService(UsageStatsManager::class.java)
        manager.queryAndAggregateUsageStats(from, to).values.filter { it.totalTimeInForeground > 0 && it.packageName !in leaveOut }.mapNotNull { u ->
            val name = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(u.packageName, 0)).toString() }.getOrNull() ?: return@mapNotNull null
            AppTime(name, u.packageName, u.totalTimeInForeground, u.lastTimeUsed)
        }.sortedByDescending { it.millis }.take(limit)
    }.getOrDefault(emptyList())

    fun battery(): BatteryReading? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        // The charge and the minutes come from the same reading as the tile of the battery.
        val info = BatteryInfo.from(intent, context)
        val manager = context.getSystemService(BatteryManager::class.java)
        fun property(id: Int) = runCatching { manager.getLongProperty(id) }.getOrNull()?.takeIf { it != Long.MIN_VALUE && it != 0L }
        return BatteryReading(
            percent = info.percent,
            charging = info.state != com.metrowin.launcher.data.BatteryState.Discharging,
            plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
            minutesLeft = info.minutesLeft,
            minutesToFull = info.minutesToFull,
            celsius = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f,
            millivolts = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0),
            health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN),
            technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY).orEmpty(),
            microAmps = property(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            microAmpHours = property(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
        )
    }

    fun network(): NetworkReading? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork ?: return null) ?: return null
        val phone = context.getSystemService(TelephonyManager::class.java)
        return NetworkReading(
            transport = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.WiFi
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.Mobile
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Transport.Ethernet
                caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> Transport.Bluetooth
                else -> Transport.Unknown
            },
            metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            downKbps = caps.linkDownstreamBandwidthKbps,
            upKbps = caps.linkUpstreamBandwidthKbps,
            operator = runCatching { phone?.networkOperatorName.orEmpty() }.getOrDefault(""),
            roaming = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING),
            vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        )
    }
}
