package com.metrowin.launcher.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.start.AppCell
import com.metrowin.launcher.ui.start.StartActions
import com.metrowin.launcher.ui.start.StartScreen
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Right to left (docs/progetti/rtl.md): the manifest has supportsRtl, so under an Arabic or Hebrew phone the pivot and Start must start
 * at the right. The same checks in the usual direction guard the test itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class RtlTest : ScreenshotBase() {
    private val width get() = 360f * context.resources.displayMetrics.density

    @Composable
    private fun In(direction: LayoutDirection, content: @Composable () -> Unit) =
        CompositionLocalProvider(LocalLayoutDirection provides direction) { content() }

    private fun pivot(direction: LayoutDirection, page: Int) = show {
        In(direction) {
            val pager = rememberPagerState(initialPage = page) { 3 }
            PivotHeader(listOf("one", "two", "three"), pager, Modifier.fillMaxWidth())
        }
    }.also { advance(300) }

    private fun bounds(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().let {
        it.positionInRoot.x to it.positionInRoot.x + it.size.width
    }

    @Test
    fun pivotTitlesStartAtTheLeftInTheUsualDirection() {
        pivot(LayoutDirection.Ltr, 0)
        assertTrue(bounds("one").first < 8f)
        assertTrue("the next title follows on the right", bounds("two").first > bounds("one").second)
    }

    @Test
    fun pivotTitlesStartAtTheRightInRtl() {
        pivot(LayoutDirection.Rtl, 0)
        assertTrue("the first title ends at the right edge: ${bounds("one")}", bounds("one").second > width - 8f)
        assertTrue("the next title follows on the left: ${bounds("two")} / ${bounds("one")}", bounds("two").second < bounds("one").first)
    }

    @Test
    fun theCurrentTitleStaysAtTheStartEdgeOnTheSecondPageInRtl() {
        pivot(LayoutDirection.Rtl, 1)
        assertTrue("page 2 title at the right edge: ${bounds("two")}", bounds("two").second > width - 8f)
    }

    @Test
    fun theCurrentTitleStaysAtTheStartEdgeOnTheSecondPageInTheUsualDirection() {
        pivot(LayoutDirection.Ltr, 1)
        assertTrue("page 2 title at the left edge: ${bounds("two")}", bounds("two").first < 8f)
    }

    private val items = ('A'..'D').map { AppItem("$it", "App $it", Launch.Action("x")) }

    private fun start(direction: LayoutDirection) = show {
        In(direction) {
            StartScreen(
                cells = items.map { AppCell(Tile(it.id, TileSize.Medium), it) },
                live = LiveTileData(), badges = emptyMap(), columns = 4, background = null,
                enterToken = 0, exitRequest = null, editingKey = null, scrollState = rememberScrollState(),
                actions = StartActions(
                    onClick = {}, onLongClick = {}, onUnpin = {}, onResize = {}, onRename = {},
                    onMove = { _, _ -> }, onMerge = { _, _ -> }, onBackgroundTap = {}, onNextPage = {},
                ),
            )
        }
    }.also { advance(2_500) }

    private fun centerX(tag: String) = compose.onNodeWithTag("tile:$tag").fetchSemanticsNode().boundsInRoot.center.x

    @Test
    fun startFillsTheRowFromTheLeftInTheUsualDirection() {
        start(LayoutDirection.Ltr)
        assertTrue(centerX("A") < centerX("B"))
    }

    @Test
    fun startFillsTheRowFromTheRightInRtl() {
        start(LayoutDirection.Rtl)
        capture("start_rtl")
        assertTrue("A ${centerX("A")} is to the right of B ${centerX("B")}", centerX("A") > centerX("B"))
    }
}
