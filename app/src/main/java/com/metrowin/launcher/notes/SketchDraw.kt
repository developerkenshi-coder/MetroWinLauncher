package com.metrowin.launcher.notes

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.io.ByteArrayOutputStream

/** The paper of a sketch: always light, so a sketch is the same in the app and in the picture that is exported. */
val SketchPaper = Color(0xFFFAFAF5)

/** Draws [strokes] on a page whose width is the width of the scope ([SketchPage.WIDTH] units across). */
fun DrawScope.drawStrokes(strokes: List<Stroke>) {
    val k = size.width / SketchPage.WIDTH
    // The highlighter goes under everything else, as a marker goes over the words and not over the pen that wrote them.
    strokes.filter { it.tool == Tool.Highlighter }.forEach { drawStroke(it, k) }
    strokes.filter { it.tool != Tool.Highlighter }.forEach { drawStroke(it, k) }
}

fun DrawScope.drawStroke(stroke: Stroke, k: Float) {
    val marker = stroke.tool == Tool.Highlighter
    val color = Color(stroke.color).let { if (marker) it.copy(alpha = 0.4f) else it }
    val style = DrawStroke(width = SketchGeometry.widthOf(stroke) * k, cap = if (marker) StrokeCap.Square else StrokeCap.Round, join = StrokeJoin.Round)
    fun at(p: Pt) = Offset(p.x * k, p.y * k)
    val p = stroke.points
    if (p.isEmpty()) return
    when (stroke.tool) {
        Tool.Pen, Tool.Highlighter -> if (p.size == 1) {
            drawCircle(color, radius = SketchGeometry.widthOf(stroke) * k / 2f, center = at(p[0]))
        } else {
            val path = Path().apply {
                moveTo(p[0].x * k, p[0].y * k)
                // A curve through the middle of each pair of points: a hand-drawn line without corners.
                for (i in 1 until p.size - 1) quadraticTo(p[i].x * k, p[i].y * k, (p[i].x + p[i + 1].x) / 2f * k, (p[i].y + p[i + 1].y) / 2f * k)
                lineTo(p.last().x * k, p.last().y * k)
            }
            drawPath(path, color, style = style)
        }
        Tool.Line -> if (p.size >= 2) drawLine(color, at(p[0]), at(p[1]), stroke.width * k, StrokeCap.Round)
        Tool.Arrow -> if (p.size >= 2) {
            drawLine(color, at(p[0]), at(p[1]), stroke.width * k, StrokeCap.Round)
            val (a, b) = SketchGeometry.arrowHead(p[0], p[1], 20f + stroke.width * 3f)
            drawLine(color, at(p[1]), at(a), stroke.width * k, StrokeCap.Round)
            drawLine(color, at(p[1]), at(b), stroke.width * k, StrokeCap.Round)
        }
        Tool.Rect -> if (p.size >= 2) {
            val a = at(p[0])
            val b = at(p[1])
            drawRect(color, Offset(minOf(a.x, b.x), minOf(a.y, b.y)), Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)), style = style)
        }
        Tool.Ellipse -> if (p.size >= 2) {
            val a = at(p[0])
            val b = at(p[1])
            drawOval(color, Offset(minOf(a.x, b.x), minOf(a.y, b.y)), Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)), style = style)
        }
        Tool.Text -> drawIntoCanvas { canvas ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color.toArgb()
                textSize = SketchGeometry.textSize(stroke) * k
                typeface = Typeface.DEFAULT
            }
            // A text can have several lines; the first starts at the point.
            stroke.text.split('\n').forEachIndexed { line, text -> canvas.nativeCanvas.drawText(text, p[0].x * k, p[0].y * k + line * paint.textSize * 1.2f, paint) }
        }
    }
}

/** The sketch as a PNG, [width] pixels across, on the paper. */
fun sketchPng(strokes: List<Stroke>, width: Int = 1000): ByteArray {
    val height = (width * SketchPage.HEIGHT / SketchPage.WIDTH).toInt()
    val image = ImageBitmap(width, height)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(width.toFloat(), height.toFloat())) {
        drawRect(SketchPaper)
        drawStrokes(strokes)
    }
    return ByteArrayOutputStream().also { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}
