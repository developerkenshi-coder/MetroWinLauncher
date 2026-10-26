package com.metrowin.launcher.data

import android.content.Context
import android.provider.ContactsContract
import java.text.Normalizer
import java.util.Locale

/** A number of a contact, as the smart dial looks at it. */
data class DialEntry(val name: String, val number: String, val lookupUri: String)

/**
 * The smart dial of the search box: digits typed find the contacts whose name is spelled by those keys of the phone keypad
 * (T9: 2 is ABC, 3 is DEF…) or whose number has them in it. Pure, so it is tested.
 */
object SmartDial {
    const val MIN_DIGITS = 2
    const val MAX_DIGITS = 14

    /** Whether [text] is only digits, enough of them to be a dial and few enough to be a prefix of something. */
    fun isQuery(text: String): Boolean = text.length in MIN_DIGITS..MAX_DIGITS && text.all { it in '0'..'9' }

    private val KEYS: Map<Char, Char> = buildMap {
        "abc".forEach { put(it, '2') }
        "def".forEach { put(it, '3') }
        "ghi".forEach { put(it, '4') }
        "jkl".forEach { put(it, '5') }
        "mno".forEach { put(it, '6') }
        "pqrs".forEach { put(it, '7') }
        "tuv".forEach { put(it, '8') }
        "wxyz".forEach { put(it, '9') }
    }

    /** The keys of [name]: its letters as digits, the accents off; a space stays a space, anything else is left out. */
    fun keysOf(name: String): String {
        val out = StringBuilder(name.length)
        for (c in Normalizer.normalize(name, Normalizer.Form.NFD)) {
            val lower = c.lowercaseChar()
            val key = KEYS[lower]
            when {
                key != null -> out.append(key)
                lower.isDigit() -> out.append(lower)
                lower.isWhitespace() || lower == '-' || lower == '\'' -> if (out.isNotEmpty() && out.last() != ' ') out.append(' ')
                else -> Unit
            }
        }
        return out.toString().trim()
    }

    /** How well [digits] fit a contact: 0 the start of the name, 1 the start of a word of it, 2 the initials, 3 the number, null no fit. */
    fun rank(name: String, number: String, digits: String): Int? {
        val keys = keysOf(name)
        if (keys.replace(" ", "").startsWith(digits)) return 0
        val words = keys.split(' ').filter { it.isNotEmpty() }
        if (words.any { it.startsWith(digits) }) return 1
        // Each digit is the first letter of a word: 27 is Anna Rossi.
        if (digits.length in 2..words.size && words.take(digits.length).joinToString("") { it.take(1) } == digits) return 2
        if (number.filter { it.isDigit() }.contains(digits)) return 3
        return null
    }

    /** The entries that fit [digits], the best fit first and then by name; one contact is listed once, at its best fit. */
    fun search(entries: List<DialEntry>, digits: String, limit: Int = 5): List<DialEntry> {
        if (!isQuery(digits)) return emptyList()
        val best = LinkedHashMap<String, Pair<Int, DialEntry>>()
        for (entry in entries) {
            val rank = rank(entry.name, entry.number, digits) ?: continue
            val kept = best[entry.lookupUri]
            if (kept == null || rank < kept.first) best[entry.lookupUri] = rank to entry
        }
        return best.values.sortedWith(compareBy({ it.first }, { it.second.name.lowercase(Locale.ROOT) })).map { it.second }.take(limit)
    }
}

/** The contacts with a number, read once and kept for a while: the smart dial asks at every key, the contacts are read at most now and then. */
class ContactDirectory(private val context: Context, private val now: () -> Long = System::currentTimeMillis) {
    private var entries: List<DialEntry> = emptyList()
    private var readAt = 0L

    /** Blocking, call it off the main thread. Empty without the contacts permission. */
    @Synchronized
    fun search(digits: String, limit: Int = 5): List<DialEntry> {
        if (!LivePermissions.isGranted(context, LivePermissions.contacts)) return emptyList()
        val t = now()
        if (entries.isEmpty() && readAt == 0L || t < readAt || t - readAt > MAX_AGE_MS) {
            entries = read()
            readAt = t
        }
        return SmartDial.search(entries, digits, limit)
    }

    private fun read(): List<DialEntry> = runCatching {
        val found = ArrayList<DialEntry>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null, null, null,
        )?.use { cursor ->
            while (found.size < MAX_ENTRIES && cursor.moveToNext()) {
                val name = cursor.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                val key = cursor.getString(1) ?: continue
                val number = cursor.getString(3)?.takeIf { it.isNotBlank() } ?: continue
                found += DialEntry(name, number, ContactsContract.Contacts.getLookupUri(cursor.getLong(0), key).toString())
            }
        }
        found
    }.getOrDefault(emptyList())

    private companion object {
        const val MAX_AGE_MS = 10 * 60 * 1000L
        const val MAX_ENTRIES = 5_000
    }
}
