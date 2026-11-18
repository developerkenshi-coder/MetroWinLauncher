package com.metrowin.launcher

import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.people.PeopleGlyph
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.Mark
import com.metrowin.launcher.ui.components.markSpec
import com.metrowin.launcher.ui.components.specOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GlyphSpecsTest {
    @Test
    fun everyGlyphAndMarkHasADrawing() {
        Glyph.entries.forEach { assertTrue("$it has parts", specOf(it).parts.isNotEmpty()) }
        Mark.entries.forEach { assertTrue("$it has parts", markSpec(it).parts.isNotEmpty()) }
    }

    @Test
    fun everyPeopleGlyphPointsAtOneDrawing() {
        PeopleGlyph.entries.forEach { assertTrue("$it", (it.glyph == null) != (it.mark == null)) }
    }

    @Test
    fun barSignsFindTheirMark() {
        listOf("＋", "✕", "⚙", "↻", "☰", "⌕", "★", "☆", "✉", "⇄", "▤", "▣", "◷", "⌂", "▦", "✓", "☑", "←").forEach {
            assertNotNull("sign $it", Mark.of(it))
            assertEquals(Mark.of(it), AppBarItem(it, "x") {}.icon)
        }
        // The "…" of the bar stays text, and an explicit mark wins over the sign.
        assertEquals(null, AppBarItem("…", "x") {}.icon)
        assertEquals(Mark.Down, AppBarItem("＋", "x") {}.icon(Mark.Down).icon)
    }
}
