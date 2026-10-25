package com.metrowin.launcher.notifications

import androidx.compose.runtime.Immutable

/** What the Action Center lists: one notification, or the several of one app under a single line. */
sealed interface NotificationItem {
    val key: String

    @Immutable
    data class Single(val entry: NotificationEntry) : NotificationItem {
        override val key: String get() = entry.key
    }

    /** Two or more notifications of [packageName], newest first. */
    @Immutable
    data class Group(val packageName: String, val entries: List<NotificationEntry>) : NotificationItem {
        override val key: String get() = "group:$packageName"
        val latest: NotificationEntry get() = entries.first()
    }
}

object NotificationGroups {
    /**
     * [entries] (newest first) as the list the panel shows. With [grouped], the notifications of an app that has more
     * than one become a [NotificationItem.Group] at the place of the app's newest; the order of the list is by newest.
     */
    fun build(entries: List<NotificationEntry>, grouped: Boolean): List<NotificationItem> {
        if (!grouped) return entries.map(NotificationItem::Single)
        val byApp = entries.groupBy { it.packageName }
        val seen = HashSet<String>()
        return buildList {
            for (entry in entries) {
                val same = byApp.getValue(entry.packageName)
                if (same.size == 1) {
                    add(NotificationItem.Single(entry))
                } else if (seen.add(entry.packageName)) {
                    add(NotificationItem.Group(entry.packageName, same))
                }
            }
        }
    }
}
