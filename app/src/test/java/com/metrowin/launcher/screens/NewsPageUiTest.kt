package com.metrowin.launcher.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.NewsItem
import com.metrowin.launcher.news.NewsList
import com.metrowin.launcher.news.NewsPictures
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class NewsPageUiTest : ScreenshotBase() {
    private val pictures = NewsPictures { url, _ ->
        val colour = if (url.endsWith("a.jpg")) Color.rgb(200, 150, 60) else if (url.endsWith("b.jpg")) Color.rgb(60, 140, 90) else Color.rgb(90, 90, 170)
        Bitmap.createBitmap(300, 180, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 300f, 180f, colour, Color.BLACK, Shader.TileMode.CLAMP) })
        }
    }

    private val items = listOf(
        NewsItem("Tech Giants Announce Major AI Breakthroughs", "Tech Daily", "https://e.com/1", System.currentTimeMillis() - 31 * 60_000, "https://c.com/a.jpg"),
        NewsItem("Global Markets Rally on Economic Optimism", "Financial Times", "https://e.com/2", System.currentTimeMillis() - 2 * 3_600_000, "https://c.com/b.jpg"),
        NewsItem("Championship Finals Set After Dramatic Semifinals", "Sports Network", "https://e.com/3", System.currentTimeMillis() - 4 * 3_600_000, "https://c.com/c.jpg"),
        NewsItem("New Smartphone Features Revolutionary Camera", "Gadget Review", "https://e.com/4", System.currentTimeMillis() - 6 * 3_600_000),
    )

    @Test
    fun aFeedWithPicturesIsAPageOfThemAndOneWithoutIsAListOfHeadlines() {
        show { Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) { NewsList(items, pictures, showImages = true) {} } }
        advance(1_200)
        compose.onAllNodesWithTag("nws:item").assertCountEquals(4)
        compose.onAllNodesWithTag("nws:hero", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithTag("nws:thumb", useUnmergedTree = true).assertCountEquals(2)
        capture("news_pictures")
    }

    @Test
    fun withThePicturesOffOrNoneInTheFeedThereAreNoPictureBoxes() {
        show { Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) { NewsList(items, pictures, showImages = false) {} } }
        advance(1_200)
        compose.onAllNodesWithTag("nws:hero", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithTag("nws:thumb", useUnmergedTree = true).assertCountEquals(0)
        capture("news_list")
    }
}
