package com.metrowin.launcher

import com.metrowin.launcher.data.Tips
import com.metrowin.launcher.data.SystemCommands
import com.metrowin.launcher.ui.components.asShape
import com.metrowin.launcher.ui.components.LocalIconShape
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.metrowin.launcher.ui.settings.RestoreDialog
import com.metrowin.launcher.data.SearchOperators
import com.metrowin.launcher.ui.start.TileFeatures
import com.metrowin.launcher.ui.start.ChoiceSheet
import com.metrowin.launcher.ui.start.Choice
import com.metrowin.launcher.data.Steps
import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrowin.launcher.data.AboutInfo
import com.metrowin.launcher.data.AppFilter
import com.metrowin.launcher.data.AppCategories
import com.metrowin.launcher.data.AppLabels
import com.metrowin.launcher.data.AppLock
import com.metrowin.launcher.data.AppUsage
import com.metrowin.launcher.data.NewApps
import com.metrowin.launcher.model.AppCategory
import com.metrowin.launcher.data.AppSearch
import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.data.AppRepository
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.CONNECTION_DEVICES_EVERY
import com.metrowin.launcher.data.ConnectionAccess
import com.metrowin.launcher.data.ConnectionPanel
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.data.GestureAction
import com.metrowin.launcher.ui.start.FolderChoice
import com.metrowin.launcher.ui.start.FolderChooser
import com.metrowin.launcher.ui.start.FolderPickBar
import com.metrowin.launcher.data.StartEntries
import com.metrowin.launcher.data.LaunchPlans
import androidx.compose.ui.platform.LocalView
import com.metrowin.launcher.data.TileGrid
import androidx.compose.ui.platform.LocalWindowInfo
import com.metrowin.launcher.data.SystemSettingDef
import com.metrowin.launcher.data.VoiceCommand
import com.metrowin.launcher.data.VoiceCommands
import com.metrowin.launcher.data.VoiceError
import com.metrowin.launcher.ui.lock.LockOptions
import com.metrowin.launcher.ui.lock.MetroLockScreen
import com.metrowin.launcher.ui.lock.rememberLockInfo
import com.metrowin.launcher.ui.voice.VoiceScreen
import com.metrowin.launcher.ui.voice.rememberVoiceSession
import androidx.core.content.ContextCompat
import com.metrowin.launcher.data.GlobalResults
import com.metrowin.launcher.data.GlobalSearch
import com.metrowin.launcher.data.LauncherSettingMatch
import com.metrowin.launcher.data.TileMatch
import com.metrowin.launcher.data.TileSearch
import com.metrowin.launcher.data.LivePermissions
import com.metrowin.launcher.data.PhoneCalls
import com.metrowin.launcher.data.AppLinks
import com.metrowin.launcher.data.Currencies
import com.metrowin.launcher.data.QuickAnswer
import com.metrowin.launcher.data.Today
import com.metrowin.launcher.data.TodayAction
import com.metrowin.launcher.data.QuickAnswers
import com.metrowin.launcher.data.LocalWidgetHost
import com.metrowin.launcher.data.RecentApps
import com.metrowin.launcher.data.WidgetProvider
import com.metrowin.launcher.data.StartPages
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Folder
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.LiveKind
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.actioncenter.ActionCenter
import com.metrowin.launcher.ui.actioncenter.ActionControls
import com.metrowin.launcher.ui.apps.AppContextMenu
import com.metrowin.launcher.ui.apps.AppListModel
import com.metrowin.launcher.ui.apps.AppListScreen
import com.metrowin.launcher.ui.apps.JumpList
import com.metrowin.launcher.ui.apps.ListHighlight
import com.metrowin.launcher.ui.apps.MenuState
import com.metrowin.launcher.ui.components.LocalIconLoader
import com.metrowin.launcher.ui.anim.LaunchedWhileStarted
import com.metrowin.launcher.ui.anim.LocalPowerSaving
import com.metrowin.launcher.ui.anim.LocalTapFeedback
import com.metrowin.launcher.ui.anim.rememberMetroSounds
import com.metrowin.launcher.ui.anim.rememberTapFeedback
import com.metrowin.launcher.data.MetroSound
import kotlinx.coroutines.flow.drop
import com.metrowin.launcher.ui.components.TurnstileHost
import com.metrowin.launcher.ui.gesture.BackGestureHandler
import com.metrowin.launcher.ui.gesture.EdgePageBack
import com.metrowin.launcher.ui.gesture.InstantBack
import com.metrowin.launcher.ui.gesture.LauncherPager
import com.metrowin.launcher.ui.gesture.PanoramaTitles
import com.metrowin.launcher.ui.gesture.PageDots
import com.metrowin.launcher.ui.gesture.PagerBack
import com.metrowin.launcher.ui.gesture.PanelBack
import com.metrowin.launcher.ui.gesture.TurnstileBack
import com.metrowin.launcher.gestures.LockAccessibilityService
import com.metrowin.launcher.ui.gesture.pullDownConnection
import com.metrowin.launcher.ui.gesture.twoFingerSwipe
import com.metrowin.launcher.ui.gesture.pullDownFromTop
import com.metrowin.launcher.ui.gesture.rememberPullPanel
import com.metrowin.launcher.ui.gesture.returnToStart
import com.metrowin.launcher.ui.kids.KidsHeader
import com.metrowin.launcher.ui.tutorial.TutorialHost
import com.metrowin.launcher.data.Tutorial
import com.metrowin.launcher.ui.settings.SettingsEnv
import com.metrowin.launcher.ui.settings.SettingsIndex
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.settings.SettingsSection
import com.metrowin.launcher.ui.start.AppCell
import com.metrowin.launcher.ui.start.AddMenu
import com.metrowin.launcher.ui.start.ClockHeader
import com.metrowin.launcher.ui.start.EditBar
import com.metrowin.launcher.ui.start.ExitRequest
import com.metrowin.launcher.ui.start.FolderCell
import com.metrowin.launcher.data.Countdown
import com.metrowin.launcher.ui.start.CountdownDialog
import com.metrowin.launcher.ui.start.ChecklistDialog
import com.metrowin.launcher.ui.start.CityPicker
import com.metrowin.launcher.ui.start.AlbumPicker
import com.metrowin.launcher.ui.start.GroupPicker
import com.metrowin.launcher.ui.start.PlaceDialog
import com.metrowin.launcher.ui.pages.Panel
import com.metrowin.launcher.ui.pages.PanelContent
import com.metrowin.launcher.ui.pages.PanelState
import com.metrowin.launcher.ui.pages.rememberNotificationPermission
import com.metrowin.launcher.data.Assistant
import com.metrowin.launcher.ui.actioncenter.ToastBanner
import com.metrowin.launcher.ui.actioncenter.VolumeHudPanel
import com.metrowin.launcher.notifications.NotificationEntry
import com.metrowin.launcher.notifications.rememberMediaInfo
import com.metrowin.launcher.ui.lock.hiddenOnLock
import com.metrowin.launcher.ui.lock.lockOptions
import com.metrowin.launcher.ui.lock.rememberLockStatus
import com.metrowin.launcher.data.AlbumInfo
import com.metrowin.launcher.data.GroupInfo
import com.metrowin.launcher.ui.start.CounterDialog
import com.metrowin.launcher.model.TileData
import com.metrowin.launcher.ui.start.NoteDialog
import com.metrowin.launcher.ui.start.PhotoFrame
import com.metrowin.launcher.ui.start.SettingPicker
import com.metrowin.launcher.ui.start.TileColorSheet
import com.metrowin.launcher.ui.start.UndoBar
import com.metrowin.launcher.ui.start.FolderNameDialog
import com.metrowin.launcher.ui.start.SiteDialog
import com.metrowin.launcher.ui.start.StartActions
import com.metrowin.launcher.ui.start.StartCell
import com.metrowin.launcher.ui.start.WidgetPicker
import com.metrowin.launcher.ui.start.StartScreen
import com.metrowin.launcher.ui.start.buildStartCells
import com.metrowin.launcher.ui.start.isTopLevel
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.weather.WeatherScreen
import com.metrowin.launcher.ui.theme.MetroAccents
import com.metrowin.launcher.ui.theme.AccessibilityPolicy
import com.metrowin.launcher.ui.theme.AccentScope
import com.metrowin.launcher.ui.theme.MetroTheme
import com.metrowin.launcher.ui.theme.pageBackground
import com.metrowin.launcher.ui.theme.Palettes
import com.metrowin.launcher.data.ThemeStyle
import androidx.compose.ui.graphics.Color
import com.metrowin.launcher.ui.theme.rememberIsDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Time for the tiles (or rows) to turn away before the app opens. */
/** A small square in the middle of the screen: where an app grows from when it was not opened from a tile. */
private fun screenCentre(view: android.view.View): android.graphics.Rect {
    val cx = view.width / 2
    val cy = view.height / 2
    val half = (minOf(view.width, view.height) * CENTRE_FRACTION).toInt() / 2
    return android.graphics.Rect(cx - half, cy - half, cx + half, cy + half)
}

private const val CENTRE_FRACTION = 0.15f

/** How far below the status bar a pull from the top edge may start, to open the Action Center from any page. */
private val EDGE_PULL_ZONE = 28.dp

/** How far two fingers move together for the swipe to count. */
private val TWO_FINGER_SWIPE = 96.dp

/** The contacts are looked for once the typing has paused this long. */
private const val CONTACT_SEARCH_DELAY_MS = 220L

/** How many settings of Android, settings of the launcher and tiles of Start the search lists, at most: the best ones. */
private const val MAX_SETTING_RESULTS = 6
private const val MAX_TILE_RESULTS = 5
private const val CURRENCY_DELAY_MS = 350L
private const val HOME_DOUBLE_DELAY_MS = 350L

/**
 * The launcher: a pager with the Start screen and the app list, plus the pages and overlays that sit
 * above both (settings, context menu, jump list, first-run permissions).
 */
