package com.metrowin.launcher.datasense

import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.metrowin.launcher.R
import com.metrowin.launcher.data.StorageInfo
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MarkIcon
import com.metrowin.launcher.ui.components.Mark
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.ColorMath
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalHighContrast
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal fun bytes(value: Long) = StorageInfo.format(value, Locale.getDefault())

/** The colours of the charts: Wi-Fi, mobile (always hatched, so it reads without colour), the warning and one for each kind of storage. */
internal class DsPalette(val wifi: Color, val mobile: Color, val warn: Color, val parts: List<Color>)

private val FixedPalette = DsPalette(
    Color(0xFF1BA1E2), Color(0xFFF09609), Color(0xFFE51400),
    listOf(Color(0xFF1BA1E2), Color(0xFFA4C400), Color(0xFFF472D0), Color(0xFFF09609), Color(0xFF8A8A8A)),
)

@Composable
internal fun rememberPalette(): DsPalette {
    val accent = LocalAccent.current
    val colors = LocalMetroColors.current
    val on = LocalDsOptions.current[Opt.Accent]
    return remember(accent, colors, on) {
        if (!on) return@remember FixedPalette
        val hsv = ColorMath.argbToHsv(accent.toArgb())
        fun turn(degrees: Float) = Color(ColorMath.hsvToArgb(hsv[0] + degrees, hsv[1].coerceAtLeast(0.55f), hsv[2].coerceIn(0.7f, 1f)))
        val nearRed = min(abs(hsv[0] - 8f), 360f - abs(hsv[0] - 8f)) < 25f
        DsPalette(accent, colors.foreground, if (nearRed) FixedPalette.mobile else FixedPalette.warn, listOf(accent, turn(72f), turn(144f), turn(216f), colors.subtle))
    }
}

/** Diagonal lines over a pale ground: the mobile data in every chart, so the two networks differ by more than the colour. */
private fun DrawScope.hatchRect(color: Color, topLeft: Offset, size: Size) {
    if (size.width <= 0f || size.height <= 0f) return
    drawRect(color.copy(alpha = color.alpha * 0.3f), topLeft, size)
    clipRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height) {
        var x = topLeft.x - size.height
        val step = 4.dp.toPx()
        while (x < topLeft.x + size.width) {
            drawLine(color, Offset(x, topLeft.y + size.height), Offset(x + size.height, topLeft.y), 1.5.dp.toPx())
            x += step
        }
    }
}

@Composable
internal fun Legend(p: DsPalette, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(14.dp)) { drawRect(p.wifi) }
        MetroText(stringResource(R.string.dsx_wifi), size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, modifier = Modifier.offset(x = (-10).dp))
        Canvas(Modifier.size(14.dp)) { hatchRect(p.mobile, Offset.Zero, size) }
        MetroText(stringResource(R.string.ds_mobile), size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, modifier = Modifier.offset(x = (-10).dp))
    }
}

/** A bar of two parts: [first] of its width in [firstColor], the rest in [restColor] (hatched when [hatchRest]). */
@Composable
internal fun Bar(
    first: Float, firstColor: Color, restColor: Color, modifier: Modifier = Modifier, height: Dp = 12.dp, hatchRest: Boolean = false, description: String? = null,
) {
    val reduce = LocalReduceMotion.current
    val animated = LocalDsOptions.current[Opt.Chart] && !reduce
    val shown by animateFloatAsState(first.coerceIn(0f, 1f), if (animated) tween(450) else snap(), label = "bar")
    val outline = if (LocalHighContrast.current) LocalMetroColors.current.foreground else Color.Transparent
    Canvas(modifier.fillMaxWidth().height(height).border(1.dp, outline).then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)) {
        if (hatchRest) hatchRect(restColor, Offset.Zero, size) else drawRect(restColor, Offset.Zero, size)
        drawRect(firstColor, Offset.Zero, Size(size.width * shown, size.height))
    }
}

/** One bar of the chart: Wi-Fi below, mobile above. */
data class ChartBar(val label: String, val wifi: Long, val mobile: Long)

/**
 * Bars side by side, each stacked. With [rich] it has a grid and its top value, a touch on a bar tells its values, a long touch and a drag
 * tells the sum of a few, and the bars rise when it is new.
 */
