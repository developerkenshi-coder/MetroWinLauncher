package com.metrowin.launcher.ui.settings

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import com.metrowin.launcher.R
import com.metrowin.launcher.data.AboutInfo
import com.metrowin.launcher.data.PlaceMatch
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.model.AppItem

/** The two pivot pages of the settings home: what the launcher looks and acts like, and the tools around it. */
enum class SettingsGroup(@param:StringRes val title: Int) {
    Launcher(R.string.settings_group_launcher),
    Tools(R.string.settings_group_tools),
}

/**
 * The sections of the settings. Each is a page of its own, listed on the home of the settings under its
 * [group] with its title and a line saying what is in it.
 */
enum class SettingsSection(
    val group: SettingsGroup,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
) {
    StartTheme(SettingsGroup.Launcher, R.string.settings_start_theme, R.string.sec_start_theme_desc),
    Gestures(SettingsGroup.Launcher, R.string.sec_gestures, R.string.sec_gestures_desc),
    Actions(SettingsGroup.Launcher, R.string.settings_actions, R.string.sec_actions_desc),
    LiveTiles(SettingsGroup.Launcher, R.string.sec_live_tiles, R.string.sec_live_tiles_desc),
    Apps(SettingsGroup.Launcher, R.string.sec_apps, R.string.sec_apps_desc),
    Backup(SettingsGroup.Tools, R.string.sec_backup, R.string.sec_backup_desc),
    Kids(SettingsGroup.Tools, R.string.kids_title, R.string.sec_kids_desc),
    About(SettingsGroup.Tools, R.string.sec_about, R.string.sec_about_desc),
}

/**
 * Everything the settings pages read and call. What a page needs beyond the settings themselves comes in
 * here with a default, so the pages can be shown (and tested) one at a time.
 */
@Immutable
class SettingsEnv(
    val settings: LauncherSettings,
    val onChange: ((LauncherSettings) -> LauncherSettings) -> Unit,
    val startBackground: ImageBitmap? = null,
    val onPickBackground: (Uri) -> Unit = {},
    val onClearBackground: () -> Unit = {},
    /** The accent that suits the Start photo, when there is one. */
    val autoAccentIndex: Int? = null,
    val notificationAccess: Boolean = false,
    val onManageNotificationAccess: () -> Unit = {},
    /** Every app, including the hidden ones and the launcher's own entries. */
    val apps: List<AppItem> = emptyList(),
    val onStartKidsCorner: () -> Unit = {},
    val about: AboutInfo = AboutInfo("", 0L, "", 0, "", 0),
    /** Writes the layout to a file the user picked. */
    val onExportLayout: (Uri) -> Unit = {},
    /** Reads a layout from a file the user picked. */
    val onImportLayout: (Uri) -> Unit = {},
    /** Looks for cities by name: null when the search could not be made, an empty list when nothing matches. */
    val searchPlaces: suspend (String) -> List<PlaceMatch>? = { emptyList() },
    val onChoosePlace: (PlaceMatch) -> Unit = {},
    val onUseLocation: () -> Unit = {},
)
