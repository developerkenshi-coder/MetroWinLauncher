package com.metrowin.launcher

import android.app.Activity
import android.app.KeyguardManager
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
import androidx.compose.runtime.getValue
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
import com.metrowin.launcher.ui.permissions.PermissionsScreen
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
import com.metrowin.launcher.ui.start.WidgetPicker
import com.metrowin.launcher.ui.start.StartScreen
import com.metrowin.launcher.ui.start.buildStartCells
import com.metrowin.launcher.ui.start.isTopLevel
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.weather.WeatherScreen
import com.metrowin.launcher.ui.theme.MetroAccents
import com.metrowin.launcher.ui.theme.MetroTheme
import com.metrowin.launcher.ui.theme.rememberIsDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Time for the tiles (or rows) to turn away before the app opens. */
private const val START_EXIT_MS = 300L
private const val LIST_EXIT_MS = 240L

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
    val permissionsHandled by vm.permissionsHandled.collectAsStateWithLifecycle()
    val homePress by vm.homePresses.collectAsStateWithLifecycle()
    val notifications by vm.notifications.collectAsStateWithLifecycle()
    val badges by vm.badges.collectAsStateWithLifecycle()
    val peeks by vm.peeks.collectAsStateWithLifecycle()
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
    val selectedCell = remember(cellsByPage, editingKey) {
        editingKey?.let { key -> cellsByPage.flatten().firstOrNull { it.key == key && it.isTopLevel } }
    }
    val selectedPage = selectedCell?.let { StartPages.pageOf(pages, it.entryId) }?.coerceAtLeast(0) ?: 0
    /** The Start screens come first and the app list is the page after the last of them. */
    val appsPage = pages.size

    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val backupFolderName by vm.backupFolderName.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val battery by vm.battery.collectAsStateWithLifecycle()
    val liveNow = remember(live, torchOn, weather, battery) { live.copy(torchOn = torchOn, weather = weather, battery = battery) }
    val appContext = LocalContext.current
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
    var lockServiceEnabled by remember { mutableStateOf(false) }
    var renamingFolderId by remember { mutableStateOf<String?>(null) }
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
            !permissionsHandled -> InstantBack { }
            panel.isShown -> PanelBack(panel)
            widgetPickerOpen -> InstantBack { widgetPickerOpen = false }
            siteDialogOpen -> InstantBack { siteDialogOpen = false }
            addMenuOpen -> InstantBack { addMenuOpen = false }
            renamingFolderId != null -> InstantBack { renamingFolderId = null }
            jumpOpen -> InstantBack { jumpOpen = false }
            menu != null -> InstantBack { menu = null }
            weatherOpen -> TurnstileBack(weatherAway, scope, ::closeWeather)
            // Back closes the open section first, and the settings after it.
            settingsOpen && settingsSection != null -> TurnstileBack(sectionAway, scope) { settingsSection = null }
            settingsOpen -> TurnstileBack(settingsAway, scope, ::closeSettings)
            kidsMode -> InstantBack { } // only a grown-up, with the screen lock, gets out of the Kid's Corner
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
        if (startKey != null) exitRequest = ExitRequest(startKey) else listExiting = true
        scope.launch {
            delay(if (startKey != null) START_EXIT_MS else LIST_EXIT_MS)
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
                vm.launch(item) -> launched = true
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
            GestureAction.OpenSettings -> vm.openSystemSettings()
            GestureAction.ActionCenter -> panel.open()
            GestureAction.AppList -> scope.launch { pagerState.animateScrollToPage(appsPage) }
            GestureAction.Torch -> vm.activateQuickAction(QuickAction.Flashlight)
            GestureAction.Camera -> vm.activateQuickAction(QuickAction.Camera)
            GestureAction.Voice -> Unit // set below once the voice screen exists
            GestureAction.Search -> {
                scope.launch { pagerState.animateScrollToPage(appsPage) }
                searchFocus++
            }
        }
    }

    val startActions = StartActions(
        onClick = { cell ->
            when {
                launching -> Unit
                editingKey != null -> editingKey = if (editingKey == cell.key) null else cell.key
                cell is FolderCell -> {
                    tapFeedback()
                    openFolderId = if (openFolderId == cell.folder.id) null else cell.folder.id
                }
                cell is AppCell -> open(cell.item, cell.key)
            }
        },
        onLongClick = { cell ->
            if (!launching) {
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
        onMerge = { dragged, onto ->
            vm.merge(dragged, onto)
            editingKey = null
        },
        onBackgroundTap = { editingKey = null },
        onDoubleTap = if (settings.doubleTapAction != GestureAction.None) ({ perform(settings.doubleTapAction) }) else null,
        onNextPage = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(appsPage)) } },
    )

    // Pull down from the top of Start, or from the top edge of any page, to open the Action Center.
    val pullBlocked = kidsMode || launching || settingsOpen || weatherOpen || menu != null || jumpOpen || editingKey != null ||
        renamingFolderId != null || !permissionsHandled
    val currentPullBlocked by rememberUpdatedState(pullBlocked)
    val pullConnections = List(LauncherSettings.MAX_START_PAGES) { page ->
        remember(panel, page) {
            pullDownConnection(panel, startScrolls[page]) { currentPullBlocked || pagerState.currentPage != page }
        }
    }
    val edgeZone = WindowInsets.statusBars.getTop(density) + with(density) { EDGE_PULL_ZONE.toPx() }

    // While the panel is up, the state of its buttons is read again every second.
    val actionCenterShown = panel.isShown
    LaunchedEffect(actionCenterShown, torchOn) {
        while (actionCenterShown) {
            quickStates = QuickAction.entries.associateWith(vm::quickActionState)
            delay(1_000)
        }
    }

    val autoAccentIndex by vm.autoAccentIndex.collectAsStateWithLifecycle()
    val accentIndex = if (settings.autoAccent) autoAccentIndex ?: settings.accentIndex else settings.accentIndex
    val accent = MetroAccents[accentIndex.coerceIn(MetroAccents.indices)]
    val dark = rememberIsDark(settings)

    CompositionLocalProvider(LocalIconLoader provides vm.icons, LocalWidgetHost provides vm.widgets) {
        MetroTheme(accent = accent, dark = dark) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(LocalMetroColors.current.background)
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
                        columns = LauncherSettings.DEFAULT_COLUMNS,
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
                    userScrollEnabled = !launching && menu == null && !jumpOpen && permissionsHandled,
                    start = { page ->
                        StartScreen(
                            cells = cellsByPage.getOrElse(page) { emptyList() },
                            live = liveNow,
                            badges = badges,
                            peeks = peeks,
                            columns = settings.columns,
                            background = startBackground,
                            enterToken = enterToken,
                            exitRequest = exitRequest,
                            editingKey = editingKey,
                            scrollState = startScrolls[page],
                            actions = startActions,
                            pullConnection = pullConnections[page],
                            parallax = settings.parallax,
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
                        onDismiss = { menu = null },
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

                if (!kidsMode && permissionsHandled) {
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
                            style = (cell as? AppCell)?.tile?.style,
                            onStyle = {
                                val tile = cell as? AppCell ?: return@EditBar
                                vm.setStyle(tile.entryId, tile.tile.style.next(cycleAvailable = tile.item.live == LiveKind.Photos))
                            },
                        )
                    }
                }

                if (!kidsMode && pages.size > 1 && permissionsHandled) {
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
                            vm.activateQuickAction(action)
                            if (action != QuickAction.Flashlight) panel.close()
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
                            settings = settings,
                            onChange = vm::updateSettings,
                            startBackground = startBackground,
                            onPickBackground = vm::setStartBackground,
                            onClearBackground = vm::clearStartBackground,
                            autoAccentIndex = autoAccentIndex,
                            notificationAccess = notificationAccess,
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
                            backupFolderName = backupFolderName,
                            onPickBackupFolder = vm::setBackupFolder,
                            onClearBackupFolder = vm::clearBackupFolder,
                            onBackupNow = { vm.backupNow() },
                            onRestoreBackup = vm::restoreFromBackupFolder,
                            lockServiceEnabled = lockServiceEnabled,
                            onOpenAccessibilitySettings = vm::openAccessibilitySettings,
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

                if (!permissionsHandled) {
                    PermissionsScreen(onFinished = vm::onPermissionsFinished)
                }
            }
        }
    }
}
