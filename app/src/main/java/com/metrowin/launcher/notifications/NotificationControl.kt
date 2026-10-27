package com.metrowin.launcher.notifications

import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.data.LauncherSettings

/**
 * The choices made for each app about where its notifications show (see the settings): the Action Center, the lock screen, the
 * banner. They are applied only while the master switch ([LauncherSettings.notifControl]) is on; the choices are kept when it is off.
 * The sound is Android's: the launcher cannot change another app's channel, so it is not here.
 */
object NotificationControl {
    /** The Action Center: without the apps kept out, the ones silenced for now and the ones with a word of the filter; messages and calls first when asked. */
    fun forActionCenter(entries: List<NotificationEntry>, settings: LauncherSettings, now: Long = System.currentTimeMillis()): List<NotificationEntry> {
        val shown = without(entries, settings, settings.notifHideCenter).filterNot { muted(it.packageName, settings, now) || filtered(it, settings) }
        return if (settings.notifPriority) shown.sortedWith(compareByDescending<NotificationEntry> { it.urgent }.thenByDescending { it.postTime }) else shown
    }

    fun forLockScreen(entries: List<NotificationEntry>, settings: LauncherSettings, now: Long = System.currentTimeMillis()): List<NotificationEntry> =
        without(entries, settings, settings.notifHideLock).filterNot { muted(it.packageName, settings, now) || filtered(it, settings) }

    /** Whether a new notification of [packageName] gets a banner: the banner is on, and the app is not one of those switched off or silenced. */
    fun showsBanner(packageName: String, settings: LauncherSettings, now: Long = System.currentTimeMillis()): Boolean =
        settings.toastBanner && !(settings.notifControl && packageName in settings.notifNoBanner) && !muted(packageName, settings, now)

    /** The app was silenced from the Action Center and its time is not up. */
    fun muted(packageName: String, settings: LauncherSettings, now: Long = System.currentTimeMillis()): Boolean =
        (settings.mutedUntil[packageName] ?: 0L) > now

    /** The filter is on and a word of it is in the title or the text (accents and capitals do not count). */
    fun filtered(entry: NotificationEntry, settings: LauncherSettings): Boolean {
        if (!settings.notifFilterOn || settings.notifFilterWords.isEmpty()) return false
        val text = AppSections.fold(listOf(entry.title, entry.text, entry.longText.orEmpty()).joinToString(" "))
        return settings.notifFilterWords.any { word -> AppSections.fold(word).let { it.isNotEmpty() && text.contains(it) } }
    }

    /** Whether a notification of [packageName] gets the banner over other apps: that banner is on too, and the app's banner is not switched off. */
    fun showsOverlay(packageName: String, settings: LauncherSettings): Boolean =
        settings.overlayBanner && showsBanner(packageName, settings)

    private fun without(entries: List<NotificationEntry>, settings: LauncherSettings, hidden: Set<String>): List<NotificationEntry> =
        if (!settings.notifControl || hidden.isEmpty()) entries else entries.filter { it.packageName !in hidden }
}
