package com.metrowin.launcher.data

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.core.content.pm.PackageInfoCompat
import kotlin.math.roundToInt

/** Facts about the app and the phone, for the about page. */
@Immutable
data class AboutInfo(
    val versionName: String,
    val versionCode: Long,
    val androidRelease: String,
    val sdk: Int,
    val device: String,
    /** Refresh rate of the screen right now, in Hz, or 0 when unknown. */
    val refreshHz: Int,
) {
    companion object {
        fun of(context: Context): AboutInfo {
            val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            @Suppress("DEPRECATION")
            val screen = if (Build.VERSION.SDK_INT >= 30) context.display else null
            return AboutInfo(
                versionName = info?.versionName.orEmpty(),
                versionCode = info?.let(PackageInfoCompat::getLongVersionCode) ?: 0L,
                androidRelease = Build.VERSION.RELEASE,
                sdk = Build.VERSION.SDK_INT,
                device = listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.joinToString(" ")
                    .replaceFirstChar { it.uppercase() },
                refreshHz = screen?.refreshRate?.roundToInt() ?: 0,
            )
        }
    }
}
