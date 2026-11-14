package com.metrowin.launcher.todo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.ClockFormat
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.kids.CheckSquare
import com.metrowin.launcher.ui.start.whenText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.LocalStyle
import com.metrowin.launcher.ui.theme.litFill
import com.metrowin.launcher.ui.theme.onAccent
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The colours a list can be given (the tile colours of Windows Phone); a list with none takes one from its name. */
private val ListPalette = listOf(
    Color(0xFF1BA1E2), Color(0xFF60A917), Color(0xFFF0A30A), Color(0xFFE51400),
    Color(0xFFAA00FF), Color(0xFFFA6800), Color(0xFF00ABA9), Color(0xFFD80073),
)

/** The colour of a list: [stored] (1 to [TodoOptionStore.COLORS]) or, while it has none, the one its name stands for; the main list has none. */
fun listTint(name: String, stored: Int): Color? {
    if (name.isEmpty()) return null
    val index = if (stored in 1..ListPalette.size) stored - 1 else (name.hashCode() and Int.MAX_VALUE) % ListPalette.size
    return ListPalette[index]
}

fun paletteColor(index: Int): Color = ListPalette[index - 1]

/** What a row needs from the screen. */
class RowEnv(
    val now: Long,
    val options: TodoOptions,
    val colorOf: (String) -> Color?,
    /** The ids of the rows that were just ticked and are about to leave: they are drawn done for a moment. */
    val completing: Set<Long>,
    val onToggle: (TodoItem) -> Unit,
    val onEdit: (TodoItem) -> Unit,
)

/** One thing to do: the box, the text, and under it when it is for, which reminder and list it has, its steps and the first line of its note. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodoRow(item: TodoItem, env: RowEnv, modifier: Modifier = Modifier) {
    val colors = LocalMetroColors.current
    val style = LocalStyle.current
    val accent = LocalAccent.current
    val o = env.options
    val k = if (o.large) 1.25f else 1f
    val dense = o.view == TodoOptions.View.Compact
    val completing = item.id in env.completing
    val shownDone = item.done || completing
    val late = !item.done && item.due != 0L && item.due < env.now
    val subtle = if (o.contrast) colors.foreground else colors.subtle
    val dim by animateFloatAsState(if (completing) 0.55f else 1f, label = "completing")
    val stateText = stringResource(if (item.done) R.string.todo_state_done else R.string.todo_state_open)
    Row(
        modifier.fillMaxWidth()
            // A side bar for what is late, in the room kept at the left of every row.
            .drawBehind { if (late) drawRect(accent, Offset.Zero, Size(3.dp.toPx(), size.height)) }
            .padding(start = 6.dp, top = if (dense) 0.dp else 3.dp, bottom = if (dense) 0.dp else 3.dp)
            .graphicsLayer { alpha = dim },
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.width(48.dp).height(if (dense) 40.dp else 48.dp).testTag("todo:box:${item.id}")
                .semantics { role = Role.Checkbox; stateDescription = stateText; toggleableState = ToggleableState(item.done); contentDescription = item.text }
                .metroPress(onClick = { env.onToggle(item) }),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.padding(top = 2.dp)) {
                if (style.isClassic) CheckSquare(shownDone)
                else MetroText(if (shownDone) "☑" else "☐", size = 28.sp, color = accent)
            }
            if (completing && o.burst && !LocalReduceMotion.current) Burst(accent, Modifier.graphicsLayer { translationY = -10.dp.toPx() })
        }
        Column(Modifier.weight(1f).padding(top = if (dense) 0.dp else 3.dp).testTag("todo:item").metroPress(onClick = { env.onEdit(item) })) {
            val titleSize = ((if (dense) 17 else 21) * k).sp
            val text = (if (item.starred) "★ " else "") + item.text
            if (shownDone) {
                BasicText(
                    text, style = TextStyle(color = subtle, fontSize = titleSize, fontWeight = style.weight(FontWeight.Light), fontFamily = style.font, textDecoration = TextDecoration.LineThrough),
                )
            } else {
                MetroText(text, size = titleSize, weight = if (o.contrast) FontWeight.Normal else FontWeight.Light, color = if (late) accent else colors.foreground)
            }
            val tint = env.colorOf(item.list)
            val small = (13 * k).sp
            val weight = if (o.contrast) FontWeight.SemiBold else FontWeight.Normal
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (item.due != 0L) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        GlyphIcon(if (item.reminderId != 0L) Glyph.Bell else Glyph.Calendar, (14 * k).dp, color = if (late) accent else subtle)
                        MetroText(whenText(item.due, env.now) + if (item.repeat != Repeat.None) " ↻" else "", size = small, weight = weight, color = if (late) accent else subtle)
                    }
                }
                if (item.list.isNotEmpty()) MetroText(item.list, size = small, weight = weight, color = tint ?: subtle)
                if (item.subs.isNotEmpty()) MetroText(stringResource(R.string.todo_steps, item.subs.count { it.done }, item.subs.size), size = small, weight = weight, color = subtle)
            }
            if (o.preview && !dense && item.note.isNotEmpty()) {
                MetroText(item.note.lineSequence().first(), size = small, weight = weight, color = subtle, maxLines = 1, minScale = 1f)
            }
        }
    }
}

/** A few small squares that fly out from the box and fade, once. */
@Composable
private fun Burst(color: Color, modifier: Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(BURST_MS)) }
    Canvas(modifier.size(48.dp)) {
        repeat(8) { i ->
            val angle = i * Math.PI / 4
            val radius = 10.dp.toPx() + 14.dp.toPx() * progress.value
            val half = 2.dp.toPx()
            drawRect(
                color.copy(alpha = 1f - progress.value),
                Offset(center.x + (cos(angle) * radius).toFloat() - half, center.y + (sin(angle) * radius).toFloat() - half),
                Size(half * 2, half * 2),
            )
        }
    }
}

