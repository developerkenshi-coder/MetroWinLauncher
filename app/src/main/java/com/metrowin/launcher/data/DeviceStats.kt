package com.metrowin.launcher.data

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.runtime.Immutable
import com.metrowin.launcher.model.AppItem
import java.time.ZonedDateTime

/** The "usage access" of Android: one switch, in a screen of its own, that lets the launcher read how the phone is used (apps, data, storage). */
object UsageAccess {
    fun granted(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** The screen where the user gives it, on the page of this app when the phone has one. */
    fun openSettings(context: Context) {
        val page = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(page) }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

/** An app of the weekly top and the time it was on screen. */
@Immutable
data class TopApp(val item: AppItem, val millis: Long)

/** What the Apps tile shows: how many apps the phone has, and the ones used most over the last week (empty without the usage access). */
@Immutable
data class AppsStats(val installed: Int, val top: List<TopApp>)

object AppStats {
    const val TOP = 5
    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    /** The [limit] apps of [items] used longest, from the time each package was on screen; apps not used are left out. */
    fun top(usage: Map<String, Long>, items: List<AppItem>, limit: Int = TOP): List<TopApp> {
        val byPackage = items.filter { !it.pinOnly && it.packageName != null }.groupBy { it.packageName }
        return usage.entries.filter { it.value > 0 }.sortedByDescending { it.value }
            .mapNotNull { (pkg, millis) -> byPackage[pkg]?.firstOrNull()?.let { TopApp(it, millis) } }
            .take(limit)
    }

    fun read(context: Context, items: List<AppItem>, now: Long = System.currentTimeMillis()): AppsStats {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val installed = runCatching { context.packageManager.queryIntentActivities(launcher, 0).map { it.activityInfo.packageName }.distinct().size }.getOrDefault(0)
        val usage = runCatching {
            context.getSystemService(UsageStatsManager::class.java).queryAndAggregateUsageStats(now - WEEK_MS, now)
                .mapValues { it.value.totalTimeInForeground }
        }.getOrDefault(emptyMap())
        return AppsStats(installed, top(usage, items))
    }
}

/** Bytes used over Wi-Fi and mobile data; null where the phone cannot say (mobile data before Android 10, or no usage access). */
@Immutable
data class DataUsage(val wifiToday: Long, val mobileToday: Long?, val wifiMonthly: Long?, val mobileMonthly: Long?)

object DataStats {
    /** The average of the full months that have any data; with none (a new phone) what the current month has so far. */
    fun monthlyAverage(previous: List<Long>, current: Long): Long {
        val used = previous.filter { it > 0 }
        return if (used.isEmpty()) current else used.sum() / used.size
    }

    fun read(context: Context, now: ZonedDateTime = ZonedDateTime.now()): DataUsage? {
        val stats = context.getSystemService(NetworkStatsManager::class.java) ?: return null
        fun total(type: Int, from: ZonedDateTime, to: ZonedDateTime): Long? = runCatching {
            // Mobile data is asked about without a subscriber from Android 10; before, that needs a permission the launcher does not ask for.
            if (type == ConnectivityManager.TYPE_MOBILE && Build.VERSION.SDK_INT < 29) return@runCatching null
            val bucket: NetworkStats.Bucket = stats.querySummaryForDevice(type, null, from.toInstant().toEpochMilli(), to.toInstant().toEpochMilli())
            bucket.rxBytes + bucket.txBytes
        }.getOrNull()

        val dayStart = now.toLocalDate().atStartOfDay(now.zone)
        val monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay(now.zone)
        fun monthly(type: Int): Long? {
            val current = total(type, monthStart, now) ?: return null
            val previous = (1..MONTHS).mapNotNull { total(type, monthStart.minusMonths(it.toLong()), monthStart.minusMonths(it - 1L)) }
            return monthlyAverage(previous, current)
        }

        val wifiToday = total(ConnectivityManager.TYPE_WIFI, dayStart, now) ?: return null
        return DataUsage(
            wifiToday, total(ConnectivityManager.TYPE_MOBILE, dayStart, now),
            monthly(ConnectivityManager.TYPE_WIFI), monthly(ConnectivityManager.TYPE_MOBILE),
        )
    }

    private const val MONTHS = 3
}

/** The memory of the phone: the [storage] reading and what takes part of it (null for what the phone would not say). The rest is [other]. */
@Immutable
data class MemoryStats(val storage: StorageInfo, val apps: Long?, val photos: Long?, val videos: Long?, val music: Long?) {
    val used: Long get() = (storage.totalBytes - storage.freeBytes).coerceAtLeast(0)

    /** What is used and none of the other four: the documents, downloads, the system. */
    val other: Long get() = (used - listOfNotNull(apps, photos, videos, music).sum()).coerceAtLeast(0)

    companion object {
        fun read(context: Context): MemoryStats? {
            val storage = StorageInfo.read() ?: return null
            // The size of all the apps (themselves, their data and their caches) is for those with the usage access.
            val apps = runCatching {
                context.getSystemService(StorageStatsManager::class.java).queryStatsForUser(StorageManager.UUID_DEFAULT, Process.myUserHandle())
                    .let { it.appBytes + it.dataBytes + it.cacheBytes }
            }.getOrNull()
            fun media(uri: Uri, permission: String?): Long? {
                val allowed = permission == null || LivePermissions.isGranted(context, if (Build.VERSION.SDK_INT >= 33) permission else Manifest.permission.READ_EXTERNAL_STORAGE)
                if (!allowed) return null
                return runCatching {
                    context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { cursor ->
                        var sum = 0L
                        while (cursor.moveToNext()) sum += cursor.getLong(0)
                        sum
                    }
                }.getOrNull()
            }
            return MemoryStats(
                storage, apps,
                photos = media(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Manifest.permission.READ_MEDIA_IMAGES),
                videos = media(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, Manifest.permission.READ_MEDIA_VIDEO),
                music = media(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, Manifest.permission.READ_MEDIA_AUDIO),
            )
        }
    }
}
