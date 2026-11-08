package com.metrowin.launcher

import com.metrowin.launcher.data.GlassIcon
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.SettingsCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassIconTest {
    private val green = 0xFF25D366.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun filledSquareLosesItsBackgroundAndKeepsThePicture() {
        // 20x20 green square with a white 6x6 mark in the middle (a legacy icon).
        val w = 20
        val px = IntArray(w * w) { i -> if (i % w in 7..12 && i / w in 7..12) white else green }
        val out = GlassIcon.of(px, w, w)
        assertEquals(0, out[0])
        assertTrue(out[10 * w + 10] ushr 24 > 150)
    }

    @Test
    fun transparentEdgesStayTransparentAndPictureIsTranslucent() {
        val w = 20
        val px = IntArray(w * w) { i -> if (i % w in 5..14 && i / w in 5..14) white else 0 }
        val out = GlassIcon.of(px, w, w)
        assertEquals(0, out[0])
        val a = out[10 * w + 10] ushr 24
        assertTrue(a in 150..230)
    }

    @Test
    fun glassSettingSurvivesTheFile() {
        val s = LauncherSettings(glassIcons = true)
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }
}
