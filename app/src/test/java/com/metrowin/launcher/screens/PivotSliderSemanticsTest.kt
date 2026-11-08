package com.metrowin.launcher.screens

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.metrowin.launcher.ui.components.MetroSlider
import com.metrowin.launcher.ui.components.PivotHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What a screen reader gets from the pivot titles and the slider made by hand: roles, the selected page, the range and its value. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class PivotSliderSemanticsTest : ScreenshotBase() {
    @Test
    fun pivotTitlesAreTabsAndTheCurrentOneIsSelected() {
        show {
            val pager = rememberPagerState(initialPage = 1) { 3 }
            PivotHeader(listOf("one", "two", "three"), pager)
        }
        advance(200)
        compose.onNodeWithText("two").assertIsSelected()
        val role = compose.onNodeWithText("one").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role)
        assertEquals(Role.Tab, role)
        assertNotNull(compose.onNode(hasText("three")).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Heading))
    }

    @Test
    fun theSliderIsARangeThatCanBeSet() {
        var level by mutableFloatStateOf(0.25f)
        show { MetroSlider(value = level, onChange = { level = it }, description = "Volume") }
        val config = compose.onNodeWithContentDescription("Volume").fetchSemanticsNode().config
        assertEquals(ProgressBarRangeInfo(0.25f, 0f..1f), config.getOrNull(SemanticsProperties.ProgressBarRangeInfo))
        assertNotNull(config.getOrNull(SemanticsActions.SetProgress))
        config[SemanticsActions.SetProgress].action!!.invoke(0.75f)
        assertEquals(0.75f, level, 0f)
    }
}
