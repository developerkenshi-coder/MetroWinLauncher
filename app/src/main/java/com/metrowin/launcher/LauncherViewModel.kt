package com.metrowin.launcher

import com.metrowin.launcher.data.Throttle
import com.metrowin.launcher.data.PowerPolicy
import com.metrowin.launcher.data.LiveSources
import com.metrowin.launcher.data.LivePermissions
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
import android.os.PowerManager
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
import com.metrowin.launcher.data.AppLabels
import com.metrowin.launcher.data.AppShortcut
import com.metrowin.launcher.data.AppShortcuts
import com.metrowin.launcher.data.AppUsage
import com.metrowin.launcher.gestures.RecentsTileService
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
import com.metrowin.launcher.data.PhoneCalls
import com.metrowin.launcher.data.QuickAnswer
import com.metrowin.launcher.data.QuickAnswerActions
import com.metrowin.launcher.data.RecentSearches
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
import com.metrowin.launcher.data.SystemSettingDef
import com.metrowin.launcher.data.WidgetProvider
import com.metrowin.launcher.data.WidgetService
import com.metrowin.launcher.data.WidgetSizing
import com.metrowin.launcher.data.ShortcutStore
import com.metrowin.launcher.data.StartBackgroundStore
import com.metrowin.launcher.data.TileStore
import com.metrowin.launcher.data.WeatherData
import com.metrowin.launcher.data.WeatherRepository
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.LiveKind
import com.metrowin.launcher.model.StartEntry
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.model.TileStyle
import com.metrowin.launcher.notifications.NotificationEntry
import com.metrowin.launcher.ui.theme.MetroAccents
import com.metrowin.launcher.notifications.NotificationHub
import com.metrowin.launcher.notifications.NotificationService
import com.metrowin.launcher.notifications.LiveCard
import com.metrowin.launcher.notifications.LiveCardOptions
import com.metrowin.launcher.notifications.LiveCards
import com.metrowin.launcher.notifications.LiveReportLine
import com.metrowin.launcher.notifications.LiveTileInfo
import com.metrowin.launcher.notifications.NowPlaying
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

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
    private val appShortcuts = AppShortcuts(application)

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

    /** How many times each app was opened from the launcher, for the "most used" group of the app list. */
    private val _usage = MutableStateFlow(store.appUsage)
    val usage: StateFlow<Map<String, Int>> = _usage.asStateFlow()

    /** What was searched for last, most recent first (see [RecentSearches]); empty while remembering is off. */
    private val _searches = MutableStateFlow(store.recentSearches)
    val recentSearches: StateFlow<List<String>> = _searches.asStateFlow()

    fun rememberSearch(query: String) {
        if (!_settings.value.rememberSearches) return
        val updated = RecentSearches.record(_searches.value, query)
        if (updated == _searches.value) return
        _searches.value = updated
        store.recentSearches = updated
    }

    fun clearSearches() {
        _searches.value = emptyList()
        store.recentSearches = emptyList()
    }

    /** Does what touching [answer] does: opens the dialer, a page, a message, sets the timer, or copies the result. */
    fun perform(answer: QuickAnswer) {
        val app = getApplication<Application>()
        val copy = QuickAnswerActions.copyText(answer)
        if (copy != null) {
            app.getSystemService(android.content.ClipboardManager::class.java)
                .setPrimaryClip(android.content.ClipData.newPlainText(app.getString(R.string.answer_clip_label), copy))
            tell(R.string.answer_copied)
            return
        }
        val intent = QuickAnswerActions.intent(answer) ?: return
        val opened = runCatching { app.startActivity(intent) }.isSuccess
        when {
            !opened && (answer is QuickAnswer.Timer || answer is QuickAnswer.Alarm) -> tell(R.string.answer_no_clock)
            !opened -> tell(R.string.launch_failed)
            answer is QuickAnswer.Timer || answer is QuickAnswer.Alarm -> tell(R.string.answer_clock_set)
        }
    }

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

    private val powerManager = application.getSystemService(PowerManager::class.java)
    private val _androidBatterySaver = MutableStateFlow(powerManager?.isPowerSaveMode == true)
    private val powerSaveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            _androidBatterySaver.value = powerManager?.isPowerSaveMode == true
        }
    }

    /**
     * Whether the launcher is sparing the battery now (see [PowerPolicy]): the screens stop what moves by itself, the
     * 120 Hz request is dropped and the data is refreshed less often.
     */
    val powerSaving: StateFlow<Boolean> =
        combine(_settings.map { it.batterySaving }.distinctUntilChanged(), _androidBatterySaver, _battery) { mode, saver, battery ->
            PowerPolicy.saving(mode, saver, battery)
        }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val throttle = Throttle()
    private var loadedLive: Set<LiveKind>? = null
    private var loadedGrants = -1
    private var listening = false

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

    /** What the user lets the live tiles of other apps show (see [LiveCards]). */
    private val liveOptions = _settings
        .map {
            LiveCardOptions(
                messages = it.liveMessages, pictures = it.livePictures, playing = it.liveNowPlaying, progress = it.liveProgress,
                hideText = it.liveHideText, offApps = it.liveOffApps, ownPackage = application.packageName,
            )
        }
        .distinctUntilChanged()

    /**
     * What the tiles of other apps show, read from their notifications: the faces and the number of unread messages of each
     * app (see [LiveCards]). Empty unless live content is on in the settings.
     */
    val liveInfo: StateFlow<Map<String, LiveTileInfo>> =
        combine(NotificationHub.apps, liveOptions, _settings.map { it.notificationPeek }.distinctUntilChanged()) { apps, options, on ->
            if (on) LiveCards.build(apps, options) else emptyMap()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** What the notification service reads from each app now, for the page that shows it (see [LiveCards.report]). */
    val liveReport: StateFlow<List<LiveReportLine>> =
        combine(NotificationHub.apps, _settings.map { it.notificationPeek }.distinctUntilChanged()) { apps, on ->
            if (on) LiveCards.report(apps, application.packageName) else emptyList()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** How many notifications each app has: the counters on the tiles. */
    val badges: StateFlow<Map<String, Int>> =
        combine(NotificationHub.entries, TileFeed.tiles, _settings, liveInfo) { entries, published, settings, info ->
            val counts = NotificationHub.countsByPackage(entries)
            // Messages are counted one by one: a chat with five waiting is five, not one.
            val counted = counts.mapValues { (pkg, n) -> maxOf(n, info[pkg]?.unread ?: 0) } +
                info.filter { (pkg, i) -> pkg !in counts && i.unread > 0 }.mapValues { it.value.unread }
            // The number an app published for its own tile wins over the count of its notifications.
            if (settings.thirdPartyTiles) counted + ThirdPartyTiles.counts(published) else counted
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _notificationAccess = MutableStateFlow(hasNotificationAccess())
    val notificationAccess: StateFlow<Boolean> = _notificationAccess.asStateFlow()

    /** The faces of the tiles that flip to show what is going on in their app. Empty unless the setting is on. */
    val peeks: StateFlow<Map<String, List<LiveCard>>> =
        combine(liveInfo, TileFeed.tiles, _settings) { info, published, settings ->
            val fromNotifications = info.mapValues { it.value.cards }.filterValues { it.isNotEmpty() }
            // What an app published for its own tile is what it wants the tile to say.
            if (settings.thirdPartyTiles) {
                fromNotifications + ThirdPartyTiles.peeks(published).mapValues { listOf(LiveCard.Digest(it.value.title, it.value.text)) }
            } else {
                fromNotifications
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** The music playing in any app, for the Music tile; null when nothing plays or the setting is off. */
    val nowPlaying: StateFlow<NowPlaying?> =
        combine(NotificationHub.apps, _settings) { apps, settings ->
            if (settings.notificationPeek && settings.liveNowPlaying) {
                LiveCards.nowPlaying(apps.filter { it.packageName !in settings.liveOffApps })
            } else {
                null
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The tiles apps have published, most recent first, for the settings. */
    val publishedTiles: StateFlow<List<ThirdPartyTile>> =
        TileFeed.tiles.map { it.values.sortedByDescending(ThirdPartyTile::updatedAt) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun clearPublishedTile(packageName: String) = TileFeed.clear(getApplication(), packageName)

    val torchOn: StateFlow<Boolean> = quickActions.torchOn

    private val _tutorialDone = MutableStateFlow(store.tutorialDone)
    val tutorialDone: StateFlow<Boolean> = _tutorialDone.asStateFlow()

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
        // The notification service reads the notifications in detail only while the live tiles use them.
        viewModelScope.launch {
            _settings.map { it.notificationPeek to it.livePictures }.distinctUntilChanged()
                .collect { (on, pictures) -> NotificationHub.useForLiveTiles(on, pictures) }
        }
        // A new name for an app changes the list: the order of the letters too.
        viewModelScope.launch {
            _settings.map { it.appLabels }.distinctUntilChanged().drop(1).collect { rebuildItems() }
        }
        // What Start shows from the phone is read again when a tile of that kind is pinned.
        viewModelScope.launch {
            combine(_pages, _items) { pages, items -> LiveSources.wanted(pages, items) }.distinctUntilChanged().collect { if (listening) refreshLive() }
        }
    }

    override fun onCleared() {
        stopListening()
        stopObservingPackages()
        quickActions.close()
    }

    /**
     * The receivers wake the process, so they are on only while the launcher is on screen: with another app in front
     * or the screen off the launcher is asleep, and what changed meanwhile is read once when it comes back.
     */
    private fun startListening() {
        if (listening) return
        listening = true
        val app = getApplication<Application>()
        ContextCompat.registerReceiver(app, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(app, powerSaveReceiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        quickActions.start()
        // What changed while the receivers were off.
        BatteryInfo.read(app)?.let { _battery.value = it }
        _androidBatterySaver.value = powerManager?.isPowerSaveMode == true
    }

    private fun stopListening() {
        if (!listening) return
        listening = false
        val app = getApplication<Application>()
        runCatching { app.unregisterReceiver(batteryReceiver) }
        runCatching { app.unregisterReceiver(powerSaveReceiver) }
        quickActions.stop()
    }

    /** Called every time the launcher goes off screen: behind another app, or the screen turned off. */
    fun onStop() = stopListening()

    /** Called every time the launcher comes back on screen. */
    fun onStart() {
        startListening()
        val saving = powerSaving.value
        // The profiles are a cheap question and show whether work apps are paused, so they are always asked; the icon packs
        // (a search through every installed app) only now and then.
        refreshProfiles()
        if (throttle.due("iconpacks", PowerPolicy.packagesMaxAgeMs(saving))) refreshIconPacks()
        syncIfDue(PowerPolicy.syncGapMs(SYNC_MIN_GAP_ON_START_MS, saving))
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

    /** Asks Android to add the Recent apps tile to the Quick Settings; the user is asked to confirm (Android 13 and later). */
    fun requestRecentsTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val app = getApplication<Application>()
        runCatching {
            app.getSystemService(android.app.StatusBarManager::class.java)?.requestAddTileService(
                ComponentName(app, RecentsTileService::class.java),
                app.getString(R.string.qa_recents),
                android.graphics.drawable.Icon.createWithResource(app, R.drawable.ic_tile_recents),
                app.mainExecutor,
            ) { }
        }
    }

    private fun startSettings(action: String) {
        runCatching { getApplication<Application>().startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** [fromBackground] is true when the launcher was stopped, behind another app, when the button was pressed. */
    fun onHomePressed(fromBackground: Boolean) {
        _homePresses.value = HomePress(_homePresses.value.count + 1, fromBackground)
    }

    /**
     * Reads what the live tiles on Start show, unless that was read lately and Start asks for nothing new. Only the
     * data of the tiles that are pinned is read, and not at all while none of them is. [force] reads in any case.
     */
    fun refreshLive(force: Boolean = false) {
        val wanted = LiveSources.wanted(_pages.value, _items.value)
        // A permission given or taken away since the last read changes what there is to show at once.
        val grants = LivePermissions.all.count { LivePermissions.isGranted(getApplication(), it) }
        val due = throttle.due("live", PowerPolicy.liveMaxAgeMs(powerSaving.value))
        val changed = force || wanted != loadedLive || grants != loadedGrants
        if (!changed && !due) return
        if (!changed && wanted.isEmpty()) return
        loadedLive = wanted
        loadedGrants = grants
        viewModelScope.launch {
            _live.value = withContext(Dispatchers.IO) { liveTiles.load(wanted) }
        }
    }

    fun onTutorialFinished() {
        store.tutorialDone = true
        _tutorialDone.value = true
        // The tutorial may have granted permissions the tiles were waiting for.
        refreshLive(force = true)
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
        val maxAge = PowerPolicy.weatherMaxAgeMs(powerSaving.value)
        if (!force && current != null && current.fahrenheit == s.weatherFahrenheit && !OpenMeteo.isStale(current, System.currentTimeMillis(), maxAge)) return
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
    /** The phone number of a contact found by [searchContacts], or null when it has none or the contacts are not readable. */
    suspend fun phoneNumberOf(lookupUri: String): String? =
        withContext(Dispatchers.IO) { PhoneCalls.numberOfContact(getApplication(), lookupUri) }

    /** Calls [number] at once ([direct], the phone permission is granted) or opens the dialer with it written in. */
    fun dial(number: String, direct: Boolean) {
        val opened = runCatching { getApplication<Application>().startActivity(PhoneCalls.intent(number, direct)) }.isSuccess
        if (!opened) tell(R.string.launch_failed)
    }

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
        if (_shortcuts.value.any { it.id == id }) removeShortcut(id) else updatePages(R.string.undo_removed) { StartPages.unpin(it, id) }
    }

    fun resize(id: String) = updatePages(R.string.undo_changed) { StartPages.update(it, id) { page -> StartEntries.resize(page, id) } }

    fun setSize(id: String, size: TileSize) =
        updatePages(R.string.undo_changed) { StartPages.update(it, id) { page -> StartEntries.setSize(page, id, size) } }

    fun setStyle(id: String, style: TileStyle) =
        updatePages(R.string.undo_changed) { StartPages.update(it, id) { page -> StartEntries.setStyle(page, id, style) } }

    /** Gives a tile or folder one of the 20 colors (ARGB), or 0 to take the color of the theme back. */
    fun setColor(id: String, color: Int) =
        updatePages(R.string.undo_changed) { StartPages.update(it, id) { page -> StartEntries.setColor(page, id, color) } }

    fun moveToPage(id: String, page: Int) = updatePages(R.string.undo_moved) { StartPages.moveToPage(it, id, page) }

    fun moveTo(draggedId: String, targetId: String) =
        updatePages(R.string.undo_moved) { StartPages.update(it, targetId) { page -> StartEntries.moveTo(page, draggedId, targetId) } }

    /** A small tile dropped in a free cell: it goes right after [afterId] (see [StartEntries.moveAfter]). */
    fun moveAfter(draggedId: String, afterId: String) =
        updatePages(R.string.undo_moved) { StartPages.update(it, afterId) { page -> StartEntries.moveAfter(page, draggedId, afterId) } }

    /**
     * Puts the tiles [ids] together in one folder (see [StartEntries.group]) and gives back the id of the folder that
     * was made, or null when nothing was.
     */
    fun createFolder(ids: Set<String>): String? {
        val id = UUID.randomUUID().toString()
        var made = false
        updatePages(R.string.undo_folder) { pages ->
            val anchor = ids.firstOrNull { StartPages.pageOf(pages, it) >= 0 } ?: return@updatePages pages
            StartPages.update(pages, anchor) { page -> StartEntries.group(page, ids) { id }.also { made = it.any { entry -> entry.id == id } } }
        }
        return id.takeIf { made }
    }

    /** Puts an app in a folder, pinning it first if it is not on Start. */
    fun addToFolder(appId: String, folderId: String) = updatePages(R.string.undo_folder) { StartPages.addToFolder(it, appId, folderId) }

    fun merge(draggedId: String, ontoId: String) =
        updatePages(R.string.undo_folder) { StartPages.update(it, ontoId) { page -> StartEntries.merge(page, draggedId, ontoId) } }

    fun releaseFolder(folderId: String) =
        updatePages(R.string.undo_folder) { StartPages.update(it, folderId) { page -> StartEntries.release(page, folderId) } }

    fun removeFromFolder(folderId: String, childId: String) =
        updatePages(R.string.undo_folder) { StartPages.update(it, folderId) { page -> StartEntries.removeFromFolder(page, folderId, childId) } }

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
                _usage.value = AppUsage.record(_usage.value, item.id)
                store.appUsage = _usage.value
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
        _items.value = AppLabels.apply(appItems, _settings.value.appLabels) +
            _shortcuts.value.map { it.toItem(shortcutStore.photoPath(it.photoFile)) }
    }

    /** The shortcuts [item] offers on a long press, up to [AppShortcuts.SHOWN]; none while another launcher is the home app. */
    fun shortcutsOf(item: AppItem): List<AppShortcut> = appShortcuts.of(item)

    fun startShortcut(shortcut: AppShortcut) {
        if (!appShortcuts.start(shortcut)) tell(R.string.launch_failed)
    }

    /** The "app info" screen of Android for [item], where the permissions, the storage and the notifications are. */
    fun openAppInfo(item: AppItem) {
        val pkg = item.packageName ?: return
        val opened = runCatching {
            getApplication<Application>().startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        if (!opened) tell(R.string.launch_failed)
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
        // A widget gives its place back to Android at once, so it cannot be put back; the others can.
        if (removed.kind == ShortcutKind.Widget) dismissUndo() else rememberForUndo(R.string.undo_removed)
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

    /** Pins a speed dial: the number the picker returned (a row of the phone table), with the photo of its contact. */
    fun pinSpeedDial(uri: Uri, page: Int) {
        viewModelScope.launch {
            val picked = withContext(Dispatchers.IO) { ContactReader(getApplication()).readNumber(uri) }
            if (picked == null) {
                tell(R.string.speed_dial_failed)
                return@launch
            }
            val address = PhoneCalls.address(picked.number)
            val id = ShortcutDef.newId(ShortcutKind.Call, picked.number)
            val photo = picked.photoJpeg?.let { shortcutStore.savePhoto(id, it) }
            addShortcut(ShortcutDef(id, ShortcutKind.Call, picked.name, address, photoFile = photo), page)
        }
    }

    /** Pins a screen of the Android settings. */
    fun pinSetting(def: SystemSettingDef, page: Int) {
        val label = getApplication<Application>().getString(def.label)
        addShortcut(ShortcutDef(ShortcutDef.newId(ShortcutKind.Setting, def.id), ShortcutKind.Setting, label, def.action), page)
    }

    /** Pins an empty note and gives back its id, so the editor can open on it. */
    fun addNote(page: Int): String {
        val id = ShortcutDef.newId(ShortcutKind.Note, "note")
        addShortcut(
            ShortcutDef(id, ShortcutKind.Note, getApplication<Application>().getString(R.string.note_default_label)),
            page, TileSize.Medium,
        )
        return id
    }

    fun saveNote(id: String, text: String) = updateShortcut(id) { it.copy(text = text.trim()) }

    /** Pins a countdown to [epochDay] and gives back its id. */
    fun addCountdown(title: String, epochDay: Long, page: Int): String {
        val id = ShortcutDef.newId(ShortcutKind.Countdown, title)
        val label = title.trim().ifEmpty { getApplication<Application>().getString(R.string.countdown_default_label) }
        addShortcut(ShortcutDef(id, ShortcutKind.Countdown, label, epochDay = epochDay), page, TileSize.Medium)
        return id
    }

    fun saveCountdown(id: String, title: String, epochDay: Long) = updateShortcut(id) {
        it.copy(label = title.trim().ifEmpty { getApplication<Application>().getString(R.string.countdown_default_label) }, epochDay = epochDay)
    }

    private fun updateShortcut(id: String, change: (ShortcutDef) -> ShortcutDef) {
        if (_shortcuts.value.none { it.id == id }) return
        _shortcuts.value = _shortcuts.value.map { if (it.id == id) change(it) else it }
        shortcutStore.save(_shortcuts.value)
        rebuildItems()
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
                val outcome = withContext(Dispatchers.IO) {
                    val layout = LayoutBackupCodec.encode(currentBackup(), 0L)
                    val local = SyncLocal(layout, s.syncPassphrase, _settings.value.deviceId, s.syncLastHash, s.syncLastStamp)
                    SyncEngine(SafSyncStorage(folder)).sync(local)
                }
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

    private inline fun updatePages(undo: Int? = null, change: (List<List<StartEntry>>) -> List<List<StartEntry>>) {
        val updated = change(_pages.value)
        if (updated != _pages.value) {
            if (undo != null) rememberForUndo(undo)
            _pages.value = updated
            store.savePages(updated)
        }
    }

    /** The last change the user made to Start, kept to be undone: how it is told, and what Start was before. */
    data class UndoState(val message: Int, val pages: List<List<StartEntry>>, val shortcuts: List<ShortcutDef>, val token: Int)

    private val _undo = MutableStateFlow<UndoState?>(null)
    val undo: StateFlow<UndoState?> = _undo.asStateFlow()
    private var undoToken = 0

    /** Keeps Start as it is now, to give back if the change that follows is undone. Only the last change is kept. */
    private fun rememberForUndo(message: Int) {
        _undo.value = UndoState(message, _pages.value, _shortcuts.value, ++undoToken)
    }

    fun dismissUndo() {
        _undo.value = null
    }

    /** Gives back Start as it was before the last change. */
    fun undoLast() {
        val state = _undo.value ?: return
        _undo.value = null
        // A tile removed with its note, its date or its number comes back with it; a photo already deleted does not.
        _shortcuts.value = state.shortcuts
        shortcutStore.save(state.shortcuts)
        rebuildItems()
        _pages.value = state.pages
        store.savePages(state.pages)
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
