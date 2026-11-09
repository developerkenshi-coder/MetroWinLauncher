package com.metrowin.launcher.calculator

import com.metrowin.launcher.ui.theme.LocalStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.CalcEngine
import com.metrowin.launcher.data.CalcSession
import com.metrowin.launcher.data.ConvertTable
import com.metrowin.launcher.data.CurrencyRepository
import com.metrowin.launcher.data.Currencies
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.start.DialogFrame
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.onAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.MathContext
import java.text.DecimalFormatSymbols
import java.util.Locale

@Composable
fun CalculatorScreen() {
    val context = LocalContext.current
    val store = remember { CalcStore(context) }
    val session = remember { store.load() }
    // The session is plain Kotlin: what the screen shows is a copy of it, replaced at every key.
    var view by remember { mutableStateOf(session.view()) }
    val act: (CalcSession.() -> Unit) -> Unit = {
        session.it()
        store.save(session)
        view = session.view()
    }
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    MiniFrame {
        PivotHeader(
            listOf(stringResource(R.string.calc_pivot_calc), stringResource(R.string.calc_pivot_history), stringResource(R.string.calc_pivot_convert)),
            pager, Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp),
        )
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { page ->
            when (page) {
                0 -> CalcPage(view, act)
                1 -> HistoryPage(view, act) { act { recall(it) }; scope.launch { pager.animateScrollToPage(0) } }
                else -> ConverterPage()
            }
        }
    }
}

private fun decimal(): Char = DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator

/** The expression or the number as the language writes it: the point becomes the decimal sign of the language. */
private fun shown(text: String): String = text.replace('.', decimal())

@Composable
private fun CalcPage(view: CalcSession.View, act: (CalcSession.() -> Unit) -> Unit) {
    var scientific by remember { mutableStateOf(false) }
    val colors = LocalMetroColors.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        val showScientific = scientific || wide
        Column(Modifier.fillMaxSize()) {
            Display(view, Modifier.padding(horizontal = 20.dp).weight(if (wide) 0.55f else 0.9f))
            MemoryRow(act)
            Row(Modifier.weight(if (wide) 1.6f else 2.8f).padding(horizontal = 6.dp)) {
                if (showScientific) {
                    ScientificPanel(act, Modifier.weight(1.25f))
                }
                Keypad(act, Modifier.weight(1f))
            }
            AppBar(
                buildList {
                    if (!wide) add(AppBarItem("ƒ", stringResource(if (scientific) R.string.calc_standard else R.string.calc_scientific)) { scientific = !scientific })
                    add(AppBarItem(if (view.angle == CalcEngine.Angle.Degrees) "°" else "㎭", stringResource(if (view.angle == CalcEngine.Angle.Degrees) R.string.calc_degrees else R.string.calc_radians)) { act { toggleAngle() } })
                },
                Modifier.background(colors.background),
            )
        }
    }
}

@Composable
private fun Display(session: CalcSession.View, modifier: Modifier) {
    val colors = LocalMetroColors.current
    val locale = Locale.getDefault()
    val failure = session.failure
    val preview = if (!session.finished && failure == null && !isPlainNumber(session.expression)) session.preview else null
    Column(modifier.fillMaxWidth().testTag("calc:display"), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Bottom) {
        val top = when {
            failure != null -> stringResource(
                when (failure) {
                    CalcEngine.Error.Syntax -> R.string.calc_error_syntax
                    CalcEngine.Error.Domain -> R.string.calc_error_domain
                    CalcEngine.Error.Overflow -> R.string.calc_error_overflow
                },
            )
            session.finished -> shown(session.lastExpression) + " ="
            session.memory != 0.0 -> "M"
            else -> ""
        }
        MetroText(
            top, size = 18.sp, weight = FontWeight.Normal, color = if (failure != null) LocalAccent.current else colors.subtle,
            maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().testTag("calc:top"),
        )
        MetroText(
            shown(session.expression).ifEmpty { "0" }, size = 54.sp, maxLines = 2, lineHeight = 58.sp, textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().testTag("calc:expression"),
        )
        MetroText(
            if (preview != null) "= " + CalcEngine.format(preview, locale) else " ", size = 24.sp, weight = FontWeight.Normal,
            color = colors.subtle, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().testTag("calc:preview"),
        )
    }
}

