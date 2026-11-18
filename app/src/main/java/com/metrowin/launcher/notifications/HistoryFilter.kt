package com.metrowin.launcher.notifications

/** What the list of dismissed notifications shows: the ones of one app, and the ones that have every word of the search in the app, the title or the text. Pure. */
object HistoryFilter {
    fun apply(history: List<NotificationEntry>, query: String, packageName: String?, labelOf: (String) -> String): List<NotificationEntry> {
        val words = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
        return history.filter { entry ->
            (packageName == null || entry.packageName == packageName) &&
                words.all { word -> entry.title.contains(word, true) || entry.text.contains(word, true) || labelOf(entry.packageName).contains(word, true) }
        }
    }
}
