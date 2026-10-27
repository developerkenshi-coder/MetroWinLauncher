package com.metrowin.launcher.ui.start

import com.metrowin.launcher.ui.components.hideKeyboardWhenGone
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.metrowin.launcher.data.Checklist
import com.metrowin.launcher.data.City
import com.metrowin.launcher.data.WorldCities
import com.metrowin.launcher.data.Countdown
import com.metrowin.launcher.data.SystemSettingDef
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroDatePicker
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.MetroAccents
import com.metrowin.launcher.ui.theme.fieldTextStyle
import com.metrowin.launcher.ui.theme.panel
import com.metrowin.launcher.ui.theme.sheet
import com.metrowin.launcher.ui.theme.toArgbInt
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** A dimmed page with [content] in a panel in the middle; touching outside it calls [onCancel]. */
@Composable
internal fun DialogFrame(onCancel: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = LocalMetroColors.current
    val currentCancel by rememberUpdatedState(onCancel)
    Box(
        modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.85f))
            .pointerInput(Unit) { detectTapGestures { currentCancel() } }
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .panel()
                .pointerInput(Unit) { detectTapGestures { } }
                // On a short screen or with big text the panel scrolls: its last button can always be reached.
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) { content() }
    }
}

/** The text of a note tile, written on several lines. */
@Composable
fun NoteDialog(initial: String, onDone: (String) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalMetroColors.current
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    DialogFrame(onCancel, modifier) {
        MetroText(stringResource(R.string.note_title), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle)
        BasicTextField(
            value = text,
            onValueChange = { text = it.take(MAX_NOTE) },
            textStyle = fieldTextStyle(colors.foreground, 22.sp),
            cursorBrush = SolidColor(LocalAccent.current),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            minLines = 3,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).focusRequester(focus).testTag("note:field").hideKeyboardWhenGone(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetroButton(stringResource(android.R.string.ok), Modifier.weight(1f).testTag("note:ok")) { onDone(text) }
            MetroButton(stringResource(android.R.string.cancel), Modifier.weight(1f).testTag("note:cancel"), onClick = onCancel)
        }
    }
}

/** The name and the date of a countdown tile: the date is chosen with a pair of buttons for the day, the month and the year. */
@Composable
fun CountdownDialog(
    initialTitle: String,
    initialDay: Long,
    onDone: (title: String, epochDay: Long) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var date by remember { mutableStateOf(Countdown.date(initialDay)) }
    val locale = Locale.getDefault()
    DialogFrame(onCancel, modifier) {
        MetroText(stringResource(R.string.countdown_title), size = 15.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
        MetroTextField(
            value = title, onValueChange = { title = it.take(MAX_TITLE) },
            placeholder = stringResource(R.string.countdown_name_hint), modifier = Modifier.testTag("countdown:title"),
        )
        MetroText(
            date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)), size = 22.sp,
            modifier = Modifier.testTag("countdown:date"),
        )
        // The date is chosen on three wheels, as on Windows Phone.
        MetroDatePicker(date, { date = it }, tag = "countdown")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetroButton(stringResource(android.R.string.ok), Modifier.weight(1f).testTag("countdown:ok")) { onDone(title, date.toEpochDay()) }
            MetroButton(stringResource(android.R.string.cancel), Modifier.weight(1f).testTag("countdown:cancel"), onClick = onCancel)
        }
    }
}

/** The name of a counter, with a button that puts its count back to zero. */
@Composable
fun CounterDialog(
    initialName: String,
    count: Int,
    onDone: (name: String, reset: Boolean) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf(initialName) }
    var reset by remember { mutableStateOf(false) }
    DialogFrame(onCancel, modifier) {
        MetroText(stringResource(R.string.counter_title), size = 15.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
        MetroTextField(
            value = name, onValueChange = { name = it.take(MAX_TITLE) },
            placeholder = stringResource(R.string.counter_name_hint), modifier = Modifier.testTag("counter:name"),
        )
        MetroText(if (reset) "0" else count.toString(), size = 40.sp, modifier = Modifier.testTag("counter:value"))
        MetroButton(stringResource(R.string.counter_reset), Modifier.fillMaxWidth().testTag("counter:reset")) { reset = true }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetroButton(stringResource(android.R.string.ok), Modifier.weight(1f).testTag("counter:ok")) { onDone(name, reset) }
            MetroButton(stringResource(android.R.string.cancel), Modifier.weight(1f).testTag("counter:cancel"), onClick = onCancel)
        }
    }
}

