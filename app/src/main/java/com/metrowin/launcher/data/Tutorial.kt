package com.metrowin.launcher.data

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.metrowin.launcher.R
import com.metrowin.launcher.gestures.LockAccessibilityService
import com.metrowin.launcher.model.Glyph

/**
 * What the tutorial may ask the user for, one thing on the page of the feature that needs it. Everything is optional:
 * the launcher works without any of it, only the feature behind it does not.
 */
enum class Need(
    @param:StringRes val title: Int,
    @param:StringRes val why: Int,
    /** What the button says: "allow" for a permission, "open settings" where Android has no dialog for it. */
    @param:StringRes val action: Int,
) {
    HomeApp(R.string.need_home, R.string.need_home_why, R.string.need_action_default),
    Photos(R.string.perm_photos, R.string.perm_photos_why, R.string.perm_allow),
    Calendar(R.string.perm_calendar, R.string.perm_calendar_why, R.string.perm_allow),
    Contacts(R.string.perm_contacts, R.string.perm_contacts_why, R.string.perm_allow),
    CallLog(R.string.perm_calls, R.string.perm_calls_why, R.string.perm_allow),
    Location(R.string.need_location, R.string.need_location_why, R.string.perm_allow),
    Microphone(R.string.need_mic, R.string.need_mic_why, R.string.perm_allow),
    NotificationAccess(R.string.need_notifications, R.string.need_notifications_why, R.string.need_action_settings),
    LockService(R.string.need_lock, R.string.need_lock_why, R.string.need_action_settings),
    Battery(R.string.need_battery, R.string.need_battery_why, R.string.perm_allow);

    /** The runtime permissions behind it, or empty when it is granted in a screen of the settings instead. */
    val permissions: List<String>
        get() = when (this) {
            Photos -> listOf(LivePermissions.photos) + if (Build.VERSION.SDK_INT >= 34) listOf("android.permission.READ_MEDIA_VISUAL_USER_SELECTED") else emptyList()
            Calendar -> listOf(LivePermissions.calendar)
            Contacts -> listOf(LivePermissions.contacts)
            CallLog -> listOf(LivePermissions.calls)
            Location -> listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
            Microphone -> listOf(Manifest.permission.RECORD_AUDIO)
            HomeApp, NotificationAccess, LockService, Battery -> emptyList()
        }
}

/**
 * Where a page of the tutorial leads in the settings: the name of a `SettingsSection` (null for the home of the settings) and, when the page is about
 * one setting, its id in the `SettingsIndex`. Names, not types, so that the data of the tour does not depend on the screens (a test checks that they exist).
 */
data class TutorialLink(val section: String? = null, val target: String? = null)

/** One page of the tutorial: a part of the launcher, what to do with it, what it needs, and where to change it in the settings. */
data class TutorialStep(
    val id: String,
    val glyph: Glyph,
    @param:StringRes val label: Int,
    @param:StringRes val title: Int,
    @param:StringRes val intro: Int,
    val tips: List<Int>,
    val needs: List<Need> = emptyList(),
    val link: TutorialLink? = null,
)

/**
 * The tour, in the order of what you meet: the first steps, Start, the apps, the mini apps, what controls the phone, how it looks, keeping it safe,
 * and the settings. Each page has a few short lines and, when there is a setting for it, a button that opens that setting.
 */
