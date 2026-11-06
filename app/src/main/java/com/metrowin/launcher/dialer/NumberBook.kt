package com.metrowin.launcher.dialer

import android.content.Context
import androidx.core.content.edit
import com.metrowin.launcher.data.PhoneCalls
import com.metrowin.launcher.data.Prefs
import com.metrowin.launcher.data.RecentCall
import org.json.JSONObject

/** Some calls of the log that are shown as one: [latest] is the most recent, [members] are all of them (newest first), [count] how many. */
data class CallGroup(val latest: RecentCall, val members: List<RecentCall>) {
    val count: Int get() = members.size
}

/** The pure part of what the dialer keeps for the numbers it has seen: the grouping of the log, the notes, the keys of the quick dial. */
object NumberBook {
    const val MAX_NOTES = 100
    const val MAX_NOTE = 140
    val KEYS = '2'..'9'

    /** Calls one after the other from the same number and of the same kind (missed, outgoing, incoming) become one row; [calls] is newest first. */
    fun group(calls: List<RecentCall>): List<CallGroup> {
        fun kind(call: RecentCall) = if (call.missed) 0 else if (call.outgoing) 1 else 2
        val groups = ArrayList<MutableList<RecentCall>>()
        for (call in calls) {
            val last = groups.lastOrNull()?.first()
            if (last != null && kind(last) == kind(call) && PhoneCalls.clean(last.number) == PhoneCalls.clean(call.number)) groups.last() += call else groups += mutableListOf(call)
        }
        return groups.map { CallGroup(it.first(), it) }
    }

    /** [notes] with the note of [number] set to [text] (cut to [MAX_NOTE]); a blank text takes it away. The oldest notes go when there are more than [MAX_NOTES]. */
    fun withNote(notes: Map<String, String>, number: String, text: String): Map<String, String> {
        val key = PhoneCalls.clean(number)
        val kept = notes - key
        val note = text.trim().take(MAX_NOTE)
        if (key.isEmpty() || note.isEmpty()) return kept
        return (kept + (key to note)).entries.toList().takeLast(MAX_NOTES).associate { it.key to it.value }
    }

    /** [keys] with [number] on [key] (2 to 9) and nowhere else; a blank [number] frees the key. */
    fun withKey(keys: Map<Char, String>, key: Char, number: String): Map<Char, String> {
        if (key !in KEYS) return keys
        val clean = PhoneCalls.clean(number)
        val others = keys.filter { it.key != key && it.value != clean }
        return if (clean.isEmpty()) others else others + (key to clean)
    }

    fun keyOf(keys: Map<Char, String>, number: String): Char? = PhoneCalls.clean(number).let { clean -> keys.entries.firstOrNull { it.value == clean }?.key }
}

/** [NumberBook] kept in the preferences. */
class NumberBookStore(context: Context) {
    private val prefs = Prefs.open(context.applicationContext, Prefs.DIALER_NUMBERS)

    fun notes(): Map<String, String> = read(KEY_NOTES)

    fun saveNotes(notes: Map<String, String>) = write(KEY_NOTES, notes)

    fun keys(): Map<Char, String> = read(KEY_KEYS).mapNotNull { (k, v) -> k.singleOrNull()?.takeIf { it in NumberBook.KEYS }?.let { it to v } }.toMap()

    fun saveKeys(keys: Map<Char, String>) = write(KEY_KEYS, keys.mapKeys { it.key.toString() })

    private fun read(name: String): Map<String, String> = runCatching {
        JSONObject(prefs.getString(name, null) ?: return emptyMap()).let { json -> json.keys().asSequence().associateWith { json.getString(it) } }
    }.getOrDefault(emptyMap())

    private fun write(name: String, map: Map<String, String>) = prefs.edit { putString(name, JSONObject(map).toString()) }

    private companion object {
        const val KEY_NOTES = "notes"
        const val KEY_KEYS = "keys"
    }
}
