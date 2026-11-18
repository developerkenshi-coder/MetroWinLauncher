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
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.notifications.LiveReportLine

/**
 * The categories of the settings home, in the order of what you reach for: how the launcher looks, Start, the lock screen, the bars,
 * how it answers to your touch, your apps, the mini apps, notifications, privacy, battery, accessibility, backups, and the app itself.
 * A category is a list on the home and a pivot of [SettingsSection] pages once opened: two levels, never more.
 */
enum class SettingsCategory(
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    val glyph: Glyph,
    /** Words, in the language of the phone, that find the category (see [SettingsIndex.searchIndex]). */
    @param:StringRes val words: Int,
    /** What the category is for, in a few lines (the "i" of its row). */
    @param:StringRes val info: Int,
) {
    Look(R.string.cat_look, R.string.cat_look_desc, Glyph.Theme, R.string.cat_look_kw, R.string.info_cat_look),
    Start(R.string.cat_start, R.string.cat_start_desc, Glyph.Widget, R.string.cat_start_kw, R.string.info_cat_start),
    Lock(R.string.cat_lock, R.string.cat_lock_desc, Glyph.Lock, R.string.cat_lock_kw, R.string.info_cat_lock),
    Bars(R.string.cat_bars, R.string.cat_bars_desc, Glyph.Wifi, R.string.cat_bars_kw, R.string.info_cat_bars),
    Gestures(R.string.cat_gestures, R.string.cat_gestures_desc, Glyph.Gesture, R.string.cat_gestures_kw, R.string.info_cat_gestures),
    Apps(R.string.cat_apps, R.string.cat_apps_desc, Glyph.Store, R.string.cat_apps_kw, R.string.info_cat_apps),
    Mini(R.string.cat_mini, R.string.cat_mini_desc, Glyph.Office, R.string.cat_mini_kw, R.string.info_cat_mini),
    Notifications(R.string.cat_notifications, R.string.cat_notifications_desc, Glyph.Bell, R.string.cat_notifications_kw, R.string.info_cat_notifications),
    Privacy(R.string.cat_privacy, R.string.cat_privacy_desc, Glyph.Vpn, R.string.cat_privacy_kw, R.string.info_cat_privacy),
    Battery(R.string.cat_battery, R.string.cat_battery_desc, Glyph.Battery, R.string.cat_battery_kw, R.string.info_cat_battery),
    Access(R.string.cat_access, R.string.cat_access_desc, Glyph.Brightness, R.string.cat_access_kw, R.string.info_cat_access),
    Backup(R.string.cat_backup, R.string.cat_backup_desc, Glyph.Storage, R.string.cat_backup_kw, R.string.info_cat_backup),
    About(R.string.cat_about, R.string.cat_about_desc, Glyph.Launcher, R.string.cat_about_kw, R.string.info_cat_about),
    Tip(R.string.cat_tip, R.string.cat_tip_desc, Glyph.Star, R.string.cat_tip_kw, R.string.info_cat_tip);

    /** The pages of the category, in order: the pivot it opens as. */
    val pages: List<SettingsSection> get() = SettingsSection.entries.filter { it.category == this }
}

/**
 * The pages of the settings. Each is a pivot page of one [category] (a setting lives on exactly one of them: what another page
 * needs of it is a link, never a copy). What is on each page is listed in the [SettingsIndex].
 */
