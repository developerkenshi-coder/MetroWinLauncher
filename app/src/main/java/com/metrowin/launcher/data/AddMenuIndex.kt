package com.metrowin.launcher.data

import androidx.annotation.StringRes
import com.metrowin.launcher.R

/** The groups of the menu that adds things to Start, in the order they are shown. */
enum class AddCategory(@param:StringRes val title: Int) {
    People(R.string.add_cat_people),
    Places(R.string.add_cat_places),
    Time(R.string.add_cat_time),
    Notes(R.string.add_cat_notes),
    Info(R.string.add_cat_info),
    Media(R.string.add_cat_media),
    System(R.string.add_cat_system),
}

/** What the quick search of the add menu knows about one entry: where it is filed and which words find it, in English and Italian. */
data class AddKind(val id: String, @param:StringRes val label: Int, val category: AddCategory, val keywords: List<String>)

/** The entries of the add menu, filed by what they are for. Pure, so the filing and the search are tested. */
object AddMenuIndex {
    val kinds: List<AddKind> = listOf(
        AddKind("contact", R.string.add_contact, AddCategory.People, listOf("contact", "person", "contatto", "persona", "rubrica")),
        AddKind("speeddial", R.string.add_speed_dial, AddCategory.People, listOf("call", "number", "chiamata", "numero", "rapida")),
        AddKind("dialgrid", R.string.add_dialgrid, AddCategory.People, listOf("calls", "numbers", "chiamate", "numeri", "preferiti")),
        AddKind("peoplehub", R.string.add_peoplehub, AddCategory.People, listOf("people", "favorites", "persone", "preferiti")),
        AddKind("group", R.string.add_group, AddCategory.People, listOf("group", "contacts", "gruppo", "contatti")),
        AddKind("birthdays", R.string.add_birthdays, AddCategory.People, listOf("birthday", "party", "compleanno", "compleanni")),
        AddKind("site", R.string.add_site, AddCategory.Places, listOf("web", "site", "link", "browser", "sito", "pagina")),
        AddKind("place", R.string.add_place, AddCategory.Places, listOf("navigate", "map", "address", "home", "work", "luogo", "mappa", "indirizzo", "casa", "lavoro")),
        AddKind("whereami", R.string.add_whereami, AddCategory.Places, listOf("location", "position", "gps", "address", "dove", "posizione", "indirizzo")),
        AddKind("parking", R.string.add_parking, AddCategory.Places, listOf("car", "park", "garage", "where", "auto", "macchina", "parcheggio", "posteggio", "dove")),
        AddKind("analogclock", R.string.add_analogclock, AddCategory.Time, listOf("hands", "watch", "face", "lancette", "orologio", "quadrante")),
        AddKind("timer", R.string.add_timer, AddCategory.Time, listOf("countdown", "minutes", "minuti", "conto")),
        AddKind("stopwatch", R.string.add_stopwatch, AddCategory.Time, listOf("chronometer", "laps", "cronometro", "giri")),
        AddKind("countdown", R.string.add_countdown, AddCategory.Time, listOf("days", "date", "event", "giorni", "data", "evento")),
        AddKind("worldclock", R.string.add_worldclock, AddCategory.Time, listOf("clock", "time zone", "city", "orologio", "fuso", "citta")),
        AddKind("reminders", R.string.add_reminders, AddCategory.Time, listOf("remind", "alert", "promemoria", "avviso")),
        AddKind("moon", R.string.add_moon, AddCategory.Time, listOf("phase", "night", "luna", "fase", "notte")),
        AddKind("note", R.string.add_note, AddCategory.Notes, listOf("text", "memo", "write", "testo", "appunto", "scrivi")),
        AddKind("checklist", R.string.add_checklist, AddCategory.Notes, listOf("todo", "tasks", "shopping", "cose da fare", "spesa", "lista")),
        AddKind("counter", R.string.add_counter, AddCategory.Notes, listOf("count", "number", "tally", "conta", "numero", "contatore")),
        AddKind("wallet", R.string.add_wallet, AddCategory.Notes, listOf("card", "loyalty", "ticket", "barcode", "carta", "fedelta", "biglietto", "codice")),
        AddKind("news", R.string.add_news, AddCategory.Info, listOf("headlines", "rss", "notizie", "titoli")),
        AddKind("finance", R.string.add_finance, AddCategory.Info, listOf("currency", "money", "crypto", "euro", "valute", "soldi", "cambio")),
        AddKind("steps", R.string.add_steps, AddCategory.Info, listOf("health", "walk", "pedometer", "passi", "salute", "cammino", "contapassi")),
        AddKind("album", R.string.add_album, AddCategory.Media, listOf("photo", "pictures", "gallery", "foto", "immagini", "galleria")),
        AddKind("camera", R.string.add_camera, AddCategory.Media, listOf("photo", "picture", "last", "foto", "fotocamera", "ultima")),
        AddKind("recorder", R.string.add_recorder, AddCategory.Media, listOf("voice", "audio", "memo", "record", "registra", "voce", "registratore")),
        AddKind("toggle", R.string.add_toggle, AddCategory.System, listOf("switch", "wifi", "bluetooth", "silent", "rotation", "flashlight", "interruttore", "silenzioso", "rotazione", "torcia")),
        AddKind("sun", R.string.add_sun, AddCategory.Info, listOf("sunrise", "sunset", "daylight", "alba", "tramonto", "sole", "luce")),
        AddKind("weatherplus", R.string.add_weatherplus, AddCategory.Info, listOf("weather", "forecast", "temperature", "rain", "meteo", "previsioni", "temperatura", "pioggia", "tempo")),
        AddKind("setting", R.string.add_setting, AddCategory.System, listOf("settings", "wifi", "bluetooth", "battery", "impostazioni", "batteria")),
        AddKind("widget", R.string.add_widget, AddCategory.System, listOf("widget", "app", "gadget")),
    )

    private val byId = kinds.associateBy { it.id }

    fun kind(id: String): AddKind = byId.getValue(id)

    /** The entries that match [query] by their name or one of their words (accents and case do not count): all of them for an empty query. */
    fun matches(kind: AddKind, name: String, query: String): Boolean {
        val words = AppSections.fold(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val haystack = (listOf(name) + kind.keywords).map(AppSections::fold)
        return words.all { word -> haystack.any { it.contains(word) } }
    }
}
