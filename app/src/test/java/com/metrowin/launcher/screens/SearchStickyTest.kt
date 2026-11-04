package com.metrowin.launcher.screens

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.metrowin.launcher.R
import com.metrowin.launcher.data.AppLists
import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.data.ContactMatch
import com.metrowin.launcher.data.GlobalResults
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.ui.apps.AppListModel
import com.metrowin.launcher.ui.apps.AppListScreen
import com.metrowin.launcher.ui.apps.ListRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The search screen: the chips right under the box, before the searches made before, and the headings of the results that stay on top. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class SearchStickyTest : ScreenshotBase() {
    private val apps = (1..6).map { AppItem("app:$it", "App $it", Launch.Action("x")) }

    @Test
    fun theChipsComeBeforeTheSearchesMadeBeforeAndTheListAfterThem() {
        val model = AppListModel(AppSections.group(AppSections.sort(apps)), frequent = apps.take(1), lists = AppLists.DEFAULTS)
        val rows = model.withRecents(listOf("one", "two"))
        val kinds = rows.map { it::class.simpleName }
        assertEquals(listOf("Search", "Categories", "Section", "Recent", "Recent", "Section"), kinds.take(6))
        assertEquals("the chips are there once", 1, rows.count { it === ListRow.Categories })
        assertEquals("nothing of the list is lost, the heading and the two searches are added", model.rows.size + 3, rows.size)
    }

    @Test
    fun aHeadingOfTheResultsStaysAtTheTopWhileItsResultsScrollUnderIt() {
        val contacts = (1..30).map { ContactMatch("Contact $it", "content://c/$it") }
        show {
            val model = remember { AppListModel(AppSections.group(AppSections.sort(apps))) }
            AppListScreen(
                model = model, listState = rememberLazyListState(), replayToken = 0, exiting = false,
                onLaunch = {}, onLongPress = { _, _, _ -> }, onHeaderClick = {},
                query = "a", onQueryChange = {}, results = apps, global = GlobalResults(contacts = contacts),
            )
        }
        advance(900)
        val screen = compose.onRoot().fetchSemanticsNode().size.height
        repeat(2) {
            compose.onRoot().performTouchInput { swipe(Offset(centerX, height * 0.8f), Offset(centerX, height * 0.2f), durationMillis = 200) }
            advance(1_500)
        }
        val heading = compose.onNodeWithTag("section:${R.string.search_section_contacts}").fetchSemanticsNode()
        assertTrue("the heading is on screen, at the top (${heading.positionInRoot.y} of $screen)", heading.positionInRoot.y < screen * 0.2f)
    }
}
