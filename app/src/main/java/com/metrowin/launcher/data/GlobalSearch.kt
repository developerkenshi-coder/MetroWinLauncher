package com.metrowin.launcher.data

import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.metrowin.launcher.R
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph

/** A screen of the Android settings that the search can take you to. */
@Immutable
data class SystemSetting(val id: String, val label: String, val action: String, val glyph: Glyph)

/** A contact that matched what was typed. [lookupUri] opens it; [number] is set when the match came from digits (the smart dial): the number to call. */
@Immutable
data class ContactMatch(val name: String, val lookupUri: String, val number: String? = null)

/**
 * A setting of the launcher itself that matched what was typed. [id] is its entry in the settings index (null when it is a whole
 * section that matched), [section] the name of the section it is in, and [where] the line that says so.
 */
@Immutable
data class LauncherSettingMatch(val id: String?, val section: String, val title: String, val where: String)

/** A tile of Start that matched what was typed (a note, a list, a countdown, a site): the tile and the line of it that matched. */
@Immutable
data class TileMatch(val item: AppItem, val snippet: String?)

/** What the search finds besides apps. [webQuery] is the text to offer to look for on the web, or null. */
@Immutable
data class GlobalResults(
    val contacts: List<ContactMatch> = emptyList(),
    val settings: List<SystemSetting> = emptyList(),
    val webQuery: String? = null,
    val events: List<EventMatch> = emptyList(),
    val launcherSettings: List<LauncherSettingMatch> = emptyList(),
    val tiles: List<TileMatch> = emptyList(),
) {
    val isEmpty: Boolean
        get() = contacts.isEmpty() && settings.isEmpty() && webQuery == null && events.isEmpty() && launcherSettings.isEmpty() && tiles.isEmpty()

    companion object {
        val Empty = GlobalResults()
    }
}

/** The settings screens of Android that the search knows, with words (in English and Italian) that also find them. */
class SystemSettingDef(
    val id: String,
    @param:StringRes val label: Int,
    val action: String,
    val glyph: Glyph,
    val keywords: List<String> = emptyList(),
)

