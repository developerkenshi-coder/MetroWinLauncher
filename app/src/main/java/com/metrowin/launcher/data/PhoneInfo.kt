package com.metrowin.launcher.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import java.util.Locale

/** What the About page says about the phone itself, read once when the page opens. Nothing here leaves the phone. */
@Immutable
data class PhoneInfo(
    val model: String,
    val android: String,
    val securityPatch: String,
    val ramBytes: Long,
    val storageTotalBytes: Long,
    val storageFreeBytes: Long,
    val uptimeMillis: Long,
    val screen: String,
    val abi: String,
) {
    companion object {
        fun read(context: Context): PhoneInfo {
            val memory = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(it) }
            val stat = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
            val metrics = context.resources.displayMetrics
            return PhoneInfo(
                model = listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.joinToString(" ").trim(),
                android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                securityPatch = Build.VERSION.SECURITY_PATCH.orEmpty(),
                ramBytes = memory.totalMem,
                storageTotalBytes = stat?.totalBytes ?: 0L,
                storageFreeBytes = stat?.availableBytes ?: 0L,
                uptimeMillis = SystemClock.elapsedRealtime(),
                screen = "${metrics.widthPixels} × ${metrics.heightPixels} · ${metrics.densityDpi} dpi",
                abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            )
        }

        /** "7.6 GB", "512 MB": a size as a person reads it (powers of 1000, like the storage of the phone says). */
        fun size(bytes: Long): String = when {
            bytes <= 0L -> "—"
            bytes >= 1_000_000_000L -> String.format(Locale.ROOT, "%.1f GB", bytes / 1e9)
            else -> "${bytes / 1_000_000L} MB"
        }

        /** "3 d 4 h", "5 h 12 min", "7 min": how long the phone has been on. */
        fun uptime(millis: Long): String {
            val minutes = (millis / 60_000L).coerceAtLeast(0L)
            val days = minutes / (24 * 60)
            val hours = minutes / 60 % 24
            val rest = minutes % 60
            return when {
                days > 0 -> "$days d $hours h"
                hours > 0 -> "$hours h ${rest.toString().padStart(2, '0')} min"
                else -> "$rest min"
            }
        }
    }
}
