package com.metrowin.launcher.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.MetroAccents
import com.metrowin.launcher.ui.theme.onAccent
import com.metrowin.launcher.ui.theme.toArgbInt
import kotlinx.coroutines.delay

/** Black, then the twenty accent colours of the phone: the same palette as everything else of the launcher. */
private val inks: List<Int> = listOf(0xFF111111.toInt()) + MetroAccents.map { it.toArgbInt() }
private val widths = listOf(3f, 6f, 12f)

/** What the finger does on the sketch: a tool of [Tool], the eraser, or the hand that moves what was drawn. The first three are the ink, the others make shapes and labels. */
private enum class Pencil(val symbol: String, val tool: Tool?) {
    Pen("✎", Tool.Pen), Highlighter("▬", Tool.Highlighter), Eraser("⌫", null),
    Line("╱", Tool.Line), Arrow("→", Tool.Arrow), Rect("▭", Tool.Rect), Ellipse("◯", Tool.Ellipse), Text("T", Tool.Text),
    Move("↔", null),
}

/** A round button of the tools, with the edge of 2 dp of the buttons of the bars of Windows Phone; filled with the accent while it is the tool in hand. */
@Composable
private fun ToolButton(pencil: Pencil, selected: Boolean, label: String, onClick: () -> Unit) {
    val colors = LocalMetroColors.current
    val ink = if (selected) onAccent() else colors.foreground
    Box(
        Modifier.size(44.dp).testTag("sketch:tool:${pencil.name}").semantics { contentDescription = label }.metroPress(onClick = onClick)
            .then(if (selected) Modifier.background(LocalAccent.current, CircleShape) else Modifier.border(2.dp, colors.foreground, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (pencil == Pencil.Highlighter) {
            // The marker: a wide bar, as it draws.
            Box(Modifier.size(width = 22.dp, height = 10.dp).background(ink.copy(alpha = 0.75f)))
        } else {
            MetroText(pencil.symbol, size = 20.sp, weight = FontWeight.Normal, color = ink)
        }
    }
}

/** A short stroke in the colour and the width picked: what the next line will look like. */
@Composable
private fun BrushPreview(color: Int, width: Float, marker: Boolean) {
    val colors = LocalMetroColors.current
    Canvas(Modifier.size(width = 64.dp, height = 32.dp).background(SketchPaper).border(1.dp, colors.subtle).testTag("sketch:brush")) {
        // The page is 1000 units across on about 340 dp: one unit is a third of a dp.
        val w = (if (marker) width * SketchGeometry.HIGHLIGHT else width) * 0.34f * density
        val c = Color(color).let { if (marker) it.copy(alpha = 0.4f) else it }
        drawLine(c, Offset(size.height / 2, size.height / 2), Offset(size.width - size.height / 2, size.height / 2), minOf(w, size.height), if (marker) StrokeCap.Square else StrokeCap.Round)
    }
}

/**
 * The sketch of a note: pen, highlighter and eraser, shapes for a diagram, text labels, the accent colours, undo and redo. [onDraft] gets the strokes
 * a moment after each change and when the app goes to the background (so a sketch is not lost with the process); [onDone] gets them when it is closed.
 */
@Composable
fun SketchEditor(initial: List<Stroke>, initialPaper: Int, prefs: NotePrefs, onDraft: (List<Stroke>, Int) -> Unit, onDone: (List<Stroke>, Int) -> Unit) {
    var strokes by remember { mutableStateOf(initial) }
    var undo by remember { mutableStateOf(emptyList<List<Stroke>>()) }
    var redo by remember { mutableStateOf(emptyList<List<Stroke>>()) }
    var pencil by remember { mutableStateOf(Pencil.Pen) }
    var ink by remember { mutableIntStateOf(0) }
    var width by remember { mutableIntStateOf(1) }
    var paper by remember { mutableIntStateOf(initialPaper) }
    // The stroke being drawn: its tool, colour and width here, its points in a list that grows (a copy of the list at every move made the sketch slower the longer a line was).
    var live by remember { mutableStateOf<Stroke?>(null) }
    val livePoints = remember { ArrayList<Pt>() }
    var tick by remember { mutableIntStateOf(0) }
    var labelAt by remember { mutableStateOf<Pt?>(null) }
    var editing by remember { mutableIntStateOf(-1) }
    var label by remember { mutableStateOf("") }
    var stylusSeen by remember { mutableStateOf(false) }
    val colors = LocalMetroColors.current
    val moveOn = prefs.on(NotePrefs.MOVE)
    val stylusOnly = prefs.on(NotePrefs.STYLUS_ONLY)

    fun commit(next: List<Stroke>) {
        undo = (undo + listOf(strokes)).takeLast(60)
        redo = emptyList()
        strokes = next
    }
    val latest by rememberUpdatedState(strokes to paper)
    var saved by remember { mutableStateOf(initial to initialPaper) }
    LaunchedEffect(strokes, paper) {
        if ((strokes to paper) == saved) return@LaunchedEffect
        delay(800)
        saved = strokes to paper
        onDraft(strokes, paper)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val watcher = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP && latest != saved) { saved = latest; onDraft(latest.first, latest.second) } }
        lifecycle.addObserver(watcher)
        onDispose { lifecycle.removeObserver(watcher) }
    }
    BackHandler { onDone(strokes, paper) }

    val inkLabel = stringResource(R.string.sketch_ink)
    val pageDescription = pluralStringResource(R.plurals.sketch_page_cd, strokes.size, strokes.size)
    val toolNames = mapOf(
        Pencil.Pen to R.string.sketch_tool_pen, Pencil.Highlighter to R.string.sketch_tool_marker, Pencil.Eraser to R.string.sketch_tool_eraser,
        Pencil.Line to R.string.sketch_tool_line, Pencil.Arrow to R.string.sketch_tool_arrow, Pencil.Rect to R.string.sketch_tool_rect,
        Pencil.Ellipse to R.string.sketch_tool_ellipse, Pencil.Text to R.string.sketch_tool_text, Pencil.Move to R.string.sketch_tool_move,
    )

    MiniFrame {
        // The ink (pen, highlighter, eraser) and then the shapes, as round buttons: a row that scrolls, so on a narrow phone none is cut off.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            val tools = Pencil.entries.filter { it != Pencil.Move || moveOn }
            tools.take(3).forEach { p -> ToolButton(p, pencil == p, stringResource(toolNames.getValue(p))) { pencil = p } }
            Box(Modifier.padding(horizontal = 6.dp).size(width = 2.dp, height = 28.dp).background(colors.subtle))
            tools.drop(3).forEach { p -> ToolButton(p, pencil == p, stringResource(toolNames.getValue(p))) { pencil = p } }
        }
        // The colours: black and the accents of the phone, side by side.
        LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            itemsIndexed(inks) { index, value ->
                val inkName = "$inkLabel ${index + 1}"
                val chosen = ink == index
                Box(
                    Modifier.size(if (chosen) 38.dp else 30.dp).testTag("sketch:ink:$index").semantics { contentDescription = inkName }
                        .metroPress(onClick = { ink = index }).background(Color(value))
                        .border(if (chosen) 4.dp else 1.dp, if (chosen) colors.foreground else colors.subtle),
                )
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val sizeNames = listOf(R.string.sketch_width_thin to R.string.sketch_width_s, R.string.sketch_width_medium to R.string.sketch_width_m, R.string.sketch_width_thick to R.string.sketch_width_l)
            sizeNames.forEachIndexed { index, (name, short) ->
                val description = stringResource(name)
                PageChip(stringResource(short), width == index, "sketch:width:$index", Modifier.semantics { contentDescription = description }) { width = index }
            }
            BrushPreview(inks[ink], widths[width], pencil == Pencil.Highlighter)
            if (prefs.on(NotePrefs.PAPER)) {
                Box(Modifier.padding(horizontal = 4.dp).size(width = 2.dp, height = 28.dp).background(colors.subtle))
                listOf(R.string.sketch_paper_plain, R.string.sketch_paper_squares, R.string.sketch_paper_lines, R.string.sketch_paper_dots).forEachIndexed { index, name ->
                    PageChip(stringResource(name), paper == index, "sketch:paper:$index") { paper = index }
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
            val pageWidth = minOf(maxWidth, maxHeight * (SketchPage.WIDTH / SketchPage.HEIGHT))
            Canvas(
                Modifier
                    .size(pageWidth, pageWidth * (SketchPage.HEIGHT / SketchPage.WIDTH))
                    .testTag("sketch:page")
                    .semantics { contentDescription = pageDescription }
                    .background(SketchPaper)
                    .pointerInput(pencil, ink, width, stylusOnly) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            if (down.type == PointerType.Stylus) stylusSeen = true
                            // With a pen in hand, a hand resting on the screen draws nothing.
                            if (stylusOnly && stylusSeen && down.type != PointerType.Stylus) return@awaitEachGesture
                            val k = size.width / SketchPage.WIDTH
                            fun at(o: Offset) = Pt((o.x / k).coerceIn(0f, SketchPage.WIDTH), (o.y / k).coerceIn(0f, SketchPage.HEIGHT))
                            val first = at(down.position)
                            val tool = pencil.tool
                            when {
                                pencil == Pencil.Move -> {
                                    val index = strokes.indexOfLast { SketchGeometry.hit(it, first.x, first.y, 22f) }
                                    if (index < 0) return@awaitEachGesture
                                    val before = strokes
                                    val start = before[index]
                                    drag(down.id) { change ->
                                        change.consume()
                                        val here = at(change.position)
                                        val shifted = SketchGeometry.moved(start, here.x - first.x, here.y - first.y)
                                        strokes = before.mapIndexed { i, s -> if (i == index) shifted else s }
                                    }
                                    if (strokes != before) { undo = (undo + listOf(before)).takeLast(60); redo = emptyList() }
                                }
                                tool == null -> {
                                    val before = strokes
                                    fun erase(p: Pt) { strokes = strokes.filterNot { SketchGeometry.hit(it, p.x, p.y, 22f) } }
                                    erase(first)
                                    drag(down.id) { change -> change.consume(); erase(at(change.position)) }
                                    if (strokes.size != before.size) { undo = (undo + listOf(before)).takeLast(60); redo = emptyList() }
                                }
                                tool == Tool.Text -> {
                                    val up = awaitPointerEventUp(down.id)
                                    if (up) {
                                        // A tap on a label already there changes it, if the hand tool is on; elsewhere it starts a new one.
                                        editing = if (moveOn) strokes.indexOfLast { it.tool == Tool.Text && SketchGeometry.hit(it, first.x, first.y, 10f) } else -1
                                        label = strokes.getOrNull(editing)?.text.orEmpty()
                                        labelAt = first
                                    }
                                }
                                else -> {
                                    val head = Stroke(tool, inks[ink], widths[width], emptyList())
                                    livePoints.clear()
                                    livePoints += first
                                    live = head
                                    tick++
                                    val freehand = tool == Tool.Pen || tool == Tool.Highlighter
                                    drag(down.id) { change ->
                                        change.consume()
                                        val p = at(change.position)
                                        if (freehand) { if (livePoints.size < NoteCodec.MAX_POINTS) livePoints += p } else { livePoints.clear(); livePoints += first; livePoints += p }
                                        tick++
                                    }
                                    val points = livePoints.toList()
                                    live = null
                                    // A tap with a shape draws nothing; with the pen or the marker it is a dot.
                                    if (freehand || points.size >= 2 && points[1] != first) commit(strokes + head.copy(points = points))
                                }
                            }
                        }
                    },
            ) {
                drawPaper(paper)
                drawStrokes(strokes)
                // Reading the counter here is what draws the line again at each move.
                if (tick >= 0) live?.let { drawStroke(it.copy(points = livePoints), size.width / SketchPage.WIDTH) }
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
                AppBarItem("✓", stringResource(R.string.sketch_done)) { onDone(strokes, paper) },
            ),
        )
    }
    labelAt?.let { at ->
        val close = { labelAt = null; label = ""; editing = -1 }
        DialogFrame(onCancel = close) {
            MetroTextField(label, { label = it.take(200) }, stringResource(R.string.sketch_label_hint), singleLine = false, modifier = Modifier.testTag("sketch:label"))
            MetroButton(stringResource(if (editing >= 0) R.string.sketch_change else R.string.sketch_add), Modifier.fillMaxWidth().testTag("sketch:label:add")) {
                val old = strokes.getOrNull(editing)
                if (label.isNotBlank()) {
                    if (old != null) commit(strokes.mapIndexed { i, s -> if (i == editing) s.copy(text = label.trim()) else s })
                    else commit(strokes + Stroke(Tool.Text, inks[ink], widths[width], listOf(at), label.trim()))
                }
                close()
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