@Composable
internal fun Chart(bars: List<ChartBar>, height: Dp, p: DsPalette, rich: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalMetroColors.current
    val reduce = LocalReduceMotion.current
    val top = (bars.maxOfOrNull { it.wifi + it.mobile } ?: 0L).coerceAtLeast(1L)
    val grow = remember(bars) { Animatable(if (rich && !reduce) 0f else 1f) }
    LaunchedEffect(bars) { if (rich && !reduce) grow.animateTo(1f, tween(450)) }
    var selection by remember(bars) { mutableStateOf<IntRange?>(null) }
    var width by remember { mutableFloatStateOf(1f) }
    val peak = bars.maxByOrNull { it.wifi + it.mobile }
    val described = if (peak == null) "" else stringResource(R.string.dsx_chart_desc, bars.size, peak.label, bytes(peak.wifi), bytes(peak.mobile))
    fun indexAt(x: Float) = (x / (width / bars.size.coerceAtLeast(1))).toInt().coerceIn(0, (bars.size - 1).coerceAtLeast(0))
    BoxWithConstraints(modifier) {
        val labelsMax = (maxWidth / 34.dp).toInt().coerceAtLeast(2)
        Column {
            Box(Modifier.fillMaxWidth().height(height)) {
                var anchor by remember { mutableFloatStateOf(0f) }
                Canvas(
                    Modifier.fillMaxSize().background(colors.surface).semantics { contentDescription = described }
                        .pointerInput(bars, rich) { if (rich && bars.isNotEmpty()) detectTapGestures { o -> selection = indexAt(o.x).let { i -> if (selection == i..i) null else i..i } } }
                        .pointerInput(bars, rich) {
                            if (rich && bars.isNotEmpty()) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { o -> anchor = indexAt(o.x).toFloat(); selection = anchor.toInt()..anchor.toInt() },
                                    onDrag = { change, _ -> val i = indexAt(change.position.x); selection = min(anchor.toInt(), i)..max(anchor.toInt(), i) },
                                )
                            }
                        },
                ) {
                    width = size.width
                    if (rich) listOf(0.5f, 1f).forEach { y -> drawLine(colors.subtle.copy(alpha = 0.4f), Offset(0f, size.height * (1 - y)), Offset(size.width, size.height * (1 - y)), 1.dp.toPx()) }
                    val slot = size.width / bars.size.coerceAtLeast(1)
                    val chosen = selection
                    bars.forEachIndexed { i, b ->
                        val fade = if (chosen != null && i !in chosen) 0.35f else 1f
                        val wifiH = size.height * b.wifi / top * grow.value
                        val mobileH = size.height * b.mobile / top * grow.value
                        drawRect(p.wifi.copy(alpha = fade), Offset(i * slot + slot * 0.15f, size.height - wifiH), Size(slot * 0.7f, wifiH))
                        hatchRect(p.mobile.copy(alpha = fade), Offset(i * slot + slot * 0.15f, size.height - wifiH - mobileH), Size(slot * 0.7f, mobileH))
                    }
                }
                if (rich) {
                    MetroText(bytes(top), size = 11.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.align(Alignment.TopStart).padding(start = 3.dp))
                    MetroText(bytes(top / 2), size = 11.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.align(Alignment.CenterStart).padding(start = 3.dp))
                }
            }
            val step = (bars.size + labelsMax - 1) / labelsMax
            Row(Modifier.fillMaxWidth()) {
                bars.forEachIndexed { i, b ->
                    Box(Modifier.weight(1f)) { if (step <= 1 || i % step == 0) MetroText(b.label, size = 12.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1, minScale = 1f) }
                }
            }
            selection?.let { r ->
                val picked = bars.slice(r.first.coerceAtLeast(0)..r.last.coerceAtMost(bars.lastIndex))
                val wifi = picked.sumOf { it.wifi }
                val mobile = picked.sumOf { it.mobile }
                MetroText(
                    if (picked.size == 1) stringResource(R.string.dsx_selected, picked.first().label, bytes(wifi), bytes(mobile))
                    else stringResource(R.string.dsx_range, picked.first().label, picked.last().label, bytes(wifi + mobile)),
                    size = 14.sp, weight = FontWeight.Normal, color = colors.foreground, maxLines = 2, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** A ring: [fraction] of it in [color] over a [track], with [text] in the middle. It moves to a new value instead of jumping. */
@Composable
internal fun Gauge(fraction: Float, color: Color, text: String, description: String, modifier: Modifier = Modifier, size: Dp = 140.dp, arc: Boolean = true) {
    val colors = LocalMetroColors.current
    val reduce = LocalReduceMotion.current
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), if (reduce) snap() else tween(600), label = "gauge")
    Box(modifier.size(size).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val stroke = Stroke(10.dp.toPx())
            val start = if (arc) 135f else -90f
            val sweep = if (arc) 270f else 360f
            drawArc(colors.surface, start, sweep, false, style = stroke)
            drawArc(color, start, sweep * shown, false, style = stroke)
        }
        MetroText(text, size = 26.sp, maxLines = 1, minScale = 0.5f, modifier = Modifier.padding(horizontal = 22.dp))
    }
}

