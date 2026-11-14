package com.metrowin.launcher.calculator

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.CalcEngine
import com.metrowin.launcher.data.CalcTools
import com.metrowin.launcher.data.ConvertTable
import com.metrowin.launcher.data.Currencies
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import java.time.LocalDate
import java.util.Locale

private val TOOLS = listOf(
    R.string.calc_tool_programmer, R.string.calc_tool_dates, R.string.calc_tool_bill, R.string.calc_tool_stats,
    R.string.calc_tool_equations, R.string.calc_tool_graph, R.string.calc_tool_constants,
)

/** The tools next to the keypad: programmer, dates, bill, statistics, equations, graph and constants. [onInsert] puts a number in the calculator. */
@Composable
internal fun ToolsPage(menu: List<AppBarItem>, onInsert: (String) -> Unit) {
    var tool by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).padding(horizontal = 24.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                itemsIndexed(TOOLS) { index, name -> PageChip(stringResource(name), tool == index, "calc:tool:$index") { tool = index } }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tool) {
                    0 -> ProgrammerTool(onInsert)
                    1 -> DatesTool()
                    2 -> BillTool()
                    3 -> StatsTool()
                    4 -> EquationsTool()
                    5 -> GraphTool()
                    else -> ConstantsTool(onInsert)
                }
            }
        }
        AppBar(emptyList(), menu = menu)
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String, tag: String, keyboardType: KeyboardType = KeyboardType.Text, singleLine: Boolean = true) {
    MetroTextField(value, onChange, placeholder, Modifier.padding(bottom = 8.dp).testTag(tag), keyboardType = keyboardType, singleLine = singleLine)
}

@Composable
private fun Out(text: String, tag: String, big: Boolean = false, onClick: (() -> Unit)? = null) {
    val modifier = Modifier.fillMaxWidth().testTag(tag).let { if (onClick != null) it.metroPress(onClick = onClick) else it }
    MetroText(text, size = if (big) 30.sp else 20.sp, color = if (big) LocalAccent.current else LocalMetroColors.current.foreground, modifier = modifier.padding(vertical = 3.dp))
}

@Composable
private fun Tool(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { content() }
}

private fun fmt(value: Double) = CalcEngine.format(value, Locale.getDefault())

// --- programmer

private val BASES = listOf("BIN" to 2, "OCT" to 8, "DEC" to 10, "HEX" to 16)
private val OPS = listOf("+" to CalcTools.Op.Add, "−" to CalcTools.Op.Sub, "×" to CalcTools.Op.Mul, "÷" to CalcTools.Op.Div, "AND" to CalcTools.Op.And, "OR" to CalcTools.Op.Or, "XOR" to CalcTools.Op.Xor, "<<" to CalcTools.Op.Shl, ">>" to CalcTools.Op.Shr, "NOT" to CalcTools.Op.Not)

@Composable
private fun ProgrammerTool(onInsert: (String) -> Unit) {
    var base by rememberSaveable { mutableIntStateOf(2) }
    var aText by rememberSaveable { mutableStateOf("") }
    var bText by rememberSaveable { mutableStateOf("") }
    var op by rememberSaveable { mutableIntStateOf(0) }
    val radix = BASES[base].second
    val a = CalcTools.parseInt(aText, radix)
    val b = CalcTools.parseInt(bText, radix)
    val operation = OPS[op].second
    val needsB = operation != CalcTools.Op.Not
    val result = if (a != null && (!needsB || b != null)) CalcTools.apply(a, operation, if (needsB) b!! else 0L) else null
    Tool {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            itemsIndexed(BASES) { index, (name, _) -> PageChip(name, base == index, "calc:prog:base:$name") { base = index } }
        }
        Field(aText, { aText = it.filter { c -> c.isLetterOrDigit() || c == '-' }.take(66) }, stringResource(R.string.calc_prog_a), "calc:prog:a")
        if (needsB) Field(bText, { bText = it.filter { c -> c.isLetterOrDigit() || c == '-' }.take(66) }, stringResource(R.string.calc_prog_b), "calc:prog:b")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            itemsIndexed(OPS) { index, (name, _) -> PageChip(name, op == index, "calc:prog:op:$index") { op = index } }
        }
        if (result != null) {
            BASES.forEach { (name, r) ->
                Out("$name  ${CalcTools.formatInt(result, r)}", "calc:prog:out:$name", big = r == radix, onClick = if (r == 10) ({ onInsert(result.toString().replace('-', '−')) }) else null)
            }
        } else if (aText.isNotEmpty()) PageNote(stringResource(R.string.calc_prog_error), Modifier.testTag("calc:prog:error"))
    }
}

