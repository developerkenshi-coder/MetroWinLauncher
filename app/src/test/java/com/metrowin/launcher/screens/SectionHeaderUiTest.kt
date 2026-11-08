package com.metrowin.launcher.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.sectionHeader
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The heading of a section stays at the top while its rows scroll, and the next one takes its place. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class SectionHeaderUiTest : ScreenshotBase() {
    @Test
    fun theHeadingOfTheSectionOnScreenStaysAtTheTop() {
        lateinit var jump: (Int) -> Unit
        show {
            val state = rememberLazyListState()
            val scope = rememberCoroutineScope()
            jump = { index -> scope.launch { state.scrollToItem(index) } }
            Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), state = state) {
                    listOf("A", "B").forEach { letter ->
                        sectionHeader("h$letter") { MetroText(letter, size = 20.sp, modifier = Modifier.testTag("head:$letter")) }
                        items(List(60) { "$letter$it" }, key = { it }) { MetroText(it, size = 20.sp, modifier = Modifier.testTag("row:$it")) }
                    }
                }
            }
        }
        advance(300)
        // Ten rows down in A: A's heading is still there, at the top.
        compose.runOnIdle { jump(10) }
        advance(300)
        compose.onNodeWithTag("head:A").assertIsDisplayed()
        assertEquals(0f, compose.onNodeWithTag("head:A").getUnclippedBoundsInRoot().top.value, 1f)
        // In B: B's heading has taken its place. (Sixty rows a section: a section must be longer than the screen, whatever the line height of the font, or its heading cannot reach the top.)
        compose.runOnIdle { jump(70) }
        advance(300)
        assertEquals(0f, compose.onNodeWithTag("head:B").getUnclippedBoundsInRoot().top.value, 1f)
    }
}