@Composable
fun LauncherApp(vm: LauncherViewModel) {
    val items by vm.items.collectAsStateWithLifecycle()
    val sections by vm.sections.collectAsStateWithLifecycle()
    val listItems by vm.listItems.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val pages by vm.pages.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val startBackground by vm.background.collectAsStateWithLifecycle()
    val pageBackgrounds by vm.pageBackgrounds.collectAsStateWithLifecycle()
    val layouts by vm.layouts.collectAsStateWithLifecycle()
    val backupCopies by vm.backupCopies.collectAsStateWithLifecycle()
    val pendingRestore by vm.pendingRestore.collectAsStateWithLifecycle()
    val lockPhoto by vm.lockPhoto.collectAsStateWithLifecycle()
    val tutorialDone by vm.tutorialDone.collectAsStateWithLifecycle()
    // The tour shows on the first run, and again whenever it is asked for in the settings.
    var tutorialReplay by remember { mutableStateOf(false) }
    val tutorialVisible = !tutorialDone || tutorialReplay
    val tutorialPager = rememberPagerState { Tutorial.steps.size }
    val homePress by vm.homePresses.collectAsStateWithLifecycle()
    val notifications by vm.notifications.collectAsStateWithLifecycle()
    val notificationHistory by vm.notificationHistory.collectAsStateWithLifecycle()
    val badges by vm.badges.collectAsStateWithLifecycle()
    val peeks by vm.peeks.collectAsStateWithLifecycle()
    val liveInfo by vm.liveInfo.collectAsStateWithLifecycle()
    val liveReport by vm.liveReport.collectAsStateWithLifecycle()
    val panelControls by vm.controls.collectAsStateWithLifecycle()
    val notificationAccess by vm.notificationAccess.collectAsStateWithLifecycle()
    val torchOn by vm.torchOn.collectAsStateWithLifecycle()

    var editingKey by remember { mutableStateOf<String?>(null) }
    // The "…" of the edit bar: its buttons show their names. It stays as it was left while the launcher is open.
    var editLabels by rememberSaveable { mutableStateOf(false) }
    var openFolderId by remember { mutableStateOf<String?>(null) }
    val cellsByPage = remember(pages, items, openFolderId) { pages.map { buildStartCells(it, items, openFolderId) } }
    val usage by vm.usage.collectAsStateWithLifecycle()
    var category by remember { mutableStateOf<AppCategory?>(null) }
    val categories = remember(listItems, settings.showCategories) {
        if (settings.showCategories) AppCategories.present(listItems) else emptyList()
    }
    LaunchedEffect(categories) { if (category != null && category !in categories) category = null }
    val frequent = remember(usage, listItems, settings.showFrequentApps) {
        if (!settings.showFrequentApps) {
            emptyList()
        } else {
            val byId = listItems.associateBy { it.id }
            AppUsage.top(usage, byId.keys).mapNotNull { byId[it] }
        }
    }
    val fresh = remember(listItems, settings.showNewApps) {
        if (settings.showNewApps) NewApps.recent(listItems, System.currentTimeMillis()) else emptyList()
    }
    val shownSections = remember(sections, listItems, category) {
        category?.let { AppSections.group(AppCategories.filter(listItems, it)) } ?: sections
    }
    val appList = remember(shownSections, frequent, fresh, categories, category, settings.searchToday) {
        // A chosen category shows only its apps, in letters; the groups at the top are for the whole list.
        AppListModel(
            shownSections, if (category == null) frequent else emptyList(), if (category == null) fresh else emptyList(), categories,
            today = settings.searchToday,
        )
    }
    val jumpKeys = remember(sections) { AppSections.jumpKeys(sections) }
    val lastOpened = remember(recents, listItems) { RecentApps.lastVisible(recents, listItems.mapTo(HashSet()) { it.id }) }
    var highlight by remember { mutableStateOf<ListHighlight?>(null) }
    val pinnedIds = remember(pages) { StartPages.pinnedIds(pages) }
    // The entry being edited, when it is one the edit bar can act on (a tile or a folder of the top level).
    val folderChoices = remember(pages) {
        StartPages.all(pages).filterIsInstance<Folder>().map { FolderChoice(it.id, it.name, it.children.size) }
    }
    val selectedCell = remember(cellsByPage, editingKey) {
        editingKey?.let { key -> cellsByPage.flatten().firstOrNull { it.key == key && it.isTopLevel } }
    }
    val selectedPage = selectedCell?.let { StartPages.pageOf(pages, it.entryId) }?.coerceAtLeast(0) ?: 0
    /** The Start screens come first and the app list is the page after the last of them. */
    val appsPage = pages.size

    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val publishedTiles by vm.publishedTiles.collectAsStateWithLifecycle()
    val installedIconPacks by vm.installedIconPacks.collectAsStateWithLifecycle()
    val backupFolderName by vm.backupFolderName.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val missingApps by vm.missingApps.collectAsStateWithLifecycle()
    val todayExtras by vm.today.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val battery by vm.battery.collectAsStateWithLifecycle()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val liveNow = remember(live, torchOn, weather, battery, nowPlaying) { live.copy(torchOn = torchOn, weather = weather, battery = battery, nowPlaying = nowPlaying) }
    val appContext = LocalContext.current
    val screenWidthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value }
    val screenHeightDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp().value }
    val about = remember(appContext) { AboutInfo.of(appContext) }
    val kidsMode = settings.kidsMode
    val kidsCells = remember(settings.kidsApps, items) {
        items.filter { it.id in settings.kidsApps }.map { AppCell(Tile(it.id, TileSize.Medium), it) }
    }
    val kidsScroll = rememberScrollState()

    val appsByPackage = remember(items) {
        buildMap { items.forEach { item -> item.packageName?.let { putIfAbsent(it, item) } } }
    }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val tapFeedback = rememberTapFeedback(settings.tapSound, settings.tapHaptics, settings.vibrationStrength)
    val pagerState = rememberPagerState { pages.size + 1 }
    val startScrolls = List(LauncherSettings.MAX_START_PAGES) { rememberScrollState() }
    // The Start screen last seen: where a new tile is pinned and where Back from the app list returns.
    var lastStartPage by remember { mutableIntStateOf(0) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { if (it < pages.size) lastStartPage = it }
    }
    val listState = rememberLazyListState()

    var menu by remember { mutableStateOf<MenuState?>(null) }
    var jumpOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var addMenuOpen by remember { mutableStateOf(false) }
    var widgetPickerOpen by remember { mutableStateOf(false) }
    /** A widget that has an id and is waiting for the user: to allow it, or to set it up. */
    var pendingWidget by remember { mutableStateOf<Pair<Int, WidgetProvider>?>(null) }
    var siteDialogOpen by remember { mutableStateOf(false) }
    var settingPickerOpen by remember { mutableStateOf(false) }
    var togglePickerOpen by remember { mutableStateOf(false) }
    var groupEditingId by remember { mutableStateOf<String?>(null) }
    // The tile (entry id) whose color is being chosen, the note being written, and the countdown being made or changed.
    var colorPickFor by remember { mutableStateOf<String?>(null) }
    var noteEditingId by remember { mutableStateOf<String?>(null) }
    var countdownEditingId by remember { mutableStateOf<String?>(null) }
    var countdownNew by remember { mutableStateOf(false) }
    // The language of the launcher chosen in the settings (Android 13 and later).
    var language by remember { mutableStateOf(vm.appLanguage()) }
    var counterEditingId by remember { mutableStateOf<String?>(null) }
    var checklistEditingId by remember { mutableStateOf<String?>(null) }
    var cityPickerOpen by remember { mutableStateOf(false) }
    // The Start screen the add menu puts the new tile on: the selected tile's, or the one on screen after a long press on the empty part.
    var addPage by remember { mutableIntStateOf(0) }
    val undoState by vm.undo.collectAsStateWithLifecycle()
    val undoDepth by vm.undoDepth.collectAsStateWithLifecycle()
    var weatherOpen by remember { mutableStateOf(false) }
    // The pages of the tiles that do more than show (timer, news, reminders…), and the choices that make some of the tiles.
    val panels = remember { PanelState() }
    val panelAway = remember { Animatable(1f) }
    var placeDialogOpen by remember { mutableStateOf(false) }
    var albumPickerOpen by remember { mutableStateOf(false) }
    var groupPickerOpen by remember { mutableStateOf(false) }
    val ensureNotifications = rememberNotificationPermission()
    // The newest notification that came while the launcher is on screen (see LauncherViewModel.banners).
    var banner by remember { mutableStateOf<NotificationEntry?>(null) }
    LaunchedEffect(vm) { vm.banners.collect { banner = it } }
    var searchFocus by remember { mutableIntStateOf(0) }
    var voiceRequest by remember { mutableIntStateOf(0) }
    var lockPreviewOpen by remember { mutableStateOf(false) }
    // Where the tile that was tapped is, said by Start just before the tap, for the app that grows out of it.
    var tapBounds by remember { mutableStateOf<android.graphics.Rect?>(null) }
    val decorView = LocalView.current
    // The setting, or Android's own "remove animations": read again whenever the launcher comes back.
    var animatorScale by remember { mutableFloatStateOf(AccessibilityPolicy.systemAnimatorScale(appContext)) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { animatorScale = AccessibilityPolicy.systemAnimatorScale(appContext) }
    val reduceMotion = AccessibilityPolicy.reduceMotion(settings.reduceMotion, animatorScale)
    val voice = rememberVoiceSession()
    var lockServiceEnabled by remember { mutableStateOf(false) }
    var renamingFolderId by remember { mutableStateOf<String?>(null) }
    var renamingAppId by remember { mutableStateOf<String?>(null) }
    // An app that asks for the screen lock, waiting for it: the app and the tile it was opened from.
    var pendingLocked by remember { mutableStateOf<Pair<AppItem, String?>?>(null) }
    // The tiles picked so far while several are chosen for a folder (null: not choosing), and the app being put in one.
    var folderPick by remember { mutableStateOf<Set<String>?>(null) }
    var folderChooserFor by remember { mutableStateOf<AppItem?>(null) }
    var query by remember { mutableStateOf("") }
    var settingsSection by remember { mutableStateOf<SettingsSection?>(null) }
    // Asked from outside (the Action Center over other apps): the settings of the launcher.
    val settingsRequest by vm.settingsRequests.collectAsStateWithLifecycle()
    LaunchedEffect(settingsRequest) {
        if (settingsRequest == 0) return@LaunchedEffect
        settingsSection = null
        settingsOpen = true
        // Met: a turned screen makes the activity again, and it must not open them once more.
        vm.settingsRequestMet()
    }
    // The setting the open section was opened for, when the search took the user there.
    var settingsTarget by remember { mutableStateOf<String?>(null) }
    // The apps are indexed once (and again when they or the counts of the opens change), not read again for every key.
    val appIndex = remember(listItems, usage) {
        AppSearch.index(
            listItems, usage,
            categoryLabel = { appContext.getString(it.label) },
            profileWords = AppSearch.ProfileWords(appContext.getString(R.string.profile_work), appContext.getString(R.string.profile_private)),
        )
    }
    // A word and a colon in front (`app:`, `contact:`, `file:`...) narrows the search to one kind of result.
    val scoped = remember(query, settings.searchOperators) { if (settings.searchOperators) SearchOperators.parse(query) else SearchOperators.Parsed(null, query) }
    val searchResults = remember(scoped, appIndex, listItems) {
        when {
            query.isBlank() -> listItems
            !scoped.allows(SearchOperators.Scope.Apps) -> emptyList()
            scoped.text.isBlank() -> listItems
            else -> appIndex.search(scoped.text)
        }
    }
    val launcherSettingsIndex = remember { SettingsIndex.searchIndex(appContext) }
    val tileIndex = remember(items) { TileSearch.index(items) }
    val recentSearches by vm.recentSearches.collectAsStateWithLifecycle()
    // What the search box answers by itself: a sum, a conversion, a number, a link, a timer or an alarm.
    // A throw of dice or a coin is made again when it is touched.
    var rollSeed by remember { mutableIntStateOf(0) }
    val localAnswers = remember(scoped, settings.searchAnswers, rollSeed) {
        if (settings.searchAnswers && scoped.scope == null) QuickAnswers.of(query) else emptyList()
    }
    // A currency conversion needs the rates: they come once the typing pauses, and only when the user allowed it.
    var currencyAnswer by remember { mutableStateOf<QuickAnswer.Currency?>(null) }
    LaunchedEffect(scoped, settings.searchAnswers, settings.searchCurrency) {
        currencyAnswer = null
        val asked = if (settings.searchAnswers && settings.searchCurrency && scoped.scope == null) Currencies.parse(query) else null
        if (asked != null) {
            delay(CURRENCY_DELAY_MS)
            currencyAnswer = vm.currencyAnswer(asked)
        }
    }
    // A sentence that asks for something (a reminder, an event, a note, a place near, the camera) comes before the plain answers.
    val assistantAnswers = remember(scoped, settings.searchAssistant) {
        if (settings.searchAssistant && scoped.scope == null) Assistant.of(query) else emptyList()
    }
    val commandAnswers = remember(scoped, settings.searchCommands) {
        if (settings.searchCommands && scoped.scope == null) listOfNotNull(SystemCommands.of(query)) else emptyList()
    }
    val answers = commandAnswers + assistantAnswers + (currencyAnswer?.let { listOf(it) + localAnswers } ?: localAnswers)

    // What the search finds besides apps. The settings and the web offer are known at once; the contacts follow
    // when the typing pauses, so each key does not query the contacts.
    var globalResults by remember { mutableStateOf(GlobalResults.Empty) }
    LaunchedEffect(
        scoped, settings.searchContacts, settings.searchSettings, settings.searchWeb, settings.searchCalendar,
        settings.searchLauncherSettings, settings.searchTiles, tileIndex, settings.searchFileTypes, settings.searchNotifications,
    ) {
        // What is looked for, without the operator in front of it.
        val query = scoped.text
        if (query.isBlank()) {
            globalResults = GlobalResults.Empty
            return@LaunchedEffect
        }
        globalResults = GlobalResults(
            settings = if (settings.searchSettings && scoped.allows(SearchOperators.Scope.Settings)) {
                GlobalSearch.matchSettings(query) { def -> appContext.getString(def.label) }.take(MAX_SETTING_RESULTS)
            } else {
                emptyList()
            },
            launcherSettings = if (settings.searchLauncherSettings && scoped.allows(SearchOperators.Scope.Settings)) {
                launcherSettingsIndex.search(query).take(MAX_SETTING_RESULTS).map { hit ->
                    val place = appContext.getString(hit.section.title)
                    LauncherSettingMatch(
                        id = hit.setting?.id,
                        section = hit.section.name,
                        title = appContext.getString(hit.setting?.title ?: hit.section.title),
                        where = if (hit.setting == null) appContext.getString(hit.section.description) else appContext.getString(R.string.search_launcher_setting_where, place),
                    )
                }
            } else {
                emptyList()
            },
            tiles = if (settings.searchTiles && scoped.allows(SearchOperators.Scope.Tiles)) {
                tileIndex.search(query).take(MAX_TILE_RESULTS).map { TileMatch(it, TileSearch.snippet(it, query)) }
            } else {
                emptyList()
            },
            webQuery = query.trim().takeIf { settings.searchWeb && scoped.allows(SearchOperators.Scope.Web) },
            notifications = if (settings.searchNotifications && scoped.allows(SearchOperators.Scope.Notifications)) vm.searchNotifications(query) else emptyList(),
        )
        val contacts = settings.searchContacts && scoped.allows(SearchOperators.Scope.Contacts)
        val events = settings.searchCalendar && scoped.allows(SearchOperators.Scope.Events)
        val files = settings.searchFileTypes.isNotEmpty() && scoped.allows(SearchOperators.Scope.Files)
        if (contacts) {
            delay(CONTACT_SEARCH_DELAY_MS)
            globalResults = globalResults.copy(contacts = vm.searchContacts(query))
        }
        if (events) {
            if (!contacts) delay(CONTACT_SEARCH_DELAY_MS)
            globalResults = globalResults.copy(events = vm.searchEvents(query))
        }
        if (files) {
            if (!contacts && !events) delay(CONTACT_SEARCH_DELAY_MS)
            globalResults = globalResults.copy(files = vm.searchFiles(query))
        }
    }

    // The Action Center. The panel is one state that follows the finger, whatever starts the pull.
    val panel = rememberPullPanel()
    // The glide of the Action Center as it comes down and goes, and the chime of a banner, when the sounds are on.
    val metroSound = rememberMetroSounds(settings.metroSounds)
    LaunchedEffect(panel) {
        snapshotFlow { panel.isShown }.drop(1).collect { metroSound(if (it) MetroSound.Open else MetroSound.Close) }
    }
    LaunchedEffect(banner?.key) { if (banner != null) metroSound(MetroSound.Notify) }
    // The status bar of Android out of the way on Start; a swipe from the top edge brings it back for a moment.
    val hostView = LocalView.current
    DisposableEffect(settings.hideStatusBar) {
        val window = (appContext as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, hostView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (settings.hideStatusBar) controller.hide(WindowInsetsCompat.Type.statusBars()) else controller.show(WindowInsetsCompat.Type.statusBars())
        }
        onDispose { }
    }
    // The sliders show how things are when the Action Center opens.
    // The flashlight lighting or going out shows or takes away its strength slider.
    LaunchedEffect(panel.isShown, torchOn) { if (panel.isShown) vm.refreshControls() }
    var quickStates by remember { mutableStateOf<Map<QuickAction, Boolean?>>(emptyMap()) }
    var connectionPanel by remember { mutableStateOf(ConnectionPanel()) }
    var screenTimeout by remember { mutableStateOf<Int?>(null) }

    // The settings page, and the section on top of it, swing in and out like a turnstile; the back gesture
    // moves them with the finger.
    val settingsAway = remember { Animatable(1f) }
    val sectionAway = remember { Animatable(1f) }
    val weatherAway = remember { Animatable(1f) }

    // Animation state. enterToken replays the tiles' entrance, listReplay the rows' entrance.
    var enterToken by remember { mutableIntStateOf(0) }
    var listReplay by remember { mutableIntStateOf(0) }
    var exitRequest by remember { mutableStateOf<ExitRequest?>(null) }
    var listExiting by remember { mutableStateOf(false) }
    var launching by remember { mutableStateOf(false) }
    var launched by remember { mutableStateOf(false) }
    var cameBackFromBackground by remember { mutableStateOf(false) }

    fun comeBack() {
        launching = false
        launched = false
        exitRequest = null
        listExiting = false
        query = ""
        panel.snapTo(0f)
        enterToken++
        listReplay++
    }

    // Returning from an app: everything swings back in, as on Windows Phone.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        cameBackFromBackground = true
        vm.onStop()
        vm.backupIfDue()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        vm.onStart()
        lockServiceEnabled = LockAccessibilityService.isEnabled(appContext)
        if (cameBackFromBackground) {
            cameBackFromBackground = false
            comeBack()
        }
    }
    // An app that opened over us without stopping us (a dialog, a floating window) still ends up here.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (launched) comeBack() }

    // An open folder that no longer exists (its last apps were removed).
    LaunchedEffect(pages) {
        if (openFolderId != null && StartPages.all(pages).none { it.id == openFolderId }) openFolderId = null
    }

    // Pressing Home goes back to the top of Start. From another app nothing is on screen yet, so everything
    // jumps there before the tiles swing in; from inside the launcher the panel, the page and the scroll
    // glide back together, and a finger on the screen takes over.
    LaunchedEffect(homePress) {
        if (homePress.count == 0) return@LaunchedEffect
        val instant = homePress.fromBackground
        editingKey = null
        menu = null
        jumpOpen = false
        renamingFolderId = null
        openFolderId = null
        query = ""
        if (settingsOpen) {
            settingsOpen = false
            comeBack()
        }
        if (weatherOpen) {
            weatherOpen = false
            comeBack()
        }
        panels.close()
        placeDialogOpen = false
        albumPickerOpen = false
        groupPickerOpen = false
        if (instant) panel.snapTo(0f) else panel.close()
        returnToStart(pagerState, startScrolls.take(pages.size), instant)
    }

    // A section left open when the settings close must not be half open the next time.
    LaunchedEffect(settingsOpen) { if (!settingsOpen) sectionAway.snapTo(1f) }

    // Leaving the app list puts its keyboard away; so does going to another app, so the keyboard is not still there when the launcher comes back.
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        focus.clearFocus(force = true)
        keyboard?.hide()
    }
    LaunchedEffect(pagerState, focus) {
        snapshotFlow { pagerState.currentPage }.collect { if (it != appsPage) focus.clearFocus() }
    }

    fun closeWeather() {
        weatherOpen = false
        comeBack()
    }

    fun closeSettings() {
        settingsOpen = false
        settingsSection = null
        comeBack()
    }

    // Back follows the finger on the layers that can: the Action Center slides up, the settings swing away,
    // the app list pans back to Start. The others just close when the gesture is done.
    BackGestureHandler {
        when {
            // Back goes back through the tour; on the first page it leaves a replay and does nothing on the first run.
            tutorialVisible -> InstantBack {
                if (tutorialPager.currentPage > 0) {
                    scope.launch { tutorialPager.animateScrollToPage(tutorialPager.currentPage - 1) }
                } else {
                    tutorialReplay = false
                }
            }
            voice.isOpen -> InstantBack { voice.close() }
            lockPreviewOpen -> InstantBack { lockPreviewOpen = false }
            panel.isShown -> PanelBack(panel)
            placeDialogOpen -> InstantBack { placeDialogOpen = false }
            albumPickerOpen -> InstantBack { albumPickerOpen = false }
            groupPickerOpen -> InstantBack { groupPickerOpen = false }
            panels.isOpen -> TurnstileBack(panelAway, scope) { panels.close() }
            widgetPickerOpen -> InstantBack { widgetPickerOpen = false }
            siteDialogOpen -> InstantBack { siteDialogOpen = false }
            noteEditingId != null -> InstantBack { noteEditingId = null }
            countdownEditingId != null || countdownNew -> InstantBack {
                countdownEditingId = null
                countdownNew = false
            }
            settingPickerOpen -> InstantBack { settingPickerOpen = false }
            cityPickerOpen -> InstantBack { cityPickerOpen = false }
            counterEditingId != null -> InstantBack { counterEditingId = null }
            checklistEditingId != null -> InstantBack { checklistEditingId = null }
            colorPickFor != null -> InstantBack { colorPickFor = null }
            addMenuOpen -> InstantBack { addMenuOpen = false }
            renamingFolderId != null -> InstantBack { renamingFolderId = null }
            folderChooserFor != null -> InstantBack { folderChooserFor = null }
            jumpOpen -> InstantBack { jumpOpen = false }
            menu != null -> InstantBack { menu = null }
            weatherOpen -> TurnstileBack(weatherAway, scope, ::closeWeather)
            // Back closes the open section first, and the settings after it.
            settingsOpen && settingsSection != null -> TurnstileBack(sectionAway, scope) { settingsSection = null }
            settingsOpen -> TurnstileBack(settingsAway, scope, ::closeSettings)
            kidsMode -> InstantBack { } // only a grown-up, with the screen lock, gets out of the Kid's Corner
            folderPick != null -> InstantBack { folderPick = null }
            editingKey != null -> InstantBack { editingKey = null }
            openFolderId != null -> InstantBack { openFolderId = null }
            query.isNotEmpty() -> InstantBack { query = "" }
            pagerState.currentPage >= appsPage -> PagerBack(pagerState, scope, appsPage - 1)
            // On Start, Back has nowhere to go. A swipe from the edge, which the system takes for Back, turns the
            // page instead: from the right to the next one, from the left to the previous one.
            settings.edgeBackPan -> EdgePageBack(pagerState, scope, pages.size + 1)
            else -> InstantBack { }
        }
    }

    val activity = LocalActivity.current
    val context = LocalContext.current
    val exitTitle = stringResource(R.string.kids_exit_title)

    fun enterKidsCorner() {
        vm.updateSettings { it.copy(kidsMode = true) }
        settingsOpen = false
        settingsSection = null
        // Screen pinning, when the user has turned it on, keeps the child from leaving through Recents.
        runCatching { activity?.startLockTask() }
        comeBack()
    }

    fun exitKidsCorner() {
        vm.updateSettings { it.copy(kidsMode = false) }
        runCatching { activity?.stopLockTask() }
        comeBack()
    }

    val widgets = vm.widgets

    fun finishWidget(id: Int, provider: WidgetProvider) {
        vm.addWidget(id, provider, lastStartPage.coerceIn(0, pages.size - 1))
        pendingWidget = null
    }

    fun abandonWidget() {
        pendingWidget?.let { (id, _) -> widgets.delete(id) }
        pendingWidget = null
    }

    /** The widget is allowed: if it needs setting up, its own screen comes first. */
    fun configureOrFinish(id: Int, provider: WidgetProvider) {
        val setup = activity
        if (widgets.info(id)?.configure != null && setup != null) {
            pendingWidget = id to provider
            widgets.configure(setup, id, MainActivity.REQUEST_CONFIGURE_WIDGET)
        } else {
            finishWidget(id, provider)
        }
    }

    val bindWidget = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pending = pendingWidget ?: return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK) configureOrFinish(pending.first, pending.second) else abandonWidget()
    }

    fun addWidget(provider: WidgetProvider) {
        val id = widgets.allocateId()
        if (widgets.bindIfAllowed(id, provider.component)) {
            configureOrFinish(id, provider)
        } else {
            // Android asks the user whether this launcher may show the widget.
            pendingWidget = id to provider
            bindWidget.launch(widgets.bindRequest(id, provider.component))
        }
    }

    // The widget's own setup screen answers through the activity.
    DisposableEffect(activity) {
        val main = activity as? MainActivity
        main?.onWidgetConfigured = { resultCode ->
            val pending = pendingWidget
            if (pending != null) {
                if (resultCode == Activity.RESULT_OK) finishWidget(pending.first, pending.second) else abandonWidget()
            }
        }
        onDispose { main?.onWidgetConfigured = null }
    }

    // Widgets only update while the launcher listens.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { widgets.startListening() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { widgets.stopListening() }

    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) vm.pinContact(uri, lastStartPage.coerceIn(0, pages.size - 1))
    }

    // A speed dial: the picker returns one number; the phone permission is asked once, and without it the tile opens the dialer.
    val speedDialPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    // The birthdays of the contacts are read with the contacts permission, asked once when that tile is put on Start.
    val birthdaysPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshLive(force = true) }
    val pickNumber = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            vm.pinSpeedDial(uri, lastStartPage.coerceIn(0, pages.size - 1))
            if (!LivePermissions.isGranted(appContext, PhoneCalls.permission)) speedDialPermission.launch(PhoneCalls.permission)
        }
    }

    val confirmCredentials = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) exitKidsCorner()
    }

    /** A grown-up leaves by confirming the screen lock. A phone without one cannot ask, so it just leaves. */
    fun requestExitKidsCorner() {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        @Suppress("DEPRECATION") // still the simple way to ask for the screen lock, and it works on every version we support
        val intent = keyguard?.takeIf { it.isDeviceSecure }?.createConfirmDeviceCredentialIntent(exitTitle, null)
        if (intent != null) confirmCredentials.launch(intent) else exitKidsCorner()
    }

    /** Opens an app, or one of the launcher's own pages, with no questions asked. */
    fun openNow(item: AppItem, startKey: String?) {
        if (launching) return
        // The torch tile switches the light and goes nowhere: the tiles do not turn away.
        if ((item.launch as? Launch.Internal)?.route == AppRepository.ROUTE_TORCH) {
            vm.activateQuickAction(QuickAction.Flashlight)
            return
        }
        // A note and a countdown open their editor on the spot.
        when ((item.launch as? Launch.Internal)?.route) {
            AppRepository.ROUTE_NOTE -> {
                noteEditingId = item.id
                return
            }
            AppRepository.ROUTE_COUNTDOWN -> {
                countdownEditingId = item.id
                return
            }
            // A counter counts one more where it is; a list opens to be ticked; the clock and the moon only show.
            AppRepository.ROUTE_COUNTER -> {
                vm.countStep(item.id, 1)
                return
            }
            AppRepository.ROUTE_CHECKLIST -> {
                checklistEditingId = item.id
                return
            }
            AppRepository.ROUTE_NONE -> return
            // The tiles with a page of their own: the page opens over Start, nothing leaves.
            AppRepository.ROUTE_TIMER -> {
                panels.open(Panel.Timer(item.id))
                return
            }
            AppRepository.ROUTE_STOPWATCH -> {
                panels.open(Panel.Stopwatch(item.id))
                return
            }
            AppRepository.ROUTE_NEWS -> {
                panels.open(Panel.News)
                return
            }
            AppRepository.ROUTE_FINANCE -> {
                panels.open(Panel.Finance)
                return
            }
            AppRepository.ROUTE_REMINDERS -> {
                panels.open(Panel.Reminders())
                return
            }
            AppRepository.ROUTE_STEPS -> {
                panels.open(Panel.Steps)
                return
            }
            // A switch kept on Start does its thing where it is.
            AppRepository.ROUTE_TOGGLE -> {
                (item.data as? TileData.Toggle)?.let { vm.toggleTile(it.action) }
                return
            }
            AppRepository.ROUTE_RECORDER -> {
                panels.open(Panel.Recorder)
                return
            }
            AppRepository.ROUTE_WALLET -> {
                panels.open(Panel.Wallet)
                return
            }
            AppRepository.ROUTE_WHEREAMI -> {
                panels.open(Panel.WhereAmI)
                return
            }
            AppRepository.ROUTE_PARKING -> {
                panels.open(Panel.Parking)
                return
            }
            AppRepository.ROUTE_DIALGRID -> {
                panels.open(Panel.DialGrid)
                return
            }
            AppRepository.ROUTE_PEOPLEHUB -> {
                panels.open(Panel.PeopleHub)
                return
            }
            // An album and a group lead to the apps that hold them.
            AppRepository.ROUTE_ALBUM -> {
                vm.openGallery()
                return
            }
            AppRepository.ROUTE_GROUP -> {
                vm.openContacts()
                return
            }
        }
        launching = true
        val plan = LaunchPlans.decide(settings.launchAnimation, reduceMotion, fromStart = startKey != null)
        if (plan.tilesTurnAway) {
            if (startKey != null) exitRequest = ExitRequest(startKey) else listExiting = true
        }
        // The app grows out of the tile that was touched; from the list, out of the middle of the screen.
        val growFrom = if (plan.expand) tapBounds.takeIf { startKey != null } ?: screenCentre(decorView) else null
        tapBounds = null
        scope.launch {
            if (plan.waitMs > 0) delay(plan.waitMs)
            val internal = item.launch as? Launch.Internal
            when {
                internal?.route == AppRepository.ROUTE_KIDS ->
                    if (settings.kidsApps.isEmpty()) {
                        settingsSection = SettingsSection.Kids
                        settingsOpen = true
                    } else {
                        enterKidsCorner()
                    }
                internal?.route == AppRepository.ROUTE_WEATHER -> weatherOpen = true
                internal != null -> {
                    settingsSection = null
                    settingsOpen = true
                }
                vm.launch(item, growFrom = growFrom, anchor = decorView) -> launched = true
                else -> comeBack()
            }
        }
    }

    val lockedAppTitle = stringResource(R.string.app_lock_title)
    val confirmAppLock = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val waiting = pendingLocked
        pendingLocked = null
        if (waiting != null && result.resultCode == Activity.RESULT_OK) openNow(waiting.first, waiting.second)
    }

    /** Opens an app, or one of the launcher's own pages. An app the user locked asks for the screen lock first. */
    fun open(item: AppItem, startKey: String?) {
        if (launching) return
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        if (AppLock.mustConfirm(item, settings.lockedApps, keyguard?.isDeviceSecure == true)) {
            @Suppress("DEPRECATION") // still the simple way to ask for the screen lock, and it works on every version we support
            val intent = keyguard?.createConfirmDeviceCredentialIntent(lockedAppTitle, item.label)
            if (intent != null) {
                pendingLocked = item to startKey
                confirmAppLock.launch(intent)
                return
            }
        }
        openNow(item, startKey)
    }

    // A locked Start says why nothing happens when a tile is held or an app is pinned.
    fun lockedNotice() = Toast.makeText(appContext, R.string.start_locked_notice, Toast.LENGTH_SHORT).show()

    fun togglePin(item: AppItem) {
        if (settings.lockStart) {
            lockedNotice()
            return
        }
        if (item.id in pinnedIds) {
            vm.unpin(item.id)
            return
        }
        val page = lastStartPage.coerceIn(0, pages.size - 1)
        vm.pin(item, page)
        // Show the new tile: go to its Start screen and scroll to the end, where it was added.
        scope.launch {
            pagerState.animateScrollToPage(page)
            delay(120)
            startScrolls[page].animateScrollTo(startScrolls[page].maxValue)
        }
    }

    /** What a gesture does, as the settings chose. */
    fun perform(action: GestureAction, appId: String = "") {
        // Screenshot, power menu, split screen and Android's own panels: the accessibility service does them.
        action.system?.let { system ->
            if (!LockAccessibilityService.perform(system)) {
                Toast.makeText(appContext, R.string.gest_lock_needs_service, Toast.LENGTH_LONG).show()
                vm.openAccessibilitySettings()
            }
            return
        }
        when (action) {
            GestureAction.None -> Unit
            GestureAction.LockScreen -> if (!LockAccessibilityService.lock()) {
                // Locking needs the accessibility service: say so, and take the user to where it is turned on.
                Toast.makeText(appContext, R.string.gest_lock_needs_service, Toast.LENGTH_LONG).show()
                vm.openAccessibilitySettings()
            }
            GestureAction.Recents -> if (!LockAccessibilityService.recents()) {
                // The same service that locks the screen opens the recent apps at once.
                Toast.makeText(appContext, R.string.gest_lock_needs_service, Toast.LENGTH_LONG).show()
                vm.openAccessibilitySettings()
            }
            GestureAction.OpenSettings -> vm.openSystemSettings()
            GestureAction.ActionCenter -> panel.open()
            GestureAction.AppList -> scope.launch { pagerState.animateScrollToPage(appsPage) }
            GestureAction.Torch -> vm.activateQuickAction(QuickAction.Flashlight)
            GestureAction.Camera -> vm.activateQuickAction(QuickAction.Camera)
            GestureAction.Voice -> voiceRequest++
            GestureAction.Search -> {
                scope.launch { pagerState.animateScrollToPage(appsPage) }
                searchFocus++
            }
            GestureAction.OpenApp -> {
                val app = items.firstOrNull { it.id == appId }
                if (app != null) open(app, null) else Toast.makeText(appContext, R.string.gest_app_none, Toast.LENGTH_LONG).show()
            }
            // Done by the service above.
            GestureAction.Screenshot, GestureAction.PowerMenu, GestureAction.SplitScreen,
            GestureAction.AndroidNotifications, GestureAction.AndroidQuickSettings -> Unit
        }
    }

    // Home pressed twice does what the settings say, once Start is back on top.
    LaunchedEffect(homePress) {
        if (!homePress.double || settings.homeDoubleAction == GestureAction.None) return@LaunchedEffect
        delay(HOME_DOUBLE_DELAY_MS)
        perform(settings.homeDoubleAction, settings.homeDoubleApp)
    }

    // A spoken call that waits for the phone permission (asked once, and only when the user turned on "call at once").
    var pendingCall by remember { mutableStateOf<String?>(null) }
    val callPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingCall?.let { vm.dial(it, direct = granted) }
        pendingCall = null
    }

    /** Carries out what was said. The session is closed here, unless there is a problem to show. */
    fun carryOut(heard: List<String>) {
        val label = { def: SystemSettingDef -> appContext.getString(def.label) }
        when (val command = VoiceCommands.best(heard, listItems, label)) {
            is VoiceCommand.OpenApp -> {
                voice.close()
                open(command.app, null)
            }
            is VoiceCommand.OpenSetting -> {
                voice.close()
                vm.launch(AppItem("search:setting", command.setting.label, Launch.Action(command.setting.action)))
            }
            is VoiceCommand.Web -> {
                voice.close()
                vm.launch(AppItem("search:web", command.query, Launch.Link(GlobalSearch.webUrl(command.query, settings.searchEngine))))
            }
            is VoiceCommand.Call -> scope.launch {
                val contact = vm.searchContacts(command.name).firstOrNull()
                if (contact == null) {
                    voice.problem(R.string.voice_no_contact, command.name)
                } else {
                    val number = vm.phoneNumberOf(contact.lookupUri)
                    voice.close()
                    when {
                        // No number to call: the card of the contact is the next best thing.
                        number == null -> vm.launch(AppItem("search:contact", contact.name, Launch.Link(contact.lookupUri)))
                        !settings.voiceCallDirect -> vm.dial(number, direct = false)
                        ContextCompat.checkSelfPermission(appContext, PhoneCalls.permission) == PackageManager.PERMISSION_GRANTED ->
                            vm.dial(number, direct = true)
                        else -> {
                            pendingCall = number
                            callPermission.launch(PhoneCalls.permission)
                        }
                    }
                }
            }
            is VoiceCommand.Clock -> {
                voice.close()
                vm.perform(command.answer)
            }
            is VoiceCommand.Assist -> {
                voice.close()
                val answer = command.answer
                when {
                    // No time was said: the form of the reminders finishes it.
                    answer is QuickAnswer.Reminder && answer.at == null -> panels.open(Panel.Reminders(answer.text))
                    else -> {
                        if (answer is QuickAnswer.Reminder) ensureNotifications()
                        vm.perform(answer)
                    }
                }
            }
            is VoiceCommand.Volume -> {
                voice.close()
                vm.changeVolume(command.change)
            }
            is VoiceCommand.Brightness -> if (vm.changeBrightness(command.change)) voice.close() else voice.problem(R.string.voice_brightness_denied)
            is VoiceCommand.Ringer -> if (vm.trySetRinger(command.mode)) voice.close() else voice.problem(R.string.voice_silent_denied)
            is VoiceCommand.Navigate -> {
                voice.close()
                vm.navigate(command.destination)
            }
            is VoiceCommand.Message -> scope.launch {
                val contact = vm.searchContacts(command.name).firstOrNull()
                if (contact == null) {
                    voice.problem(R.string.voice_no_contact, command.name)
                } else {
                    val number = vm.phoneNumberOf(contact.lookupUri)
                    voice.close()
                    if (number == null) {
                        vm.launch(AppItem("search:contact", contact.name, Launch.Link(contact.lookupUri)))
                    } else {
                        vm.composeMessage(number, command.text)
                    }
                }
            }
            is VoiceCommand.System -> {
                voice.close()
                perform(command.action)
            }
            is VoiceCommand.Torch -> {
                voice.close()
                if (command.on == null || command.on != torchOn) vm.activateQuickAction(QuickAction.Flashlight)
            }
            VoiceCommand.LauncherSettings -> {
                voice.close()
                settingsSection = null
                settingsOpen = true
            }
            VoiceCommand.Weather -> {
                voice.close()
                weatherOpen = true
            }
            VoiceCommand.LockScreen -> {
                voice.close()
                perform(GestureAction.LockScreen)
            }
            VoiceCommand.ActionCenter -> {
                voice.close()
                panel.open()
            }
            VoiceCommand.AppList -> {
                voice.close()
                scope.launch { pagerState.animateScrollToPage(appsPage) }
            }
            VoiceCommand.Start -> {
                voice.close()
                scope.launch { pagerState.animateScrollToPage(lastStartPage.coerceIn(0, pages.size - 1)) }
            }
            is VoiceCommand.NotFound -> voice.problem(R.string.voice_not_found, command.target, webQuery = command.target)
            VoiceCommand.Unclear -> voice.problem(R.string.voice_nothing)
        }
    }
    SideEffect {
        voice.preferOffline = settings.voiceOffline
        voice.onHeard = ::carryOut
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.open() else voice.problem(VoiceError.Permission.message)
    }
    // Voice is asked for by a gesture, the microphone in the search box, or the "try again" of its screen.
    LaunchedEffect(voiceRequest) {
        if (voiceRequest == 0) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) voice.open() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    /** Adds the tile to the picked ones, or takes it out. A folder cannot go into a folder, so only one folder can be picked. */
    fun togglePick(cell: StartCell) {
        val picked = folderPick ?: return
        if (!cell.isTopLevel) return
        val id = cell.entryId
        val folders = StartPages.all(pages).filterIsInstance<Folder>().mapTo(HashSet()) { it.id }
        folderPick = when {
            id in picked -> picked - id
            id in folders && picked.any { it in folders } -> picked
            else -> picked + id
        }
    }

    val startActions = StartActions(
        onClick = { cell ->
            when {
                launching -> Unit
                folderPick != null -> togglePick(cell)
                editingKey != null -> editingKey = if (editingKey == cell.key) null else cell.key
                cell is FolderCell -> {
                    openFolderId = if (openFolderId == cell.folder.id) null else cell.folder.id
                }
                cell is AppCell -> open(cell.item, cell.key)
            }
        },
        onLongClick = { cell ->
            if (settings.lockStart) {
                if (!launching) lockedNotice()
            } else if (!launching && folderPick == null) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                editingKey = cell.key
            }
        },
        onUnpin = { cell ->
            when {
                cell is FolderCell -> vm.releaseFolder(cell.folder.id)
                cell is AppCell && cell.folderId != null -> vm.removeFromFolder(cell.folderId, cell.tile.id)
                else -> vm.unpin(cell.entryId)
            }
            editingKey = null
        },
        onResize = { cell -> vm.resize(cell.entryId) },
        onColor = { cell -> colorPickFor = cell.entryId },
        onRename = { cell -> renamingFolderId = cell.folder.id },
        onMove = vm::moveTo,
        onMoveAfter = vm::moveAfter,
        onMerge = { dragged, onto ->
            vm.merge(dragged, onto)
            editingKey = null
        },
        onBackgroundTap = { if (folderPick == null) editingKey = null },
        onEditContent = { cell ->
            val item = (cell as? AppCell)?.item
            when {
                item == null -> Unit
                item.data is TileData.Counter -> counterEditingId = item.id
                item.data is TileData.Checklist -> checklistEditingId = item.id
                item.targetDay != 0L -> countdownEditingId = item.id
                item.note != null -> noteEditingId = item.id
            }
        },
        onCounterStep = vm::countStep,
        onBackgroundLongPress = if (kidsMode) {
            null
        } else {
            {
                if (!launching && folderPick == null && !tutorialVisible && menu == null && !settingsOpen && !settings.lockStart) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    editingKey = null
                    addPage = lastStartPage.coerceIn(0, pages.size - 1)
                    addMenuOpen = true
                }
            }
        },
        onTileBounds = { bounds ->
            tapBounds = android.graphics.Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
        },
        onDoubleTap = if (settings.doubleTapAction != GestureAction.None) ({ perform(settings.doubleTapAction, settings.doubleTapApp) }) else null,
        onNextPage = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(appsPage)) } },
    )

    // Pull down from the top of Start, or from the top edge of any page, to open the Action Center.
    val pullBlocked = kidsMode || voice.isOpen || lockPreviewOpen || launching || settingsOpen || weatherOpen || panels.isOpen ||
        placeDialogOpen || albumPickerOpen || groupPickerOpen || menu != null || jumpOpen || editingKey != null ||
        folderPick != null || folderChooserFor != null || renamingFolderId != null || tutorialVisible
    val currentPullBlocked by rememberUpdatedState(pullBlocked)
    val pullConnections = List(LauncherSettings.MAX_START_PAGES) { page ->
        remember(panel, page) {
            pullDownConnection(panel, startScrolls[page]) { currentPullBlocked || pagerState.currentPage != page }
        }
    }
    val edgeZone = WindowInsets.statusBars.getTop(density) + with(density) { EDGE_PULL_ZONE.toPx() }

    // While the panel is up, the state of its buttons is read again every second.
    val actionCenterShown = panel.isShown
    LaunchedWhileStarted(actionCenterShown to torchOn) {
        var round = 0
        while (actionCenterShown) {
            quickStates = QuickAction.entries.associateWith(vm::quickActionState)
            screenTimeout = vm.quickActionTimeout(QuickAction.ScreenTimeout)
            // Bluetooth devices are read every few seconds, the Wi-Fi network and list every second.
            connectionPanel = vm.readConnections(connectionPanel, devices = round % CONNECTION_DEVICES_EVERY == 0)
            round++
            delay(1_000)
        }
    }
    val connectionPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val dark = rememberIsDark(settings, weather?.details?.sunrise, weather?.details?.sunset)
    val autoAccentIndex by vm.autoAccentIndex.collectAsStateWithLifecycle()
    val accentIndex = if (settings.autoAccent) autoAccentIndex ?: settings.accentIndex else settings.accentIndex
    // The accent of the night, when there is one, wins over the others while the dark theme is on.
    val accent = if (dark && settings.accentNightIndex >= 0) {
        MetroAccents[settings.accentNightIndex.coerceIn(MetroAccents.indices)]
    } else if (settings.customAccent != 0) {
        Color(settings.customAccent)
    } else {
        MetroAccents[accentIndex.coerceIn(MetroAccents.indices)]
    }
    // Modern Droid takes the colors of the wallpaper (Android 12 and later), or builds its palette from the chosen color.
    val droid = if (settings.themeStyle == ThemeStyle.Droid) {
        remember(settings.dynamicColor, accent, context) {
            (if (settings.dynamicColor) Palettes.fromSystem(context) else null) ?: Palettes.fromSeed(accent)
        }
    } else {
        null
    }

    val powerSaving by vm.powerSaving.collectAsStateWithLifecycle()

    // The Today card is written from what the launcher already holds, and again whenever the list comes up. The calendar and the
    // alarm are read only when the app list page is the one on screen (and not more often than PowerPolicy.todayMaxAgeMs).
    val todayInfo = remember(todayExtras, weather, reminders, listReplay, settings.searchToday, settings.tipOfDay) {
        if (settings.searchToday) {
            Today.info(todayExtras, weather, reminders, System.currentTimeMillis()).let { info ->
                if (settings.tipOfDay) info.copy(tip = Tips.indexFor(java.time.LocalDate.now())) else info
            }
        } else {
            null
        }
    }
    LaunchedEffect(pagerState, appsPage, settings.searchToday) {
        if (!settings.searchToday) return@LaunchedEffect
        snapshotFlow { pagerState.currentPage == appsPage }.distinctUntilChanged().filter { it }.collect { vm.refreshToday() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { if (pagerState.currentPage == appsPage) vm.refreshToday() }

    CompositionLocalProvider(
        LocalIconLoader provides vm.icons, LocalIconShape provides settings.iconShape.asShape(), LocalWidgetHost provides vm.widgets, LocalPowerSaving provides powerSaving,
        LocalTapFeedback provides tapFeedback,
    ) {
        MetroTheme(accent = accent, dark = dark, reduceMotion = reduceMotion, highContrast = settings.highContrast, style = settings.themeStyle, droid = droid, oled = settings.oledMode, textScale = settings.textScale / 100f, boldText = settings.boldText, systemFont = settings.systemFont, tilt = settings.tiltStrength / 100f, speed = settings.animationSpeed / 100f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pageBackground()
                    .onSizeChanged { panel.travelPx = it.height.toFloat() }
                    .pullDownFromTop(panel, edgeZone, enabled = !pullBlocked && settings.edgePull)
                    .twoFingerSwipe(
                        enabled = !pullBlocked && (settings.twoFingerDownAction != GestureAction.None || settings.twoFingerUpAction != GestureAction.None),
                        thresholdPx = with(density) { TWO_FINGER_SWIPE.toPx() },
                        onDown = { perform(settings.twoFingerDownAction, settings.twoFingerDownApp) },
                        onUp = { perform(settings.twoFingerUpAction, settings.twoFingerUpApp) },
                    ),
            ) {
                // The big titles of the pages behind them, moving slower (off until asked for in the settings). A Start photo would hide
                // them, so with one they are drawn over the pages instead, faint enough to read through.
                val panorama = settings.panoramaTitles && !kidsMode
                val panoramaStart = stringResource(R.string.panorama_start)
                val panoramaApps = stringResource(R.string.panorama_apps)
                if (panorama && !settings.hasStartBackground) PanoramaTitles(pagerState, pages.size, panoramaStart, panoramaApps)
                if (kidsMode) {
                    StartScreen(
                        cells = kidsCells,
                        live = LiveTileData(),
                        badges = emptyMap(),
                        columns = TileGrid.columnsFor(screenWidthDp, more = false),
                        background = null,
                        enterToken = enterToken,
                        exitRequest = exitRequest,
                        editingKey = null,
                        scrollState = kidsScroll,
                        actions = startActions,
                        readOnly = true,
                        header = { KidsHeader(onExit = ::requestExitKidsCorner) },
                    )
                } else LauncherPager(
                    startAccent = settings.accentStart,
                    appsAccent = settings.accentApps,
                    state = pagerState,
                    startPages = pages.size,
                    userScrollEnabled = !launching && menu == null && !jumpOpen && !tutorialVisible && folderPick == null,
                    start = { page ->
                        StartScreen(
                            cells = cellsByPage.getOrElse(page) { emptyList() },
                            live = liveNow,
                            badges = badges,
                            peeks = peeks,
                            columns = TileGrid.columnsFor(screenWidthDp, settings.moreTiles),
                            background = pageBackgrounds[page] ?: startBackground,
                            enterToken = enterToken,
                            exitRequest = exitRequest,
                            editingKey = editingKey,
                            scrollState = startScrolls[page],
                            actions = startActions,
                            pullConnection = pullConnections[page],
                            parallax = settings.parallax,
                            pickedKeys = folderPick,
                            framing = PhotoFrame(settings.startPhotoZoom, settings.startPhotoX, settings.startPhotoY),
                            photoFit = settings.startPhotoFit,
                            showNames = settings.showTileNames,
                            spacing = settings.tileSpacing.factor,
                            features = TileFeatures(settings.tileMusicControls, settings.batteryEstimate, settings.calendarAgenda, settings.weatherBlocks, settings.stepGoal, settings.iconTileColors),
                            showGroups = settings.groupTitles,
                            locked = settings.lockStart,
                            topInset = (screenHeightDp * settings.startLowerPercent / 100f).dp,
                            header = if (settings.showClock && page == 0) {
                                {
                                    ClockHeader(
                                        hours = settings.clockHours, showAlarm = settings.clockAlarm, showWeek = settings.clockWeek,
                                        zone = settings.clockZone,
                                        onTimeClick = if (settings.clockTaps) ({ vm.openClock() }) else null,
                                        onDateClick = if (settings.clockTaps) ({ vm.openCalendar() }) else null,
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    },
                    apps = {
                        AppListScreen(
                            model = appList,
                            listState = listState,
                            replayToken = listReplay,
                            exiting = listExiting,
                            onLaunch = { item ->
                                vm.rememberSearch(query)
                                open(item, null)
                            },
                            onLongPress = { item, top, height ->
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                menu = MenuState(item, top, height)
                            },
                            onHeaderClick = { jumpOpen = true },
                            query = query,
                            onQueryChange = { query = it },
                            results = searchResults,
                            highlight = highlight,
                            global = globalResults,
                            searchFocus = searchFocus,
                            onVoice = if (settings.voiceButton) ({ voiceRequest++ }) else null,
                            categories = categories,
                            category = category,
                            onCategory = { category = it },
                            answers = answers,
                            onAnswer = { answer ->
                                if (answer is QuickAnswer.Dice || answer is QuickAnswer.Coin) {
                                    rollSeed++
                                } else if (answer is QuickAnswer.Reminder && answer.at == null) {
                                    // No time was said: the form of the reminders finishes it.
                                    vm.rememberSearch(query)
                                    panels.open(Panel.Reminders(answer.text))
                                } else {
                                    if (answer is QuickAnswer.Reminder) ensureNotifications()
                                    vm.rememberSearch(query)
                                    vm.perform(answer)
                                }
                            },
                            onOpenNotification = { entry ->
                                vm.rememberSearch(query)
                                vm.openNotification(entry)
                            },
                            onOpenSetting = { match ->
                                vm.rememberSearch(query)
                                settingsTarget = match.id
                                settingsSection = SettingsSection.valueOf(match.section)
                                settingsOpen = true
                            },
                            recentSearches = recentSearches.takeIf { settings.rememberSearches }.orEmpty(),
                            onClearRecents = vm::clearSearches,
                            engine = settings.searchEngine,
                            onSearchSubmit = vm::rememberSearch,
                            compact = settings.compactList,
                            onDial = { number -> vm.dial(number, settings.voiceCallDirect && LivePermissions.isGranted(appContext, PhoneCalls.permission)) },
                            today = todayInfo,
                            onToday = { action ->
                                when (action) {
                                    TodayAction.Calendar -> vm.openCalendar()
                                    TodayAction.Weather -> weatherOpen = true
                                    TodayAction.Reminders -> panels.open(Panel.Reminders())
                                    TodayAction.Clock -> vm.openClock()
                                    is TodayAction.Near -> vm.perform(QuickAnswer.NearMe(action.what))
                                }
                            },
                        )
                    },
                )

                if (panorama && settings.hasStartBackground) PanoramaTitles(pagerState, pages.size, panoramaStart, panoramaApps)

                menu?.let { state ->
                    // What the app itself offers on a long press: asked once for each menu that opens.
                    val appShortcuts = remember(state.item.id) { vm.shortcutsOf(state.item) }
                    val lockable = AppLock.canLock(state.item)
                    val needsScreenLock = stringResource(R.string.app_lock_needs_screen_lock)
                    AppContextMenu(
                        state = state,
                        pinned = state.item.id in pinnedIds,
                        shortcuts = appShortcuts,
                        onShortcut = { shortcut ->
                            menu = null
                            vm.startShortcut(shortcut)
                        },
                        onPinShortcut = { shortcut ->
                            menu = null
                            vm.pinAppShortcut(shortcut, lastStartPage.coerceIn(0, pages.size - 1))
                        },
                        onRename = if (!state.item.pinOnly) {
                            {
                                menu = null
                                renamingAppId = state.item.id
                            }
                        } else {
                            null
                        },
                        onResetName = if (state.item.id in settings.appLabels) {
                            {
                                menu = null
                                vm.updateSettings { it.copy(appLabels = it.appLabels - state.item.id) }
                            }
                        } else {
                            null
                        },
                        onInfo = if (state.item.packageName != null) {
                            {
                                menu = null
                                vm.openAppInfo(state.item)
                            }
                        } else {
                            null
                        },
                        onStore = if (state.item.packageName?.let(AppLinks::valid) == true && !state.item.pinOnly) {
                            {
                                menu = null
                                vm.openInStore(state.item)
                            }
                        } else {
                            null
                        },
                        onShare = if (state.item.packageName?.let(AppLinks::valid) == true && !state.item.pinOnly) {
                            {
                                menu = null
                                vm.shareApp(state.item)
                            }
                        } else {
                            null
                        },
                        compact = settings.compactList,
                        locked = if (lockable) state.item.id in settings.lockedApps else null,
                        onToggleLock = {
                            menu = null
                            val keyguard = context.getSystemService(KeyguardManager::class.java)
                            if (state.item.id in settings.lockedApps) {
                                vm.updateSettings { it.copy(lockedApps = it.lockedApps - state.item.id) }
                            } else if (keyguard?.isDeviceSecure == true) {
                                vm.updateSettings { it.copy(lockedApps = it.lockedApps + state.item.id) }
                            } else {
                                Toast.makeText(appContext, needsScreenLock, Toast.LENGTH_LONG).show()
                            }
                        },
                        onPinToggle = {
                            menu = null
                            togglePin(state.item)
                        },
                        onUninstall = {
                            menu = null
                            vm.uninstall(state.item)
                        },
                        onHide = if (AppFilter.canHide(state.item)) {
                            {
                                menu = null
                                vm.updateSettings { it.copy(hiddenApps = it.hiddenApps + state.item.id) }
                            }
                        } else {
                            null
                        },
                        onAddToFolder = if (folderChoices.isNotEmpty()) {
                            {
                                menu = null
                                folderChooserFor = state.item
                            }
                        } else {
                            null
                        },
                        onDismiss = { menu = null },
                    )
                }

                folderPick?.let { picked ->
                    val entries = StartPages.all(pages)
                    FolderPickBar(
                        picked = picked.size,
                        canCreate = StartEntries.canGroup(entries, picked),
                        joining = entries.any { it is Folder && it.id in picked },
                        onCreate = {
                            val made = vm.createFolder(picked)
                            folderPick = null
                            // A folder that was just made gets its name asked for, as on Windows Phone.
                            if (made != null) renamingFolderId = made
                        },
                        onCancel = { folderPick = null },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }

                folderChooserFor?.let { item ->
                    FolderChooser(
                        folders = folderChoices,
                        onPick = { choice ->
                            vm.addToFolder(item.id, choice.id)
                            folderChooserFor = null
                            Toast.makeText(appContext, R.string.folder_added, Toast.LENGTH_SHORT).show()
                        },
                        onCancel = { folderChooserFor = null },
                    )
                }

                if (jumpOpen) {
                    JumpList(
                        keys = jumpKeys,
                        available = appList.letters,
                        hasRecent = lastOpened != null && appList.indexOfApp(lastOpened) != null,
                        onPickRecent = {
                            jumpOpen = false
                            val id = lastOpened
                            val index = id?.let(appList::indexOfApp)
                            if (id != null && index != null) {
                                scope.launch {
                                    // The row goes a little below the top, so the one above it shows too.
                                    listState.scrollToItem((index - 1).coerceAtLeast(0))
                                    listReplay++
                                    highlight = ListHighlight(id, (highlight?.token ?: 0) + 1)
                                }
                            }
                        },
                        onPick = { key ->
                            jumpOpen = false
                            appList.headerIndex[key]?.let { index ->
                                scope.launch {
                                    listState.scrollToItem(index)
                                    listReplay++
                                }
                            }
                        },
                        onDismiss = { jumpOpen = false },
                    )
                }

                if (!kidsMode && !tutorialVisible) {
                    selectedCell?.let { cell ->
                        EditBar(
                            size = cell.size,
                            page = selectedPage,
                            pageCount = pages.size,
                            onSize = { vm.setSize(cell.entryId, it) },
                            onMovePage = { delta ->
                                val target = (selectedPage + delta).coerceIn(0, pages.size - 1)
                                vm.moveToPage(cell.entryId, target)
                                scope.launch { pagerState.animateScrollToPage(target) }
                            },
                            onDone = { editingKey = null },
                            modifier = Modifier.align(Alignment.BottomCenter),
                            onAdd = {
                                addPage = selectedPage
                                addMenuOpen = true
                            },
                            onFolder = {
                                folderPick = setOf(cell.entryId)
                                editingKey = null
                            },
                            onLabels = if (settings.appBar) ({ editLabels = !editLabels }) else null,
                            labels = editLabels,
                            style = (cell as? AppCell)?.tile?.style,
                            sizes = if (settings.largeTiles) TileSize.entries else TileSize.entries - TileSize.Large,
                            onGroup = if (settings.groupTitles) ({ groupEditingId = cell.entryId }) else null,
                            onStyle = {
                                val tile = cell as? AppCell ?: return@EditBar
                                val pictures = tile.item.live == LiveKind.Photos || tile.item.live == LiveKind.Album
                                vm.setStyle(tile.entryId, tile.tile.style.next(cycleAvailable = pictures, collectionAvailable = pictures && settings.collectionTiles))
                            },
                        )
                    }
                }

                if (!kidsMode && pages.size > 1 && !tutorialVisible) {
                    PageDots(
                        pagerState, pages.size + 1,
                        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 6.dp),
                    )
                }

                if (panel.isShown) {
                    AccentScope(settings.accentPanel) { ActionCenter(
                        slots = QuickAction.slots(settings.quickActions),
                        states = quickStates,
                        timeout = screenTimeout,
                        notifications = notifications,
                        appsByPackage = appsByPackage,
                        hasNotificationAccess = notificationAccess,
                        panel = panel,
                        onAction = { action ->
                            if (action == QuickAction.LauncherSettings) {
                                // The settings of the launcher itself: a shortcut to what is also an entry in the app list.
                                panel.close()
                                settingsSection = null
                                settingsOpen = true
                            } else if (action == QuickAction.Screenshot) {
                                // The panel goes first, so that it is not in the picture.
                                panel.close()
                                scope.launch {
                                    delay(SCREENSHOT_DELAY_MS)
                                    perform(GestureAction.Screenshot)
                                }
                            } else if (action == QuickAction.Recents) {
                                // The panel goes first, so that the recent apps do not open behind it.
                                panel.close()
                                perform(GestureAction.Recents)
                            } else {
                                // The flashlight and the quiet hours switch on the spot and the panel stays; anything else opens a screen.
                                val inPlace = vm.activateQuickAction(action)
                                if (inPlace) {
                                    quickStates = quickStates + (action to vm.quickActionState(action))
                                    screenTimeout = vm.quickActionTimeout(QuickAction.ScreenTimeout)
                                } else {
                                    panel.close()
                                }
                            }
                        },
                        connections = connectionPanel,
                        onConnectionList = { action -> if (action == QuickAction.Wifi) vm.scanNetworks() },
                        onAllowConnections = { action ->
                            if (action == QuickAction.Wifi && connectionPanel.wifiAccess == ConnectionAccess.NeedsLocation) {
                                // The permission is given but the location is off.
                                panel.close()
                                vm.activateQuickAction(QuickAction.Location)
                            } else {
                                vm.connectionPermission(action)?.let { connectionPermission.launch(it) }
                            }
                        },
                        onAllSettings = {
                            panel.close()
                            vm.openSystemSettings()
                        },
                        onOpenNotification = { entry ->
                            panel.close()
                            vm.openNotification(entry)
                        },
                        onDismissNotification = vm::dismissNotification,
                        onReply = if (settings.inlineReply) vm::replyToNotification else null,
                        groupConversations = settings.groupConversations,
                        controls = if (settings.actionSliders) {
                            ActionControls(
                                volume = panelControls.volume, ringer = panelControls.ringer, brightness = panelControls.brightness,
                                canSetBrightness = panelControls.canSetBrightness,
                                onVolume = vm::setVolume, onRinger = vm::setRinger, onBrightness = vm::setBrightness,
                                onAllowBrightness = {
                                    panel.close()
                                    vm.openBrightnessAccess()
                                },
                                torch = panelControls.torch.takeIf { torchOn },
                                onTorch = vm::setTorchStrength,
                                ringVolume = panelControls.ringVolume, alarmVolume = panelControls.alarmVolume,
                                onRingVolume = vm::setRingVolume, onAlarmVolume = vm::setAlarmVolume,
                                profiles = if (settings.volumeProfilesShow) settings.volumeProfiles.map { it.name } else emptyList(),
                                onProfile = vm::applyVolumeProfile,
                            )
                        } else {
                            null
                        },
                        onSnooze = vm::snoozeNotification,
                        onMute = if (settings.muteFromPanel) vm::muteApp else null,
                        onAppNotificationSettings = { entry ->
                            panel.close()
                            vm.openAppNotificationSettings(entry)
                        },
                        onClearAll = vm::clearNotifications,
                        onNotificationAction = vm::runNotificationAction,
                        history = notificationHistory,
                        onClearHistory = vm::clearNotificationHistory,
                        onTurnOnNotificationAccess = vm::openNotificationAccessSettings,
                        onClose = panel::close,
                    ) }
                }

                TurnstileHost(show = weatherOpen, away = weatherAway) {
                    val cityWeather by vm.cityWeather.collectAsStateWithLifecycle()
                    LaunchedEffect(weatherOpen, settings.weatherCities) { if (weatherOpen) vm.refreshCities() }
                    WeatherScreen(
                        weather = weather,
                        place = settings.weatherCity,
                        others = settings.weatherCities.map { it to cityWeather[it.key] },
                        blocks = settings.weatherBlocks,
                        onRefresh = { vm.refreshWeather(force = true) },
                        onChooseCity = {
                            weatherOpen = false
                            settingsSection = SettingsSection.LiveTiles
                            settingsOpen = true
                        },
                    )
                }

                // The page of a tile (timer, news, reminders…). It keeps the page it showed while it turns away.
                var lastPanel by remember { mutableStateOf<Panel?>(null) }
                panels.current?.let { lastPanel = it }
                TurnstileHost(show = panels.isOpen, away = panelAway) {
                    lastPanel?.let { page ->
                        PanelContent(
                            panel = page, vm = vm, items = items, settings = settings,
                            onOpenSettings = { section ->
                                panels.close()
                                settingsSection = section
                                settingsOpen = true
                            },
                        )
                    }
                }

                TurnstileHost(show = settingsOpen, away = settingsAway) {
                    AccentScope(settings.accentApps) { SettingsScreen(
                        env = SettingsEnv(
                            powerSaving = powerSaving,
                            settings = settings,
                            onChange = vm::updateSettings,
                            startBackground = startBackground,
                            onPickBackground = vm::setStartBackground,
                            onClearBackground = vm::clearStartBackground,
                            autoAccentIndex = autoAccentIndex,
                            notificationAccess = notificationAccess,
                            liveTilePackages = liveInfo.keys,
                            liveReport = liveReport,
                            onManageNotificationAccess = vm::openNotificationAccessSettings,
                            apps = items,
                            onStartKidsCorner = ::enterKidsCorner,
                            onClearSearches = vm::clearSearches,
                            onResetStart = vm::resetStart,
                            onSaveVolumeProfile = vm::saveVolumeProfile,
                            undoDepth = undoDepth,
                            onUndo = vm::undoStep,
                            language = language,
                            onLanguage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ({ tag ->
                                language = tag
                                vm.setAppLanguage(tag)
                            }) else null,
                            about = about,
                            onExportLayout = vm::exportLayout,
                            onImportLayout = vm::importLayout,
                            backupCopies = backupCopies,
                            onRefreshCopies = vm::refreshBackupCopies,
                            onRestoreCopy = vm::restoreFromCopy,
                            onShareLayout = vm::shareLayout,
                            onShareTheme = vm::shareTheme,
                            onExportApps = vm::exportApps,
                            onImportApps = vm::importApps,
                            layouts = layouts,
                            onSaveLayout = vm::saveLayout,
                            onApplyLayout = vm::applyLayout,
                            onResaveLayout = vm::resaveLayout,
                            onDeleteLayout = vm::deleteLayout,
                            onLayoutHour = vm::setLayoutHour,
                            pageBackgrounds = pageBackgrounds,
                            onPickPageBackground = vm::setPageBackground,
                            onClearPageBackground = vm::clearPageBackground,
                            missingApps = missingApps,
                            onOpenStore = vm::openStore,
                            searchPlaces = vm::searchPlaces,
                            onChoosePlace = vm::choosePlace,
                            onFollowPlace = vm::followPlace,
                            onUnfollowCity = vm::unfollowCity,
                            onUseLocation = vm::useMyLocation,
                            profiles = profiles,
                            onSetProfilePaused = vm::setProfilePaused,
                            onTryVoice = { voiceRequest++ },
                            onShowTutorial = {
                                scope.launch { tutorialPager.scrollToPage(0) }
                                tutorialReplay = true
                            },
                            publishedTiles = publishedTiles,
                            onClearPublishedTile = vm::clearPublishedTile,
                            lockPhoto = lockPhoto,
                            onPickLockPhoto = vm::setLockPhoto,
                            onClearLockPhoto = vm::clearLockPhoto,
                            onApplyLockPhoto = vm::applyLockPhotoToSystem,
                            onOpenDreamSettings = vm::openDreamSettings,
                            onPreviewLock = { lockPreviewOpen = true },
                            iconPacks = installedIconPacks,
                            onExportTheme = vm::exportTheme,
                            onImportTheme = vm::importTheme,
                            backupFolderName = backupFolderName,
                            onPickBackupFolder = vm::setBackupFolder,
                            onClearBackupFolder = vm::clearBackupFolder,
                            onBackupNow = { vm.backupNow() },
                            onRestoreBackup = vm::restoreFromBackupFolder,
                            onSyncNow = { vm.syncNow() },
                            lockServiceEnabled = lockServiceEnabled,
                            onOpenAccessibilitySettings = vm::openAccessibilitySettings,
                            onAddRecentsTile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) vm::requestRecentsTile else null,
                            notificationPackages = badges.keys,
                            onAppNotificationSettings = { pkg -> vm.openAppNotificationSettings(pkg) },
                        ),
                        section = settingsSection,
                        onSectionChange = { settingsSection = it },
                        sectionAway = sectionAway,
                        target = settingsTarget,
                        onTargetChange = { settingsTarget = it },
                    ) }
                }

                // The banner of a new notification, while the launcher is on screen and nothing covers the top of it.
                banner?.let { entry ->
                    if (!panel.isShown && !lockPreviewOpen && !tutorialVisible && !voice.isOpen) {
                        AccentScope(settings.accentPanel) { ToastBanner(
                            entry = entry, app = appsByPackage[entry.packageName], hideText = settings.liveHideText,
                            onOpen = {
                                banner = null
                                vm.openNotification(entry)
                            },
                            onDismiss = { if (banner?.key == entry.key) banner = null },
                            modifier = Modifier.align(Alignment.TopCenter),
                        ) }
                    }
                }

                // The volume panel: over everything but the tutorial, while the launcher is on screen.
                val volumeHud by vm.volumeHud.collectAsStateWithLifecycle()
                volumeHud?.let { hud ->
                    if (!tutorialVisible) {
                        AccentScope(settings.accentPanel) { VolumeHudPanel(
                            hud = hud, onSet = vm::setVolume, onToggle = vm::toggleVolumeHud, onDismiss = vm::dismissVolumeHud,
                            modifier = Modifier.align(Alignment.TopCenter),
                        ) }
                    }
                }

                // What was just done to Start, with a way to take it back; it goes by itself after a few seconds.
                if (!kidsMode && !tutorialVisible && !settingsOpen && !weatherOpen) {
                    undoState?.let { state ->
                        LaunchedEffect(state.token) {
                            delay(UNDO_MS)
                            vm.dismissUndo()
                        }
                        UndoBar(
                            message = stringResource(state.message),
                            onUndo = {
                                vm.undoLast()
                                editingKey = null
                            },
                            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (selectedCell != null) 84.dp else 16.dp),
                        )
                    }
                }

                if (addMenuOpen) {
                    AddMenu(
                        onContact = {
                            addMenuOpen = false
                            pickContact.launch(null)
                        },
                        onSite = {
                            addMenuOpen = false
                            siteDialogOpen = true
                        },
                        onWidget = {
                            addMenuOpen = false
                            widgetPickerOpen = true
                        },
                        onSpeedDial = {
                            addMenuOpen = false
                            pickNumber.launch(Intent(Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                        },
                        onSetting = {
                            addMenuOpen = false
                            settingPickerOpen = true
                        },
                        onNote = {
                            addMenuOpen = false
                            noteEditingId = vm.addNote(addPage)
                        },
                        onCountdown = {
                            addMenuOpen = false
                            countdownNew = true
                        },
                        onCounter = {
                            addMenuOpen = false
                            counterEditingId = vm.addCounter(addPage)
                        },
                        onChecklist = {
                            addMenuOpen = false
                            checklistEditingId = vm.addChecklist(addPage)
                        },
                        onWorldClock = {
                            addMenuOpen = false
                            cityPickerOpen = true
                        },
                        onMoon = {
                            addMenuOpen = false
                            vm.addMoon(addPage)
                        },
                        onBirthdays = {
                            addMenuOpen = false
                            vm.addBirthdays(addPage)
                            if (!LivePermissions.isGranted(appContext, LivePermissions.contacts)) birthdaysPermission.launch(LivePermissions.contacts)
                        },
                        onPlace = {
                            addMenuOpen = false
                            placeDialogOpen = true
                        },
                        onAlbum = {
                            addMenuOpen = false
                            albumPickerOpen = true
                        },
                        onGroup = {
                            addMenuOpen = false
                            groupPickerOpen = true
                        },
                        onTimer = {
                            addMenuOpen = false
                            panels.open(Panel.Timer(vm.addTimer(addPage)))
                        },
                        onStopwatch = {
                            addMenuOpen = false
                            panels.open(Panel.Stopwatch(vm.addStopwatch(addPage)))
                        },
                        onReminders = {
                            addMenuOpen = false
                            vm.addRemindersTile(addPage)
                            ensureNotifications()
                        },
                        onNews = {
                            addMenuOpen = false
                            vm.addNews(addPage)
                        },
                        onFinance = {
                            addMenuOpen = false
                            vm.addFinance(addPage)
                        },
                        onDialGrid = {
                            addMenuOpen = false
                            vm.addDialGrid(addPage)
                            if (!LivePermissions.isGranted(appContext, LivePermissions.contacts)) birthdaysPermission.launch(LivePermissions.contacts)
                        },
                        onPeopleHub = {
                            addMenuOpen = false
                            vm.addPeopleHub(addPage)
                            if (!LivePermissions.isGranted(appContext, LivePermissions.contacts)) birthdaysPermission.launch(LivePermissions.contacts)
                        },
                        onSteps = {
                            addMenuOpen = false
                            vm.addSteps(addPage)
                            Steps.permission()?.takeIf { !LivePermissions.isGranted(appContext, it) }?.let(birthdaysPermission::launch)
                        },
                        onRecorder = {
                            addMenuOpen = false
                            vm.addRecorder(addPage)
                        },
                        onCamera = {
                            addMenuOpen = false
                            vm.addCamera(addPage)
                            if (!LivePermissions.canReadPhotos(appContext)) birthdaysPermission.launch(LivePermissions.photos)
                        },
                        onWallet = {
                            addMenuOpen = false
                            vm.addWallet(addPage)
                        },
                        onWhereAmI = {
                            addMenuOpen = false
                            vm.addWhereAmI(addPage)
                        },
                        onParking = {
                            addMenuOpen = false
                            vm.addParking(addPage)
                        },
                        onAnalogClock = {
                            addMenuOpen = false
                            vm.addAnalogClock(addPage)
                        },
                        onSun = {
                            addMenuOpen = false
                            vm.addSun(addPage)
                        },
                        onToggle = {
                            addMenuOpen = false
                            togglePickerOpen = true
                        },
                        onDismiss = { addMenuOpen = false },
                    )
                }

                if (togglePickerOpen) {
                    val choices = remember(appContext) {
                        QuickAction.toggles.map { Choice(it.id, it.glyph, appContext.getString(it.label)) }
                    }
                    ChoiceSheet(
                        title = stringResource(R.string.add_toggle_title), choices = choices, tagPrefix = "toggle",
                        onPick = { choice ->
                            togglePickerOpen = false
                            QuickAction.fromId(choice.id)?.let { vm.addToggle(it, addPage) }
                        },
                        onDismiss = { togglePickerOpen = false },
                    )
                }

                if (settingPickerOpen) {
                    val defs = remember { GlobalSearch.systemSettings.map { it to appContext.getString(it.label) }.sortedBy { it.second.lowercase() } }
                    SettingPicker(
                        settings = defs,
                        onPick = { def ->
                            settingPickerOpen = false
                            vm.pinSetting(def, addPage)
                        },
                        onDismiss = { settingPickerOpen = false },
                    )
                }

                if (placeDialogOpen) {
                    PlaceDialog(
                        onDone = { name, address ->
                            val accepted = vm.addPlace(name, address, addPage)
                            if (accepted) placeDialogOpen = false
                            accepted
                        },
                        onCancel = { placeDialogOpen = false },
                    )
                }

                if (albumPickerOpen) {
                    var granted by remember { mutableStateOf(LivePermissions.canReadPhotos(appContext)) }
                    val photosPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                        granted = LivePermissions.canReadPhotos(appContext)
                    }
                    val albums by produceState<List<AlbumInfo>?>(null, granted) { value = if (granted) vm.albums() else emptyList() }
                    AlbumPicker(
                        albums = albums, permissionMissing = !granted, onAllow = { photosPermission.launch(LivePermissions.photos) },
                        onPick = { album ->
                            albumPickerOpen = false
                            vm.addAlbum(album, addPage)
                        },
                        onDismiss = { albumPickerOpen = false },
                    )
                }

                if (groupPickerOpen) {
                    var granted by remember { mutableStateOf(LivePermissions.isGranted(appContext, LivePermissions.contacts)) }
                    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
                    val groups by produceState<List<GroupInfo>?>(null, granted) { value = if (granted) vm.groups() else emptyList() }
                    GroupPicker(
                        groups = groups, permissionMissing = !granted, onAllow = { contactsPermission.launch(LivePermissions.contacts) },
                        onPick = { group ->
                            groupPickerOpen = false
                            vm.addGroup(group, addPage)
                        },
                        onDismiss = { groupPickerOpen = false },
                    )
                }

                colorPickFor?.let { id ->
                    val current = StartPages.all(pages).firstOrNull { it.id == id }?.color ?: 0
                    TileColorSheet(
                        current = current,
                        onPick = { color ->
                            vm.setColor(id, color)
                            colorPickFor = null
                        },
                        onDismiss = { colorPickFor = null },
                    )
                }

                noteEditingId?.let { id ->
                    val note = items.firstOrNull { it.id == id }
                    if (note != null) {
                        NoteDialog(
                            initial = note.note.orEmpty(),
                            onDone = { text ->
                                vm.saveNote(id, text)
                                noteEditingId = null
                            },
                            onCancel = { noteEditingId = null },
                        )
                    }
                }

                if (cityPickerOpen) {
                    CityPicker(
                        onPick = { city ->
                            cityPickerOpen = false
                            vm.addWorldClock(city, addPage)
                        },
                        onDismiss = { cityPickerOpen = false },
                    )
                }

                counterEditingId?.let { id ->
                    val counter = items.firstOrNull { it.id == id }
                    if (counter != null) {
                        CounterDialog(
                            initialName = counter.label,
                            count = (counter.data as? TileData.Counter)?.count ?: 0,
                            onDone = { name, reset ->
                                vm.saveCounter(id, name, reset)
                                counterEditingId = null
                            },
                            onCancel = { counterEditingId = null },
                        )
                    }
                }

                checklistEditingId?.let { id ->
                    val list = items.firstOrNull { it.id == id }
                    if (list != null) {
                        ChecklistDialog(
                            initialRaw = (list.data as? TileData.Checklist)?.raw.orEmpty(),
                            onChange = { raw -> vm.saveChecklist(id, raw) },
                            onClose = { checklistEditingId = null },
                        )
                    }
                }

                if (countdownNew || countdownEditingId != null) {
                    val existing = countdownEditingId?.let { id -> items.firstOrNull { it.id == id } }
                    CountdownDialog(
                        initialTitle = existing?.label.orEmpty().takeIf { existing != null } ?: "",
                        initialDay = existing?.targetDay ?: Countdown.defaultDay(java.time.LocalDate.now()),
                        onDone = { title, day ->
                            val editing = countdownEditingId
                            if (editing != null) vm.saveCountdown(editing, title, day) else vm.addCountdown(title, day, addPage)
                            countdownEditingId = null
                            countdownNew = false
                        },
                        onCancel = {
                            countdownEditingId = null
                            countdownNew = false
                        },
                    )
                }

                if (widgetPickerOpen) {
                    WidgetPicker(
                        providers = remember { vm.widgets.providers() },
                        onPick = { provider ->
                            widgetPickerOpen = false
                            addWidget(provider)
                        },
                    )
                }

                if (siteDialogOpen) {
                    SiteDialog(
                        onDone = { title, address ->
                            val accepted = vm.pinSite(title, address, addPage)
                            if (accepted) siteDialogOpen = false
                            accepted
                        },
                        onCancel = { siteDialogOpen = false },
                    )
                }

                if (pendingRestore != null) {
                    RestoreDialog(onDone = vm::applyRestore, onCancel = vm::cancelRestore)
                }

                groupEditingId?.let { id ->
                    val entry = StartPages.all(pages).firstOrNull { it.id == id }
                    if (entry != null) {
                        FolderNameDialog(
                            initial = entry.group,
                            title = R.string.group_title_prompt,
                            maxLength = StartEntries.MAX_GROUP,
                            onDone = { title ->
                                vm.setGroupTitle(id, title)
                                groupEditingId = null
                            },
                            onCancel = { groupEditingId = null },
                        )
                    }
                }

                renamingFolderId?.let { id ->
                    val folder = StartPages.all(pages).firstOrNull { it.id == id } as? Folder
                    if (folder != null) {
                        FolderNameDialog(
                            initial = folder.name,
                            onDone = { name ->
                                vm.renameFolder(id, name)
                                renamingFolderId = null
                            },
                            onCancel = { renamingFolderId = null },
                        )
                    }
                }

                renamingAppId?.let { id ->
                    val app = items.firstOrNull { it.id == id }
                    if (app != null) {
                        FolderNameDialog(
                            initial = app.label,
                            title = R.string.app_rename_title,
                            maxLength = AppLabels.MAX,
                            onDone = { name ->
                                // An empty name brings the original one back.
                                val clean = AppLabels.clean(name)
                                vm.updateSettings { s -> s.copy(appLabels = if (clean == null) s.appLabels - id else s.appLabels + (id to clean)) }
                                renamingAppId = null
                            },
                            onCancel = { renamingAppId = null },
                        )
                    }
                }

                if (lockPreviewOpen) {
                    MetroLockScreen(
                        info = rememberLockInfo(settings.hiddenOnLock()),
                        photo = lockPhoto ?: startBackground,
                        options = settings.lockOptions(),
                        onUnlock = { lockPreviewOpen = false },
                        status = rememberLockStatus(settings),
                        media = rememberMediaInfo(settings.lockShowMusic),
                    )
                }

                voice.state?.let { state ->
                    VoiceScreen(
                        state = state,
                        onRetry = { voiceRequest++ },
                        onCancel = voice::close,
                        onSearchWeb = { query ->
                            voice.close()
                            vm.launch(AppItem("search:web", query, Launch.Link(GlobalSearch.webUrl(query, settings.searchEngine))))
                        },
                    )
                }

                if (tutorialVisible) {
                    TutorialHost(
                        pager = tutorialPager,
                        onFinish = {
                            if (!tutorialDone) vm.onTutorialFinished()
                            tutorialReplay = false
                        },
                        onOpenNotificationAccess = vm::openNotificationAccessSettings,
                        onOpenAccessibility = vm::openAccessibilitySettings,
                    )
                }
            }
        }
    }
}

/** How long the undo bar stays. */
private const val UNDO_MS = 6_000L

/** Long enough for the Action Center to slide away before the screenshot is taken. */
private const val SCREENSHOT_DELAY_MS = 450L