// --- dates

@Composable
private fun DatesTool() {
    var from by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var to by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var days by rememberSaveable { mutableStateOf("30") }
    val a = CalcTools.parseDate(from)
    val b = CalcTools.parseDate(to)
    val n = days.toLongOrNull()
    Tool {
        Field(from, { from = it.take(10) }, stringResource(R.string.calc_date_from), "calc:date:from")
        Field(to, { to = it.take(10) }, stringResource(R.string.calc_date_to), "calc:date:to")
        if (a != null && b != null) {
            val gap = CalcTools.between(a, b)
            Out(stringResource(R.string.calc_date_gap, gap.years, gap.months, gap.days), "calc:date:gap", big = true)
            Out(stringResource(R.string.calc_date_total, gap.totalDays), "calc:date:total")
        } else PageNote(stringResource(R.string.calc_date_invalid), Modifier.testTag("calc:date:invalid"))
        Field(days, { days = it.filter { c -> c.isDigit() || c == '-' }.take(7) }, stringResource(R.string.calc_date_days), "calc:date:days", KeyboardType.Number)
        if (a != null && n != null) Out(stringResource(R.string.calc_date_after, n, a.plusDays(n).toString()), "calc:date:after")
    }
}

// --- bill

@Composable
private fun BillTool() {
    var amount by rememberSaveable { mutableStateOf("") }
    var tip by rememberSaveable { mutableStateOf("10") }
    var people by rememberSaveable { mutableStateOf("1") }
    val bill = ConvertTable.parseAmount(amount)?.let { a ->
        CalcTools.bill(a, ConvertTable.parseAmount(tip) ?: 0.0, people.toIntOrNull() ?: 0)
    }
    val locale = Locale.getDefault()
    Tool {
        Field(amount, { amount = ConvertTable.cleanAmount(it) }, stringResource(R.string.calc_bill_amount), "calc:bill:amount", KeyboardType.Decimal)
        Field(tip, { tip = ConvertTable.cleanAmount(it) }, stringResource(R.string.calc_bill_tip), "calc:bill:tip", KeyboardType.Decimal)
        Field(people, { people = it.filter(Char::isDigit).take(3) }, stringResource(R.string.calc_bill_people), "calc:bill:people", KeyboardType.Number)
        if (bill != null) {
            Out(stringResource(R.string.calc_bill_each, Currencies.format(bill.perPerson, locale)), "calc:bill:each", big = true)
            Out(stringResource(R.string.calc_bill_tip_is, Currencies.format(bill.tip, locale)), "calc:bill:tipis")
            Out(stringResource(R.string.calc_bill_total, Currencies.format(bill.total, locale)), "calc:bill:total")
        }
    }
}

// --- statistics

