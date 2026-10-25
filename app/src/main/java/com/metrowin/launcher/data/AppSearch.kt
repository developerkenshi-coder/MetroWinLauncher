package com.metrowin.launcher.data

import com.metrowin.launcher.model.AppCategory
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import kotlin.math.ln

/** Typing in the app list: the apps that match, best first. */
object AppSearch {
    /**
     * The apps that match [query] (see [index] for what counts), best match first; an empty query gives [items] as they are.
     * Builds an index for the call, which is fine for a short list or one search; keep the [index] when the same apps are
     * searched again and again.
     */
    fun filter(items: List<AppItem>, query: String): List<AppItem> {
        if (SearchText.words(query).isEmpty()) return items
        return index(items).search(query)
    }

    /**
     * The index of [items]. An app is found by its name, and, less strongly, by the words that say what it is for (in Italian and
     * English, for the apps that have a Metro glyph), the words of its package (`com.google.android.apps.maps` is found by "maps" and
     * "google"), its category in the language of the phone ([categoryLabel]), and whether it lives in the work profile or the
     * private space. The apps opened often ([usage]: opens by app id) come first among equal matches.
     */
    fun index(
        items: List<AppItem>,
        usage: Map<String, Int> = emptyMap(),
        categoryLabel: (AppCategory) -> String? = { null },
        profileWords: ProfileWords = ProfileWords(),
    ): SearchIndex<AppItem> = SearchIndex(
        items.map { item ->
            SearchEntry(
                value = item,
                title = item.label,
                keywords = keywords(item, categoryLabel, profileWords),
                boost = boost(usage[item.id] ?: 0),
            )
        },
        keywordPrefixFrom = KEYWORD_PREFIX_FROM,
    )

    /** "cal" finds Calendar and Calculator by name, and not the Phone for "call": the words that say what an app is for are found from this many letters. */
    const val KEYWORD_PREFIX_FROM = 4

    /** What the words for "work" and "private" are in the language of the phone. */
    class ProfileWords(val work: String? = null, val private: String? = null)

    internal fun keywords(item: AppItem, categoryLabel: (AppCategory) -> String?, profileWords: ProfileWords): List<String> = buildList {
        addAll(SearchAliases.forGlyph(item.glyph))
        addAll(packageWords(item.packageName))
        item.category?.let { categoryLabel(it) }?.let { add(it) }
        if (item.isWork) profileWords.work?.let { add(it) }
        if (item.isPrivate) profileWords.private?.let { add(it) }
    }

    /** The words of a package name that say something: `com.google.android.apps.photos` gives google and photos. */
    fun packageWords(packageName: String?): List<String> =
        packageName.orEmpty().split('.', '_', '-').map { it.lowercase() }
            .filter { it.length >= 3 && it !in PACKAGE_NOISE }
            .distinct()

    /** 0 for an app never opened, up to [SearchIndex.MAX_BOOST] for one opened a lot: it grows slowly, so a few opens count. */
    fun boost(opens: Int): Int =
        if (opens <= 0) 0 else (ln(opens + 1.0) * 4).toInt().coerceIn(1, SearchIndex.MAX_BOOST)

    private val PACKAGE_NOISE = setOf("com", "org", "net", "android", "apps", "app", "mobile", "free", "www", "client", "release", "main", "the")
}

/**
 * Other words, in Italian and English, for what the stock apps of the launcher are: who looks for "email" finds the mail app
 * whatever the phone calls it.
 */
