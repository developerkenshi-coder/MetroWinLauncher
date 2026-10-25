package com.metrowin.launcher.notifications

import android.app.PendingIntent
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One notification as the Action Center shows it. */
@Immutable
data class NotificationEntry(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val contentIntent: PendingIntent?,
    /** Removed from the list when opened, as the app asked. */
    val autoCancel: Boolean,
)

/** What a tile shows when it flips to the latest notification of its app. */
@Immutable
data class TilePeek(val title: String, val text: String)

/**
 * The notifications the service sees, shared with the rest of the app. The service lives in its own
 * lifecycle, so this is how its state reaches the screens.
 */
object NotificationHub {
    private val _entries = MutableStateFlow<List<NotificationEntry>>(emptyList())
    val entries: StateFlow<List<NotificationEntry>> = _entries.asStateFlow()

    @Volatile
    internal var service: NotificationService? = null

    fun update(entries: List<NotificationEntry>) {
        _entries.value = entries
    }

    /** How many notifications each app has: the counters on the tiles. */
    fun countsByPackage(entries: List<NotificationEntry>): Map<String, Int> =
        entries.groupingBy { it.packageName }.eachCount()

    /** The most recent notification of each app (the entries come newest first), as the tiles flip to show it. */
    fun latestByPackage(entries: List<NotificationEntry>): Map<String, TilePeek> {
        val latest = LinkedHashMap<String, TilePeek>()
        for (entry in entries) latest.putIfAbsent(entry.packageName, TilePeek(entry.title, entry.text))
        return latest
    }

    /** Whether a notification is worth showing: not an ongoing one (music, a download), not a group summary, not ours, not empty. */
    fun isWorthShowing(ongoing: Boolean, groupSummary: Boolean, fromUs: Boolean, title: CharSequence?, text: CharSequence?): Boolean =
        !ongoing && !groupSummary && !fromUs && !(title.isNullOrBlank() && text.isNullOrBlank())

    fun dismiss(key: String) {
        service?.cancelNotification(key)
    }

    fun dismissAll() {
        service?.cancelAllNotifications()
    }
}
