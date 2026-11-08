package com.metrowin.launcher.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.calculator.CalculatorScreen
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.TileGrid
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.start.AppCell
import com.metrowin.launcher.ui.start.StartActions
import com.metrowin.launcher.ui.start.StartScreen
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tablets and foldables in portrait (docs/progetti/tablet-pieghevoli.md): Start and a mini app on 600 dp and 840 dp wide screens. The text of the
 * settings, menus and dialogs on the same sizes is in TextFitTablet600Test and TextFitTablet840Test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w840dp-h1200dp-xxhdpi")
class WideScreensTest : ScreenshotBase() {
    private val density get() = context.resources.displayMetrics.density
    private val sizes = listOf(TileSize.Medium, TileSize.Wide, TileSize.Small, TileSize.Small, TileSize.Small, TileSize.Small, TileSize.Large, TileSize.Medium, TileSize.Medium)
    private val items = sizes.indices.map { AppItem("$it", "App $it", Launch.Action("x")) }

    private fun start(widthDp: Int) {
        show(size = DpSize(widthDp.dp, 1200.dp)) {
            StartScreen(
                cells = items.mapIndexed { i, item -> AppCell(Tile(item.id, sizes[i]), item) },
                live = LiveTileData(), badges = emptyMap(), columns = TileGrid.columnsFor(widthDp.toFloat(), more = false),
                background = null, enterToken = 0, exitRequest = null, editingKey = null, scrollState = rememberScrollState(),
                actions = StartActions(
                    onClick = {}, onLongClick = {}, onUnpin = {}, onResize = {}, onRename = {},
                    onMove = { _, _ -> }, onMerge = { _, _ -> }, onBackgroundTap = {}, onNextPage = {},
                ),
            )
        }
        advance(2_500)
    }

    /** Every tile inside the screen width, none on top of another, and a medium one about the size it has on a phone. */
    private fun checkStart(widthDp: Int) {
        start(widthDp)
        capture("start_${widthDp}dp")
        val boxes = items.map { compose.onNodeWithTag("tile:${it.id}").fetchSemanticsNode().let { n -> n.positionInRoot.x to n.positionInRoot.y to n.size } }
        val widthPx = widthDp * density
        boxes.forEachIndexed { i, (pos, size) ->
            val (x, y) = pos
            assertTrue("tile $i leaves the screen: x=$x width=${size.width} of $widthPx", x >= -1f && x + size.width <= widthPx + 1f)
            for (j in i + 1 until boxes.size) {
                val (otherPos, other) = boxes[j]
                val overlapX = minOf(x + size.width, otherPos.first + other.width) - maxOf(x, otherPos.first)
                val overlapY = minOf(y + size.height, otherPos.second + other.height) - maxOf(y, otherPos.second)
                assertTrue("tiles $i and $j overlap", overlapX <= 1f || overlapY <= 1f)
            }
        }
        val medium = boxes[0].second.width / density
        // The columns come in blocks of two: 600 dp takes 6 (tiles about 188 dp, a quarter bigger than on a phone), 840 dp takes 8 (about 160 dp).
        assertTrue("a medium tile is $medium dp wide on $widthDp dp", medium in 130f..200f)
    }

    @Test
    fun startOn600dpKeepsTheTilesInsideAndOfAUsualSize() = checkStart(600)

    @Test
    fun startOn840dpKeepsTheTilesInsideAndOfAUsualSize() = checkStart(840)

    @Test
    fun aMiniAppFillsTheWidthOf840dp() {
        show(size = DpSize(840.dp, 1200.dp)) { CalculatorScreen() }
        advance(1_000)
        capture("calculator_840dp")
        val key = compose.onNodeWithTag("calc:7").fetchSemanticsNode()
        val widthPx = 840 * density
        assertTrue("the key is on screen: ${key.positionInRoot.x} + ${key.size.width}", key.positionInRoot.x + key.size.width <= widthPx + 1f)
        val equals = compose.onNodeWithTag("calc:equals").fetchSemanticsNode()
        assertTrue("the last key is on screen", equals.positionInRoot.x + equals.size.width <= widthPx + 1f && equals.positionInRoot.y + equals.size.height <= 1200 * density + 1f)
    }
}
