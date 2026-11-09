package com.metrowin.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHighlightTest {
    @Test
    fun `the word searched for is marked wherever it is, in any case`() {
        val marked = com.metrowin.launcher.ui.components.highlighted("Camera and CAMERA roll", " camera ", androidx.compose.ui.graphics.Color.Red)
        assertEquals(listOf(0 to 6, 11 to 17), marked.spanStyles.map { it.start to it.end })
        assertEquals(0, com.metrowin.launcher.ui.components.highlighted("Notes", "", androidx.compose.ui.graphics.Color.Red).spanStyles.size)
    }
}
