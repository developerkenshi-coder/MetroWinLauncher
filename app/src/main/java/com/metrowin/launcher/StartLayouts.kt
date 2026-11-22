package com.metrowin.launcher

import android.app.Application
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LayoutStore
import com.metrowin.launcher.data.Layouts
import com.metrowin.launcher.data.ProfileState
import com.metrowin.launcher.data.QuickProfile
import com.metrowin.launcher.data.SavedLayout
import com.metrowin.launcher.data.SettingProfileStore
import com.metrowin.launcher.data.SettingsCodec
import com.metrowin.launcher.data.ShortcutCodec
import com.metrowin.launcher.data.ShortcutDef
import com.metrowin.launcher.data.StartPages
import com.metrowin.launcher.data.TileCodec
import com.metrowin.launcher.model.StartEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the layouts and the quick profiles need of the rest of the launcher, given by [LauncherViewModel] (which keeps the settings, the pages and the shortcuts). */
internal interface LayoutHost {
    val settings: LauncherSettings
    fun updateSettings(change: (LauncherSettings) -> LauncherSettings)
    val pages: List<List<StartEntry>>

    /** Shows [pages] on Start and keeps them. */
    fun showPages(pages: List<List<StartEntry>>)
    val shortcuts: List<ShortcutDef>

    /** Adds shortcuts that a layout brings back (the list of Start is rebuilt and kept). */
    fun addShortcuts(list: List<ShortcutDef>)
    fun itemIds(): Set<String>
}

/**
 * The quick profiles of the settings and the named layouts of Start (the first of the two areas cut out of the big [LauncherViewModel]; the pattern of
 * [ClockTilesController] and [BackupController]): their own state and files, and the rest of the launcher through [LayoutHost].
 */
internal class LayoutController(application: Application, private val host: LayoutHost) {
    // --- quick profiles of the settings

    private val profileStore = SettingProfileStore(application)
    private val _quickProfiles = MutableStateFlow(profileStore.state())
    val quickProfiles: StateFlow<ProfileState> = _quickProfiles.asStateFlow()

    /** Keeps the settings as they are now under [profile]. */
    fun saveProfile(profile: QuickProfile) {
        profileStore.save(profile, host.settings)
        _quickProfiles.value = profileStore.state()
    }

    fun forgetProfile(profile: QuickProfile) {
        profileStore.forget(profile)
        _quickProfiles.value = profileStore.state()
    }

    /** Puts the settings of [profile] on, over the ones of now; what belongs to this phone alone stays as it is. */
    fun applyProfile(profile: QuickProfile) {
        val restored = profileStore.read(profile)?.let { SettingsCodec.decode(it, host.settings) } ?: return
        host.updateSettings { restored }
        profileStore.setActive(profile)
        _quickProfiles.value = profileStore.state()
    }

    // --- named layouts of Start

    private val layoutStore = LayoutStore(application)
    private val _layouts = MutableStateFlow(layoutStore.load())
    val layouts: StateFlow<List<SavedLayout>> = _layouts.asStateFlow()

    /** Reads the layouts again from their file (a backup put new ones there). */
    fun reloadLayouts() {
        _layouts.value = layoutStore.load()
    }

    private fun saveLayouts(list: List<SavedLayout>) {
        _layouts.value = list
        layoutStore.save(list)
    }

    /** Start as it is now (the pages and the shortcuts they hold), as a layout. */
    private fun snapshotLayout(id: String, name: String, hour: Int): SavedLayout {
        val pinned = StartPages.pinnedIds(host.pages)
        val used = host.shortcuts.filter { it.id in pinned }
        return SavedLayout(id, name, hour, TileCodec.encodePages(host.pages), ShortcutCodec.encode(ShortcutCodec.portable(used)))
    }

    /** Keeps Start as it is now under [name]; false when the name is empty or there are already as many layouts as allowed. */
    fun saveLayout(name: String): Boolean {
        val clean = name.trim().take(Layouts.MAX_NAME)
        if (clean.isEmpty() || _layouts.value.size >= Layouts.MAX) return false
        val layout = snapshotLayout(java.util.UUID.randomUUID().toString(), clean, -1)
        saveLayouts(_layouts.value + layout)
        host.updateSettings { it.copy(activeLayout = layout.id) }
        return true
    }

    /** Brings a layout up to date with what is on Start now. */
    fun resaveLayout(id: String) = saveLayouts(_layouts.value.map { if (it.id == id) snapshotLayout(it.id, it.name, it.hour) else it })

    fun deleteLayout(id: String) {
        saveLayouts(_layouts.value.filterNot { it.id == id })
        if (host.settings.activeLayout == id) host.updateSettings { it.copy(activeLayout = "") }
    }

    fun setLayoutHour(id: String, hour: Int) = saveLayouts(_layouts.value.map { if (it.id == id) it.copy(hour = hour.takeIf { h -> h in 0..23 } ?: -1) else it })

    /**
     * Puts a layout on Start. What is on Start now first goes back into the layout that was on, so what was changed during the day is not
     * lost. The shortcuts the layout needs and this phone no longer has come back from it.
     */
    fun applyLayout(id: String) {
        val layout = _layouts.value.firstOrNull { it.id == id } ?: return
        val pages = TileCodec.decodePages(layout.pages) ?: return
        val active = host.settings.activeLayout
        val kept = _layouts.value.map { if (it.id == active && active != id) snapshotLayout(it.id, it.name, it.hour) else it }
        val have = host.shortcuts.mapTo(HashSet()) { it.id }
        val back = ShortcutCodec.decode(layout.shortcuts).orEmpty().filter { it.id !in have }
        if (back.isNotEmpty()) host.addShortcuts(back)
        host.updateSettings { it.copy(startPages = pages.size.coerceIn(1, LauncherSettings.MAX_START_PAGES), activeLayout = id) }
        val shown = StartPages.prune(StartPages.resize(pages, host.settings.startPages), host.itemIds())
        host.showPages(shown)
        saveLayouts(kept)
    }

    /** With the schedule on, the layout whose hour came last takes Start once, when its hour comes; a layout chosen by hand stays until the next hour. */
    fun applyScheduledLayout() {
        if (!host.settings.layoutSchedule) return
        val due = Layouts.due(_layouts.value, java.time.LocalTime.now().hour) ?: return
        if (due.id == layoutStore.lastScheduled) return
        layoutStore.lastScheduled = due.id
        if (due.id != host.settings.activeLayout) applyLayout(due.id)
    }
}
