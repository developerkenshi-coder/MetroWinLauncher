package com.metrowin.launcher

import android.app.Activity
import android.app.KeyguardManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrowin.launcher.data.AppSearch
import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.data.AppRepository
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Folder
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.actioncenter.ActionCenter
import com.metrowin.launcher.ui.apps.AppContextMenu
import com.metrowin.launcher.ui.apps.AppListModel
import com.metrowin.launcher.ui.apps.AppListScreen
import com.metrowin.launcher.ui.apps.JumpList
import com.metrowin.launcher.ui.apps.MenuState
import com.metrowin.launcher.ui.components.LocalIconLoader
import com.metrowin.launcher.ui.components.TurnstileHost
import com.metrowin.launcher.ui.kids.KidsHeader
import com.metrowin.launcher.ui.permissions.PermissionsScreen
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.start.AppCell
import com.metrowin.launcher.ui.start.ExitRequest
import com.metrowin.launcher.ui.start.FolderCell
import com.metrowin.launcher.ui.start.FolderNameDialog
import com.metrowin.launcher.ui.start.StartActions
import com.metrowin.launcher.ui.start.StartScreen
import com.metrowin.launcher.ui.start.buildStartCells
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.MetroAccents
import com.metrowin.launcher.ui.theme.MetroTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PAGE_START = 0
private const val PAGE_APPS = 1

/** The Kid's Corner is the third page of the settings pivot. */
private const val PAGE_KIDS_SETTINGS = 2

/** Time for the tiles (or rows) to turn away before the app opens. */
private const val START_EXIT_MS = 300L
private const val LIST_EXIT_MS = 240L

/** Pulled down further than this, the Action Center stays open when let go. */
private const val PULL_OPENS_ABOVE = 0.3f
private const val PULL_FLING_VELOCITY = 800f

/**
 * The launcher: a pager with the Start screen and the app list, plus the pages and overlays that sit
 * above both (settings, context menu, jump list, first-run permissions).
 */
