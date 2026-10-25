package com.metrowin.launcher.notifications

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The way a notification can be answered from here: the action the app gave it, with its text input. Sending a reply
 * hands the typed text to the app through the action's own pending intent, as the system shade does.
 */
@Immutable
class ReplyAction(
    val intent: PendingIntent,
    val input: RemoteInput,
    /** All the inputs of the action: Android wants them all in the result, the ones left empty too. */
    val inputs: List<RemoteInput>,
)

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
    /** Set when the app made the notification answerable (a message, a chat). */
    val reply: ReplyAction? = null,
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

    private val _apps = MutableStateFlow<List<AppNotification>>(emptyList())

    /** What the live tiles read from the notifications that are up (see [LiveCards]); empty while live content is off. */
    val apps: StateFlow<List<AppNotification>> = _apps.asStateFlow()

    /** Whether the live tiles use the notifications at all: the service reads them in detail only then. */
    @Volatile
    var liveContent = false
        private set

    /** Whether the pictures that come with them are read too. */
    @Volatile
    var livePictures = true
        private set

    /** Said by the settings: what the live tiles use. A change makes the service read the notifications again. */
    fun useForLiveTiles(enabled: Boolean, pictures: Boolean) {
        if (liveContent == enabled && livePictures == pictures) return
        liveContent = enabled
        livePictures = pictures
        if (!enabled) _apps.value = emptyList()
        service?.reload()
    }

    fun updateApps(apps: List<AppNotification>) {
        _apps.value = apps
    }

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

    /** Sends [text] as the answer to [entry]. False when it cannot be answered or the app's intent is gone. */
    fun reply(context: Context, entry: NotificationEntry, text: CharSequence): Boolean {
        val action = entry.reply ?: return false
        if (text.isBlank()) return false
        return runCatching {
            val answer = Intent()
            val results = Bundle().apply {
                // Every input of the action gets a value; the free text one gets the reply.
                action.inputs.forEach { putCharSequence(it.resultKey, if (it.resultKey == action.input.resultKey) text else "") }
            }
            RemoteInput.addResultsToIntent(action.inputs.toTypedArray(), answer, results)
            action.intent.send(context, 0, answer)
        }.isSuccess
    }

    fun dismiss(key: String) {
        service?.cancelNotification(key)
    }

    /** Puts the notification [key] away for [millis]; the system brings it back after that. False when the service is not running. */
    fun snooze(key: String, millis: Long): Boolean {
        val service = service ?: return false
        return runCatching { service.snoozeNotification(key, millis) }.isSuccess
    }

    fun dismissAll() {
        service?.cancelAllNotifications()
    }
}
