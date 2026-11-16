package com.metrowin.launcher.recorder

import com.metrowin.launcher.ui.theme.Danger
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.data.TimerFormat
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.LocalStyle
import com.metrowin.launcher.data.ThemeStyle
import kotlin.math.abs
import kotlin.math.sqrt

/** The red of a recording: a dot, a button, the live wave. The one place it is named. */
val RecordRed = Danger

const val WAVE_BARS = 48
const val WAVE_STEP_MS = 80L

/**
 * The time of a recording with every digit in a cell of the same width, so the numbers do not shake as they change (the font of the
 * launcher has digits of different widths). TalkBack reads it as one time.
 */
@Composable
fun RecTimer(ms: Long, size: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    val text = TimerFormat.stopwatch(ms, tenths = false)
    val cell = with(LocalDensity.current) { (size.toPx() * 0.56f).toDp() }
    Row(modifier.clearAndSetSemantics { contentDescription = text }, verticalAlignment = Alignment.CenterVertically) {
        text.forEach { ch ->
            Box(Modifier.width(if (ch == ':') cell * 0.55f else cell), contentAlignment = Alignment.Center) { MetroText(ch.toString(), size = size, maxLines = 1) }
        }
    }
}

/** The red dot of a recording, on and off about once a second; steady with reduced motion. */
@Composable
fun RecordingDot(modifier: Modifier = Modifier, steady: Boolean = false) {
    val reduced = LocalReduceMotion.current || steady
    val alpha = if (reduced) 1f else rememberInfiniteTransition(label = "recDot").animateFloat(
        1f, 0.15f, infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse), label = "alpha",
    ).value
    Box(modifier.size(18.dp).graphicsLayer { this.alpha = alpha }.background(RecordRed, CircleShape).testTag("rec:dot"))
}

/**
 * The big round button of the recorder: a red disc in a ring that becomes a rounded square while it records (it eases there, or jumps with
 * reduced motion). [description] is what TalkBack says.
 */
@Composable
fun RecordButton(recording: Boolean, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalMetroColors.current
    val reduced = LocalReduceMotion.current
    val shape by animateFloatAsState(if (recording) 1f else 0f, if (reduced) tween(0) else tween(220), label = "recShape")
    Canvas(
        modifier.size(104.dp).metroPress(onClick = onClick).semantics { role = Role.Button; contentDescription = description },
    ) {
        drawCircle(colors.foreground, radius = size.minDimension / 2f - 2.dp.toPx(), style = Stroke(4.dp.toPx()))
        val side = size.minDimension * (0.62f - 0.22f * shape)
        val corner = side / 2f * (1f - 0.7f * shape)
        drawRoundRect(RecordRed, Offset((size.width - side) / 2f, (size.height - side) / 2f), Size(side, side), CornerRadius(corner))
    }
}

/** The small round buttons around it: a flag for a marker, two bars for a pause, a triangle for resuming. */
enum class Glyph { Pause, Play, Flag, Next, Previous }

@Composable
fun GlyphButton(glyph: Glyph, description: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 60.dp, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = LocalMetroColors.current
    val ink = if (enabled) colors.foreground else colors.subtle
    Canvas(
        modifier.size(size).border(2.dp, ink, CircleShape).metroPress(onClick = onClick, enabled = enabled).semantics { role = Role.Button; contentDescription = description },
    ) {
        val w = this.size.width
        val h = this.size.height
        when (glyph) {
            Glyph.Pause -> {
                drawRect(ink, Offset(w * 0.34f, h * 0.30f), Size(w * 0.11f, h * 0.40f))
                drawRect(ink, Offset(w * 0.55f, h * 0.30f), Size(w * 0.11f, h * 0.40f))
            }
            Glyph.Play -> drawPath(Path().apply { moveTo(w * 0.38f, h * 0.28f); lineTo(w * 0.72f, h * 0.5f); lineTo(w * 0.38f, h * 0.72f); close() }, ink)
            Glyph.Flag -> {
                drawRect(ink, Offset(w * 0.36f, h * 0.26f), Size(w * 0.05f, h * 0.48f))
                drawPath(Path().apply { moveTo(w * 0.41f, h * 0.28f); lineTo(w * 0.70f, h * 0.38f); lineTo(w * 0.41f, h * 0.50f); close() }, ink)
            }
            Glyph.Next -> {
                drawPath(Path().apply { moveTo(w * 0.32f, h * 0.30f); lineTo(w * 0.62f, h * 0.5f); lineTo(w * 0.32f, h * 0.70f); close() }, ink)
                drawRect(ink, Offset(w * 0.64f, h * 0.30f), Size(w * 0.06f, h * 0.40f))
            }
            Glyph.Previous -> {
                drawPath(Path().apply { moveTo(w * 0.68f, h * 0.30f); lineTo(w * 0.38f, h * 0.5f); lineTo(w * 0.68f, h * 0.70f); close() }, ink)
                drawRect(ink, Offset(w * 0.30f, h * 0.30f), Size(w * 0.06f, h * 0.40f))
            }
        }
    }
}

