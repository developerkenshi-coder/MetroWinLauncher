package com.metrowin.launcher.data

import com.metrowin.launcher.model.AppItem

/** Which apps the app list, its search and its jump list show. */
object AppFilter {
    /**
     * [items] without the shortcuts that only live on Start, the apps the user hid, and the work or private
     * space apps when those are switched off.
     */
    fun visible(items: List<AppItem>, settings: LauncherSettings): List<AppItem> =
        items.filter { item ->
            !item.pinOnly &&
                item.id !in settings.hiddenApps &&
                (settings.showWorkApps || !item.isWork) &&
                (settings.showPrivateApps || !item.isPrivate)
        }

    /** Every app can be hidden but the launcher's own settings: hiding those would leave no way to bring the others back. */
    fun canHide(item: AppItem): Boolean = item.id != AppRepository.SETTINGS_ID && !item.pinOnly

    /** The hidden apps that exist, for the settings page that brings them back, in the order of [items]. */
    fun hidden(items: List<AppItem>, settings: LauncherSettings): List<AppItem> =
        items.filter { it.id in settings.hiddenApps && !it.pinOnly }
}
