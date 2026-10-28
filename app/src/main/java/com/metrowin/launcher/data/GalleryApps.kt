package com.metrowin.launcher.data

import android.content.Context
import android.content.Intent

/** The gallery apps of the phone, to choose the one the Photos tile opens. */
object GalleryApps {
    /** Galleries that do not always announce themselves with the gallery category. */
    private val known = listOf(
        "com.google.android.apps.photos", "com.sec.android.gallery3d", "com.miui.gallery", "com.vivo.gallery", "com.android.gallery3d",
        "com.coloros.gallery3d", "com.oneplus.gallery", "com.huawei.photos", "com.motorola.gallery", "com.nothing.gallery",
        "org.fossify.gallery", "com.simplemobiletools.gallery.pro", "com.google.android.apps.nbu.files",
    )

    /** The installed galleries (package and name), by name; the launcher itself is never one. */
    fun installed(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val byCategory = runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY), 0).map { it.activityInfo.packageName }
        }.getOrDefault(emptyList())
        return (byCategory + known)
            .distinct()
            .filter { it != context.packageName && pm.getLaunchIntentForPackage(it) != null }
            .mapNotNull { pkg -> runCatching { IconPackInfo(pkg, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()) }.getOrNull() }
            .sortedBy { it.label.lowercase() }
    }
}
