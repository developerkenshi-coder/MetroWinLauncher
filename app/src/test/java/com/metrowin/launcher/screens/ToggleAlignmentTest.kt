package com.metrowin.launcher.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.ThemeStyle
import com.metrowin.launcher.ui.components.TOGGLE_THUMB_TAG
import com.metrowin.launcher.ui.components.TOGGLE_TRACK_TAG
import com.metrowin.launcher.ui.components.MetroToggle
import com.metrowin.launcher.ui.components.ToggleGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The switches of the three looks, drawn and measured: the block that slides in the track has the same room around it at
 * both ends of its travel and above and below, and never touches the border.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "xhdpi")
class ToggleAlignmentTest : ScreenshotBase() {
    private var on by mutableStateOf(false)
    private var changes = 0

    private fun showToggle(style: ThemeStyle, label: String = "Wi-Fi") {
        show(style = style) {
            Column(Modifier.padding(24.dp)) {
                MetroToggle(label, on, { on = it; changes++ })
            }
        }
    }

    private fun bounds(tag: String): Rect {
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        return Rect(node.positionInRoot, androidx.compose.ui.geometry.Size(node.size.width.toFloat(), node.size.height.toFloat()))
    }

    /** What the test wrote reaches the composition once the test thread is idle; then the animation runs on the paused clock. */
    private fun settle() {
        compose.waitForIdle()
        advance(600)
        compose.waitForIdle()
    }

    /** Space between the thumb and the track: left, top, right, bottom, in pixels. */
    private fun room(): List<Float> {
        val track = bounds(TOGGLE_TRACK_TAG)
        val thumb = bounds(TOGGLE_THUMB_TAG)
        return listOf(thumb.left - track.left, thumb.top - track.top, track.right - thumb.right, track.bottom - thumb.bottom)
    }

    private val density get() = compose.density.density

    // --- Classic

    @Test
    fun theClassicThumbHasTheSameRoomOnEverySideWhenOff() {
        on = false
        showToggle(ThemeStyle.Classic)
        settle()
        capture("toggle_classic_off")
        val (left, top, _, bottom) = room()
        val expected = ToggleGeometry.INSET * density
        assertEquals(expected, left, 1f)
        assertEquals("the thumb is centered in the track", top, bottom, 1f)
        assertEquals(expected, top, 1f)
    }

    @Test
    fun theClassicThumbHasTheSameRoomOnEverySideWhenOn() {
        on = true
        showToggle(ThemeStyle.Classic)
        settle()
        capture("toggle_classic_on")
        val (_, top, right, bottom) = room()
        val expected = ToggleGeometry.INSET * density
        assertEquals("the thumb does not run into the border", expected, right, 1f)
        assertEquals(top, bottom, 1f)
    }

    @Test
    fun theClassicThumbTravelsFromOneEndToTheOtherAndStaysInsideTheBorder() {
        on = false
        showToggle(ThemeStyle.Classic)
        settle()
        val before = room()
        on = true
        settle()
        val after = room()
        // What was the room on the left is the room on the right, and the thumb moved by the whole travel.
        assertEquals(before[0], after[2], 1f)
        assertEquals(ToggleGeometry.TRAVEL * density, after[0] - before[0], 1f)
        val border = ToggleGeometry.BORDER * density
        assertTrue(before[0] > border && after[2] > border)
    }

    // --- every look

    private fun assertSymmetric(style: ThemeStyle) {
        on = false
        showToggle(style)
        settle()
        val off = room()
        capture("toggle_${style.code}_off")
        on = true
        settle()
        val onRoom = room()
        capture("toggle_${style.code}_on")
        // Centered above and below in both states.
        assertEquals("$style off", off[1], off[3], 1f)
        assertEquals("$style on", onRoom[1], onRoom[3], 1f)
        // The room left of the thumb when it is off is the room right of it when it is on.
        assertEquals("$style travel", off[0], onRoom[2], 1.5f)
        // And the thumb is inside the track at both ends.
        assertTrue(off.all { it >= 0f } && onRoom.all { it >= 0f })
    }

    @Test
    fun theGlassSwitchIsStraight() = assertSymmetric(ThemeStyle.Glass)

    @Test
    fun theDroidSwitchIsStraight() = assertSymmetric(ThemeStyle.Droid)

    @Test
    fun theClassicSwitchIsStraight() = assertSymmetric(ThemeStyle.Classic)

    // --- the row

    private val isSwitch = SemanticsMatcher("is a switch") { it.config.getOrNull(SemanticsProperties.Role) == Role.Switch }

    private fun aTouchFlipsIt(style: ThemeStyle) {
        on = false
        changes = 0
        showToggle(style)
        compose.onNode(isSwitch).assertIsOff().performClick()
        settle()
        assertEquals(1, changes)
        compose.onNode(isSwitch).assertIsOn()
    }

    @Test
    fun aTouchOnTheClassicRowFlipsTheSwitchAndTalkBackSeesASwitch() = aTouchFlipsIt(ThemeStyle.Classic)

    @Test
    fun aTouchOnTheGlassRowFlipsTheSwitchAndTalkBackSeesASwitch() = aTouchFlipsIt(ThemeStyle.Glass)

    @Test
    fun aTouchOnTheDroidRowFlipsTheSwitchAndTalkBackSeesASwitch() = aTouchFlipsIt(ThemeStyle.Droid)

    @Test
    fun talkBackCanFlipTheSwitchWithoutATouch() {
        on = false
        changes = 0
        showToggle(ThemeStyle.Classic)
        val node = compose.onNode(isSwitch).fetchSemanticsNode()
        val click = node.config.getOrNull(SemanticsActions.OnClick)!!
        compose.runOnUiThread { click.action!!.invoke() }
        settle()
        assertEquals(1, changes)
        compose.onNode(isSwitch).assertIsOn()
    }

    @Test
    fun theLabelTakesTheSameRoomWhateverTheStateOfTheSwitch() {
        // The words beside the Classic switch ("On", "Off"; in Italian "Attivato", "Disattivato") hold the same room in both states,
        // so a long label wraps the same way before and after a touch.
        val label = "Show the notifications on the tiles of the Action Center and keep them"
        on = false
        showToggle(ThemeStyle.Classic, label)
        settle()
        val off = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().size
        on = true
        settle()
        val onSize = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().size
        assertEquals(off, onSize)
    }
}