@Composable
private fun StatsTool() {
    var xs by rememberSaveable { mutableStateOf("") }
    var ys by rememberSaveable { mutableStateOf("") }
    val x = CalcTools.parseList(xs)
    val y = CalcTools.parseList(ys)
    val stats = x?.let { CalcTools.stats(it) }
    Tool {
        Field(xs, { xs = it.take(4000) }, stringResource(R.string.calc_stats_x), "calc:stats:x", singleLine = false)
        Field(ys, { ys = it.take(4000) }, stringResource(R.string.calc_stats_y), "calc:stats:y", singleLine = false)
        if (stats != null) {
            Out(stringResource(R.string.calc_stat_mean) + "  " + fmt(stats.mean), "calc:stats:mean", big = true)
            Out(stringResource(R.string.calc_stat_count) + "  " + stats.count, "calc:stats:count")
            Out(stringResource(R.string.calc_stat_sum) + "  " + fmt(stats.sum), "calc:stats:sum")
            Out(stringResource(R.string.calc_stat_median) + "  " + fmt(stats.median), "calc:stats:median")
            Out(stringResource(R.string.calc_stat_min) + "  " + fmt(stats.min) + "   " + stringResource(R.string.calc_stat_max) + "  " + fmt(stats.max), "calc:stats:range")
            Out(stringResource(R.string.calc_stat_sd) + "  " + fmt(stats.deviationOfPopulation), "calc:stats:sd")
            stats.deviationOfSample?.let { Out(stringResource(R.string.calc_stat_sds) + "  " + fmt(it), "calc:stats:sds") }
            val line = if (y != null) CalcTools.regression(x, y) else null
            if (line != null) {
                Out(stringResource(R.string.calc_stat_slope) + "  " + fmt(line.slope), "calc:stats:slope")
                Out(stringResource(R.string.calc_stat_intercept) + "  " + fmt(line.intercept), "calc:stats:intercept")
                Out(stringResource(R.string.calc_stat_r) + "  " + fmt(line.correlation), "calc:stats:r")
            }
        } else if (xs.isNotBlank()) PageNote(stringResource(R.string.calc_stats_error), Modifier.testTag("calc:stats:error"))
    }
}

// --- equations

@Composable
private fun EquationsTool() {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var text by rememberSaveable { mutableStateOf("") }
    Tool {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            PageChip(stringResource(R.string.calc_eq_quadratic), mode == 0, "calc:eq:quadratic") { mode = 0 }
            PageChip(stringResource(R.string.calc_eq_system), mode == 1, "calc:eq:system") { mode = 1 }
        }
        PageNote(stringResource(if (mode == 0) R.string.calc_eq_quadratic_hint else R.string.calc_eq_system_hint), Modifier.padding(bottom = 6.dp))
        Field(text, { text = it.take(2000) }, stringResource(R.string.calc_eq_input), "calc:eq:input", singleLine = mode == 0)
        if (text.isBlank()) return@Tool
        if (mode == 0) {
            val c = CalcTools.parseList(text)?.takeIf { it.size == 3 }
            when (val roots = c?.let { CalcTools.quadratic(it[0], it[1], it[2]) }) {
                is CalcTools.Roots.Real -> {
                    Out("x₁ = " + fmt(roots.x1), "calc:eq:x1", big = true)
                    if (roots.x1 != roots.x2) Out("x₂ = " + fmt(roots.x2), "calc:eq:x2", big = true)
                }
                is CalcTools.Roots.Complex -> Out("x = " + fmt(roots.re) + " ± " + fmt(roots.im) + " i", "calc:eq:complex", big = true)
                null -> PageNote(stringResource(R.string.calc_eq_error), Modifier.testTag("calc:eq:error"))
            }
        } else {
            val rows = text.lines().filter { it.isNotBlank() }.map { CalcTools.parseList(it) }
            val solution = if (rows.any { it == null }) null else CalcTools.solve(rows.filterNotNull())
            if (solution == null) PageNote(stringResource(R.string.calc_eq_none), Modifier.testTag("calc:eq:none"))
            else solution.forEachIndexed { i, v -> Out("${UNKNOWNS.getOrElse(i) { "x${i + 1}" }} = ${fmt(v)}", "calc:eq:v$i", big = true) }
        }
    }
}

private val UNKNOWNS = listOf("x", "y", "z", "w", "v")

// --- graph

