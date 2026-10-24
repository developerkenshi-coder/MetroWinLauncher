package com.metrowin.launcher

import android.app.ActivityOptions
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import android.graphics.Rect
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.metrowin.launcher.data.AppRepository
import com.metrowin.launcher.data.AppSection
import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.data.DefaultStart
import com.metrowin.launcher.data.StartEntries
import com.metrowin.launcher.data.IconLoader
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.data.QuickActionsController
import com.metrowin.launcher.data.LiveTileRepository
import com.metrowin.launcher.data.SettingsStore
import com.metrowin.launcher.data.StartBackgroundStore
import com.metrowin.launcher.data.TileStore
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.StartEntry
import com.metrowin.launcher.notifications.NotificationEntry
import com.metrowin.launcher.notifications.NotificationHub
import com.metrowin.launcher.notifications.NotificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** [count] only ever grows, so every press is a new value even when nothing else differs. */
data class HomePress(val count: Int = 0, val fromBackground: Boolean = false)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val apps = AppRepository(application)
    private val store = TileStore(application)
    private val liveTiles = LiveTileRepository(application)
    private val settingsStore = SettingsStore(application)
    private val backgroundStore = StartBackgroundStore(application)
    private val quickActions = QuickActionsController(application)

    val icons = IconLoader(application)

    private val _items = MutableStateFlow<List<AppItem>>(emptyList())
    val items: StateFlow<List<AppItem>> = _items.asStateFlow()

    /** The app list, grouped by first letter. */
    val sections: StateFlow<List<AppSection>> =
        _items.map(AppSections::group).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** What is pinned to Start: tiles and folders, in order. */
    private val _entries = MutableStateFlow<List<StartEntry>>(emptyList())
    val entries: StateFlow<List<StartEntry>> = _entries.asStateFlow()

    private val _live = MutableStateFlow(LiveTileData())
    val live: StateFlow<LiveTileData> = _live.asStateFlow()

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

    /** The photo behind the Start tiles, if the user picked one. */
    private val _background = MutableStateFlow<ImageBitmap?>(null)
    val background: StateFlow<ImageBitmap?> = _background.asStateFlow()

    /** Notifications for the Action Center. Empty until the user turns on notification access. */
    val notifications: StateFlow<List<NotificationEntry>> = NotificationHub.entries

    /** How many notifications each app has: the counters on the tiles. */
    val badges: StateFlow<Map<String, Int>> =
        NotificationHub.entries.map(NotificationHub::countsByPackage).stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _notificationAccess = MutableStateFlow(hasNotificationAccess())
    val notificationAccess: StateFlow<Boolean> = _notificationAccess.asStateFlow()

    val torchOn: StateFlow<Boolean> = quickActions.torchOn

    private val _permissionsHandled = MutableStateFlow(store.permissionsHandled)
    val permissionsHandled: StateFlow<Boolean> = _permissionsHandled.asStateFlow()

    /** The Home button pressed while the launcher is already running: how many times, and how the last one found us. */
    private val _homePresses = MutableStateFlow(HomePress())
    val homePresses: StateFlow<HomePress> = _homePresses.asStateFlow()

    private var tilesReady = false
    private var loadedLocale: Locale? = null
    private var reloadJob: Job? = null
    private val stopObservingPackages: () -> Unit

    init {
        reloadApps()
        if (_settings.value.hasStartBackground) loadBackground()
        stopObservingPackages = apps.observe(::scheduleReload)
    }

    override fun onCleared() {
        stopObservingPackages()
        quickActions.close()
    }

    /** Called every time the launcher comes back on screen. */
    fun onStart() {
        if (loadedLocale != null && loadedLocale != Locale.getDefault()) reloadApps()
        refreshLive()
        val access = hasNotificationAccess()
        _notificationAccess.value = access
        // After the process was killed Android does not always bind the service again by itself.
        if (access && NotificationHub.service == null) {
            NotificationListenerService.requestRebind(ComponentName(getApplication(), NotificationService::class.java))
        }
    }

    private fun hasNotificationAccess() =
        NotificationManagerCompat.getEnabledListenerPackages(getApplication()).contains(getApplication<Application>().packageName)

    fun quickActionState(action: QuickAction): Boolean? = quickActions.isOn(action)

    fun activateQuickAction(action: QuickAction) = quickActions.activate(action)

    /** Runs what the notification does when tapped, then removes it if the app asked for that. */
    fun openNotification(entry: NotificationEntry) {
        runCatching {
            val options = if (Build.VERSION.SDK_INT >= 34) {
                @Suppress("DEPRECATION") // replaced on Android 16 by finer modes, this one still works there
                val allowed = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(allowed).toBundle()
            } else {
                null
            }
            entry.contentIntent?.send(getApplication(), 0, null, null, null, null, options)
        }
        if (entry.autoCancel) NotificationHub.dismiss(entry.key)
    }

    fun dismissNotification(entry: NotificationEntry) = NotificationHub.dismiss(entry.key)

    fun clearNotifications() = NotificationHub.dismissAll()

    fun openNotificationAccessSettings() = startSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun openSystemSettings() = startSettings(Settings.ACTION_SETTINGS)

    private fun startSettings(action: String) {
        runCatching { getApplication<Application>().startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** [fromBackground] is true when the launcher was stopped, behind another app, when the button was pressed. */
    fun onHomePressed(fromBackground: Boolean) {
        _homePresses.value = HomePress(_homePresses.value.count + 1, fromBackground)
    }

    fun refreshLive() {
        viewModelScope.launch {
            _live.value = withContext(Dispatchers.IO) { liveTiles.load() }
        }
    }

    fun onPermissionsFinished() {
        store.permissionsHandled = true
        _permissionsHandled.value = true
        refreshLive()
    }

    fun updateSettings(change: (LauncherSettings) -> LauncherSettings) {
        val updated = change(_settings.value)
        if (updated != _settings.value) {
            _settings.value = updated
            settingsStore.save(updated)
        }
    }

    /** Shrinks and keeps the photo the user picked as the Start background. */
    fun setStartBackground(uri: Uri) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { backgroundStore.save(uri) }
            if (saved) {
                updateSettings { it.copy(backgroundRevision = it.backgroundRevision + 1) }
                loadBackground()
            } else {
                Toast.makeText(getApplication(), R.string.photo_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun clearStartBackground() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { backgroundStore.clear() }
            _background.value = null
            updateSettings { it.copy(backgroundRevision = 0) }
        }
    }

    private fun loadBackground() {
        viewModelScope.launch {
            _background.value = withContext(Dispatchers.IO) { backgroundStore.load()?.asImageBitmap() }
        }
    }

    fun isPinned(id: String) = StartEntries.isPinned(_entries.value, id)

    fun pin(item: AppItem) = updateEntries { StartEntries.pin(it, item.id) }

    fun unpin(id: String) = updateEntries { StartEntries.unpin(it, id) }

    fun resize(id: String) = updateEntries { StartEntries.resize(it, id) }

    fun moveTo(draggedId: String, targetId: String) = updateEntries { StartEntries.moveTo(it, draggedId, targetId) }

    fun merge(draggedId: String, ontoId: String) = updateEntries { StartEntries.merge(it, draggedId, ontoId) }

    fun releaseFolder(folderId: String) = updateEntries { StartEntries.release(it, folderId) }

    fun removeFromFolder(folderId: String, childId: String) =
        updateEntries { StartEntries.removeFromFolder(it, folderId, childId) }

    fun renameFolder(folderId: String, name: String) = updateEntries { StartEntries.rename(it, folderId, name) }

    /** Returns false (and tells the user) when the app could not be opened. */
    fun launch(item: AppItem, sourceBounds: Rect? = null): Boolean {
        val opened = apps.launch(item, sourceBounds)
        if (!opened) {
            Toast.makeText(getApplication(), R.string.launch_failed, Toast.LENGTH_SHORT).show()
        }
        return opened
    }

    fun uninstall(item: AppItem) {
        apps.uninstall(item)
    }

    private fun scheduleReload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            delay(RELOAD_DEBOUNCE_MS) // an install fires several package events in a row
            reload()
        }
    }

    private fun reloadApps() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val list = withContext(Dispatchers.Default) { apps.load() }
        loadedLocale = Locale.getDefault()
        icons.clear()
        _items.value = list
        syncTiles(list)
    }

    private fun syncTiles(items: List<AppItem>) {
        if (items.isEmpty()) return // never prune the Start screen on a failed or empty load
        val known = items.mapTo(HashSet()) { it.id }
        val base = if (tilesReady) _entries.value else store.loadTiles() ?: DefaultStart.build(items)
        val kept = StartEntries.prune(base, known)
        tilesReady = true
        if (kept != _entries.value) {
            _entries.value = kept
            store.saveTiles(kept)
        }
    }

    private inline fun updateEntries(change: (List<StartEntry>) -> List<StartEntry>) {
        val updated = change(_entries.value)
        if (updated != _entries.value) {
            _entries.value = updated
            store.saveTiles(updated)
        }
    }

    private companion object {
        const val RELOAD_DEBOUNCE_MS = 400L
    }
}