object Tutorial {
    val steps: List<TutorialStep> = listOf(
        TutorialStep(
            "welcome", Glyph.Star, R.string.tut_welcome_label, R.string.tut_welcome_title, R.string.tut_welcome_intro,
            listOf(R.string.tut_welcome_tip1, R.string.tut_welcome_tip2),
        ),
        TutorialStep(
            "home", Glyph.Home, R.string.tut_home_label, R.string.tut_home_title, R.string.tut_home_intro,
            listOf(R.string.tut_home_tip1, R.string.tut_home_tip2), listOf(Need.HomeApp),
        ),
        // --- Start
        TutorialStep(
            "start", Glyph.Widget, R.string.tut_start_label, R.string.tut_start_title, R.string.tut_start_intro,
            listOf(R.string.tut_start_tip1, R.string.tut_start_tip2, R.string.tut_start_tip3, R.string.tut_start_tip4, R.string.tut_start_tip5),
            link = TutorialLink("Start", "start.columns"),
        ),
        TutorialStep(
            "folders", Glyph.Folder, R.string.tut_folders_label, R.string.tut_folders_title, R.string.tut_folders_intro,
            listOf(R.string.tut_folders_tip1, R.string.tut_folders_tip2, R.string.tut_folders_tip3),
            link = TutorialLink("Start", "start.groups"),
        ),
        TutorialStep(
            "layouts", Glyph.Recents, R.string.tut_layouts_label, R.string.tut_layouts_title, R.string.tut_layouts_intro,
            listOf(R.string.tut_layouts_tip1, R.string.tut_layouts_tip2, R.string.tut_layouts_tip3),
            link = TutorialLink("Start", "start.layouts"),
        ),
        TutorialStep(
            "live", Glyph.Calendar, R.string.tut_live_label, R.string.tut_live_title, R.string.tut_live_intro,
            listOf(R.string.tut_live_tip1, R.string.tut_live_tip2),
            listOf(Need.Photos, Need.Calendar, Need.Contacts, Need.CallLog),
            TutorialLink("LiveTiles", "live.master"),
        ),
        TutorialStep(
            "infotile", Glyph.Stopwatch, R.string.tut_infotile_label, R.string.tut_infotile_title, R.string.tut_infotile_intro,
            listOf(R.string.tut_infotile_tip1, R.string.tut_infotile_tip2, R.string.tut_infotile_tip3, R.string.tut_infotile_tip4),
        ),
        TutorialStep(
            "weather", Glyph.Weather, R.string.tut_weather_label, R.string.tut_weather_title, R.string.tut_weather_intro,
            listOf(R.string.tut_weather_tip1, R.string.tut_weather_tip2), listOf(Need.Location),
            TutorialLink("LiveTiles", "live.city"),
        ),
        // --- apps and search
        TutorialStep(
            "apps", Glyph.Store, R.string.tut_apps_label, R.string.tut_apps_title, R.string.tut_apps_intro,
            listOf(R.string.tut_apps_tip1, R.string.tut_apps_tip2, R.string.tut_apps_tip3, R.string.tut_apps_tip4),
            link = TutorialLink("Apps", "apps.frequent"),
        ),
        TutorialStep(
            "search", Glyph.Search, R.string.tut_search_label, R.string.tut_search_title, R.string.tut_search_intro,
            listOf(R.string.tut_search_tip1, R.string.tut_search_tip2, R.string.tut_search_tip3, R.string.tut_search_tip4),
            link = TutorialLink("Search", "search.operators"),
        ),
        TutorialStep(
            "icons", Glyph.Photos, R.string.tut_icons_label, R.string.tut_icons_title, R.string.tut_icons_intro,
            listOf(R.string.tut_icons_tip1, R.string.tut_icons_tip2, R.string.tut_icons_tip3),
            link = TutorialLink("Icons", "icons.pack"),
        ),
        TutorialStep(
            "voice", Glyph.Mic, R.string.tut_voice_label, R.string.tut_voice_title, R.string.tut_voice_intro,
            listOf(R.string.tut_voice_tip1, R.string.tut_voice_tip2, R.string.tut_voice_tip3), listOf(Need.Microphone),
            TutorialLink("Voice", "voice.button"),
        ),
        // --- the mini apps
        TutorialStep(
            "minis", Glyph.Office, R.string.tut_minis_label, R.string.tut_minis_title, R.string.tut_minis_intro,
            listOf(R.string.tut_minis_tip1, R.string.tut_minis_tip2, R.string.tut_minis_tip3),
            link = TutorialLink("MiniApps"),
        ),
        TutorialStep(
            "phone", Glyph.Phone, R.string.tut_phone_label, R.string.tut_phone_title, R.string.tut_phone_intro,
            listOf(R.string.tut_phone_tip1, R.string.tut_phone_tip2, R.string.tut_phone_tip3),
        ),
        // --- what controls the phone
        TutorialStep(
            "action", Glyph.Bell, R.string.tut_action_label, R.string.tut_action_title, R.string.tut_action_intro,
            listOf(R.string.tut_action_tip1, R.string.tut_action_tip2, R.string.tut_action_tip3), listOf(Need.NotificationAccess),
            TutorialLink("Actions", "ac.enabled"),
        ),
        TutorialStep(
            "notifications", Glyph.Messaging, R.string.tut_notif_label, R.string.tut_notif_title, R.string.tut_notif_intro,
            listOf(R.string.tut_notif_tip1, R.string.tut_notif_tip2, R.string.tut_notif_tip3, R.string.tut_notif_tip4),
            link = TutorialLink("Notifications", "ac.reply"),
        ),
        TutorialStep(
            "bars", Glyph.Wifi, R.string.tut_bars_label, R.string.tut_bars_title, R.string.tut_bars_intro,
            listOf(R.string.tut_bars_tip1, R.string.tut_bars_tip2, R.string.tut_bars_tip3, R.string.tut_bars_tip4),
            link = TutorialLink("StatusBar", "statusbar.metro"),
        ),
        TutorialStep(
            "gestures", Glyph.Gesture, R.string.tut_gestures_label, R.string.tut_gestures_title, R.string.tut_gestures_intro,
            listOf(R.string.tut_gestures_tip1, R.string.tut_gestures_tip2, R.string.tut_gestures_tip3), listOf(Need.LockService),
            TutorialLink("Gestures", "gest.doubletap"),
        ),
        TutorialStep(
            "lock", Glyph.Lock, R.string.tut_lock_label, R.string.tut_lock_title, R.string.tut_lock_intro,
            listOf(R.string.tut_lock_tip1, R.string.tut_lock_tip2, R.string.tut_lock_tip3),
            link = TutorialLink("Lock", "lock.preview"),
        ),
        // --- look and ease
        TutorialStep(
            "look", Glyph.Theme, R.string.tut_look_label, R.string.tut_look_title, R.string.tut_look_intro,
            listOf(R.string.tut_look_tip1, R.string.tut_look_tip2, R.string.tut_look_tip3),
            link = TutorialLink("StartTheme", "theme.style"),
        ),
        TutorialStep(
            "access", Glyph.Brightness, R.string.tut_access_label, R.string.tut_access_title, R.string.tut_access_intro,
            listOf(R.string.tut_access_tip1, R.string.tut_access_tip2, R.string.tut_access_tip3, R.string.tut_access_tip4),
            link = TutorialLink("Accessibility", "access.textsize"),
        ),
        // --- safe and kept
        TutorialStep(
            "privacy", Glyph.Vpn, R.string.tut_privacy_label, R.string.tut_privacy_title, R.string.tut_privacy_intro,
            listOf(R.string.tut_privacy_tip1, R.string.tut_privacy_tip2, R.string.tut_privacy_tip3, R.string.tut_privacy_tip4),
            link = TutorialLink("Privacy", "gest.banksafe"),
        ),
        TutorialStep(
            "backup", Glyph.Storage, R.string.tut_backup_label, R.string.tut_backup_title, R.string.tut_backup_intro,
            listOf(R.string.tut_backup_tip1, R.string.tut_backup_tip2, R.string.tut_backup_tip3, R.string.tut_backup_tip4),
            link = TutorialLink("Backup", "backup.folder"),
        ),
        TutorialStep(
            "alive", Glyph.Battery, R.string.tut_alive_label, R.string.tut_alive_title, R.string.tut_alive_intro,
            listOf(R.string.tut_alive_tip1, R.string.tut_alive_tip2, R.string.tut_alive_tip3), listOf(Need.Battery),
            TutorialLink("Battery", "battery.saving"),
        ),
        TutorialStep(
            "settings", Glyph.Settings, R.string.tut_settings_label, R.string.tut_settings_title, R.string.tut_settings_intro,
            listOf(
                R.string.tut_settings_tip1, R.string.tut_settings_tip2, R.string.tut_settings_tip3, R.string.tut_settings_tip4,
                R.string.tut_settings_tip5,
            ),
            link = TutorialLink(),
        ),
    )

    /** Every need the tutorial asks for, in the order it asks. */
    val needs: List<Need> get() = steps.flatMap { it.needs }
}

/** Whether the launcher is the Home app of the phone, and how to ask Android to make it so. */
object HomeApp {
    /** [resolvedHomePackage] is who Android would open for Home now (the "chooser" when the user never picked one). */
    fun isOurs(resolvedHomePackage: String?, ownPackage: String): Boolean = resolvedHomePackage == ownPackage

    fun isDefault(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) return roles.isRoleHeld(RoleManager.ROLE_HOME)
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        return isOurs(resolved, context.packageName)
    }

    /** Android 10 and later has a dialog for it; before that the user is taken to the settings of the Home app. */
    fun requestIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) return roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }
}

/** Reads which needs are met right now. */
class NeedChecker(private val context: Context) {
    fun isMet(need: Need): Boolean = when (need) {
        Need.HomeApp -> HomeApp.isDefault(context)
        Need.Photos -> LivePermissions.canReadPhotos(context)
        Need.NotificationAccess -> NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        Need.LockService -> LockAccessibilityService.isEnabled(context)
        Need.Battery -> context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
        else -> need.permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }

    fun met(): Set<Need> = Need.entries.filterTo(HashSet(), ::isMet)
}
