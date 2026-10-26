package com.metrowin.launcher.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * A thing to remember at a time: [at] is when (epoch milliseconds). A reminder that rang ([rang]) stays in the list until it is
 * done; [rang] keeps a reboot or an update from ringing it again.
 */
@Immutable
data class Reminder(val id: Long, val text: String, val at: Long, val done: Boolean = false, val rang: Boolean = false)

/** The list of reminders and what is done with it. Pure, so it is tested. */
object Reminders {
    const val MAX = 60
    const val MAX_TEXT = 140

    /** A reminder is still "next" for this long after its time, so one that just rang does not vanish from the tile. */
    const val GRACE_MS = 60_000L

    /** [text] on one line and no longer than [MAX_TEXT], or null when nothing is left of it. */
    fun clean(text: String): String? = text.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() }?.take(MAX_TEXT)

    /** The ones still to come, the nearest first. */
    fun upcoming(list: List<Reminder>, now: Long): List<Reminder> =
        list.filter { !it.done && it.at >= now - GRACE_MS }.sortedBy { it.at }

    /** The ones whose time has passed and that are not done, the oldest first. */
    fun missed(list: List<Reminder>, now: Long): List<Reminder> =
        list.filter { !it.done && it.at < now - GRACE_MS }.sortedBy { it.at }

    fun next(list: List<Reminder>, now: Long): Reminder? = upcoming(list, now).firstOrNull()

    fun nextId(list: List<Reminder>): Long = (list.maxOfOrNull { it.id } ?: 0L) + 1L

    /** [list] with a new reminder; at most [MAX] are kept, the done ones going first and then the oldest. */
    fun add(list: List<Reminder>, text: String, at: Long): List<Reminder> {
        val clean = clean(text) ?: return list
        val added = list + Reminder(nextId(list), clean, at)
        if (added.size <= MAX) return added
        val drop = added.filter { it.done }.sortedBy { it.at }.firstOrNull() ?: added.minByOrNull { it.at } ?: return added
        return added - drop
    }

    fun complete(list: List<Reminder>, id: Long): List<Reminder> = list.map { if (it.id == id) it.copy(done = true) else it }

    /** The reminder has rung: it stays in the list, and is not rung again. */
    fun markRang(list: List<Reminder>, id: Long): List<Reminder> = list.map { if (it.id == id) it.copy(rang = true) else it }

    fun remove(list: List<Reminder>, id: Long): List<Reminder> = list.filter { it.id != id }

    /** The reminder moved to [at] and not done again. */
    fun reschedule(list: List<Reminder>, id: Long, at: Long): List<Reminder> =
        list.map { if (it.id == id) it.copy(at = at, done = false, rang = false) else it }

    fun clearDone(list: List<Reminder>): List<Reminder> = list.filter { !it.done }

    /** The times the form of a new reminder offers at a touch. */
    enum class QuickTime { InOneHour, ThisEvening, TomorrowMorning }

    /** The moment of a [QuickTime] counted from [now]: this evening at 8 is tomorrow's when it is nearly 8 already. */
    fun quickTime(which: QuickTime, now: java.time.LocalDateTime): java.time.LocalDateTime = when (which) {
        QuickTime.InOneHour -> now.plusHours(1).withSecond(0).withNano(0)
        QuickTime.ThisEvening -> {
            val evening = now.toLocalDate().atTime(EVENING_HOUR, 0)
            if (evening > now.plusMinutes(10)) evening else evening.plusDays(1)
        }
        QuickTime.TomorrowMorning -> now.toLocalDate().plusDays(1).atTime(MORNING_HOUR, 0)
    }

    private const val EVENING_HOUR = 20
    private const val MORNING_HOUR = 9

    fun encode(list: List<Reminder>): String = JSONArray().also { array ->
        list.forEach { array.put(JSONObject().put("id", it.id).put("text", it.text).put("at", it.at).put("done", it.done).put("rang", it.rang)) }
    }.toString()

    /** The list in [json]; empty when it is not one. */
    fun decode(json: String?): List<Reminder> = runCatching {
        val array = JSONArray(json ?: return emptyList())
        buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val text = clean(o.getString("text")) ?: continue
                add(Reminder(o.getLong("id"), text, o.getLong("at"), o.optBoolean("done"), o.optBoolean("rang")))
            }
        }.take(MAX)
    }.getOrDefault(emptyList())
}

/** The reminders kept on the phone. */
class ReminderStore(context: Context) {
    private val prefs = context.getSharedPreferences("metrowin", Context.MODE_PRIVATE)

    fun load(): List<Reminder> = Reminders.decode(prefs.getString(KEY, null))

    fun save(list: List<Reminder>) = prefs.edit { putString(KEY, Reminders.encode(list)) }

    private companion object {
        const val KEY = "reminders"
    }
}