object GlobalSearch {
    val systemSettings: List<SystemSettingDef> = listOf(
        SystemSettingDef("wifi", R.string.ss_wifi, Settings.ACTION_WIFI_SETTINGS, Glyph.Wifi, listOf("wifi", "wi-fi", "wireless", "network", "internet", "rete", "senza fili", "internet")),
        SystemSettingDef("bluetooth", R.string.ss_bluetooth, Settings.ACTION_BLUETOOTH_SETTINGS, Glyph.Bluetooth, listOf("bluetooth", "pair", "accoppia", "cuffie", "auricolari")),
        SystemSettingDef("airplane", R.string.ss_airplane, Settings.ACTION_AIRPLANE_MODE_SETTINGS, Glyph.Airplane, listOf("airplane", "flight", "aeroplane", "aereo", "volo", "modalita aereo")),
        SystemSettingDef("display", R.string.ss_display, Settings.ACTION_DISPLAY_SETTINGS, Glyph.Brightness, listOf("display", "screen", "brightness", "rotation", "font", "schermo", "luminosita", "rotazione", "carattere", "testo")),
        SystemSettingDef("sound", R.string.ss_sound, Settings.ACTION_SOUND_SETTINGS, Glyph.Bell, listOf("sound", "volume", "ringtone", "vibration", "suoni", "suoneria", "volume", "vibrazione", "audio")),
        SystemSettingDef("battery", R.string.ss_battery, Settings.ACTION_BATTERY_SAVER_SETTINGS, Glyph.Battery, listOf("battery", "power", "saver", "batteria", "energia", "risparmio")),
        SystemSettingDef("storage", R.string.ss_storage, Settings.ACTION_INTERNAL_STORAGE_SETTINGS, Glyph.Storage, listOf("storage", "memory", "space", "memoria", "spazio", "archiviazione")),
        SystemSettingDef("apps", R.string.ss_apps, Settings.ACTION_APPLICATION_SETTINGS, Glyph.Store, listOf("apps", "applications", "permissions", "applicazioni", "autorizzazioni", "permessi")),
        SystemSettingDef("location", R.string.ss_location, Settings.ACTION_LOCATION_SOURCE_SETTINGS, Glyph.Maps, listOf("location", "gps", "position", "posizione", "gps", "localizzazione")),
        SystemSettingDef("security", R.string.ss_security, Settings.ACTION_SECURITY_SETTINGS, Glyph.Lock, listOf("security", "screen lock", "password", "pin", "fingerprint", "sicurezza", "blocco schermo", "password", "impronta", "codice")),
        SystemSettingDef("privacy", R.string.ss_privacy, Settings.ACTION_PRIVACY_SETTINGS, Glyph.Lock, listOf("privacy", "riservatezza", "dati personali")),
        SystemSettingDef("accessibility", R.string.ss_accessibility, Settings.ACTION_ACCESSIBILITY_SETTINGS, Glyph.Settings, listOf("accessibility", "talkback", "magnification", "accessibilita", "ingrandimento", "lettore schermo")),
        SystemSettingDef("date", R.string.ss_date, Settings.ACTION_DATE_SETTINGS, Glyph.Alarm, listOf("date", "time", "clock", "timezone", "data", "ora", "orologio", "fuso orario")),
        SystemSettingDef("language", R.string.ss_language, Settings.ACTION_LOCALE_SETTINGS, Glyph.Globe, listOf("language", "locale", "region", "lingua", "regione", "localizzazione")),
        SystemSettingDef("keyboard", R.string.ss_keyboard, Settings.ACTION_INPUT_METHOD_SETTINGS, Glyph.Settings, listOf("keyboard", "input", "typing", "tastiera", "digitazione", "scrittura")),
        SystemSettingDef("data", R.string.ss_data, "android.settings.DATA_USAGE_SETTINGS", Glyph.Data, listOf("data", "usage", "mobile data", "roaming", "dati", "consumo", "traffico", "roaming", "dati mobili")),
        SystemSettingDef("nfc", R.string.ss_nfc, Settings.ACTION_NFC_SETTINGS, Glyph.Wallet, listOf("nfc", "contactless", "tap to pay", "pagamenti", "senza contatto")),
        SystemSettingDef("vpn", R.string.ss_vpn, Settings.ACTION_VPN_SETTINGS, Glyph.Vpn, listOf("vpn", "rete privata")),
        SystemSettingDef("quiet", R.string.ss_quiet, "android.settings.ZEN_MODE_SETTINGS", Glyph.Moon, listOf("do not disturb", "dnd", "silent", "quiet", "non disturbare", "silenzioso", "silenzio", "ore silenziose")),
        SystemSettingDef("hotspot", R.string.ss_hotspot, "android.settings.TETHER_SETTINGS", Glyph.Hotspot, listOf("hotspot", "tethering", "share internet", "condividi internet", "tethering", "hotspot personale")),
        SystemSettingDef("cast", R.string.ss_cast, Settings.ACTION_CAST_SETTINGS, Glyph.Cast, listOf("cast", "mirror", "screen sharing", "trasmetti", "specchia", "condivisione schermo")),
        SystemSettingDef("defaults", R.string.ss_defaults, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, Glyph.Settings, listOf("default apps", "home app", "launcher", "browser", "app predefinite", "app home", "predefinite", "browser")),
        SystemSettingDef("developer", R.string.ss_developer, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, Glyph.Settings, listOf("developer", "usb debugging", "sviluppatore", "debug usb", "opzioni sviluppatore")),
        SystemSettingDef("about", R.string.ss_about, Settings.ACTION_DEVICE_INFO_SETTINGS, Glyph.Settings, listOf("about", "phone info", "version", "model", "info", "informazioni", "versione", "modello", "telefono")),
    )

    /**
     * The settings screens whose name (or an English keyword) matches [query], ignoring case and accents, best match first (see
     * [SearchIndex]: what starts with the query before what has it inside, and a name before a keyword).
     * [labelOf] gives the name in the language of the phone.
     */
    fun matchSettings(query: String, defs: List<SystemSettingDef> = systemSettings, labelOf: (SystemSettingDef) -> String): List<SystemSetting> {
        if (SearchText.words(query).isEmpty()) return emptyList()
        val index = SearchIndex(defs.map { SearchEntry(it, labelOf(it), it.keywords) })
        return index.search(query).map { SystemSetting(it.id, labelOf(it), it.action, it.glyph) }
    }

    /** The address of a web search for [query] with [engine]: DuckDuckGo until the user picks another one. */
    fun webUrl(query: String, engine: SearchEngine = SearchEngine.DuckDuckGo): String = engine.url(query)
}
