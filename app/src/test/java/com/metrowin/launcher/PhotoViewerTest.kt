package com.metrowin.launcher

import com.metrowin.launcher.photos.panLimit
import com.metrowin.launcher.photos.sampleFor
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoViewerTest {
    @Test
    fun `a big picture is read down by a power of two and a small one whole`() {
        assertEquals(1, sampleFor(1000, 800, 2048))
        assertEquals(2, sampleFor(4000, 3000, 2048))
        assertEquals(4, sampleFor(12000, 9000, 2048))
    }

    @Test
    fun `a zoomed picture can be dragged only as far as its edge`() {
        assertEquals(0f, panLimit(1080f, 1f), 0f)
        assertEquals(540f, panLimit(1080f, 2f), 0.001f)
        assertEquals(0f, panLimit(1080f, 0.5f), 0f)
    }

    @Test
    fun `the word searched for is marked wherever it is, in any case`() {
        val marked = com.metrowin.launcher.ui.components.highlighted("Camera and CAMERA roll", " camera ", androidx.compose.ui.graphics.Color.Red)
        assertEquals(listOf(0 to 6, 11 to 17), marked.spanStyles.map { it.start to it.end })
        assertEquals(0, com.metrowin.launcher.ui.components.highlighted("Notes", "", androidx.compose.ui.graphics.Color.Red).spanStyles.size)
    }
}
