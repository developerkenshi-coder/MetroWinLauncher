package com.metrowin.launcher.data

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.metrowin.launcher.R
import com.metrowin.launcher.gestures.SystemAction
import androidx.core.content.edit

/** Light, dark, or one of the two automatic modes. */
enum class ThemeMode(val code: String) {
    Dark("dark"),
    Light("light"),

    /** Follows the dark theme of Android. */
    System("system"),

    /** Dark between two hours of the day, light the rest of it. */
    Schedule("schedule");

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code } ?: Dark

        /**
         * Whether the launcher is dark right now. In [Schedule] mode it is dark from [fromHour] up to [toHour],
         * across midnight when [fromHour] is later than [toHour] (19 to 7 is the night); equal hours mean always dark.
         */
        fun isDark(mode: ThemeMode, systemDark: Boolean, hour: Int, fromHour: Int, toHour: Int): Boolean = when (mode) {
            Dark -> true
            Light -> false
            System -> systemDark
            Schedule -> when {
                fromHour == toHour -> true
                fromHour < toHour -> hour in fromHour until toHour
                else -> hour >= fromHour || hour < toHour
            }
        }
    }
}

/** What a gesture does, chosen in the settings. */
enum class GestureAction(val code: String, @param:StringRes val label: Int) {
    None("none", R.string.gact_none),
    LockScreen("lock", R.string.gact_lock),
    OpenSettings("settings", R.string.gact_settings),
    ActionCenter("action_center", R.string.gact_action_center),
    AppList("apps", R.string.gact_apps),
    Torch("torch", R.string.gact_torch),
    Camera("camera", R.string.gact_camera),
    Voice("voice", R.string.gact_voice),
    Search("search", R.string.gact_search),

    /** An app the user chose for that gesture. */
    OpenApp("app", R.string.gact_app),

    /** The recent apps, at once (through the screen lock accessibility service). */
    Recents("recents", R.string.gact_recents),

    /** What the accessibility service can do besides: see [SystemAction]. */
    Screenshot("screenshot", R.string.gact_screenshot),
    PowerMenu("power", R.string.gact_power),
    SplitScreen("split", R.string.gact_split),
    AndroidNotifications("android_notifications", R.string.gact_android_notifications),
    AndroidQuickSettings("android_quick", R.string.gact_android_quick);

    /** The service action behind this gesture, or null for the others. */
    val system: SystemAction?
        get() = when (this) {
            Screenshot -> SystemAction.Screenshot
            PowerMenu -> SystemAction.PowerMenu
            SplitScreen -> SystemAction.SplitScreen
            AndroidNotifications -> SystemAction.Notifications
            AndroidQuickSettings -> SystemAction.QuickSettings
            else -> null
        }

    companion object {
        fun fromCode(code: String?, default: GestureAction = None) = entries.firstOrNull { it.code == code } ?: default
    }
}

/** How the Start clock writes the hours: as the phone does, or on a 12 or 24 hour clock whatever the phone says. */
enum class ClockHours(val code: String, @param:StringRes val label: Int) {
    System("system", R.string.clock_hours_system),
    H12("12", R.string.clock_hours_12),
    H24("24", R.string.clock_hours_24);

    /** Whether the clock shows 24 hours, given what the phone uses ([system]). */
    fun is24(system: Boolean): Boolean = when (this) {
        System -> system
        H12 -> false
        H24 -> true
    }

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code } ?: System
    }
}

/** How an app opens: the Metro turnstile, or the tile growing into the window of the app. */
enum class LaunchAnimation(val code: String, @param:StringRes val label: Int) {
    Turnstile("turnstile", R.string.launch_turnstile),
    Expand("expand", R.string.launch_expand);

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code } ?: Turnstile
    }
}

enum class OrientationMode(val code: String, @param:StringRes val label: Int) {
    Portrait("portrait", R.string.screen_orient_portrait),

    /** Follows the rotation of the phone, and its rotation lock, so the launcher can be used in landscape. */
    Auto("auto", R.string.screen_orient_auto),
    Landscape("landscape", R.string.screen_orient_landscape);

    /** The value for [android.app.Activity.setRequestedOrientation]. */
    val requested: Int
        get() = when (this) {
            Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            Auto -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code } ?: Portrait
    }
}

