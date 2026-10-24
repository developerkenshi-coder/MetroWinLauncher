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
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.ui.apps.AppContextMenu
import com.metrowin.launcher.ui.apps.AppListModel
import com.metrowin.launcher.ui.apps.AppListScreen
import com.metrowin.launcher.ui.apps.JumpList
import com.metrowin.launcher.ui.apps.MenuState
import com.metrowin.launcher.ui.components.LocalIconLoader
import com.metrowin.launcher.ui.permissions.PermissionsScreen
import com.metrowin.launcher.ui.start.ExitRequest
import com.metrowin.launcher.ui.start.StartScreen
import com.metrowin.launcher.ui.start.StartTile
import com.metrowin.launcher.ui.theme.MetroBlack
import com.metrowin.launcher.ui.theme.MetroTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PAGE_START = 0
private const val PAGE_APPS = 1

/** Time for the tiles (or rows) to turn away before the app opens. */
private const val START_EXIT_MS = 300L
private const val LIST_EXIT_MS = 240L

/**
 * The launcher: a pager with the Start screen and the app list, plus the overlays that sit above
 * both (context menu, jump list, first-run permissions).
 */
@Composable
fun LauncherApp(vm: LauncherViewModel) {
    val items by vm.items.collectAsStateWithLifecycle()
    val sections by vm.sections.collectAsStateWithLifecycle()
    val tiles by vm.tiles.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val permissionsHandled by vm.permissionsHandled.collectAsStateWithLifecycle()
    val homePresses by vm.homePresses.collectAsStateWithLifecycle()

    val startTiles = remember(tiles, items) {
        val byId = items.associateBy { it.id }
        tiles.mapNotNull { tile -> byId[tile.id]?.let { StartTile(tile, it) } }
    }
    val appList = remember(sections) { AppListModel(sections) }
    val jumpKeys = remember(sections) { AppSections.jumpKeys(sections) }
    val pinnedIds = remember(tiles) { tiles.mapTo(HashSet()) { it.id } }

    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val pagerState = rememberPagerState { 2 }
    val startScroll = rememberScrollState()
    val listState = rememberLazyListState()

    var editingId by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<MenuState?>(null) }
    var jumpOpen by remember { mutableStateOf(false) }

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

    // Pressing Home while the launcher is open goes back to the top of Start.
    LaunchedEffect(homePresses) {
        if (homePresses == 0) return@LaunchedEffect
        editingId = null
        menu = null
        jumpOpen = false
        if (pagerState.currentPage != PAGE_START) pagerState.animateScrollToPage(PAGE_START)
        startScroll.animateScrollTo(0)
    }

    BackHandler {
        when {
            jumpOpen -> jumpOpen = false
            menu != null -> menu = null
            editingId != null -> editingId = null
            pagerState.currentPage == PAGE_APPS -> scope.launch { pagerState.animateScrollToPage(PAGE_START) }
            // On Start, Back does nothing: a launcher has nowhere to go back to.
        }
    }

    fun launchFromStart(start: StartTile) {
        if (launching) return
        launching = true
        exitRequest = ExitRequest(start.tile.id)
        scope.launch {
            delay(START_EXIT_MS)
            if (vm.launch(start.item)) launched = true else comeBack()
        }
    }

    fun launchFromList(item: AppItem) {
        if (launching) return
        launching = true
        listExiting = true
        scope.launch {
            delay(LIST_EXIT_MS)
            if (vm.launch(item)) launched = true else comeBack()
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

    CompositionLocalProvider(LocalIconLoader provides vm.icons) {
        MetroTheme {
            Box(Modifier.fillMaxSize().background(MetroBlack)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = !launching && menu == null && !jumpOpen && permissionsHandled,
                    key = { it },
                ) { page ->
                    when (page) {
                        PAGE_START -> StartScreen(
                            tiles = startTiles,
                            live = live,
                            enterToken = enterToken,
                            exitRequest = exitRequest,
                            editingId = editingId,
                            scrollState = startScroll,
                            onTileClick = { start ->
                                when {
                                    launching -> Unit
                                    editingId == null -> launchFromStart(start)
                                    editingId == start.tile.id -> editingId = null
                                    else -> editingId = start.tile.id
                                }
                            },
                            onTileLongClick = { start ->
                                if (!launching) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    editingId = start.tile.id
                                }
                            },
                            onUnpin = { start ->
                                vm.unpin(start.tile.id)
                                editingId = null
                            },
                            onResize = { start -> vm.resize(start.tile.id) },
                            onBackgroundTap = { editingId = null },
                            onOpenApps = { scope.launch { pagerState.animateScrollToPage(PAGE_APPS) } },
                        )

                        else -> AppListScreen(
                            model = appList,
                            listState = listState,
                            replayToken = listReplay,
                            exiting = listExiting,
                            onLaunch = ::launchFromList,
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

                if (!permissionsHandled) {
                    PermissionsScreen(onFinished = vm::onPermissionsFinished)
                }
            }
        }
    }
}
