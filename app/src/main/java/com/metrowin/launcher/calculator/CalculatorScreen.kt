package com.metrowin.launcher.calculator

import com.metrowin.launcher.ui.actioncenter.MetroToast
import android.content.ClipData
import android.content.ClipboardManager
import android.text.format.DateUtils
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key as KeyCode
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.metrowin.launcher.R
import com.metrowin.launcher.data.CalcEngine
import com.metrowin.launcher.data.CalcSession
import com.metrowin.launcher.data.CalcTools
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.ButtonRole
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroToggle
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.LocalStyle
import com.metrowin.launcher.ui.theme.onAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** What is read from the files before the first key: the session (history, memory, expression) and the options. */
@Stable
private class Loaded(val session: CalcSession, val options: CalcOptions)

@Composable
fun CalculatorScreen() {
    val context = LocalContext.current
    val store = remember { CalcStore(context) }
    // The files are read off the main thread: until then the page is empty, which lasts a frame or two.
    val loaded by produceState<Loaded?>(null) { value = withContext(Dispatchers.IO) { Loaded(store.load(), CalcOptions(context)) } }
    MiniFrame {
        loaded?.let { CalculatorBody(it.session, it.options, store) }
    }
}

private fun decimal(): Char = DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator

/** The expression or the number as the language writes it: the point becomes the decimal sign of the language, thousands are grouped if [group]. */
private fun shown(text: String, group: Boolean = false): String = (if (group) CalcTools.group(text) else text).replace('.', decimal())

@Composable
private fun ColumnScope.CalculatorBody(session: CalcSession, options: CalcOptions, store: CalcStore) {
    val context = LocalContext.current
    // The session is plain Kotlin: what the screen shows is a copy of it, replaced at every key.
    var view by remember { mutableStateOf(session.view()) }
    var changes by remember { mutableIntStateOf(0) }
    val act: (CalcSession.() -> Unit) -> Unit = {
        session.it()
        view = session.view()
        changes++
    }
    // Written a moment after the last key, and at once when the app goes to the background or closes: a key never waits for the disk.
    LaunchedEffect(changes) {
        if (changes > 0) {
            delay(SAVE_DELAY_MS)
            store.save(session)
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) store.save(session) }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            store.save(session)
        }
    }
    LaunchedEffect(view.expression.isNotEmpty()) { if (view.expression.isNotEmpty()) options.seeHint() }

    val pages = if (options[CalcOption.Tools]) 4 else 3
    val pager = rememberPagerState { pages }
    val scope = rememberCoroutineScope()
    var showOptions by remember { mutableStateOf(false) }
    val clipboard = options[CalcOption.Clipboard]
    val focus = remember { FocusRequester() }
    val titles = buildList {
        add(stringResource(R.string.calc_pivot_calc)); add(stringResource(R.string.calc_pivot_history)); add(stringResource(R.string.calc_pivot_convert))
        if (pages == 4) add(stringResource(R.string.calc_pivot_tools))
    }

    val copied = stringResource(R.string.calc_copied)
    val nothing = stringResource(R.string.calc_paste_none)
    val menu = buildList {
        add(AppBarItem("⚙", stringResource(R.string.calc_menu_options)) { showOptions = true })
        if (clipboard) {
            add(AppBarItem("⧉", stringResource(R.string.calc_menu_copy)) { copyText(context, currentValueText(view), copied) })
            add(AppBarItem("⎘", stringResource(R.string.calc_menu_paste)) {
                val text = readClipboard(context)
                if (CalcSession.pasteable(text) == null) MetroToast.show(context, nothing) else act { paste(text) }
            })
        }
    }

    Box(
        Modifier.weight(1f).fillMaxWidth().focusRequester(focus).focusable().onKeyEvent { event ->
            if (!options[CalcOption.Shortcuts] || event.type != KeyEventType.KeyDown || pager.currentPage != 0) return@onKeyEvent false
            when {
                event.key == KeyCode.Enter || event.key == KeyCode.NumPadEnter -> { act { equals() }; true }
                event.key == KeyCode.Backspace || event.key == KeyCode.Delete -> { act { backspace() }; true }
                event.key == KeyCode.Escape -> { act { clear() }; true }
                clipboard && event.isCtrlPressed && event.key == KeyCode.C -> { copyText(context, currentValueText(view), copied); true }
                clipboard && event.isCtrlPressed && event.key == KeyCode.V -> { act { paste(readClipboard(context)) }; true }
                event.isCtrlPressed -> false
                else -> { var handled = false; act { handled = type(event.utf16CodePoint.toChar()) }; handled }
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            PivotHeader(titles, pager, Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp))
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { page ->
                when (page) {
                    0 -> CalcPage(view, options, act, menu)
                    1 -> HistoryPage(view, options, act, menu) { act { recall(it) }; scope.launch { pager.animateScrollToPage(0) } }
                    2 -> ConverterPage(options, menu)
                    else -> ToolsPage(menu) { text -> act { insert(text) }; scope.launch { pager.animateScrollToPage(0) } }
                }
            }
        }
        if (showOptions) OptionsDialog(options) { showOptions = false }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

private const val SAVE_DELAY_MS = 400L

/** What "copy" copies: the result when "=" was pressed, else what the preview says, else the expression. */
private fun currentValueText(view: CalcSession.View): String =
    if (view.finished) view.expression.replace('−', '-') else view.preview?.let { CalcEngine.format(it) } ?: view.expression

private fun copyText(context: android.content.Context, text: String, message: String) {
    if (text.isEmpty()) return
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("calculator", text))
    MetroToast.show(context, message)
}

