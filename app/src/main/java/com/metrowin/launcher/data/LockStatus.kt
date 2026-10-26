package com.metrowin.launcher.data

import androidx.compose.runtime.Immutable
import com.metrowin.launcher.notifications.NotificationEntry

/** An app of the quick status of the lock screen: its icon and the number of its notifications (0 shows the icon alone). */
@Immutable
data class QuickBadge(val packageName: String, val count: Int)

/** The last notification of the app chosen for the detailed status: who it is from, what it says ([text] is empty when the text is kept private). */
@Immutable
data class DetailLine(val packageName: String, val title: String, val text: String)

/** The two kinds of status on the lock screen, as in Windows Phone: up to five apps with a counter, and one app told in detail. */
@Immutable
data class LockStatus(val quick: List<QuickBadge> = emptyList(), val detail: DetailLine? = null)

/** Works out the [LockStatus] from the notifications that are up. Pure, so it is tested. */
object LockStatusBuilder {
    const val MAX = 5

    /**
     * [entries] are the notifications, the newest first, already without the apps hidden from the lock screen. [quickApps] are the
     * packages the user chose (their icons show even with nothing to count); with none chosen the apps with the most notifications
     * take the places, the one that wrote last first among equals. [own] is this launcher: it is never one of those. With
     * [quickOn] off there is no quick status; [detailApp] empty means no detailed one. [hideText] keeps the words of a message
     * off the screen (only who it is from shows).
     */
    fun build(
        entries: List<NotificationEntry>,
        quickOn: Boolean,
        quickApps: List<String>,
        detailApp: String,
        hideText: Boolean,
        own: String,
    ): LockStatus {
        val counts = LinkedHashMap<String, Int>()
        for (entry in entries) counts[entry.packageName] = (counts[entry.packageName] ?: 0) + 1
        val quick = when {
            !quickOn -> emptyList()
            quickApps.isNotEmpty() -> quickApps.distinct().take(MAX).map { QuickBadge(it, counts[it] ?: 0) }
            else -> counts.entries.filter { it.key != own }
                // LinkedHashMap keeps the order of the newest first, and a stable sort keeps it among equal counts.
                .sortedByDescending { it.value }.take(MAX).map { QuickBadge(it.key, it.value) }
        }
        val detail = detailApp.takeIf { it.isNotEmpty() }?.let { pkg ->
            entries.firstOrNull { it.packageName == pkg }?.let { DetailLine(pkg, it.title, if (hideText) "" else it.text) }
        }
        return LockStatus(quick, detail)
    }
}