/** A small label in a frame: a format, "text", "cut". */
@Composable
fun Chip(text: String, modifier: Modifier = Modifier, filled: Boolean = false) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val shape = LocalStyle.current.controlShape
    Box(
        modifier.then(if (filled) Modifier.background(accent, shape) else Modifier.border(1.dp, colors.subtle, shape)).padding(horizontal = 7.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(text, size = 12.sp, weight = FontWeight.Normal, color = if (filled) Color.White else colors.subtle, maxLines = 1)
    }
}

/** Words side by side, the chosen one in full and the others faint: the sections of a recording. */
@Composable
fun Segments(titles: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalMetroColors.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        titles.forEachIndexed { i, title ->
            MetroText(
                title, size = 24.sp, color = if (i == selected) colors.foreground else colors.subtle,
                modifier = Modifier.metroPress(onClick = { onSelect(i) }).padding(vertical = 6.dp).semantics { role = Role.Tab },
            )
        }
    }
}

/** A microphone drawn in lines, for the places with nothing to show (an empty list, a microphone that is not allowed); [crossed] strikes it out. */
@Composable
fun MicIllustration(modifier: Modifier = Modifier, crossed: Boolean = false) {
    val ink = LocalMetroColors.current.subtle
    Canvas(modifier.size(96.dp).clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        val line = Stroke(width = 4.dp.toPx())
        drawRoundRect(ink, Offset(w * 0.36f, h * 0.08f), Size(w * 0.28f, h * 0.48f), CornerRadius(w * 0.14f), style = line)
        drawArc(ink, 0f, 180f, false, Offset(w * 0.24f, h * 0.30f), Size(w * 0.52f, h * 0.42f), style = line)
        drawLine(ink, Offset(w * 0.5f, h * 0.72f), Offset(w * 0.5f, h * 0.90f), strokeWidth = 4.dp.toPx())
        drawLine(ink, Offset(w * 0.36f, h * 0.90f), Offset(w * 0.64f, h * 0.90f), strokeWidth = 4.dp.toPx())
        if (crossed) drawLine(RecordRed, Offset(w * 0.15f, h * 0.10f), Offset(w * 0.85f, h * 0.92f), strokeWidth = 5.dp.toPx())
    }
}

/** The sound of the last moments as bars, the newest at the right, each at least a thin line so a silence still reads as a baseline. */
@Composable
fun LiveWave(levels: List<Float>, modifier: Modifier = Modifier, paused: Boolean = false) {
    val colors = LocalMetroColors.current
    val kind = LocalStyle.current.kind
    val ink = if (paused) colors.subtle else RecordRed
    Canvas(modifier) {
        val slot = size.width / WAVE_BARS
        val offset = WAVE_BARS - levels.size
        val round = if (kind == ThemeStyle.Classic) 0f else slot / 2f
        levels.forEachIndexed { i, level ->
            // The microphone's swing is small most of the time: a square root makes speech fill the picture.
            val h = (sqrt(level) * size.height).coerceAtLeast(3f)
            drawRoundRect(ink, Offset((offset + i) * slot, (size.height - h) / 2f), Size(maxOf(slot - 2f, 1f), h), CornerRadius(round))
        }
    }
}

