package com.metrowin.launcher

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrowin.launcher.data.AboutInfo
import com.metrowin.launcher.data.AppFilter
import com.metrowin.launcher.data.AppSearch
import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.data.AppRepository
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LiveTileData
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
import com.metrowin.launcher.ui.apps.AppContextMenu
import com.metrowin.launcher.ui.apps.AppListModel
import com.metrowin.launcher.ui.apps.AppListScreen
import com.metrowin.launcher.ui.apps.JumpList
import com.metrowin.launcher.ui.apps.ListHighlight
import com.metrowin.launcher.ui.apps.MenuState
import com.metrowin.launcher.ui.components.LocalIconLoader
import com.metrowin.launcher.ui.anim.LaunchedWhileStarted
import com.metrowin.launcher.ui.anim.LocalPowerSaving
import com.metrowin.launcher.ui.anim.rememberTapFeedback
import com.metrowin.launcher.ui.components.TurnstileHost
import com.metrowin.launcher.ui.gesture.BackGestureHandler
import com.metrowin.launcher.ui.gesture.EdgePageBack
import com.metrowin.launcher.ui.gesture.InstantBack
import com.metrowin.launcher.ui.gesture.LauncherPager
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
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.settings.SettingsSection
import com.metrowin.launcher.ui.start.AppCell
import com.metrowin.launcher.ui.start.AddMenu
import com.metrowin.launcher.ui.start.ClockHeader
import com.metrowin.launcher.ui.start.EditBar
import com.metrowin.launcher.ui.start.ExitRequest
import com.metrowin.launcher.ui.start.FolderCell
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
    val lockPhoto by vm.lockPhoto.collectAsStateWithLifecycle()
    val tutorialDone by vm.tutorialDone.collectAsStateWithLifecycle()
    // The tour shows on the first run, and again whenever it is asked for in the settings.
    var tutorialReplay by remember { mutableStateOf(false) }
    val tutorialVisible = !tutorialDone || tutorialReplay
    val tutorialPager = rememberPagerState { Tutorial.steps.size }
    val homePress by vm.homePresses.collectAsStateWithLifecycle()
    val notifications by vm.notifications.collectAsStateWithLifecycle()
    val badges by vm.badges.collectAsStateWithLifecycle()
    val peeks by vm.peeks.collectAsStateWithLifecycle()
    val liveInfo by vm.liveInfo.collectAsStateWithLifecycle()
    val notificationAccess by vm.notificationAccess.collectAsStateWithLifecycle()
    val torchOn by vm.torchOn.collectAsStateWithLifecycle()

    var editingKey by remember { mutableStateOf<String?>(null) }
    var openFolderId by remember { mutableStateOf<String?>(null) }
    val cellsByPage = remember(pages, items, openFolderId) { pages.map { buildStartCells(it, items, openFolderId) } }
    val appList = remember(sections) { AppListModel(sections) }
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
    val battery by vm.battery.collectAsStateWithLifecycle()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val liveNow = remember(live, torchOn, weather, battery, nowPlaying) { live.copy(torchOn = torchOn, weather = weather, battery = battery, nowPlaying = nowPlaying) }
    val appContext = LocalContext.current
    val screenWidthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value }
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
    val tapFeedback = rememberTapFeedback(settings.tapSound, settings.tapHaptics)
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
    var weatherOpen by remember { mutableStateOf(false) }
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
    // The tiles picked so far while several are chosen for a folder (null: not choosing), and the app being put in one.
    var folderPick by remember { mutableStateOf<Set<String>?>(null) }
    var folderChooserFor by remember { mutableStateOf<AppItem?>(null) }
    var query by remember { mutableStateOf("") }
    var settingsSection by remember { mutableStateOf<SettingsSection?>(null) }
    val searchResults = remember(query, listItems) { AppSearch.filter(listItems, query) }

    // What the search finds besides apps. The settings and the web offer are known at once; the contacts follow
    // when the typing pauses, so each key does not query the contacts.
    var globalResults by remember { mutableStateOf(GlobalResults.Empty) }
    LaunchedEffect(query, settings.searchContacts, settings.searchSettings, settings.searchWeb) {
        if (query.isBlank()) {
            globalResults = GlobalResults.Empty
            return@LaunchedEffect
        }
        globalResults = GlobalResults(
            settings = if (settings.searchSettings) {
                GlobalSearch.matchSettings(query) { def -> appContext.getString(def.label) }
            } else {
                emptyList()
            },
            webQuery = query.trim().takeIf { settings.searchWeb },
        )
        if (settings.searchContacts) {
            delay(CONTACT_SEARCH_DELAY_MS)
            globalResults = globalResults.copy(contacts = vm.searchContacts(query))
        }
    }

    // The Action Center. The panel is one state that follows the finger, whatever starts the pull.
    val panel = rememberPullPanel()
    var quickStates by remember { mutableStateOf<Map<QuickAction, Boolean?>>(emptyMap()) }

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
        if (instant) panel.snapTo(0f) else panel.close()
        returnToStart(pagerState, startScrolls.take(pages.size), instant)
    }

    // A section left open when the settings close must not be half open the next time.
    LaunchedEffect(settingsOpen) { if (!settingsOpen) sectionAway.snapTo(1f) }

    // Leaving the app list puts its keyboard away.
    val focus = LocalFocusManager.current
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
            widgetPickerOpen -> InstantBack { widgetPickerOpen = false }
            siteDialogOpen -> InstantBack { siteDialogOpen = false }
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

    /** Opens an app, or one of the launcher's own pages. [startKey] is the tile it was opened from, if any. */
    fun open(item: AppItem, startKey: String?) {
        if (launching) return
        tapFeedback()
        // The torch tile switches the light and goes nowhere: the tiles do not turn away.
        if ((item.launch as? Launch.Internal)?.route == AppRepository.ROUTE_TORCH) {
            vm.activateQuickAction(QuickAction.Flashlight)
            return
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

    fun togglePin(item: AppItem) {
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
    fun perform(action: GestureAction) {
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
        }
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
                vm.launch(AppItem("search:web", command.query, Launch.Link(GlobalSearch.webUrl(command.query))))
            }
            is VoiceCommand.Call -> scope.launch {
                val contact = vm.searchContacts(command.name).firstOrNull()
                if (contact == null) {
                    voice.problem(R.string.voice_no_contact, command.name)
                } else {
                    voice.close()
                    vm.launch(AppItem("search:contact", contact.name, Launch.Link(contact.lookupUri)))
                }
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
                    tapFeedback()
                    openFolderId = if (openFolderId == cell.folder.id) null else cell.folder.id
                }
                cell is AppCell -> open(cell.item, cell.key)
            }
        },
        onLongClick = { cell ->
            if (!launching && folderPick == null) {
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
        onRename = { cell -> renamingFolderId = cell.folder.id },
        onMove = vm::moveTo,
        onMoveAfter = vm::moveAfter,
        onMerge = { dragged, onto ->
            vm.merge(dragged, onto)
            editingKey = null
        },
        onBackgroundTap = { if (folderPick == null) editingKey = null },
        onTileBounds = { bounds ->
            tapBounds = android.graphics.Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
        },
        onDoubleTap = if (settings.doubleTapAction != GestureAction.None) ({ perform(settings.doubleTapAction) }) else null,
        onNextPage = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(appsPage)) } },
    )

    // Pull down from the top of Start, or from the top edge of any page, to open the Action Center.
    val pullBlocked = kidsMode || voice.isOpen || lockPreviewOpen || launching || settingsOpen || weatherOpen || menu != null || jumpOpen || editingKey != null ||
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
        while (actionCenterShown) {
            quickStates = QuickAction.entries.associateWith(vm::quickActionState)
            delay(1_000)
        }
    }

    val autoAccentIndex by vm.autoAccentIndex.collectAsStateWithLifecycle()
    val accentIndex = if (settings.autoAccent) autoAccentIndex ?: settings.accentIndex else settings.accentIndex
    val accent = if (settings.customAccent != 0) Color(settings.customAccent) else MetroAccents[accentIndex.coerceIn(MetroAccents.indices)]
    val dark = rememberIsDark(settings)
    // Modern Droid takes the colors of the wallpaper (Android 12 and later), or builds its palette from the chosen color.
    val droid = if (settings.themeStyle == ThemeStyle.Droid) {
        remember(settings.dynamicColor, accent, context) {
            (if (settings.dynamicColor) Palettes.fromSystem(context) else null) ?: Palettes.fromSeed(accent)
        }
    } else {
        null
    }

    val powerSaving by vm.powerSaving.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalIconLoader provides vm.icons, LocalWidgetHost provides vm.widgets, LocalPowerSaving provides powerSaving) {
        MetroTheme(accent = accent, dark = dark, reduceMotion = reduceMotion, highContrast = settings.highContrast, style = settings.themeStyle, droid = droid, oled = settings.oledMode) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pageBackground()
                    .onSizeChanged { panel.travelPx = it.height.toFloat() }
                    .pullDownFromTop(panel, edgeZone, enabled = !pullBlocked && settings.edgePull)
                    .twoFingerSwipe(
                        enabled = !pullBlocked && (settings.twoFingerDownAction != GestureAction.None || settings.twoFingerUpAction != GestureAction.None),
                        thresholdPx = with(density) { TWO_FINGER_SWIPE.toPx() },
                        onDown = { perform(settings.twoFingerDownAction) },
                        onUp = { perform(settings.twoFingerUpAction) },
                    ),
            ) {
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
                            background = startBackground,
                            enterToken = enterToken,
                            exitRequest = exitRequest,
                            editingKey = editingKey,
                            scrollState = startScrolls[page],
                            actions = startActions,
                            pullConnection = pullConnections[page],
                            parallax = settings.parallax,
                            pickedKeys = folderPick,
                            header = if (settings.showClock && page == 0) ({ ClockHeader() }) else null,
                        )
                    },
                    apps = {
                        AppListScreen(
                            model = appList,
                            listState = listState,
                            replayToken = listReplay,
                            exiting = listExiting,
                            onLaunch = { item -> open(item, null) },
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
                        )
                    },
                )

                menu?.let { state ->
                    AppContextMenu(
                        state = state,
                        pinned = state.item.id in pinnedIds,
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
                            onAdd = { addMenuOpen = true },
                            onFolder = {
                                folderPick = setOf(cell.entryId)
                                editingKey = null
                            },
                            style = (cell as? AppCell)?.tile?.style,
                            onStyle = {
                                val tile = cell as? AppCell ?: return@EditBar
                                vm.setStyle(tile.entryId, tile.tile.style.next(cycleAvailable = tile.item.live == LiveKind.Photos))
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
                    ActionCenter(
                        slots = QuickAction.slots(settings.quickActions),
                        states = quickStates,
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
                            } else if (action == QuickAction.Recents) {
                                // The panel goes first, so that the recent apps do not open behind it.
                                panel.close()
                                perform(GestureAction.Recents)
                            } else {
                                vm.activateQuickAction(action)
                                if (action != QuickAction.Flashlight) panel.close()
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
                        onClearAll = vm::clearNotifications,
                        onTurnOnNotificationAccess = vm::openNotificationAccessSettings,
                        onClose = panel::close,
                    )
                }

                TurnstileHost(show = weatherOpen, away = weatherAway) {
                    WeatherScreen(
                        weather = weather,
                        place = settings.weatherCity,
                        onRefresh = { vm.refreshWeather(force = true) },
                        onChooseCity = {
                            weatherOpen = false
                            settingsSection = SettingsSection.LiveTiles
                            settingsOpen = true
                        },
                    )
                }

                TurnstileHost(show = settingsOpen, away = settingsAway) {
                    SettingsScreen(
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
                            onManageNotificationAccess = vm::openNotificationAccessSettings,
                            apps = items,
                            onStartKidsCorner = ::enterKidsCorner,
                            about = about,
                            onExportLayout = vm::exportLayout,
                            onImportLayout = vm::importLayout,
                            searchPlaces = vm::searchPlaces,
                            onChoosePlace = vm::choosePlace,
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
                        ),
                        section = settingsSection,
                        onSectionChange = { settingsSection = it },
                        sectionAway = sectionAway,
                    )
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
                        onDismiss = { addMenuOpen = false },
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
                            val accepted = vm.pinSite(title, address, selectedPage)
                            if (accepted) siteDialogOpen = false
                            accepted
                        },
                        onCancel = { siteDialogOpen = false },
                    )
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

                if (lockPreviewOpen) {
                    MetroLockScreen(
                        info = rememberLockInfo(),
                        photo = lockPhoto ?: startBackground,
                        options = LockOptions(settings.lockShowAlarm, settings.lockShowNotifications, settings.lockShowBattery),
                        onUnlock = { lockPreviewOpen = false },
                    )
                }

                voice.state?.let { state ->
                    VoiceScreen(
                        state = state,
                        onRetry = { voiceRequest++ },
                        onCancel = voice::close,
                        onSearchWeb = { query ->
                            voice.close()
                            vm.launch(AppItem("search:web", query, Launch.Link(GlobalSearch.webUrl(query))))
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
