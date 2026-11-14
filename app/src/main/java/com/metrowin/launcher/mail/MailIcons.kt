package com.metrowin.launcher.mail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalMetroColors

/** The small drawn signs of Mail, in place of the emoji (which each phone draws in its own colours, and some do not have). */
enum class MailGlyph { Flag, File, Image, Envelope, Dot }

private class Shape(val data: String, val fill: Boolean)

private fun shapes(glyph: MailGlyph): List<Shape> = when (glyph) {
    MailGlyph.Flag -> listOf(Shape("M28 12L28 90", false), Shape("M28 16L82 32L28 52Z", true))
    MailGlyph.File -> listOf(Shape("M24 8H60L80 28V92H24Z", false), Shape("M60 8V28H80", false))
    MailGlyph.Image -> listOf(Shape("M10 20H90V80H10Z", false), Shape("M10 70L36 44L56 64L68 52L90 74", false))
    MailGlyph.Envelope -> listOf(Shape("M8 20H92V80H8Z", false), Shape("M8 22L50 56L92 22", false))
    MailGlyph.Dot -> listOf(Shape("M50 28a22 22 0 1 0 0.01 0Z", true))
}

/** [glyph] drawn in [color] inside a square of [size]. */
@Composable
fun MailGlyphIcon(glyph: MailGlyph, size: Dp, modifier: Modifier = Modifier, color: Color = LocalMetroColors.current.foreground) {
    val paths = remember(glyph) { shapes(glyph).map { it.fill to PathParser().parsePathString(it.data).toPath() } }
    Canvas(modifier.size(size)) {
        scale(this.size.minDimension / 100f, pivot = Offset.Zero) {
            paths.forEach { (fill, path) ->
                drawPath(path, color, style = if (fill) Fill else Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** The sign of an attached file by its kind: a picture frame for an image, else a page with the first letters of its extension in it. */
@Composable
fun FileKindIcon(name: String, mime: String, size: Dp, color: Color = LocalMetroColors.current.foreground) {
    if (mime.startsWith("image/")) {
        MailGlyphIcon(MailGlyph.Image, size, color = color)
        return
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        MailGlyphIcon(MailGlyph.File, size, Modifier.fillMaxSize(), color)
        val ext = name.substringAfterLast('.', "").take(3).uppercase()
        if (ext.isNotEmpty() && size >= 24.dp) MetroText(ext, size = (size.value * 0.26f).sp, color = color, modifier = Modifier.then(Modifier))
    }
}
