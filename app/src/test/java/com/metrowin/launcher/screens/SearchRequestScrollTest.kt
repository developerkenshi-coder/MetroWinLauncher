package com.metrowin.launcher.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.ui.apps.AppListModel
import com.metrowin.launcher.ui.apps.AppListScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The search asked for without typing (the double touch above Start, the double press of the home button, the
 * search button of the navigation bar): a list left scrolled down from before must come back to the search box,
 * and the box must have the focus (and with it the keyboard).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class SearchRequestScrollTest : ScreenshotBase() {
    private val apps = (0 until 78).map { AppItem("app:$it", "${'A' + it / 3}${'a' + it % 3}", Launch.Action("x")) }
    private lateinit var state: LazyListState
    private lateinit var scope: CoroutineScope
    private val focusRequest = mutableIntStateOf(0)

    private fun showList() {
        focusRequest.intValue = 0
        show {
            state = rememberLazyListState()
            scope = rememberCoroutineScope()
            val model = remember { AppListModel(AppSections.group(AppSections.sort(apps))) }
            AppListScreen(
                model = model, listState = state, replayToken = 0, exiting = false,
                onLaunch = {}, onLongPress = { _, _, _ -> }, onHeaderClick = {},
                query = "", onQueryChange = {}, results = emptyList(),
                searchFocus = focusRequest.intValue, onSearchFocusDone = { focusRequest.intValue = 0 },
            )
        }
        advance(900)
    }

    @Test
    fun aSearchRequestBringsTheSearchBoxBackWhenTheListWasLeftScrolled() {
        showList()
        // The user scrolled the list down on their last visit and went back to Start; the scroll stays.
        compose.runOnIdle { scope.launch { state.scrollToItem(18) } }
        compose.waitForIdle()
        assertTrue("the list is really scrolled down first (${state.firstVisibleItemIndex})", state.firstVisibleItemIndex > 2)

        // The double touch of the gesture: the request comes, and the search box answers with the keyboard.
        compose.runOnUiThread { focusRequest.intValue = 1 }
        advance(600)
        compose.waitForIdle()
        compose.onNodeWithTag("search:field").assertIsFocused()
        assertEquals(0, state.firstVisibleItemIndex)
    }

    @Test
    fun anAlreadyAnsweredRequestOpensNothingNew() {
        showList()
        // The box took the request: the token comes back handled, and the box does not ask for the keyboard once more
        // (a recomposition of the list, an open of another page and back, none of it may open the keyboard again).
        compose.runOnIdle { focusRequest.intValue = 1 }
        compose.waitForIdle()
        compose.runOnIdle { focusRequest.intValue = 0 }
        compose.waitForIdle()
        assertEquals(0, state.firstVisibleItemIndex)
        compose.onNodeWithTag("search:field").assertExists()
    }
}