private fun readClipboard(context: android.content.Context): String =
    context.getSystemService(ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()

@Composable
private fun OptionsDialog(options: CalcOptions, onClose: () -> Unit) {
    DialogFrame(onCancel = onClose, title = stringResource(R.string.calc_options_title)) {
        Column(Modifier.testTag("calc:options")) {
            for (option in CalcOption.entries) {
                if (option == CalcOption.LeftHand && !options[CalcOption.OneHand]) continue
                MetroToggle(stringResource(option.title), options[option], { options[option] = it }, Modifier.testTag("calc:option:${option.name}"))
                PageNote(stringResource(option.hint), Modifier.padding(bottom = 8.dp))
            }
            MetroButton(stringResource(R.string.calc_close), Modifier.padding(top = 6.dp), onClick = onClose)
        }
    }
}

@Composable
private fun CalcPage(view: CalcSession.View, options: CalcOptions, act: (CalcSession.() -> Unit) -> Unit, menu: List<AppBarItem>) {
    val colors = LocalMetroColors.current
    val keysLook = options[CalcOption.KeysLook]
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        val scientific = options.scientific || wide
        // A short screen gives its height to the keys, a tall one to the display.
        val displayWeight = if (wide) 0.55f else (maxHeight.value / 640f * 0.9f).coerceIn(0.65f, 1.2f)
        val panel by animateFloatAsState(if (scientific) 1.25f else 0f, tween(if (LocalReduceMotion.current) 0 else 220), label = "scientific")
        val oneHand = options[CalcOption.OneHand] && !wide
        val left = options[CalcOption.LeftHand]
        Column(Modifier.fillMaxSize()) {
            Display(view, options, Modifier.padding(horizontal = 20.dp).weight(displayWeight))
            MemoryRow(view, keysLook, act)
            Row(Modifier.weight(if (wide) 1.6f else 2.8f).padding(horizontal = 6.dp)) {
                if (oneHand && !left && !scientific) Spacer(Modifier.weight(0.5f))
                if (panel > 0.02f) ScientificPanel(act, keysLook, if (wide) 19.sp else 15.sp, Modifier.weight(panel).graphicsLayer { alpha = (panel / 1.25f).coerceIn(0f, 1f) })
                Keypad(act, keysLook, Modifier.weight(1f))
                if (oneHand && left && !scientific) Spacer(Modifier.weight(0.5f))
            }
            AppBar(
                buildList {
                    if (!wide) add(AppBarItem("ƒ", stringResource(if (options.scientific) R.string.calc_standard else R.string.calc_scientific)) { options.keepScientific(!options.scientific) })
                    add(AppBarItem(if (view.angle == CalcEngine.Angle.Degrees) "°" else "㎭", stringResource(if (view.angle == CalcEngine.Angle.Degrees) R.string.calc_degrees else R.string.calc_radians)) { act { toggleAngle() } })
                },
                Modifier.background(colors.background),
                menu = menu,
            )
        }
    }
}

