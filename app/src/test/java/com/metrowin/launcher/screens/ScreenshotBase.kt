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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.centerX
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.ui.theme.DefaultAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.MetroTheme
import androidx.compose.ui.unit.DpSize
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
    protected fun show(
        dark: Boolean = true,
        accent: Color = DefaultAccent,
        size: DpSize = DpSize(360.dp, 760.dp),
        content: @Composable () -> Unit,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MetroTheme(accent, dark) {
                Box(Modifier.size(size).background(LocalMetroColors.current.background)) { content() }
            }
        }
    }

    protected fun advance(ms: Long) = compose.mainClock.advanceTimeBy(ms)

    /**
     * Swipes the page up until the control is on screen, then taps it. (performScrollTo animates its scroll, which
     * never finishes while the test clock is paused, so the page is scrolled with a finger instead.)
     */
    protected fun tapScrolled(tag: String) {
        val node = compose.onNodeWithTag(tag)
        val screenHeight = compose.onRoot().fetchSemanticsNode().size.height
        repeat(8) {
            // Position and size are not clipped to the screen, unlike boundsInRoot, which is empty off screen.
            val semantics = node.fetchSemanticsNode()
            val top = semantics.positionInRoot.y
            if (top >= 0f && top + semantics.size.height <= screenHeight) return@repeat
            compose.onAllNodes(hasScrollAction()).onLast().performTouchInput {
                swipe(Offset(centerX, height * 0.85f), Offset(centerX, height * 0.15f), durationMillis = 200)
            }
            // A touch on a page that is still flinging only stops it: wait for it to settle.
            advance(2_500)
        }
        node.performClick()
        // The state written by the click reaches the composition only once the test thread is idle.
        compose.waitForIdle()
    }

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
