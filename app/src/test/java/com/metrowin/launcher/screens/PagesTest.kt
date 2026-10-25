package com.metrowin.launcher.screens

import androidx.activity.BackEventCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.metrowin.launcher.ui.gesture.BackGestureHandler
import com.metrowin.launcher.ui.gesture.EdgePageBack
import com.metrowin.launcher.ui.gesture.LauncherPager
import com.metrowin.launcher.ui.gesture.PageDots
import com.metrowin.launcher.ui.gesture.PagerBack
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Three Start screens and the app list after them: how the back swipe and the dots behave with several pages. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class PagesTest : ScreenshotBase() {
    private val startPages = 3
    private val appsPage = startPages
    private lateinit var pager: PagerState

    private val colors = listOf(Color(0xFF1BA1E2), Color(0xFF60A917), Color(0xFFE51400), Color(0xFF444444))

    @Composable
    private fun Pages() {
        pager = rememberPagerState { startPages + 1 }
        val scope = rememberCoroutineScope()
        Box(Modifier.fillMaxSize()) {
            LauncherPager(
                state = pager, startPages = startPages, userScrollEnabled = true,
                start = { page -> Box(Modifier.fillMaxSize().background(colors[page]).testTag("page:$page")) },
                apps = { Box(Modifier.fillMaxSize().background(colors[3]).testTag("page:apps")) },
            )
            BackGestureHandler {
                when {
                    pager.currentPage >= appsPage -> PagerBack(pager, scope, appsPage - 1)
                    else -> EdgePageBack(pager, scope, startPages + 1)
                }
            }
        }
    }

    private val dispatcher get() = compose.activity.onBackPressedDispatcher
    private fun event(progress: Float, edge: Int) = BackEventCompat(0f, 0f, progress, edge)

    private fun goTo(page: Int) {
        compose.runOnUiThread { runBlocking { pager.scrollToPage(page) } }
        advance(100)
    }

    private fun swipe(edge: Int, progress: Float = 0.5f, commit: Boolean = true) {
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(event(0f, edge))
            dispatcher.dispatchOnBackProgressed(event(progress, edge))
        }
        advance(32)
        compose.runOnUiThread { if (commit) dispatcher.onBackPressed() else dispatcher.dispatchOnBackCancelled() }
        advance(1_500)
    }

    @Test
    fun aSwipeFromTheLeftEdgeGoesBackOnePageAndOneFromTheRightGoesOn() {
        show { Pages() }
        advance(1_000)
        goTo(1)
        swipe(BackEventCompat.EDGE_LEFT)
        assertEquals("back to the first screen", 0, pager.currentPage)
        goTo(1)
        swipe(BackEventCompat.EDGE_RIGHT)
        assertEquals("on to the third", 2, pager.currentPage)
    }

    @Test
    fun theFirstScreenHasNothingBeforeIt() {
        show { Pages() }
        advance(1_000)
        swipe(BackEventCompat.EDGE_LEFT)
        assertEquals(0, pager.currentPage)
        assertEquals(0f, pager.currentPageOffsetFraction, 0f)
    }

    @Test
    fun fromTheLastStartScreenTheRightEdgeLeadsToTheAppList() {
        show { Pages() }
        advance(1_000)
        goTo(startPages - 1)
        swipe(BackEventCompat.EDGE_RIGHT)
        assertEquals(appsPage, pager.currentPage)
    }

    @Test
    fun backFromTheAppListReturnsToTheLastStartScreen() {
        show { Pages() }
        advance(1_000)
        goTo(appsPage)
        swipe(BackEventCompat.EDGE_LEFT)
        assertEquals(startPages - 1, pager.currentPage)
        goTo(appsPage)
        compose.runOnUiThread { dispatcher.onBackPressed() } // the button
        advance(1_500)
        assertEquals(startPages - 1, pager.currentPage)
    }

    @Test
    fun aCancelledSwipeLeavesThePageWhereItWas() {
        show { Pages() }
        advance(1_000)
        goTo(1)
        swipe(BackEventCompat.EDGE_RIGHT, progress = 0.4f, commit = false)
        assertEquals(1, pager.currentPage)
        assertEquals(0f, pager.currentPageOffsetFraction, 0.001f)
    }

    @Test
    fun theBackSwipeMovesThePagesWithTheFinger() {
        show { Pages() }
        advance(1_000)
        goTo(1)
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(event(0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(event(0.5f, BackEventCompat.EDGE_LEFT))
        }
        advance(32)
        assertEquals(0.5f, pager.currentPage + pager.currentPageOffsetFraction, 0.05f)
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        advance(1_500)
    }

    @Test
    fun theDotsFollowThePage() {
        show {
            Box(Modifier.fillMaxSize()) {
                Pages()
                PageDots(pager, startPages + 1, Modifier.testTag("dots"))
            }
        }
        advance(1_000)
        val first = capture("dots_first")
        goTo(2)
        advance(300)
        val third = capture("dots_third")
        // The dots are drawn in white at the top left corner (nothing else is white there): the bright one moves.
        fun whiteColumns(b: android.graphics.Bitmap) = (0 until 200).filter { x -> (0 until 40).any { y -> b.getPixel(x, y) == 0xFFFFFFFF.toInt() } }
        assertTrue("some dot is bright on the first screen", whiteColumns(first).isNotEmpty())
        assertTrue("and on the third", whiteColumns(third).isNotEmpty())
        assertTrue("the bright dot is not the same one", whiteColumns(first) != whiteColumns(third))
    }
}
