package com.metrowin.launcher.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.down
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.theme.DefaultAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class MetroPressTest : ScreenshotBase() {

    /** Height in pixels of the tile at the column [x]. */
    private fun heightAt(bitmap: Bitmap, x: Int): Int =
        (0 until bitmap.height).count { y -> isAccent(bitmap.getPixel(x, y)) }

    /** Left and right extent of the tile. */
    private fun horizontalBounds(bitmap: Bitmap): Pair<Int, Int> {
        val columns = (0 until bitmap.width).filter { x -> (0 until bitmap.height).any { y -> isAccent(bitmap.getPixel(x, y)) } }
        return columns.first() to columns.last()
    }

    @Test
    fun pressedTileSinksAwayFromTheFinger() {
        show {
            Box(Modifier.padding(40.dp)) {
                Box(Modifier.size(200.dp).testTag("tile").metroPress(onClick = {}).background(DefaultAccent))
            }
        }
        advance(50)
        val resting = capture("press_before")
        val (left, right) = horizontalBounds(resting)
        assertEquals("a resting tile is flat", heightAt(resting, left + 10), heightAt(resting, right - 10))

        // Finger on the right edge: that edge should move away from the viewer and look shorter.
        compose.onNodeWithTag("tile").performTouchInput { down(Offset(width - 4f, height / 2f)) }
        advance(500)
        val pressed = capture("press_right_edge")
        val (pressedLeft, pressedRight) = horizontalBounds(pressed)
        val leftHeight = heightAt(pressed, pressedLeft + 10)
        val rightHeight = heightAt(pressed, pressedRight - 10)
        assertTrue("right edge ($rightHeight) should be shorter than left edge ($leftHeight)", rightHeight < leftHeight - 4)
    }
}
