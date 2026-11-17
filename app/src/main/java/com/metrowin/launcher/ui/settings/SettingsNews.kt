package com.metrowin.launcher.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.theme.LocalAccent

/**
 * The settings that an update brought or changed, marked for a few days after the update (a bar beside the setting, a square on its category).
 * A new install marks nothing: there is nothing new to someone who has seen nothing before.
 */
object SettingsNews {
    /** The ids of [SettingsIndex] the latest builds added or changed. Edit this list with each release: an empty one marks nothing. */
    val ids: Set<String> = setOf("gest.sleepall", "gest.soundvolume", "statusbar.mainsim", "statusbar.roaming", "statusbar.dataSim", "apps.recent")

    const val DAYS = 7
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    /** An update is an install over an older one: the last update came more than a minute after the first install. Marked for [DAYS] days from it. */
    fun active(firstInstall: Long, lastUpdate: Long, now: Long): Boolean =
        lastUpdate - firstInstall > 60_000L && now - lastUpdate in 0 until DAYS * DAY_MS

    /** What to mark now: [ids] during the days after an update, nothing otherwise (or when the phone does not say). */
    fun current(context: Context, now: Long = System.currentTimeMillis()): Set<String> = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (active(info.firstInstallTime, info.lastUpdateTime, now)) ids else emptySet()
    }.getOrDefault(emptySet())

    /** Whether any setting of [category] is marked. */
    fun marks(category: SettingsCategory, marked: Set<String>): Boolean =
        marked.isNotEmpty() && category.pages.any { page -> SettingsIndex.of(page).any { it.id in marked } }
}

/** The ids marked right now (see [SettingsNews]); empty outside the days after an update. */
internal val LocalNewSettings = staticCompositionLocalOf<Set<String>> { emptySet() }

/** A bar of the accent in the margin beside a setting that is marked, said as "new" to a screen reader. */
@Composable
internal fun Modifier.newMark(id: String?): Modifier {
    if (id == null || id !in LocalNewSettings.current) return this
    val accent = LocalAccent.current
    val said = stringResource(R.string.settings_new)
    return drawBehind { drawRect(accent, Offset(-14.dp.toPx(), 0f), Size(3.dp.toPx(), size.height)) }.semantics { stateDescription = said }
}

/** The square of the accent on a category that holds a marked setting. */
@Composable
internal fun NewSquare(modifier: Modifier = Modifier) {
    val said = stringResource(R.string.settings_new)
    Box(modifier.size(10.dp).background(LocalAccent.current).semantics { stateDescription = said })
}
