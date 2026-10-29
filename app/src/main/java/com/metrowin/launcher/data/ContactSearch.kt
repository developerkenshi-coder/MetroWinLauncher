package com.metrowin.launcher.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

/** Looks for contacts by name. It needs the contacts permission, and finds nothing without it. */
class ContactSearch(private val context: Context) {
    /** Blocking, call it off the main thread. */
    fun search(query: String, limit: Int = LIMIT): List<ContactMatch> {
        val text = query.trim()
        if (text.isEmpty() || !LivePermissions.isGranted(context, LivePermissions.contacts)) return emptyList()
        return runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(text))
            val matches = ArrayList<ContactMatch>(limit)
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                null, null, "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
            )?.use { cursor ->
                while (matches.size < limit && cursor.moveToNext()) {
                    val name = cursor.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                    val key = cursor.getString(1) ?: continue
                    matches += ContactMatch(name, ContactsContract.Contacts.getLookupUri(cursor.getLong(0), key).toString())
                }
            }
            matches
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val LIMIT = 5

        /** How many candidates a spoken name reads: the provider lists them by name, so the best one may come late. */
        const val SPOKEN_LIMIT = 25

        /**
         * The contact that a spoken [query] names among [matches]. The provider also lets through contacts found by their number or
         * e-mail, and lists them alphabetically; here only a name that has every spoken word counts, the closest first. Several that
         * fit equally well are not guessed between: they are returned to be asked about.
         */
        fun pick(query: String, matches: List<ContactMatch>): ContactPick {
            val wanted = VoiceCommands.normalize(query).split(' ').filter { it.isNotEmpty() }
            if (wanted.isEmpty()) return ContactPick.None
            val ranked = matches.mapNotNull { match ->
                val name = VoiceCommands.normalize(match.name)
                val words = name.split(' ')
                val rank = when {
                    name == wanted.joinToString(" ") -> 0
                    wanted.all { it in words } -> 1
                    wanted.all { w -> words.any { it.startsWith(w) } } -> 2
                    else -> null
                }
                rank?.let { Triple(it, name, match) }
            }
            val best = ranked.minOfOrNull { it.first } ?: return ContactPick.None
            // The same name twice is the same person written twice.
            val top = ranked.filter { it.first == best }.distinctBy { it.second }.map { it.third }
            return if (top.size == 1) ContactPick.One(top[0]) else ContactPick.Ambiguous(top)
        }
    }
}

/** What a spoken name leads to: one contact, several that fit equally well, or none. */
sealed interface ContactPick {
    data class One(val contact: ContactMatch) : ContactPick
    data class Ambiguous(val contacts: List<ContactMatch>) : ContactPick
    data object None : ContactPick
}