/** A list of things to do: touch one to tick it, the cross takes it away, the box at the bottom adds one. Saved at every change. */
@Composable
fun ChecklistDialog(initialRaw: String, onChange: (String) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalMetroColors.current
    var items by remember { mutableStateOf(Checklist.parse(initialRaw)) }
    var draft by remember { mutableStateOf("") }
    fun change(next: List<Checklist.Item>) {
        if (next == items) return
        items = next
        onChange(Checklist.serialize(next))
    }
    fun addDraft() {
        change(Checklist.add(items, draft))
        draft = ""
    }
    DialogFrame(onClose, modifier) {
        MetroText(stringResource(R.string.checklist_title), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle)
        Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()).testTag("checklist:items")) {
            if (items.isEmpty()) MetroText(stringResource(R.string.checklist_empty), size = 17.sp, weight = FontWeight.Normal, color = colors.subtle)
            items.forEachIndexed { index, item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).testTag("checklist:item:$index").metroPress(onClick = { change(Checklist.toggle(items, index)) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.metrowin.launcher.ui.kids.CheckSquare(item.done)
                        Spacer(Modifier.width(12.dp))
                        MetroText(item.text, size = 19.sp, color = if (item.done) colors.subtle else colors.foreground, maxLines = 2)
                    }
                    val description = stringResource(R.string.checklist_remove)
                    Box(
                        Modifier.testTag("checklist:remove:$index").semantics { contentDescription = description }.metroPress(onClick = { change(Checklist.remove(items, index)) }).padding(10.dp),
                    ) { MetroText("×", size = 24.sp, weight = FontWeight.Normal, color = colors.subtle) }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                MetroTextField(
                    value = draft, onValueChange = { draft = it.take(Checklist.MAX_TEXT) },
                    placeholder = stringResource(R.string.checklist_add_hint), modifier = Modifier.testTag("checklist:draft"), onSearch = ::addDraft,
                )
            }
            Box(Modifier.testTag("checklist:add").metroPress(onClick = ::addDraft).padding(start = 12.dp, top = 12.dp, bottom = 12.dp)) {
                MetroText(stringResource(R.string.checklist_add), size = 18.sp, weight = FontWeight.Normal, color = LocalAccent.current)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (items.any { it.done }) {
                MetroButton(stringResource(R.string.checklist_clear_done), Modifier.weight(1f).testTag("checklist:cleardone")) { change(Checklist.clearDone(items)) }
            }
            MetroButton(stringResource(R.string.edit_done), Modifier.weight(1f).testTag("checklist:done"), onClick = onClose)
        }
    }
}

/** The cities of the world clock, with a box to search them. */
@Composable
fun CityPicker(onPick: (City) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalMetroColors.current
    val currentDismiss by rememberUpdatedState(onDismiss)
    var query by remember { mutableStateOf("") }
    val cities = remember(query) { WorldCities.search(query) }
    Box(
        modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.78f))
            .pointerInput(Unit) { detectTapGestures { currentDismiss() } }
            .imePadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .sheet()
                .pointerInput(Unit) { detectTapGestures { } }
                .navigationBarsPadding()
                .padding(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 76.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetroText(stringResource(R.string.worldclock_title), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle)
            MetroTextField(value = query, onValueChange = { query = it }, placeholder = stringResource(R.string.worldclock_search), modifier = Modifier.testTag("city:query"))
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                cities.forEach { city ->
                    MetroText(
                        city.name, size = 21.sp,
                        modifier = Modifier.fillMaxWidth().testTag("city:${city.name}").metroPress(onClick = { onPick(city) }).padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/** The screens of the Android settings that can be put on Start, one under the other. */
@Composable
fun SettingPicker(
    settings: List<Pair<SystemSettingDef, String>>,
    onPick: (SystemSettingDef) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMetroColors.current
    val currentDismiss by rememberUpdatedState(onDismiss)
    Box(
        modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.78f))
            .pointerInput(Unit) { detectTapGestures { currentDismiss() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .sheet()
                .pointerInput(Unit) { detectTapGestures { } }
                .navigationBarsPadding()
                .padding(top = 12.dp),
        ) {
            MetroText(
                stringResource(R.string.add_setting_title), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(bottom = 76.dp)) {
                settings.forEach { (def, label) ->
                    Row(
                        Modifier.fillMaxWidth().testTag("setting:${def.id}").metroPress(onClick = { onPick(def) }).padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlyphIcon(def.glyph, 28.dp)
                        Spacer(Modifier.width(16.dp))
                        MetroText(label, size = 21.sp)
                    }
                }
            }
        }
    }
}

/** The 20 colors of Windows Phone and a way back to the color of the theme, for the selected tile. */
@Composable
fun TileColorSheet(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalMetroColors.current
    val currentDismiss by rememberUpdatedState(onDismiss)
    Box(
        modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.78f))
            .pointerInput(Unit) { detectTapGestures { currentDismiss() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .sheet()
                .pointerInput(Unit) { detectTapGestures { } }
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 76.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetroText(stringResource(R.string.tile_color_title), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle)
            MetroAccents.chunked(5).forEachIndexed { row, group ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    group.forEachIndexed { column, color ->
                        val index = row * 5 + column
                        val argb = color.toArgbInt()
                        Box(
                            Modifier
                                .weight(1f)
                                .size(52.dp)
                                .testTag("color:$index")
                                .metroPress(onClick = { onPick(argb) })
                                .background(color),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (current == argb) MetroText("✓", size = 26.sp, weight = FontWeight.Normal, color = Color.White)
                        }
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().testTag("color:default").metroPress(onClick = { onPick(0) }).padding(vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                MetroText(
                    stringResource(R.string.tile_color_default), size = 20.sp,
                    color = if (current == 0) LocalAccent.current else colors.foreground,
                )
            }
        }
    }
}

/** A line at the bottom that says what was just done to Start and offers to undo it. */
@Composable
fun UndoBar(message: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .panel()
            .padding(start = 18.dp, end = 6.dp)
            .testTag("undo:bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetroText(message, size = 18.sp, weight = FontWeight.Normal, modifier = Modifier.weight(1f).padding(vertical = 14.dp))
        Box(Modifier.testTag("undo:button").metroPress(onClick = onUndo).padding(horizontal = 14.dp, vertical = 14.dp)) {
            MetroText(stringResource(R.string.undo), size = 18.sp, weight = FontWeight.SemiBold, color = LocalAccent.current)
        }
    }
}

private const val MAX_NOTE = 400
private const val MAX_TITLE = 30
