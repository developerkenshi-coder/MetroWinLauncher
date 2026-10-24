package com.metrowin.launcher.screens

import androidx.activity.BackEventCompat
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.test.advanceEventTime
import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.actioncenter.ActionCenter
import com.metrowin.launcher.ui.apps.AppListModel
import com.metrowin.launcher.ui.apps.AppListScreen
import com.metrowin.launcher.ui.gesture.BackGestureHandler
import com.metrowin.launcher.ui.gesture.InstantBack
import com.metrowin.launcher.ui.gesture.LauncherPager
import com.metrowin.launcher.ui.gesture.PAGE_APPS
import com.metrowin.launcher.ui.gesture.PAGE_START
import com.metrowin.launcher.ui.gesture.PagerBack
import com.metrowin.launcher.ui.gesture.PanelBack
import com.metrowin.launcher.ui.gesture.PullPanel
import com.metrowin.launcher.ui.gesture.pullDownConnection
import com.metrowin.launcher.ui.gesture.pullDownFromTop
import com.metrowin.launcher.ui.gesture.rememberPullPanel
import com.metrowin.launcher.ui.gesture.returnToStart
import com.metrowin.launcher.ui.gesture.runBackGesture
import com.metrowin.launcher.ui.start.StartActions
import com.metrowin.launcher.ui.start.StartScreen
import com.metrowin.launcher.ui.start.buildStartCells
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Gestures and transitions of the launcher, driven with real touch input and the real back dispatcher
 * on the JVM. The phone is 360x760dp at 3x: 1080x2280 pixels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class GesturesTest : ScreenshotBase() {
    private val items = (0 until 40).map {
        AppItem("t$it", "App ${('A' + it % 26)}$it", Launch.Action("x"), Glyph.entries[5 + it % 8], removable = true)
    }
    private val entries = items.map { Tile(it.id, TileSize.Medium) }

    private fun noActions() = StartActions(
        onClick = {}, onLongClick = {}, onUnpin = {}, onResize = {}, onRename = {},
        onMove = { _, _ -> }, onMerge = { _, _ -> }, onBackgroundTap = {}, onOpenApps = {},
    )

    // Handles on what the shell composes, so a test can look at them and move them.
    private lateinit var panel: PullPanel
    private lateinit var pager: PagerState
    private lateinit var startScroll: ScrollState

    /** The launcher's pager, Start, app list and Action Center wired the way LauncherApp wires them. */
    @Composable
    private fun Shell(content: @Composable () -> Unit = {}) {
        panel = rememberPullPanel()
        pager = rememberPagerState { 2 }
        startScroll = rememberScrollState()
        val listState = rememberLazyListState()
        val appList = remember { AppListModel(AppSections.group(AppSections.sort(items))) }
        val connection = remember(panel) { pullDownConnection(panel, startScroll) { pager.currentPage != PAGE_START } }
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { panel.travelPx = it.height.toFloat() }
                .pullDownFromTop(panel, zonePx = 84f, enabled = true),
        ) {
            LauncherPager(
                state = pager,
                userScrollEnabled = true,
                start = {
                    StartScreen(
                        cells = buildStartCells(entries, items, null),
                        live = LiveTileData(),
                        badges = emptyMap(),
                        columns = 4,
                        background = null,
                        enterToken = 0,
                        exitRequest = null,
                        editingKey = null,
                        scrollState = startScroll,
                        actions = noActions(),
                        pullConnection = connection,
                    )
                },
                apps = {
                    AppListScreen(
                        model = appList, listState = listState, replayToken = 0, exiting = false,
                        onLaunch = {}, onLongPress = { _, _, _ -> }, onHeaderClick = {},
                        query = "", onQueryChange = {}, results = emptyList(),
                    )
                },
            )
            if (panel.isShown) {
                ActionCenter(
                    slots = listOf(QuickAction.Wifi, QuickAction.Bluetooth, QuickAction.Airplane, QuickAction.Flashlight),
                    states = emptyMap(), notifications = emptyList(), appsByPackage = emptyMap(),
                    hasNotificationAccess = true, panel = panel,
                    onAction = {}, onAllSettings = {}, onOpenNotification = {}, onDismissNotification = {},
                    onClearAll = {}, onTurnOnNotificationAccess = {}, onClose = panel::close,
                )
            }
            content()
        }
    }

    private fun scrollStartTo(value: Int) {
        compose.runOnUiThread {
            runBlocking { startScroll.scrollTo(value) }
            Snapshot.sendApplyNotifications()
        }
        advance(100)
    }

    /** Waits long enough before lifting the finger for it to be at rest. */
    private fun androidx.compose.ui.test.TouchInjectionScope.pause() = advanceEventTime(400)

    @Test
    fun theTilesAreNeverSeenInPlaceBetweenComingBackAndFlyingIn() {
        var token by mutableIntStateOf(0)
        show {
            StartScreen(
                cells = buildStartCells(entries, items, null),
                live = LiveTileData(),
                badges = emptyMap(),
                columns = 4,
                background = null,
                enterToken = token,
                exitRequest = null,
                editingKey = null,
                scrollState = rememberScrollState(),
                actions = noActions(),
            )
        }
        advance(3_000)
        assertTrue("Start is full of tiles once settled", capture("return_settled").accentShare() > 0.2f)

        // Coming back from an app bumps the token. The very first frame after that must already have the
        // tiles turned away, otherwise they flash in place and then fly in.
        compose.runOnUiThread {
            token++
            Snapshot.sendApplyNotifications()
        }
        advance(16)
        val firstFrame = capture("return_first_frame").accentShare()
        assertTrue("the tiles should be turned away on the first frame ($firstFrame)", firstFrame < 0.02f)
        advance(2_000)
        assertTrue("and they come back in", capture("return_done").accentShare() > 0.2f)
    }

    // --- Action Center: pulling it down

    @Test
    fun pullingFromTheTopEdgeOpensThePanelEvenWhenStartIsScrolledDown() {
        show { Shell() }
        advance(2_500)
        scrollStartTo(600)
        compose.onRoot().performTouchInput {
            down(Offset(540f, 40f))
            moveBy(Offset(0f, 450f)) // 150 dp, more than the 110 dp that commits
            pause()
            up()
        }
        advance(1_500)
        assertEquals("the panel settles fully open", 1f, panel.progress, 0.001f)
        assertTrue("Start was not scrolled by the pull", startScroll.value >= 600)
    }

    @Test
    fun aShortPullFromTheEdgeFallsBack() {
        show { Shell() }
        advance(2_500)
        compose.onRoot().performTouchInput {
            down(Offset(540f, 40f))
            moveBy(Offset(0f, 150f)) // 50 dp
            pause()
            up()
        }
        advance(1_500)
        assertEquals(0f, panel.progress, 0.001f)
        assertTrue(!panel.isShown)
    }

    @Test
    fun thePanelFollowsTheFingerOneToOne() {
        show { Shell() }
        advance(2_500)
        val slop = android.view.ViewConfiguration.get(compose.activity).scaledTouchSlop // not part of the movement
        compose.onRoot().performTouchInput {
            down(Offset(540f, 40f))
            moveBy(Offset(0f, 600f))
        }
        advance(32)
        val expected = (600f - slop) / panel.travelPx
        assertEquals("the lower edge of the panel is where the finger moved it", expected, panel.progress, 0.002f)
        compose.onRoot().performTouchInput { up() }
        advance(1_500)
    }

    @Test
    fun pullingStartDownFromAnywhereWhenItIsAtTheTopAlsoOpensIt() {
        show { Shell() }
        advance(2_500)
        compose.onRoot().performTouchInput {
            down(Offset(540f, 1200f))
            moveBy(Offset(0f, 500f))
            pause()
            up()
        }
        advance(1_500)
        assertEquals(1f, panel.progress, 0.001f)
    }

    @Test
    fun draggingDownInTheMiddleOfAScrolledStartScrollsItInsteadOfPullingThePanel() {
        show { Shell() }
        advance(2_500)
        scrollStartTo(900)
        compose.onRoot().performTouchInput {
            down(Offset(540f, 1200f))
            moveBy(Offset(0f, 300f))
            pause()
            up()
        }
        advance(500)
        assertEquals(0f, panel.progress, 0f)
        assertTrue("Start scrolled back up (${startScroll.value})", startScroll.value < 900)
    }

    @Test
    fun aSidewaysSwipeFromTheTopEdgeTurnsThePageAndDoesNotPullThePanel() {
        show { Shell() }
        advance(2_500)
        compose.onRoot().performTouchInput {
            down(Offset(900f, 40f))
            moveBy(Offset(-500f, 0f))
            pause()
            up()
        }
        advance(1_000)
        assertEquals(PAGE_APPS, pager.currentPage)
        assertEquals(0f, panel.progress, 0f)
    }

    @Test
    fun thePanelCanBeCaughtWhileItMovesAndDoesNotFightTheFinger() {
        show { Shell() }
        advance(2_500)
        compose.onRoot().performTouchInput {
            down(Offset(540f, 40f))
            moveBy(Offset(0f, 450f))
            up()
        }
        advance(60) // opening, not there yet
        assertTrue("it is on its way (${panel.progress})", panel.progress in 0.05f..0.99f)

        compose.onRoot().performTouchInput { down(Offset(540f, 600f)) } // on the part of the panel that is already down
        compose.onRoot().performTouchInput { moveBy(Offset(0f, -300f)) } // and pushes it up
        val held = panel.progress
        assertTrue("the spring that was opening it let go ($held)", held < 0.9f)
        advance(300) // the finger rests: nothing may carry the panel on to open
        assertEquals("a held panel stays where the finger left it", held, panel.progress, 0.0005f)
        compose.onRoot().performTouchInput { up() }
        advance(1_500)
    }

    @Test
    fun pushingTheOpenPanelUpPutsItAway() {
        show { Shell() }
        advance(2_500)
        compose.runOnUiThread { panel.open() }
        advance(1_500)
        assertEquals(1f, panel.progress, 0.001f)
        compose.onRoot().performTouchInput {
            down(Offset(540f, 1500f))
            moveBy(Offset(0f, -500f))
            up()
        }
        advance(1_500)
        assertEquals(0f, panel.progress, 0.001f)
    }

    // --- pages

    @Test
    fun aDragOverAThirdOfThePageTurnsItAndAShorterOneDoesNot() {
        show { Shell() }
        advance(2_500)
        compose.onRoot().performTouchInput {
            down(Offset(900f, 1200f))
            moveBy(Offset(-216f, 0f)) // 20 %
            pause()
            up()
        }
        advance(1_000)
        assertEquals("a short drag springs back", PAGE_START, pager.currentPage)

        compose.onRoot().performTouchInput {
            down(Offset(900f, 1200f))
            moveBy(Offset(-432f, 0f)) // 40 %
            pause()
            up()
        }
        advance(1_000)
        assertEquals("a longer one turns the page", PAGE_APPS, pager.currentPage)
    }

    @Test
    fun theAppListIsReadyBeforeTheFirstSwipe() {
        show { Shell() }
        advance(2_500)
        compose.onAllNodesWithTagCount("app:t0")
    }

    @Test
    fun midSwipeBothPagesAreVisibleAndTheyFadeAndSwing() {
        show { Shell() }
        advance(2_500)
        compose.onRoot().performTouchInput {
            down(Offset(900f, 1200f))
            moveBy(Offset(-540f, 0f))
        }
        advance(32)
        capture("pager_mid_swipe")
        val startDistance = pager.currentPageOffsetFraction
        assertTrue("half way ($startDistance)", kotlin.math.abs(startDistance) > 0.3f || pager.currentPage == PAGE_APPS)
        compose.onRoot().performTouchInput { up() }
        advance(1_000)
    }

    // --- back

    /** The back handling of LauncherApp for the layers these tests need, with the same drivers. */
    @Composable
    private fun BackShell() {
        val scope = rememberCoroutineScope()
        Shell {
            BackGestureHandler {
                when {
                    panel.isShown -> PanelBack(panel)
                    pager.currentPage == PAGE_APPS -> PagerBack(pager, scope, PAGE_START)
                    else -> PagerBack(pager, scope, PAGE_APPS, onlyFromRight = true)
                }
            }
        }
    }

    private fun backEvent(progress: Float, edge: Int = BackEventCompat.EDGE_LEFT) = BackEventCompat(0f, 0f, progress, edge)

    private val dispatcher get() = compose.activity.onBackPressedDispatcher

    @Test
    fun theBackSwipeSlidesTheActionCenterUpWithTheFinger() {
        show { BackShell() }
        advance(2_500)
        compose.runOnUiThread { panel.snapTo(1f) }
        advance(100)
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(backEvent(0f))
            dispatcher.dispatchOnBackProgressed(backEvent(0.4f))
        }
        advance(32)
        assertEquals("40 % of the swipe is 40 % of the way up", 0.6f, panel.progress, 0.01f)
        compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(backEvent(0.7f)) }
        advance(32)
        assertEquals(0.3f, panel.progress, 0.01f)
        compose.runOnUiThread { dispatcher.onBackPressed() } // the swipe is completed
        advance(1_500)
        assertEquals(0f, panel.progress, 0.001f)
    }

    @Test
    fun aCancelledBackSwipeBringsTheActionCenterBack() {
        show { BackShell() }
        advance(2_500)
        compose.runOnUiThread { panel.snapTo(1f) }
        advance(100)
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(backEvent(0f))
            dispatcher.dispatchOnBackProgressed(backEvent(0.5f))
        }
        advance(32)
        assertEquals(0.5f, panel.progress, 0.01f)
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        advance(1_500)
        assertEquals("it comes back down", 1f, panel.progress, 0.001f)
    }

    @Test
    fun theBackSwipeFromTheAppListPansBackToStartUnderTheFinger() {
        show { BackShell() }
        advance(2_500)
        compose.runOnUiThread { runBlocking { pager.scrollToPage(PAGE_APPS) } }
        advance(100)
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(backEvent(0f))
            dispatcher.dispatchOnBackProgressed(backEvent(0.5f))
        }
        advance(32)
        val position = pager.currentPage + pager.currentPageOffsetFraction
        assertEquals("half way between the app list and Start", 0.5f, position, 0.05f)
        compose.runOnUiThread { dispatcher.onBackPressed() }
        advance(1_500)
        assertEquals(PAGE_START, pager.currentPage)
        assertEquals(0f, pager.currentPageOffsetFraction, 0.001f)
    }

    @Test
    fun aCancelledBackSwipeLeavesTheAppListWhereItWas() {
        show { BackShell() }
        advance(2_500)
        compose.runOnUiThread { runBlocking { pager.scrollToPage(PAGE_APPS) } }
        advance(100)
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(backEvent(0f))
            dispatcher.dispatchOnBackProgressed(backEvent(0.3f))
            dispatcher.dispatchOnBackCancelled()
        }
        advance(1_500)
        assertEquals(PAGE_APPS, pager.currentPage)
        assertEquals(0f, pager.currentPageOffsetFraction, 0.001f)
    }

    @Test
    fun aBackSwipeFromTheRightEdgeOnStartPansOnToTheAppList() {
        show { BackShell() }
        advance(2_500)
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(backEvent(0f, BackEventCompat.EDGE_RIGHT))
            dispatcher.dispatchOnBackProgressed(backEvent(0.5f, BackEventCompat.EDGE_RIGHT))
        }
        advance(32)
        assertEquals(0.5f, pager.currentPage + pager.currentPageOffsetFraction, 0.05f)
        compose.runOnUiThread { dispatcher.onBackPressed() }
        advance(1_500)
        assertEquals(PAGE_APPS, pager.currentPage)
    }

    @Test
    fun aBackSwipeFromTheLeftEdgeOnStartAndTheBackButtonDoNothing() {
        show { BackShell() }
        advance(2_500)
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(backEvent(0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(backEvent(0.8f, BackEventCompat.EDGE_LEFT))
            dispatcher.onBackPressed()
        }
        advance(1_500)
        assertEquals(PAGE_START, pager.currentPage)
        assertEquals(0f, pager.currentPageOffsetFraction, 0f)
        // The three-button bar sends no progress at all.
        compose.runOnUiThread { dispatcher.onBackPressed() }
        advance(1_500)
        assertEquals(PAGE_START, pager.currentPage)
    }

    @Test
    fun backWithoutProgressStillGoesBackFromTheAppList() {
        show { BackShell() }
        advance(2_500)
        compose.runOnUiThread { runBlocking { pager.scrollToPage(PAGE_APPS) } }
        advance(100)
        compose.runOnUiThread { dispatcher.onBackPressed() } // the button
        advance(1_500)
        assertEquals(PAGE_START, pager.currentPage)
    }

    @Test
    fun instantBackRunsItsActionOnCommitOnly() = runBlocking {
        var done = 0
        val driver = InstantBack { done++ }
        runBackGesture(kotlinx.coroutines.flow.flowOf(backEvent(0.2f), backEvent(0.6f)), driver)
        assertEquals(1, done)
    }

    // --- coming back to Start

    @Test
    fun returningToStartFromTheAppListAndAScrolledStartGlidesBothAtOnce() {
        var trigger by mutableIntStateOf(0)
        show {
            Shell { LaunchedEffect(trigger) { if (trigger > 0) returnToStart(pager, startScroll, instant = false) } }
        }
        advance(2_500)
        compose.runOnUiThread { runBlocking { pager.scrollToPage(PAGE_APPS); startScroll.scrollTo(900) } }
        advance(100)
        compose.runOnUiThread {
            trigger++
            Snapshot.sendApplyNotifications()
        }
        advance(120)
        val pagerMoved = pager.currentPage + pager.currentPageOffsetFraction < 0.99f
        val scrollMoved = startScroll.value < 900
        assertTrue("the page is gliding back", pagerMoved)
        assertTrue("and so is the scroll, at the same time (${startScroll.value})", scrollMoved)
        advance(2_000)
        assertEquals(PAGE_START, pager.currentPage)
        assertEquals(0, startScroll.value)
    }

    @Test
    fun returningFromAnotherAppJumpsStraightToTheTopOfStart() {
        var trigger by mutableIntStateOf(0)
        show {
            Shell { LaunchedEffect(trigger) { if (trigger > 0) returnToStart(pager, startScroll, instant = true) } }
        }
        advance(2_500)
        compose.runOnUiThread { runBlocking { pager.scrollToPage(PAGE_APPS); startScroll.scrollTo(900) } }
        advance(100)
        compose.runOnUiThread {
            trigger++
            Snapshot.sendApplyNotifications()
        }
        advance(16)
        assertEquals("no glide: it is already there on the first frame", PAGE_START, pager.currentPage)
        assertEquals(0, startScroll.value)
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTagCount(tag: String) {
        val nodes = onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes()
        assertTrue("the app list row $tag is already composed (${nodes.size})", nodes.isNotEmpty())
    }
}
