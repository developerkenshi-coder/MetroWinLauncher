package com.metrowin.launcher.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Feeds [NotificationHub]. Android starts it once the user turns on notification access for the launcher. */
class NotificationService : NotificationListenerService() {
    override fun onListenerConnected() {
        NotificationHub.service = this
        refresh()
    }

    override fun onListenerDisconnected() {
        if (NotificationHub.service === this) NotificationHub.service = null
        NotificationHub.update(emptyList())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification) = refresh()

    private fun refresh() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        NotificationHub.update(
            active.mapNotNull(::toEntry).sortedByDescending { it.postTime },
        )
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
        )
    }
}
