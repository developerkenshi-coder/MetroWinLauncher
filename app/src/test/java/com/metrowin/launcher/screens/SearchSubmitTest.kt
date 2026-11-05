package com.metrowin.launcher.screens

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.data.ContactMatch
import com.metrowin.launcher.data.GlobalResults
import com.metrowin.launcher.data.QuickAnswer
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.ui.apps.AppListModel
import com.metrowin.launcher.ui.apps.AppListScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The confirm of the keyboard in the search of the app list: it opens the first row, as a touch on that row would do,
 * and with nothing typed it opens nothing (the plain list is not the result of a search).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class SearchSubmitTest : ScreenshotBase() {
    private val alpha = AppItem("app:a", "Alpha", Launch.Action("x"))
    private val beta = AppItem("app:b", "Beta", Launch.Action("x"))
    private val launched = mutableListOf<AppItem>()
    private val dialled = mutableListOf<String>()
    private val answered = mutableListOf<QuickAnswer>()

    @Before
    fun clean() {
        launched.clear()
        dialled.clear()
        answered.clear()
    }

    private fun showList(
        query: String,
        results: List<AppItem> = emptyList(),
        answers: List<QuickAnswer> = emptyList(),
        global: GlobalResults = GlobalResults.Empty,
    ) {
        show {
            val model = remember { AppListModel(AppSections.group(AppSections.sort(listOf(alpha, beta)))) }
            AppListScreen(
                model = model, listState = rememberLazyListState(), replayToken = 0, exiting = false,
                onLaunch = { launched += it }, onLongPress = { _, _, _ -> }, onHeaderClick = {},
                query = query, onQueryChange = {}, results = results, answers = answers, global = global,
                onAnswer = { answered += it }, onDial = { dialled += it },
            )
        }
        advance(600)
    }

    /** The search key of the keyboard. */
    private fun confirm() {
        compose.onNodeWithTag("search:field").performClick()
        compose.onNodeWithTag("search:field").performImeAction()
        advance(600)
    }

    @Test
    fun theConfirmOpensTheFirstAppTheSearchFound() {
        showList("al", results = listOf(alpha, beta))
        confirm()
        assertEquals(listOf(alpha), launched)
    }

    @Test
    fun withNothingTypedTheConfirmOpensNothing() {
        // The list of every app is not a result: its first row is the box itself, and pressing enter on an empty box
        // must not start the first app of the alphabet.
        showList("", results = emptyList())
        confirm()
        assertTrue("nothing may open ($launched)", launched.isEmpty())
        assertTrue(dialled.isEmpty() && answered.isEmpty())
    }

    @Test
    fun anAnswerAboveTheAppsIsTheFirstRow() {
        val sum = QuickAnswer.Calculation("12*3", "36")
        showList("12*3", results = listOf(alpha), answers = listOf(sum))
        confirm()
        assertEquals(listOf(sum), answered)
        assertTrue("the app below the answer stays where it is ($launched)", launched.isEmpty())
    }

    @Test
    fun aContactThatIsTheFirstRowIsDialledAsItsRowDoes() {
        val anna = ContactMatch("Anna", "content://contacts/1", number = "3331112222")
        showList("ann", global = GlobalResults(contacts = listOf(anna)))
        confirm()
        assertEquals(listOf("3331112222"), dialled)
        assertTrue("no web search for the name of a contact ($launched)", launched.isEmpty())
    }

    @Test
    fun theWebSearchOpensWhenItIsAllTheSearchFound() {
        showList("qwzx", global = GlobalResults(webQuery = "qwzx"))
        confirm()
        assertEquals("search:web", launched.single().id)
    }
}
