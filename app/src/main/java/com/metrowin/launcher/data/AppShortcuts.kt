package com.metrowin.launcher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch

/** A shortcut an app offers on long press ("new message", "incognito tab"), for the menu of the app list. */
data class AppShortcut(val id: String, val packageName: String, val label: String, val user: UserHandle)

/**
 * The shortcuts apps publish (the same ones the home screen of Android shows on a long press). Only the launcher the
 * phone uses as its home may ask for them, so this finds none while another launcher is the home app: nothing breaks,
 * the menu simply has no shortcuts.
 */
class AppShortcuts(
    context: Context,
    /** Which shortcuts to ask for. Tests ask for the dynamic ones only: that is all the test double of the system knows. */
    private val queryFlags: Int = LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    /** Blocking (a call to the system); at most [limit], in the order the app ranks them. */
    fun of(item: AppItem, limit: Int = SHOWN): List<AppShortcut> {
        val target = item.launch as? Launch.Activity ?: return emptyList()
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        return runCatching {
            val query = LauncherApps.ShortcutQuery()
                .setPackage(target.component.packageName)
                .setQueryFlags(queryFlags)
            launcherApps.getShortcuts(query, target.user).orEmpty()
        }.getOrDefault(emptyList()).let { found -> choose(found, target.user, limit) }
    }

    /** Starts [shortcut]; false when the system refused (it was removed, or the app is not ready). */
    fun start(shortcut: AppShortcut): Boolean =
        runCatching { launcherApps.startShortcut(shortcut.packageName, shortcut.id, null, null, shortcut.user) }.isSuccess

    companion object {
        const val SHOWN = 4

        /** Which of [found] to offer: the enabled ones that have a name, in the order of their rank, without repeats. */
        fun choose(found: List<ShortcutInfo>, user: UserHandle, limit: Int = SHOWN): List<AppShortcut> =
            found
                .filter { it.isEnabled }
                .sortedBy { it.rank }
                .mapNotNull { info ->
                    val label = (info.shortLabel ?: info.longLabel)?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    AppShortcut(info.id, info.`package`, label, user)
                }
                .distinctBy { it.label }
                .take(limit)
    }
}