@Composable
fun LauncherApp(vm: LauncherViewModel) {
    val items by vm.items.collectAsStateWithLifecycle()
    val sections by vm.sections.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val startBackground by vm.background.collectAsStateWithLifecycle()
    val permissionsHandled by vm.permissionsHandled.collectAsStateWithLifecycle()
    val homePresses by vm.homePresses.collectAsStateWithLifecycle()
    val notifications by vm.notifications.collectAsStateWithLifecycle()
    val badges by vm.badges.collectAsStateWithLifecycle()
    val notificationAccess by vm.notificationAccess.collectAsStateWithLifecycle()
    val torchOn by vm.torchOn.collectAsStateWithLifecycle()

    var openFolderId by remember { mutableStateOf<String?>(null) }
    val cells = remember(entries, items, openFolderId) { buildStartCells(entries, items, openFolderId) }
    val appList = remember(sections) { AppListModel(sections) }
    val jumpKeys = remember(sections) { AppSections.jumpKeys(sections) }
    val pinnedIds = remember(entries) {
        entries.flatMapTo(HashSet()) { entry -> if (entry is Folder) entry.children + entry.id else listOf(entry.id) }
    }

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
    val pagerState = rememberPagerState { 2 }
    val startScroll = rememberScrollState()
    val listState = rememberLazyListState()

    var editingKey by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<MenuState?>(null) }
    var jumpOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var renamingFolderId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var settingsPage by remember { mutableIntStateOf(0) }
    val searchResults = remember(query, items) { AppSearch.filter(items, query) }

    // The Action Center: 0 hidden, 1 fully pulled down. Pulling down from the top of Start moves it.
    var actionCenter by remember { mutableFloatStateOf(0f) }
    var quickStates by remember { mutableStateOf<Map<QuickAction, Boolean?>>(emptyMap()) }

    fun moveActionCenterTo(target: Float) {
        scope.launch { animate(actionCenter, target, animationSpec = tween(durationMillis = 240)) { value, _ -> actionCenter = value } }
    }

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
        actionCenter = 0f
        enterToken++
        listReplay++
    }

    // Returning from an app: everything swings back in, as on Windows Phone.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { cameBackFromBackground = true }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        vm.onStart()
        if (cameBackFromBackground) {
            cameBackFromBackground = false
            comeBack()
        }
    }
    // An app that opened over us without stopping us (a dialog, a floating window) still ends up here.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (launched) comeBack() }

    // An open folder that no longer exists (its last apps were removed).
    LaunchedEffect(entries) {
        if (openFolderId != null && entries.none { it.id == openFolderId }) openFolderId = null
    }

    // Pressing Home while the launcher is open goes back to the top of Start.
    LaunchedEffect(homePresses) {
        if (homePresses == 0) return@LaunchedEffect
        editingKey = null
        menu = null
        jumpOpen = false
        renamingFolderId = null
        query = ""
        if (actionCenter > 0f) moveActionCenterTo(0f)
        if (settingsOpen) {
            settingsOpen = false
            comeBack()
        }
        if (pagerState.currentPage != PAGE_START) pagerState.animateScrollToPage(PAGE_START)
        startScroll.animateScrollTo(0)
    }

    fun closeSettings() {
        settingsOpen = false
        comeBack()
    }

    BackHandler {
        when {
            actionCenter > 0f -> moveActionCenterTo(0f)
            renamingFolderId != null -> renamingFolderId = null
            jumpOpen -> jumpOpen = false
            menu != null -> menu = null
            settingsOpen -> closeSettings()
            kidsMode -> Unit // only a grown-up, with the screen lock, gets out of the Kid's Corner
            editingKey != null -> editingKey = null
            openFolderId != null -> openFolderId = null
            query.isNotEmpty() -> query = ""
            pagerState.currentPage == PAGE_APPS -> scope.launch { pagerState.animateScrollToPage(PAGE_START) }
            // On Start, Back does nothing: a launcher has nowhere to go back to.
        }
    }

    val activity = LocalActivity.current
    val context = LocalContext.current
    val exitTitle = stringResource(R.string.kids_exit_title)

    fun enterKidsCorner() {
        vm.updateSettings { it.copy(kidsMode = true) }
        settingsOpen = false
        // Screen pinning, when the user has turned it on, keeps the child from leaving through Recents.
        runCatching { activity?.startLockTask() }
        comeBack()
    }

    fun exitKidsCorner() {
        vm.updateSettings { it.copy(kidsMode = false) }
        runCatching { activity?.stopLockTask() }
        comeBack()
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
        launching = true
        if (startKey != null) exitRequest = ExitRequest(startKey) else listExiting = true
        scope.launch {
            delay(if (startKey != null) START_EXIT_MS else LIST_EXIT_MS)
            val internal = item.launch as? Launch.Internal
            when {
                internal?.route == AppRepository.ROUTE_KIDS ->
                    if (settings.kidsApps.isEmpty()) {
                        settingsPage = PAGE_KIDS_SETTINGS
                        settingsOpen = true
                    } else {
                        enterKidsCorner()
                    }
                internal != null -> {
                    settingsPage = 0
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
        vm.pin(item)
        // Show the new tile: go to Start and scroll to the end, where it was added.
        scope.launch {
            pagerState.animateScrollToPage(PAGE_START)
            delay(120)
            startScroll.animateScrollTo(startScroll.maxValue)
        }
    }

    val startActions = StartActions(
        onClick = { cell ->
            when {
                launching -> Unit
                editingKey != null -> editingKey = if (editingKey == cell.key) null else cell.key
                cell is FolderCell -> openFolderId = if (openFolderId == cell.folder.id) null else cell.folder.id
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
        onOpenApps = { scope.launch { pagerState.animateScrollToPage(PAGE_APPS) } },
    )

    // Pull down from the top of Start to open the Action Center. Dragging back up closes it again.
    val pullBlocked = kidsMode || launching || settingsOpen || menu != null || jumpOpen || editingKey != null ||
        renamingFolderId != null || !permissionsHandled || pagerState.currentPage != PAGE_START
    val currentPullBlocked by rememberUpdatedState(pullBlocked)
    val maxPullPx = with(density) { 320.dp.toPx() }
    val pullConnection = remember(maxPullPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val progress = actionCenter
                if (source == NestedScrollSource.UserInput && available.y < 0f && progress > 0f && progress < 1f) {
                    val updated = (progress + available.y / maxPullPx).coerceAtLeast(0f)
                    actionCenter = updated
                    return Offset(0f, (updated - progress) * maxPullPx)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f && !currentPullBlocked && startScroll.value == 0) {
                    actionCenter = (actionCenter + available.y / maxPullPx).coerceAtMost(1f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val progress = actionCenter
                if (progress <= 0f || progress >= 1f) return Velocity.Zero
                moveActionCenterTo(if (progress > PULL_OPENS_ABOVE || available.y > PULL_FLING_VELOCITY) 1f else 0f)
                return available
            }
        }
    }

    // While the panel is up, the state of its buttons is read again every second.
    val actionCenterShown = actionCenter > 0f
    LaunchedEffect(actionCenterShown, torchOn) {
        while (actionCenterShown) {
            quickStates = QuickAction.entries.associateWith(vm::quickActionState)
            delay(1_000)
        }
    }

    val accent = MetroAccents[settings.accentIndex.coerceIn(MetroAccents.indices)]

    CompositionLocalProvider(LocalIconLoader provides vm.icons) {
        MetroTheme(accent = accent, dark = settings.darkTheme) {
            Box(Modifier.fillMaxSize().background(LocalMetroColors.current.background)) {
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
                } else HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = !launching && menu == null && !jumpOpen && permissionsHandled,
                    key = { it },
                ) { page ->
                    when (page) {
                        PAGE_START -> StartScreen(
                            cells = cells,
                            live = live,
                            badges = badges,
                            columns = settings.columns,
                            background = startBackground,
                            enterToken = enterToken,
                            exitRequest = exitRequest,
                            editingKey = editingKey,
                            scrollState = startScroll,
                            actions = startActions,
                            pullConnection = pullConnection,
                        )

                        else -> AppListScreen(
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
                        )
                    }
                }

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
                        onDismiss = { menu = null },
                    )
                }

                if (jumpOpen) {
                    JumpList(
                        keys = jumpKeys,
                        available = appList.letters,
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

                if (actionCenter > 0f) {
                    ActionCenter(
                        slots = QuickAction.slots(settings.quickActions),
                        states = quickStates,
                        notifications = notifications,
                        appsByPackage = appsByPackage,
                        hasNotificationAccess = notificationAccess,
                        progress = actionCenter,
                        onAction = { action ->
                            vm.activateQuickAction(action)
                            if (action != QuickAction.Flashlight) moveActionCenterTo(0f)
                        },
                        onAllSettings = {
                            moveActionCenterTo(0f)
                            vm.openSystemSettings()
                        },
                        onOpenNotification = { entry ->
                            moveActionCenterTo(0f)
                            vm.openNotification(entry)
                        },
                        onDismissNotification = vm::dismissNotification,
                        onClearAll = vm::clearNotifications,
                        onTurnOnNotificationAccess = vm::openNotificationAccessSettings,
                        onClose = { moveActionCenterTo(0f) },
                    )
                }

                TurnstileHost(show = settingsOpen) {
                    SettingsScreen(
                        settings = settings,
                        startBackground = startBackground,
                        onChange = vm::updateSettings,
                        onPickBackground = vm::setStartBackground,
                        onClearBackground = vm::clearStartBackground,
                        notificationAccess = notificationAccess,
                        onManageNotificationAccess = vm::openNotificationAccessSettings,
                        apps = items,
                        onStartKidsCorner = ::enterKidsCorner,
                        initialPage = settingsPage,
                    )
                }

                renamingFolderId?.let { id ->
                    val folder = entries.firstOrNull { it.id == id } as? Folder
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
