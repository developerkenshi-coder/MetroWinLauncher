package com.metrowin.launcher

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Folder
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.ui.apps.AppContextMenu
import com.metrowin.launcher.ui.apps.AppListModel
import com.metrowin.launcher.ui.apps.AppListScreen
import com.metrowin.launcher.ui.apps.JumpList
import com.metrowin.launcher.ui.apps.MenuState
import com.metrowin.launcher.ui.components.LocalIconLoader
import com.metrowin.launcher.ui.components.TurnstileHost
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

/** Time for the tiles (or rows) to turn away before the app opens. */
private const val START_EXIT_MS = 300L
private const val LIST_EXIT_MS = 240L

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

    var openFolderId by remember { mutableStateOf<String?>(null) }
    val cells = remember(entries, items, openFolderId) { buildStartCells(entries, items, openFolderId) }
    val appList = remember(sections) { AppListModel(sections) }
    val jumpKeys = remember(sections) { AppSections.jumpKeys(sections) }
    val pinnedIds = remember(entries) {
        entries.flatMapTo(HashSet()) { entry -> if (entry is Folder) entry.children + entry.id else listOf(entry.id) }
    }

    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val pagerState = rememberPagerState { 2 }
    val startScroll = rememberScrollState()
    val listState = rememberLazyListState()

    var editingKey by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<MenuState?>(null) }
    var jumpOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var renamingFolderId by remember { mutableStateOf<String?>(null) }

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
            renamingFolderId != null -> renamingFolderId = null
            jumpOpen -> jumpOpen = false
            menu != null -> menu = null
            settingsOpen -> closeSettings()
            editingKey != null -> editingKey = null
            openFolderId != null -> openFolderId = null
            pagerState.currentPage == PAGE_APPS -> scope.launch { pagerState.animateScrollToPage(PAGE_START) }
            // On Start, Back does nothing: a launcher has nowhere to go back to.
        }
    }

    /** Opens an app, or one of the launcher's own pages. [startKey] is the tile it was opened from, if any. */
    fun open(item: AppItem, startKey: String?) {
        if (launching) return
        launching = true
        if (startKey != null) exitRequest = ExitRequest(startKey) else listExiting = true
        scope.launch {
            delay(if (startKey != null) START_EXIT_MS else LIST_EXIT_MS)
            when {
                item.launch is Launch.Internal -> settingsOpen = true
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

    val accent = MetroAccents[settings.accentIndex.coerceIn(MetroAccents.indices)]

    CompositionLocalProvider(LocalIconLoader provides vm.icons) {
        MetroTheme(accent = accent, dark = settings.darkTheme) {
            Box(Modifier.fillMaxSize().background(LocalMetroColors.current.background)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = !launching && menu == null && !jumpOpen && permissionsHandled,
                    key = { it },
                ) { page ->
                    when (page) {
                        PAGE_START -> StartScreen(
                            cells = cells,
                            live = live,
                            badges = emptyMap(),
                            columns = settings.columns,
                            background = startBackground,
                            enterToken = enterToken,
                            exitRequest = exitRequest,
                            editingKey = editingKey,
                            scrollState = startScroll,
                            actions = startActions,
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

                TurnstileHost(show = settingsOpen) {
                    SettingsScreen(
                        settings = settings,
                        startBackground = startBackground,
                        onChange = vm::updateSettings,
                        onPickBackground = vm::setStartBackground,
                        onClearBackground = vm::clearStartBackground,
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
