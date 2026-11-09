package com.metrowin.launcher.ui.settings

import com.metrowin.launcher.data.WeatherBlock
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
import com.metrowin.launcher.ui.settings.SettingsSection.Notifications
import com.metrowin.launcher.ui.settings.SettingsSection.OtherProjects
import com.metrowin.launcher.ui.settings.SettingsSection.StartBars
import com.metrowin.launcher.ui.settings.SettingsSection.StatusBar
import com.metrowin.launcher.ui.settings.SettingsSection.StartBehaviour
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
import com.metrowin.launcher.ui.settings.SettingsSection.BugReport

/**
 * One setting of the launcher, as the index knows it: the [section] it lives in, the [title] it has on its page and other words
 * (Italian and English; the other languages have theirs per section, see [SettingsSection.words]) that find it. The [id] is what the control on the page says it is (see [settingAnchor]), which is how the
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
        e("theme.nightaccent", StartTheme, R.string.theme_night_accent, "night", "dark", "colour", "color", "notte", "scuro", "colore", "accento"),
        e("theme.autoaccent", StartTheme, R.string.theme_auto_accent, "photo", "automatic", "color from photo", "foto", "automatico"),
        e("theme.background", StartTheme, R.string.theme_start_background, "photo", "wallpaper", "picture", "image", "sfondo", "foto", "immagine"),
        e("theme.syswallpaper", StartTheme, R.string.theme_system_wallpaper, "wallpaper", "recents", "recent apps", "overview", "multitasking", "background", "sfondo", "app recenti", "multitasking", "sfondo di android"),
        e("theme.pagebackgrounds", StartTheme, R.string.page_photos, "photo", "page", "background", "wallpaper", "pagina", "sfondo", "foto"),
        e("theme.photofit", StartTheme, R.string.photo_fit, "fill", "fit", "stretch", "tile", "center", "wallpaper", "riempi", "adatta", "allunga", "affianca", "centra", "sfondo"),
        e("theme.accentuse", StartTheme, R.string.accent_use, "accent", "colour", "color", "where", "grey", "colore", "dove", "grigio", "tinta"),
        e("theme.framing", StartTheme, R.string.photo_framing, "zoom", "crop", "position", "inquadratura", "ritaglio", "posizione"),
        e("theme.tileopacity", StartTheme, R.string.theme_tile_opacity, "transparent", "translucent", "see through", "photo", "tiles", "trasparenza", "trasparente", "riquadri", "foto", "sfondo"),
        e("theme.photoinside", StartTheme, R.string.theme_photo_inside, "photo", "background", "inside", "tiles", "window", "foto", "sfondo", "dentro", "riquadri", "finestra"),
        e("theme.parallax", StartTheme, R.string.theme_parallax, "parallax", "parallasse", "scroll", "depth", "motion", "scorrimento", "profondita", "movimento"),
        e("theme.panorama", StartTheme, R.string.theme_panorama, "panorama", "title", "big title", "parallax", "titolo", "titoli", "parallasse", "pagine"),

        // --- start: tiles and screens (clock, bars and behaviour are listed with their own sections below)
        e("start.moretiles", Start, R.string.theme_more_tiles, "columns", "smaller", "bigger", "colonne", "piu riquadri", "piccoli"),
        e("start.columns", Start, R.string.start_columns, "columns", "tiles per row", "across", "grid", "colonne", "riquadri per riga", "griglia", "quantita"),
        e("start.free", Start, R.string.start_free, "free", "free mode", "anywhere", "position", "gaps", "libero", "modalita libero", "ovunque", "posizione", "spazi"),
        e("start.tilenames", Start, R.string.theme_tile_names, "labels", "titles", "etichette", "titoli", "nomi"),
        e("start.spacing", Start, R.string.theme_tile_spacing, "gap", "margin", "narrow", "wide", "margine", "stretta", "larga", "spaziatura"),
        e("start.pages", Start, R.string.theme_start_pages, "screens", "pages", "schermate", "pagine", "quante"),
        e("lock.overlay", Lock, R.string.lock_overlay, "lock", "screen", "standby", "wake", "blocco", "schermo", "standby", "accensione"),
        e("start.favorite", Start, R.string.theme_favorite_page, "favourite", "favorite", "return", "page", "screen", "preferito", "preferita", "ritorno", "pagina", "schermata"),
        e("start.home", Start, R.string.theme_home_page, "home", "main", "second", "left", "secondary", "principale", "secondario", "sinistra", "destra"),
        e("start.appsleft", Start, R.string.theme_apps_left, "left", "apps", "list", "side", "sinistra", "elenco", "app", "lato"),
        e("start.appsarrow", Start, R.string.theme_apps_arrow, "arrow", "next", "button", "circle", "freccia", "pulsante", "cerchio", "avanti", "nascondi", "dots", "puntini", "punti"),
        e("start.antiburnin", StartBehaviour, R.string.theme_anti_burn_in, "burn", "burn-in", "oled", "amoled", "screen", "pixel", "schermo", "bruciare", "ritenzione", "immagine fantasma"),
        e("start.hidestatus", StartBars, R.string.theme_hide_status_bar, "fullscreen", "full screen", "status bar", "notch", "schermo intero", "barra"),
        e("statusbar.metro", StatusBar, R.string.theme_metro_status_bar, "windows phone", "status bar", "signal", "wifi", "battery", "percentage", "clock", "barra di stato", "segnale", "batteria", "percentuale", "orario"),
        e("statusbar.charge", StatusBar, R.string.charge_animation, "charging", "charge", "battery", "animation", "ricarica", "animazione", "batteria"),
        e("statusbar.apps", StatusBar, R.string.theme_metro_status_bar_apps, "windows phone", "status bar", "other apps", "over apps", "barra di stato", "altre app", "sopra le app"),
        e("statusbar.notifications", StatusBar, R.string.statusbar_notifications, "status bar", "notifications", "bell", "barra di stato", "notifiche", "campanella"),
        e("statusbar.airplane", StatusBar, R.string.statusbar_airplane, "status bar", "airplane", "flight", "barra di stato", "aereo", "volo"),
        e("statusbar.hotspot", StatusBar, R.string.statusbar_hotspot, "status bar", "hotspot", "tethering", "barra di stato", "condivisione"),
        e("statusbar.vpn", StatusBar, R.string.statusbar_vpn, "status bar", "vpn", "barra di stato"),
        e("statusbar.location", StatusBar, R.string.statusbar_location, "status bar", "location", "gps", "barra di stato", "posizione"),
        e("statusbar.bluetooth", StatusBar, R.string.statusbar_bluetooth, "status bar", "bluetooth", "barra di stato"),
        e("statusbar.alarm", StatusBar, R.string.statusbar_alarm, "status bar", "alarm", "barra di stato", "sveglia"),
        e("statusbar.sound", StatusBar, R.string.statusbar_sound, "status bar", "silent", "vibrate", "ringer", "barra di stato", "silenzioso", "vibrazione", "suoneria"),
        e("statusbar.dnd", StatusBar, R.string.statusbar_dnd, "status bar", "do not disturb", "dnd", "barra di stato", "non disturbare"),
        e("statusbar.saver", StatusBar, R.string.statusbar_saver, "status bar", "battery saver", "barra di stato", "risparmio energetico", "risparmio batteria"),
        e("statusbar.signal", StatusBar, R.string.statusbar_signal, "status bar", "signal", "mobile", "bars", "barra di stato", "segnale", "rete mobile", "tacche"),
        e("statusbar.network", StatusBar, R.string.statusbar_network, "status bar", "5g", "4g", "3g", "lte", "edge", "network type", "barra di stato", "tipo di rete", "connettività"),
        e("statusbar.wifi", StatusBar, R.string.statusbar_wifi, "status bar", "wifi", "wi-fi", "barra di stato"),
        e("statusbar.batteryPercent", StatusBar, R.string.statusbar_battery_percent, "status bar", "battery", "percentage", "barra di stato", "batteria", "percentuale"),
        e("statusbar.battery", StatusBar, R.string.statusbar_battery, "status bar", "battery", "barra di stato", "batteria", "carica"),
        e("statusbar.time", StatusBar, R.string.statusbar_time, "status bar", "time", "clock", "barra di stato", "ora", "orologio"),
        e("start.reset", Start, R.string.theme_reset_start, "restore", "default", "original", "undo", "ripristina", "predefinito", "originale", "reset"),
        e("start.clock", StartBars, R.string.theme_show_clock, "time", "date", "ora", "data", "orologio"),
        e("start.hours", StartBars, R.string.clock_hours, "12", "24", "am", "pm", "format", "formato", "ore"),
        e("start.alarm", StartBars, R.string.clock_show_alarm, "next alarm", "sveglia", "prossima sveglia"),
        e("start.zone", StartBars, R.string.clock_second_zone, "clock", "time", "zone", "world", "timezone", "orologio", "fuso", "ora", "secondo"),
        e("start.stepgoal", StartBehaviour, R.string.steps_goal_setting, "steps", "goal", "walk", "passi", "obiettivo", "contapassi"),
        e("start.topafter", StartBehaviour, R.string.start_top_after, "top", "scroll", "return", "minutes", "cima", "torna", "minuti", "inizio"),
        e("start.lock", StartBehaviour, R.string.start_lock, "lock", "layout", "fixed", "move", "blocca", "fisso", "sposta", "disposizione"),
        e("start.lower", StartBehaviour, R.string.start_lower, "one hand", "thumb", "reach", "lower", "una mano", "pollice", "raggiungere", "piu in basso"),
        e("start.large", Start, R.string.start_large, "large", "size", "4x4", "big", "grande", "dimensione", "riquadro grande"),
        e("start.groups", Start, R.string.start_groups, "group", "title", "heading", "gruppo", "titolo", "gruppi"),
        e("start.collection", Start, R.string.start_collection, "collage", "collection", "photos", "mosaico", "foto"),
        e("start.agenda", Start, R.string.start_agenda, "calendar", "events", "month", "agenda", "eventi", "mese", "calendario"),
        e("start.batteryleft", Start, R.string.start_battery_left, "battery", "time left", "hours", "batteria", "durata", "tempo rimasto"),
        e("start.musiccontrols", Start, R.string.start_music_controls, "music", "play", "pause", "skip", "musica", "pausa", "controlli"),
        e("start.layouts", Start, R.string.layouts_title, "layout", "profile", "home", "work", "travel", "casa", "lavoro", "viaggio", "profili"),
        e("start.layoutschedule", Start, R.string.layouts_schedule, "schedule", "hour", "automatic", "orario", "automatico", "ora"),
        e("start.week", StartBars, R.string.clock_show_week, "week number", "numero della settimana", "settimana"),
        e("start.clocktaps", StartBars, R.string.theme_clock_taps, "tap", "open clock", "open calendar", "tocca", "apri", "calendario"),
        e("start.appbar", StartBars, R.string.theme_app_bar, "bar", "ellipsis", "dots", "labels", "buttons", "edit", "barra", "puntini", "etichette", "pulsanti", "modifica"),
        e("start.actionbar", StartBars, R.string.action_bar_title, "retro", "super", "app bar", "application bar", "buttons", "ellipsis", "barra", "barra delle azioni", "azioni", "pulsanti"),

        // --- icons & themes
        e("icons.shape", Icons, R.string.icons_shape, "shape", "round", "circle", "square", "squircle", "forma", "rotonde", "cerchio", "quadrate"),
        e("icons.tilecolors", Icons, R.string.icons_tile_colors, "colour", "color", "tile", "icon", "colore", "riquadro", "icona", "dominante"),
        e("gest.gameapps", Gestures, R.string.gest_game_apps, "game", "games", "add", "list", "not recognised", "gioco", "giochi", "aggiungi", "elenco", "non riconosciuto"),
        e("gest.gamebars", Gestures, R.string.gest_game_bars, "game", "games", "bars", "hide", "status bar", "navigation", "gioco", "giochi", "barre", "nascondi", "barra di stato", "navigazione"),
        e("gest.navspacing", Gestures, R.string.gest_navbar_spacing, "navigation", "bar", "spacing", "distance", "buttons", "barra", "distanza", "tasti", "spaziatura"),
        e("gest.navbarhold", Gestures, R.string.gest_navbar_hold, "navigation", "bar", "hold", "recent", "long", "barra", "tieni", "recenti", "lunga"),
        e("icons.glass", Icons, R.string.icons_glass, "glass", "transparent", "see-through", "icon", "tile", "vetro", "trasparente", "icona", "riquadro"),
        e("icons.largetiles", Icons, R.string.icons_large_tiles, "large", "big", "tile", "icon", "full", "grandi", "grande", "riquadro", "icona", "riempie"),
        e("icons.find", Icons, R.string.icons_pack_find, "play store", "download", "icon pack", "install", "scarica", "cerca", "icone"),
        e("icons.pack", Icons, R.string.icons_pack, "icons", "icon pack", "nova", "adw", "apex", "go launcher", "icone"),
        e("icons.export", Icons, R.string.theme_export, "theme file", "share", "save", "file tema", "condividi", "salva"),
        e("icons.import", Icons, R.string.theme_import, "theme file", "load", "open", "file tema", "carica", "apri"),

        // --- accessibility: how it reads and moves
        e("access.textsize", Accessibility, R.string.text_size, "font size", "bigger", "larger", "smaller", "text", "dimensione", "testo", "grande", "piccolo", "scala"),
        e("access.bold", Accessibility, R.string.theme_bold, "bold", "heavy", "weight", "grassetto", "marcato", "spesso"),
        e("access.systemfont", Accessibility, R.string.theme_system_font, "font", "typeface", "carattere", "tipo di carattere", "font di sistema"),
        e("access.font", Accessibility, R.string.access_font, "font", "typeface", "custom", "ttf", "otf", "my font", "carattere", "personalizzato", "il mio carattere", "tipo di carattere"),
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
        e("lock.sensor", Lock, R.string.lock_sensor, "fingerprint", "biometric", "finger", "impronta", "sensore", "biometria"),
        e("lock.sensorY", Lock, R.string.lock_sensor_position, "fingerprint position", "sensor height", "posizione impronta", "altezza sensore"),
        e("lock.sensorOwn", Lock, R.string.lock_sensor_own, "fingerprint icon", "draw", "icona impronta", "disegna"),
        e("lock.oneRow", Lock, R.string.lock_icons_one_row, "single row", "one row", "small", "icons", "notifications", "una riga", "piccole", "icone", "notifiche", "riga"),
        e("lock.music", Lock, R.string.lock_music, "music", "player", "cover", "skip", "pause", "musica", "copertina", "pausa"),

        // --- screen: rotation, opening animation, refresh rate, language
        e("screen.orientation", Screen, R.string.screen_orientation, "rotate", "landscape", "portrait", "orientation", "orizzontale", "verticale", "orientamento"),
        e("screen.launch", Screen, R.string.screen_launch, "animation", "turnstile", "expand", "opening", "fade", "slide", "rise", "zoom", "none", "instant", "windows phone", "original", "animazione", "espansione", "apertura", "dissolvenza", "scorrimento", "salita", "nessuna", "originale"),
        e("screen.padding", Screen, R.string.screen_padding, "margin", "margins", "spacing", "edges", "sides", "padding", "compact", "wide", "margini", "bordi", "spazio", "larghezza"),
        e("screen.language", Screen, R.string.screen_language, "english", "italian", "italiano", "inglese", "lingua"),
        e("screen.refresh", Screen, R.string.screen_high_refresh, "120 hz", "fps", "smooth", "frequenza", "fluidita", "hertz"),

        // --- gestures
        e("gest.doubletap", Gestures, R.string.gest_double_tap, "lock", "screen off", "blocca", "schermo", "tocco"),
        e("gest.homedouble", Gestures, R.string.gest_home_double, "home button", "twice", "tasto home", "due volte"),
        e("gest.twodown", Gestures, R.string.gest_two_down, "two finger", "swipe", "due dita", "scorri"),
        e("gest.twoup", Gestures, R.string.gest_two_up, "two finger", "swipe", "due dita", "scorri"),
        e("gest.recents", Gestures, R.string.gest_recents_title, "recent apps", "overview", "quick settings tile", "app recenti", "impostazioni rapide"),
        e("gest.banksafe", Gestures, R.string.bank_safe, "bank", "banking", "security", "overlay", "accessibility", "blocked", "banca", "banco", "sicurezza", "bloccata", "accessibilita"),
        e("gest.lockservice", Gestures, R.string.gest_lock_service, "accessibility service", "lock screen", "servizio di accessibilita", "blocca lo schermo"),
        e("gest.navbar", Gestures, R.string.gest_navbar, "navigation bar", "buttons", "back", "home", "three buttons", "windows", "barra di navigazione", "pulsanti", "indietro", "tre tasti"),
        e("gest.edgepull", Gestures, R.string.gest_edge_pull, "action center", "notifications", "pull", "top", "tirata", "bordo", "notifiche"),
        e("gest.edgeback", Gestures, R.string.gest_edge_back_pan, "back", "swipe", "indietro", "bordo", "pagine"),
        e("gest.tapsound", Gestures, R.string.gest_tap_sound, "click", "sound", "audio", "suono", "tocco"),
        e("gest.animspeed", Gestures, R.string.feel_speed, "animation", "speed", "fast", "slow", "animazioni", "velocita", "veloce", "lento"),
        e("gest.tilt", Gestures, R.string.feel_tilt, "tilt", "press", "lean", "inclinazione", "pressione"),
        e("gest.vibration", Gestures, R.string.feel_vibration, "vibration", "haptic", "strength", "vibrazione", "forza", "intensita"),
        e("gest.taphaptics", Gestures, R.string.gest_tap_haptics, "vibration", "haptic", "buzz", "vibrazione", "tocco"),

        // --- notifications + actions
        e("ac.buttons", Actions, R.string.settings_quick_actions, "toggles", "buttons", "wifi", "bluetooth", "flashlight", "pulsanti", "interruttori", "scorciatoie"),
        e("ac.shizuku", Actions, R.string.ac_shizuku, "shizuku", "wifi", "bluetooth", "toggle", "switch", "radio", "interruttore", "accendi", "spegni"),
        e("ac.torchoff", Actions, R.string.ac_torch_off, "flashlight", "torch", "timer", "automatic", "torcia", "spegni", "automatico", "minuti"),
        e("ac.profiles", Actions, R.string.ac_profiles, "volume", "presets", "profiles", "sound", "ringtone", "volumi", "profili", "suoneria", "preimpostazioni"),
        e("ac.sliders", Actions, R.string.ac_sliders, "volume", "brightness", "torch", "luminosita", "torcia"),
        e("ac.peek", Notifications, R.string.notif_peek, "notifications on tiles", "peek", "live tiles", "notifiche", "riquadri"),
        e("ac.reply", Notifications, R.string.notif_inline_reply, "reply", "answer", "message", "rispondi", "risposta", "messaggio"),
        e("ac.group", Notifications, R.string.notif_group, "group", "stack", "raggruppa", "raggruppamento"),
        e("ac.access", Notifications, R.string.settings_notification_access, "permission", "listener", "permesso", "accesso", "notifiche"),
        e("ac.enabled", Notifications, R.string.ac_enabled, "action center", "off", "disable", "stock", "android notifications", "default", "disattiva", "spegni", "notifiche di android", "pannello", "centro notifiche"),
        e("ac.banner", Notifications, R.string.ac_banner, "toast", "heads up", "popup", "notifications", "avviso", "notifiche", "banner"),
        e("ac.overlay", Notifications, R.string.ac_overlay, "overlay", "over other apps", "display over", "draw over", "sopra altre app", "fuori dal launcher", "notifiche", "banner"),
        e("ac.mute", Notifications, R.string.ac_mute, "mute", "silence", "app", "hour", "silenzia", "zittisci", "ora", "notifiche"),
        e("ac.clearonopen", Notifications, R.string.ac_clear_on_open, "open", "opening", "clear", "badge", "number", "aprire", "apri", "numero", "pulisci"),
        e("ac.priority", Notifications, R.string.ac_priority, "priority", "messages", "calls", "first", "priorita", "messaggi", "chiamate", "prima"),
        e("ac.filter", Notifications, R.string.ac_filter, "filter", "words", "block", "spam", "filtro", "parole", "blocca"),
        e("ac.filterwords", Notifications, R.string.ac_filter_words, "filter", "words", "add", "filtro", "parole", "aggiungi"),
        e("ac.dndminutes", Notifications, R.string.ac_dnd_minutes, "do not disturb", "quiet", "timed", "minutes", "non disturbare", "silenzioso", "tempo", "minuti"),
        e("ac.panelsounds", Notifications, R.string.ac_panel_sounds, "sound", "glide", "pull", "panel", "suono", "scorrimento", "pannello", "abbassa", "alza"),
        e("ac.sounds", Notifications, R.string.ac_sounds, "sound", "chime", "tone", "audio", "suono", "suoni", "notifica", "melodia"),
        e("ac.hide", Notifications, R.string.ac_hide_system, "hide", "double", "duplicate", "android notification", "system notification", "nascondi", "doppie", "notifica di android", "banner"),
        e("ac.edge", Notifications, R.string.ac_edge, "action center", "edge", "swipe", "pull down", "over other apps", "bordo", "sopra altre app", "centro notifiche", "striscia"),
        e("ac.volume", Actions, R.string.ac_volume_panel, "volume keys", "panel", "hud", "sound", "tasti del volume", "pannello", "suono"),
        e("ac.notifcontrol", Notifications, R.string.ac_notifcontrol, "per app", "app by app", "mute", "silence", "block", "app per app", "silenzia", "blocca"),
        e("ac.notifapps", Notifications, R.string.ac_notifapps_title, "apps", "action center", "lock screen", "banner", "sound", "centro azioni", "blocco", "suono"),

        // --- battery
        e("battery.saving", Battery, R.string.saving_title, "power", "saver", "energy", "low battery", "risparmio", "energia", "batteria scarica"),

        // --- voice
        e("voice.icon", Voice, R.string.voice_set_icon, "assistant", "microphone", "icon", "start", "assistente", "microfono", "icona"),
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
        e("search.commands", Search, R.string.apps_commands, "torch", "wifi", "silent", "switch", "command", "toggle", "torcia", "silenzioso", "comando", "interruttore", "accendi", "spegni"),
        e("search.tip", Search, R.string.apps_tip, "tip", "hint", "daily", "suggerimento", "consiglio", "giorno", "trucco"),
        e("start.undosteps", StartBehaviour, R.string.start_undo_steps, "undo", "history", "steps", "annulla", "cronologia", "passaggi"),
        e("start.undonow", StartBehaviour, R.string.start_undo_now_title, "undo", "last change", "annulla", "ultima modifica"),
        e("search.assistant", Search, R.string.apps_assistant, "reminder", "event", "note", "keep", "lens", "near me", "qr", "promemoria", "evento", "nota", "vicino a me", "scansiona"),
        e("search.dial", Search, R.string.apps_dial, "t9", "dial", "keypad", "numbers", "tastierino", "chiamata", "numeri"),
        e("search.today", Search, R.string.apps_today, "card", "agenda", "today", "scheda", "oggi", "giornata"),
        e("search.todayweather", Search, R.string.today_show_weather, "card", "today", "weather", "scheda", "oggi", "meteo"),
        e("search.todaynear", Search, R.string.today_show_near, "card", "today", "near me", "places", "scheda", "oggi", "vicino a me", "posti", "luoghi"),
        e("search.todaydate", Search, R.string.today_show_date, "card", "today", "date", "day", "scheda", "oggi", "data", "giorno"),
        e("search.todayalarm", Search, R.string.today_show_alarm, "card", "today", "alarm", "clock", "scheda", "oggi", "sveglia", "allarme"),
        e("search.currency", Search, R.string.apps_currency, "money", "exchange", "euro", "dollar", "valute", "cambio", "soldi"),
        e("search.notifications", Search, R.string.search_notifications, "notifications", "messages", "notifiche", "messaggi"),
        e("search.operators", Search, R.string.search_operators, "operator", "prefix", "app:", "contact:", "operatori", "prefisso", "scorciatoie"),
        e("search.files", Search, R.string.search_files_title, "files", "documents", "pdf", "photos", "videos", "music", "file", "documenti", "foto", "musica", "cerca file"),
        e("search.calendar", Search, R.string.apps_calendar, "events", "agenda", "eventi", "calendario"),
        e("search.remember", Search, R.string.apps_remember, "history", "recent searches", "cronologia", "ricerche recenti"),
        e("search.forget", Search, R.string.apps_forget, "clear", "delete history", "cancella", "elimina", "cronologia"),

        // --- live tiles
        e("live.city", LiveTiles, R.string.weather_city, "weather", "location", "gps", "meteo", "citta", "posizione", "follow", "cities", "segui", "altre citta"),
        e("live.gallery", LiveTiles, R.string.gallery_heading, "photos", "gallery", "album", "pictures", "chosen photos", "foto", "galleria", "immagini", "cartelle", "foto scelte"),
        e("live.fahrenheit", LiveTiles, R.string.weather_fahrenheit, "celsius", "degrees", "units", "gradi", "unita", "temperatura"),
        *WeatherBlock.entries.map { b -> e("live.wx.${b.code}", LiveTiles, b.label, "weather", "forecast", "meteo", "previsioni", "tempo") }.toTypedArray(),
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
        e("live.financebase", LiveTiles, R.string.finance_base_title, "currency", "euro", "dollar", "valuta", "cambio", "moneta"),
        e("live.financeitems", LiveTiles, R.string.finance_items_title, "bitcoin", "crypto", "exchange", "currencies", "valute", "criptovalute", "borsa"),
        e("live.published", LiveTiles, R.string.tp_toggle, "third party", "publish", "terze parti", "pubblica", "tile"),

        // --- profiles
        e("profile.pause", Profiles, R.string.profile_pause, "work profile", "private space", "pause", "profilo di lavoro", "spazio privato", "pausa"),
        e("profile.showapps", Profiles, R.string.profile_show_apps, "work apps", "private apps", "app di lavoro", "app private"),

        // --- backup + sync
        e("backup.folder", Backup, R.string.backup_folder, "drive", "directory", "cartella", "salvataggio"),
        e("backup.every", Backup, R.string.backup_every, "automatic", "schedule", "daily", "weekly", "automatico", "ogni", "giorni"),
        e("backup.apps", Backup, R.string.apps_backup_heading, "apps", "reinstall", "new phone", "list", "installed", "app installate", "nuovo telefono", "reinstalla", "elenco"),
        e("backup.copies", Backup, R.string.backup_copies, "copies", "versions", "keep", "history", "copie", "versioni", "conserva", "cronologia"),
        e("backup.share", Backup, R.string.backup_share_layout, "share", "send", "file", "condividi", "invia"),
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

        // --- bug ticket
        e("bugreport.describe", BugReport, R.string.bug_field, "bug", "problem", "error", "report", "describe", "problema", "errore", "segnalazione", "descrivi"),
        e("bugreport.send", BugReport, R.string.bug_send, "bug", "ticket", "log", "mail", "email", "send", "segnala", "invia", "errore"),

        // --- about
        e("about.version", About, R.string.about_version, "build", "info", "versione"),
        e("about.update", About, R.string.update_title, "update", "github", "download", "install", "version", "aggiorna", "aggiornamento", "scarica", "installa", "versione"),
        e("about.updatecheck", About, R.string.update_check, "update", "check", "new version", "controlla", "aggiornamenti", "nuova versione"),
        e("about.updateauto", About, R.string.update_auto, "update", "automatic", "12 hours", "aggiornamenti", "automatico", "12 ore"),
        e("about.phoneinfo", About, R.string.about_phone_toggle, "phone", "model", "memory", "storage", "uptime", "telefono", "modello", "memoria", "spazio"),
        e("about.phone", About, R.string.about_phone_title, "phone", "model", "ram", "patch", "telefono", "modello", "informazioni"),
        e("about.tutorial", About, R.string.tut_replay, "tour", "help", "guide", "intro", "aiuto", "guida", "tutorial"),
        e("projects.foss", OtherProjects, R.string.prj_foss, "foss", "open source", "keyboard", "tastiera", "launcher", "github", "software libero"),
        e("projects.nonfoss", OtherProjects, R.string.prj_nonfoss, "spesatracker", "play store", "google play", "spese", "budget", "non foss"),
        e("about.credits", About, R.string.about_credits, "licenses", "thanks", "open source", "licenze", "ringraziamenti"),
    )

    private val byId: Map<String, SettingEntry> = entries.associateBy { it.id }

    fun get(id: String): SettingEntry? = byId[id]

    fun of(section: SettingsSection): List<SettingEntry> = entries.filter { it.section == section }

    /** The words of the section in the language of the phone: the keywords written in the code are only English and Italian. */
    private fun words(context: Context, section: SettingsSection) = context.getString(section.words).split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * An index to search the settings (and the sections themselves) in the language of the phone. A setting is found by its name,
     * and less strongly by its keywords and by the name of its section ("gestures" lists the gestures); a section by its name and
     * what it says it holds.
     */
    fun searchIndex(context: Context): SearchIndex<SettingHit> {
        val rows = buildList {
            SettingsSection.entries.forEach { section ->
                add(SearchEntry(SettingHit(section, null), context.getString(section.title), listOf(context.getString(section.description), context.getString(section.group.title)) + words(context, section)))
            }
            entries.forEach { entry ->
                val place = context.getString(entry.section.title)
                add(SearchEntry(SettingHit(entry.section, entry), context.getString(entry.title), entry.keywords + place + words(context, entry.section), boost = 0))
            }
        }
        return SearchIndex(rows, keywordPrefixFrom = 3)
    }
}
