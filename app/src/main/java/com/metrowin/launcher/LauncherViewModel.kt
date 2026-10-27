package com.metrowin.launcher

import com.metrowin.launcher.data.VolumeProfiles
import com.metrowin.launcher.data.ParkedCarStore
import com.metrowin.launcher.data.ParkedCar
import com.metrowin.launcher.data.WeatherBlock
import com.metrowin.launcher.data.RestorePart
import com.metrowin.launcher.data.BackupCopies
import com.metrowin.launcher.notifications.Mute
import com.metrowin.launcher.data.ShortcutCodec
import com.metrowin.launcher.data.TileCodec
import com.metrowin.launcher.data.SavedLayout
import com.metrowin.launcher.data.Layouts
import com.metrowin.launcher.data.LayoutStore
import com.metrowin.launcher.data.FileType
import com.metrowin.launcher.data.FileSearch
import com.metrowin.launcher.data.FileMatch
import com.metrowin.launcher.data.SavedApp
import com.metrowin.launcher.data.AppListBackup
import com.metrowin.launcher.data.SavedCity
import com.metrowin.launcher.data.SavedCities
import com.metrowin.launcher.model.TileData
import com.metrowin.launcher.data.WhereAmI
import com.metrowin.launcher.data.WalletStore
import com.metrowin.launcher.data.WalletCard
import com.metrowin.launcher.data.TileExtras
import com.metrowin.launcher.data.Steps
import com.metrowin.launcher.data.SiteHeadlines
import com.metrowin.launcher.data.Recordings
import com.metrowin.launcher.data.PlaceFinder
import com.metrowin.launcher.data.Code128
import com.metrowin.launcher.data.DeferWhileAway
import com.metrowin.launcher.data.Throttle
import com.metrowin.launcher.data.PowerPolicy
import com.metrowin.launcher.data.LiveSources
import com.metrowin.launcher.data.LivePermissions
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
import com.metrowin.launcher.data.City
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
import kotlinx.coroutines.flow.update
import com.metrowin.launcher.data.updatedWith
import com.metrowin.launcher.data.keeping
import com.metrowin.launcher.data.OpenMeteo
import com.metrowin.launcher.data.IconPackInfo
import com.metrowin.launcher.data.IconPackRepository
import com.metrowin.launcher.data.PhoneCalls
import com.metrowin.launcher.data.AppLinks
import com.metrowin.launcher.data.VoiceActions
import com.metrowin.launcher.data.CalendarSearch
import com.metrowin.launcher.data.CurrencyRepository
import com.metrowin.launcher.data.Currencies
import com.metrowin.launcher.data.EventMatch
import com.metrowin.launcher.data.AppLanguages
import com.metrowin.launcher.data.HomePresses
import com.metrowin.launcher.data.LevelChange
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
import com.metrowin.launcher.data.BrightnessController
import com.metrowin.launcher.data.ConnectionAccess
import com.metrowin.launcher.data.ConnectionController
import com.metrowin.launcher.data.ConnectionPanel
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.data.RingerMode
import com.metrowin.launcher.data.SoundController
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
import com.metrowin.launcher.data.AlbumInfo
import com.metrowin.launcher.data.NextAlarm
import com.metrowin.launcher.data.TodayExtras
import com.metrowin.launcher.data.BootCount
import com.metrowin.launcher.data.ContactDirectory
import com.metrowin.launcher.data.Favourite
import com.metrowin.launcher.data.Finance
import com.metrowin.launcher.data.FinanceItems
import com.metrowin.launcher.data.FinanceRepository
import com.metrowin.launcher.data.GroupInfo
import com.metrowin.launcher.data.MediaCollections
import com.metrowin.launcher.data.NewsEdition
import com.metrowin.launcher.data.NewsRepository
import com.metrowin.launcher.data.NewsSources
import com.metrowin.launcher.data.PeopleReader
import com.metrowin.launcher.data.Places
import com.metrowin.launcher.data.RecentCall
import com.metrowin.launcher.data.Reminder
import com.metrowin.launcher.data.ReminderStore
import com.metrowin.launcher.data.Reminders
import com.metrowin.launcher.data.SmartDial
import com.metrowin.launcher.model.StopwatchState
import com.metrowin.launcher.model.TimerState
import com.metrowin.launcher.model.TimerStatus
import com.metrowin.launcher.notifications.Alerts
import com.metrowin.launcher.notifications.BannerGate
import com.metrowin.launcher.notifications.MediaSessions
import com.metrowin.launcher.data.VolumeControl
import com.metrowin.launcher.data.VolumeHud
import com.metrowin.launcher.data.VolumeRoute
import com.metrowin.launcher.data.VolumeRouting
import com.metrowin.launcher.data.VolumeRow
import com.metrowin.launcher.data.VolumeStream
import android.media.AudioManager
import com.metrowin.launcher.notifications.NotifAction
import com.metrowin.launcher.notifications.NotificationControl
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.LiveKind
import com.metrowin.launcher.model.StartEntry
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.model.TileStyle
import com.metrowin.launcher.notifications.NotificationEntry
import com.metrowin.launcher.ui.theme.MetroAccents
import com.metrowin.launcher.notifications.NotificationHub
import com.metrowin.launcher.notifications.Snooze
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
data class HomePress(val count: Int = 0, val fromBackground: Boolean = false, val double: Boolean = false)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val apps = AppRepository(application)
    private val store = TileStore(application)
    private val liveTiles = LiveTileRepository(application)
    private val settingsStore = SettingsStore(application)
    private val backgroundStore = StartBackgroundStore(application)
    private val lockStore = StartBackgroundStore(application, "lock_background.jpg")
    private val quickActions = QuickActionsController(application)
    private val connections = ConnectionController(application)
    private val shortcutStore = ShortcutStore(application)
    private val weatherRepository = WeatherRepository(application)
    private val currencyRepository = CurrencyRepository(application)
    private val newsRepository = NewsRepository(application)
    private val financeRepository = FinanceRepository(application)
    private val reminderStore = ReminderStore(application)
    private val directory = ContactDirectory(application)
    private val collections = MediaCollections(application)
    private val people = PeopleReader(application)
    private val profilesController = ProfilesController(application)
    private val iconPacks = IconPackRepository(application)
    private val appShortcuts = AppShortcuts(application)
    private val sound = SoundController(application)
    private val brightnessControl = BrightnessController(application)

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

    /** What the sliders and the sound buttons of the Action Center show: read when the panel opens, then kept as they are moved. */
    data class PanelControls(
        val volume: Float? = null,
        val ringer: RingerMode? = null,
        val brightness: Float? = null,
        /** The "modify system settings" access that the brightness needs has been given. */
        val canSetBrightness: Boolean = false,
        /** How strong the flashlight shines, 0 to 1: only while it is on and the phone can change it. */
        val torch: Float? = null,
        /** The volume of the ringtone and of the alarms, 0 to 1. */
        val ringVolume: Float? = null,
        val alarmVolume: Float? = null,
    )

    private val _controls = MutableStateFlow(PanelControls())
    val controls: StateFlow<PanelControls> = _controls.asStateFlow()

    fun refreshControls() {
        _controls.value = PanelControls(
            sound.volume(), sound.ringer(), brightnessControl.brightness(), brightnessControl.canWrite(), quickActions.torchStrength(),
            sound.ringVolume(), sound.alarmVolume(),
        )
    }

    /** Sets the three volumes to those of the preset at [index] of the settings, and the sliders with them. */
    fun applyVolumeProfile(index: Int) {
        val profile = _settings.value.volumeProfiles.getOrNull(index) ?: return
        VolumeProfiles.apply(sound, profile)
        _controls.value = _controls.value.copy(volume = profile.media / 100f, ringVolume = profile.ring / 100f, alarmVolume = profile.alarm / 100f)
    }

    /** Keeps the volumes the phone has now under [name]; false when the name is blank. */
    fun saveVolumeProfile(name: String): Boolean {
        val profile = VolumeProfiles.capture(name, sound.ringVolume(), sound.volume(), sound.alarmVolume()) ?: return false
        updateSettings { it.copy(volumeProfiles = VolumeProfiles.add(it.volumeProfiles, profile)) }
        return true
    }

    fun setRingVolume(fraction: Float) {
        sound.setRingVolume(fraction)
        _controls.value = _controls.value.copy(ringVolume = fraction)
    }

    fun setAlarmVolume(fraction: Float) {
        sound.setAlarmVolume(fraction)
        _controls.value = _controls.value.copy(alarmVolume = fraction)
    }

    fun setVolume(fraction: Float) {
        sound.setVolume(fraction)
        _controls.value = _controls.value.copy(volume = fraction)
    }

    fun setBrightness(fraction: Float) {
        if (brightnessControl.set(fraction)) _controls.value = _controls.value.copy(brightness = fraction)
    }

    /** Changes the sound mode; silent needs the "do not disturb" access, and without it the screen that gives it opens. */
    fun setRinger(mode: RingerMode) {
        if (!sound.setRinger(mode)) startSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        _controls.value = _controls.value.copy(ringer = sound.ringer())
    }

    /** A spoken change of the media volume. */
    fun changeVolume(change: LevelChange) {
        val now = sound.volume() ?: return
        setVolume(change.apply(now))
    }

    /** A spoken change of the brightness. False without the "modify system settings" access, and the screen that gives it opens. */
    fun changeBrightness(change: LevelChange): Boolean {
        if (!brightnessControl.canWrite()) {
            openBrightnessAccess()
            return false
        }
        val now = brightnessControl.brightness() ?: return false
        setBrightness(change.apply(now))
        return true
    }

    /** Sets the sound mode; false when silent needs the "do not disturb" access that is not given (the screen that gives it opens). */
    fun trySetRinger(mode: RingerMode): Boolean {
        val done = sound.setRinger(mode)
        if (!done) startSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        _controls.value = _controls.value.copy(ringer = sound.ringer())
        return done
    }

    /** Directions to [destination] in the first maps app that takes them. */
    fun navigate(destination: String) {
        val app = getApplication<Application>()
        val opened = VoiceActions.navigationUris(destination).any { uri ->
            runCatching { app.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }
        if (!opened) tell(R.string.launch_failed)
    }

    /** The messages app with a message to [number] written in. */
    fun composeMessage(number: String, text: String) = startIntent(VoiceActions.smsIntent(number, text))

    /** The clock app (its alarms) for a touch on the time of Start. */
    fun openClock() = startIntent(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))

    /** The calendar app for a touch on the date of Start. */
    fun openCalendar() = startIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR))

    private fun startIntent(intent: Intent) {
        val opened = runCatching { getApplication<Application>().startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (!opened) tell(R.string.launch_failed)
    }

    /** The screen of Android where the user lets the launcher change the brightness. */
    fun openBrightnessAccess() {
        val opened = runCatching {
            getApplication<Application>().startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.fromParts("package", getApplication<Application>().packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        if (!opened) tell(R.string.launch_failed)
    }

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
        when (answer) {
            // The reminders are the launcher's own; one without a time is finished in the form of its page (see the caller).
            is QuickAnswer.Reminder -> {
                val at = answer.at
                if (at != null && addReminder(answer.text, at)) tell(R.string.reminder_added)
                return
            }
            is QuickAnswer.Note -> {
                saveNoteText(answer.text)
                return
            }
            is QuickAnswer.Command -> {
                answer.ringer?.let(::setRinger) ?: answer.action?.let { activateQuickAction(it) }
                return
            }
            else -> Unit
        }
        var intent = QuickAnswerActions.intent(answer) ?: return
        // Google first where it is installed; any other app that does the same takes over where it is not.
        when (answer) {
            is QuickAnswer.CalendarEvent -> intent = preferring(intent, QuickAnswerActions.GOOGLE_CALENDAR)
            is QuickAnswer.NearMe -> intent = preferring(intent, QuickAnswerActions.GOOGLE_MAPS)
            else -> Unit
        }
        var opened = runCatching { app.startActivity(intent) }.isSuccess
        // No Google Lens: the camera at least.
        if (!opened && answer is QuickAnswer.Scan) opened = runCatching { app.startActivity(QuickAnswerActions.cameraIntent()) }.isSuccess
        when {
            !opened && (answer is QuickAnswer.Timer || answer is QuickAnswer.Alarm) -> tell(R.string.answer_no_clock)
            !opened -> tell(R.string.launch_failed)
            answer is QuickAnswer.Timer || answer is QuickAnswer.Alarm -> tell(R.string.answer_clock_set)
        }
    }

    /** [intent] aimed at [packageName] when that app is installed and takes it, otherwise as it was. */
    private fun preferring(intent: Intent, packageName: String): Intent {
        val aimed = Intent(intent).setPackage(packageName)
        return if (getApplication<Application>().packageManager.resolveActivity(aimed, 0) != null) aimed else intent
    }

    /** A note: to Google Keep when it is installed, else a note tile on the first Start screen. */
    private fun saveNoteText(text: String) {
        val app = getApplication<Application>()
        val keep = QuickAnswerActions.noteIntent(text).setPackage(QuickAnswerActions.GOOGLE_KEEP)
        if (runCatching { app.startActivity(keep) }.isSuccess) return
        val id = addNote(0)
        saveNote(id, text)
        tell(R.string.note_saved_tile)
    }

    /** The weather of the chosen city: the last fetched, shown at once and refreshed when it gets old. */
    private val _weather = MutableStateFlow(weatherRepository.cached())
    val weather: StateFlow<WeatherData?> = _weather.asStateFlow()
    private var weatherJob: Job? = null

    // The photo of each Start screen that has one of its own; declared here because the setup below reads it.
    private val pageBackgroundStores = HashMap<Int, StartBackgroundStore>()
    private fun pageBackgroundStore(page: Int) = pageBackgroundStores.getOrPut(page) { StartBackgroundStore(getApplication(), "start_background_p$page.jpg") }
    private val _pageBackgrounds = MutableStateFlow<Map<Int, ImageBitmap>>(emptyMap())
    val pageBackgrounds: StateFlow<Map<Int, ImageBitmap>> = _pageBackgrounds.asStateFlow()

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
    private var loadedAlbums: Set<String> = emptySet()
    private var loadedGroups: Set<Long> = emptySet()
    private var feedsJob: Job? = null
    private var loadedGrants = -1
    private var listening = false

    /** Apps installed, updated or removed while the launcher was off screen: the list is read once when it comes back. */
    private val packageEvents = DeferWhileAway()

    /** Whether this screen is counted as one that shows the notifications (see [NotificationHub.startWatching]). */
    private var watchingNotifications = false

    private val _live = MutableStateFlow(LiveTileData(reminders = reminderStore.load()))
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

    /**
     * Notifications for the Action Center, without the apps the user keeps out of it (see [NotificationControl]). Empty until the
     * user turns on notification access.
     */
    val notifications: StateFlow<List<NotificationEntry>> =
        combine(NotificationHub.entries, _settings) { entries, settings -> NotificationControl.forActionCenter(entries, settings) }
            .distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The notifications that arrive while the launcher is on screen, one after the other, for the banner (see [NotificationControl.showsBanner]). */
    private val _banners = kotlinx.coroutines.flow.MutableSharedFlow<NotificationEntry>(extraBufferCapacity = 4, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val banners: kotlinx.coroutines.flow.SharedFlow<NotificationEntry> = _banners

    private var seenNotifications: Map<String, String> = emptyMap()
    private var bannersFrom = 0L

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

    /** Asked from outside the launcher (the Action Center over other apps) to open its settings: counts the requests, back to 0 once they are met. */
    private val _settingsRequests = MutableStateFlow(0)
    val settingsRequests: StateFlow<Int> = _settingsRequests.asStateFlow()

    fun requestSettings() {
        _settingsRequests.value += 1
    }

    fun settingsRequestMet() {
        _settingsRequests.value = 0
    }

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
        // The photos of the Start screens are read when the setting is on, and let go when it is turned off.
        viewModelScope.launch {
            _settings.map { it.pageBackgrounds to it.startPages }.distinctUntilChanged().collect { (on, _) ->
                if (on) loadPageBackgrounds() else _pageBackgrounds.value = emptyMap()
            }
        }
        // Turning on "hide the notifications of Android" moves what is in the shade now to the Action Center.
        viewModelScope.launch {
            _settings.map { Triple(it.hideSystemNotifications, it.notifFilterOn, it.notifFilterWords) }.distinctUntilChanged().drop(1)
                .collect { (hide, filter, _) -> if (hide || filter) NotificationHub.sweep() }
        }
        // The strip of the Action Center over other apps comes and goes with its switch.
        viewModelScope.launch {
            _settings.map { it.edgeActionCenter }.distinctUntilChanged().collect { NotificationHub.edgeChanged() }
        }
        // A notification that arrives while the launcher is on screen gets a banner. What was up when the launcher came back, and
        // what the service reads again at that moment, does not: it is old news.
        viewModelScope.launch {
            NotificationHub.entries.collect { entries ->
                val s = _settings.value
                val wall = System.currentTimeMillis()
                if (listening && android.os.SystemClock.elapsedRealtime() >= bannersFrom && s.toastBanner) {
                    entries
                        .filter {
                            BannerGate.isNew(seenNotifications, it) && wall - it.postTime < BANNER_MAX_AGE_MS && it.packageName != application.packageName &&
                                NotificationControl.showsBanner(it.packageName, s, wall) && !NotificationControl.filtered(it, s)
                        }
                        .maxByOrNull { it.postTime }
                        ?.let {
                            _banners.tryEmit(it)
                            if (s.hideSystemNotifications) NotificationHub.hold(it)
                        }
                }
                seenNotifications = BannerGate.seen(entries)
            }
        }
        // A new name for an app changes the list: the order of the letters too.
        viewModelScope.launch {
            _settings.map { it.appLabels }.distinctUntilChanged().drop(1).collect { rebuildItems() }
        }
        // What Start shows from the phone is read again when a tile of that kind is pinned.
        viewModelScope.launch {
            combine(_pages, _items) { pages, items ->
                listOf(
                    LiveSources.wanted(pages, items), LiveSources.albumIds(pages, items), LiveSources.groupIds(pages, items),
                    LiveSources.feedsWanted(pages, items),
                    // The steps, place and site tiles read for themselves as soon as one is pinned.
                    StartPages.pinnedIds(pages).let { pinned -> items.filter { it.id in pinned && it.data != null }.map { it.id }.toSet() },
                )
            }.distinctUntilChanged().collect { if (listening) refreshLive() }
        }
        // The last message of each person with a tile on Start, from the notifications that wait.
        viewModelScope.launch {
            combine(notifications, _items) { waiting, items ->
                items.filter { it.data is TileData.Contact }.mapNotNull { person ->
                    waiting.firstOrNull { it.title.equals(person.label, ignoreCase = true) }?.let { person.id to (it.longText ?: it.text) }
                }.toMap()
            }.distinctUntilChanged().collect { messages -> setExtras { it.copy(messages = messages) } }
        }
        // A new source of news, other currencies: the tiles read again, now.
        viewModelScope.launch {
            _settings.map { listOf(it.newsSource, it.newsUrl, it.financeBase, it.financeItems) }.distinctUntilChanged().drop(1)
                .collect { if (listening) refreshFeeds() }
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
        // The service reads the notifications again when a screen comes on: a moment of quiet, so they are not all announced.
        bannersFrom = android.os.SystemClock.elapsedRealtime() + BANNER_SETTLE_MS
        if (!watchingNotifications) {
            watchingNotifications = true
            NotificationHub.startWatching()
        }
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
        if (watchingNotifications) {
            watchingNotifications = false
            NotificationHub.stopWatching()
        }
    }

    /** Called every time the launcher goes off screen: behind another app, or the screen turned off. */
    fun onStop() {
        _volumeHud.value = null
        leftAt = android.os.SystemClock.elapsedRealtime()
        stopListening()
    }

    /** When the launcher last went off screen (elapsed time), for [onStart] to tell how long the phone was elsewhere. */
    private var leftAt = 0L

    /** Called every time the launcher comes back on screen. */
    fun onStart() {
        val away = if (leftAt == 0L) 0L else android.os.SystemClock.elapsedRealtime() - leftAt
        leftAt = 0L
        val limit = _settings.value.startTopAfterMin
        // Long enough away: Start is at the top, as after Home.
        if (limit > 0 && away >= limit * 60_000L) _homePresses.value = HomePress(_homePresses.value.count + 1, fromBackground = true)
        startListening()
        val saving = powerSaving.value
        // The profiles are a cheap question and show whether work apps are paused, so they are always asked; the icon packs
        // (a search through every installed app) only now and then.
        refreshProfiles()
        if (throttle.due("iconpacks", PowerPolicy.packagesMaxAgeMs(saving))) refreshIconPacks()
        syncIfDue(PowerPolicy.syncGapMs(SYNC_MIN_GAP_ON_START_MS, saving))
        val changedMeanwhile = packageEvents.takePending()
        if (changedMeanwhile || (loadedLocale != null && loadedLocale != Locale.getDefault())) reloadApps()
        // The buttons of a reminder's notification (done, in 10 min) change the list while the launcher is away.
        reminderStore.load().let { kept -> if (kept != _live.value.reminders) _live.update { it.copy(reminders = kept) } }
        refreshLive()
        refreshWeather()
        applyScheduledLayout()
        forgetEndedMutes()
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

    /** What the Wi-Fi and Bluetooth buttons of the Action Center say, read again from [previous] (see [ConnectionController.read]). */
    suspend fun readConnections(previous: ConnectionPanel, devices: Boolean): ConnectionPanel = connections.read(previous, devices)

    /** A long press on the Wi-Fi button opened the list: asks Android for a fresh look at the networks around. */
    fun scanNetworks() = connections.scan()

    /** The permission to ask for before the list of [action] can show names, or null when it needs none or the access is already there. */
    fun connectionPermission(action: QuickAction): String? =
        connections.permissionFor(action)?.takeIf {
            if (action == QuickAction.Wifi) connections.wifiAccess() == ConnectionAccess.NeedsPermission
            else connections.bluetoothAccess() == ConnectionAccess.NeedsPermission
        }

    /** The length of the screen timeout, in milliseconds, for the button that goes through them (null for the other buttons). */
    fun quickActionTimeout(action: QuickAction): Int? = if (action == QuickAction.ScreenTimeout) quickActions.screenTimeout() else null

    /**
     * Does what the button does. True when that was done on the spot (the flashlight, the quiet hours), false when a screen
     * of Android opened for it. Quiet hours without the "do not disturb" access ask for that access first.
     */
    fun activateQuickAction(action: QuickAction): Boolean {
        val inPlace = quickActions.activate(action, _settings.value.dndMinutes, _settings.value.torchAutoOffMin)
        if ((action == QuickAction.QuietHours || action == QuickAction.QuietTimed) && !inPlace) tell(R.string.qa_quiet_allow)
        return inPlace
    }

    /** The touch on a switch kept on Start: it does what the button of the Action Center does, and its state is read again a moment later. */
    fun toggleTile(actionId: String) {
        val action = QuickAction.fromId(actionId) ?: return
        activateQuickAction(action)
        viewModelScope.launch {
            delay(TOGGLE_SETTLE_MS)
            refreshExtras(force = true)
        }
    }

    /** The strength of the flashlight, 0 to 1, when the phone can change it (Android 13 and later) and the light is on. */
    fun torchStrength(): Float? = quickActions.torchStrength()

    fun setTorchStrength(fraction: Float) {
        quickActions.setTorchStrength(fraction)
        _controls.value = _controls.value.copy(torch = fraction)
    }

    /** Silences the app of [entry] for [option]: its notifications and banners stay out of sight until then (the copies are kept). */
    fun muteApp(entry: NotificationEntry, option: Mute.Option) {
        val until = Mute.until(option, java.time.Instant.now(), java.time.ZoneId.systemDefault())
        updateSettings { it.copy(mutedUntil = it.mutedUntil + (entry.packageName to until)) }
        tell(R.string.mute_done)
    }

    /** Ends the silence of the apps whose time is up, so the settings do not keep what is over. */
    private fun forgetEndedMutes() {
        val now = System.currentTimeMillis()
        if (_settings.value.mutedUntil.any { it.value <= now }) updateSettings { s -> s.copy(mutedUntil = s.mutedUntil.filterValues { it > now }) }
    }

    /** Puts a notification away for a while. */
    fun snoozeNotification(entry: NotificationEntry, option: Snooze.Option) {
        val millis = Snooze.millis(option, java.time.Instant.now(), java.time.ZoneId.systemDefault())
        if (!NotificationHub.snooze(entry.key, millis)) tell(R.string.launch_failed)
    }

    /** The notification settings of the app a notification came from. */
    fun openAppNotificationSettings(entry: NotificationEntry) = openAppNotificationSettings(entry.packageName)

    /** The page of Android where the sound, the vibration and the importance of an app's notifications are chosen. */
    fun openAppNotificationSettings(packageName: String) = startIntent(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
    )

    /** Runs what the notification does when tapped, then removes it if the app asked for that. */
    fun openNotification(entry: NotificationEntry) = NotificationHub.open(getApplication(), entry)

    /** Sends the typed answer to a notification. False (and says so) when the app does not take it. */
    fun replyToNotification(entry: NotificationEntry, text: String): Boolean {
        val sent = NotificationHub.reply(getApplication(), entry, text)
        if (sent && entry.autoCancel) NotificationHub.dismiss(entry.key)
        return sent
    }

    fun dismissNotification(entry: NotificationEntry) = NotificationHub.dismiss(entry.key)

    fun clearNotifications() = NotificationHub.dismissAll()

    /** What the notifications dismissed lately were, newest first (in memory only). */
    val notificationHistory: StateFlow<List<NotificationEntry>> = NotificationHub.history

    fun clearNotificationHistory() = NotificationHub.clearHistory()

    /** Runs the button an app put on its notification. */
    fun runNotificationAction(entry: NotificationEntry, action: NotifAction) = NotificationHub.run(getApplication(), entry, action)

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
    fun onHomePressed(fromBackground: Boolean, now: Long = android.os.SystemClock.uptimeMillis()) {
        val double = HomePresses.isDouble(lastHomePress, now)
        // A third press starts counting again instead of being a second double.
        lastHomePress = if (double) 0L else now
        _homePresses.value = HomePress(_homePresses.value.count + 1, fromBackground, double)
    }

    private var lastHomePress = 0L

    /** The language the launcher was switched to, as a tag of [AppLanguages]; empty when it follows the phone or cannot be switched. */
    fun appLanguage(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return ""
        val locales = getApplication<Application>().getSystemService(android.app.LocaleManager::class.java).applicationLocales
        return if (locales.isEmpty) "" else AppLanguages.normalize(locales[0].language)
    }

    /** Switches the language of the launcher (Android 13 and later); the empty tag goes back to the phone's. */
    fun setAppLanguage(tag: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        getApplication<Application>().getSystemService(android.app.LocaleManager::class.java).applicationLocales =
            if (tag.isEmpty()) android.os.LocaleList.getEmptyLocaleList() else android.os.LocaleList.forLanguageTags(tag)
    }

    /**
     * Reads what the live tiles on Start show, unless that was read lately and Start asks for nothing new. Only the
     * data of the tiles that are pinned is read, and not at all while none of them is. [force] reads in any case.
     */
    fun refreshLive(force: Boolean = false) {
        refreshFeeds(force)
        refreshExtras(force)
        val wanted = LiveSources.wanted(_pages.value, _items.value)
        val albumIds = LiveSources.albumIds(_pages.value, _items.value)
        val groupIds = LiveSources.groupIds(_pages.value, _items.value)
        val feeds = LiveSources.feedsWanted(_pages.value, _items.value)
        // A permission given or taken away since the last read changes what there is to show at once.
        val grants = LivePermissions.all.count { LivePermissions.isGranted(getApplication(), it) }
        val everything = force || wanted != loadedLive || grants != loadedGrants || albumIds != loadedAlbums || groupIds != loadedGroups
        // Every kind has its own age (see PowerPolicy.liveMaxAgeMs): the birthdays are not read again at every return to Start.
        val saving = powerSaving.value
        val stale = wanted.filterTo(HashSet()) { throttle.due("live:${it.name}", PowerPolicy.liveMaxAgeMs(it, saving)) }
        val toRead = if (everything) wanted else stale
        if (toRead.isEmpty() && !everything) {
            // Nothing to read, but a tile that went from Start must let go of what it showed.
            _live.update { it.keeping(wanted + feeds, albumIds, groupIds) }
            return
        }
        loadedLive = wanted
        loadedGrants = grants
        loadedAlbums = albumIds
        loadedGroups = groupIds
        viewModelScope.launch {
            val fresh = if (toRead.isEmpty()) LiveTileData() else withContext(Dispatchers.IO) { liveTiles.load(toRead, albumIds, groupIds) }
            _live.update { it.keeping(wanted + feeds, albumIds, groupIds).updatedWith(fresh, toRead) }
        }
    }

    /**
     * Reads the headlines and the quotes of the News and Finance tiles that are on Start, unless what is kept is recent
     * ([PowerPolicy.feedMaxAgeMs]); a failed try is not repeated at every return ([PowerPolicy.feedRetryMs]). [force] reads at once.
     * The network is asked only while the launcher is on screen and only for a tile that is pinned.
     */
    private fun refreshFeeds(force: Boolean = false) {
        val feeds = LiveSources.feedsWanted(_pages.value, _items.value)
        if (feeds.isEmpty()) {
            if (_live.value.news != null || _live.value.finance != null) _live.update { it.copy(news = null, finance = null) }
            return
        }
        val s = _settings.value
        val locale = Locale.getDefault()
        val edition = NewsEdition.of(locale)
        val newsKey = NewsSources.key(s.newsSource, s.newsUrl, edition)
        val base = Finance.baseFor(s.financeBase, locale)
        val items = FinanceItems.normalize(s.financeItems, base)
        val financeKey = Finance.key(base, items)
        val saving = powerSaving.value
        val maxAge = PowerPolicy.feedMaxAgeMs(saving)
        val retry = PowerPolicy.feedRetryMs(saving)
        fun stale(fetchedAt: Long, now: Long) = now < fetchedAt || now - fetchedAt >= maxAge
        feedsJob?.cancel()
        feedsJob = viewModelScope.launch {
            if (LiveKind.News in feeds) {
                // What is kept is shown at once, if it is of the source that is chosen now.
                val kept = (_live.value.news ?: withContext(Dispatchers.IO) { newsRepository.cached() })?.takeIf { it.sourceKey == newsKey }
                _live.update { it.copy(news = kept) }
                val now = System.currentTimeMillis()
                if ((force || kept == null || stale(kept.fetchedAt, now)) && (force || throttle.due("news:$newsKey", retry))) {
                    val fetched = withContext(Dispatchers.IO) { newsRepository.fetch(s.newsSource, s.newsUrl, edition, now) }
                    if (fetched != null) _live.update { it.copy(news = fetched) }
                }
            }
            if (LiveKind.Finance in feeds) {
                val kept = (_live.value.finance ?: withContext(Dispatchers.IO) { financeRepository.cached() })?.takeIf { it.key == financeKey }
                _live.update { it.copy(finance = kept) }
                val now = System.currentTimeMillis()
                if (items.isNotEmpty() && (force || kept == null || stale(kept.fetchedAt, now)) && (force || throttle.due("finance:$financeKey", retry))) {
                    val fetched = withContext(Dispatchers.IO) { financeRepository.fetch(base, items, now) }
                    if (fetched != null) _live.update { it.copy(finance = fetched) }
                }
            }
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

    /** The weather of the other cities that are followed, by [SavedCity.key]; what is not there yet is being fetched. */
    private val _cityWeather = MutableStateFlow<Map<String, WeatherData>>(emptyMap())
    val cityWeather: StateFlow<Map<String, WeatherData>> = _cityWeather.asStateFlow()

    /** Reads the weather of the followed cities when the Forecast page is opened: not before, and not again within a few minutes. */
    fun refreshCities(force: Boolean = false) {
        val s = _settings.value
        _cityWeather.update { old -> old.filterKeys { key -> s.weatherCities.any { it.key == key } } }
        if (s.weatherCities.isEmpty()) return
        val saving = powerSaving.value
        if (!force && !throttle.due("cities", PowerPolicy.weatherRetryMs(saving))) return
        viewModelScope.launch {
            s.weatherCities.forEach { city ->
                val fetched = withContext(Dispatchers.IO) { weatherRepository.fetch(city.latitude, city.longitude, s.weatherFahrenheit) }
                if (fetched != null) _cityWeather.update { it + (city.key to fetched) }
            }
        }
    }

    /** Follows [place] as well: the Forecast page shows it under the city of the tile. */
    fun followPlace(place: PlaceMatch) {
        updateSettings { s ->
            if (s.weatherCities.size >= SavedCities.MAX || s.weatherCities.any { it.latitude == place.latitude && it.longitude == place.longitude }) s
            else s.copy(weatherCities = s.weatherCities + SavedCity(place.displayName, place.latitude, place.longitude))
        }
        refreshCities(force = true)
    }

    fun unfollowCity(city: SavedCity) {
        updateSettings { s -> s.copy(weatherCities = s.weatherCities - city) }
        _cityWeather.update { it - city.key }
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
        val saving = powerSaving.value
        val unitChanged = current != null && current.fahrenheit != s.weatherFahrenheit
        val wantsAir = WeatherBlock.Air.code in s.weatherBlocks || WeatherBlock.Pollen.code in s.weatherBlocks
        // The air is a second answer: switched on later, it is asked for at the next visit even if the forecast is recent.
        val stale = current == null || OpenMeteo.isStale(current, System.currentTimeMillis(), PowerPolicy.weatherMaxAgeMs(saving)) ||
            (wantsAir && current.air == null)
        if (!force && !unitChanged && !stale) return
        // Done or not, the next try waits: with no connection every return to Start would otherwise go to the network again.
        if (!force && !unitChanged && !throttle.due("weather", PowerPolicy.weatherRetryMs(saving))) return
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            val fetched = withContext(Dispatchers.IO) {
                weatherRepository.fetch(s.weatherLatitude, s.weatherLongitude, s.weatherFahrenheit)?.let { forecast ->
                    if (wantsAir) forecast.copy(air = weatherRepository.fetchAir(s.weatherLatitude, s.weatherLongitude)) else forecast
                }
            }
            // Nothing new (no connection): the weather already there stays, however old.
            if (fetched != null) {
                _weather.value = fetched
                weatherRepository.save(fetched)
            }
        }
    }

    /** The next event and alarm for the Today card: read when the app list comes up, never in the background. */
    private val _today = MutableStateFlow(TodayExtras())
    val today: StateFlow<TodayExtras> = _today.asStateFlow()

    /**
     * Reads the next event of the calendar and the next alarm for the Today card, unless that was done lately
     * ([PowerPolicy.todayMaxAgeMs]) or the card is off. It is called only while the app list is on screen; the weather and the
     * reminders the card also shows are the ones the launcher already holds.
     */
    fun refreshToday(force: Boolean = false) {
        if (!_settings.value.searchToday) return
        if (!force && !throttle.due("today", PowerPolicy.todayMaxAgeMs(powerSaving.value))) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val read = withContext(Dispatchers.IO) {
                TodayExtras(now, CalendarSearch(app).next(now, TODAY_WINDOW_MS), NextAlarm.read(app).takeIf { it > 0L })
            }
            if (read != _today.value) _today.value = read
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

    suspend fun searchContacts(query: String): List<ContactMatch> = withContext(Dispatchers.IO) {
        val digits = query.trim()
        // Digits are a dial: the keys of the names and the numbers, with the number to call at hand (see [SmartDial]).
        if (_settings.value.searchSmartDial && SmartDial.isQuery(digits)) {
            directory.search(digits).map { ContactMatch(it.name, it.lookupUri, it.number) }
        } else {
            ContactSearch(getApplication()).search(query)
        }
    }

    /** The files whose name matches [query], of the kinds the user chose in the settings. */
    suspend fun searchFiles(query: String): List<FileMatch> = withContext(Dispatchers.IO) {
        FileSearch(getApplication()).search(query, _settings.value.searchFileTypes.mapNotNullTo(HashSet(), FileType::fromCode))
    }

    /** The notifications that wait in the Action Center whose title or text has every word of [query]. */
    fun searchNotifications(query: String): List<NotificationEntry> {
        val words = AppSections.fold(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty() || query.trim().length < 2) return emptyList()
        return notifications.value.filter { entry ->
            val text = AppSections.fold(listOf(entry.title, entry.text, entry.longText.orEmpty()).joinToString(" "))
            words.all { it in text }
        }.take(NOTIFICATION_RESULTS)
    }

    suspend fun searchEvents(query: String): List<EventMatch> =
        withContext(Dispatchers.IO) { CalendarSearch(getApplication()).search(query) }

    /**
     * The conversion [query] asks for, with the rates of the last six hours (asked from the Internet when they are older; the old
     * ones are used when there is no connection). Null when there are no rates at all.
     */
    suspend fun currencyAnswer(query: Currencies.Query): QuickAnswer.Currency? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val kept = currencyRepository.cached()
        val rates = kept?.takeIf { now - it.fetchedAt < Currencies.MAX_AGE_MS } ?: currencyRepository.fetch(now) ?: kept
        rates?.let { Currencies.answer(query, it) }
    }

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
            if (updated.weatherFahrenheit != previous.weatherFahrenheit) _cityWeather.value = emptyMap()
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

    fun resize(id: String) {
        val allowLarge = _settings.value.largeTiles
        updatePages(R.string.undo_changed) { StartPages.update(it, id) { page -> StartEntries.resize(page, id, allowLarge) } }
    }

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

    /** Writes [title] above the tile or folder [id] as the title of the group it starts; blank takes the title away. */
    fun setGroupTitle(id: String, title: String) =
        updatePages(R.string.undo_changed) { StartPages.update(it, id) { page -> StartEntries.setGroup(page, id, title) } }

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
                // Going into the app is reading what it says: its notifications, and the number on its tile, go.
                val target = item.launch
                if (_settings.value.clearOnOpen && target is Launch.Activity) NotificationHub.dismissApp(target.component.packageName)
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
        // Apps update all day, each with a handful of events; the whole list is not read again at every one of them
        // with another app in front or the screen off, only once when the launcher is back.
        if (!packageEvents.request(onScreen = listening)) return
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
    /** The app's page in the store app, or on the web when the phone has no store app. */
    fun openInStore(item: AppItem) {
        val pkg = item.packageName ?: return
        val app = getApplication<Application>()
        val pages = listOfNotNull(AppLinks.storeUri(pkg), AppLinks.webUrl(pkg))
        val opened = pages.any { page ->
            runCatching { app.startActivity(Intent(Intent.ACTION_VIEW, page.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }
        if (!opened) tell(R.string.launch_failed)
    }

    /** Opens the share sheet with the name of the app and the link of its page. */
    fun shareApp(item: AppItem) {
        val pkg = item.packageName ?: return
        val text = AppLinks.shareText(item.label, pkg) ?: return
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        val opened = runCatching {
            getApplication<Application>().startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!opened) tell(R.string.launch_failed)
    }

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
        if (removed.kind == ShortcutKind.Widget) clearUndo() else rememberForUndo(R.string.undo_removed)
        _shortcuts.value = _shortcuts.value.filter { it.id != id }
        shortcutStore.save(_shortcuts.value)
        shortcutStore.deletePhoto(removed.photoFile)
        if (removed.kind == ShortcutKind.Widget) widgets.delete(removed.widgetId)
        if (removed.kind == ShortcutKind.Timer) Alerts.cancelTimer(getApplication(), removed.id)
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

    /** Pins a counter at zero and gives back its id. */
    fun addCounter(page: Int): String {
        val id = ShortcutDef.newId(ShortcutKind.Counter, "counter")
        addShortcut(ShortcutDef(id, ShortcutKind.Counter, getApplication<Application>().getString(R.string.counter_default_label)), page, TileSize.Medium)
        return id
    }

    /** One more or one less on a counter, never below zero. */
    fun countStep(id: String, delta: Int) = updateShortcut(id) { it.copy(number = (it.number + delta).coerceIn(0L, MAX_COUNT)) }

    /** The name of a counter (blank gives the default one back), and its count back to zero when [reset]. */
    fun saveCounter(id: String, name: String, reset: Boolean) = updateShortcut(id) {
        it.copy(
            label = name.trim().ifEmpty { getApplication<Application>().getString(R.string.counter_default_label) },
            number = if (reset) 0L else it.number,
        )
    }

    /** Pins an empty list of things to do and gives back its id. */
    fun addChecklist(page: Int): String {
        val id = ShortcutDef.newId(ShortcutKind.Checklist, "list")
        addShortcut(ShortcutDef(id, ShortcutKind.Checklist, getApplication<Application>().getString(R.string.checklist_default_label)), page, TileSize.Medium)
        return id
    }

    fun saveChecklist(id: String, raw: String) = updateShortcut(id) { it.copy(text = raw) }

    /** Pins the time in [city]. */
    fun addWorldClock(city: City, page: Int) =
        addShortcut(ShortcutDef(ShortcutDef.newId(ShortcutKind.WorldClock, city.zone), ShortcutKind.WorldClock, city.name, city.zone), page, TileSize.Medium)

    fun addMoon(page: Int) = addShortcut(
        ShortcutDef(ShortcutDef.newId(ShortcutKind.Moon, "moon"), ShortcutKind.Moon, getApplication<Application>().getString(R.string.moon_default_label)),
        page, TileSize.Medium,
    )

    fun addBirthdays(page: Int) = addShortcut(
        ShortcutDef(ShortcutDef.newId(ShortcutKind.Birthdays, "birthdays"), ShortcutKind.Birthdays, getApplication<Application>().getString(R.string.birthdays_default_label)),
        page, TileSize.Wide,
    )

    // --- the tiles that need a choice or keep a state of their own

    /** Pins a place: a name and an address; a touch starts the navigation there. False when there is no address. */
    fun addPlace(name: String, address: String, page: Int): Boolean {
        val clean = Places.clean(address) ?: return false
        val label = Places.nameFor(name, clean).ifBlank { getApplication<Application>().getString(R.string.place_default_label) }
        addShortcut(ShortcutDef(ShortcutDef.newId(ShortcutKind.Place, clean), ShortcutKind.Place, label, clean), page, TileSize.Medium)
        return true
    }

    /** The albums of photos to choose from (needs the permission to read photos). */
    suspend fun albums(): List<AlbumInfo> = withContext(Dispatchers.IO) { collections.albums() }

    /** The groups of contacts to choose from (needs the permission to read contacts). */
    suspend fun groups(): List<GroupInfo> = withContext(Dispatchers.IO) { collections.groups() }

    /** Pins an album as a Cycle tile: its photos fade into one another. */
    fun addAlbum(album: AlbumInfo, page: Int) {
        val id = ShortcutDef.newId(ShortcutKind.Album, album.id)
        addShortcut(ShortcutDef(id, ShortcutKind.Album, album.name, album.id), page, TileSize.Medium)
        setStyle(id, TileStyle.Cycle)
    }

    /** Pins a group of contacts: the faces of its people, one after the other. */
    fun addGroup(group: GroupInfo, page: Int) =
        addShortcut(ShortcutDef(ShortcutDef.newId(ShortcutKind.Group, group.id.toString()), ShortcutKind.Group, group.name, group.id.toString()), page, TileSize.Medium)

    fun addNews(page: Int) = addShortcut(
        ShortcutDef(ShortcutDef.newId(ShortcutKind.News, "news"), ShortcutKind.News, getApplication<Application>().getString(R.string.news_default_label)),
        page, TileSize.Wide,
    )

    fun addFinance(page: Int) = addShortcut(
        ShortcutDef(ShortcutDef.newId(ShortcutKind.Finance, "finance"), ShortcutKind.Finance, getApplication<Application>().getString(R.string.finance_default_label)),
        page, TileSize.Medium,
    )

    fun addDialGrid(page: Int) = addShortcut(
        ShortcutDef(ShortcutDef.newId(ShortcutKind.DialGrid, "dialgrid"), ShortcutKind.DialGrid, getApplication<Application>().getString(R.string.dialgrid_default_label)),
        page, TileSize.Medium,
    )

    fun addPeopleHub(page: Int) = addShortcut(
        ShortcutDef(ShortcutDef.newId(ShortcutKind.PeopleHub, "peoplehub"), ShortcutKind.PeopleHub, getApplication<Application>().getString(R.string.peoplehub_default_label)),
        page, TileSize.Medium,
    )

    fun addRemindersTile(page: Int) = addShortcut(
        ShortcutDef(ShortcutDef.newId(ShortcutKind.Reminders, "reminders"), ShortcutKind.Reminders, getApplication<Application>().getString(R.string.reminders_default_label)),
        page, TileSize.Medium,
    )

    // --- steps, recorder, camera, wallet, where am I

    private fun addTile(kind: ShortcutKind, @androidx.annotation.StringRes label: Int, page: Int, size: TileSize = TileSize.Medium) =
        addShortcut(ShortcutDef(ShortcutDef.newId(kind, kind.code), kind, getApplication<Application>().getString(label)), page, size)

    fun addSteps(page: Int) = addTile(ShortcutKind.Steps, R.string.steps_default_label, page)
    fun addRecorder(page: Int) = addTile(ShortcutKind.Recorder, R.string.recorder_default_label, page)
    fun addCamera(page: Int) = addTile(ShortcutKind.Camera, R.string.camera_default_label, page)
    fun addWallet(page: Int) = addTile(ShortcutKind.Wallet, R.string.wallet_default_label, page)
    fun addWhereAmI(page: Int) = addTile(ShortcutKind.WhereAmI, R.string.whereami_default_label, page)
    fun addParking(page: Int) = addTile(ShortcutKind.Parking, R.string.parking_default_label, page)
    fun addAnalogClock(page: Int) = addTile(ShortcutKind.AnalogClock, R.string.analogclock_default_label, page)
    fun addSun(page: Int) = addTile(ShortcutKind.Sun, R.string.sun_default_label, page)

    /** Keeps the switch [action] of the Action Center on Start as a small tile. */
    fun addToggle(action: QuickAction, page: Int) = addShortcut(
        ShortcutDef(ShortcutDef.newId(ShortcutKind.Toggle, action.id), ShortcutKind.Toggle, getApplication<Application>().getString(action.label), action.id),
        page, TileSize.Small,
    )

    private val walletStore = WalletStore(application)
    private val _wallet = MutableStateFlow(walletStore.load())
    val wallet: StateFlow<List<WalletCard>> = _wallet.asStateFlow()

    /** Keeps a card; false when the name is empty, the code cannot be drawn as a barcode or the wallet is full. */
    fun addCard(name: String, code: String): Boolean {
        val clean = name.trim()
        val digits = code.trim()
        if (clean.isEmpty() || !Code128.valid(digits) || _wallet.value.size >= WalletStore.MAX_CARDS) return false
        saveWallet(_wallet.value + WalletCard("card:" + System.nanoTime().toString(36), clean, digits))
        return true
    }

    fun removeCard(id: String) = saveWallet(_wallet.value.filter { it.id != id })

    private fun saveWallet(cards: List<WalletCard>) {
        _wallet.value = cards
        walletStore.save(cards)
        setExtras { it.copy(cards = cards.size) }
    }

    private val recordings = Recordings(application)
    private val parkedCarStore = ParkedCarStore(application)
    private var extrasJob: Job? = null

    private fun setExtras(change: (TileExtras) -> TileExtras) = _live.update { it.copy(extras = change(it.extras)) }

    /** Keeps where the phone is as the place of the car; null when the place could not be found. */
    suspend fun parkHere(): ParkedCar? {
        val here = withContext(Dispatchers.IO) { PlaceFinder.find(getApplication()) } ?: return null
        val car = ParkedCar(here.latitude, here.longitude, here.address, System.currentTimeMillis())
        parkedCarStore.save(car)
        setExtras { it.copy(parked = car) }
        return car
    }

    fun forgetCar() {
        parkedCarStore.save(null)
        setExtras { it.copy(parked = null) }
    }

    /** Where the phone is, found now and kept for the tile that shows it. */
    suspend fun locate(): WhereAmI? {
        val here = withContext(Dispatchers.IO) { PlaceFinder.find(getApplication()) }
        here?.address?.let { street -> setExtras { it.copy(place = street) } }
        return here
    }

    /**
     * Reads what the steps, place, recorder, wallet, person and site tiles on Start show. Only the tiles that are pinned cost anything:
     * the step sensor and the position are asked once, and the pages of the sites only while the launcher is on screen.
     */
    fun refreshExtras(force: Boolean = false) {
        val app = getApplication<Application>()
        val pinned = StartPages.pinnedIds(_pages.value)
        val tiles = _items.value.filter { it.id in pinned }
        val kinds = tiles.mapNotNull { it.data }
        val sites = tiles.filter { it.data is TileData.Site }
        setExtras { old ->
            old.copy(
                cards = if (kinds.any { it is TileData.Wallet }) _wallet.value.size else 0,
                recordings = if (kinds.any { it is TileData.Recorder }) recordings.list().size else 0,
                parked = if (kinds.any { it is TileData.Parking }) parkedCarStore.load() else null,
                sites = old.sites.filterKeys { id -> sites.any { it.id == id } },
                toggles = kinds.filterIsInstance<TileData.Toggle>().mapNotNull { tile ->
                    QuickAction.fromId(tile.action)?.let { action -> quickActions.isOn(action)?.let { tile.action to it } }
                }.toMap(),
            )
        }
        // A read that is on its way is not cut short by the next return to Start: its throttle slot is already used.
        if (!force && extrasJob?.isActive == true) return
        extrasJob?.cancel()
        extrasJob = viewModelScope.launch {
            if (kinds.any { it is TileData.Steps } && (force || throttle.due("extra:steps", 60_000L))) {
                Steps.read(app)?.let { n -> setExtras { it.copy(steps = n, stepDays = Steps.history(app)) } }
            }
            if (kinds.any { it is TileData.WhereAmI } && (force || throttle.due("extra:where", 15 * 60_000L))) {
                locate()
            }
            val retry = PowerPolicy.feedRetryMs(powerSaving.value)
            sites.forEach { site ->
                val url = (site.data as TileData.Site).url
                if (force || throttle.due("extra:site:${site.id}", retry)) {
                    withContext(Dispatchers.IO) { SiteHeadlines.latest(url) }?.let { news -> setExtras { it.copy(sites = it.sites + (site.id to news)) } }
                }
            }
        }
    }

    // --- timer: a moment kept in the tile and one alarm of Android; nothing runs while it counts

    /** Pins a timer of five minutes and gives back its id, so its page can open on it. */
    fun addTimer(page: Int): String {
        val id = ShortcutDef.newId(ShortcutKind.Timer, "timer")
        addShortcut(
            ShortcutDef(id, ShortcutKind.Timer, getApplication<Application>().getString(R.string.timer_default_label), text = TimerState.Default.encode()),
            page, TileSize.Medium,
        )
        return id
    }

    fun timerStart(id: String) = changeTimer(id) { it.start(System.currentTimeMillis()) }

    fun timerPause(id: String) = changeTimer(id) { it.pause(System.currentTimeMillis()) }

    fun timerReset(id: String) = changeTimer(id) { it.reset() }

    /** Another length for the timer, from the beginning. */
    fun timerLength(id: String, millis: Long) = changeTimer(id) { it.withDuration(millis) }

    /** Adds [millis] to the timer that runs (or to the one that waits), up to the longest one. */
    fun timerAdd(id: String, millis: Long) = changeTimer(id) { state ->
        val now = System.currentTimeMillis()
        if (state.running && state.status(now) == TimerStatus.Running) {
            val longer = (state.durationMs + millis).coerceAtMost(TimerState.MAX_MS)
            state.copy(durationMs = longer, endAt = state.endAt + (longer - state.durationMs))
        } else {
            state.withDuration(state.durationMs + millis)
        }
    }

    private fun changeTimer(id: String, change: (TimerState) -> TimerState) {
        val def = _shortcuts.value.firstOrNull { it.id == id && it.kind == ShortcutKind.Timer } ?: return
        val after = change(TimerState.decode(def.text))
        updateShortcut(id) { it.copy(text = after.encode()) }
        val app = getApplication<Application>()
        // The alarm that rings it is set while it counts, and taken back when it stops; the notification of a timer that rang goes with a reset.
        if (after.running && after.status(System.currentTimeMillis()) == TimerStatus.Running) {
            Alerts.scheduleTimer(app, id, def.label, after.endAt)
        } else {
            Alerts.cancelTimer(app, id)
        }
    }

    // --- stopwatch: the two clocks of the phone at the start, and arithmetic when somebody looks

    fun addStopwatch(page: Int): String {
        val id = ShortcutDef.newId(ShortcutKind.Stopwatch, "stopwatch")
        addShortcut(
            ShortcutDef(id, ShortcutKind.Stopwatch, getApplication<Application>().getString(R.string.stopwatch_default_label), text = StopwatchState().encode()),
            page, TileSize.Medium,
        )
        return id
    }

    fun stopwatchStart(id: String) = changeStopwatch(id) { it.start(android.os.SystemClock.elapsedRealtime(), System.currentTimeMillis(), boot()) }

    fun stopwatchStop(id: String) = changeStopwatch(id) { it.stop(android.os.SystemClock.elapsedRealtime(), System.currentTimeMillis(), boot()) }

    fun stopwatchLap(id: String) = changeStopwatch(id) { it.lap(android.os.SystemClock.elapsedRealtime(), System.currentTimeMillis(), boot()) }

    fun stopwatchReset(id: String) = changeStopwatch(id) { it.reset() }

    private fun boot() = BootCount.read(getApplication())

    private fun changeStopwatch(id: String, change: (StopwatchState) -> StopwatchState) {
        val def = _shortcuts.value.firstOrNull { it.id == id && it.kind == ShortcutKind.Stopwatch } ?: return
        updateShortcut(id) { it.copy(text = change(StopwatchState.decode(def.text)).encode()) }
    }

    // --- reminders

    /** The reminders, in the order they were made (see [Reminders] for the order of the list). */
    val reminders: StateFlow<List<Reminder>> = _live.map { it.reminders }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, _live.value.reminders)

    /** Makes a reminder for [at] and sets its alarm. False when there is no text. */
    fun addReminder(text: String, at: Long): Boolean {
        val before = _live.value.reminders
        val after = Reminders.add(before, text, at)
        val added = after.firstOrNull { it.id == Reminders.nextId(before) } ?: return false
        saveReminders(after)
        Alerts.scheduleReminder(getApplication(), added)
        return true
    }

    fun completeReminder(id: Long) {
        saveReminders(Reminders.complete(_live.value.reminders, id))
        Alerts.cancelReminder(getApplication(), id)
    }

    fun removeReminder(id: Long) {
        saveReminders(Reminders.remove(_live.value.reminders, id))
        Alerts.cancelReminder(getApplication(), id)
    }

    fun clearDoneReminders() = saveReminders(Reminders.clearDone(_live.value.reminders))

    private fun saveReminders(list: List<Reminder>) {
        reminderStore.save(list)
        _live.update { it.copy(reminders = list) }
    }

    /** Whether Android lets the launcher ring a reminder to the minute. */
    fun canScheduleExactAlarms() = Alerts.canScheduleExact(getApplication())

    /** The screen of Android where the user lets the launcher set exact alarms (Android 12 and later). */
    fun openExactAlarmAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startIntent(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", getApplication<Application>().packageName, null)))
        }
    }

    // --- the people: favourites, recent calls

    suspend fun favourites(): List<Favourite> = withContext(Dispatchers.IO) { people.favourites() }

    suspend fun recentCalls(): List<RecentCall> = withContext(Dispatchers.IO) { people.recentCalls() }

    // --- the volume keys: a panel over the launcher for what is sounding now

    private val volumeControl = VolumeControl(application)
    private val _volumeHud = MutableStateFlow<VolumeHud?>(null)

    /** The volume panel, or null while it is away. It goes by itself (see VolumeHudPanel) and with the launcher leaving the screen. */
    val volumeHud: StateFlow<VolumeHud?> = _volumeHud.asStateFlow()
    private var hudToken = 0

    /** The player whose own volume the panel moves, while it is that one's. */
    private var hudPlayer: android.media.session.MediaController? = null

    private fun labelOf(packageName: String): String = runCatching {
        val manager = getApplication<Application>().packageManager
        manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    /**
     * A press of a volume key while the launcher is on screen. It moves what is sounding, the way Android does (the call, then a
     * player with a volume of its own, then the music of whatever app plays, then the ringer), and shows the panel with the name
     * of the app that plays. False when the panel is off in the settings or nothing could be moved: Android then handles the key.
     */
    fun onVolumeKey(raise: Boolean): Boolean {
        if (!_settings.value.volumePanel) return false
        return runCatching {
            val sessions = MediaSessions.active(getApplication())
            val playing = sessions.firstOrNull { it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING }
            val remote = playing?.takeIf {
                val info = it.playbackInfo
                info != null && info.playbackType == android.media.session.MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE &&
                    info.volumeControl != android.media.VolumeProvider.VOLUME_CONTROL_FIXED
            }
            val hud = when (val route = VolumeRouting.route(volumeControl.inCall(), remote != null, volumeControl.musicActive() || playing != null)) {
                VolumeRoute.Remote -> remote?.let { moveRemote(it, raise) }
                is VolumeRoute.Stream -> volumeControl.adjust(route.stream, raise)?.let { row ->
                    hudPlayer = null
                    VolumeHud(++hudToken, row, app = playing?.takeIf { route.stream == VolumeStream.Music }?.let { labelOf(it.packageName) })
                }
            } ?: return false
            _volumeHud.value = hud
            true
        }.getOrDefault(false)
    }

    private fun moveRemote(player: android.media.session.MediaController, raise: Boolean): VolumeHud? {
        val info = player.playbackInfo ?: return null
        runCatching { player.adjustVolume(if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0) }
        hudPlayer = player
        // The level is worked out here: the player reports the new one a moment later.
        val level = VolumeRouting.step(info.currentVolume, 0, info.maxVolume, raise)
        return VolumeHud(++hudToken, VolumeRow(null, player.packageName, level, 0, info.maxVolume), app = labelOf(player.packageName))
    }

    /** A slider of the panel was touched: sets that volume and keeps the panel up. */
    fun setVolume(row: VolumeRow, fraction: Float) {
        val hud = _volumeHud.value ?: return
        val level = VolumeRouting.level(fraction, row.min, row.max)
        val updated = when {
            row.remotePackage != null -> {
                hudPlayer?.takeIf { it.packageName == row.remotePackage }?.let { runCatching { it.setVolumeTo(level, 0) } }
                row.copy(level = level)
            }
            row.stream != null -> volumeControl.set(row.stream, level) ?: row
            else -> row
        }
        fun same(other: VolumeRow) = other.stream == row.stream && other.remotePackage == row.remotePackage
        _volumeHud.value = hud.copy(
            token = ++hudToken,
            main = if (same(hud.main)) updated else hud.main,
            others = hud.others.map { if (same(it)) updated else it },
        )
    }

    /** Opens the panel out to the other volumes, or folds it back. */
    fun toggleVolumeHud() {
        val hud = _volumeHud.value ?: return
        val expanded = !hud.expanded
        val others = if (expanded) {
            listOf(VolumeStream.Music, VolumeStream.Ring, VolumeStream.Alarm)
                .filter { it != hud.main.stream }
                .mapNotNull(volumeControl::read)
        } else {
            emptyList()
        }
        _volumeHud.value = hud.copy(token = ++hudToken, expanded = expanded, others = others)
    }

    fun dismissVolumeHud() {
        _volumeHud.value = null
    }

    /** The gallery app, for a touch on an album tile. */
    fun openGallery() = startIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY))

    /** The contacts app, for a touch on a group tile. */
    fun openContacts() = startIntent(Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI))

    /** Opens a web page (a headline, a quote) in the browser. */
    fun openUrl(address: String) = startIntent(Intent(Intent.ACTION_VIEW, address.toUri()))

    /** The headlines and the quotes read again, now (the refresh button of their pages). */
    fun refreshFeedsNow() = refreshFeeds(force = true)

    /** Puts a shortcut of an app on Start (the app's icon, its name, and the shortcut as what it opens). */
    fun pinAppShortcut(shortcut: AppShortcut, page: Int) {
        val serial = getApplication<Application>().getSystemService(android.os.UserManager::class.java).getSerialNumberForUser(shortcut.user)
        val uri = ShortcutDef.appShortcutUri(shortcut.packageName, serial, shortcut.id)
        addShortcut(ShortcutDef(ShortcutDef.newId(ShortcutKind.AppShortcut, uri), ShortcutKind.AppShortcut, shortcut.label, uri), page, TileSize.Medium)
    }

    /**
     * Start as it was when the launcher was installed: the first tiles of the default layout, and everything the user pinned
     * that is not an app (contacts, sites, notes, widgets…) taken away. Undo brings it all back except the widgets, which give
     * their place back to Android at once.
     */
    fun resetStart() {
        rememberForUndo(R.string.undo_reset)
        _shortcuts.value.forEach { shortcut ->
            shortcutStore.deletePhoto(shortcut.photoFile)
            if (shortcut.kind == ShortcutKind.Timer) Alerts.cancelTimer(getApplication(), shortcut.id)
            if (shortcut.kind == ShortcutKind.Widget) widgets.delete(shortcut.widgetId)
        }
        _shortcuts.value = emptyList()
        shortcutStore.save(emptyList())
        rebuildItems()
        val screens = _settings.value.startPages.coerceAtLeast(1)
        val fresh = listOf<List<StartEntry>>(DefaultStart.build(_items.value)) + List(screens - 1) { emptyList() }
        _pages.value = fresh
        store.savePages(fresh)
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
        val keep = _settings.value.backupCopies
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                val bytes = LayoutBackupCodec.encode(currentBackup(), System.currentTimeMillis()).toByteArray()
                val files = SafFolder(getApplication(), folder.toUri())
                val written = files.write(SafFolder.BACKUP_FILE, "application/json", bytes)
                // With more than one copy asked for, the same file is also kept under the minute it was made, and the oldest ones go.
                if (written && keep > 1) {
                    val copy = BackupCopies.name(java.time.LocalDateTime.now())
                    if (files.write(copy, "application/json", bytes)) BackupCopies.excess(files.names() + copy, keep).forEach(files::delete)
                }
                written
            }
            if (saved) updateSettings { it.copy(lastBackupAt = System.currentTimeMillis()) }
            if (announce) tell(if (saved) R.string.backup_saved else R.string.backup_failed)
            if (saved) refreshBackupCopies()
        }
    }

    private val _backupCopies = MutableStateFlow<List<String>>(emptyList())

    /** The names of the copies of the backup in the folder, the newest first (read when the page of the backup opens and after a backup). */
    val backupCopies: StateFlow<List<String>> = _backupCopies.asStateFlow()

    fun refreshBackupCopies() {
        val folder = _settings.value.backupFolder
        if (folder.isBlank()) {
            _backupCopies.value = emptyList()
            return
        }
        viewModelScope.launch {
            _backupCopies.value = withContext(Dispatchers.IO) { BackupCopies.newestFirst(SafFolder(getApplication(), folder.toUri()).names()) }
        }
    }

    /** Reads one of the copies and asks which parts of it to put back. */
    fun restoreFromCopy(name: String) {
        val folder = _settings.value.backupFolder
        if (folder.isBlank() || BackupCopies.parse(name) == null) return
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) { SafFolder(getApplication(), folder.toUri()).read(name) }
            val backup = bytes?.let { LayoutBackupCodec.decode(it.decodeToString(), _settings.value) }
            when {
                bytes == null -> tell(R.string.backup_no_file)
                backup == null -> tell(R.string.backup_not_layout)
                else -> _pendingRestore.value = backup
            }
        }
    }

    /** A backup that has been read and waits for the user to say which parts of it to put back. */
    private val _pendingRestore = MutableStateFlow<LayoutBackup?>(null)
    val pendingRestore: StateFlow<LayoutBackup?> = _pendingRestore.asStateFlow()

    fun cancelRestore() {
        _pendingRestore.value = null
    }

    fun applyRestore(parts: Set<RestorePart>) {
        val backup = _pendingRestore.value ?: return
        _pendingRestore.value = null
        restoreBackupParts(backup, parts)
        tell(R.string.backup_imported)
    }

    /**
     * Puts back only [parts] of [backup]: the Start screens with their shortcuts, the settings (all but how the launcher looks, unless
     * the look is chosen too), or the look alone (see [ThemeCodec]).
     */
    fun restoreBackupParts(backup: LayoutBackup, parts: Set<RestorePart>) {
        val current = _settings.value
        var settings = current
        if (RestorePart.Settings in parts) {
            settings = backup.settings
            if (RestorePart.Theme !in parts) settings = ThemeCodec.decode(ThemeCodec.encode(current), settings) ?: settings
        } else if (RestorePart.Theme in parts) {
            settings = ThemeCodec.decode(ThemeCodec.encode(backup.settings), current) ?: current
        }
        if (RestorePart.Start in parts) {
            settings = settings.copy(startPages = backup.pages.size.coerceIn(1, LauncherSettings.MAX_START_PAGES))
            _shortcuts.value = backup.shortcuts
            shortcutStore.save(backup.shortcuts)
            rebuildItems()
        }
        if (settings != current) updateSettings { settings }
        if (RestorePart.Start in parts) {
            val known = _items.value.mapTo(HashSet()) { it.id }
            val pages = StartPages.prune(StartPages.resize(backup.pages, _settings.value.startPages), known)
            _pages.value = pages
            store.savePages(pages)
        }
    }

    /** Sends the layout (see [LayoutBackupCodec]) to another app or person as a file, through the share sheet. */
    fun shareLayout() = shareFile("metrowin-layout.json") { LayoutBackupCodec.encode(currentBackup(), System.currentTimeMillis()) }

    /** The same for the look alone (see [ThemeCodec]): the file the Icons & themes page also reads. */
    fun shareTheme() = shareFile("metrowin-theme.json") { ThemeCodec.encode(_settings.value) }

    private fun shareFile(name: String, text: () -> String) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = java.io.File(app.cacheDir, "shared").apply { mkdirs() }
                    val file = java.io.File(dir, name).apply { writeText(text()) }
                    androidx.core.content.FileProvider.getUriForFile(app, app.packageName + ".files", file)
                }.getOrNull()
            }
            if (uri == null) {
                tell(R.string.backup_failed)
                return@launch
            }
            val send = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { app.startActivity(chooser) }.isFailure) tell(R.string.launch_failed)
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
                else -> _pendingRestore.value = backup
            }
        }
    }

    // --- named layouts of Start

    private val layoutStore = LayoutStore(application)
    private val _layouts = MutableStateFlow(layoutStore.load())
    val layouts: StateFlow<List<SavedLayout>> = _layouts.asStateFlow()

    private fun saveLayouts(list: List<SavedLayout>) {
        _layouts.value = list
        layoutStore.save(list)
    }

    /** Start as it is now (the pages and the shortcuts they hold), as a layout. */
    private fun snapshotLayout(id: String, name: String, hour: Int): SavedLayout {
        val pinned = StartPages.pinnedIds(_pages.value)
        val used = _shortcuts.value.filter { it.id in pinned }
        return SavedLayout(id, name, hour, TileCodec.encodePages(_pages.value), ShortcutCodec.encode(ShortcutCodec.portable(used)))
    }

    /** Keeps Start as it is now under [name]; false when the name is empty or there are already as many layouts as allowed. */
    fun saveLayout(name: String): Boolean {
        val clean = name.trim().take(Layouts.MAX_NAME)
        if (clean.isEmpty() || _layouts.value.size >= Layouts.MAX) return false
        val layout = snapshotLayout(java.util.UUID.randomUUID().toString(), clean, -1)
        saveLayouts(_layouts.value + layout)
        updateSettings { it.copy(activeLayout = layout.id) }
        return true
    }

    /** Brings a layout up to date with what is on Start now. */
    fun resaveLayout(id: String) = saveLayouts(_layouts.value.map { if (it.id == id) snapshotLayout(it.id, it.name, it.hour) else it })

    fun deleteLayout(id: String) {
        saveLayouts(_layouts.value.filterNot { it.id == id })
        if (_settings.value.activeLayout == id) updateSettings { it.copy(activeLayout = "") }
    }

    fun setLayoutHour(id: String, hour: Int) = saveLayouts(_layouts.value.map { if (it.id == id) it.copy(hour = hour.takeIf { h -> h in 0..23 } ?: -1) else it })

    /**
     * Puts a layout on Start. What is on Start now first goes back into the layout that was on, so what was changed during the day is not
     * lost. The shortcuts the layout needs and this phone no longer has come back from it.
     */
    fun applyLayout(id: String) {
        val layout = _layouts.value.firstOrNull { it.id == id } ?: return
        val pages = TileCodec.decodePages(layout.pages) ?: return
        val active = _settings.value.activeLayout
        val kept = _layouts.value.map { if (it.id == active && active != id) snapshotLayout(it.id, it.name, it.hour) else it }
        val have = _shortcuts.value.mapTo(HashSet()) { it.id }
        val back = ShortcutCodec.decode(layout.shortcuts).orEmpty().filter { it.id !in have }
        if (back.isNotEmpty()) {
            _shortcuts.value = _shortcuts.value + back
            shortcutStore.save(_shortcuts.value)
            rebuildItems()
        }
        updateSettings { it.copy(startPages = pages.size.coerceIn(1, LauncherSettings.MAX_START_PAGES), activeLayout = id) }
        val known = _items.value.mapTo(HashSet()) { it.id }
        val shown = StartPages.prune(StartPages.resize(pages, _settings.value.startPages), known)
        _pages.value = shown
        store.savePages(shown)
        saveLayouts(kept)
    }

    /** With the schedule on, the layout whose hour came last takes Start once, when its hour comes; a layout chosen by hand stays until the next hour. */
    private fun applyScheduledLayout() {
        if (!_settings.value.layoutSchedule) return
        val due = Layouts.due(_layouts.value, java.time.LocalTime.now().hour) ?: return
        if (due.id == layoutStore.lastScheduled) return
        layoutStore.lastScheduled = due.id
        if (due.id != _settings.value.activeLayout) applyLayout(due.id)
    }

    // --- a photo for each Start screen

    private fun loadPageBackgrounds() {
        viewModelScope.launch {
            val screens = _settings.value.startPages
            _pageBackgrounds.value = withContext(Dispatchers.IO) {
                (0 until screens).mapNotNull { page -> pageBackgroundStore(page).load()?.let { page to it.asImageBitmap() } }.toMap()
            }
        }
    }

    fun setPageBackground(page: Int, uri: Uri) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { pageBackgroundStore(page).save(uri) }
            if (saved) loadPageBackgrounds() else Toast.makeText(getApplication(), R.string.photo_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun clearPageBackground(page: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { pageBackgroundStore(page).clear() }
            loadPageBackgrounds()
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

    /** The apps of a saved list that this phone does not have (yet), for the page of the backup. */
    private val _savedApps = MutableStateFlow<List<SavedApp>>(emptyList())
    val missingApps: StateFlow<List<SavedApp>> =
        combine(_savedApps, _items) { saved, items -> AppListBackup.missing(saved, items.mapNotNullTo(HashSet()) { it.packageName }) }
            .distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Writes the list of the apps the user installed to [uri]. */
    fun exportApps(uri: Uri) {
        viewModelScope.launch {
            val apps = _items.value.filter { it.removable && it.packageName != null }.map { SavedApp(it.packageName!!, it.label) }.distinctBy { it.packageName }
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    val text = AppListBackup.encode(apps, System.currentTimeMillis())
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                }.isSuccess
            }
            tell(if (written) R.string.apps_backup_saved else R.string.backup_failed)
        }
    }

    /** Reads a saved list of apps; the ones not installed here are then offered, each with its page in the store. */
    fun importApps(uri: Uri) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    AppListBackup.decode(getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readAtMost(MAX_LAYOUT_BYTES) }!!.decodeToString())
                }.getOrNull()
            }
            if (saved == null) {
                tell(R.string.apps_backup_not_list)
            } else {
                _savedApps.value = saved
                tell(R.string.apps_backup_read)
            }
        }
    }

    /** Opens the page of an app in the store, or on the web when the store is not on the phone. */
    fun openStore(packageName: String) {
        val opened = runCatching {
            getApplication<Application>().startActivity(Intent(Intent.ACTION_VIEW, AppListBackup.storeUri(packageName).toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!opened) openUrl(AppListBackup.webStoreUri(packageName))
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
            if (backup == null) tell(R.string.backup_not_layout) else _pendingRestore.value = backup
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

    /** The change the "Undo" message offers now: the last one, or the one before it right after an undo. */
    private val _undo = MutableStateFlow<UndoState?>(null)
    val undo: StateFlow<UndoState?> = _undo.asStateFlow()
    private var undoToken = 0

    /** The changes that can still be undone, the last on top; no more than [LauncherSettings.undoSteps]. */
    private val undoStack = ArrayDeque<UndoState>()
    private val _undoDepth = MutableStateFlow(0)
    val undoDepth: StateFlow<Int> = _undoDepth.asStateFlow()

    /** Keeps Start as it is now, to give back if the change that follows is undone. */
    private fun rememberForUndo(message: Int) {
        undoStack.addLast(UndoState(message, _pages.value, _shortcuts.value, ++undoToken))
        while (undoStack.size > _settings.value.undoSteps.coerceAtLeast(1)) undoStack.removeFirst()
        _undo.value = undoStack.last()
        _undoDepth.value = undoStack.size
    }

    /** Hides the "Undo" message; the changes stay, and the settings can still undo them. */
    fun dismissUndo() {
        _undo.value = null
    }

    /** Forgets every change that could be undone (after a widget is removed, whose place on Start cannot come back). */
    private fun clearUndo() {
        undoStack.clear()
        _undo.value = null
        _undoDepth.value = 0
    }

    /** The "Undo" message was touched: gives back Start as it was before the last change. Nothing once the message is gone. */
    fun undoLast() {
        if (_undo.value != null) undoStep()
    }

    /** Gives back Start as it was before the last change that is still kept, and offers the one before it. */
    fun undoStep() {
        val state = undoStack.removeLastOrNull() ?: return
        _undoDepth.value = undoStack.size
        _undo.value = undoStack.lastOrNull()?.copy(token = ++undoToken)
        // A tile removed with its note, its date or its number comes back with it; a photo already deleted does not.
        _shortcuts.value = state.shortcuts
        shortcutStore.save(state.shortcuts)
        rebuildItems()
        _pages.value = state.pages
        store.savePages(state.pages)
    }

    private companion object {
        const val MAX_COUNT = 99_999L
        const val RELOAD_DEBOUNCE_MS = 400L
        const val SAMPLE_SIDE = 48
        const val PROFILE_REFRESH_MS = 800L
        const val SYNC_MIN_GAP_ON_START_MS = 15 * 60_000L

        /** The Today card tells about the events of the next day. */
        const val TODAY_WINDOW_MS = 24L * 60 * 60 * 1000

        /** A banner is for a notification that came just now, not for one that is a while old. */
        const val BANNER_MAX_AGE_MS = 15_000L

        /** After the launcher comes on screen, this long nothing is announced: the service is reading what is up. */
        const val BANNER_SETTLE_MS = 1_500L
        const val NOTIFICATION_RESULTS = 4

        /** How long a switch of Android takes to show its new state, before the tile reads it again. */
        const val TOGGLE_SETTLE_MS = 700L
        const val SYNC_MIN_GAP_ON_LEAVE_MS = 15 * 60_000L
        const val MAX_LAYOUT_BYTES = 2 * 1024 * 1024
    }
}
