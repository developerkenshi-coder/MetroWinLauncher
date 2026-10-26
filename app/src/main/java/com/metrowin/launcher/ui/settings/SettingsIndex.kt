package com.metrowin.launcher.ui.settings

import android.content.Context
import androidx.annotation.StringRes
import com.metrowin.launcher.R
import com.metrowin.launcher.data.SearchEntry
import com.metrowin.launcher.data.SearchIndex
import com.metrowin.launcher.ui.settings.SettingsSection.Accessibility
import com.metrowin.launcher.ui.settings.SettingsSection.Actions
import com.metrowin.launcher.ui.settings.SettingsSection.Apps
import com.metrowin.launcher.ui.settings.SettingsSection.Backup
import com.metrowin.launcher.ui.settings.SettingsSection.Battery
import com.metrowin.launcher.ui.settings.SettingsSection.Gestures
import com.metrowin.launcher.ui.settings.SettingsSection.Icons
import com.metrowin.launcher.ui.settings.SettingsSection.Kids
import com.metrowin.launcher.ui.settings.SettingsSection.LiveTiles
import com.metrowin.launcher.ui.settings.SettingsSection.Lock
import com.metrowin.launcher.ui.settings.SettingsSection.Profiles
import com.metrowin.launcher.ui.settings.SettingsSection.Screen
import com.metrowin.launcher.ui.settings.SettingsSection.Search
import com.metrowin.launcher.ui.settings.SettingsSection.Start
import com.metrowin.launcher.ui.settings.SettingsSection.StartTheme
import com.metrowin.launcher.ui.settings.SettingsSection.Voice
import com.metrowin.launcher.ui.settings.SettingsSection.About

/**
 * One setting of the launcher, as the index knows it: the [section] it lives in, the [title] it has on its page and other words
 * (Italian and English) that find it. The [id] is what the control on the page says it is (see [settingAnchor]), which is how the
 * search takes you to it.
 */
class SettingEntry(
    val id: String,
    val section: SettingsSection,
    @param:StringRes val title: Int,
    val keywords: List<String> = emptyList(),
)

/** What a search of the settings finds: a whole [section] (when [setting] is null) or one setting in it. */
class SettingHit(val section: SettingsSection, val setting: SettingEntry?)

/**
 * Every setting of the launcher, in one place: where it is and what finds it. The pages are laid out by topic, and this list is the
 * map of them (a test checks that each page has exactly the settings listed here, so a new setting cannot be left out).
 */
object SettingsIndex {
    private fun e(id: String, section: SettingsSection, @StringRes title: Int, vararg keywords: String) =
        SettingEntry(id, section, title, keywords.toList())

