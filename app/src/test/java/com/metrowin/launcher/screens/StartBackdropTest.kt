package com.metrowin.launcher.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.metrowin.launcher.data.PhotoFit
import com.metrowin.launcher.ui.gesture.LauncherPager
import com.metrowin.launcher.ui.start.ParallaxBackground
import com.metrowin.launcher.ui.start.PhotoFrame
import com.metrowin.launcher.ui.start.StartBackdrop
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Start photo: no bare band under it when Start is scrolled, and it stays put while the Start screens change. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class StartBackdropTest : ScreenshotBase() {
    private fun photo(color: Int): ImageBitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }.asImageBitmap()

    private fun pixels(name: String): IntArray = capture(name).let { b -> IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) } }

    private fun brightness(bitmap: Bitmap, x: Int, y: Int) = AndroidColor.red(bitmap.getPixel(x, y))

    private fun coversAllWhenScrolled(zoom: Int, y: Int) {
        show { ParallaxBackground(photo(AndroidColor.WHITE), { 100_000 }, parallax = true, PhotoFrame(zoom, 50, y), PhotoFit.Fill) }
        advance(100)
        val shot = capture("backdrop-fill-$zoom-$y")
        // The photo is white under a shade of 12 per cent: a bare band would be black.
        for (row in listOf(0, shot.height / 2, shot.height - 1)) assertTrue("row $row is bare", brightness(shot, shot.width / 2, row) > 200)
    }

    @Test
    fun aFilledPhotoStillCoversTheBottomWhenStartIsScrolledAsFarAsTheParallaxGoes() = coversAllWhenScrolled(zoom = 100, y = 50)

    @Test
    fun andSoWhenZoomedAndKeptAtTheTop() = coversAllWhenScrolled(zoom = 250, y = 0)

    @Test
    fun orKeptAtTheBottom() = coversAllWhenScrolled(zoom = 150, y = 100)

    private lateinit var pager: PagerState
    private val scrolls = List(2) { ScrollState(0) }

    @Composable
    private fun Starts(photos: List<ImageBitmap>) {
        pager = rememberPagerState { 3 }
        Box(Modifier.fillMaxSize()) {
            LauncherPager(
                state = pager, startPages = 2, userScrollEnabled = true,
                behind = { StartBackdrop(pager, 2, false, scrolls, { photos[it] }, true, PhotoFrame(), PhotoFit.Fill) },
                start = { Box(Modifier.fillMaxSize()) },
                apps = { Box(Modifier.fillMaxSize()) },
            )
        }
    }

    private fun goTo(page: Int) {
        compose.runOnUiThread { runBlocking { pager.scrollToPage(page) } }
        advance(100)
    }

    @Test
    fun theSamePhotoDoesNotMoveFromOneStartToTheNext() {
        val one = photo(AndroidColor.WHITE)
        show { Starts(listOf(one, one)) }
        advance(100)
        val first = pixels("backdrop-start-1")
        goTo(1)
        assertArrayEquals(first, pixels("backdrop-start-2"))
    }

    @Test
    fun aPhotoOfItsOwnShowsOnceItsStartIsInView() {
        show { Starts(listOf(photo(AndroidColor.WHITE), photo(AndroidColor.BLACK))) }
        advance(100)
        assertTrue(brightness(capture("backdrop-own-1"), 180, 380) > 200)
        goTo(1)
        assertTrue(brightness(capture("backdrop-own-2"), 180, 380) < 20)
    }
}