object SearchAliases {
    fun forGlyph(glyph: Glyph?): List<String> = when (glyph) {
        null -> emptyList()
        Glyph.Alarm -> listOf("alarm", "clock", "timer", "stopwatch", "sveglia", "orologio", "cronometro")
        Glyph.Battery -> listOf("battery", "power", "saver", "batteria", "energia", "risparmio")
        Glyph.Calculator -> listOf("calculator", "math", "calcolatrice", "calcolo")
        Glyph.Calendar -> listOf("calendar", "events", "agenda", "calendario", "eventi", "appuntamenti")
        Glyph.Camera -> listOf("camera", "photo", "video", "selfie", "fotocamera", "foto", "scatta")
        Glyph.Cortana -> listOf("assistant", "voice", "assistente", "voce")
        Glyph.Data -> listOf("data", "usage", "internet", "mobile", "consumo", "dati")
        Glyph.Excel -> listOf("spreadsheet", "sheet", "foglio", "calcolo", "tabelle")
        Glyph.Files -> listOf("files", "documents", "folder", "file", "documenti", "cartelle")
        Glyph.Finance -> listOf("finance", "money", "stocks", "finanza", "soldi", "borsa")
        Glyph.Games -> listOf("games", "play", "giochi", "gioca")
        Glyph.Browser -> listOf("browser", "web", "internet", "surf", "navigatore", "sito", "siti")
        Glyph.Mail -> listOf("mail", "email", "inbox", "posta", "messaggi")
        Glyph.Maps -> listOf("maps", "map", "navigation", "gps", "directions", "mappe", "mappa", "navigatore", "indicazioni")
        Glyph.Messaging -> listOf("messages", "message", "sms", "chat", "text", "messaggi", "messaggio")
        Glyph.Mic -> listOf("microphone", "voice", "record", "microfono", "voce", "registra")
        Glyph.Music -> listOf("music", "songs", "player", "musica", "canzoni", "brani")
        Glyph.News -> listOf("news", "headlines", "notizie", "giornale")
        Glyph.Office -> listOf("office", "documents", "ufficio", "documenti")
        Glyph.OneDrive -> listOf("cloud", "drive", "storage", "backup", "archivio")
        Glyph.OneNote -> listOf("notes", "note", "notebook", "appunti", "blocco")
        Glyph.People -> listOf("people", "contacts", "address", "book", "persone", "contatti", "rubrica")
        Glyph.Phone -> listOf("phone", "dialer", "call", "calls", "telefono", "chiama", "chiamate", "tastierino")
        Glyph.Photos -> listOf("photos", "gallery", "pictures", "images", "foto", "galleria", "immagini")
        Glyph.Podcasts -> listOf("podcasts", "radio", "audio")
        Glyph.PowerPoint -> listOf("slides", "presentation", "presentazione", "diapositive")
        Glyph.Settings -> listOf("settings", "options", "preferences", "impostazioni", "opzioni", "preferenze")
        Glyph.Skype -> listOf("video", "call", "chiamata", "videochiamata")
        Glyph.Sports -> listOf("sports", "scores", "sport", "calcio", "risultati")
        Glyph.Storage -> listOf("storage", "space", "memory", "disk", "memoria", "spazio")
        Glyph.Store -> listOf("store", "shop", "apps", "download", "negozio", "scarica")
        Glyph.Video -> listOf("video", "movies", "films", "film", "filmati")
        Glyph.Wallet -> listOf("wallet", "pay", "cards", "portafoglio", "pagamenti", "carte")
        Glyph.Weather -> listOf("weather", "forecast", "temperature", "rain", "meteo", "previsioni", "pioggia")
        Glyph.Word -> listOf("word", "documents", "text", "documenti", "testo", "scrivere")
        Glyph.Xbox -> listOf("xbox", "games", "gaming", "giochi")
        Glyph.Theme -> listOf("theme", "colors", "tema", "colori")
        Glyph.Search -> listOf("search", "find", "cerca", "trova")
        Glyph.Wifi -> listOf("wifi", "wireless", "network", "internet", "rete")
        Glyph.Bluetooth -> listOf("bluetooth", "pair", "accoppia")
        Glyph.Airplane -> listOf("airplane", "flight", "aereo", "volo")
        Glyph.Moon -> listOf("moon", "night", "luna", "notte")
        Glyph.Flashlight -> listOf("flashlight", "torch", "light", "torcia", "luce", "pila")
        Glyph.Rotation -> listOf("rotation", "orientation", "rotazione", "orientamento")
        Glyph.Hotspot -> listOf("hotspot", "tethering", "condividi")
        Glyph.Vpn -> listOf("vpn", "private", "network", "privata")
        Glyph.Cast -> listOf("cast", "mirror", "screen", "trasmetti", "schermo")
        Glyph.Brightness -> listOf("brightness", "screen", "luminosita", "schermo")
        Glyph.Bell -> listOf("sound", "volume", "ringtone", "suoni", "suoneria")
        Glyph.Kids -> listOf("kids", "children", "child", "bambini", "figli")
        Glyph.Globe -> listOf("world", "web", "mondo", "sito")
        Glyph.Widget -> listOf("widget")
        Glyph.Lock -> listOf("lock", "security", "blocco", "sicurezza")
        Glyph.Star -> listOf("favorites", "birthdays", "preferiti", "compleanni")
        Glyph.Home -> listOf("home", "start")
        Glyph.Gesture -> listOf("gesture", "gesti")
        Glyph.Folder -> listOf("folder", "cartella")
        Glyph.Recents -> listOf("recents", "recent", "recenti")
    }
}
