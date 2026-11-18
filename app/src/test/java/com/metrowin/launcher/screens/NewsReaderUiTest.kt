package com.metrowin.launcher.screens

import android.graphics.Bitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.metrowin.launcher.data.NewsItem
import com.metrowin.launcher.news.NewsOpts
import com.metrowin.launcher.news.NewsPictures
import com.metrowin.launcher.news.NewsReader
import com.metrowin.launcher.news.NewsFailure
import com.metrowin.launcher.news.ReadBlock
import com.metrowin.launcher.news.ReadResult
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class NewsReaderUiTest : ScreenshotBase() {
    private val none = NewsPictures { _, _ -> null as Bitmap? }
    private val item = NewsItem("A story", "ANSA", "https://e.com/1", 0L)
    private val blocks = listOf(ReadBlock.Pic("https://e.com/a.jpg"), ReadBlock.Para("First paragraph of the story."), ReadBlock.Head("A heading"), ReadBlock.Para("Second paragraph."))

    @Test
    fun theTextShowsAndThePicturesOnlyWhenThePicturesAreOn() {
        show { NewsReader(item, none, NewsOpts(), 0L, { ReadResult.Ok(blocks) }, {}, {}, {}) }
        advance(600)
        compose.onNodeWithTag("nws:read:title").assertTextContains("A story")
        compose.onAllNodesWithTag("nws:read:para").assertCountEquals(2)
        compose.onAllNodesWithTag("nws:read:head").assertCountEquals(1)
        compose.onAllNodesWithTag("nws:read:pic", useUnmergedTree = true).assertCountEquals(0)
        capture("news_reader")
    }

    @Test
    fun theSizeButtonsAskForTheNextSizeAndTheLinksAreThere() {
        val sizes = ArrayList<Int>()
        var browser = 0
        var shared = 0
        show { NewsReader(item, none, NewsOpts(readPercent = 120), 0L, { ReadResult.Ok(blocks) }, { sizes += it }, { browser++ }, { shared++ }) }
        advance(600)
        compose.onNodeWithTag("nws:read:size").assertTextContains("120%")
        compose.onNodeWithTag("nws:read:smaller").performClick()
        compose.onNodeWithTag("nws:read:larger").performClick()
        compose.onNodeWithTag("nws:read:browser").performClick()
        compose.onNodeWithTag("nws:read:share").performClick()
        advance(300)
        assertEquals(listOf(110, 140), sizes)
        assertEquals(1, browser)
        assertEquals(1, shared)
    }

    @Test
    fun anArticleThatCannotBeShownOffersTheBrowser() {
        show { NewsReader(item, none, NewsOpts(), 0L, { ReadResult.Empty }, {}, {}, {}) }
        advance(600)
        compose.onNodeWithTag("nws:read:empty").assertExists()
        compose.onNodeWithTag("nws:read:browser").assertExists()
    }

    @Test
    fun aFailureSaysWhyAndRetryReadsAgain() {
        var calls = 0
        show { NewsReader(item, none, NewsOpts(), 0L, { calls++; if (calls == 1) ReadResult.Failed(NewsFailure.Offline) else ReadResult.Ok(blocks) }, {}, {}, {}) }
        advance(600)
        compose.onNodeWithTag("nws:read:failed").assertExists()
        compose.onNodeWithTag("nws:read:retry").performClick()
        advance(900)
        compose.waitForIdle()
        compose.onAllNodesWithTag("nws:read:para").assertCountEquals(2)
        assertEquals(2, calls)
    }
}
