package com.metrowin.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.metrowin.launcher.ui.start.DragOutcome
import com.metrowin.launcher.ui.start.dragOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class DragOutcomeTest {
    // Two medium tiles side by side, then one below: 0 at the top left, 1 at the top right, 2 below 0.
    private val rects = listOf(
        Rect(0f, 0f, 100f, 100f),
        Rect(110f, 0f, 210f, 100f),
        Rect(0f, 110f, 100f, 210f),
    )
    private val allTopLevel = listOf(true, true, true)

    private fun outcome(center: Offset, dragged: Int = 0, canMerge: Boolean = true, topLevel: List<Boolean> = allTopLevel) =
        dragOutcome(rects, topLevel, dragged, center, canMerge)

    @Test
    fun nothingHappensOverEmptySpaceOrOverItself() {
        assertEquals(DragOutcome.Nothing, outcome(Offset(105f, 105f)))
        assertEquals(DragOutcome.Nothing, outcome(Offset(50f, 50f)))
    }

    @Test
    fun theEdgeOfATileReorders() {
        assertEquals(DragOutcome.Reorder(1), outcome(Offset(115f, 50f)))
        assertEquals(DragOutcome.Reorder(2), outcome(Offset(50f, 205f)))
    }

    @Test
    fun theMiddleOfATileIsAFolderDrop() {
        assertEquals(DragOutcome.Hover(1), outcome(Offset(160f, 50f)))
    }

    @Test
    fun aFolderOnlyReordersBecauseFoldersCannotNest() {
        assertEquals(DragOutcome.Reorder(1), outcome(Offset(160f, 50f), canMerge = false))
    }

    @Test
    fun theAppsOfAnOpenFolderAreNotTargets() {
        assertEquals(DragOutcome.Nothing, outcome(Offset(160f, 50f), topLevel = listOf(true, false, true)))
    }
}
