package com.metrowin.launcher.wallet

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * The switches of the wallet, in a file of preferences of its own (the "options" of the menu). Each is a state of Compose, so the screen
 * follows a change at once. What touches privacy (the lock, the history) is off until the user turns it on.
 */
class WalletPrefs(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))

    inner class Flag(private val key: String, private val default: Boolean) {
        var on by mutableStateOf(prefs.getBoolean(key, default))
            private set

        fun set(value: Boolean) {
            on = value
            prefs.edit { putBoolean(key, value) }
        }
    }

    /** No screenshot and no picture in the list of recent apps while a card or its code is on the screen. */
    val secure = Flag("secure", true)
    val encryptedExport = Flag("encrypted_export", false)
    val lock = Flag("lock", false)
    val history = Flag("history", false)
    val motion = Flag("motion", true)
    val expiry = Flag("expiry", true)
    val preview = Flag("preview", true)
    val rich = Flag("rich", true)
    val grid = Flag("grid", false)
    val group = Flag("group", false)
    val scanExtras = Flag("scan_extras", true)

    /** Opening the wallet (the tile too) goes straight to the card used most, when the history says which. */
    val direct = Flag("direct", false)

    var sort by mutableStateOf(runCatching { SortMode.valueOf(prefs.getString("sort", null) ?: "") }.getOrDefault(SortMode.Name))
        private set

    fun nextSort() {
        sort = SortMode.entries[(sort.ordinal + 1) % SortMode.entries.size]
        prefs.edit { putString("sort", sort.name) }
    }

    companion object {
        const val FILE = "metrowin_wallet"
    }
}
