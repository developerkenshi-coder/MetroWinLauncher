package com.metrowin.launcher

import android.app.Application
import android.graphics.Rect
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.metrowin.launcher.data.AppRepository
import com.metrowin.launcher.data.AppSection
import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.data.DefaultStart
import com.metrowin.launcher.data.IconLoader
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.LiveTileRepository
import com.metrowin.launcher.data.TileStore
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
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

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val apps = AppRepository(application)
    private val store = TileStore(application)
    private val liveTiles = LiveTileRepository(application)

    val icons = IconLoader(application)

    private val _items = MutableStateFlow<List<AppItem>>(emptyList())
    val items: StateFlow<List<AppItem>> = _items.asStateFlow()

    /** The app list, grouped by first letter. */
    val sections: StateFlow<List<AppSection>> =
        _items.map(AppSections::group).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _tiles = MutableStateFlow<List<Tile>>(emptyList())
    val tiles: StateFlow<List<Tile>> = _tiles.asStateFlow()

    private val _live = MutableStateFlow(LiveTileData())
    val live: StateFlow<LiveTileData> = _live.asStateFlow()

    private val _permissionsHandled = MutableStateFlow(store.permissionsHandled)
    val permissionsHandled: StateFlow<Boolean> = _permissionsHandled.asStateFlow()

    /** Counts presses of the Home button while the launcher is already on screen. */
    private val _homePresses = MutableStateFlow(0)
    val homePresses: StateFlow<Int> = _homePresses.asStateFlow()

    private var tilesReady = false
    private var loadedLocale: Locale? = null
    private var reloadJob: Job? = null
    private val stopObservingPackages: () -> Unit

    init {
        reloadApps()
        stopObservingPackages = apps.observe(::scheduleReload)
    }

    override fun onCleared() {
        stopObservingPackages()
    }

    /** Called every time the launcher comes back on screen. */
    fun onStart() {
        if (loadedLocale != null && loadedLocale != Locale.getDefault()) reloadApps()
        refreshLive()
    }

    fun onHomePressed() {
        _homePresses.value++
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

    fun isPinned(id: String) = _tiles.value.any { it.id == id }

    fun pin(item: AppItem) = updateTiles { tiles ->
        if (tiles.any { it.id == item.id }) tiles else tiles + Tile(item.id, TileSize.Medium)
    }

    fun unpin(id: String) = updateTiles { tiles -> tiles.filterNot { it.id == id } }

    fun resize(id: String) = updateTiles { tiles ->
        tiles.map { if (it.id == id) it.copy(size = it.size.next()) else it }
    }

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
        val base = if (tilesReady) _tiles.value else store.loadTiles() ?: DefaultStart.build(items)
        val kept = base.filter { it.id in known }
        tilesReady = true
        if (kept != _tiles.value) {
            _tiles.value = kept
            store.saveTiles(kept)
        }
    }

    private inline fun updateTiles(change: (List<Tile>) -> List<Tile>) {
        val updated = change(_tiles.value)
        if (updated != _tiles.value) {
            _tiles.value = updated
            store.saveTiles(updated)
        }
    }

    private companion object {
        const val RELOAD_DEBOUNCE_MS = 400L
    }
}
