package com.metrowin.launcher.data

/**
 * A word and a colon at the start of the search narrow it to one kind of result: `app: maps`, `contact: anna`, `file: tax`, `notif: bank`.
 * The short forms (`a:`, `c:`, `s:`, `f:`, `e:`, `n:`, `w:`) and the Italian words work too. Pure, so it is tested.
 */
object SearchOperators {
    enum class Scope(vararg val words: String) {
        Apps("app", "apps", "a"),
        Contacts("contact", "contatto", "contacts", "c"),
        Settings("setting", "settings", "impostazione", "imp", "s"),
        Files("file", "files", "f"),
        Events("event", "evento", "events", "e"),
        Notifications("notif", "notifica", "notifiche", "n"),
        Tiles("tile", "riquadro", "t"),
        Web("web", "w"),
    }

    /** What is left to look for ([text]) and where ([scope]; null looks everywhere). */
    data class Parsed(val scope: Scope?, val text: String) {
        /** Whether results of [other] kind are wanted. */
        fun allows(other: Scope) = scope == null || scope == other
    }

    private const val LONGEST_WORD = 12

    fun parse(query: String): Parsed {
        val trimmed = query.trimStart()
        val colon = trimmed.indexOf(':')
        if (colon <= 0 || colon > LONGEST_WORD) return Parsed(null, query)
        val word = AppSections.fold(trimmed.substring(0, colon)).trim()
        val scope = Scope.entries.firstOrNull { word in it.words } ?: return Parsed(null, query)
        return Parsed(scope, trimmed.substring(colon + 1).trimStart())
    }
}
