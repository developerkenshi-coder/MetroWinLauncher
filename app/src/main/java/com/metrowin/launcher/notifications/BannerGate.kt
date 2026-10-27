package com.metrowin.launcher.notifications

/**
 * Which notifications are news for a banner. A chat keeps one notification and posts it again at each message, with the same key:
 * what counts is the post (its time and what it says), not the key, or every message after the first would go without a banner.
 */
object BannerGate {
    fun signature(entry: NotificationEntry): String = "${entry.postTime}\u0000${entry.title}\u0000${entry.text}"

    /** [seen] is what [seen] returned for what was up before. */
    fun isNew(seen: Map<String, String>, entry: NotificationEntry): Boolean = seen[entry.key] != signature(entry)

    fun seen(entries: List<NotificationEntry>): Map<String, String> = entries.associate { it.key to signature(it) }
}
