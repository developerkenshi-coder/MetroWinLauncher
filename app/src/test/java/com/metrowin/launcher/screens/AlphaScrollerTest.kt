package com.metrowin.launcher.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.ui.apps.AlphaIndex
import com.metrowin.launcher.ui.apps.AppListModel
import com.metrowin.launcher.ui.apps.AppListScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The letter at the edge of the app list: it says where the list is, and dragging it moves the list. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class AlphaScrollerTest : ScreenshotBase() {
    private val apps = (0 until 78).map { AppItem("app:$it", "${'A' + it / 3}${'a' + it % 3}", Launch.Action("x")) }
    private lateinit var state: LazyListState

    private fun showList() {
        show {
            state = rememberLazyListState()
            val model = remember { AppListModel(AppSections.group(AppSections.sort(apps))) }
            AppListScreen(
                model = model, listState = state, replayToken = 0, exiting = false,
                onLaunch = {}, onLongPress = { _, _, _ -> }, onHeaderClick = {},
                query = "", onQueryChange = {}, results = emptyList(),
            )
        }
        advance(900)
    }

    @Test
    fun theLetterIsTheLastHeaderAtOrAboveTheRow() {
        val headers = listOf(2 to "a", 6 to "b", 10 to "c")
        assertEquals("a", AlphaIndex.letterAt(headers, 0))
        assertEquals("a", AlphaIndex.letterAt(headers, 5))
        assertEquals("b", AlphaIndex.letterAt(headers, 6))
        assertEquals("c", AlphaIndex.letterAt(headers, 99))
        assertNull(AlphaIndex.letterAt(emptyList(), 3))
    }

    @Test
    fun thePositionGoesFromTopToBottomAndBack() {
        assertEquals(0f, AlphaIndex.fraction(0, 100, atEnd = false), 0f)
        assertEquals(0.5f, AlphaIndex.fraction(50, 101, atEnd = false), 1e-6f)
        assertEquals("the list cannot go further: the bottom", 1f, AlphaIndex.fraction(70, 100, atEnd = true), 0f)
        assertEquals(0, AlphaIndex.indexAt(0f, 100))
        assertEquals(99, AlphaIndex.indexAt(1f, 100))
        assertEquals(50, AlphaIndex.indexAt(0.5f, 101))
    }

    @Test
    fun scrollingShowsTheLetterOfTheAppsInViewAndDraggingItMovesTheList() {
        showList()
        compose.onRoot().performTouchInput { swipe(Offset(centerX, height * 0.8f), Offset(centerX, height * 0.4f), durationMillis = 200) }
        advance(600) // still moving or just stopped: the letter shows
        fun letter() = compose.onNodeWithTag("alphascroller:letter").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("")
        capture("alpha_scroller")
        val first = letter()
        val before = state.firstVisibleItemIndex
        compose.onNodeWithTag("alphascroller").performTouchInput {
            swipe(center, Offset(center.x, center.y + 900f), durationMillis = 400)
        }
        advance(500)
        assertTrue("dragging the letter down went further down the list ($before to ${state.firstVisibleItemIndex})", state.firstVisibleItemIndex > before + 20)
        assertTrue("and the letter follows (${first} to ${letter()})", letter() > first)
    }
}
