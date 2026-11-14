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

/** The faint ink of the lines of the paper. */
private val PaperInk = Color(0x33607080)

/** The pattern of paper number [paper] (see [Note.paper]) on a page whose width is the width of the scope: squares, lines or dots, 50 units apart. */
fun DrawScope.drawPaper(paper: Int) {
    if (paper <= 0) return
    val k = size.width / SketchPage.WIDTH
    val step = 50f * k
    val line = maxOf(1f, k * 1.5f)
    var y = step
    while (y < size.height) {
        if (paper == 1 || paper == 2) drawLine(PaperInk, Offset(0f, y), Offset(size.width, y), line)
        if (paper == 3) {
            var x = step
            while (x < size.width) { drawCircle(PaperInk, line * 1.3f, Offset(x, y)); x += step }
        }
        y += step
    }
    if (paper == 1) {
        var x = step
        while (x < size.width) { drawLine(PaperInk, Offset(x, 0f), Offset(x, size.height), line); x += step }
    }
}

/** Draws [strokes] on a page whose width is the width of the scope ([SketchPage.WIDTH] units across). */
fun DrawScope.drawStrokes(strokes: List<Stroke>) {
    val k = size.width / SketchPage.WIDTH
    // The highlighter goes under everything else, as a marker goes over the words and not over the pen that wrote them. Two passes, no new lists: it is drawn at every frame.
    for (i in strokes.indices) if (strokes[i].tool == Tool.Highlighter) drawStroke(strokes[i], k)
    for (i in strokes.indices) if (strokes[i].tool != Tool.Highlighter) drawStroke(strokes[i], k)
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

/** The sketch as a PNG, [width] pixels across, on the paper (number [paper], see [drawPaper]). */
fun sketchPng(strokes: List<Stroke>, width: Int = 1000, paper: Int = 0): ByteArray {
    val height = (width * SketchPage.HEIGHT / SketchPage.WIDTH).toInt()
    val image = ImageBitmap(width, height)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(width.toFloat(), height.toFloat())) {
        drawRect(SketchPaper)
        drawPaper(paper)
        drawStrokes(strokes)
    }
    return ByteArrayOutputStream().also { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}
