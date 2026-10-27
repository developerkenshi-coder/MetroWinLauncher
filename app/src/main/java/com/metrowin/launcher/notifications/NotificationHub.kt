package com.metrowin.launcher.notifications

import android.app.Notification
import android.app.ActivityOptions
import android.app.PendingIntent
import android.graphics.Bitmap
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.compose.runtime.Immutable
import java.util.concurrent.atomic.AtomicInteger
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

/** A button the app put on its notification (archive, mark as read…): its text and what it does. */
@Immutable
class NotifAction(val title: String, val intent: PendingIntent)

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
    /** The whole text of a long notification, when it is longer than [text]. */
    val longText: String? = null,
    /** The buttons of the app that need no typing, at most three. */
    val actions: List<NotifAction> = emptyList(),
    /** The picture that comes with it, when it has one. */
    val picture: Bitmap? = null,
    /** A message or a call: these come first when the Action Center is set to put them first. */
    val urgent: Boolean = false,
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

    private val watchers = AtomicInteger(0)

    /**
     * Whether a screen of the launcher is on (see [startWatching]). The service reads the notifications only then: it stays
     * alive all day, and what it reads is for the screens, which have nothing to show while they are off.
     */
    val watched: Boolean get() = watchers.get() > 0

    /** A screen that shows the notifications came on (the launcher, the screen saver). Pair it with [stopWatching]. */
    fun startWatching() {
        if (watchers.getAndIncrement() == 0) service?.screenOn()
    }

    /** That screen went off. */
    fun stopWatching() {
        if (watchers.updateAndGet { (it - 1).coerceAtLeast(0) } == 0) service?.screenOff()
    }

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

    /**
     * What the launcher took off the shade of Android (see [hold]) and keeps for the Action Center until it is dismissed or the app
     * posts it again. Only the main thread touches it (the service's callbacks and the screens are all there); the lock is for safety.
     */
    private val held = LinkedHashMap<String, NotificationEntry>()

    fun update(entries: List<NotificationEntry>) {
        val shown = synchronized(held) {
            // Posted again: the live one counts, not the copy.
            held.keys.removeAll(entries.mapTo(HashSet()) { it.key })
            if (held.isEmpty()) entries else (entries + held.values).sortedByDescending { it.postTime }
        }
        _entries.value = shown
    }

    /**
     * The launcher shows this notification in its own banner and takes the one of Android away, so it does not come twice. It stays in the
     * Action Center. Android shows its own for an instant first and the app makes its sound: neither can be stopped. The app is told the
     * notification was dismissed.
     */
    fun hold(entry: NotificationEntry) {
        synchronized(held) {
            held[entry.key] = entry
            while (held.size > MAX_HELD) held.remove(held.keys.first())
        }
        service?.cancelNotification(entry.key)
    }

    private val _history = MutableStateFlow<List<NotificationEntry>>(emptyList())

    /** What was dismissed from the Action Center lately, newest first. Only in memory: it is gone when the process is. */
    val history: StateFlow<List<NotificationEntry>> = _history.asStateFlow()

    private fun rememberDismissed(entries: List<NotificationEntry>) {
        _history.value = (entries + _history.value).distinctBy { it.key to it.postTime }.take(MAX_HISTORY)
    }

    fun clearHistory() {
        _history.value = emptyList()
    }

    /** What the button of an app on a notification does. */
    fun run(context: Context, entry: NotificationEntry, action: NotifAction) {
        runCatching {
            val options = if (Build.VERSION.SDK_INT >= 34) {
                @Suppress("DEPRECATION") // replaced on Android 16 by finer modes, this one still works there
                val allowed = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(allowed).toBundle()
            } else {
                null
            }
            action.intent.send(context, 0, null, null, null, null, options)
        }
        // A copy kept after the app's own was taken away is done with.
        forget(entry.key)
    }

    /** The copies kept for the Action Center are let go: the service went away and what it knew with it. */
    internal fun clearHeld() = synchronized(held) { held.clear() }

    /** The notification is gone for good: dismissed, or its place taken. */
    private fun forget(key: String) {
        synchronized(held) { held.remove(key) }
        _entries.value = _entries.value.filterNot { it.key == key }
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

    /**
     * Whether the launcher may take a notification away from the shade of Android (and keep its copy in the Action Center): not an
     * ongoing one or one the user cannot clear, not ours, not a call, an alarm, a navigation, the player of the music, a progress or a
     * service, nothing that opens a full screen, and not an app the user keeps out of the Action Center (it would be lost).
     */
    fun mayHide(ongoing: Boolean, clearable: Boolean, fromUs: Boolean, category: String?, fullScreen: Boolean, hiddenFromCenter: Boolean): Boolean =
        !ongoing && clearable && !fromUs && !fullScreen && !hiddenFromCenter && category !in KEPT_CATEGORIES

    /** Every notification up now that may be hidden is moved to the Action Center: at once, when the setting is turned on. */
    fun sweep() {
        service?.hideAllNow()
    }

    private val KEPT_CATEGORIES = setOf(
        Notification.CATEGORY_CALL, Notification.CATEGORY_ALARM, Notification.CATEGORY_NAVIGATION, Notification.CATEGORY_TRANSPORT,
        Notification.CATEGORY_PROGRESS, Notification.CATEGORY_SERVICE, Notification.CATEGORY_STOPWATCH,
    )

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

    /** Runs what the notification does when tapped, then removes it if the app asked for that. */
    fun open(context: Context, entry: NotificationEntry) {
        runCatching {
            val options = if (Build.VERSION.SDK_INT >= 34) {
                @Suppress("DEPRECATION") // replaced on Android 16 by finer modes, this one still works there
                val allowed = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(allowed).toBundle()
            } else {
                null
            }
            entry.contentIntent?.send(context, 0, null, null, null, null, options)
        }
        if (entry.autoCancel) {
            forget(entry.key)
            service?.cancelNotification(entry.key)
        }
    }

    /** The accessibility service came or went: the strip of the Action Center over other apps needs it. */
    fun edgeChanged() {
        service?.syncEdge()
    }

    fun dismiss(key: String) {
        _entries.value.firstOrNull { it.key == key }?.let { rememberDismissed(listOf(it)) }
        forget(key)
        service?.cancelNotification(key)
    }

    /** Puts the notification [key] away for [millis]; the system brings it back after that. False when the service is not running. */
    fun snooze(key: String, millis: Long): Boolean {
        val service = service ?: return false
        return runCatching { service.snoozeNotification(key, millis) }.isSuccess
    }

    /** The notifications of the app [packageName] among [entries]. */
    fun ofApp(entries: List<NotificationEntry>, packageName: String): List<NotificationEntry> = entries.filter { it.packageName == packageName }

    /**
     * The app was opened: its notifications are taken away, so the number on its tile goes with them. They go to the history
     * of dismissed ones, where they can be looked at again.
     */
    fun dismissApp(packageName: String) {
        val mine = ofApp(_entries.value, packageName)
        if (mine.isEmpty()) return
        rememberDismissed(mine)
        mine.forEach { entry ->
            forget(entry.key)
            service?.cancelNotification(entry.key)
        }
    }

    fun dismissAll() {
        rememberDismissed(_entries.value)
        clearHeld()
        _entries.value = emptyList()
        service?.cancelAllNotifications()
    }

    private const val MAX_HELD = 200
    private const val MAX_HISTORY = 30
}
