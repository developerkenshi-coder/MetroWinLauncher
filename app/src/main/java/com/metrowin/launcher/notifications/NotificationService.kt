package com.metrowin.launcher.notifications

import com.metrowin.launcher.data.LauncherSettings
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import com.metrowin.launcher.data.SettingsStore
import com.metrowin.launcher.gestures.LockAccessibilityService

/**
 * Feeds [NotificationHub]. Android starts it once the user turns on notification access for the launcher, and keeps it
 * alive for good, so it does nothing while no screen of the launcher is looking (see [NotificationPacer]): a phone full of
 * apps posts notifications all day, and reading them all at each one is what this service must never do. The one exception is the
 * banner over other apps, which the user must turn on: it looks at the one notification that came, and nothing else.
 */
class NotificationService : NotificationListenerService() {
    private val pacer = NotificationPacer()
    private val handler = Handler(Looper.getMainLooper())
    private val readLater = Runnable { refresh() }

    override fun onListenerConnected() {
        Log.i(TAG, "listener connected")
        NotificationHub.service = this
        runCatching { registerReceiver(screenWatch, IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_USER_PRESENT) }) }
        NotificationHub.clearOnOpen = SettingsStore(this).load().clearOnOpen
        syncEdge()
        changed()
        // What is in the shade of Android already, when the setting asks for it to be moved away.
        handler.post { hideAllNow() }
    }

    override fun onListenerDisconnected() {
        Log.i(TAG, "listener disconnected")
        if (NotificationHub.service === this) NotificationHub.service = null
        runCatching { unregisterReceiver(screenWatch) }
        handler.removeCallbacks(readLater)
        overlay.hide()
        overlaySeen.clear()
        edge?.dispose()
        NotificationHub.clearHeld()
        pacer.away()
        NotificationHub.update(emptyList())
        NotificationHub.updateApps(emptyList())
        seen.clear()
        // What is read next time is read from scratch.
        pacer.changed(SystemClock.elapsedRealtime(), watched = false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // What the bug report shows of a notification that did not arrive: where it came from and how Android flagged it, never what it says.
        if (!sbn.isOngoing) Log.i(TAG, "posted ${sbn.packageName} channel=${sbn.notification.channelId} group=${sbn.notification.group != null} summary=${sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0} watched=${NotificationHub.watched}")
        if (dropFiltered(sbn)) {
            changed()
            return
        }
        showOverBanner(sbn)
        hideSystemCopy(sbn)
        changed()
        clearIfSeen(sbn)
    }

    /**
     * A notification of the app you are in is read where it is: if that app is still in front a moment later, the notification (and the
     * number on its tile) goes, without a touch on it.
     */
    private fun clearIfSeen(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg != NotificationHub.foregroundPackage) return
        handler.postDelayed({ if (NotificationHub.foregroundPackage == pkg) NotificationHub.appOpened(pkg) }, SEEN_MS)
    }

    /**
     * With "hide the notifications of Android" on, a notification goes from the shade of Android to the Action Center whether or not it
     * got a banner: a quiet one (a silent channel, a group summary). The banner decides what is shown, never what is taken away.
     * With the screen off or locked it stays where it is for now: Android stops the sound and the vibration of a notification the moment
     * it is cancelled, so the ones that came while you were away would be silent. [screenWatch] moves them when you look at the phone.
     */
    private fun hideSystemCopy(sbn: StatusBarNotification) {
        val store = SettingsStore(this)
        if (!store.hideSystemOn() || awayFromScreen()) return
        hideFromAndroid(sbn, store.load())
    }

    private fun awayFromScreen() = NotificationHub.keepForSound(
        interactive = getSystemService(PowerManager::class.java).isInteractive,
        locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked,
    )

    /** When the screen comes on unlocked (or the phone is unlocked), what waited in the shade of Android for its sound is moved. */
    private val screenWatch = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!awayFromScreen()) handler.post { hideAllNow() }
        }
    }

    /**
     * With the filter of words on, a notification that has one of them is taken away from Android altogether, and shows nowhere. The same
     * rules as for hiding keep what must stay (a call, an alarm, the player of the music). True when it was taken away.
     */
    private fun dropFiltered(sbn: StatusBarNotification, known: LauncherSettings? = null): Boolean {
        val store = SettingsStore(this)
        if (known == null && !store.filterOn()) return false
        val settings = known ?: store.load()
        if (!settings.notifFilterOn) return false
        val entry = toEntry(sbn) ?: return false
        if (!NotificationControl.filtered(entry, settings)) return false
        val notification = sbn.notification
        val allowed = NotificationHub.mayHide(
            ongoing = sbn.isOngoing, clearable = sbn.isClearable, fromUs = sbn.packageName == packageName,
            category = notification.category, fullScreen = notification.fullScreenIntent != null, hiddenFromCenter = false,
        )
        if (allowed) cancelNotification(sbn.key)
        return allowed
    }

    /** The same for everything in the shade now: when the listener connects and when a setting that decides it is changed. */
    internal fun hideAllNow() {
        val store = SettingsStore(this)
        if (!store.hideSystemOn() && !store.filterOn()) return
        val settings = store.load()
        runCatching { activeNotifications }.getOrNull()?.forEach { sbn ->
            if (!dropFiltered(sbn, settings) && settings.hideSystemNotifications) hideFromAndroid(sbn, settings)
        }
        changed()
    }

    private fun hideFromAndroid(sbn: StatusBarNotification, settings: LauncherSettings) {
        val notification = sbn.notification
        val allowed = NotificationHub.mayHide(
            ongoing = sbn.isOngoing, clearable = sbn.isClearable, fromUs = sbn.packageName == packageName,
            category = notification.category, fullScreen = notification.fullScreenIntent != null,
            hiddenFromCenter = settings.notifControl && sbn.packageName in settings.notifHideCenter,
        )
        if (!allowed) return
        val entry = toEntry(sbn)
        when {
            entry != null -> {
                keepForLive(sbn)
                NotificationHub.hold(entry)
            }
            // A bundle ("5 new messages"): its notifications are moved one by one, so the heading has nothing left to head.
            notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 -> cancelNotification(sbn.key)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        if (!sbn.isOngoing) Log.i(TAG, "removed ${sbn.packageName} reason=$reason")
        // The launcher's own cancel (see NotificationHub.hold) keeps its copy and what the banner said, so a repost is not a new banner;
        // any other end of the notification (the app took it back, a timeout) takes the copy with it.
        if (reason != REASON_LISTENER_CANCEL && reason != REASON_LISTENER_CANCEL_ALL) {
            NotificationHub.removedByApp(sbn.key)
            overlaySeen.remove(sbn.key)
        }
        changed()
    }

    private val overlay by lazy { OverlayBanner(this, LockAccessibilityService::host) }

    /** What each notification said when it got a banner over other apps: the same post is not shown twice, a new message in the chat is. */
    private val overlaySeen = HashMap<String, String>()

    /**
     * The Metro banner over other apps, for the notification that just came. It is the only work done for a notification while no
     * screen of the launcher is on, so every check that costs something comes after the cheap ones: users who did not allow
     * drawing over other apps, or did not turn the banner on, stop at the first lines.
     */
    private fun showOverBanner(sbn: StatusBarNotification) {
        if (NotificationHub.watched || sbn.packageName == packageName) return
        if (!Settings.canDrawOverlays(this)) return skipped(sbn, "no permission to draw over apps")
        val store = SettingsStore(this)
        if (!store.overlayBannerOn()) return
        val ranking = NotificationListenerService.Ranking()
        val known = runCatching { currentRanking.getRanking(sbn.key, ranking) }.getOrDefault(false)
        val entry = toEntry(sbn) ?: return skipped(sbn, "not worth showing (ongoing, group summary or empty)")
        if (BannerGate.same(overlaySeen[sbn.key], entry)) return skipped(sbn, "the same post again")
        val settings = store.load()
        val allowed = OverlayRules.shows(
            settings, sbn.packageName,
            canDraw = true, launcherOnScreen = false,
            interactive = getSystemService(PowerManager::class.java).isInteractive,
            locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked,
            importance = if (known) ranking.importance else NotificationManager.IMPORTANCE_DEFAULT,
            passesDoNotDisturb = !known || ranking.matchesInterruptionFilter(),
        )
        if (!allowed) return skipped(sbn, "rules: importance=${if (known) ranking.importance else -1} interactive/locked/DnD")
        if (overlaySeen.size >= MAX_OVERLAY_KEYS) overlaySeen.clear()
        overlaySeen[sbn.key] = BannerGate.signature(entry)
        overlay.show(entry, settings)
        if (settings.hideSystemNotifications) {
            keepForLive(sbn)
            NotificationHub.hold(entry)
        }
    }

    private fun skipped(sbn: StatusBarNotification, why: String) {
        Log.i(TAG, "no banner for ${sbn.packageName}: $why")
    }

    private fun changed() = act(pacer.changed(SystemClock.elapsedRealtime(), NotificationHub.watched))

    private fun act(step: NotificationPacer.Step) {
        when (step) {
            NotificationPacer.Step.Now -> {
                handler.removeCallbacks(readLater)
                refresh()
            }
            is NotificationPacer.Step.After -> handler.postDelayed(readLater, step.millis)
            NotificationPacer.Step.Idle -> Unit
        }
    }

    /** A screen of the launcher came on: whatever changed while none was looking is read now. */
    internal fun screenOn() = act(pacer.watched())

    /** The last screen of the launcher went off: nothing is read until one is on again. */
    internal fun screenOff() {
        handler.removeCallbacks(readLater)
        pacer.away()
    }

    private var edge: OverlayActionCenter? = null

    /**
     * The strip at the top edge that opens the Action Center over other apps: there when the user turned it on and the accessibility
     * service is on; not there otherwise. Run when one of the two changes and when the panel goes, never on a timer.
     */
    internal fun syncEdge() {
        val wanted = LockAccessibilityService.host() != null && SettingsStore(this).edgeActionCenterOn()
        // Not wanted any more (or the accessibility service went, and the system took its windows with it): nothing of it is kept.
        if (wanted) (edge ?: OverlayActionCenter(this, LockAccessibilityService::host, ::syncEdge).also { edge = it }).showStrip() else edge?.dispose()
    }

    private var failedReads = 0

    private fun refresh() {
        pacer.read(SystemClock.elapsedRealtime())
        val active = runCatching { activeNotifications }.getOrNull()
        if (active == null) {
            // Android refused (the listener is still connecting, or busy): try again a few times, so the list is not left out of date.
            if (failedReads++ < MAX_READ_RETRIES) act(pacer.changed(SystemClock.elapsedRealtime(), NotificationHub.watched))
            return
        }
        failedReads = 0
        NotificationHub.update(
            active.mapNotNull(::toEntry).sortedByDescending { it.postTime },
        )
        refreshLive(active)
    }

    /** Reads the notifications again, after the settings changed what the live tiles use. */
    fun reload() {
        seen.clear()
        changed()
    }

    private val reader by lazy { NotificationReader(applicationContext) }

    /** What the notifications said when last read, so a picture is decoded once and not at every notification. */
    private val seen = HashMap<String, Read>()

    private class Read(val app: AppNotification, val at: Long)

    private fun readLive(sbn: StatusBarNotification) = Read(
        reader.read(
            key = sbn.key, packageName = sbn.packageName, postTime = sbn.postTime, notification = sbn.notification,
            ongoing = sbn.isOngoing || !sbn.isClearable,
            groupSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            withPictures = NotificationHub.livePictures,
        ),
        SystemClock.elapsedRealtime(),
    )

    /**
     * A notification the launcher takes off the shade of Android is no longer in [getActiveNotifications], but its tile must still say what
     * it is about (the number on the tile comes from the copy the launcher keeps): it is read now, while it is in hand.
     */
    private fun keepForLive(sbn: StatusBarNotification) {
        if (NotificationHub.liveContent && sbn.packageName != packageName) seen[sbn.key] = readLive(sbn)
    }

    /** The copies the launcher keeps changed (one was dismissed or opened): the tiles are worked out again. */
    internal fun heldChanged() = changed()

    private fun refreshLive(active: Array<StatusBarNotification>) {
        if (!NotificationHub.liveContent) {
            seen.clear()
            return
        }
        val now = SystemClock.elapsedRealtime()
        val reads = active.filter { it.packageName != packageName }.map { sbn ->
            val before = seen[sbn.key]
            // A notification is read again when it is posted again, as a song's is at each song. The music is also read
            // again after a while without a new post (its picture can come late), but not at every change of the others.
            before?.takeIf { it.app.postTime == sbn.postTime && (it.app.playing == null || now - it.at < NotificationPacer.MEDIA_FRESH_MS) }
                ?: readLive(sbn)
        }
        val upKeys = reads.mapTo(HashSet()) { it.app.key }
        // What the launcher took off the shade is still news for its tile, until it is dismissed or opened.
        val heldKeys = NotificationHub.heldKeys()
        val taken = seen.values.filter { it.app.key !in upKeys && it.app.key in heldKeys }
        seen.keys.retainAll(upKeys + heldKeys)
        reads.forEach { seen[it.app.key] = it }
        NotificationHub.updateApps((reads + taken).map { it.app })
    }

    private fun toEntry(sbn: StatusBarNotification): NotificationEntry? {
        val notification = sbn.notification
        val extras = notification.extras
        // Android's "shown over other apps" notice about the launcher's own windows.
        if (NotificationHub.isOverlayNotice(sbn.packageName, sbn.tag, notification.channelId)) return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)
        // A chat keeps one notification: its plain text can be an older message, the newest is in the messages.
        val text: CharSequence? = reader.latestMessage(notification)?.let { line ->
            if (line.sender.isNotBlank() && line.sender != title?.toString()) "${line.sender}: ${line.text}" else line.text
        } ?: extras.getCharSequence(Notification.EXTRA_TEXT) ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
        val worthShowing = NotificationHub.isWorthShowing(
            ongoing = sbn.isOngoing || !sbn.isClearable,
            groupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            fromUs = sbn.packageName == packageName,
            title = title,
            text = text,
        )
        if (!worthShowing) return null
        val longText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.length > (text?.length ?: 0) }
        // The buttons that need no typing (the one that takes text is the reply), the way the shade of Android shows them.
        val actions = notification.actions.orEmpty()
            .filter { it.actionIntent != null && it.remoteInputs.isNullOrEmpty() && !it.title.isNullOrBlank() }
            .take(MAX_ACTIONS)
            .map { NotifAction(it.title.toString(), it.actionIntent) }
        return NotificationEntry(
            key = sbn.key,
            packageName = sbn.packageName,
            title = title?.toString().orEmpty(),
            text = text?.toString().orEmpty(),
            postTime = sbn.postTime,
            contentIntent = notification.contentIntent,
            autoCancel = notification.flags and Notification.FLAG_AUTO_CANCEL != 0,
            reply = replyOf(notification),
            longText = longText,
            actions = actions,
            picture = pictureOf(extras),
            urgent = notification.category == Notification.CATEGORY_MESSAGE || notification.category == Notification.CATEGORY_CALL || replyOf(notification) != null,
            icon = runCatching { notification.smallIcon?.loadDrawable(this)?.toBitmap(ICON_PX, ICON_PX)?.let(::EntryIcon) }.getOrNull(),
        )
    }

    /** The picture of a notification with one (a photo that came in a chat, a screenshot), or null. Kept at most [PICTURE_MAX_PX] wide: held entries keep it for good. */
    private fun pictureOf(extras: android.os.Bundle): android.graphics.Bitmap? = runCatching {
        val bitmap = if (Build.VERSION.SDK_INT >= 33) {
            extras.getParcelable(Notification.EXTRA_PICTURE, android.graphics.Bitmap::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable<android.os.Parcelable>(Notification.EXTRA_PICTURE) as? android.graphics.Bitmap
        }
        val longest = maxOf(bitmap?.width ?: 0, bitmap?.height ?: 0)
        if (bitmap == null || longest <= PICTURE_MAX_PX) {
            bitmap
        } else {
            val scale = PICTURE_MAX_PX.toFloat() / longest
            android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        }
    }.getOrNull()

    /** The action that takes free text, if the notification has one: the semantic "reply" first, then any other. */
    private fun replyOf(notification: Notification): ReplyAction? {
        val actions = notification.actions?.toList().orEmpty()
        fun Notification.Action.freeText() = remoteInputs?.firstOrNull { it.allowFreeFormInput }
        // The semantic "reply" first (Android 9 and later says which action it is), then any action that takes text.
        val action = actions.firstOrNull { Build.VERSION.SDK_INT >= 28 && it.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY && it.freeText() != null }
            ?: actions.firstOrNull { it.freeText() != null }
            ?: return null
        val input = action.freeText() ?: return null
        val intent = action.actionIntent ?: return null
        return ReplyAction(intent, input, action.remoteInputs.toList())
    }

    private companion object {
        const val TAG = "MetroNotif"

        /** How long an app stays in front after a notification of it came, for the notification to count as seen. */
        const val SEEN_MS = 2_500L

        const val PICTURE_MAX_PX = 800

        /** The small icon is kept this big: it is drawn at 24dp or so on the lock screen. */
        const val ICON_PX = 64

        const val MAX_OVERLAY_KEYS = 64
        const val MAX_ACTIONS = 3
        const val MAX_READ_RETRIES = 3
    }
}
