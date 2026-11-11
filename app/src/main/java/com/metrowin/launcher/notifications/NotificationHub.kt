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
import android.service.notification.NotificationListenerService.REASON_CANCEL
import android.service.notification.NotificationListenerService.REASON_CANCEL_ALL
import android.service.notification.NotificationListenerService.REASON_CLICK
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

/** A small icon held by an entry without counting in its equality: a new bitmap is drawn at every read, and a list that "changed" only for that would redraw the screens. */
class EntryIcon(val bitmap: Bitmap) {
    override fun equals(other: Any?) = other is EntryIcon
    override fun hashCode() = 0
}

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
    /** The small icon of the notification (a white shape on nothing, like the ones of the status bar) for the lock screen's row of icons, when the app has one. */
    val icon: EntryIcon? = null,
    /** The group the app put it in, and whether it is the heading of that group ("5 new messages"). */
    val group: String? = null,
    val summary: Boolean = false,
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

    /** The launcher's own screen is on: it draws its banners itself, so none is drawn over it. Not set by the bars and overlays that only read the notifications. */
    @Volatile
    var launcherShown = false

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

    /** The register on disk (see [NotificationLedger]), set by the service when it connects; null before that and in tests. */
    @Volatile
    private var ledger: NotificationLedger? = null

    internal fun attach(context: Context) = attach(ledger ?: NotificationLedger(context.applicationContext))

    /** The service connected: what the register has (a process that was killed, a listener that rebound) is in the list again. */
    internal fun attach(ledger: NotificationLedger) {
        if (this.ledger === ledger) return
        this.ledger = ledger
        synchronized(held) {
            ledger.load().forEach { item -> held.putIfAbsent(item.key, entryOf(item)) }
            while (held.size > NotificationLedger.MAX_ITEMS) held.remove(held.keys.first())
        }
    }

    private fun entryOf(item: LedgerItem) = NotificationEntry(
        key = item.key, packageName = item.packageName, title = item.title, text = item.text, postTime = item.postTime, contentIntent = null,
        autoCancel = item.autoCancel, longText = item.longText, urgent = item.urgent, group = item.group, summary = item.summary,
    )

    private fun ledgerOf(entry: NotificationEntry) = LedgerItem(
        key = entry.key, packageName = entry.packageName, title = entry.title.take(NotificationLedger.MAX_TEXT), text = entry.text.take(NotificationLedger.MAX_TEXT),
        longText = entry.longText?.take(NotificationLedger.MAX_TEXT), postTime = entry.postTime, autoCancel = entry.autoCancel,
        urgent = entry.urgent, group = entry.group, summary = entry.summary,
    )

    /** One line for the journal that goes in the bug report (see [NotificationLedger.note]): where from and what happened, never the words. */
    internal fun note(line: String) {
        ledger?.note("${java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.ROOT).format(System.currentTimeMillis())} $line")
    }

    internal fun journal(): List<String> = ledger?.journalLines().orEmpty()

    /**
     * Written down the moment the notification is posted, with or without a screen of the launcher on: the app can take it back a
     * second later (a chat read on another device) and the list is only read when a screen comes on. It stays until the user takes
     * it away here ([forget]), a newer post with the same key takes its place, or [NotificationLedger.MAX_AGE_MS] passes.
     */
    fun keep(entry: NotificationEntry) {
        if (!actionCenterOn) return
        synchronized(held) {
            held.remove(entry.key)
            held[entry.key] = entry
            while (held.size > NotificationLedger.MAX_ITEMS) held.remove(held.keys.first())
        }
        ledger?.record(ledgerOf(entry))
    }

    /**
     * The live ones (what Android has up now) and the kept ones the app is not showing any more, newest first. A group's heading is
     * shown only while none of the group's own notifications is, so a bundle is not told twice.
     */
    fun update(rawEntries: List<NotificationEntry>) {
        // A notification the user just dismissed can still be up in Android for a moment (the cancel is asked, not done): it is not read back.
        val entries = synchronized(held) {
            dismissed.keys.retainAll(rawEntries.mapTo(HashSet()) { it.key })
            rawEntries.filterNot { (dismissed[it.key] ?: Long.MIN_VALUE) >= it.postTime }
        }
        // What Android already had up when the listener connected was never posted to it: it is written down now, without its picture.
        entries.forEach { live -> if (synchronized(held) { (held[live.key]?.postTime ?: Long.MIN_VALUE) < live.postTime }) keep(live.copy(picture = null)) }
        val shown = synchronized(held) {
            val live = entries.mapTo(HashSet()) { it.key }
            val all = if (held.isEmpty()) entries else entries + held.values.filter { it.key !in live }
            val headed = all.filterNot { it.summary }.mapTo(HashSet()) { it.packageName to it.group }
            all.filter { !it.summary || (it.packageName to it.group) !in headed }.sortedByDescending { it.postTime }
        }
        _entries.value = shown
        clearJustOpened(shown)
    }

    /** Whether opening an app takes away its notifications (see [LauncherSettings.clearOnOpen]); said by the settings and read by the service. */
    @Volatile
    var clearOnOpen = false

    /** When each app was opened lately, by package: a notification of an app the user is in front of is not news. */
    private val openedAt = HashMap<String, Long>()

    /**
     * An app was opened (from Start, the list, a tile, a notification): its notifications go now, and the ones that come in the next
     * few seconds too. They are read a moment after they are posted (the service paces its reads), so a notification of the app that
     * was already there when it was opened could reach the list only after that, and stay.
     */
    /** The app in front, as the accessibility service sees windows change; null when not known. Its notifications are read as they come. */
    @Volatile
    var foregroundPackage: String? = null

    /** The Action Center is on: only then does the launcher take notifications out of Android's shade, or clear them when their app is opened. */
    @Volatile
    var actionCenterOn: Boolean = true

    fun appOpened(packageName: String, now: Long = android.os.SystemClock.elapsedRealtime()) {
        if (!clearOnOpen || !actionCenterOn) return
        synchronized(openedAt) { openedAt[packageName] = now }
        dismissApp(packageName)
    }

    private fun clearJustOpened(shown: List<NotificationEntry>, now: Long = android.os.SystemClock.elapsedRealtime()) {
        if (!clearOnOpen || !actionCenterOn) return
        val packages = synchronized(openedAt) {
            openedAt.values.removeAll { now - it > JUST_OPENED_MS }
            openedAt.keys.toList()
        }
        if (packages.isEmpty()) return
        shown.mapTo(HashSet()) { it.packageName }.filter { it in packages }.forEach(::dismissApp)
    }

    /**
     * The launcher shows this notification in its own banner and takes the one of Android away, so it does not come twice. It stays in the
     * Action Center. Android shows its own for an instant first. Cancelling it also stops its sound and vibration, so this is
     * only done while the screen is on (see [keepForSound]). The app is told the notification was dismissed.
     */
    fun hold(entry: NotificationEntry) {
        if (!actionCenterOn) return
        keep(entry)
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

    /** Everything kept is let go, the register on disk too: the user turned the access to the notifications off, or cleared them all. */
    internal fun clearHeld() {
        synchronized(held) { held.clear(); dismissed.clear() }
        ledger?.clear()
    }

    /**
     * Android ended a notification. Only the user doing it in the shade of Android (a tap, a swipe, "clear all") takes the copy with it:
     * the app taking it back (a chat read on another device) or the system ending it (a timeout, a package that changed) does not, and the
     * launcher's own cancel (see [hold]) keeps it by design.
     */
    fun removedByUser(reason: Int) = reason == REASON_CLICK || reason == REASON_CANCEL || reason == REASON_CANCEL_ALL

    /** The copies kept for the Action Center, by key. */
    internal fun heldKeys(): Set<String> = synchronized(held) { held.keys.toSet() }

    /**
     * The post (its time) of each notification the user dismissed that Android may still list: [update] skips it until Android lets go of it,
     * so a read in between does not put it back (it would stay, as the cancel that follows is not "the user" for [removedByUser]).
     * A newer post of the same key is news and passes.
     */
    private val dismissed = HashMap<String, Long>()

    /** The notification is gone for good: dismissed, or its place taken. */
    fun forget(key: String) {
        val gone = synchronized(held) {
            val entry = held.remove(key) ?: _entries.value.firstOrNull { it.key == key }
            if (entry != null) dismissed[key] = maxOf(dismissed[key] ?: Long.MIN_VALUE, entry.postTime)
            val keys = mutableListOf(key)
            // The last notification of a group went: its heading ("3 new messages") has nothing left to head and goes with it.
            if (entry != null && !entry.summary && entry.group != null) {
                fun NotificationEntry.sameGroup() = packageName == entry.packageName && group == entry.group
                if (held.values.none { !it.summary && it.sameGroup() }) {
                    held.values.filter { it.summary && it.sameGroup() }.forEach { held.remove(it.key); keys += it.key }
                }
            }
            keys
        }
        ledger?.remove(gone)
        _entries.value = _entries.value.filterNot { it.key in gone }
        service?.heldChanged()
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

    /** A notification of the launcher itself is not news for the banner and the Action Center, except what it tells the user (a text message, a new mail, a timer, a reminder, a data limit): those are news like the ones of any other app. What stays on in the shade (a call, a recording, the mail connection) and the quiet notices (an update, Shizuku) are not. */
    fun isOwn(packageName: String, ownPackage: String, channelId: String?): Boolean =
        packageName == ownPackage && channelId !in NEWS_CHANNELS

    private val NEWS_CHANNELS = setOf(
        com.metrowin.launcher.messages.SmsReceiver.CHANNEL, com.metrowin.launcher.mail.MailSync.CHANNEL, com.metrowin.launcher.datasense.DataLimitWatch.CHANNEL,
        Alerts.CHANNEL_TIMER, Alerts.CHANNEL_REMINDER,
    )

    /** With the screen off or locked a notification is left in the shade of Android: taking it away would cut its sound. */
    fun keepForSound(interactive: Boolean, locked: Boolean): Boolean = !interactive || locked

    /** How long after it came a notification may still be playing its sound or vibration. */
    const val SOUND_GRACE_MS = 5_000L

    /** True while a notification that came at [postTime] may still be sounding: cancelling it now would cut it. */
    fun soundMayPlay(postTime: Long, now: Long): Boolean = now - postTime < SOUND_GRACE_MS

    /** Every notification up now that may be hidden is moved to the Action Center: at once, when the setting is turned on. */
    fun sweep() {
        service?.hideAllNow()
    }

    private val KEPT_CATEGORIES = setOf(
        Notification.CATEGORY_CALL, Notification.CATEGORY_ALARM, Notification.CATEGORY_NAVIGATION, Notification.CATEGORY_TRANSPORT,
        Notification.CATEGORY_PROGRESS, Notification.CATEGORY_SERVICE, Notification.CATEGORY_STOPWATCH,
    )

    /** The notice Android posts while an app has a window over the others. It cannot be dismissed and comes back with every banner. */
    fun isOverlayNotice(packageName: String, tag: String?, channelId: String?): Boolean {
        if (packageName != "android") return false
        fun String?.flat() = orEmpty().lowercase().filter { it.isLetter() }
        return tag.flat().contains("alertwindow") || channelId.flat().contains("alertwindow")
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
        // Opened from the list, the app is seen: the notification goes, whatever the app asked for, and the others of that app with it.
        com.metrowin.launcher.gestures.LockAccessibilityService.opening(entry.packageName)
        if (entry.contentIntent == null) openApp(context, entry.packageName)
        if (entry.autoCancel || clearOnOpen) {
            forget(entry.key)
            service?.cancelNotification(entry.key)
        }
        appOpened(entry.packageName)
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

    fun ofApp(entries: List<NotificationEntry>, packageName: String): List<NotificationEntry> = entries.filter { it.packageName == packageName }

    /** Takes away the notifications of an app that was opened; they stay in the history. */
    fun dismissApp(packageName: String) {
        val mine = ofApp(_entries.value, packageName)
        if (mine.isEmpty()) return
        rememberDismissed(mine)
        mine.forEach { entry ->
            forget(entry.key)
            service?.cancelNotification(entry.key)
        }
    }

    /** A kept notification has no intent of the app any more (it was read back from disk): the app itself opens. */
    private fun openApp(context: Context, packageName: String) {
        runCatching { context.packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity) }
    }

    fun dismissAll() {
        rememberDismissed(_entries.value)
        clearHeld()
        synchronized(held) { _entries.value.forEach { dismissed[it.key] = it.postTime } }
        _entries.value = emptyList()
        service?.cancelAllNotifications()
    }

    private const val JUST_OPENED_MS = 8_000L
    private const val MAX_HISTORY = 30
}