    val entries: List<SettingEntry> = listOf(
        // --- start+theme: the colors and the background
        e("theme.style", StartTheme, R.string.style_title, "style", "look", "classic", "glass", "droid", "material", "stile", "aspetto", "vetro"),
        e("theme.mode", StartTheme, R.string.theme_mode, "dark", "light", "night", "auto", "automatic", "sunset", "sunrise", "scuro", "chiaro", "notte", "automatico", "tramonto", "alba"),
        e("theme.from", StartTheme, R.string.theme_dark_from, "hours", "schedule", "dark from", "ore", "orario", "scuro dalle"),
        e("theme.to", StartTheme, R.string.theme_dark_to, "hours", "schedule", "dark until", "ore", "orario", "scuro fino alle"),
        e("theme.oled", StartTheme, R.string.theme_oled, "amoled", "black", "battery", "nero", "batteria"),
        e("theme.dynamic", StartTheme, R.string.style_dynamic, "material you", "wallpaper colors", "colori dello sfondo", "colori del telefono"),
        e("theme.accent", StartTheme, R.string.theme_accent, "color", "colour", "colors", "hue", "custom", "colore", "colori", "tonalita", "personalizzato"),
        e("theme.autoaccent", StartTheme, R.string.theme_auto_accent, "photo", "automatic", "color from photo", "foto", "automatico"),
        e("theme.background", StartTheme, R.string.theme_start_background, "photo", "wallpaper", "picture", "image", "sfondo", "foto", "immagine"),
        e("theme.framing", StartTheme, R.string.photo_framing, "zoom", "crop", "position", "inquadratura", "ritaglio", "posizione"),
        e("theme.parallax", StartTheme, R.string.theme_parallax, "parallax", "parallasse", "scroll", "depth", "motion", "scorrimento", "profondita", "movimento"),
        e("theme.panorama", StartTheme, R.string.theme_panorama, "panorama", "title", "big title", "parallax", "titolo", "titoli", "parallasse", "pagine"),

        // --- start: tiles, clock and the Start screens
        e("start.moretiles", Start, R.string.theme_more_tiles, "columns", "smaller", "bigger", "colonne", "piu riquadri", "piccoli"),
        e("start.tilenames", Start, R.string.theme_tile_names, "labels", "titles", "etichette", "titoli", "nomi"),
        e("start.spacing", Start, R.string.theme_tile_spacing, "gap", "margin", "narrow", "wide", "margine", "stretta", "larga", "spaziatura"),
        e("start.pages", Start, R.string.theme_start_pages, "screens", "pages", "schermate", "pagine", "quante"),
        e("start.hidestatus", Start, R.string.theme_hide_status_bar, "fullscreen", "full screen", "status bar", "notch", "schermo intero", "barra"),
        e("start.reset", Start, R.string.theme_reset_start, "restore", "default", "original", "undo", "ripristina", "predefinito", "originale", "reset"),
        e("start.clock", Start, R.string.theme_show_clock, "time", "date", "ora", "data", "orologio"),
        e("start.hours", Start, R.string.clock_hours, "12", "24", "am", "pm", "format", "formato", "ore"),
        e("start.alarm", Start, R.string.clock_show_alarm, "next alarm", "sveglia", "prossima sveglia"),
        e("start.week", Start, R.string.clock_show_week, "week number", "numero della settimana", "settimana"),
        e("start.clocktaps", Start, R.string.theme_clock_taps, "tap", "open clock", "open calendar", "tocca", "apri", "calendario"),
        e("start.appbar", Start, R.string.theme_app_bar, "bar", "ellipsis", "dots", "labels", "buttons", "edit", "barra", "puntini", "etichette", "pulsanti", "modifica"),

        // --- icons & themes
        e("icons.pack", Icons, R.string.icons_pack, "icons", "icon pack", "nova", "adw", "apex", "go launcher", "icone"),
        e("icons.export", Icons, R.string.theme_export, "theme file", "share", "save", "file tema", "condividi", "salva"),
        e("icons.import", Icons, R.string.theme_import, "theme file", "load", "open", "file tema", "carica", "apri"),

        // --- accessibility: how it reads and moves
        e("access.textsize", Accessibility, R.string.text_size, "font size", "bigger", "larger", "smaller", "text", "dimensione", "testo", "grande", "piccolo", "scala"),
        e("access.bold", Accessibility, R.string.theme_bold, "bold", "heavy", "weight", "grassetto", "marcato", "spesso"),
        e("access.systemfont", Accessibility, R.string.theme_system_font, "font", "typeface", "carattere", "tipo di carattere", "font di sistema"),
        e("access.motion", Accessibility, R.string.access_reduce_motion, "animations", "motion", "reduce", "animazioni", "movimento", "ridurre"),
        e("access.contrast", Accessibility, R.string.access_high_contrast, "contrast", "readable", "contrasto", "leggibile"),
        e("access.talkback", Accessibility, R.string.access_talkback, "screen reader", "voice", "blind", "lettore di schermo", "voce", "non vedenti"),

        // --- lock screen
        e("lock.preview", Lock, R.string.lock_preview, "try", "see", "prova", "vedi"),
        e("lock.photo", Lock, R.string.lock_photo, "wallpaper", "picture", "image", "sfondo", "foto", "immagine"),
        e("lock.alarm", Lock, R.string.lock_show_alarm, "next alarm", "sveglia"),
        e("lock.notifications", Lock, R.string.lock_show_notifications, "count", "notifiche", "numero"),
        e("lock.battery", Lock, R.string.lock_show_battery, "charge", "percentage", "batteria", "carica", "percentuale"),
        e("lock.weather", Lock, R.string.lock_show_weather, "forecast", "temperature", "meteo", "previsioni", "temperatura"),
        e("lock.event", Lock, R.string.lock_show_event, "calendar", "next event", "agenda", "calendario", "prossimo evento"),
        e("lock.apply", Lock, R.string.lock_apply, "wallpaper", "system lock screen", "sfondo", "blocco schermo del telefono"),
        e("lock.dream", Lock, R.string.lock_dream, "screensaver", "daydream", "charging", "salvaschermo", "ricarica", "dock"),
        e("lock.quick", Lock, R.string.lock_quick, "status", "counters", "badges", "icons", "stato", "contatori", "icone", "notifiche"),
        e("lock.quickapps", Lock, R.string.lock_quick_apps, "choose apps", "five", "scegli le app", "cinque"),
        e("lock.detail", Lock, R.string.lock_detail, "detailed", "last notification", "message", "dettagliato", "ultima notifica", "messaggio"),
        e("lock.music", Lock, R.string.lock_music, "music", "player", "cover", "skip", "pause", "musica", "copertina", "pausa"),

        // --- screen: rotation, opening animation, refresh rate, language
        e("screen.orientation", Screen, R.string.screen_orientation, "rotate", "landscape", "portrait", "orientation", "orizzontale", "verticale", "orientamento"),
        e("screen.launch", Screen, R.string.screen_launch, "animation", "turnstile", "expand", "opening", "animazione", "espansione", "apertura"),
        e("screen.language", Screen, R.string.screen_language, "english", "italian", "italiano", "inglese", "lingua"),
        e("screen.refresh", Screen, R.string.screen_high_refresh, "120 hz", "fps", "smooth", "frequenza", "fluidita", "hertz"),

        // --- gestures
        e("gest.doubletap", Gestures, R.string.gest_double_tap, "lock", "screen off", "blocca", "schermo", "tocco"),
        e("gest.homedouble", Gestures, R.string.gest_home_double, "home button", "twice", "tasto home", "due volte"),
        e("gest.twodown", Gestures, R.string.gest_two_down, "two finger", "swipe", "due dita", "scorri"),
        e("gest.twoup", Gestures, R.string.gest_two_up, "two finger", "swipe", "due dita", "scorri"),
        e("gest.recents", Gestures, R.string.gest_recents_title, "recent apps", "overview", "quick settings tile", "app recenti", "impostazioni rapide"),
        e("gest.lockservice", Gestures, R.string.gest_lock_service, "accessibility service", "lock screen", "servizio di accessibilita", "blocca lo schermo"),
        e("gest.edgepull", Gestures, R.string.gest_edge_pull, "action center", "notifications", "pull", "top", "tirata", "bordo", "notifiche"),
        e("gest.edgeback", Gestures, R.string.gest_edge_back_pan, "back", "swipe", "indietro", "bordo", "pagine"),
        e("gest.tapsound", Gestures, R.string.gest_tap_sound, "click", "sound", "audio", "suono", "tocco"),
        e("gest.taphaptics", Gestures, R.string.gest_tap_haptics, "vibration", "haptic", "buzz", "vibrazione", "tocco"),

        // --- notifications + actions
        e("ac.buttons", Actions, R.string.settings_quick_actions, "toggles", "buttons", "wifi", "bluetooth", "flashlight", "pulsanti", "interruttori", "scorciatoie"),
        e("ac.sliders", Actions, R.string.ac_sliders, "volume", "brightness", "torch", "luminosita", "torcia"),
        e("ac.peek", Actions, R.string.notif_peek, "notifications on tiles", "peek", "live tiles", "notifiche", "riquadri"),
        e("ac.reply", Actions, R.string.notif_inline_reply, "reply", "answer", "message", "rispondi", "risposta", "messaggio"),
        e("ac.group", Actions, R.string.notif_group, "group", "stack", "raggruppa", "raggruppamento"),
        e("ac.access", Actions, R.string.settings_notification_access, "permission", "listener", "permesso", "accesso", "notifiche"),
        e("ac.banner", Actions, R.string.ac_banner, "toast", "heads up", "popup", "notifications", "avviso", "notifiche", "banner"),
        e("ac.volume", Actions, R.string.ac_volume_panel, "volume keys", "panel", "hud", "sound", "tasti del volume", "pannello", "suono"),
        e("ac.notifcontrol", Actions, R.string.ac_notifcontrol, "per app", "app by app", "mute", "silence", "block", "app per app", "silenzia", "blocca"),
        e("ac.notifapps", Actions, R.string.ac_notifapps_title, "apps", "action center", "lock screen", "banner", "sound", "centro azioni", "blocco", "suono"),

        // --- battery
        e("battery.saving", Battery, R.string.saving_title, "power", "saver", "energy", "low battery", "risparmio", "energia", "batteria scarica"),

        // --- voice
        e("voice.button", Voice, R.string.voice_set_button, "microphone", "mic", "speech", "microfono", "parlare"),
        e("voice.offline", Voice, R.string.voice_set_offline, "on the phone", "no internet", "sul telefono", "senza rete", "offline"),
        e("voice.call", Voice, R.string.voice_set_call, "call", "dial", "phone", "chiama", "chiamata", "telefono"),
        e("voice.try", Voice, R.string.voice_try, "test", "speak", "prova", "parla"),

        // --- apps: the list
        e("apps.frequent", Apps, R.string.apps_frequent, "most used", "top", "favorites", "piu usate", "preferite"),
        e("apps.new", Apps, R.string.apps_new, "recent", "installed", "recenti", "installate", "nuove"),
        e("apps.compact", Apps, R.string.apps_compact, "small", "tight", "dense", "compatta", "piccola", "stretta"),
        e("apps.categories", Apps, R.string.apps_categories, "chips", "filter", "games", "social", "filtro", "categorie", "giochi"),
        e("apps.hidden", Apps, R.string.apps_hidden, "hide", "show", "nascoste", "nascondi", "mostra"),
        e("apps.renamed", Apps, R.string.apps_renamed, "rename", "name", "rinomina", "nome", "rinominate"),
        e("apps.locked", Apps, R.string.apps_locked, "lock", "pin", "fingerprint", "blocco", "bloccate", "impronta", "protette"),

        // --- search
        e("search.contacts", Search, R.string.apps_search_contacts, "people", "phone book", "persone", "rubrica", "contatti"),
        e("search.settings", Search, R.string.apps_search_settings, "system", "wifi", "sistema", "impostazioni"),
        e("search.launcher", Search, R.string.apps_search_launcher, "these settings", "this app", "queste impostazioni", "launcher"),
        e("search.tiles", Search, R.string.apps_search_tiles, "notes", "lists", "countdown", "note", "liste", "riquadri", "start"),
        e("search.web", Search, R.string.apps_search_web, "internet", "browser", "google", "ricerca web"),
        e("search.engine", Search, R.string.apps_engine, "duckduckgo", "google", "bing", "brave", "ecosia", "startpage", "qwant", "motore", "browser"),
        e("search.answers", Search, R.string.apps_answers, "calculator", "convert", "units", "timer", "calcolatrice", "conversioni", "unita", "risposte"),
        e("search.assistant", Search, R.string.apps_assistant, "reminder", "event", "note", "keep", "lens", "near me", "qr", "promemoria", "evento", "nota", "vicino a me", "scansiona"),
        e("search.dial", Search, R.string.apps_dial, "t9", "dial", "keypad", "numbers", "tastierino", "chiamata", "numeri"),
        e("search.today", Search, R.string.apps_today, "card", "agenda", "today", "scheda", "oggi", "giornata"),
        e("search.currency", Search, R.string.apps_currency, "money", "exchange", "euro", "dollar", "valute", "cambio", "soldi"),
        e("search.calendar", Search, R.string.apps_calendar, "events", "agenda", "eventi", "calendario"),
        e("search.remember", Search, R.string.apps_remember, "history", "recent searches", "cronologia", "ricerche recenti"),
        e("search.forget", Search, R.string.apps_forget, "clear", "delete history", "cancella", "elimina", "cronologia"),

        // --- live tiles
        e("live.city", LiveTiles, R.string.weather_city, "weather", "location", "gps", "meteo", "citta", "posizione"),
        e("live.fahrenheit", LiveTiles, R.string.weather_fahrenheit, "celsius", "degrees", "units", "gradi", "unita", "temperatura"),
        e("live.master", LiveTiles, R.string.live_master, "whatsapp", "telegram", "instagram", "messages", "notifications", "messaggi", "notifiche"),
        e("live.access", LiveTiles, R.string.live_access, "notification access", "permission", "permesso", "accesso alle notifiche"),
        e("live.messages", LiveTiles, R.string.live_messages, "chats", "conversations", "messaggi", "chat", "conversazioni"),
        e("live.hidetext", LiveTiles, R.string.live_hide_text, "privacy", "hide", "private", "nascondi", "riservato"),
        e("live.pictures", LiveTiles, R.string.live_pictures, "photos", "images", "foto", "immagini"),
        e("live.playing", LiveTiles, R.string.live_playing, "music", "song", "player", "musica", "brano"),
        e("live.progress", LiveTiles, R.string.live_progress, "download", "upload", "percentage", "avanzamento", "scarica"),
        e("live.apps", LiveTiles, R.string.live_apps, "per app", "switch off", "una per una", "spegni"),
        e("live.report", LiveTiles, R.string.live_report, "debug", "reads", "legge", "diagnosi"),
        e("live.transition", LiveTiles, R.string.transition_title, "flip", "slide", "animation", "tile", "rotazione", "scorrimento", "animazione", "faccia"),
        e("live.news", LiveTiles, R.string.news_source_title, "google news", "rss", "feed", "headlines", "source", "notizie", "fonte", "titoli", "giornale"),
        e("live.financebase", LiveTiles, R.string.finance_base_title, "currency", "euro", "dollar", "valuta", "cambio", "moneta"),
        e("live.financeitems", LiveTiles, R.string.finance_items_title, "bitcoin", "crypto", "exchange", "currencies", "valute", "criptovalute", "borsa"),
        e("live.published", LiveTiles, R.string.tp_toggle, "third party", "publish", "terze parti", "pubblica", "tile"),

        // --- profiles
        e("profile.pause", Profiles, R.string.profile_pause, "work profile", "private space", "pause", "profilo di lavoro", "spazio privato", "pausa"),
        e("profile.showapps", Profiles, R.string.profile_show_apps, "work apps", "private apps", "app di lavoro", "app private"),

        // --- backup + sync
        e("backup.folder", Backup, R.string.backup_folder, "drive", "directory", "cartella", "salvataggio"),
        e("backup.every", Backup, R.string.backup_every, "automatic", "schedule", "daily", "weekly", "automatico", "ogni", "giorni"),
        e("backup.now", Backup, R.string.backup_now, "save", "salva", "subito"),
        e("backup.restore", Backup, R.string.backup_restore, "load", "ripristina", "recupera", "carica"),
        e("backup.forget", Backup, R.string.backup_forget_folder, "remove folder", "dimentica", "togli"),
        e("sync.enable", Backup, R.string.sync_enable, "sync", "phones", "encrypted", "sincronizza", "telefoni", "cifrata"),
        e("sync.passphrase", Backup, R.string.sync_passphrase, "password", "secret", "frase", "segreta", "parola"),
        e("sync.now", Backup, R.string.sync_now, "sync", "sincronizza", "adesso"),
        e("backup.export", Backup, R.string.backup_export, "file", "save", "layout", "esporta", "salva", "disposizione"),
        e("backup.import", Backup, R.string.backup_import, "file", "load", "layout", "importa", "carica", "disposizione"),

        // --- kid's corner
        e("kids.start", Kids, R.string.kids_start, "child", "children", "kid", "bambino", "bambini", "figli"),
        e("kids.apps", Kids, R.string.kids_apps, "allowed", "child", "consentite", "scegli", "bambino"),

        // --- about
        e("about.version", About, R.string.about_version, "build", "info", "versione"),
        e("about.tutorial", About, R.string.tut_replay, "tour", "help", "guide", "intro", "aiuto", "guida", "tutorial"),
        e("about.credits", About, R.string.about_credits, "licenses", "thanks", "open source", "licenze", "ringraziamenti"),
    )

    private val byId: Map<String, SettingEntry> = entries.associateBy { it.id }

    fun get(id: String): SettingEntry? = byId[id]

    fun of(section: SettingsSection): List<SettingEntry> = entries.filter { it.section == section }

    /**
     * An index to search the settings (and the sections themselves) in the language of the phone. A setting is found by its name,
     * and less strongly by its keywords and by the name of its section ("gestures" lists the gestures); a section by its name and
     * what it says it holds.
     */
    fun searchIndex(context: Context): SearchIndex<SettingHit> {
        val rows = buildList {
            SettingsSection.entries.forEach { section ->
                add(SearchEntry(SettingHit(section, null), context.getString(section.title), listOf(context.getString(section.description), context.getString(section.group.title))))
            }
            entries.forEach { entry ->
                val place = context.getString(entry.section.title)
                add(SearchEntry(SettingHit(entry.section, entry), context.getString(entry.title), entry.keywords + place, boost = 0))
            }
        }
        return SearchIndex(rows, keywordPrefixFrom = 3)
    }
}