/** How loud it is now as a bar, the peak of the last seconds as a line, and a red zone where the microphone starts to clip. */
@Composable
fun LevelMeter(level: Float, peak: Float, modifier: Modifier = Modifier) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    Canvas(modifier.clearAndSetSemantics { }) {
        val w = size.width
        drawRect(colors.surface, Offset.Zero, size)
        val now = sqrt(level).coerceIn(0f, 1f)
        drawRect(if (now > CLIP) RecordRed else accent, Offset.Zero, Size(w * now, size.height))
        drawRect(RecordRed.copy(alpha = 0.35f), Offset(w * CLIP, 0f), Size(w * (1f - CLIP), size.height))
        val at = sqrt(peak).coerceIn(0f, 1f) * w
        drawRect(colors.foreground, Offset((at - 1.5f).coerceAtLeast(0f), 0f), Size(3f, size.height))
    }
}

private const val CLIP = 0.92f

/**
 * The picture of one recording: its loudness along it (or, with [spectrum], its frequencies), the two handles of the piece to keep, the
 * markers and the place being played. A touch moves the playing there; a drag that starts at a handle moves it, a drag elsewhere moves the
 * playing too (or, zoomed in, scrolls the picture). TalkBack gets the handles as actions. [from] and [to] are fractions of the length.
 */
