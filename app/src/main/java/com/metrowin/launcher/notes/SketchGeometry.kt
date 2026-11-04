package com.metrowin.launcher.notes

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Where a stroke of a sketch is, for the eraser. Pure. */
object SketchGeometry {
    /** The size of the text of a text stroke, in units of the page. */
    fun textSize(stroke: Stroke) = stroke.width * 5f + 20f

    /** Whether the point ([x], [y]) is within [radius] of the lines of [stroke] (or, for a text, of its box). */
    fun hit(stroke: Stroke, x: Float, y: Float, radius: Float): Boolean {
        val reach = radius + stroke.width / 2f
        val p = stroke.points
        if (p.isEmpty()) return false
        return when (stroke.tool) {
            Tool.Pen -> if (p.size == 1) hypot(x - p[0].x, y - p[0].y) <= reach else p.zipWithNext().any { (a, b) -> near(a, b, x, y, reach) }
            Tool.Line, Tool.Arrow -> p.size >= 2 && near(p[0], p[1], x, y, reach)
            Tool.Rect -> p.size >= 2 && outline(p[0], p[1]).any { (a, b) -> near(a, b, x, y, reach) }
            Tool.Ellipse -> p.size >= 2 && ellipse(p[0], p[1]).zipWithNext().any { (a, b) -> near(a, b, x, y, reach) }
            Tool.Text -> {
                val size = textSize(stroke)
                val width = max(1, stroke.text.length) * size * 0.55f
                x >= p[0].x - radius && x <= p[0].x + width + radius && y >= p[0].y - size - radius && y <= p[0].y + radius
            }
        }
    }

    /** The four sides of the rectangle with the corners [a] and [b]. */
    fun outline(a: Pt, b: Pt): List<Pair<Pt, Pt>> {
        val left = min(a.x, b.x)
        val right = max(a.x, b.x)
        val top = min(a.y, b.y)
        val bottom = max(a.y, b.y)
        val corners = listOf(Pt(left, top), Pt(right, top), Pt(right, bottom), Pt(left, bottom), Pt(left, top))
        return corners.zipWithNext()
    }

    /** The ellipse in the rectangle with the corners [a] and [b], as 48 points round it. */
    fun ellipse(a: Pt, b: Pt): List<Pt> {
        val cx = (a.x + b.x) / 2f
        val cy = (a.y + b.y) / 2f
        val rx = kotlin.math.abs(b.x - a.x) / 2f
        val ry = kotlin.math.abs(b.y - a.y) / 2f
        return List(49) { Pt(cx + rx * cos(it * Math.PI * 2 / 48).toFloat(), cy + ry * sin(it * Math.PI * 2 / 48).toFloat()) }
    }

    /** The two ends of the head of an arrow that points to [to] from [from]. */
    fun arrowHead(from: Pt, to: Pt, length: Float): Pair<Pt, Pt> {
        val angle = Math.atan2((to.y - from.y).toDouble(), (to.x - from.x).toDouble())
        fun end(turn: Double) = Pt(to.x - length * cos(angle + turn).toFloat(), to.y - length * sin(angle + turn).toFloat())
        return end(0.45) to end(-0.45)
    }

    private fun near(a: Pt, b: Pt, x: Float, y: Float, reach: Float): Boolean {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val length2 = dx * dx + dy * dy
        val t = if (length2 == 0f) 0f else (((x - a.x) * dx + (y - a.y) * dy) / length2).coerceIn(0f, 1f)
        return hypot(x - (a.x + t * dx), y - (a.y + t * dy)) <= reach
    }
}