@Composable
private fun GraphTool() {
    var expression by rememberSaveable { mutableStateOf("sin(x)") }
    var centerX by remember { mutableFloatStateOf(0f) }
    var centerY by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(0f) } // pixels for one unit; 0 until the canvas has a size
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    Column(Modifier.fillMaxSize()) {
        Field(expression, { expression = it.take(200) }, stringResource(R.string.calc_graph_input), "calc:graph:input")
        PageNote(stringResource(R.string.calc_graph_hint), Modifier.padding(bottom = 6.dp))
        Canvas(
            Modifier.fillMaxWidth().weight(1f).testTag("calc:graph:canvas").onSizeChanged { if (scale == 0f) scale = it.height / 12f }.pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    if (scale == 0f) return@detectTransformGestures
                    // The point under the fingers stays under them: zoom around the centroid, then follow the pan.
                    val before = Offset((centroid.x - size.width / 2f) / scale + centerX, centerY - (centroid.y - size.height / 2f) / scale)
                    scale = (scale * zoom).coerceIn(2f, 20_000f)
                    centerX = before.x - (centroid.x - size.width / 2f) / scale - pan.x / scale
                    centerY = before.y + (centroid.y - size.height / 2f) / scale + pan.y / scale
                }
            },
        ) {
            if (scale == 0f) return@Canvas
            val w = size.width
            val h = size.height
            fun px(x: Float) = w / 2f + (x - centerX) * scale
            fun py(y: Float) = h / 2f - (y - centerY) * scale
            if (px(0f) in 0f..w) drawLine(colors.subtle, Offset(px(0f), 0f), Offset(px(0f), h), 2f)
            if (py(0f) in 0f..h) drawLine(colors.subtle, Offset(0f, py(0f)), Offset(w, py(0f)), 2f)
            val count = (w / 2f).toInt().coerceIn(2, 600)
            val left = centerX - w / 2f / scale
            val right = centerX + w / 2f / scale
            val values = CalcTools.sample(expression, left.toDouble(), right.toDouble(), count, CalcEngine.Angle.Radians)
            val path = Path()
            var drawing = false
            var lastY = 0f
            for (i in 0 until count) {
                val y = values[i]
                val x = px(left + (right - left) * i / (count - 1))
                val p = py(y.toFloat())
                // A gap (no value) or a jump across the whole screen (an asymptote) ends the line.
                if (y.isNaN() || !p.isFinite() || (drawing && kotlin.math.abs(p - lastY) > h * 2)) { drawing = false; continue }
                if (!drawing) { path.moveTo(x, p); drawing = true } else path.lineTo(x, p)
                lastY = p
            }
            drawPath(path, accent, style = Stroke(width = 4f))
        }
        MetroButton(stringResource(R.string.calc_graph_reset), Modifier.padding(top = 6.dp).testTag("calc:graph:reset")) { centerX = 0f; centerY = 0f; scale = 0f }
    }
}

// --- constants

@Composable
private fun ConstantsTool(onInsert: (String) -> Unit) {
    Tool {
        PageNote(stringResource(R.string.calc_const_hint), Modifier.padding(bottom = 6.dp))
        CalcTools.constants.forEach { c ->
            Column(Modifier.fillMaxWidth().testTag("calc:const:${c.symbol}").metroPress(onClick = { onInsert(CalcEngine.format(c.value)) }).padding(vertical = 6.dp)) {
                MetroText(c.symbol + "  " + constantName(c.symbol), size = 18.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
                MetroText(fmt(c.value) + " " + c.unit, size = 22.sp)
            }
        }
    }
}

@Composable
private fun constantName(symbol: String): String = stringResource(
    when (symbol) {
        "c" -> R.string.calc_const_c
        "g" -> R.string.calc_const_g
        "G" -> R.string.calc_const_gg
        "h" -> R.string.calc_const_h
        "e" -> R.string.calc_const_e
        "k" -> R.string.calc_const_k
        "N" -> R.string.calc_const_n
        "R" -> R.string.calc_const_r
        "mₑ" -> R.string.calc_const_me
        else -> R.string.calc_const_mp
    },
)