@Composable
private fun Display(session: CalcSession.View, options: CalcOptions, modifier: Modifier) {
    val colors = LocalMetroColors.current
    val look = options[CalcOption.Look]
    val failure = session.failure
    val preview = if (!session.finished && failure == null && !isPlainNumber(session.expression)) session.preview else null
    val reduce = LocalReduceMotion.current
    // The result comes in: it fades up when "=" is pressed.
    val arrival = remember { Animatable(1f) }
    LaunchedEffect(session.finished, session.lastExpression) {
        if (look && !reduce && session.finished) {
            arrival.snapTo(0f)
            arrival.animateTo(1f, tween(240))
        }
    }
    Column(modifier.fillMaxWidth().testTag("calc:display"), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Bottom) {
        val top = when {
            failure != null -> stringResource(
                when (failure) {
                    CalcEngine.Error.Syntax -> R.string.calc_error_syntax
                    CalcEngine.Error.Domain -> R.string.calc_error_domain
                    CalcEngine.Error.Overflow -> R.string.calc_error_overflow
                },
            )
            session.finished -> shown(session.lastExpression, look) + " ="
            session.memory != 0.0 -> "M  " + shown(CalcEngine.format(session.memory), look)
            session.expression.isEmpty() && !options.hintSeen -> stringResource(R.string.calc_hint)
            else -> ""
        }
        // The tape: the last two lines of the history above the display, like the roll of a printing calculator.
        if (options[CalcOption.Tape]) session.history.take(2).reversed().forEach {
            MetroText(shown(it.expression, look) + " = " + shown(it.result, look), size = 14.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().testTag("calc:tape"))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (look) MetroText(if (session.angle == CalcEngine.Angle.Degrees) "DEG" else "RAD", size = 14.sp, weight = FontWeight.SemiBold, color = LocalAccent.current, modifier = Modifier.padding(end = 10.dp).testTag("calc:angle"))
            MetroText(
                top, size = 18.sp, weight = FontWeight.Normal, color = if (failure != null) LocalAccent.current else colors.subtle,
                maxLines = if (session.expression.isEmpty() && !options.hintSeen) 2 else 1, textAlign = TextAlign.End, modifier = Modifier.weight(1f).testTag("calc:top"),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            MetroText(
                shown(session.expression, look).ifEmpty { "0" }, size = 54.sp, maxLines = 2, lineHeight = 58.sp, textAlign = TextAlign.End,
                modifier = Modifier.weight(1f, fill = false).alpha(if (session.finished) 0.25f + 0.75f * arrival.value else 1f).testTag("calc:expression"),
            )
            if (look && !session.finished && failure == null) Cursor()
        }
        MetroText(
            if (preview != null) "= " + shown(CalcEngine.format(preview), look) else " ", size = 24.sp, weight = FontWeight.Normal,
            color = colors.subtle, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().testTag("calc:preview"),
        )
    }
}

/** The blinking bar after the expression, in the accent colour. It stays still with reduced motion. */
@Composable
private fun Cursor() {
    var on by remember { mutableStateOf(true) }
    val still = LocalReduceMotion.current
    LaunchedEffect(still) {
        while (!still) {
            delay(CURSOR_MS)
            on = !on
        }
        on = true
    }
    Box(Modifier.padding(start = 3.dp).width(2.dp).height(46.dp).alpha(if (on) 1f else 0f).background(LocalAccent.current).testTag("calc:cursor"))
}

private const val CURSOR_MS = 530L

/** A bare number (or a minus sign and a number): the preview would only repeat it. */
internal fun isPlainNumber(text: String) = text.isEmpty() || text.removePrefix("−").all { it.isDigit() || it == '.' }

@Composable
private fun MemoryRow(view: CalcSession.View, keysLook: Boolean, act: (CalcSession.() -> Unit) -> Unit) {
    // 48 dp, the least a finger can hit; the keys show what the memory holds to whoever asks TalkBack.
    val holds = if (view.memory != 0.0) CalcEngine.format(view.memory) else ""
    Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 6.dp)) {
        Key("MC", Modifier.weight(1f), small = true, keysLook = keysLook, tag = "calc:mc", description = stringResource(R.string.calc_key_mc)) { act { memoryClear() } }
        Key("MR", Modifier.weight(1f), small = true, keysLook = keysLook, tag = "calc:mr", description = stringResource(R.string.calc_key_mr, holds)) { act { memoryRecall() } }
        Key("M+", Modifier.weight(1f), small = true, keysLook = keysLook, tag = "calc:mplus", description = stringResource(R.string.calc_key_mplus)) { act { memoryAdd(1) } }
        Key("M−", Modifier.weight(1f), small = true, keysLook = keysLook, tag = "calc:mminus", description = stringResource(R.string.calc_key_mminus)) { act { memoryAdd(-1) } }
    }
}