enum class SettingsSection(
    val category: SettingsCategory,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    /** Words, in the language of the phone, that find the page and its settings (see [SettingsIndex.searchIndex]). */
    @param:StringRes val words: Int,
) {
    // look and themes
    StartTheme(SettingsCategory.Look, R.string.sec_look, R.string.sec_look_desc, R.string.kw_look),
    Screen(SettingsCategory.Look, R.string.sec_screen, R.string.sec_screen_desc, R.string.kw_screen),

    // start and tiles
    Start(SettingsCategory.Start, R.string.sec_start, R.string.sec_start_desc, R.string.kw_start),
    StartBars(SettingsCategory.Start, R.string.sec_bars, R.string.sec_bars_desc, R.string.kw_start_bars),
    StartBehaviour(SettingsCategory.Start, R.string.sec_start_behaviour, R.string.sec_start_behaviour_desc, R.string.kw_start_behaviour),
    LiveTiles(SettingsCategory.Start, R.string.sec_live_tiles, R.string.sec_live_tiles_desc, R.string.kw_live_tiles),

    // lock screen
    Lock(SettingsCategory.Lock, R.string.sec_lock, R.string.sec_lock_desc, R.string.kw_lock),

    // bars and action center
    StatusBar(SettingsCategory.Bars, R.string.sec_statusbar, R.string.sec_statusbar_desc, R.string.kw_status_bar),
    Actions(SettingsCategory.Bars, R.string.sec_action_center, R.string.sec_action_center_desc, R.string.kw_actions),
    NavBar(SettingsCategory.Bars, R.string.sec_navbar, R.string.sec_navbar_desc, R.string.kw_navbar),

    // gestures and shortcuts
    Gestures(SettingsCategory.Gestures, R.string.sec_gestures, R.string.sec_gestures_desc, R.string.kw_gestures),
    Feel(SettingsCategory.Gestures, R.string.sec_feel, R.string.sec_feel_desc, R.string.kw_feel),
    Voice(SettingsCategory.Gestures, R.string.sec_voice, R.string.sec_voice_desc, R.string.kw_voice),

    // apps and icons
    Apps(SettingsCategory.Apps, R.string.sec_apps, R.string.sec_apps_desc, R.string.kw_apps),
    Icons(SettingsCategory.Apps, R.string.sec_icons, R.string.sec_icons_desc, R.string.kw_icons),
    Search(SettingsCategory.Apps, R.string.sec_search, R.string.sec_search_desc, R.string.kw_search),

    // mini apps
    MiniApps(SettingsCategory.Mini, R.string.sec_mini_apps, R.string.sec_mini_apps_desc, R.string.kw_mini_apps),

    // notifications
    Notifications(SettingsCategory.Notifications, R.string.sec_notifications, R.string.sec_notifications_desc, R.string.kw_notifications),

    // privacy and security
    Privacy(SettingsCategory.Privacy, R.string.sec_privacy, R.string.sec_privacy_desc, R.string.kw_privacy),
    Kids(SettingsCategory.Privacy, R.string.kids_title, R.string.sec_kids_desc, R.string.kw_kids),

    // battery
    Battery(SettingsCategory.Battery, R.string.sec_battery, R.string.sec_battery_desc, R.string.kw_battery),

    // accessibility
    Accessibility(SettingsCategory.Access, R.string.sec_access, R.string.sec_access_desc, R.string.kw_access),

    // backup and profiles
    Backup(SettingsCategory.Backup, R.string.sec_backup, R.string.sec_backup_desc, R.string.kw_backup),
    Profiles(SettingsCategory.Backup, R.string.sec_profiles, R.string.sec_profiles_desc, R.string.kw_profiles),

    // about
    About(SettingsCategory.About, R.string.sec_about, R.string.sec_about_desc, R.string.kw_about),
    BugReport(SettingsCategory.About, R.string.sec_bug, R.string.sec_bug_desc, R.string.kw_bug),
    OtherProjects(SettingsCategory.About, R.string.sec_projects, R.string.sec_projects_desc, R.string.kw_projects),

    // tip
    Tip(SettingsCategory.Tip, R.string.sec_tip, R.string.sec_tip_desc, R.string.kw_tip),
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
    /** The font file the user chose: the launcher keeps a copy and uses it. */
    val onPickFont: (Uri) -> Unit = {},
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
    /** The copies of another phone's layout that a sync with a conflict kept in the folder (names, the newest first), to put back or delete. */
    val syncOthers: List<String> = emptyList(),
    val onRefreshSyncOthers: () -> Unit = {},
    val onRecoverSyncOther: (String) -> Unit = {},
    val onClearSyncOthers: () -> Unit = {},
    val onShareLayout: () -> Unit = {},
    val onShareTheme: () -> Unit = {},
    val onExportApps: (Uri) -> Unit = {},
    val onImportApps: (Uri) -> Unit = {},
    /** The Start screens kept under a name: saved from what is on Start now, put back, brought up to date, deleted, given an hour. */
    val layouts: List<SavedLayout> = emptyList(),
    /** The quick profiles of the settings: which hold something, saved from the settings of now and forgotten. */
    val quickProfiles: com.metrowin.launcher.data.ProfileState = com.metrowin.launcher.data.ProfileState(),
    val onSaveProfile: (com.metrowin.launcher.data.QuickProfile) -> Unit = {},
    val onForgetProfile: (com.metrowin.launcher.data.QuickProfile) -> Unit = {},
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
    /** Shows the news of this version (a dialog of the launcher, not of the page: the page scrolls). */
    val onShowNews: () -> Unit = {},
    /** Prepares the bug ticket mail with the text of the user and the log of the app. */
    val onSendBugReport: (String) -> Unit = {},
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
    /** Opens the dialog that chooses the icon of these apps (by id). */
    val onPickIcons: (List<String>) -> Unit = {},
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