/** A ring cut in [parts] (share, colour). */
@Composable
internal fun Donut(parts: List<Pair<Float, Color>>, text: String, description: String, modifier: Modifier = Modifier, size: Dp = 150.dp) {
    val colors = LocalMetroColors.current
    Box(modifier.size(size).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val stroke = Stroke(18.dp.toPx())
            drawArc(colors.surface, 0f, 360f, false, style = stroke)
            var angle = -90f
            parts.forEach { (share, color) ->
                val sweep = 360f * share.coerceIn(0f, 1f)
                if (sweep > 0.5f) drawArc(color, angle, sweep - 1f, false, style = stroke)
                angle += sweep
            }
        }
        MetroText(text, size = 26.sp, maxLines = 1, minScale = 0.5f, modifier = Modifier.padding(horizontal = 30.dp))
    }
}

/** The biggest things as rectangles, as large as their size; a touch on one runs [onClick] with its index. */
@Composable
internal fun Treemap(items: List<Triple<String, Long, Color>>, modifier: Modifier = Modifier, onClick: (Int) -> Unit = {}) {
    val cells = remember(items) { DataMath.treemap(items.map { it.second }) }
    val bg = LocalMetroColors.current.background
    BoxWithConstraints(modifier.fillMaxWidth().height(190.dp)) {
        cells.forEachIndexed { i, c ->
            val (label, size, color) = items[i]
            val w = maxWidth * c.w
            val h = maxHeight * c.h
            Box(Modifier.offset(maxWidth * c.x, maxHeight * c.y).size(w, h).border(1.dp, bg).background(color).metroPress(onClick = { onClick(i) })
                .semantics { contentDescription = "$label, ${bytes(size)}" }) {
                if (w > 64.dp && h > 34.dp) {
                    Column(Modifier.padding(4.dp)) {
                        MetroText(label, size = 13.sp, weight = FontWeight.SemiBold, color = Color.White, maxLines = 2, minScale = 1f)
                        MetroText(bytes(size), size = 12.sp, weight = FontWeight.Normal, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** The days of a stretch as a calendar: the more bytes a day has, the stronger its square. [firstWeekday] is 0 for Monday. */
@Composable
internal fun CalendarGrid(days: List<Pair<Int, Long>>, firstWeekday: Int, p: DsPalette, modifier: Modifier = Modifier) {
    val top = (days.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    val cells: List<Pair<Int, Long>?> = List(firstWeekday) { null } + days
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (k in 0 until 7) {
                    val cell = week.getOrNull(k)
                    Box(
                        Modifier.weight(1f).aspectRatio(1.3f).then(if (cell == null) Modifier else Modifier.background(p.wifi.copy(alpha = 0.12f + 0.88f * cell.second / top)).semantics { contentDescription = "${cell.first}: ${bytes(cell.second)}" }),
                        contentAlignment = Alignment.Center,
                    ) { if (cell != null) MetroText(cell.first.toString(), size = 12.sp, weight = FontWeight.Normal, color = Color.White, maxLines = 1, minScale = 1f) }
                }
            }
        }
    }
}

/** Seven days by 24 hours: the hours that use most are the strongest squares. [values] is 168 numbers, the oldest day first. */
@Composable
internal fun HourHeat(values: LongArray, p: DsPalette, description: String, modifier: Modifier = Modifier) {
    val top = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Canvas(modifier.fillMaxWidth().height(110.dp).semantics { contentDescription = description }) {
        val cw = size.width / 24f
        val ch = size.height / 7f
        for (d in 0 until 7) for (h in 0 until 24) {
            val v = values.getOrElse(d * 24 + h) { 0L }
            drawRect(p.wifi.copy(alpha = 0.1f + 0.9f * v / top), Offset(h * cw + 0.5f, d * ch + 0.5f), Size(cw - 1f, ch - 1f))
        }
    }
}

/** Where the data of the top apps goes: a band from each app to Wi-Fi or mobile, as thick as its bytes. */
@Composable
internal fun FlowChart(apps: List<AppData>, p: DsPalette, description: String, modifier: Modifier = Modifier) {
    val colors = LocalMetroColors.current
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = 12.sp, color = colors.foreground)
    val top = apps.take(5)
    Canvas(modifier.fillMaxWidth().height(170.dp).semantics { contentDescription = description }) {
        val total = top.sumOf { it.total }.toFloat()
        if (total <= 0f) return@Canvas
        val gap = 4.dp.toPx()
        val usable = size.height - gap * (top.size - 1)
        val leftW = size.width * 0.34f
        val nodeW = 10.dp.toPx()
        val rightX = size.width - nodeW
        val wifiTotal = top.sumOf { it.wifi }.toFloat()
        val mobileTotal = top.sumOf { it.mobile }.toFloat()
        val rightGap = if (wifiTotal > 0 && mobileTotal > 0) gap * 3 else 0f
        val rightUsable = size.height - rightGap
        var y = 0f
        var wifiY = 0f
        var mobileY = if (wifiTotal > 0) rightUsable * wifiTotal / total + rightGap else 0f
        top.forEach { app ->
            val h = usable * app.total / total
            drawRect(colors.foreground.copy(alpha = 0.8f), Offset(leftW - nodeW, y), Size(nodeW, h))
            if (h >= 15.dp.toPx()) {
                val text = measurer.measure(app.label, style, maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = androidx.compose.ui.unit.Constraints(maxWidth = (leftW - nodeW - 6.dp.toPx()).toInt().coerceAtLeast(1)))
                drawText(text, topLeft = Offset(0f, y + (h - text.size.height) / 2f))
            }
            fun band(bytes: Long, color: Color, toY: Float): Float {
                if (bytes <= 0) return 0f
                val bh = usable * bytes / total
                val rh = rightUsable * bytes / total
                val upper = if (color == p.wifi) y else y + usable * app.wifi / total
                val path = Path().apply {
                    moveTo(leftW, upper); cubicTo((leftW + rightX) / 2, upper, (leftW + rightX) / 2, toY, rightX, toY)
                    lineTo(rightX, toY + rh); cubicTo((leftW + rightX) / 2, toY + rh, (leftW + rightX) / 2, upper + bh, leftW, upper + bh); close()
                }
                drawPath(path, color.copy(alpha = 0.45f))
                return rh
            }
            wifiY += band(app.wifi, p.wifi, wifiY)
            mobileY += band(app.mobile, p.mobile, mobileY)
            y += h + gap
        }
        if (wifiTotal > 0) drawRect(p.wifi, Offset(rightX, 0f), Size(nodeW, rightUsable * wifiTotal / total))
        if (mobileTotal > 0) drawRect(p.mobile, Offset(rightX, rightUsable * wifiTotal / total + rightGap), Size(nodeW, rightUsable * mobileTotal / total))
    }
}

/** Pale boxes that pulse while a page reads: the shape of what comes, instead of a word. */
@Composable
internal fun Skeleton(rows: Int, modifier: Modifier = Modifier, chart: Boolean = false) {
    val colors = LocalMetroColors.current
    val reduce = LocalReduceMotion.current
    val pulse = rememberInfiniteTransition(label = "skeleton").animateFloat(0.5f, 1f, infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse), label = "pulse")
    val alpha = if (reduce) 0.8f else pulse.value
    val described = stringResource(R.string.dsx_working)
    Column(modifier.fillMaxWidth().semantics { contentDescription = described }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (chart) Box(Modifier.fillMaxWidth().height(110.dp).background(colors.surface.copy(alpha = alpha)))
        repeat(rows) { Box(Modifier.fillMaxWidth(if (it % 2 == 0) 1f else 0.7f).height(26.dp).background(colors.surface.copy(alpha = alpha))) }
    }
}

/** The picture of an empty page: two squares of the Metro grid with a sign on them, and the [text] under it. */
@Composable
internal fun EmptyArt(sign: String, text: String, modifier: Modifier = Modifier) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    Column(modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(104.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(colors.surface, Offset.Zero, Size(70.dp.toPx(), 70.dp.toPx()))
                drawRect(accent, Offset(44.dp.toPx(), 44.dp.toPx()), Size(56.dp.toPx(), 56.dp.toPx()))
            }
            MetroText(sign, size = 34.sp, color = colors.foreground, modifier = Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 14.dp))
        }
        MetroText(text, size = 14.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = Int.MAX_VALUE, modifier = Modifier.padding(top = 8.dp))
    }
}

/** An up or down mark and the percent of a change; nothing for no change. */
@Composable
internal fun Trend(percent: Int?, modifier: Modifier = Modifier) {
    if (percent == null) return
    val colors = LocalMetroColors.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (percent != 0) MarkIcon(if (percent > 0) Mark.Up else Mark.Down, 14.dp, color = colors.subtle)
        MetroText(
            stringResource(if (percent > 0) R.string.dsx_trend_up else if (percent < 0) R.string.dsx_trend_down else R.string.dsx_trend_same, abs(percent)),
            size = 14.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 2, modifier = Modifier.padding(start = 4.dp),
        )
    }
}

private val iconCache = LruCache<String, ImageBitmap>(80)

/** The icon of an app, small and square, read off the main thread and kept; blank until it is there or for what has no package. */
@Composable
internal fun AppIcon(packageName: String?, size: Dp = 28.dp) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    if (packageName == null) { Spacer(Modifier.size(size)); return }
    val icon by produceState(iconCache.get(packageName), packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(px, px).asImageBitmap() }.getOrNull()?.also { iconCache.put(packageName, it) }
            }
        }
    }
    icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(size)) } ?: Spacer(Modifier.size(size))
}
