package com.metrowin.launcher.notifications

import android.app.Notification
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Feeds [NotificationHub]. Android starts it once the user turns on notification access for the launcher, and keeps it
 * alive for good, so it does nothing while no screen of the launcher is looking (see [NotificationPacer]): a phone full of
 * apps posts notifications all day, and reading them all at each one is what this service must never do.
 */
class NotificationService : NotificationListenerService() {
    private val pacer = NotificationPacer()
    private val handler = Handler(Looper.getMainLooper())
    private val readLater = Runnable { refresh() }

    override fun onListenerConnected() {
        NotificationHub.service = this
        changed()
    }

    override fun onListenerDisconnected() {
        if (NotificationHub.service === this) NotificationHub.service = null
        handler.removeCallbacks(readLater)
        pacer.away()
        NotificationHub.update(emptyList())
        NotificationHub.updateApps(emptyList())
        seen.clear()
        // What is read next time is read from scratch.
        pacer.changed(SystemClock.elapsedRealtime(), watched = false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = changed()

    override fun onNotificationRemoved(sbn: StatusBarNotification) = changed()

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
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT) ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
        val worthShowing = NotificationHub.isWorthShowing(
            ongoing = sbn.isOngoing || !sbn.isClearable,
            groupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            fromUs = sbn.packageName == packageName,
            title = title,
            text = text,
        )
        if (!worthShowing) return null
        return NotificationEntry(
            key = sbn.key,
            packageName = sbn.packageName,
            title = title?.toString().orEmpty(),
            text = text?.toString().orEmpty(),
            postTime = sbn.postTime,
            contentIntent = notification.contentIntent,
            autoCancel = notification.flags and Notification.FLAG_AUTO_CANCEL != 0,
            reply = replyOf(notification),
        )
    }

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
}