/** A bare number (or a minus sign and a number): the preview would only repeat it. */
private fun isPlainNumber(text: String) = text.isEmpty() || text.removePrefix("−").all { it.isDigit() || it == '.' }

@Composable
private fun MemoryRow(act: (CalcSession.() -> Unit) -> Unit) {
    Row(Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 6.dp)) {
        Key("MC", Modifier.weight(1f), small = true, tag = "calc:mc") { act { memoryClear() } }
        Key("MR", Modifier.weight(1f), small = true, tag = "calc:mr") { act { memoryRecall() } }
        Key("M+", Modifier.weight(1f), small = true, tag = "calc:mplus") { act { memoryAdd(1) } }
        Key("M−", Modifier.weight(1f), small = true, tag = "calc:mminus") { act { memoryAdd(-1) } }
    }
}

@Composable
private fun Keypad(act: (CalcSession.() -> Unit) -> Unit, modifier: Modifier) {
    val dot = decimal().toString()
    Column(modifier) {
        KeyRow {
            Key("C", Modifier.weight(1f), tag = "calc:clear") { act { clear() } }
            Key("⌫", Modifier.weight(1f), tag = "calc:back") { act { backspace() } }
            Key("%", Modifier.weight(1f), tag = "calc:percent") { act { postfix('%') } }
            Key("÷", Modifier.weight(1f), operator = true, tag = "calc:div") { act { operator('÷') } }
        }
        for (row in listOf("789×", "456−", "123+")) {
            KeyRow {
                row.forEach { c ->
                    if (c.isDigit()) Key(c.toString(), Modifier.weight(1f), digit = true, tag = "calc:$c") { act { digit(c) } }
                    else Key(c.toString(), Modifier.weight(1f), operator = true, tag = "calc:$c") { act { operator(c) } }
                }
            }
        }
        KeyRow {
            Key("±", Modifier.weight(1f), tag = "calc:sign") { act { negate() } }
            Key("0", Modifier.weight(1f), digit = true, tag = "calc:0") { act { digit('0') } }
            Key(dot, Modifier.weight(1f), digit = true, tag = "calc:point") { act { point() } }
            Key("=", Modifier.weight(1f), equals = true, tag = "calc:equals") { act { equals() } }
        }
    }
}

