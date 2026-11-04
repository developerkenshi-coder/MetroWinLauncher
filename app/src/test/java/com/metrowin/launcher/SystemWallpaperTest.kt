package com.metrowin.launcher

import android.graphics.Bitmap
import android.graphics.Color
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.PhotoFit
import com.metrowin.launcher.data.SystemWallpaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SystemWallpaperTest {
    @Test
    fun aPhotoThatIsWiderThanTheScreenIsCroppedAtTheSidesAndKeptWhereAsked() {
        // 400 x 100 photo on a 100 x 200 screen: the height decides (scale 2), 800 wide, so 700 are cropped.
        val centre = SystemWallpaper.fillPlacement(400, 100, 100, 200, zoom = 100, x = 50, y = 50)
        assertEquals(2f, centre.scale, 0f)
        assertEquals(-350f, centre.left, 0.01f)
        assertEquals(0f, centre.top, 0.01f)
        assertEquals(0f, SystemWallpaper.fillPlacement(400, 100, 100, 200, 100, x = 0, y = 50).left, 0.01f)
        assertEquals(-700f, SystemWallpaper.fillPlacement(400, 100, 100, 200, 100, x = 100, y = 50).left, 0.01f)
        // Zoomed, it is bigger and there is more to crop in both directions.
        val zoomed = SystemWallpaper.fillPlacement(400, 100, 100, 200, zoom = 200, x = 50, y = 50)
        assertEquals(4f, zoomed.scale, 0f)
        assertEquals(-100f, zoomed.top, 0.01f)
    }

    @Test
    fun withNoPhotoTheWallpaperIsThePlainColour() {
        val picture = SystemWallpaper.render(null, PhotoFit.Fill, 100, 50, 50, Color.BLACK, 10, 20)
        assertEquals(10, picture.width)
        assertEquals(20, picture.height)
        assertEquals(Color.BLACK, picture.getPixel(5, 10))
        assertEquals(Color.WHITE, SystemWallpaper.render(null, PhotoFit.Fill, 100, 50, 50, Color.WHITE, 4, 4).getPixel(1, 1))
    }

    @Test
    fun aPhotoIsDrawnWhereStartDrawsItWithTheVeilOverIt() {
        // Left half red, right half blue, 2 x 1: on a square screen filled and centred, the left of the screen is red and the right blue.
        val photo = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888).apply { setPixel(0, 0, Color.RED); setPixel(1, 0, Color.BLUE) }
        val picture = SystemWallpaper.render(photo, PhotoFit.Fill, 100, 50, 50, Color.BLACK, 20, 20)
        val left = picture.getPixel(2, 10)
        val right = picture.getPixel(17, 10)
        assert(Color.red(left) > 180 && Color.blue(left) < 70) { "left is red" }
        assert(Color.blue(right) > 180 && Color.red(right) < 70) { "right is blue" }
        // The veil darkens the photo a little: not the full colour.
        assert(Color.red(left) < 255)
        // Fit keeps all of the photo with bars (the colour) above and below.
        val fit = SystemWallpaper.render(photo, PhotoFit.Fit, 100, 50, 50, Color.BLACK, 20, 20)
        assertEquals(Color.BLACK, fit.getPixel(10, 1))
    }

    @Test
    fun theKeyChangesWithWhatThePictureIsMadeOf() {
        val s = LauncherSettings()
        val base = SystemWallpaper.key(s, dark = true, width = 1080, height = 2400)
        assertEquals(base, SystemWallpaper.key(s, true, 1080, 2400))
        assertNotEquals(base, SystemWallpaper.key(s, false, 1080, 2400))
        assertNotEquals(base, SystemWallpaper.key(s.copy(backgroundRevision = 3), true, 1080, 2400))
        assertNotEquals(base, SystemWallpaper.key(s.copy(startPhotoZoom = 150), true, 1080, 2400))
        assertNotEquals(base, SystemWallpaper.key(s, true, 1080, 2340))
    }
}
