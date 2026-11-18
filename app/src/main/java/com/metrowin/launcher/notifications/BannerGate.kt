package com.metrowin.launcher.notifications

/**
 * Which notifications are news for a banner. A chat keeps one notification and posts it again at each message, with the same key:
 * what counts is the post (its time and what it says), not the key, or every message after the first would go without a banner.
 */
object BannerGate {
    /**
     * An app that posts its notification again right after the launcher took it away (it is told it was dismissed) says the same
     * thing a moment later with a new post time: that is not news. The same words said again after this are.
     */
    const val REPOST_MS = 1_500L

    fun signature(entry: NotificationEntry): String = "${entry.postTime}\u0000${entry.title}\u0000${entry.text}"

    /** Whether [entry] is the post [old] (a [signature]) was made from, or that one posted again at once. */
    fun same(old: String?, entry: NotificationEntry): Boolean {
        val parts = old?.split('\u0000', limit = 2) ?: return false
        val at = parts[0].toLongOrNull() ?: return false
        return parts.getOrNull(1) == "${entry.title}\u0000${entry.text}" && entry.postTime - at in 0 until REPOST_MS
    }

    /** [seen] is what [seen] returned for what was up before. */
    fun isNew(seen: Map<String, String>, entry: NotificationEntry): Boolean = !same(seen[entry.key], entry)

    fun seen(entries: List<NotificationEntry>): Map<String, String> = entries.associate { it.key to signature(it) }

    /** The same words from the same app, under another key (a group's heading, a copy an app posts as well), are not announced twice in this time. */
    const val ANNOUNCE_MS = 3_000L

    private val announced = HashMap<String, Long>()

    /** True when [entry] may get a banner now, and then remembers it: the launcher's own banner and the one over other apps share this. */
    @Synchronized
    fun announce(entry: NotificationEntry, now: Long, recent: MutableMap<String, Long> = announced): Boolean {
        recent.values.removeAll { now - it >= ANNOUNCE_MS }
        val words = "${entry.packageName}\u0000${entry.title}\u0000${entry.text}"
        if (words in recent) return false
        recent[words] = now
        return true
    }
}
