package com.metrowin.launcher.screens

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.Tutorial
import com.metrowin.launcher.ui.tutorial.TutorialScreen
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The tour in Italian, and on a screen that is wider than tall. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "it-w360dp-h760dp-xxhdpi")
class TutorialItalianTest : ScreenshotBase() {
    @Composable
    private fun Tour(onPage: (Int) -> Unit = {}) {
        val pager = rememberPagerState { Tutorial.steps.size }
        onPage(pager.currentPage)
        TutorialScreen(pager, emptySet(), onRequest = {}, onFinish = {})
    }

    @Test
    fun theButtonsAndTheFirstPageAreInItalian() {
        show { Tour() }
        advance(300)
        capture("tutorial_welcome_it")
        compose.onNodeWithTag("tutorial:next").assertTextEquals("avanti")
        compose.onNodeWithTag("tutorial:skip").assertTextEquals("salta")
        compose.onNodeWithTag("tutorial:title").assertTextEquals("benvenuto")
    }

    @Test
    fun theLastPageHasTheItalianButtonThatFits() {
        show { Tour() }
        advance(300)
        repeat(Tutorial.steps.size - 1) {
            compose.onNodeWithTag("tutorial:next").performClick()
            advance(800)
        }
        capture("tutorial_settings_it")
        compose.onNodeWithTag("tutorial:next").assertTextEquals("cominciamo")
        // The whole label is there: it is not cut to "comin…".
        val node = compose.onNodeWithTag("tutorial:next").fetchSemanticsNode()
        assertTrue("the button is wide enough (${node.size.width} px)", node.size.width > 300)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h360dp-land-xxhdpi")
class TutorialLandscapeTest : ScreenshotBase() {
    @Test
    fun theButtonsStayOnScreenInLandscape() {
        show(size = DpSize(800.dp, 360.dp)) {
            val pager = rememberPagerState { Tutorial.steps.size }
            TutorialScreen(pager, emptySet(), onRequest = {}, onFinish = {})
        }
        advance(300)
        capture("tutorial_landscape")
        val next = compose.onNodeWithTag("tutorial:next").fetchSemanticsNode()
        assertTrue("the next button is inside the 360 dp screen", next.positionInRoot.y + next.size.height <= 360f * 3f + 1f)
        compose.onNodeWithTag("tutorial:title").assertTextEquals("welcome")
    }
}
