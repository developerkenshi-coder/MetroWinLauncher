package com.metrowin.launcher.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.edit

/** The "start+theme" settings. */
@Immutable
data class LauncherSettings(
    /** The Windows Phone background: dark (default) or light. */
    val darkTheme: Boolean = true,
    /** Index into the 20 accent colours, 4 is Windows Phone's cyan. */
    val accentIndex: Int = DEFAULT_ACCENT,
    /** Three columns of medium tiles instead of two ("show more tiles"). */
    val moreTiles: Boolean = false,
    /** 0 when Start has no background photo, otherwise bumped for every new photo. */
    val backgroundRevision: Int = 0,
    /** The four buttons on top of the Action Center, as [QuickAction.id]s. */
    val quickActions: List<String> = QuickAction.defaults.map { it.id },
    /** The apps of the Kid's Corner, as [com.metrowin.launcher.model.AppItem.id]s. */
    val kidsApps: Set<String> = emptySet(),
    /** The Kid's Corner is on: Start shows only [kidsApps], until a grown-up leaves it. */
    val kidsMode: Boolean = false,
) {
    val hasStartBackground: Boolean get() = backgroundRevision > 0

    /** Columns of small tiles on the Start grid. */
    val columns: Int get() = if (moreTiles) 6 else DEFAULT_COLUMNS

    companion object {
        const val DEFAULT_ACCENT = 4
        const val DEFAULT_COLUMNS = 4
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("metrowin", Context.MODE_PRIVATE)

    fun load() = LauncherSettings(
        darkTheme = prefs.getBoolean(KEY_DARK, true),
        accentIndex = prefs.getInt(KEY_ACCENT, LauncherSettings.DEFAULT_ACCENT),
        moreTiles = prefs.getBoolean(KEY_MORE_TILES, false),
        backgroundRevision = prefs.getInt(KEY_BACKGROUND, 0),
        quickActions = prefs.getString(KEY_QUICK_ACTIONS, null)?.split(',')
            ?.let { ids -> QuickAction.slots(ids).map { it.id } }
            ?: QuickAction.defaults.map { it.id },
        kidsApps = prefs.getStringSet(KEY_KIDS_APPS, null)?.toSet().orEmpty(),
        kidsMode = prefs.getBoolean(KEY_KIDS_MODE, false),
    )

    fun save(settings: LauncherSettings) = prefs.edit {
        putBoolean(KEY_DARK, settings.darkTheme)
        putInt(KEY_ACCENT, settings.accentIndex)
        putBoolean(KEY_MORE_TILES, settings.moreTiles)
        putInt(KEY_BACKGROUND, settings.backgroundRevision)
        putString(KEY_QUICK_ACTIONS, settings.quickActions.joinToString(","))
        putStringSet(KEY_KIDS_APPS, settings.kidsApps)
        putBoolean(KEY_KIDS_MODE, settings.kidsMode)
    }

    private companion object {
        const val KEY_DARK = "dark_theme"
        const val KEY_ACCENT = "accent"
        const val KEY_MORE_TILES = "more_tiles"
        const val KEY_BACKGROUND = "background_revision"
        const val KEY_QUICK_ACTIONS = "quick_actions"
        const val KEY_KIDS_APPS = "kids_apps"
        const val KEY_KIDS_MODE = "kids_mode"
    }
}
