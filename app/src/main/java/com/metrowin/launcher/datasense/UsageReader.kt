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
    val plugged: String,
    val celsius: Float,
    val millivolts: Int,
    val health: Int,
    val technology: String,
    val microAmps: Long?,
    val microAmpHours: Long?,
)

data class NetworkReading(
    val transport: String,
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

    private fun label(uid: Int): Pair<String, String?> {
        when (uid) {
            NetworkStats.Bucket.UID_REMOVED -> return "Removed apps" to null
            NetworkStats.Bucket.UID_TETHERING -> return "Hotspot" to null
            Process.SYSTEM_UID -> return "Android system" to null
        }
        val pkg = runCatching { pm.getPackagesForUid(uid)?.firstOrNull() }.getOrNull() ?: return "uid $uid" to null
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

    /** The bytes of the apps between [from] and [to], the biggest first. */
    fun apps(from: Long, to: Long, limit: Int = 60): List<AppData> {
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

    /** The apps that take most room on the phone. Needs the usage access; empty without it. */
    fun appSizes(limit: Int = 20): List<AppSize> {
        val storage = context.getSystemService(StorageStatsManager::class.java) ?: return emptyList()
        val user = Process.myUserHandle()
        return runCatching {
            pm.getInstalledApplications(0).mapNotNull { info ->
                runCatching {
                    val s = storage.queryStatsForPackage(StorageManager.UUID_DEFAULT, info.packageName, user)
                    AppSize(pm.getApplicationLabel(info).toString(), info.packageName, s.appBytes, s.dataBytes, s.cacheBytes)
                }.getOrNull()
            }.sortedByDescending { it.total }.take(limit)
        }.getOrDefault(emptyList())
    }

    /** The time each app was on screen between [from] and [to], the longest first. */
    fun screenTime(from: Long, to: Long, limit: Int = 30): List<AppTime> = runCatching {
        val manager = context.getSystemService(UsageStatsManager::class.java)
        manager.queryAndAggregateUsageStats(from, to).values.filter { it.totalTimeInForeground > 0 }.mapNotNull { u ->
            val name = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(u.packageName, 0)).toString() }.getOrNull() ?: return@mapNotNull null
            AppTime(name, u.packageName, u.totalTimeInForeground, u.lastTimeUsed)
        }.sortedByDescending { it.millis }.take(limit)
    }.getOrDefault(emptyList())

    fun battery(): BatteryReading? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val manager = context.getSystemService(BatteryManager::class.java)
        fun property(id: Int) = runCatching { manager.getLongProperty(id) }.getOrNull()?.takeIf { it != Long.MIN_VALUE && it != 0L }
        return BatteryReading(
            percent = if (level < 0) 0 else level * 100 / scale,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            plugged = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                else -> ""
            },
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
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
                else -> "?"
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