@Composable
private fun Keypad(act: (CalcSession.() -> Unit) -> Unit, keysLook: Boolean, modifier: Modifier) {
    val dot = decimal().toString()
    Column(modifier) {
        KeyRow {
            Key("C", Modifier.weight(1f), keysLook = keysLook, tag = "calc:clear") { act { clear() } }
            Key("⌫", Modifier.weight(1f), keysLook = keysLook, tag = "calc:back", description = stringResource(R.string.calc_key_back)) { act { backspace() } }
            Key("%", Modifier.weight(1f), keysLook = keysLook, tag = "calc:percent") { act { postfix('%') } }
            Key("÷", Modifier.weight(1f), operator = true, keysLook = keysLook, tag = "calc:div", description = stringResource(R.string.calc_key_divide)) { act { operator('÷') } }
        }
        for (row in listOf("789×", "456−", "123+")) {
            KeyRow {
                row.forEach { c ->
                    if (c.isDigit()) Key(c.toString(), Modifier.weight(1f), digit = true, keysLook = keysLook, tag = "calc:$c") { act { digit(c) } }
                    else Key(
                        c.toString(), Modifier.weight(1f), operator = true, keysLook = keysLook, tag = "calc:$c",
                        description = when (c) {
                            '×' -> stringResource(R.string.calc_key_multiply)
                            '−' -> stringResource(R.string.calc_key_minus)
                            else -> null
                        },
                    ) { act { operator(c) } }
                }
            }
        }
        KeyRow {
            Key("±", Modifier.weight(1f), keysLook = keysLook, tag = "calc:sign", description = stringResource(R.string.calc_key_sign)) { act { negate() } }
            Key("0", Modifier.weight(1f), digit = true, keysLook = keysLook, tag = "calc:0") { act { digit('0') } }
            Key(dot, Modifier.weight(1f), digit = true, keysLook = keysLook, tag = "calc:point") { act { point() } }
            Key("=", Modifier.weight(1f), equals = true, keysLook = keysLook, tag = "calc:equals") { act { equals() } }
        }
    }
}

@Composable
private fun ScientificPanel(act: (CalcSession.() -> Unit) -> Unit, keysLook: Boolean, size: TextUnit, modifier: Modifier) {
    @Composable
    fun RowScope.K(label: String, tag: String, onClick: () -> Unit) = Key(label, Modifier.weight(1f), small = true, keysLook = keysLook, tag = "calc:$tag", labelSize = size, onClick = onClick)
    Column(modifier) {
        KeyRow {
            for (f in listOf("sin", "cos", "tan", "ln", "log")) K(f, f) { act { function(f) } }
        }
        KeyRow {
            for (f in listOf("asin", "acos", "atan")) K(f, f) { act { function(f) } }
            K("eˣ", "exp") { act { function("exp") } }
            K("10ˣ", "tenpow") { act { tenPower() } }
        }
        KeyRow {
            K("√", "sqrt") { act { function("√") } }
            K("∛", "cbrt") { act { function("∛") } }
            K("x²", "square") { act { power("2") } }
            K("xʸ", "pow") { act { operator('^') } }
            K("x!", "fact") { act { postfix('!') } }
        }
        KeyRow {
            K("π", "pi") { act { constant("π") } }
            K("e", "e") { act { constant("e") } }
            K("(", "open") { act { openBracket() } }
            K(")", "close") { act { closeBracket() } }
            K("1/x", "recip") { act { reciprocal() } }
        }
        KeyRow {
            K("Ans", "ans") { act { constant("Ans") } }
            K("|x|", "abs") { act { function("abs") } }
            K("mod", "mod") { act { mod() } }
        }
        KeyRow {
            for (f in listOf("sinh", "cosh", "tanh")) K(f, f) { act { function(f) } }
        }
    }
}

@Composable
private fun ColumnScope.KeyRow(content: @Composable RowScope.() -> Unit) {
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
    keysLook: Boolean = true,
    labelSize: TextUnit = 15.sp,
    /** What TalkBack says when the glyph on the key is a sign it would read badly (⌫ ÷ × − ±). */
    description: String? = null,
    tag: String,
    onClick: () -> Unit,
) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val style = LocalStyle.current
    // The keys of Windows Phone are flat, a hair apart, and turn to the accent colour while they are touched; with "key style" every look does that.
    var pressed by remember { mutableStateOf(false) }
    val lit = (style.isClassic || keysLook) && pressed
    val fill = when {
        lit || equals -> accent
        digit -> colors.containers.getOrElse(1) { colors.surface }
        else -> colors.containers.getOrElse(0) { colors.surface }
    }
    val shape = if (keysLook && !style.isClassic) style.squareShape else RoundedCornerShape(0.dp)
    val gap = if (keysLook) 1.5.dp else if (style.isClassic) 1.dp else 2.dp
    Box(
        modifier.fillMaxHeight().padding(gap).testTag(tag).metroPress(onClick = onClick, onPressedChange = { pressed = it }).clip(shape).background(fill)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(
            label, size = if (small) labelSize else 28.sp, weight = if (digit || equals) FontWeight.Normal else FontWeight.Light,
            color = if (lit || equals) onAccent() else if (operator) accent else colors.foreground, maxLines = 1,
        )
    }
}