/** Every setting of the launcher. They are saved as one piece of JSON, see [SettingsCodec]. */
@Immutable
data class LauncherSettings(
    // --- start+theme
    val themeMode: ThemeMode = ThemeMode.Dark,
    /** In [ThemeMode.Schedule] the launcher is dark from this hour (0..23) to [darkToHour]. */
    val darkFromHour: Int = 19,
    val darkToHour: Int = 7,
    /** Index into the 20 accent colours, 4 is Windows Phone's cyan. */
    val accentIndex: Int = DEFAULT_ACCENT,
    /** The look: Classic, Modern Glass or Modern Droid. */
    val themeStyle: ThemeStyle = ThemeStyle.Classic,
    /** A color made with the sliders (ARGB), instead of one of the 20; 0 when there is none. */
    val customAccent: Int = 0,
    /** OLED screens: the dark theme is pure black, so the pixels of the background are off (see Themes.resolve). */
    val oledMode: Boolean = false,
    /** In Modern Droid, take the colors of the wallpaper where Android gives them (12 and later). */
    val dynamicColor: Boolean = true,
    /** When the launcher spares the battery: by itself, always or never (see [PowerPolicy]). */
    val batterySaving: BatterySaving = BatterySaving.Auto,
    /** The accent is taken from the Start photo, when there is one. */
    val autoAccent: Boolean = false,
    /** Three columns of medium tiles instead of two ("show more tiles"). */
    val moreTiles: Boolean = false,
    /** 0 when Start has no background photo, otherwise bumped for every new photo. */
    val backgroundRevision: Int = 0,
    /** The photo moves slower than the tiles when Start scrolls. */
    val parallax: Boolean = true,
    /** How many Start screens there are, 1 to [MAX_START_PAGES]. */
    val startPages: Int = 1,
    /** The time and date above the tiles. */
    val showClock: Boolean = false,
    /** A touch on the time opens the clock, on the date the calendar. */
    val clockTaps: Boolean = true,
    val clockHours: ClockHours = ClockHours.System,
    /** The next alarm under the date. */
    val clockAlarm: Boolean = false,
    /** The number of the week next to the date. */
    val clockWeek: Boolean = false,
    /** The size of the text of the launcher, in percent (see [TEXT_SCALES]). */
    val textScale: Int = 100,
    /** The status bar of Android is out of the way on Start; a swipe from the top edge brings it back for a moment. */
    val hideStatusBar: Boolean = false,
    /** How the Start photo is framed: zoom in percent (100 to 300), and the point of the photo that stays in view, in percent. */
    val startPhotoZoom: Int = 100,
    val startPhotoX: Int = 50,
    val startPhotoY: Int = 50,

    // --- display
    val orientation: OrientationMode = OrientationMode.Portrait,
    /** Ask Android for the fastest refresh rate of the screen. */
    val highRefreshRate: Boolean = true,
    val launchAnimation: LaunchAnimation = LaunchAnimation.Turnstile,

    // --- gestures
    /** Pulling down from the top edge opens the Action Center from any page. */
    val edgePull: Boolean = true,
    /** The back swipe from the right edge on Start goes on to the next page. */
    val edgeBackPan: Boolean = true,
    val doubleTapAction: GestureAction = GestureAction.None,
    val twoFingerDownAction: GestureAction = GestureAction.None,
    val twoFingerUpAction: GestureAction = GestureAction.None,
    /** The apps ([com.metrowin.launcher.model.AppItem.id]) that the gestures open when they are set to "open an app". */
    val doubleTapApp: String = "",
    val twoFingerDownApp: String = "",
    val twoFingerUpApp: String = "",
    val tapSound: Boolean = false,
    val tapHaptics: Boolean = false,

    // --- notifications and actions
    /** The four buttons on top of the Action Center, as [QuickAction.id]s. */
    val quickActions: List<String> = QuickAction.defaults.map { it.id },
    /** Tiles flip to show the latest notification of their app. Off until asked for: it puts the text of messages on Start. */
    val notificationPeek: Boolean = false,
    val inlineReply: Boolean = true,
    val groupConversations: Boolean = true,
    /** The volume and the brightness sliders and the sound mode buttons in the Action Center. */
    val actionSliders: Boolean = true,
    /** With [notificationPeek] on: the conversations of messaging apps (who wrote, and what), one face for each. */
    val liveMessages: Boolean = true,
    /** Say who wrote, not what: the text of messages and notifications is left off the tiles. */
    val liveHideText: Boolean = false,
    /** The pictures that come with notifications fill the tile. */
    val livePictures: Boolean = true,
    /** The music that plays in any media app, on its tile and on the Music tile. */
    val liveNowPlaying: Boolean = true,
    /** Downloads, uploads and the like, with their progress. */
    val liveProgress: Boolean = true,
    /** Apps (package names) whose tile never shows live content. */
    val liveOffApps: Set<String> = emptySet(),

    // --- live tiles
    val weatherCity: String = "",
    val weatherLatitude: Double = Double.NaN,
    val weatherLongitude: Double = Double.NaN,
    val weatherFahrenheit: Boolean = false,
    /** Apps may show their own content on their tile through the launcher's content provider. */
    val thirdPartyTiles: Boolean = true,

    // --- apps
    /** Apps hidden from the app list, as [com.metrowin.launcher.model.AppItem.id]s. */
    val hiddenApps: Set<String> = emptySet(),
    val searchContacts: Boolean = true,
    val searchSettings: Boolean = true,
    val searchWeb: Boolean = true,
    /** The web search the search box offers. */
    val searchEngine: SearchEngine = SearchEngine.DuckDuckGo,
    /** Sums, conversions, phone numbers, links, timers and alarms answered in the search box. */
    val searchAnswers: Boolean = true,
    /** The last searches are kept on the phone and offered when the search box is touched. */
    val rememberSearches: Boolean = true,
    /** Package of the icon pack in use, or empty for the apps' own icons. */
    val iconPack: String = "",
    /** The apps opened most often, in a group at the top of the list. */
    val showFrequentApps: Boolean = true,
    /** The apps installed in the last week, in a group at the top of the list. */
    val showNewApps: Boolean = true,
    /** Chips under the search box that filter the list by what the apps say they are. */
    val showCategories: Boolean = true,
    /** Names the user gave to apps, by [com.metrowin.launcher.model.AppItem.id]. */
    val appLabels: Map<String, String> = emptyMap(),
    /** Apps that ask for the screen lock before they open, as app ids. */
    val lockedApps: Set<String> = emptySet(),

    // --- profiles
    val showWorkApps: Boolean = true,
    val showPrivateApps: Boolean = true,

    // --- lock screen
    /** Counts the photos picked for the lock screen; 0 when there is none. */
    val lockPhotoRevision: Int = 0,
    val lockShowAlarm: Boolean = true,
    val lockShowNotifications: Boolean = true,
    val lockShowBattery: Boolean = true,

    // --- voice
    val voiceOffline: Boolean = true,
    /** A microphone next to the search box of the app list. */
    val voiceButton: Boolean = true,
    /** "Call Anna" places the call at once (needs the phone permission) instead of opening the dialer with the number. */
    val voiceCallDirect: Boolean = false,

    // --- accessibility
    /** Fades instead of turnstiles, flips and springs. */
    val reduceMotion: Boolean = false,
    /** Stronger frames and no see-through tiles. */
    val highContrast: Boolean = false,

    // --- backup and sync
    /** The folder (a document tree [android.net.Uri]) where backups and the sync file go. */
    val backupFolder: String = "",
    /** Days between automatic backups, 0 for never. */
    val backupEveryDays: Int = 0,
    val lastBackupAt: Long = 0L,
    val syncEnabled: Boolean = false,
    val syncPassphrase: String = "",
    /** Tells this phone apart from the others that sync the same folder. */
    val deviceId: String = "",
    /** The fingerprint of this phone's layout, and the stamp of the folder's file, at the last sync. */
    val syncLastHash: String = "",
    val syncLastStamp: Long = 0L,
    val syncLastAt: Long = 0L,

    // --- kid's corner
    /** The apps of the Kid's Corner, as [com.metrowin.launcher.model.AppItem.id]s. */
    val kidsApps: Set<String> = emptySet(),
    /** The Kid's Corner is on: Start shows only [kidsApps], until a grown-up leaves it. */
    val kidsMode: Boolean = false,
) {
    val hasStartBackground: Boolean get() = backgroundRevision > 0
    val hasLockPhoto: Boolean get() = lockPhotoRevision > 0

    companion object {
        const val DEFAULT_ACCENT = 4
        const val DEFAULT_COLUMNS = 4
        const val MAX_START_PAGES = 3

        /** The sizes of text to choose from, in percent. */
        val TEXT_SCALES = listOf(85, 90, 100, 110, 120, 130)
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("metrowin", Context.MODE_PRIVATE)

    fun load(): LauncherSettings =
        prefs.getString(KEY_JSON, null)?.let(SettingsCodec::decode) ?: legacy()

    fun save(settings: LauncherSettings) = prefs.edit { putString(KEY_JSON, SettingsCodec.encode(settings)) }

    /** The settings of the first versions, one key each. */
    private fun legacy() = LauncherSettings(
        themeMode = if (prefs.getBoolean("dark_theme", true)) ThemeMode.Dark else ThemeMode.Light,
        accentIndex = prefs.getInt("accent", LauncherSettings.DEFAULT_ACCENT),
        moreTiles = prefs.getBoolean("more_tiles", false),
        backgroundRevision = prefs.getInt("background_revision", 0),
        quickActions = prefs.getString("quick_actions", null)?.split(',')
            ?.let { ids -> QuickAction.slots(ids).map { it.id } }
            ?: QuickAction.defaults.map { it.id },
        kidsApps = prefs.getStringSet("kids_apps", null)?.toSet().orEmpty(),
        kidsMode = prefs.getBoolean("kids_mode", false),
    )

    private companion object {
        const val KEY_JSON = "settings_json"
    }
}
