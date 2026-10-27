package com.metrowin.launcher.notifications

import com.metrowin.launcher.data.LauncherSettings
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
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
        NotificationHub.service = this
        syncEdge()
        changed()
        // What is in the shade of Android already, when the setting asks for it to be moved away.
        handler.post { hideAllNow() }
    }

    override fun onListenerDisconnected() {
        if (NotificationHub.service === this) NotificationHub.service = null
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
        if (dropFiltered(sbn)) {
            changed()
            return
        }
        showOverBanner(sbn)
        hideSystemCopy(sbn)
        changed()
    }

    /**
     * With "hide the notifications of Android" on, a notification goes from the shade of Android to the Action Center whether or not it
     * got a banner: a quiet one (a silent channel, a group summary), one that came with the screen off or locked. The banner decides what
     * is shown, never what is taken away.
     */
    private fun hideSystemCopy(sbn: StatusBarNotification) {
        val store = SettingsStore(this)
        if (store.hideSystemOn()) hideFromAndroid(sbn, store.load())
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
            entry != null -> NotificationHub.hold(entry)
            // A bundle ("5 new messages"): its notifications are moved one by one, so the heading has nothing left to head.
            notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 -> cancelNotification(sbn.key)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        overlaySeen.remove(sbn.key)
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
        if (!Settings.canDrawOverlays(this)) return
        val store = SettingsStore(this)
        if (!store.overlayBannerOn()) return
        val ranking = NotificationListenerService.Ranking()
        val known = runCatching { currentRanking.getRanking(sbn.key, ranking) }.getOrDefault(false)
        val entry = toEntry(sbn) ?: return
        if (overlaySeen[sbn.key] == BannerGate.signature(entry)) return
        val settings = store.load()
        val allowed = OverlayRules.shows(
            settings, sbn.packageName,
            canDraw = true, launcherOnScreen = false,
            interactive = getSystemService(PowerManager::class.java).isInteractive,
            locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked,
            importance = if (known) ranking.importance else NotificationManager.IMPORTANCE_DEFAULT,
            passesDoNotDisturb = !known || ranking.matchesInterruptionFilter(),
        )
        if (!allowed) return
        if (overlaySeen.size >= MAX_OVERLAY_KEYS) overlaySeen.clear()
        overlaySeen[sbn.key] = BannerGate.signature(entry)
        overlay.show(entry, settings)
        if (settings.hideSystemNotifications) NotificationHub.hold(entry)
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

    private fun refresh() {
        pacer.read(SystemClock.elapsedRealtime())
        val active = runCatching { activeNotifications }.getOrNull() ?: return
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
                ?: Read(
                    reader.read(
                        key = sbn.key, packageName = sbn.packageName, postTime = sbn.postTime, notification = sbn.notification,
                        ongoing = sbn.isOngoing || !sbn.isClearable,
                        groupSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
                        withPictures = NotificationHub.livePictures,
                    ),
                    now,
                )
        }
        seen.keys.retainAll(reads.map { it.app.key }.toSet())
        reads.forEach { seen[it.app.key] = it }
        NotificationHub.updateApps(reads.map { it.app })
    }

    private fun toEntry(sbn: StatusBarNotification): NotificationEntry? {
        val notification = sbn.notification
        val extras = notification.extras
        // Android's own "shown over other apps" notice about the banner and the strip of the launcher: not one to list or to count.
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
        )
    }

    /** The picture of a notification with one (a photo that came in a chat, a screenshot), or null. */
    private fun pictureOf(extras: android.os.Bundle): android.graphics.Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            extras.getParcelable(Notification.EXTRA_PICTURE, android.graphics.Bitmap::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable<android.os.Parcelable>(Notification.EXTRA_PICTURE) as? android.graphics.Bitmap
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
        const val MAX_OVERLAY_KEYS = 64
        const val MAX_ACTIONS = 3
    }
}