// --- the history

private fun dayOf(at: Long): LocalDate = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
private fun HistoryPage(view: CalcSession.View, options: CalcOptions, act: (CalcSession.() -> Unit) -> Unit, menu: List<AppBarItem>, onPick: (CalcSession.Entry) -> Unit) {
    val colors = LocalMetroColors.current
    val context = LocalContext.current
    val look = options[CalcOption.Look]
    val shortcuts = options[CalcOption.Shortcuts]
    val clipboard = options[CalcOption.Clipboard]
    var confirmClear by remember { mutableStateOf(false) }
    var lineMenu by remember { mutableStateOf<CalcSession.Entry?>(null) }
    val copied = stringResource(R.string.calc_copied)
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).padding(horizontal = 24.dp)) {
            if (view.history.isEmpty()) PageNote(stringResource(R.string.calc_history_empty), Modifier.testTag("calc:history:empty"))
            LazyColumn(Modifier.fillMaxSize().testTag("calc:history")) {
                itemsIndexed(view.history) { index, entry ->
                    // A line of a new day gets the day above it (lines from before the days were kept have none).
                    val day = if (look && entry.at > 0) dayOf(entry.at) else null
                    val before = view.history.getOrNull(index - 1)?.takeIf { it.at > 0 }?.let { dayOf(it.at) }
                    if (look && index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.subtle.copy(alpha = 0.25f)))
                    if (day != null && day != before) {
                        MetroText(dayName(context, entry.at), size = 14.sp, weight = FontWeight.SemiBold, color = LocalAccent.current, modifier = Modifier.padding(top = 10.dp))
                    }
                    Column(
                        Modifier.fillMaxWidth().metroPress(onClick = { onPick(entry) }, onLongClick = if (shortcuts) ({ lineMenu = entry }) else null).padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        MetroText(shown(entry.expression, look) + " =", size = 16.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 2, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                        MetroText(shown(entry.result, look), size = 32.sp, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        AppBar(listOf(AppBarItem("✕", stringResource(R.string.calc_history_clear)) { if (view.history.isNotEmpty()) confirmClear = true }), menu = menu)
    }
    if (confirmClear) {
        DialogFrame(onCancel = { confirmClear = false }, title = stringResource(R.string.calc_clear_confirm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetroButton(stringResource(R.string.calc_clear_yes), Modifier.weight(1f).testTag("calc:history:clear:yes"), role = ButtonRole.Danger) { act { clearHistory() }; confirmClear = false }
                MetroButton(stringResource(R.string.calc_cancel), Modifier.weight(1f)) { confirmClear = false }
            }
        }
    }
    lineMenu?.let { entry ->
        DialogFrame(onCancel = { lineMenu = null }, title = shown(entry.expression) + " = " + shown(entry.result)) {
            Column(Modifier.testTag("calc:history:menu")) {
                MenuRow(stringResource(R.string.calc_history_delete), "calc:history:delete") { act { removeFromHistory(entry) }; lineMenu = null }
                if (clipboard) {
                    MenuRow(stringResource(R.string.calc_history_copy_expression), "calc:history:copy:expression") { copyText(context, entry.expression, copied); lineMenu = null }
                    MenuRow(stringResource(R.string.calc_history_copy_result), "calc:history:copy:result") { copyText(context, entry.result.replace('−', '-'), copied); lineMenu = null }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(text: String, tag: String, onClick: () -> Unit) {
    MetroText(text, size = 22.sp, modifier = Modifier.fillMaxWidth().testTag(tag).metroPress(onClick = onClick).padding(vertical = 10.dp))
}

@Composable
private fun dayName(context: android.content.Context, at: Long): String = when {
    DateUtils.isToday(at) -> stringResource(R.string.calc_today)
    DateUtils.isToday(at + DateUtils.DAY_IN_MILLIS) -> stringResource(R.string.calc_yesterday)
    else -> DateUtils.formatDateTime(context, at, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH)
}
