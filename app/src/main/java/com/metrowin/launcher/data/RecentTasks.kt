package com.metrowin.launcher.data

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import com.metrowin.launcher.recents.RecentsActivity

/**
 * The apps used last, for the cards of the recent apps. Android hands a launcher no list of the tasks it keeps, but the usage access (which the
 * launcher asks for anyway) says which app came to the front and when. An app the user closed with its cross stays out of the list until it is
 * used again.
 */
object RecentTasks {
    // Six hours: the cards show 12 apps at most, and walking a day of events (thousands) for them made every open slow.
    private const val WINDOW_MS = 6L * 3_600_000
    private const val FILE = "metrowin_recents"

    /** The apps of [events] (package and time), the latest use of each, newest first, without [skip] and without those closed after their last use. */
    fun order(events: List<Pair<String, Long>>, dismissed: Map<String, Long>, skip: Set<String>, limit: Int): List<String> =
        events.groupBy({ it.first }, { it.second }).mapValues { it.value.max() }
            .filter { (pkg, last) -> pkg !in skip && last > (dismissed[pkg] ?: 0L) }
            .entries.sortedByDescending { it.value }.map { it.key }.take(limit)

    private fun prefs(context: Context) = Prefs.open(context, FILE)

    private fun dismissed(context: Context): Map<String, Long> = prefs(context).all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()

    /** Blocking, off the main thread. Only apps that can be opened again are listed. */
    fun read(context: Context, limit: Int = 12): List<String> {
        val usage = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val now = System.currentTimeMillis()
        val last = HashMap<String, Long>()
        val cursor = runCatching { usage.queryEvents(now - WINDOW_MS, now) }.getOrNull() ?: return emptyList()
        val event = UsageEvents.Event()
        while (cursor.hasNextEvent()) {
            cursor.getNextEvent(event)
            // ACTIVITY_RESUMED (29) is MOVE_TO_FOREGROUND (1) renamed: the same number.
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) last[event.packageName] = maxOf(last[event.packageName] ?: 0L, event.timeStamp)
        }
        val pm = context.packageManager
        val skip = setOf(context.packageName, "com.android.systemui")
        return order(last.toList(), dismissed(context), skip, limit * 2).filter { pm.getLaunchIntentForPackage(it) != null }.take(limit)
    }

    /** The app on screen at the end of [events] (package, resumed or paused, oldest first): the last one resumed that was not paused since, or null. */
    fun top(events: List<Pair<String, Boolean>>): String? {
        var top: String? = null
        for ((pkg, resumed) in events) if (resumed) top = pkg else if (pkg == top) top = null
        return top
    }

    /** Blocking, off the main thread: the app in front now by the usage events, or null (no usage access, or none known). */
    fun foreground(context: Context): String? {
        val usage = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val cursor = runCatching { usage.queryEvents(now - WINDOW_MS, now) }.getOrNull() ?: return null
        val events = ArrayList<Pair<String, Boolean>>()
        val event = UsageEvents.Event()
        while (cursor.hasNextEvent()) {
            cursor.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> events += event.packageName to true
                UsageEvents.Event.ACTIVITY_PAUSED -> events += event.packageName to false
            }
        }
        return top(events)
    }

    /** The cross of a card: the app is taken off the list and stopped (the shell's force stop when Shizuku is there, else what Android lets an app do). */
    fun close(context: Context, packageName: String) {
        prefs(context).edit { putLong(packageName, System.currentTimeMillis()) }
        if (ShizukuShell.ready()) ShizukuShell.async(listOf(arrayOf("am", "force-stop", packageName)))
        else context.getSystemService(ActivityManager::class.java)?.killBackgroundProcesses(packageName)
    }

    /** Opens the cards when the usage access is given; false (and nothing opened) when it is not, so the caller opens the recent apps of Android. */
    fun open(context: Context): Boolean {
        if (!UsageAccess.granted(context)) return false
        return runCatching { context.startActivity(Intent(context, RecentsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }
}
