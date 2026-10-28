package com.metrowin.launcher.ui.settings

import com.metrowin.launcher.data.SavedLayout
import com.metrowin.launcher.data.SavedApp
import com.metrowin.launcher.data.SavedCity
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import com.metrowin.launcher.R
import com.metrowin.launcher.data.AboutInfo
import com.metrowin.launcher.data.IconPackInfo
import com.metrowin.launcher.data.PlaceMatch
import com.metrowin.launcher.data.ThirdPartyTile
import com.metrowin.launcher.data.ProfileInfo
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.notifications.LiveReportLine

/**
 * The pivot pages of the settings home, in the order of what you reach for: how the launcher looks, how it behaves and reacts to
 * you, what it does with your apps, and the tools around it.
 */
enum class SettingsGroup(@param:StringRes val title: Int) {
    Look(R.string.settings_group_look),
    Behavior(R.string.settings_group_behavior),
    Apps(R.string.settings_group_apps),
    Tools(R.string.settings_group_tools),
}

/**
 * The sections of the settings. Each is a page of its own, listed on the home of the settings under its
 * [group] with its title and a line saying what is in it. What is on each page is listed in the [SettingsIndex].
 */
enum class SettingsSection(
    val group: SettingsGroup,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
) {
    // look: what you see
    StartTheme(SettingsGroup.Look, R.string.settings_start_theme, R.string.sec_start_theme_desc),
    Start(SettingsGroup.Look, R.string.sec_start, R.string.sec_start_desc),
    Icons(SettingsGroup.Look, R.string.sec_icons, R.string.sec_icons_desc),
    Accessibility(SettingsGroup.Look, R.string.sec_access, R.string.sec_access_desc),
    Lock(SettingsGroup.Look, R.string.sec_lock, R.string.sec_lock_desc),

    // behavior: how it moves and answers
    Screen(SettingsGroup.Behavior, R.string.sec_screen, R.string.sec_screen_desc),
    Gestures(SettingsGroup.Behavior, R.string.sec_gestures, R.string.sec_gestures_desc),
    Actions(SettingsGroup.Behavior, R.string.settings_actions, R.string.sec_actions_desc),
    Voice(SettingsGroup.Behavior, R.string.sec_voice, R.string.sec_voice_desc),
    Battery(SettingsGroup.Behavior, R.string.sec_battery, R.string.sec_battery_desc),

    // apps: the list, the search, the live content and the profiles they come from
    Apps(SettingsGroup.Apps, R.string.sec_apps, R.string.sec_apps_desc),
    Search(SettingsGroup.Apps, R.string.sec_search, R.string.sec_search_desc),
    LiveTiles(SettingsGroup.Apps, R.string.sec_live_tiles, R.string.sec_live_tiles_desc),
    Profiles(SettingsGroup.Apps, R.string.sec_profiles, R.string.sec_profiles_desc),

    // tools
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
    /** The launcher is sparing the battery now (see PowerPolicy). */
    val powerSaving: Boolean = false,
    val notificationAccess: Boolean = false,
    /** The apps whose tile shows live content now (see LiveCards), for the list of apps that can be switched off. */
    val liveTilePackages: Set<String> = emptySet(),
    /** What the launcher reads from the notifications of each app now (see LiveCards.report). */
    val liveReport: List<LiveReportLine> = emptyList(),
    val onManageNotificationAccess: () -> Unit = {},
    /** Every app, including the hidden ones and the launcher's own entries. */
    val apps: List<AppItem> = emptyList(),
    val onStartKidsCorner: () -> Unit = {},
    /** How many changes to Start can be undone now, and a way to undo the last one. */
    val undoDepth: Int = 0,
    val onUndo: () -> Unit = {},
    /** Keeps the volumes the phone has now as a preset with this name; false when the name is blank. */
    val onSaveVolumeProfile: (String) -> Boolean = { false },
    /** Forgets the recent searches of the search box. */
    val onClearSearches: () -> Unit = {},
    /** Puts Start back as it was at the start (see LauncherViewModel.resetStart). */
    val onResetStart: () -> Unit = {},
    /** The language of the launcher (a tag of AppLanguages, empty for the phone's) and how to change it; null where it cannot be (before Android 13). */
    val language: String = "",
    val onLanguage: ((String) -> Unit)? = null,
    val about: AboutInfo = AboutInfo("", 0L, "", 0, "", 0),
    /** Writes the layout to a file the user picked. */
    val onExportLayout: (Uri) -> Unit = {},
    /** Reads a layout from a file the user picked. */
    val onImportLayout: (Uri) -> Unit = {},
    /** The list of the installed apps: saved to a file, read from one, and the apps of it this phone does not have. */
    /** The copies kept in the backup folder (their file names, the newest first), and what the page does with them. */
    val backupCopies: List<String> = emptyList(),
    val onRefreshCopies: () -> Unit = {},
    val onRestoreCopy: (String) -> Unit = {},
    val onShareLayout: () -> Unit = {},
    val onShareTheme: () -> Unit = {},
    val onExportApps: (Uri) -> Unit = {},
    val onImportApps: (Uri) -> Unit = {},
    /** The Start screens kept under a name: saved from what is on Start now, put back, brought up to date, deleted, given an hour. */
    val layouts: List<SavedLayout> = emptyList(),
    val onSaveLayout: (String) -> Boolean = { false },
    val onApplyLayout: (String) -> Unit = {},
    val onResaveLayout: (String) -> Unit = {},
    val onDeleteLayout: (String) -> Unit = {},
    val onLayoutHour: (String, Int) -> Unit = { _, _ -> },
    /** The photo of each Start screen that has one of its own, by the number of the screen (from 0). */
    val pageBackgrounds: Map<Int, ImageBitmap> = emptyMap(),
    val onPickPageBackground: (Int, Uri) -> Unit = { _, _ -> },
    val onClearPageBackground: (Int) -> Unit = {},
    val missingApps: List<SavedApp> = emptyList(),
    val onOpenStore: (String) -> Unit = {},
    /** Looks for cities by name: null when the search could not be made, an empty list when nothing matches. */
    val searchPlaces: suspend (String) -> List<PlaceMatch>? = { emptyList() },
    val onChoosePlace: (PlaceMatch) -> Unit = {},
    /** Follows a city besides the one of the tile, and stops following it. */
    val onFollowPlace: (PlaceMatch) -> Unit = {},
    val onUnfollowCity: (SavedCity) -> Unit = {},
    val onUseLocation: () -> Unit = {},
    /** The service that lets the launcher lock the screen is turned on. */
    val lockServiceEnabled: Boolean = false,
    val onOpenAccessibilitySettings: () -> Unit = {},
    /** Asks Android to add the Recent apps tile to the Quick Settings (Android 13 and later); null where it cannot be asked. */
    val onAddRecentsTile: (() -> Unit)? = null,
    /** The name of the backup folder, or null when none is chosen. */
    val backupFolderName: String? = null,
    val onPickBackupFolder: (Uri) -> Unit = {},
    val onClearBackupFolder: () -> Unit = {},
    val onBackupNow: () -> Unit = {},
    val onRestoreBackup: () -> Unit = {},
    val onSyncNow: () -> Unit = {},
    /** The work profile and the private space, when the phone has them. */
    val profiles: List<ProfileInfo> = emptyList(),
    val onSetProfilePaused: (serial: Long, paused: Boolean) -> Unit = { _, _ -> },
    val onTryVoice: () -> Unit = {},
    /** Shows the tutorial again. */
    val onShowTutorial: () -> Unit = {},
    /** The tiles apps have published, and a way to clear one. */
    val publishedTiles: List<ThirdPartyTile> = emptyList(),
    val onClearPublishedTile: (String) -> Unit = {},
    /** The photo of the lock screen, when it has its own. */
    val lockPhoto: ImageBitmap? = null,
    val onPickLockPhoto: (Uri) -> Unit = {},
    val onClearLockPhoto: () -> Unit = {},
    val onApplyLockPhoto: () -> Unit = {},
    val onOpenDreamSettings: () -> Unit = {},
    val onPreviewLock: () -> Unit = {},
    val iconPacks: List<IconPackInfo> = emptyList(),
    /** The update from GitHub: where it is, and what the page does about it. */
    val update: com.metrowin.launcher.data.UpdateState = com.metrowin.launcher.data.UpdateState.Idle,
    val onCheckUpdate: () -> Unit = {},
    val onInstallUpdate: () -> Unit = {},
    /** The gallery apps of the phone (package and name), for the app the Photos tile opens, and the albums its pictures come from. */
    val galleryApps: List<IconPackInfo> = emptyList(),
    val loadAlbums: suspend () -> List<com.metrowin.launcher.data.AlbumInfo> = { emptyList() },
    val onExportTheme: (Uri) -> Unit = {},
    val onImportTheme: (Uri) -> Unit = {},
    /** The apps that have a notification up now, for the per-app notification choices (they come first). */
    val notificationPackages: Set<String> = emptySet(),
    /** Opens the page of Android where the sound and the vibration of an app's notifications are chosen. */
    val onAppNotificationSettings: (String) -> Unit = {},
)
