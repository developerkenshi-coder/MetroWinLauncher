package com.metrowin.launcher

import android.app.ActivityOptions
import android.app.WallpaperManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import android.graphics.Bitmap
import androidx.core.graphics.scale
import android.graphics.Rect
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.metrowin.launcher.data.AppFilter
import com.metrowin.launcher.data.AppRepository
import com.metrowin.launcher.data.BatteryInfo
import com.metrowin.launcher.data.AppSection
import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.data.DefaultStart
import com.metrowin.launcher.data.DominantColor
import com.metrowin.launcher.data.StartEntries
import com.metrowin.launcher.data.StartPages
import com.metrowin.launcher.data.IconLoader
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.OpenMeteo
import com.metrowin.launcher.data.IconPackInfo
import com.metrowin.launcher.data.IconPackRepository
import com.metrowin.launcher.data.PlaceMatch
import com.metrowin.launcher.data.ThirdPartyTile
import com.metrowin.launcher.data.ThirdPartyTiles
import com.metrowin.launcher.data.TileFeed
import com.metrowin.launcher.data.SafSyncStorage
import com.metrowin.launcher.data.SyncEngine
import com.metrowin.launcher.data.SyncLocal
import com.metrowin.launcher.data.SyncOutcome
import com.metrowin.launcher.data.readAtMost
import com.metrowin.launcher.data.ThemeCodec
import com.metrowin.launcher.data.ProfileInfo
import com.metrowin.launcher.data.ProfilesController
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.data.RecentApps
import com.metrowin.launcher.data.QuickActionsController
import com.metrowin.launcher.data.LayoutBackup
import com.metrowin.launcher.data.LayoutBackupCodec
import com.metrowin.launcher.data.LiveTileRepository
import com.metrowin.launcher.data.SafFolder
import com.metrowin.launcher.data.SettingsStore
import com.metrowin.launcher.data.BackupSchedule
import com.metrowin.launcher.data.ContactMatch
import com.metrowin.launcher.data.ContactReader
import com.metrowin.launcher.data.ContactSearch
import com.metrowin.launcher.data.ShortcutDef
import com.metrowin.launcher.data.ShortcutKind
import com.metrowin.launcher.data.SiteInput
import com.metrowin.launcher.data.WidgetProvider
import com.metrowin.launcher.data.WidgetService
import com.metrowin.launcher.data.WidgetSizing
import com.metrowin.launcher.data.ShortcutStore
import com.metrowin.launcher.data.StartBackgroundStore
import com.metrowin.launcher.data.TileStore
import com.metrowin.launcher.data.WeatherData
import com.metrowin.launcher.data.WeatherRepository
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.StartEntry
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.model.TileStyle
import com.metrowin.launcher.notifications.NotificationEntry
import com.metrowin.launcher.ui.theme.MetroAccents
import com.metrowin.launcher.notifications.NotificationHub
import com.metrowin.launcher.notifications.NotificationService
import com.metrowin.launcher.notifications.TilePeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val lockStore = StartBackgroundStore(application, "lock_background.jpg")
    private val quickActions = QuickActionsController(application)
    private val shortcutStore = ShortcutStore(application)
    private val weatherRepository = WeatherRepository(application)
    private val profilesController = ProfilesController(application)
    private val iconPacks = IconPackRepository(application)

    /** The widgets placed on Start. */
    val widgets = WidgetService(application)

    val icons = IconLoader(application)

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

    /** The icon packs installed on the phone. */
    private val _iconPacks = MutableStateFlow<List<IconPackInfo>>(emptyList())
    val installedIconPacks: StateFlow<List<IconPackInfo>> = _iconPacks.asStateFlow()

    /** The work profile and the private space, if the phone has them. */
    private val _profiles = MutableStateFlow<List<ProfileInfo>>(emptyList())
    val profiles: StateFlow<List<ProfileInfo>> = _profiles.asStateFlow()

    /** The apps of the phone. [items] adds the shortcuts to them. */
    private var appItems: List<AppItem> = emptyList()

    private val _shortcuts = MutableStateFlow(shortcutStore.load())
    val shortcuts: StateFlow<List<ShortcutDef>> = _shortcuts.asStateFlow()

    private val _items = MutableStateFlow<List<AppItem>>(emptyList())
    val items: StateFlow<List<AppItem>> = _items.asStateFlow()

    /** What the app list, its search and its jump list show: [items] without the hidden apps and the Start-only shortcuts. */
    val listItems: StateFlow<List<AppItem>> =
        combine(_items, _settings) { items, settings -> AppFilter.visible(items, settings) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The app list, grouped by first letter. */
    val sections: StateFlow<List<AppSection>> =
        listItems.map(AppSections::group).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** What is pinned to Start, one list of tiles and folders (in order) for each Start screen. */
    private val _pages = MutableStateFlow<List<List<StartEntry>>>(listOf(emptyList()))
    val pages: StateFlow<List<List<StartEntry>>> = _pages.asStateFlow()

    /** The apps opened last, most recent first. */
    private val _recents = MutableStateFlow(store.recentApps)
    val recents: StateFlow<List<String>> = _recents.asStateFlow()

    /** The weather of the chosen city: the last fetched, shown at once and refreshed when it gets old. */
    private val _weather = MutableStateFlow(weatherRepository.cached())
    val weather: StateFlow<WeatherData?> = _weather.asStateFlow()
    private var weatherJob: Job? = null

    private val _battery = MutableStateFlow(BatteryInfo.read(application))
    val battery: StateFlow<BatteryInfo?> = _battery.asStateFlow()
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            _battery.value = BatteryInfo.from(intent, context)
        }
    }

    private val _live = MutableStateFlow(LiveTileData())
    val live: StateFlow<LiveTileData> = _live.asStateFlow()


    /** The photo behind the Start tiles, if the user picked one. */
    private val _lockPhoto = MutableStateFlow<ImageBitmap?>(null)

    /** The photo of the Metro lock screen, or null when it has none of its own. */
    val lockPhoto: StateFlow<ImageBitmap?> = _lockPhoto.asStateFlow()

    private val _background = MutableStateFlow<ImageBitmap?>(null)
    val background: StateFlow<ImageBitmap?> = _background.asStateFlow()

    /** The accent that suits the Start photo, as an index into the accent colours; null without a photo or colour. */
    private val _autoAccent = MutableStateFlow<Int?>(null)
    val autoAccentIndex: StateFlow<Int?> = _autoAccent.asStateFlow()

    /** Notifications for the Action Center. Empty until the user turns on notification access. */
    val notifications: StateFlow<List<NotificationEntry>> = NotificationHub.entries

    /** How many notifications each app has: the counters on the tiles. */
    val badges: StateFlow<Map<String, Int>> =
        combine(NotificationHub.entries, TileFeed.tiles, _settings) { entries, published, settings ->
            val counts = NotificationHub.countsByPackage(entries)
            // The number an app published for its own tile wins over the count of its notifications.
            if (settings.thirdPartyTiles) counts + ThirdPartyTiles.counts(published) else counts
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _notificationAccess = MutableStateFlow(hasNotificationAccess())
    val notificationAccess: StateFlow<Boolean> = _notificationAccess.asStateFlow()

    /** The latest notification of each app, for the tiles that flip to show it. Empty unless the setting is on. */
    val peeks: StateFlow<Map<String, TilePeek>> =
        combine(NotificationHub.entries, TileFeed.tiles, _settings) { entries, published, settings ->
            val fromNotifications = if (settings.notificationPeek) NotificationHub.latestByPackage(entries) else emptyMap()
            // What an app published for its own tile is what it wants the tile to say.
            if (settings.thirdPartyTiles) fromNotifications + ThirdPartyTiles.peeks(published) else fromNotifications
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** The tiles apps have published, most recent first, for the settings. */
    val publishedTiles: StateFlow<List<ThirdPartyTile>> =
        TileFeed.tiles.map { it.values.sortedByDescending(ThirdPartyTile::updatedAt) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun clearPublishedTile(packageName: String) = TileFeed.clear(getApplication(), packageName)

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
        TileFeed.load(application)
        applyIconPack(_settings.value.iconPack)
        reloadApps()
        if (_settings.value.hasStartBackground) loadBackground()
        if (_settings.value.hasLockPhoto) loadLockPhoto()
        stopObservingPackages = apps.observe(::scheduleReload)
        ContextCompat.registerReceiver(
            application, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(batteryReceiver) }
        stopObservingPackages()
        quickActions.close()
    }

    /** Called every time the launcher comes back on screen. */
    fun onStart() {
        refreshProfiles()
        refreshIconPacks()
        syncIfDue(SYNC_MIN_GAP_ON_START_MS)
        if (loadedLocale != null && loadedLocale != Locale.getDefault()) reloadApps()
        refreshLive()
        refreshWeather()
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

    /** Sends the typed answer to a notification. False (and says so) when the app does not take it. */
    fun replyToNotification(entry: NotificationEntry, text: String): Boolean {
        val sent = NotificationHub.reply(getApplication(), entry, text)
        if (sent && entry.autoCancel) NotificationHub.dismiss(entry.key)
        return sent
    }

    fun dismissNotification(entry: NotificationEntry) = NotificationHub.dismiss(entry.key)

    fun clearNotifications() = NotificationHub.dismissAll()

    fun openNotificationAccessSettings() = startSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun openSystemSettings() = startSettings(Settings.ACTION_SETTINGS)

    fun openAccessibilitySettings() = startSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)

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

    private fun refreshIconPacks() {
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { runCatching { iconPacks.installed() }.getOrDefault(emptyList()) }
            _iconPacks.value = found
            // A pack that was uninstalled: back to the icons of the apps.
            val chosen = _settings.value.iconPack
            if (chosen.isNotEmpty() && found.none { it.packageName == chosen }) updateSettings { it.copy(iconPack = "") }
        }
    }

    private fun applyIconPack(packageName: String) {
        viewModelScope.launch {
            val pack = if (packageName.isEmpty()) null else withContext(Dispatchers.IO) { iconPacks.load(packageName) }
            icons.usePack(pack)
        }
    }

    /** Writes the theme (see [ThemeCodec]) to [uri], a file the user chose. */
    fun exportTheme(uri: Uri) {
        viewModelScope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!
                        .use { it.write(ThemeCodec.encode(_settings.value).toByteArray()) }
                }.isSuccess
            }
            tell(if (written) R.string.theme_exported else R.string.theme_failed)
        }
    }

    /** Applies the theme in [uri]. */
    fun importTheme(uri: Uri) {
        viewModelScope.launch {
            val themed = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readAtMost(MAX_LAYOUT_BYTES) }!!
                    ThemeCodec.decode(bytes.decodeToString(), _settings.value)
                }.getOrNull()
            }
            if (themed == null) {
                tell(R.string.theme_not_theme)
            } else {
                updateSettings { themed }
                tell(R.string.theme_imported)
            }
        }
    }

    private fun refreshProfiles() {
        viewModelScope.launch { _profiles.value = withContext(Dispatchers.IO) { runCatching { profilesController.profiles() }.getOrDefault(emptyList()) } }
    }

    /** Pauses or resumes a profile. The list of apps follows by itself, as Android reports the apps gone or back. */
    fun setProfilePaused(serial: Long, paused: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { profilesController.setPaused(serial, paused) }
            delay(PROFILE_REFRESH_MS)
            refreshProfiles()
            reloadApps()
        }
    }

    /** Fetches the weather of the chosen city, unless what is there is recent. [force] fetches in any case. */
    fun refreshWeather(force: Boolean = false) {
        val s = _settings.value
        if (s.weatherLatitude.isNaN() || s.weatherLongitude.isNaN()) {
            _weather.value = null
            weatherRepository.save(null)
            return
        }
        val current = _weather.value
        if (!force && current != null && current.fahrenheit == s.weatherFahrenheit && !OpenMeteo.isStale(current, System.currentTimeMillis())) return
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            val fetched = withContext(Dispatchers.IO) {
                weatherRepository.fetch(s.weatherLatitude, s.weatherLongitude, s.weatherFahrenheit)
            }
            // Nothing new (no connection): the weather already there stays, however old.
            if (fetched != null) {
                _weather.value = fetched
                weatherRepository.save(fetched)
            }
        }
    }

    /** The contacts whose name matches [query]. Empty without the contacts permission. */
    suspend fun searchContacts(query: String): List<ContactMatch> =
        withContext(Dispatchers.IO) { ContactSearch(getApplication()).search(query) }

    /** Null when the search could not be made (no connection), an empty list when nothing matches. */
    suspend fun searchPlaces(query: String): List<PlaceMatch>? =
        withContext(Dispatchers.IO) { weatherRepository.search(query, Locale.getDefault().language) }

    fun choosePlace(place: PlaceMatch) {
        updateSettings { it.copy(weatherCity = place.displayName, weatherLatitude = place.latitude, weatherLongitude = place.longitude) }
    }

    /** Takes the last known position of the phone as the weather's place. Needs the location permission. */
    @android.annotation.SuppressLint("MissingPermission")
    fun useMyLocation() {
        val app = getApplication<Application>()
        val manager = app.getSystemService(LocationManager::class.java)
        val here = runCatching {
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
                .maxByOrNull { it.time }
        }.getOrNull()
        if (here == null) {
            tell(R.string.weather_no_location)
            return
        }
        updateSettings {
            it.copy(
                weatherCity = app.getString(R.string.weather_my_location),
                weatherLatitude = here.latitude,
                weatherLongitude = here.longitude,
            )
        }
    }

    fun updateSettings(change: (LauncherSettings) -> LauncherSettings) {
        val previous = _settings.value
        val updated = change(previous)
        if (updated != previous) {
            _settings.value = updated
            settingsStore.save(updated)
            val movedWeather = updated.weatherLatitude != previous.weatherLatitude && !(updated.weatherLatitude.isNaN() && previous.weatherLatitude.isNaN()) ||
                updated.weatherLongitude != previous.weatherLongitude && !(updated.weatherLongitude.isNaN() && previous.weatherLongitude.isNaN()) ||
                updated.weatherFahrenheit != previous.weatherFahrenheit
            if (movedWeather) {
                // Another city or another unit: the weather on show is not the one asked for.
                _weather.value = null
                refreshWeather(force = true)
            }
            if (updated.iconPack != previous.iconPack) applyIconPack(updated.iconPack)
            if (updated.startPages != previous.startPages && tilesReady) {
                updatePages { StartPages.resize(it, updated.startPages) }
            }
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
            _autoAccent.value = null
            updateSettings { it.copy(backgroundRevision = 0) }
        }
    }

    fun setLockPhoto(uri: Uri) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { lockStore.save(uri) }
            if (saved) {
                updateSettings { it.copy(lockPhotoRevision = it.lockPhotoRevision + 1) }
                loadLockPhoto()
            } else {
                tell(R.string.photo_failed)
            }
        }
    }

    fun clearLockPhoto() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { lockStore.clear() }
            _lockPhoto.value = null
            updateSettings { it.copy(lockPhotoRevision = 0) }
        }
    }

    private fun loadLockPhoto() {
        viewModelScope.launch { _lockPhoto.value = withContext(Dispatchers.IO) { lockStore.load() }?.asImageBitmap() }
    }

    /** Sets the lock screen photo (or the Start photo) as the wallpaper of the lock screen of Android. */
    fun applyLockPhotoToSystem() {
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val photo = lockStore.load() ?: backgroundStore.load() ?: return@withContext R.string.lock_no_photo
                val done = runCatching {
                    WallpaperManager.getInstance(getApplication()).setBitmap(photo, null, true, WallpaperManager.FLAG_LOCK)
                }.isSuccess
                if (done) R.string.lock_applied else R.string.lock_apply_failed
            }
            tell(outcome)
        }
    }

    fun openDreamSettings() = startSettings(Settings.ACTION_DREAM_SETTINGS)

    private fun loadBackground() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { backgroundStore.load() }
            _background.value = loaded?.asImageBitmap()
            _autoAccent.value = loaded?.let { withContext(Dispatchers.Default) { accentFor(it) } }
        }
    }

    /** The accent closest to the colour the picture is about, from a small copy of it. */
    private fun accentFor(bitmap: Bitmap): Int? {
        val small = bitmap.scale(SAMPLE_SIDE, SAMPLE_SIDE)
        val pixels = IntArray(SAMPLE_SIDE * SAMPLE_SIDE).also { small.getPixels(it, 0, SAMPLE_SIDE, 0, 0, SAMPLE_SIDE, SAMPLE_SIDE) }
        if (small !== bitmap) small.recycle()
        val dominant = DominantColor.of(pixels) ?: return null
        return DominantColor.nearestIndex(dominant, MetroAccents.map { it.toArgb() })
    }

    fun isPinned(id: String) = StartPages.isPinned(_pages.value, id)

    /** Pins [item] at the end of Start screen [page]. */
    fun pin(item: AppItem, page: Int = 0) = updatePages { StartPages.pin(it, item.id, page) }

    fun unpin(id: String) {
        // A shortcut has no other life than its tile: taking it off Start deletes it.
        if (_shortcuts.value.any { it.id == id }) removeShortcut(id) else updatePages { StartPages.unpin(it, id) }
    }

    fun resize(id: String) = updatePages { StartPages.update(it, id) { page -> StartEntries.resize(page, id) } }

    fun setSize(id: String, size: TileSize) =
        updatePages { StartPages.update(it, id) { page -> StartEntries.setSize(page, id, size) } }

    fun setStyle(id: String, style: TileStyle) =
        updatePages { StartPages.update(it, id) { page -> StartEntries.setStyle(page, id, style) } }

    fun moveToPage(id: String, page: Int) = updatePages { StartPages.moveToPage(it, id, page) }

    fun moveTo(draggedId: String, targetId: String) =
        updatePages { StartPages.update(it, targetId) { page -> StartEntries.moveTo(page, draggedId, targetId) } }

    fun merge(draggedId: String, ontoId: String) =
        updatePages { StartPages.update(it, ontoId) { page -> StartEntries.merge(page, draggedId, ontoId) } }

    fun releaseFolder(folderId: String) =
        updatePages { StartPages.update(it, folderId) { page -> StartEntries.release(page, folderId) } }

    fun removeFromFolder(folderId: String, childId: String) =
        updatePages { StartPages.update(it, folderId) { page -> StartEntries.removeFromFolder(page, folderId, childId) } }

    fun renameFolder(folderId: String, name: String) =
        updatePages { StartPages.update(it, folderId) { page -> StartEntries.rename(page, folderId, name) } }

    /** Returns false (and tells the user) when the app could not be opened. */
    fun launch(item: AppItem, sourceBounds: Rect? = null, growFrom: Rect? = null, anchor: android.view.View? = null): Boolean {
        val opened = apps.launch(item, sourceBounds, growFrom, anchor)
        if (opened) {
            // A contact, a setting or a web search found by the search is not an app of the list.
            if (!item.id.startsWith("search:")) {
                _recents.value = RecentApps.record(_recents.value, item.id)
                store.recentApps = _recents.value
            }
        } else {
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
        appItems = list
        rebuildItems()
        syncTiles(_items.value)
    }

    /** Apps first, then the shortcuts: the list every screen reads. */
    private fun rebuildItems() {
        _items.value = appItems + _shortcuts.value.map { it.toItem(shortcutStore.photoPath(it.photoFile)) }
    }

    /** Adds a shortcut and pins its tile at the end of Start screen [page], in [size] if one is given. */
    fun addShortcut(shortcut: ShortcutDef, page: Int = 0, size: TileSize? = null) {
        _shortcuts.value = _shortcuts.value.filter { it.id != shortcut.id } + shortcut
        shortcutStore.save(_shortcuts.value)
        rebuildItems()
        updatePages { pages ->
            val pinned = StartPages.pin(pages, shortcut.id, page)
            if (size == null) pinned else StartPages.update(pinned, shortcut.id) { StartEntries.setSize(it, shortcut.id, size) }
        }
    }

    /** Puts a widget that Android has bound to [appWidgetId] on Start. */
    fun addWidget(appWidgetId: Int, provider: WidgetProvider, page: Int) {
        val id = "shortcut:widget:$appWidgetId"
        addShortcut(
            ShortcutDef(
                id = id, kind = ShortcutKind.Widget, label = provider.label.ifBlank { provider.appLabel },
                widgetProvider = provider.component.flattenToString(), widgetId = appWidgetId,
            ),
            page, WidgetSizing.tileSizeFor(provider.minWidthDp, provider.minHeightDp),
        )
    }

    fun removeShortcut(id: String) {
        val removed = _shortcuts.value.firstOrNull { it.id == id } ?: return
        _shortcuts.value = _shortcuts.value.filter { it.id != id }
        shortcutStore.save(_shortcuts.value)
        shortcutStore.deletePhoto(removed.photoFile)
        if (removed.kind == ShortcutKind.Widget) widgets.delete(removed.widgetId)
        rebuildItems()
        updatePages { StartPages.unpin(it, id) }
    }

    /** Pins the contact the picker returned, with its photo, at the end of Start screen [page]. */
    fun pinContact(uri: Uri, page: Int) {
        viewModelScope.launch {
            val picked = withContext(Dispatchers.IO) { ContactReader(getApplication()).read(uri) }
            if (picked == null) {
                tell(R.string.contact_failed)
                return@launch
            }
            val id = ShortcutDef.newId(ShortcutKind.Contact, picked.lookupUri)
            val photo = picked.photoJpeg?.let { shortcutStore.savePhoto(id, it) }
            addShortcut(ShortcutDef(id, ShortcutKind.Contact, picked.name, picked.lookupUri, photoFile = photo), page)
        }
    }

    /** Pins a web page. False when [address] is not a web address. */
    fun pinSite(title: String, address: String, page: Int): Boolean {
        val url = SiteInput.normalize(address) ?: return false
        val label = title.trim().ifEmpty { SiteInput.suggestedTitle(url) }
        addShortcut(ShortcutDef(ShortcutDef.newId(ShortcutKind.Site, url), ShortcutKind.Site, label, url), page)
        return true
    }

    /** What the layout is now, as a backup file would hold it. */
    fun currentBackup() = LayoutBackup(_pages.value, _shortcuts.value, _settings.value)

    /** Puts a backup in place of the current layout. Tiles of apps this phone does not have are left out. */
    fun restoreBackup(backup: LayoutBackup) {
        _shortcuts.value = backup.shortcuts
        shortcutStore.save(backup.shortcuts)
        rebuildItems()
        updateSettings { backup.settings }
        val known = _items.value.mapTo(HashSet()) { it.id }
        val pages = StartPages.prune(StartPages.resize(backup.pages, backup.settings.startPages), known)
        _pages.value = pages
        store.savePages(pages)
    }

    /** The name of the backup folder, for the settings; null while none is chosen or it cannot be read. */
    val backupFolderName: StateFlow<String?> = _settings
        .map { it.backupFolder }
        .distinctUntilChanged()
        .map { uri -> if (uri.isBlank()) null else withContext(Dispatchers.IO) { SafFolder(getApplication(), uri.toUri()).displayName() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Keeps [uri] (a folder the user picked) for the backups: the permission is kept past a restart of the phone. */
    fun setBackupFolder(uri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        // Another folder is another sync: what was known about the old one does not apply.
        updateSettings { it.copy(backupFolder = uri.toString(), lastBackupAt = 0L, syncLastHash = "", syncLastStamp = 0L, syncLastAt = 0L) }
    }

    fun clearBackupFolder() {
        val old = _settings.value.backupFolder
        if (old.isNotBlank()) {
            runCatching {
                getApplication<Application>().contentResolver.releasePersistableUriPermission(
                    old.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        updateSettings {
            it.copy(backupFolder = "", backupEveryDays = 0, lastBackupAt = 0L, syncEnabled = false, syncLastHash = "", syncLastStamp = 0L, syncLastAt = 0L)
        }
    }

    /** Saves the layout in the backup folder. [announce] says so on screen; an automatic backup does not. */
    fun backupNow(announce: Boolean = true) {
        val folder = _settings.value.backupFolder
        if (folder.isBlank()) return
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                val text = LayoutBackupCodec.encode(currentBackup(), System.currentTimeMillis())
                SafFolder(getApplication(), folder.toUri()).write(SafFolder.BACKUP_FILE, "application/json", text.toByteArray())
            }
            if (saved) updateSettings { it.copy(lastBackupAt = System.currentTimeMillis()) }
            if (announce) tell(if (saved) R.string.backup_saved else R.string.backup_failed)
        }
    }

    /** Called when the launcher goes to the background: makes the automatic backup if it is time. */
    fun backupIfDue() {
        val s = _settings.value
        if (BackupSchedule.isDue(s.backupEveryDays, s.backupFolder, s.lastBackupAt, System.currentTimeMillis())) backupNow(announce = false)
        syncIfDue(SYNC_MIN_GAP_ON_LEAVE_MS)
    }

    private var syncing = false

    /** An automatic sync, unless one ran a moment ago. */
    fun syncIfDue(minGapMs: Long) {
        val s = _settings.value
        if (!s.syncEnabled || s.syncPassphrase.isEmpty() || s.backupFolder.isBlank()) return
        if (System.currentTimeMillis() - s.syncLastAt < minGapMs) return
        syncNow(announce = false)
    }

    /**
     * Brings this phone and the sync file in the backup folder in line (see [SyncEngine]). [announce] says how it
     * went on screen; an automatic sync only speaks when something needs the user.
     */
    fun syncNow(announce: Boolean = true) {
        val s = _settings.value
        if (!s.syncEnabled || s.backupFolder.isBlank() || s.syncPassphrase.isEmpty()) {
            if (announce) tell(R.string.sync_not_ready)
            return
        }
        if (syncing) return
        syncing = true
        viewModelScope.launch {
            try {
                if (s.deviceId.isEmpty()) updateSettings { it.copy(deviceId = java.util.UUID.randomUUID().toString()) }
                val folder = SafFolder(getApplication(), s.backupFolder.toUri())
                val layout = LayoutBackupCodec.encode(currentBackup(), 0L)
                val local = SyncLocal(layout, s.syncPassphrase, _settings.value.deviceId, s.syncLastHash, s.syncLastStamp)
                val outcome = withContext(Dispatchers.IO) { SyncEngine(SafSyncStorage(folder)).sync(local) }
                applySyncOutcome(outcome, folder, announce)
            } finally {
                syncing = false
            }
        }
    }

    private suspend fun applySyncOutcome(outcome: SyncOutcome, folder: SafFolder, announce: Boolean) {
        val now = System.currentTimeMillis()
        when (outcome) {
            SyncOutcome.Nothing -> {
                updateSettings { it.copy(syncLastAt = now) }
                if (announce) tell(R.string.sync_up_to_date)
            }
            is SyncOutcome.Uploaded -> {
                updateSettings { it.copy(syncLastHash = outcome.hash, syncLastStamp = outcome.stamp, syncLastAt = now) }
                if (outcome.keptTheOtherAs != null) {
                    // Something the user should know: the other phone's layout did not win.
                    Toast.makeText(getApplication(), getApplication<Application>().getString(R.string.sync_conflict, outcome.keptTheOtherAs), Toast.LENGTH_LONG).show()
                } else if (announce) {
                    tell(R.string.sync_uploaded)
                }
            }
            is SyncOutcome.Applied -> {
                val backup = LayoutBackupCodec.decode(outcome.layoutJson, _settings.value)
                if (backup == null) {
                    tell(R.string.sync_not_file)
                    return
                }
                if (_settings.value.syncLastHash.isEmpty()) {
                    // Joining a sync that already has data: what this phone had is kept as the backup file.
                    withContext(Dispatchers.IO) {
                        folder.write(SafFolder.BACKUP_FILE, "application/json", LayoutBackupCodec.encode(currentBackup(), now).toByteArray())
                    }
                }
                restoreBackup(backup)
                val hash = SyncEngine.hash(LayoutBackupCodec.encode(currentBackup(), 0L))
                updateSettings { it.copy(syncLastHash = hash, syncLastStamp = outcome.stamp, syncLastAt = now) }
                if (announce) tell(R.string.sync_applied)
            }
            SyncOutcome.WrongPassphrase -> tell(R.string.sync_wrong_passphrase)
            SyncOutcome.NotASyncFile -> tell(R.string.sync_not_file)
            SyncOutcome.Failed -> if (announce) tell(R.string.sync_failed)
        }
    }

    /** Puts back the layout saved in the backup folder. */
    fun restoreFromBackupFolder() {
        val folder = _settings.value.backupFolder
        if (folder.isBlank()) return
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) { SafFolder(getApplication(), folder.toUri()).read(SafFolder.BACKUP_FILE) }
            val backup = bytes?.let { LayoutBackupCodec.decode(it.decodeToString(), _settings.value) }
            when {
                bytes == null -> tell(R.string.backup_no_file)
                backup == null -> tell(R.string.backup_not_layout)
                else -> {
                    restoreBackup(backup)
                    tell(R.string.backup_imported)
                }
            }
        }
    }

    /** Writes the layout to [uri] (a file the user chose). */
    fun exportLayout(uri: Uri) {
        viewModelScope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    val text = LayoutBackupCodec.encode(currentBackup(), System.currentTimeMillis())
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                }.isSuccess
            }
            tell(if (written) R.string.backup_exported else R.string.backup_failed)
        }
    }

    /** Reads a layout file and puts it in place. */
    fun importLayout(uri: Uri) {
        viewModelScope.launch {
            val backup = withContext(Dispatchers.IO) {
                runCatching {
                    val text = getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readAtMost(MAX_LAYOUT_BYTES) }!!
                        .decodeToString()
                    LayoutBackupCodec.decode(text, _settings.value)
                }.getOrNull()
            }
            if (backup == null) {
                tell(R.string.backup_not_layout)
            } else {
                restoreBackup(backup)
                tell(R.string.backup_imported)
            }
        }
    }

    private fun tell(message: Int) = Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()

    private fun syncTiles(items: List<AppItem>) {
        if (items.isEmpty()) return // never prune the Start screen on a failed or empty load
        val known = items.mapTo(HashSet()) { it.id }
        val base = if (tilesReady) _pages.value else store.loadPages() ?: listOf(DefaultStart.build(items))
        val kept = StartPages.prune(StartPages.resize(base, _settings.value.startPages), known)
        tilesReady = true
        if (kept != _pages.value) {
            _pages.value = kept
            store.savePages(kept)
        }
    }

    private inline fun updatePages(change: (List<List<StartEntry>>) -> List<List<StartEntry>>) {
        val updated = change(_pages.value)
        if (updated != _pages.value) {
            _pages.value = updated
            store.savePages(updated)
        }
    }

    private companion object {
        const val RELOAD_DEBOUNCE_MS = 400L
        const val SAMPLE_SIDE = 48
        const val PROFILE_REFRESH_MS = 800L
        const val SYNC_MIN_GAP_ON_START_MS = 5 * 60_000L
        const val SYNC_MIN_GAP_ON_LEAVE_MS = 60_000L
        const val MAX_LAYOUT_BYTES = 2 * 1024 * 1024
    }
}