@Composable
fun EditWave(
    peaks: FloatArray,
    spectrum: ImageBitmap?,
    from: Float,
    to: Float,
    position: Long,
    duration: Long,
    marks: List<Long>,
    zoomed: Boolean,
    contrast: Boolean,
    themed: Boolean,
    description: String,
    actions: List<Pair<String, () -> Unit>>,
    onFrom: (Float) -> Unit,
    onTo: (Float) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val kind = LocalStyle.current.kind
    val curFrom by rememberUpdatedState(from)
    val curTo by rememberUpdatedState(to)
    val gap = if (duration > 0) (300f / duration).coerceAtMost(0.5f) else 0.02f
    Canvas(
        modifier
            .semantics { contentDescription = description; customActions = actions.map { (label, run) -> CustomAccessibilityAction(label) { run(); true } } }
            .pointerInput(duration) {
                detectTapGestures { tap -> onSeek((tap.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(duration, zoomed) {
                val reach = 28.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val dx = down.position.x
                    val fx = curFrom * size.width
                    val tx = curTo * size.width
                    val handle = when {
                        abs(dx - fx) <= reach && abs(dx - fx) <= abs(dx - tx) -> 1
                        abs(dx - tx) <= reach -> 2
                        else -> 0
                    }
                    // Zoomed in, a drag away from the handles scrolls the picture (the parent does it): it is not taken here.
                    if (handle == 0 && zoomed) return@awaitEachGesture
                    drag(down.id) { change ->
                        change.consume()
                        val f = (change.position.x / size.width).coerceIn(0f, 1f)
                        when (handle) {
                            1 -> onFrom(f.coerceAtMost(curTo - gap).coerceAtLeast(0f))
                            2 -> onTo(f.coerceAtLeast(curFrom + gap).coerceAtMost(1f))
                            else -> onSeek(f)
                        }
                    }
                }
            },
    ) {
        drawRect(colors.surface, Offset.Zero, size)
        if (spectrum != null) {
            drawImage(spectrum, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
            drawRect(colors.background.copy(alpha = 0.55f), Offset.Zero, Size(from * size.width, size.height))
            drawRect(colors.background.copy(alpha = 0.55f), Offset(to * size.width, 0f), Size((1f - to) * size.width, size.height))
        } else {
            val bar = size.width / maxOf(peaks.size, 1)
            // The style of the launcher gives the bars: square in Classic, round in the others.
            val round = if (!themed || kind == ThemeStyle.Classic) 0f else (bar * 0.45f).coerceAtMost(4.dp.toPx())
            val outside = if (contrast) colors.foreground.copy(alpha = 0.55f) else colors.subtle
            peaks.forEachIndexed { i, p ->
                val x = i * bar
                val inside = x / size.width in from..to
                val h = (p * size.height).coerceAtLeast(2f)
                drawRoundRect(if (inside) accent else outside, Offset(x, (size.height - h) / 2f), Size(maxOf(bar - 1f, 1f), h), CornerRadius(round))
            }
        }
        if (duration > 0) marks.forEach { m -> drawRect(RecordRed, Offset(m.toFloat() / duration * size.width - 1f, 0f), Size(2f, size.height * 0.3f)) }
        // The handles: a line and a knob each, with room to catch them with a thumb.
        val line = if (contrast) 6.dp.toPx() else 4.dp.toPx()
        listOf(from, to).forEach { f ->
            drawRect(colors.foreground, Offset(f * size.width - line / 2f, 0f), Size(line, size.height))
            if (kind == ThemeStyle.Classic) {
                // The block of the slider of Windows Phone, 9 x 18 dp.
                drawRect(colors.foreground, Offset(f * size.width - 4.5.dp.toPx(), size.height / 2f - 9.dp.toPx()), Size(9.dp.toPx(), 18.dp.toPx()))
            } else {
                drawCircle(colors.foreground, 9.dp.toPx(), Offset(f * size.width, size.height / 2f))
                drawCircle(accent, 5.dp.toPx(), Offset(f * size.width, size.height / 2f))
            }
        }
        if (duration > 0) drawRect(colors.foreground.copy(alpha = 0.7f), Offset(position.toFloat() / duration * size.width, 0f), Size(2.dp.toPx(), size.height))
    }
}

/** Turns the numbers of [AudioEdit.spectrogram] into a picture: dark to the accent to white, low sound at the bottom. */
fun spectrumImage(data: FloatArray, columns: Int, bins: Int, accent: Color): ImageBitmap {
    val bitmap = android.graphics.Bitmap.createBitmap(columns, bins, android.graphics.Bitmap.Config.ARGB_8888)
    val pixels = IntArray(columns * bins)
    val argb = accent.toArgb()
    val ar = (argb shr 16) and 0xFF
    val ag = (argb shr 8) and 0xFF
    val ab = argb and 0xFF
    for (x in 0 until columns) for (y in 0 until bins) {
        val v = data[x * bins + (bins - 1 - y)]
        val r: Int; val g: Int; val b: Int
        if (v < 0.6f) { val t = v / 0.6f; r = (ar * t).toInt(); g = (ag * t).toInt(); b = (ab * t).toInt() }
        else { val t = (v - 0.6f) / 0.4f; r = (ar + (255 - ar) * t).toInt(); g = (ag + (255 - ag) * t).toInt(); b = (ab + (255 - ab) * t).toInt() }
        pixels[y * columns + x] = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    }
    bitmap.setPixels(pixels, 0, columns, 0, 0, columns, bins)
    return bitmap.asImageBitmap()
}

/** The thin scroll bar of Windows Phone for a column that scrolls (see the one of the lists): Classic only, there while it scrolls. */
@Composable
fun Modifier.scrollBar(state: androidx.compose.foundation.ScrollState): Modifier {
    if (!LocalStyle.current.isClassic) return this
    val color = LocalMetroColors.current.foreground
    val reduced = LocalReduceMotion.current
    val scrolling = state.isScrollInProgress
    val alpha by animateFloatAsState(if (scrolling) 0.6f else 0f, if (reduced) tween(0) else if (scrolling) tween(80) else tween(450, delayMillis = 700), label = "scrollBar")
    return drawWithContent {
        drawContent()
        if (alpha <= 0f || state.maxValue <= 0) return@drawWithContent
        val view = size.height
        val length = (view * view / (view + state.maxValue)).coerceAtLeast(24.dp.toPx())
        val top = state.value.toFloat() / state.maxValue * (view - length)
        drawRect(color.copy(alpha = alpha), Offset(size.width - 2.dp.toPx(), top), Size(2.dp.toPx(), length))
    }
}
