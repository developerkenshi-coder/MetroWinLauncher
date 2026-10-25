package com.metrowin.launcher.data

import com.metrowin.launcher.model.AppItem
import org.json.JSONObject

/** Names the user gave to apps. Every screen shows the new name: the list, the tiles, the search, the voice. */
object AppLabels {
    const val MAX = 40

    /** [items] with the names the user chose, the list in alphabetical order again. */
    fun apply(items: List<AppItem>, labels: Map<String, String>): List<AppItem> {
        if (labels.isEmpty()) return items
        val renamed = items.map { item -> labels[item.id]?.let { item.copy(label = it) } ?: item }
        return AppSections.sort(renamed)
    }

    /** A name as it is kept: trimmed and cut, or null when it is blank (the original name is back). */
    fun clean(name: String): String? = name.trim().take(MAX).takeIf { it.isNotBlank() }

    fun encode(labels: Map<String, String>): JSONObject = JSONObject().also { o -> labels.toSortedMap().forEach { (k, v) -> o.put(k, v) } }

    fun decode(o: JSONObject?): Map<String, String> {
        o ?: return emptyMap()
        return o.keys().asSequence().mapNotNull { key -> clean(o.optString(key))?.let { key to it } }.toMap()
    }
}

/** Apps that ask for the screen lock before they open. */
object AppLock {
    /**
     * Whether opening [item] must be confirmed with the screen lock first. Without a screen lock on the phone there
     * is nothing to confirm with, so nothing is asked (and the lock cannot be turned on, see [canLock]).
     */
    fun mustConfirm(item: AppItem, locked: Set<String>, deviceSecure: Boolean): Boolean = deviceSecure && item.id in locked

    /** Only real apps can be locked, not the launcher's own pages, shortcuts or searches. */
    fun canLock(item: AppItem): Boolean = item.launch is com.metrowin.launcher.model.Launch.Activity && !item.pinOnly
}
