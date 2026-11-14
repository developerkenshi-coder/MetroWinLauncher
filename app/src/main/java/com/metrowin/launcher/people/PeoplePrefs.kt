package com.metrowin.launcher.people

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.metrowin.launcher.data.ContactSource

/**
 * The own choices of the People app, in its own file of preferences (not the settings of the launcher): the switches of its options menu,
 * the favourites and the call-back list the user keeps here. Everything that shows more of a person than before (the words of a
 * notification, a notification in a card) starts off. Every value is state: the screen follows a change at once.
 */
class PeoplePrefs(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The birthdays that come within a month, under "what's new" and in the card. */
    var birthdays by flag("birthdays", true)

    /** The words of a notification under "what's new" (off: only who it is from). Private: off. */
    var newsText by flag("news_text", false)

    /** The notifications of a person among its history. Private: off. */
    var historyNotifications by flag("history_notifications", false)

    /** The chips of the groups of the phone over the list. */
    var groupChips by flag("group_chips", false)

    /** A thin line between two people of the list. */
    var dividers by flag("dividers", true)

    /** The letter of the list as a plain accent letter, like the phone, not a square. */
    var plainLetters by flag("plain_letters", false)

    /** A dot of the colour of the place (Google, phone, SIM) after the name, when the contacts come from more than one. */
    var sourceDots by flag("source_dots", true)

    /** How big the favourites are: 0 small, 1 medium, 2 large. */
    var tileSize by number("tile_size", 1)

    /** The places of the contacts shown (a mask of [ContactSource]). */
    var sources by number("sources", ContactSource.ALL)

    /** The hint of the first time, once read. */
    var hintSeen by flag("hint_seen", false)

    /** The order of the three pages of the hub: a permutation of "012" (all, what's new, favourites). */
    var order by text("order", "012")
    private var favouriteKeys by mutableStateOf(prefs.getStringSet("favourites", emptySet()).orEmpty())
    private var callBackRaw by mutableStateOf(prefs.getStringSet("call_back", emptySet()).orEmpty())

    /** Favourites kept here as well as the starred ones of the contacts: the contacts of the phone are only read, never written. */
    fun isFavourite(key: String) = key in favouriteKeys

    val favourites: Set<String> get() = favouriteKeys

    fun toggleFavourite(key: String) {
        favouriteKeys = if (key in favouriteKeys) favouriteKeys - key else favouriteKeys + key
        prefs.edit().putStringSet("favourites", favouriteKeys).apply()
    }

    /** The people to call back: key to the time it was asked. */
    val callBack: Map<String, Long> get() = callBackRaw.mapNotNull { it.substringBefore('|').takeIf(String::isNotEmpty)?.let { k -> k to (it.substringAfter('|').toLongOrNull() ?: 0L) } }.toMap()

    fun toggleCallBack(key: String, now: Long = System.currentTimeMillis()) {
        callBackRaw = if (key in callBack) callBackRaw.filterNot { it.substringBefore('|') == key }.toSet() else callBackRaw + "$key|$now"
        prefs.edit().putStringSet("call_back", callBackRaw).apply()
    }

    /** The page [page] of the hub moved one place to the right (the last goes back to the first). */
    fun moveRight(page: Int) {
        val list = order.map { it }.toMutableList()
        val at = list.indexOf(page.digitToChar()).takeIf { it >= 0 } ?: return
        val to = (at + 1) % list.size
        list.add(to, list.removeAt(at))
        order = list.joinToString("")
    }

    private fun flag(name: String, default: Boolean) = Backed(prefs, name, default, { p, k, d -> p.getBoolean(k, d) }, { e, k, v -> e.putBoolean(k, v) })
    private fun number(name: String, default: Int) = Backed(prefs, name, default, { p, k, d -> p.getInt(k, d) }, { e, k, v -> e.putInt(k, v) })
    private fun text(name: String, default: String) = Backed(prefs, name, default, { p, k, d -> p.getString(k, d) ?: d }, { e, k, v -> e.putString(k, v) })

    /** A value in the file that Compose follows: read once, written at every change. */
    private class Backed<T>(
        private val prefs: SharedPreferences, private val name: String, default: T,
        read: (SharedPreferences, String, T) -> T, private val write: (SharedPreferences.Editor, String, T) -> Unit,
    ) {
        private var state by mutableStateOf(read(prefs, name, default))

        operator fun getValue(thisRef: Any?, property: Any?): T = state

        operator fun setValue(thisRef: Any?, property: Any?, value: T) {
            state = value
            prefs.edit().also { write(it, name, value) }.apply()
        }
    }

    private companion object {
        const val FILE = "people_app"
    }
}