private const val BURST_MS = 380

/** A row swiped to the right is done, to the left deleted; it follows the finger and comes back when let go short of [THRESHOLD_DP]. */
@Composable
fun Modifier.swipeRow(enabled: Boolean, onRight: () -> Unit, onLeft: () -> Unit): Modifier {
    if (!enabled) return this
    var dx by remember { mutableFloatStateOf(0f) }
    val right by rememberUpdatedState(onRight)
    val left by rememberUpdatedState(onLeft)
    val threshold = with(LocalDensity.current) { THRESHOLD_DP.dp.toPx() }
    return this
        .graphicsLayer { translationX = dx; alpha = 1f - min(abs(dx) / (threshold * 4), 0.5f) }
        .pointerInput(threshold) {
            detectHorizontalDragGestures(
                onDragEnd = { if (dx > threshold) right() else if (dx < -threshold) left(); dx = 0f },
                onDragCancel = { dx = 0f },
            ) { change, amount -> change.consume(); dx += amount }
        }
}

private const val THRESHOLD_DP = 96

/** The chips of a row drawn with a fade at both ends, so it shows there is more to scroll to. */
fun Modifier.fadeEdges(width: Dp = 20.dp): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
    drawContent()
    val w = width.toPx()
    drawRect(Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black, startX = 0f, endX = w), size = Size(w, size.height), blendMode = BlendMode.DstIn)
    drawRect(
        Brush.horizontalGradient(0f to Color.Black, 1f to Color.Transparent, startX = size.width - w, endX = size.width),
        topLeft = Offset(size.width - w, 0f), size = Size(w, size.height), blendMode = BlendMode.DstIn,
    )
}

/** A small choice among several, lit when it is the one in use; [tint] is the colour of a list (a dot before the name), [description] what a screen reader says for a sign. */
@Composable
fun TodoChip(text: String, selected: Boolean, tag: String, modifier: Modifier = Modifier, tint: Color? = null, description: String? = null, onClick: () -> Unit) {
    Row(
        modifier.testTag(tag).metroPress(onClick = onClick).litFill(selected).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (tint != null) Box(Modifier.size(9.dp).drawBehind { drawRect(tint) })
        MetroText(
            text, size = 16.sp, weight = FontWeight.Normal, color = if (selected) onAccent() else LocalMetroColors.current.foreground,
            modifier = if (description != null) Modifier.clearAndSetSemantics { contentDescription = description } else Modifier,
        )
    }
}

/** The title of a group of rows: in the accent colour, lowercase and large in the classic look. [count] is the open rows in it (null for no number). */
@Composable
fun GroupTitle(group: Todos.Group, now: Long, count: Int?, color: Color?) {
    val classic = LocalStyle.current.isClassic
    val colors = LocalMetroColors.current
    val today = remember(now / 3_600_000L) { LocalDate.now() }
    val name = when {
        group.section != null -> stringResource(
            when (group.section) {
                Todos.Section.Late -> R.string.todo_late
                Todos.Section.Today -> R.string.todo_today
                Todos.Section.Later -> R.string.todo_later
                Todos.Section.NoDate -> R.string.todo_no_date
            },
        )
        group.day != null -> when (group.day) {
            today -> stringResource(R.string.todo_today)
            today.plusDays(1) -> stringResource(R.string.todo_tomorrow)
            else -> ClockFormat.date(group.day, Locale.getDefault())
        }
        group.key == "d:none" -> stringResource(R.string.todo_no_date)
        group.list.isNullOrEmpty() -> stringResource(R.string.todo_no_list)
        else -> group.list
    }
    val late = group.section == Todos.Section.Late || (group.day != null && group.day.isBefore(today))
    val text = name + if (count != null) "  $count" else ""
    val accent = LocalAccent.current
    if (classic) MetroText(text.lowercase(Locale.getDefault()), size = 20.sp, color = color ?: accent, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
    else MetroText(text, size = 16.sp, weight = FontWeight.SemiBold, color = color ?: if (late) accent else colors.subtle, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}

/** A pictogram for a page with nothing on it: a check in a circle when all is done, a plus in a square when nothing was written yet. */
@Composable
fun EmptyArt(done: Boolean, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Canvas(modifier.size(84.dp)) {
        val stroke = Stroke(4.dp.toPx(), cap = StrokeCap.Square, join = StrokeJoin.Miter)
        if (done) {
            drawCircle(accent, radius = size.minDimension / 2 - 4.dp.toPx(), style = stroke)
            val tick = Path().apply { moveTo(size.width * 0.28f, size.height * 0.52f); lineTo(size.width * 0.45f, size.height * 0.68f); lineTo(size.width * 0.74f, size.height * 0.34f) }
            drawPath(tick, accent, style = stroke)
        } else {
            drawRect(accent, Offset(4.dp.toPx(), 4.dp.toPx()), Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()), style = stroke)
            drawLine(accent, Offset(size.width / 2, size.height * 0.3f), Offset(size.width / 2, size.height * 0.7f), 4.dp.toPx())
            drawLine(accent, Offset(size.width * 0.3f, size.height / 2), Offset(size.width * 0.7f, size.height / 2), 4.dp.toPx())
        }
    }
}

/** The width from which the page is laid out in two columns (the lists at the left): a tablet, or a folding phone opened. */
const val WIDE_DP = 600

@Composable
fun isWide(): Boolean = LocalConfiguration.current.screenWidthDp >= WIDE_DP