@Composable
private fun ScientificPanel(act: (CalcSession.() -> Unit) -> Unit, modifier: Modifier) {
    Column(modifier) {
        KeyRow {
            for (f in listOf("sin", "cos", "tan", "ln", "log")) Key(f, Modifier.weight(1f), small = true, tag = "calc:$f") { act { function(f) } }
        }
        KeyRow {
            for (f in listOf("asin", "acos", "atan")) Key(f, Modifier.weight(1f), small = true, tag = "calc:$f") { act { function(f) } }
            Key("eˣ", Modifier.weight(1f), small = true, tag = "calc:exp") { act { function("exp") } }
            Key("10ˣ", Modifier.weight(1f), small = true, tag = "calc:tenpow") { act { tenPower() } }
        }
        KeyRow {
            Key("√", Modifier.weight(1f), small = true, tag = "calc:sqrt") { act { function("√") } }
            Key("∛", Modifier.weight(1f), small = true, tag = "calc:cbrt") { act { function("∛") } }
            Key("x²", Modifier.weight(1f), small = true, tag = "calc:square") { act { power("2") } }
            Key("xʸ", Modifier.weight(1f), small = true, tag = "calc:pow") { act { operator('^') } }
            Key("x!", Modifier.weight(1f), small = true, tag = "calc:fact") { act { postfix('!') } }
        }
        KeyRow {
            Key("π", Modifier.weight(1f), small = true, tag = "calc:pi") { act { constant("π") } }
            Key("e", Modifier.weight(1f), small = true, tag = "calc:e") { act { constant("e") } }
            Key("(", Modifier.weight(1f), small = true, tag = "calc:open") { act { openBracket() } }
            Key(")", Modifier.weight(1f), small = true, tag = "calc:close") { act { closeBracket() } }
            Key("1/x", Modifier.weight(1f), small = true, tag = "calc:recip") { act { reciprocal() } }
        }
        KeyRow {
            Key("Ans", Modifier.weight(1f), small = true, tag = "calc:ans") { act { constant("Ans") } }
            Key("|x|", Modifier.weight(1f), small = true, tag = "calc:abs") { act { function("abs") } }
            for (f in listOf("sinh", "cosh", "tanh")) Key(f, Modifier.weight(1f), small = true, tag = "calc:$f") { act { function(f) } }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.KeyRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.weight(1f).fillMaxWidth(), content = content)
}

@Composable
private fun Key(
    label: String,
    modifier: Modifier = Modifier,
    digit: Boolean = false,
    operator: Boolean = false,
    equals: Boolean = false,
    small: Boolean = false,
    tag: String,
    onClick: () -> Unit,
) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val classic = LocalStyle.current.isClassic
    // The keys of Windows Phone are flat, 2 dp apart, and turn to the accent colour while they are touched.
    var pressed by remember { mutableStateOf(false) }
    val lit = classic && pressed
    val fill = when {
        lit || equals -> accent
        digit -> colors.containers.getOrElse(1) { colors.surface }
        else -> colors.containers.getOrElse(0) { colors.surface }
    }
    Box(
        modifier.fillMaxHeight().padding(if (classic) 1.dp else 2.dp).testTag(tag).metroPress(onClick = onClick, onPressedChange = { pressed = it }).background(fill),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(
            label, size = if (small) 15.sp else 28.sp, weight = if (digit || equals) FontWeight.Normal else FontWeight.Light,
            color = if (lit || equals) onAccent() else if (operator) accent else colors.foreground, maxLines = 1,
        )
    }
}

