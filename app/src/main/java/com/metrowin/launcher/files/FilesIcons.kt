package com.metrowin.launcher.files

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.metrowin.launcher.ui.theme.LocalMetroColors

/** The signs of the buttons and menus of the file manager, drawn (the font of the launcher has no signs for them). [symbol] is the text the Retro bar shows. */
enum class FilesIcon(val symbol: String) {
    New("＋"), Paste("⎘"), Select("☑"), View("⇅"), Categories("☰"), More("⋯"), Copy("⧉"), Cut("✂"), Delete("✕"), Share("⇧"), Compress("▣"), Rename("✎"),
    Trash("✕"), Chevron("›"), Folder("▤"), Info("i"), Star("★"), Search("⌕"), Check("✓"),
}

/** [icon] drawn in a square of [size] with a line of 1/12 of it. */
@Composable
fun FilesGlyph(icon: FilesIcon, size: Dp, modifier: Modifier = Modifier, color: Color = LocalMetroColors.current.foreground) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        val w = 2f * u
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), w, StrokeCap.Square)
        fun box(x: Float, y: Float, bw: Float, bh: Float, fill: Boolean = false) =
            drawRect(color, Offset(x * u, y * u), Size(bw * u, bh * u), style = if (fill) Fill else Stroke(w))
        fun ring(x: Float, y: Float, r: Float, fill: Boolean = false) = drawCircle(color, r * u, Offset(x * u, y * u), style = if (fill) Fill else Stroke(w))
        when (icon) {
            FilesIcon.New -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
            FilesIcon.Paste -> { box(6f, 5f, 12f, 15f); box(9f, 3f, 6f, 4f, true) }
            FilesIcon.Select -> { box(4f, 4f, 16f, 16f); line(8f, 12f, 11f, 15f); line(11f, 15f, 17f, 8f) }
            FilesIcon.View -> { line(7f, 5f, 7f, 20f); line(4f, 17f, 7f, 20f); line(7f, 20f, 10f, 17f); line(17f, 19f, 17f, 4f); line(14f, 7f, 17f, 4f); line(17f, 4f, 20f, 7f) }
            FilesIcon.Categories -> { line(4f, 7f, 20f, 7f); line(4f, 12f, 20f, 12f); line(4f, 17f, 20f, 17f) }
            FilesIcon.More -> { ring(5f, 12f, 1.8f, true); ring(12f, 12f, 1.8f, true); ring(19f, 12f, 1.8f, true) }
            FilesIcon.Copy -> { box(9f, 9f, 11f, 11f); box(4f, 4f, 11f, 11f) }
            FilesIcon.Cut -> { ring(7f, 17f, 3f); ring(17f, 17f, 3f); line(9f, 15f, 18f, 4f); line(15f, 15f, 6f, 4f) }
            FilesIcon.Delete, FilesIcon.Trash -> { box(6f, 8f, 12f, 12f); line(4f, 8f, 20f, 8f); box(9f, 4f, 6f, 4f) }
            FilesIcon.Share -> { line(12f, 16f, 12f, 4f); line(7f, 9f, 12f, 4f); line(12f, 4f, 17f, 9f); line(5f, 14f, 5f, 20f); line(5f, 20f, 19f, 20f); line(19f, 20f, 19f, 14f) }
            FilesIcon.Compress -> { box(4f, 4f, 16f, 16f); line(12f, 4f, 12f, 12f); box(10f, 12f, 4f, 4f, true) }
            FilesIcon.Rename -> { line(5f, 19f, 6f, 15f); line(6f, 15f, 16f, 5f); line(16f, 5f, 19f, 8f); line(19f, 8f, 9f, 18f); line(9f, 18f, 5f, 19f) }
            FilesIcon.Chevron -> { line(9f, 5f, 16f, 12f); line(16f, 12f, 9f, 19f) }
            FilesIcon.Folder -> { box(3f, 7f, 18f, 12f); box(3f, 4f, 8f, 3f, true) }
            FilesIcon.Info -> { ring(12f, 12f, 9f); line(12f, 11f, 12f, 17f); ring(12f, 7.5f, 1.2f, true) }
            FilesIcon.Star -> { line(12f, 3f, 14.5f, 9f); line(14.5f, 9f, 21f, 9.5f); line(21f, 9.5f, 16f, 14f); line(16f, 14f, 17.5f, 21f); line(17.5f, 21f, 12f, 17f); line(12f, 17f, 6.5f, 21f); line(6.5f, 21f, 8f, 14f); line(8f, 14f, 3f, 9.5f); line(3f, 9.5f, 9.5f, 9f); line(9.5f, 9f, 12f, 3f) }
            FilesIcon.Search -> { ring(10f, 10f, 6f); line(14.5f, 14.5f, 20f, 20f) }
            FilesIcon.Check -> { line(5f, 12f, 10f, 17f); line(10f, 17f, 19f, 7f) }
        }
    }
}
