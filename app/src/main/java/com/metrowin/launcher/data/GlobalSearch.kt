package com.metrowin.launcher.data

import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.metrowin.launcher.R
import com.metrowin.launcher.model.Glyph

/** A screen of the Android settings that the search can take you to. */
@Immutable
data class SystemSetting(val id: String, val label: String, val action: String, val glyph: Glyph)

/** A contact that matched what was typed. [lookupUri] opens it. */
@Immutable
data class ContactMatch(val name: String, val lookupUri: String)

/** What the search finds besides apps. [webQuery] is the text to offer to look for on the web, or null. */
@Immutable
data class GlobalResults(
    val contacts: List<ContactMatch> = emptyList(),
    val settings: List<SystemSetting> = emptyList(),
    val webQuery: String? = null,
    val events: List<EventMatch> = emptyList(),
) {
    val isEmpty: Boolean get() = contacts.isEmpty() && settings.isEmpty() && webQuery == null && events.isEmpty()

    companion object {
        val Empty = GlobalResults()
    }
}

/** The settings screens of Android that the search knows, with words (in English) that also find them. */
class SystemSettingDef(
    val id: String,
    @param:StringRes val label: Int,
    val action: String,
    val glyph: Glyph,
    val keywords: List<String> = emptyList(),
)

object GlobalSearch {
    val systemSettings: List<SystemSettingDef> = listOf(
        SystemSettingDef("wifi", R.string.ss_wifi, Settings.ACTION_WIFI_SETTINGS, Glyph.Wifi, listOf("wifi", "wi-fi", "wireless", "network", "internet")),
        SystemSettingDef("bluetooth", R.string.ss_bluetooth, Settings.ACTION_BLUETOOTH_SETTINGS, Glyph.Bluetooth, listOf("bluetooth", "pair")),
        SystemSettingDef("airplane", R.string.ss_airplane, Settings.ACTION_AIRPLANE_MODE_SETTINGS, Glyph.Airplane, listOf("airplane", "flight", "aeroplane")),
        SystemSettingDef("display", R.string.ss_display, Settings.ACTION_DISPLAY_SETTINGS, Glyph.Brightness, listOf("display", "screen", "brightness", "rotation", "font")),
        SystemSettingDef("sound", R.string.ss_sound, Settings.ACTION_SOUND_SETTINGS, Glyph.Bell, listOf("sound", "volume", "ringtone", "vibration")),
        SystemSettingDef("battery", R.string.ss_battery, Settings.ACTION_BATTERY_SAVER_SETTINGS, Glyph.Battery, listOf("battery", "power", "saver")),
        SystemSettingDef("storage", R.string.ss_storage, Settings.ACTION_INTERNAL_STORAGE_SETTINGS, Glyph.Storage, listOf("storage", "memory", "space")),
        SystemSettingDef("apps", R.string.ss_apps, Settings.ACTION_APPLICATION_SETTINGS, Glyph.Store, listOf("apps", "applications", "permissions")),
        SystemSettingDef("location", R.string.ss_location, Settings.ACTION_LOCATION_SOURCE_SETTINGS, Glyph.Maps, listOf("location", "gps", "position")),
        SystemSettingDef("security", R.string.ss_security, Settings.ACTION_SECURITY_SETTINGS, Glyph.Lock, listOf("security", "screen lock", "password", "pin", "fingerprint")),
        SystemSettingDef("privacy", R.string.ss_privacy, Settings.ACTION_PRIVACY_SETTINGS, Glyph.Lock, listOf("privacy")),
        SystemSettingDef("accessibility", R.string.ss_accessibility, Settings.ACTION_ACCESSIBILITY_SETTINGS, Glyph.Settings, listOf("accessibility", "talkback", "magnification")),
        SystemSettingDef("date", R.string.ss_date, Settings.ACTION_DATE_SETTINGS, Glyph.Alarm, listOf("date", "time", "clock", "timezone")),
        SystemSettingDef("language", R.string.ss_language, Settings.ACTION_LOCALE_SETTINGS, Glyph.Globe, listOf("language", "locale", "region")),
        SystemSettingDef("keyboard", R.string.ss_keyboard, Settings.ACTION_INPUT_METHOD_SETTINGS, Glyph.Settings, listOf("keyboard", "input", "typing")),
        SystemSettingDef("data", R.string.ss_data, "android.settings.DATA_USAGE_SETTINGS", Glyph.Data, listOf("data", "usage", "mobile data", "roaming")),
        SystemSettingDef("nfc", R.string.ss_nfc, Settings.ACTION_NFC_SETTINGS, Glyph.Wallet, listOf("nfc", "contactless", "tap to pay")),
        SystemSettingDef("vpn", R.string.ss_vpn, Settings.ACTION_VPN_SETTINGS, Glyph.Vpn, listOf("vpn")),
        SystemSettingDef("quiet", R.string.ss_quiet, "android.settings.ZEN_MODE_SETTINGS", Glyph.Moon, listOf("do not disturb", "dnd", "silent", "quiet")),
        SystemSettingDef("hotspot", R.string.ss_hotspot, "android.settings.TETHER_SETTINGS", Glyph.Hotspot, listOf("hotspot", "tethering", "share internet")),
        SystemSettingDef("cast", R.string.ss_cast, Settings.ACTION_CAST_SETTINGS, Glyph.Cast, listOf("cast", "mirror", "screen sharing")),
        SystemSettingDef("defaults", R.string.ss_defaults, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, Glyph.Settings, listOf("default apps", "home app", "launcher", "browser")),
        SystemSettingDef("developer", R.string.ss_developer, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, Glyph.Settings, listOf("developer", "usb debugging")),
        SystemSettingDef("about", R.string.ss_about, Settings.ACTION_DEVICE_INFO_SETTINGS, Glyph.Settings, listOf("about", "phone info", "version", "model")),
    )

    /**
     * The settings screens whose name (or an English keyword) matches [query], ignoring case and accents: the ones
     * that start with it first, then the ones with a word that starts with it, then the ones that have it inside.
     * [labelOf] gives the name in the language of the phone.
     */
    fun matchSettings(query: String, defs: List<SystemSettingDef> = systemSettings, labelOf: (SystemSettingDef) -> String): List<SystemSetting> {
        val needle = AppSections.fold(query)
        if (needle.isEmpty()) return emptyList()
        return defs
            .mapNotNull { def ->
                val label = AppSections.fold(labelOf(def))
                val words = label.split(' ', '-', '+', '.', '_', '/') + def.keywords.flatMap { it.split(' ', '-') }
                val rank = when {
                    label.startsWith(needle) -> 0
                    def.keywords.any { AppSections.fold(it).startsWith(needle) } -> 0
                    words.any { it.startsWith(needle) } -> 1
                    needle in label || def.keywords.any { needle in AppSections.fold(it) } -> 2
                    else -> return@mapNotNull null
                }
                rank to SystemSetting(def.id, labelOf(def), def.action, def.glyph)
            }
            .sortedBy { it.first } // stable: the order of the catalog within a rank
            .map { it.second }
    }

    /** The address of a web search for [query] with [engine]: DuckDuckGo until the user picks another one. */
    fun webUrl(query: String, engine: SearchEngine = SearchEngine.DuckDuckGo): String = engine.url(query)
}
