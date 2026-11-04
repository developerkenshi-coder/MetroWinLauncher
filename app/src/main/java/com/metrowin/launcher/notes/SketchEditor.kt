package com.metrowin.launcher.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.start.DialogFrame
import com.metrowin.launcher.ui.theme.LocalMetroColors

private val inks = listOf(0xFF111111, 0xFF0050EF, 0xFFE51400, 0xFF008A00, 0xFFFA6800, 0xFF6A00FF, 0xFF7A7A7A).map { it.toInt() }
private val widths = listOf(3f, 6f, 12f)

/** What the finger does on the sketch: a tool of [Tool], or the eraser. */
private enum class Pencil(val symbol: String, val tool: Tool?) {
    Pen("✎", Tool.Pen), Line("╱", Tool.Line), Arrow("→", Tool.Arrow), Rect("▭", Tool.Rect), Ellipse("◯", Tool.Ellipse), Text("T", Tool.Text), Eraser("⌫", null)
}

/** The sketch of a note: pen and shapes for a diagram, text labels, an eraser, undo and redo. [onDone] gets the strokes when it is closed. */
@Composable
fun SketchEditor(initial: List<Stroke>, onDone: (List<Stroke>) -> Unit) {
    var strokes by remember { mutableStateOf(initial) }
    var undo by remember { mutableStateOf(emptyList<List<Stroke>>()) }
    var redo by remember { mutableStateOf(emptyList<List<Stroke>>()) }
    var pencil by remember { mutableStateOf(Pencil.Pen) }
    var ink by remember { mutableIntStateOf(0) }
    var width by remember { mutableIntStateOf(1) }
    var live by remember { mutableStateOf<Stroke?>(null) }
    var labelAt by remember { mutableStateOf<Pt?>(null) }
    var label by remember { mutableStateOf("") }
    val colors = LocalMetroColors.current

    fun commit(next: List<Stroke>) {
        undo = (undo + listOf(strokes)).takeLast(60)
        redo = emptyList()
        strokes = next
    }
    BackHandler { onDone(strokes) }

    MiniFrame {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pencil.entries.forEach { p -> PageChip(p.symbol, pencil == p, "sketch:tool:${p.name}") { pencil = p } }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            inks.forEachIndexed { index, value ->
                Box(
                    Modifier.size(30.dp).testTag("sketch:ink:$index").metroPress(onClick = { ink = index }).background(Color(value))
                        .border(if (ink == index) 3.dp else 1.dp, if (ink == index) colors.foreground else colors.subtle),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("S", "M", "L").forEachIndexed { index, text -> PageChip(text, width == index, "sketch:width:$index") { width = index } }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
            val pageWidth = minOf(maxWidth, maxHeight * (SketchPage.WIDTH / SketchPage.HEIGHT))
            Canvas(
                Modifier
                    .size(pageWidth, pageWidth * (SketchPage.HEIGHT / SketchPage.WIDTH))
                    .testTag("sketch:page")
                    .background(SketchPaper)
                    .pointerInput(pencil, ink, width) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val k = size.width / SketchPage.WIDTH
                            fun at(o: androidx.compose.ui.geometry.Offset) = Pt((o.x / k).coerceIn(0f, SketchPage.WIDTH), (o.y / k).coerceIn(0f, SketchPage.HEIGHT))
                            val first = at(down.position)
                            val tool = pencil.tool
                            when {
                                tool == null -> {
                                    val before = strokes
                                    fun erase(p: Pt) { strokes = strokes.filterNot { SketchGeometry.hit(it, p.x, p.y, 22f) } }
                                    erase(first)
                                    drag(down.id) { change -> change.consume(); erase(at(change.position)) }
                                    if (strokes.size != before.size) { undo = (undo + listOf(before)).takeLast(60); redo = emptyList() }
                                }
                                tool == Tool.Text -> {
                                    val up = awaitPointerEventUp(down.id)
                                    if (up) labelAt = first
                                }
                                else -> {
                                    var current = Stroke(tool, inks[ink], widths[width], listOf(first))
                                    live = current
                                    drag(down.id) { change ->
                                        change.consume()
                                        val p = at(change.position)
                                        current = if (tool == Tool.Pen) current.copy(points = current.points + p) else current.copy(points = listOf(first, p))
                                        live = current
                                    }
                                    live = null
                                    // A tap with a shape draws nothing; with the pen it is a dot.
                                    if (tool == Tool.Pen || current.points.size >= 2 && current.points[1] != first) commit(strokes + current.copy(points = current.points.take(NoteCodec.MAX_POINTS)))
                                }
                            }
                        }
                    },
            ) {
                drawStrokes(strokes)
                live?.let { drawStroke(it, size.width / SketchPage.WIDTH) }
            }
        }
        AppBar(
            listOf(
                AppBarItem("↶", stringResource(R.string.sketch_undo)) {
                    undo.lastOrNull()?.let { previous -> redo = redo + listOf(strokes); strokes = previous; undo = undo.dropLast(1) }
                },
                AppBarItem("↷", stringResource(R.string.sketch_redo)) {
                    redo.lastOrNull()?.let { next -> undo = undo + listOf(strokes); strokes = next; redo = redo.dropLast(1) }
                },
                AppBarItem("✕", stringResource(R.string.sketch_clear)) { if (strokes.isNotEmpty()) commit(emptyList()) },
                AppBarItem("✓", stringResource(R.string.sketch_done)) { onDone(strokes) },
            ),
        )
    }
    labelAt?.let { at ->
        DialogFrame(onCancel = { labelAt = null; label = "" }) {
            MetroTextField(label, { label = it.take(200) }, stringResource(R.string.sketch_label_hint), singleLine = false, modifier = Modifier.testTag("sketch:label"))
            MetroButton(stringResource(R.string.sketch_add), Modifier.fillMaxWidth().testTag("sketch:label:add")) {
                if (label.isNotBlank()) commit(strokes + Stroke(Tool.Text, inks[ink], widths[width], listOf(at), label.trim()))
                labelAt = null
                label = ""
            }
        }
    }
}

/** True when the finger [id] goes up without having moved much (a tap). */
private suspend fun AwaitPointerEventScope.awaitPointerEventUp(id: PointerId): Boolean {
    var moved = false
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == id } ?: return false
        if (change.positionChanged() && (change.position - change.previousPosition).getDistance() > 8f) moved = true
        if (change.changedToUp()) return !moved
    }
}
