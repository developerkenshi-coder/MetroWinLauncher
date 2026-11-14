package com.metrowin.launcher.photos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.ui.theme.LocalMetroColors

/** The signs of the Photo hub, drawn as paths (no emoji, no font that may lack them); they take the colour of the text. */
enum class PhotoIcon { Share, Trash, Info, Heart, HeartFilled, Check, Select, Play, Wallpaper, Compare, Zoom, Search, Options }

@Composable
fun PhotoIconView(icon: PhotoIcon, modifier: Modifier = Modifier, size: Dp = 22.dp, color: Color = LocalMetroColors.current.foreground) {
    Canvas(modifier.size(size)) { drawIcon(icon, color) }
}

private fun DrawScope.drawIcon(icon: PhotoIcon, color: Color) {
    val u = size.minDimension / 24f
    val line = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun path(build: Path.() -> Unit) = Path().apply(build)
    when (icon) {
        PhotoIcon.Share -> {
            drawCircle(color, 2.6f * u, p(18f, 5f)); drawCircle(color, 2.6f * u, p(6f, 12f)); drawCircle(color, 2.6f * u, p(18f, 19f))
            drawLine(color, p(8.2f, 10.8f), p(15.8f, 6.2f), 2f * u, StrokeCap.Round); drawLine(color, p(8.2f, 13.2f), p(15.8f, 17.8f), 2f * u, StrokeCap.Round)
        }
        PhotoIcon.Trash -> {
            drawLine(color, p(4f, 6.5f), p(20f, 6.5f), 2f * u, StrokeCap.Round)
            drawPath(path { moveTo(9f, 6.5f); lineTo(9f, 4f); lineTo(15f, 4f); lineTo(15f, 6.5f) }, color, style = line)
            drawPath(path { moveTo(6f, 6.5f); lineTo(7f, 20f); lineTo(17f, 20f); lineTo(18f, 6.5f) }, color, style = line)
            drawLine(color, p(10f, 10f), p(10f, 16.5f), 2f * u, StrokeCap.Round); drawLine(color, p(14f, 10f), p(14f, 16.5f), 2f * u, StrokeCap.Round)
        }
        PhotoIcon.Info -> {
            drawCircle(color, 10f * u, p(12f, 12f), style = line)
            drawLine(color, p(12f, 11f), p(12f, 17f), 2f * u, StrokeCap.Round); drawCircle(color, 1.3f * u, p(12f, 7.4f))
        }
        PhotoIcon.Heart, PhotoIcon.HeartFilled -> {
            val heart = path {
                moveTo(12f * u, 20.5f * u)
                cubicTo(3f * u, 14f * u, 2.5f * u, 8f * u, 6.5f * u, 5.5f * u)
                cubicTo(9f * u, 4f * u, 11.5f * u, 5.5f * u, 12f * u, 7.5f * u)
                cubicTo(12.5f * u, 5.5f * u, 15f * u, 4f * u, 17.5f * u, 5.5f * u)
                cubicTo(21.5f * u, 8f * u, 21f * u, 14f * u, 12f * u, 20.5f * u)
                close()
            }
            if (icon == PhotoIcon.HeartFilled) drawPath(heart, color, style = Fill) else drawPath(heart, color, style = line)
        }
        PhotoIcon.Check -> drawPath(path { moveTo(4.5f * u, 12.5f * u); lineTo(9.5f * u, 17.5f * u); lineTo(19.5f * u, 6.5f * u) }, color, style = Stroke(2.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        PhotoIcon.Select -> {
            drawRect(color, p(3.5f, 3.5f), Size(17f * u, 17f * u), style = line)
            drawPath(path { moveTo(8f * u, 12.5f * u); lineTo(11f * u, 15.5f * u); lineTo(16.5f * u, 9f * u) }, color, style = line)
        }
        PhotoIcon.Play -> drawPath(path { moveTo(8f * u, 5f * u); lineTo(19f * u, 12f * u); lineTo(8f * u, 19f * u); close() }, color, style = Fill)
        PhotoIcon.Wallpaper -> {
            drawRect(color, p(6f, 2.5f), Size(12f * u, 19f * u), style = line)
            drawPath(path { moveTo(6f * u, 16f * u); lineTo(11f * u, 11f * u); lineTo(14f * u, 14f * u); lineTo(15.5f * u, 12.5f * u); lineTo(18f * u, 15f * u) }, color, style = line)
        }
        PhotoIcon.Compare -> {
            drawRect(color, p(2.5f, 5f), Size(8f * u, 14f * u), style = line); drawRect(color, p(13.5f, 5f), Size(8f * u, 14f * u), style = line)
        }
        PhotoIcon.Zoom -> {
            drawCircle(color, 6.5f * u, p(10f, 10f), style = line); drawLine(color, p(15f, 15f), p(21f, 21f), 2f * u, StrokeCap.Round)
            drawLine(color, p(7.3f, 10f), p(12.7f, 10f), 2f * u, StrokeCap.Round); drawLine(color, p(10f, 7.3f), p(10f, 12.7f), 2f * u, StrokeCap.Round)
        }
        PhotoIcon.Search -> {
            drawCircle(color, 6.5f * u, p(10f, 10f), style = line); drawLine(color, p(15f, 15f), p(21f, 21f), 2f * u, StrokeCap.Round)
        }
        PhotoIcon.Options -> listOf(5f, 12f, 19f).forEach { drawCircle(color, 1.8f * u, p(it, 12f)) }
    }
}

/** The picture of an empty hub: a frame with a hill and a sun, in the colour [color]. */
@Composable
fun PhotoIllustration(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier) {
        val u = size.minDimension / 100f
        val line = Stroke(width = 4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRect(color, Offset(10f * u, 18f * u), Size(80f * u, 64f * u), style = line)
        drawCircle(color, 7f * u, Offset(32f * u, 38f * u), style = line)
        drawPath(Path().apply { moveTo(10f * u, 74f * u); lineTo(38f * u, 52f * u); lineTo(55f * u, 66f * u); lineTo(68f * u, 56f * u); lineTo(90f * u, 74f * u) }, color, style = line)
    }
}
