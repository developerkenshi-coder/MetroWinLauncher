package com.metrowin.launcher.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.ui.theme.DefaultAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.MetroTheme
import org.junit.Rule
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Renders composables on a 360x760dp phone with Robolectric's native graphics, so the real Skia
 * drawing and the 3D transforms run on the JVM. Every capture is also saved as
 * `app/build/screenshots/<name>.png` for a quick look.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotBase {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    protected val context: Context get() = ApplicationProvider.getApplicationContext()

    /** The test clock is paused, so animations only move when the test calls [advance]. */
    protected fun show(dark: Boolean = true, accent: Color = DefaultAccent, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MetroTheme(accent, dark) {
                Box(Modifier.size(360.dp, 760.dp).background(LocalMetroColors.current.background)) { content() }
            }
        }
    }

    protected fun advance(ms: Long) = compose.mainClock.advanceTimeBy(ms)

    protected fun capture(name: String): Bitmap {
        // Compose's own captureToImage waits for a real window draw, which Robolectric does not deliver
        // while the test clock is paused. Drawing the decor view into a bitmap works at any frame.
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    protected fun Bitmap.pixels(): IntArray = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }

    protected fun isAccent(pixel: Int) = pixel and 0xFFFFFF == DefaultAccent.toArgb() and 0xFFFFFF

    /** Share of the picture painted in the accent colour: 0 when tiles are hidden, large when they are shown. */
    protected fun Bitmap.accentShare(): Float = pixels().count(::isAccent).toFloat() / (width * height)
}
