package com.metrowin.launcher.notifications

import com.metrowin.launcher.data.LauncherSettings

/**
 * The choices made for each app about where its notifications show (see the settings): the Action Center, the lock screen, the
 * banner. They are applied only while the master switch ([LauncherSettings.notifControl]) is on; the choices are kept when it is off.
 * The sound is Android's: the launcher cannot change another app's channel, so it is not here.
 */
object NotificationControl {
    fun forActionCenter(entries: List<NotificationEntry>, settings: LauncherSettings): List<NotificationEntry> =
        without(entries, settings, settings.notifHideCenter)

    fun forLockScreen(entries: List<NotificationEntry>, settings: LauncherSettings): List<NotificationEntry> =
        without(entries, settings, settings.notifHideLock)

    /** Whether a new notification of [packageName] gets a banner: the banner is on, and the app is not one of those switched off. */
    fun showsBanner(packageName: String, settings: LauncherSettings): Boolean =
        settings.toastBanner && !(settings.notifControl && packageName in settings.notifNoBanner)

    private fun without(entries: List<NotificationEntry>, settings: LauncherSettings, hidden: Set<String>): List<NotificationEntry> =
        if (!settings.notifControl || hidden.isEmpty()) entries else entries.filter { it.packageName !in hidden }
}
