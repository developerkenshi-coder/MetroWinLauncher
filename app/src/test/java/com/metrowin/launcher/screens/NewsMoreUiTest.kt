package com.metrowin.launcher.screens

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.NewsItem
import com.metrowin.launcher.data.NewsSources
import com.metrowin.launcher.news.NewsOpts
import com.metrowin.launcher.news.NewsPictures
import com.metrowin.launcher.news.NewsList
import com.metrowin.launcher.news.NewsSkeleton
import com.metrowin.launcher.news.NewsSourceDef
import com.metrowin.launcher.news.NewsView
import com.metrowin.launcher.news.SourcesDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class NewsMoreUiTest : ScreenshotBase() {
    private val none = NewsPictures { _, _ -> null as Bitmap? }
    private val day = 24 * 3_600_000L
    private val now = System.currentTimeMillis()
    private val items = listOf(
        NewsItem("First story", "ANSA", "https://e.com/1", now - 60_000),
        NewsItem("Second story", "Post", "https://e.com/2", now - 120_000),
        NewsItem("Third story", "Post", "https://e.com/3", now - 3 * day),
    )

    @Test
    fun dayLinesFollowTheDayAndCanBeTurnedOff() {
        show { Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) { NewsList(items, none, showImages = false, view = NewsView(now = now)) {} } }
        advance(600)
        compose.onAllNodesWithTag("nws:day", useUnmergedTree = true).assertCountEquals(2)
        compose.onAllNodesWithTag("nws:item").assertCountEquals(3)
    }

    @Test
    fun noDayLinesWhenTheOptionIsOff() {
        show { Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) { NewsList(items, none, showImages = false, view = NewsView(NewsOpts(days = false), now = now)) {} } }
        advance(600)
        compose.onAllNodesWithTag("nws:day", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun aLongTouchAsksForTheActionsAndATouchOpens() {
        var long: String? = null
        var opened: String? = null
        show { Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) { NewsList(items, none, false, NewsView(now = now), onLong = { long = it.title }) { opened = it.title } } }
        advance(600)
        compose.onAllNodesWithTag("nws:item").onFirst().performTouchInput { longClick() }
        advance(600)
        assertEquals("First story", long)
        compose.onAllNodesWithTag("nws:item").onFirst().performClick()
        advance(600)
        assertEquals("First story", opened)
    }

    @Test
    fun theSkeletonStandsWhereTheHeadlinesWillBe() {
        show { Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) { NewsSkeleton() } }
        advance(600)
        compose.onNodeWithTag("nws:skeleton", useUnmergedTree = true).assertExists()
        capture("news_skeleton")
    }

    @Test
    fun removingASourceAsksFirstAndTheEndsOfTheListCannotMove() {
        val list = listOf(NewsSourceDef("google", "Google News", NewsSources.GOOGLE_TOP), NewsSourceDef("c", "Blog", NewsSources.CUSTOM, "https://b.example.com/f"))
        val removed = ArrayList<String>()
        val moved = ArrayList<List<String>>()
        show { SourcesDialog(list, emptyMap(), onChange = { moved += it.map { s -> s.id } }, onRemove = { removed += it }, showImages = false, onImages = {}, onClose = {}, tile = "c") }
        advance(600)
        compose.onNodeWithTag("nws:remove:c", useUnmergedTree = true).performClick()
        advance(300)
        compose.onNodeWithTag("nws:confirm", useUnmergedTree = true).assertExists()
        assertTrue(removed.isEmpty())
        compose.onNodeWithTag("nws:remove_no", useUnmergedTree = true).performClick()
        advance(300)
        assertTrue(removed.isEmpty())
        compose.onNodeWithTag("nws:remove:c", useUnmergedTree = true).performClick()
        advance(300)
        compose.onNodeWithTag("nws:remove_yes", useUnmergedTree = true).performClick()
        advance(300)
        assertEquals(listOf("c"), removed)
        compose.onNodeWithTag("nws:up:google", useUnmergedTree = true).assertIsNotEnabled()
        compose.onNodeWithTag("nws:down:c", useUnmergedTree = true).assertIsNotEnabled()
        compose.onNodeWithTag("nws:up:c", useUnmergedTree = true).assertIsEnabled().performClick()
        advance(300)
        assertEquals(listOf(listOf("c", "google")), moved)
    }
}
