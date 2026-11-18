package com.metrowin.launcher

import android.content.ComponentCallbacks2
import androidx.compose.ui.geometry.Offset
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.photos.PhotoOption
import com.metrowin.launcher.photos.PhotoPrefs
import com.metrowin.launcher.photos.PhotoThumbs
import com.metrowin.launcher.photos.exposureText
import com.metrowin.launcher.photos.fitSize
import com.metrowin.launcher.photos.matrixFor
import com.metrowin.launcher.photos.panLimit
import com.metrowin.launcher.photos.pullProgress
import com.metrowin.launcher.photos.sampleFor
import com.metrowin.launcher.photos.zoomAbout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhotoViewerMathTest {
    @Test
    fun aPictureThatFitsCannotBeMovedAndOneThatDoesNotCanByHalfTheExcess() {
        assertEquals(0f, panLimit(1000f, 800f, 1f), 0f)
        assertEquals(0f, panLimit(1000f, 400f, 2f), 0f)
        assertEquals(500f, panLimit(1000f, 1000f, 2f), 0.001f)
    }

    @Test
    fun fittingKeepsTheProportionsAndASizelessPictureFillsTheBox() {
        val (w, h) = fitSize(4000f, 3000f, 1080f, 2000f)
        assertEquals(1080f, w, 0.01f)
        assertEquals(810f, h, 0.01f)
        assertEquals(1080f to 2000f, fitSize(0f, 0f, 1080f, 2000f))
    }

    @Test
    fun theZoomKeepsThePointUnderTheFingersStill() {
        val centroid = Offset(100f, -50f)
        val moved = zoomAbout(Offset.Zero, centroid, 1f, 2f)
        // The point of the picture under the centroid at scale 1 (offset zero) is still under it at scale 2: offset + 2 * point = centroid.
        assertEquals(centroid, moved + centroid * 2f)
        assertEquals(Offset.Zero, zoomAbout(Offset.Zero, Offset.Zero, 1f, 3f))
    }

    @Test
    fun theSampleShrinksByPowersOfTwoUntilTheLongestSideFits() {
        assertEquals(1, sampleFor(1000, 800))
        assertEquals(2, sampleFor(4000, 3000))
        assertEquals(4, sampleFor(8000, 6000))
        assertEquals(1, sampleFor(2048, 100))
    }

    @Test
    fun aPullDownGrowsToOneAtAThirdOfTheHeightAndNeverBackwards() {
        assertEquals(0f, pullProgress(0f, 1000f), 0f)
        assertEquals(0.5f, pullProgress(175f, 1000f), 0.001f)
        assertEquals(1f, pullProgress(900f, 1000f), 0f)
        assertEquals(0f, pullProgress(-50f, 1000f), 0f)
        assertEquals(0f, pullProgress(50f, 0f), 0f)
    }

    @Test
    fun exposureListsOnlyWhatThePictureHas() = withLocale {
        assertEquals("f/1.8 · 1/120 s · ISO 100 · 5.4 mm", exposureText(1.8, 1 / 120.0, 100, 5.4))
        assertEquals("ISO 400", exposureText(0.0, 0.0, 400, 0.0))
        assertEquals("2.0 s", exposureText(0.0, 2.0, 0, 0.0))
        assertEquals("", exposureText(0.0, 0.0, 0, 0.0))
    }

    @Test
    fun everyExifOrientationThatTurnsAPictureHasAMatrixAndTheNormalOneNone() {
        assertNull(matrixFor(ExifInterface.ORIENTATION_NORMAL))
        assertNull(matrixFor(ExifInterface.ORIENTATION_UNDEFINED))
        listOf(2, 3, 4, 5, 6, 7, 8).forEach { assertNotNull("orientation $it", matrixFor(it)) }
    }

    @Test
    fun theThumbnailCacheIsAnEighthOfTheMemoryAtMost24Mb() {
        assertEquals(24 * 1024 * 1024, PhotoThumbs.budget(1024L * 1024 * 1024))
        assertEquals(16 * 1024 * 1024, PhotoThumbs.budget(128L * 1024 * 1024))
        PhotoThumbs.trim(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    }

    @Test
    fun theOptionsHaveTheirDefaultsAndKeepWhatTheUserSets() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val prefs = PhotoPrefs(context)
        // What leaves the phone, or changes the look of the viewer much, is off.
        assertFalse(prefs[PhotoOption.StripMetadata])
        assertFalse(prefs[PhotoOption.Filmstrip])
        assertTrue(prefs[PhotoOption.Select])
        prefs.set(PhotoOption.Filmstrip, true)
        prefs.cell = 5_000
        assertTrue(PhotoPrefs(context)[PhotoOption.Filmstrip])
        assertEquals(PhotoPrefs.MAX_CELL, PhotoPrefs(context).cell)
        assertEquals(PhotoOption.entries.size, PhotoOption.entries.map { it.key }.toSet().size)
    }
}
