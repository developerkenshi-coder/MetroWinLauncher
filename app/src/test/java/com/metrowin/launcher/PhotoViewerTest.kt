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
}