@Composable
private fun HistoryPage(session: CalcSession.View, act: (CalcSession.() -> Unit) -> Unit, onPick: (CalcSession.Entry) -> Unit) {
    val colors = LocalMetroColors.current
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).padding(horizontal = 24.dp)) {
            if (session.history.isEmpty()) PageNote(stringResource(R.string.calc_history_empty), Modifier.testTag("calc:history:empty"))
            LazyColumn(Modifier.fillMaxSize().testTag("calc:history")) {
                items(session.history) { entry ->
                    Column(Modifier.fillMaxWidth().metroPress(onClick = { onPick(entry) }).padding(vertical = 8.dp), horizontalAlignment = Alignment.End) {
                        MetroText(shown(entry.expression) + " =", size = 16.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 2, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                        MetroText(shown(entry.result), size = 32.sp, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        AppBar(listOf(AppBarItem("✕", stringResource(R.string.calc_history_clear)) { act { clearHistory() } }))
    }
}

// --- the converter

@Composable
private fun ConverterPage() {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val kinds = remember { ConvertTable.Kind.entries }
    var kind by remember { mutableStateOf<ConvertTable.Kind?>(ConvertTable.Kind.Length) }
    var input by remember { mutableStateOf("1") }
    var fromIndex by remember { mutableIntStateOf(0) }
    var toIndex by remember { mutableIntStateOf(1) }
    var picking by remember { mutableIntStateOf(0) }
    val repository = remember { CurrencyRepository(context) }
    val rates by produceState<Currencies.Rates?>(repository.cached()) {
        val cached = repository.cached()
        if (cached == null || System.currentTimeMillis() - cached.fetchedAt > Currencies.MAX_AGE_MS) {
            withContext(Dispatchers.IO) { repository.fetch(System.currentTimeMillis()) }?.let { value = it }
        }
    }
    val symbols: List<String> = kind?.let { k -> ConvertTable.units.getValue(k).map { it.symbol } }
        ?: rates?.let { (it.perEuro.keys + "EUR").sorted() }.orEmpty()

    fun amount() = input.replace(',', '.').toDoubleOrNull() ?: 0.0

    fun convert(from: Int, to: Int): Double? {
        val a = symbols.getOrNull(from) ?: return null
        val b = symbols.getOrNull(to) ?: return null
        val k = kind
        return if (k != null) {
            val list = ConvertTable.units.getValue(k)
            ConvertTable.convert(amount(), list.first { it.symbol == a }, list.first { it.symbol == b })
        } else {
            rates?.let { Currencies.convert(Currencies.Query(amount(), a, b), it) }
        }
    }

    fun format(value: Double?): String =
        value?.let { CalcEngine.format(BigDecimal(it).round(MathContext(10)).toDouble(), locale) } ?: "—"

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                items(kinds) { k ->
                    PageChip(kindName(k), kind == k, "calc:kind:${k.name}") { kind = k; fromIndex = 0; toIndex = 1 }
                }
                item { PageChip(stringResource(R.string.calc_kind_currency), kind == null, "calc:kind:Currency") { kind = null; fromIndex = 0; toIndex = 1 } }
            }
            if (kind == null && rates == null) PageNote(stringResource(R.string.calc_rates_missing), Modifier.padding(bottom = 8.dp).testTag("calc:rates:missing"))
            MetroTextField(input, { input = it.filter { c -> c.isDigit() || c == '.' || c == ',' || c == '-' }.take(24) }, stringResource(R.string.calc_amount), Modifier.testTag("calc:amount"), keyboardType = KeyboardType.Decimal)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PageChip(symbols.getOrElse(fromIndex) { "—" }, true, "calc:from", Modifier.weight(1f)) { picking = 1 }
                PageChip("⇄", false, "calc:swap") { val t = fromIndex; fromIndex = toIndex; toIndex = t }
                PageChip(symbols.getOrElse(toIndex) { "—" }, true, "calc:to", Modifier.weight(1f)) { picking = 2 }
            }
            MetroText(
                format(convert(fromIndex, toIndex)) + " " + symbols.getOrElse(toIndex) { "" }, size = 40.sp, maxLines = 2,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp).testTag("calc:result"),
            )
            if (kind == null) rates?.let { PageNote(stringResource(R.string.calc_rates_date, it.date), Modifier.padding(bottom = 6.dp)) }
            LazyColumn(Modifier.weight(1f).testTag("calc:all")) {
                items(symbols.indices.toList()) { index ->
                    Row(Modifier.fillMaxWidth().metroPress(onClick = { toIndex = index }).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        MetroText(symbols[index], size = 18.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, modifier = Modifier.weight(0.4f))
                        MetroText(format(convert(fromIndex, index)), size = 22.sp, maxLines = 1, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    }
                }
            }
        }
        if (picking != 0) {
            val current = if (picking == 1) fromIndex else toIndex
            DialogFrame(onCancel = { picking = 0 }) {
                LazyColumn(Modifier.height(320.dp)) {
                    items(symbols.indices.toList()) { index ->
                        MetroText(
                            symbols[index], size = 22.sp, weight = if (index == current) FontWeight.SemiBold else FontWeight.Light,
                            color = if (index == current) LocalAccent.current else LocalMetroColors.current.foreground,
                            modifier = Modifier.fillMaxWidth().metroPress(onClick = { if (picking == 1) fromIndex = index else toIndex = index; picking = 0 }).padding(vertical = 9.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun kindName(kind: ConvertTable.Kind): String = stringResource(
    when (kind) {
        ConvertTable.Kind.Length -> R.string.calc_kind_length
        ConvertTable.Kind.Area -> R.string.calc_kind_area
        ConvertTable.Kind.Volume -> R.string.calc_kind_volume
        ConvertTable.Kind.Mass -> R.string.calc_kind_mass
        ConvertTable.Kind.Temperature -> R.string.calc_kind_temperature
        ConvertTable.Kind.Speed -> R.string.calc_kind_speed
        ConvertTable.Kind.Time -> R.string.calc_kind_time
        ConvertTable.Kind.Data -> R.string.calc_kind_data
        ConvertTable.Kind.Energy -> R.string.calc_kind_energy
        ConvertTable.Kind.Pressure -> R.string.calc_kind_pressure
        ConvertTable.Kind.Angle -> R.string.calc_kind_angle
    },
)
