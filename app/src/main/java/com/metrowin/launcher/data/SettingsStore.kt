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
) {
    val hasStartBackground: Boolean get() = backgroundRevision > 0

    /** Columns of small tiles on the Start grid. */
    val columns: Int get() = if (moreTiles) 6 else 4

    companion object {
        const val DEFAULT_ACCENT = 4
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("metrowin", Context.MODE_PRIVATE)

    fun load() = LauncherSettings(
        darkTheme = prefs.getBoolean(KEY_DARK, true),
        accentIndex = prefs.getInt(KEY_ACCENT, LauncherSettings.DEFAULT_ACCENT),
        moreTiles = prefs.getBoolean(KEY_MORE_TILES, false),
        backgroundRevision = prefs.getInt(KEY_BACKGROUND, 0),
    )

    fun save(settings: LauncherSettings) = prefs.edit {
        putBoolean(KEY_DARK, settings.darkTheme)
        putInt(KEY_ACCENT, settings.accentIndex)
        putBoolean(KEY_MORE_TILES, settings.moreTiles)
        putInt(KEY_BACKGROUND, settings.backgroundRevision)
    }

    private companion object {
        const val KEY_DARK = "dark_theme"
        const val KEY_ACCENT = "accent"
        const val KEY_MORE_TILES = "more_tiles"
        const val KEY_BACKGROUND = "background_revision"
    }
}
