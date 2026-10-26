package com.metrowin.launcher.ui.apps

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.metrowin.launcher.data.QuickAnswer
import com.metrowin.launcher.data.TodayAction
import com.metrowin.launcher.data.TodayInfo
import com.metrowin.launcher.data.QuickAnswerActions
import com.metrowin.launcher.data.SearchEngine
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import com.metrowin.launcher.R
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.fieldTextStyle
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import com.metrowin.launcher.model.AppCategory
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import java.util.Locale
import com.metrowin.launcher.data.EventMatch
import com.metrowin.launcher.data.ClockFormat
import com.metrowin.launcher.data.CalendarSearch
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.data.AppSection
import com.metrowin.launcher.data.ContactMatch
import com.metrowin.launcher.data.LauncherSettingMatch
import com.metrowin.launcher.data.TileMatch
import com.metrowin.launcher.data.GlobalResults
import com.metrowin.launcher.data.GlobalSearch
import com.metrowin.launcher.data.SystemSetting
import com.metrowin.launcher.model.Launch
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.ui.anim.Motion
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.anim.turnstile
import com.metrowin.launcher.ui.components.AppSquare
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalStyle
import com.metrowin.launcher.ui.theme.litFill
import com.metrowin.launcher.ui.theme.onAccent
import com.metrowin.launcher.ui.theme.panel
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter

sealed interface ListRow {
    val key: String

    /** The search box at the top of the list. */
    data object Search : ListRow {
        override val key = "search"
    }

    /** The card with the day, the next event, the weather and the reminders, above the search box. */
    data object Today : ListRow {
        override val key = "today"
    }

    data class Header(val letter: String) : ListRow {
        override val key = "header:$letter"
    }

    /** An app. [group] is empty in the alphabetical list and names the group ("f" most used, "n" new) at the top. */
    data class App(val item: AppItem, val group: String = "") : ListRow {
        override val key = if (group.isEmpty()) "app:${item.id}" else "app:$group:${item.id}"
    }

    /** What the search box answers by itself: a sum, a conversion, a number to call, a link, a timer. */
    data class Answer(val answer: QuickAnswer) : ListRow {
        override val key = "answer:${answer::class.simpleName}:${answer.hashCode()}"
    }

    /** A search made before, offered while the search box is empty and touched. */
    data class Recent(val query: String) : ListRow {
        override val key = "recent:$query"
    }

    /** The chips under the search box that filter the list by category. */
    data object Categories : ListRow {
        override val key = "categories"
    }

    /** The heading of a group of results that are not apps. */
    data class Section(@param:StringRes val title: Int) : ListRow {
        override val key = "section:$title"
    }

    data class Contact(val match: ContactMatch) : ListRow {
        override val key = "contact:${match.lookupUri}"
    }

    data class Setting(val setting: SystemSetting) : ListRow {
        override val key = "setting:${setting.id}"
    }

    data class Web(val query: String) : ListRow {
        override val key = "web"
    }

    data class Event(val match: EventMatch) : ListRow {
        override val key = "event:${match.eventId}:${match.startMillis}"
    }

    /** A setting of the launcher itself (or a whole section of them): touching it opens the settings where it is. */
    data class LauncherSetting(val match: LauncherSettingMatch) : ListRow {
        override val key = "lsetting:${match.id ?: "section:${match.section}"}"
    }

    /** A note, a list, a countdown or a site pinned to Start. */
    data class Tile(val match: TileMatch) : ListRow {
        override val key = "tile:${match.item.id}"
    }
}

/**
 * The alphabetical app list flattened into rows: a letter header, then the apps under it. Above the letters: the chips of
 * the [categories], the apps opened most often ([frequent]) and the ones installed lately ([fresh]), when there are any.
 */
@Immutable
class AppListModel(
    sections: List<AppSection>,
    frequent: List<AppItem> = emptyList(),
    fresh: List<AppItem> = emptyList(),
    categories: List<AppCategory> = emptyList(),
    /** The Today card goes above the search box. */
    today: Boolean = false,
) {
    /** How many rows come before the first one that belongs to the apps: the search box, and the Today card when there is one. */
    val head: Int = if (today) 2 else 1

    val rows: List<ListRow> = buildList {
        if (today) add(ListRow.Today)
        add(ListRow.Search)
        if (categories.isNotEmpty()) add(ListRow.Categories)
        if (frequent.isNotEmpty()) {
            add(ListRow.Section(R.string.list_frequent))
            frequent.forEach { add(ListRow.App(it, GROUP_FREQUENT)) }
        }
        if (fresh.isNotEmpty()) {
            add(ListRow.Section(R.string.list_new))
            fresh.forEach { add(ListRow.App(it, GROUP_NEW)) }
        }
        sections.forEach { section ->
            add(ListRow.Header(section.key))
            section.items.forEach { add(ListRow.App(it)) }
        }
    }
    val headerIndex: Map<String, Int> =
        rows.withIndex().filter { it.value is ListRow.Header }.associate { (it.value as ListRow.Header).letter to it.index }
    val letters: Set<String> get() = headerIndex.keys

    /** Position of the row of app [id], or null when the list does not show it. */
    fun indexOfApp(id: String): Int? = rows.indexOfFirst { it is ListRow.App && it.group.isEmpty() && it.item.id == id }.takeIf { it >= 0 }

    companion object {
        const val GROUP_FREQUENT = "f"
        const val GROUP_NEW = "n"
    }
}

/** An app row that glows for a moment: where the star of the jump list took you. [token] makes a repeat glow again. */
@Immutable
data class ListHighlight(val appId: String, val token: Int)

/** Whether the list is the compact one: smaller squares and tighter rows. */
val LocalCompactList = staticCompositionLocalOf { false }

private val RowHeight: Dp @Composable get() = if (LocalCompactList.current) 48.dp else 64.dp
private val SquareSize: Dp @Composable get() = if (LocalCompactList.current) 38.dp else 52.dp

/**
 * The Windows Phone app list: every app in alphabetical order under its letter. Tapping a letter
 * opens the jump list. Rows swing in like a turnstile when the page opens and swing out when an app
 * is launched.
 *
 * [replayToken] restarts the entrance, used after the jump list moves the list somewhere else.
 */
@Composable
fun AppListScreen(
    model: AppListModel,
    listState: LazyListState,
    replayToken: Int,
    exiting: Boolean,
    onLaunch: (AppItem) -> Unit,
    onLongPress: (item: AppItem, top: Float, height: Float) -> Unit,
    onHeaderClick: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<AppItem>,
    modifier: Modifier = Modifier,
    highlight: ListHighlight? = null,
    /** What the search finds besides apps: contacts, screens of the Android settings, a web search. */
    global: GlobalResults = GlobalResults.Empty,
    /** Bumped to put the cursor in the search box (and show the keyboard), as the "search the apps" gesture asks. */
    searchFocus: Int = 0,
    /** Shown as a microphone in the search box while it is empty; null for no microphone. */
    onVoice: (() -> Unit)? = null,
    /** The categories of the chips under the search box (the model decides whether they show), the chosen one, and a choice. */
    categories: List<AppCategory> = emptyList(),
    category: AppCategory? = null,
    onCategory: (AppCategory?) -> Unit = {},
    /** What the search box answers by itself for the typed text, and what touching an answer does. */
    answers: List<QuickAnswer> = emptyList(),
    onAnswer: (QuickAnswer) -> Unit = {},
    /** What touching a setting of the launcher found by the search does: the caller opens the settings there. */
    onOpenSetting: (LauncherSettingMatch) -> Unit = {},
    /** The searches made before, offered while the box is touched and empty; the way to forget them. */
    recentSearches: List<String> = emptyList(),
    onClearRecents: () -> Unit = {},
    /** The web search engine the row that offers a web search uses. */
    engine: SearchEngine = SearchEngine.DuckDuckGo,
    /** The search was confirmed from the keyboard. */
    onSearchSubmit: (String) -> Unit = {},
    /** Smaller squares and tighter rows. */
    compact: Boolean = false,
    /** A touch on a contact found by digits (the smart dial) calls its number. */
    onDial: (String) -> Unit = {},
    /** What the Today card (the model decides whether it shows) tells, and what a touch on one of its lines does. */
    today: TodayInfo? = null,
    onToday: (TodayAction) -> Unit = {},
) {
    val insets = WindowInsets.systemBars.asPaddingValues()
    // In landscape the camera cutout sits on a side.
    val sides = WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal).asPaddingValues()
    val direction = LocalLayoutDirection.current
    // While searching the list is just the matches, without letter headers, followed by what else was found.
    var searchFocused by remember { mutableStateOf(false) }
    val rows = remember(model, query, results, global, answers, recentSearches, searchFocused) {
        if (query.isBlank()) {
            if (searchFocused && recentSearches.isNotEmpty()) {
                // The searches made before come right under the box; the list stays below them.
                listOfNotNull<ListRow>(ListRow.Today.takeIf { model.head > 1 }, ListRow.Search, ListRow.Section(R.string.search_section_recent)) +
                    recentSearches.map { ListRow.Recent(it) } + model.rows.drop(model.head)
            } else {
                model.rows
            }
        } else {
            buildList {
                add(ListRow.Search)
                answers.forEach { add(ListRow.Answer(it)) }
                results.forEach { add(ListRow.App(it)) }
                if (global.launcherSettings.isNotEmpty()) {
                    add(ListRow.Section(R.string.search_section_launcher))
                    global.launcherSettings.forEach { add(ListRow.LauncherSetting(it)) }
                }
                if (global.tiles.isNotEmpty()) {
                    add(ListRow.Section(R.string.search_section_tiles))
                    global.tiles.forEach { add(ListRow.Tile(it)) }
                }
                if (global.contacts.isNotEmpty()) {
                    add(ListRow.Section(R.string.search_section_contacts))
                    global.contacts.forEach { add(ListRow.Contact(it)) }
                }
                if (global.settings.isNotEmpty()) {
                    add(ListRow.Section(R.string.search_section_settings))
                    global.settings.forEach { add(ListRow.Setting(it)) }
                }
                if (global.events.isNotEmpty()) {
                    add(ListRow.Section(R.string.search_section_events))
                    global.events.forEach { add(ListRow.Event(it)) }
                }
                global.webQuery?.let {
                    add(ListRow.Section(R.string.search_section_web))
                    add(ListRow.Web(it))
                }
            }
        }
    }
    // Scrolling the list puts the keyboard away, and a new search starts from the top.
    val focus = LocalFocusManager.current
    LaunchedEffect(listState, focus) {
        snapshotFlow { listState.isScrollInProgress }.filter { it }.collect { focus.clearFocus() }
    }
    LaunchedEffect(query) { listState.scrollToItem(0) }
    // Rows composed within this window after the page opens animate in; rows that scroll into view later do not.
    val openedAt = remember(replayToken) { System.nanoTime() }

    CompositionLocalProvider(LocalCompactList provides compact) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = sides.calculateStartPadding(direction),
            end = sides.calculateEndPadding(direction),
            top = insets.calculateTopPadding() + 12.dp,
            bottom = insets.calculateBottomPadding() + 12.dp,
        ),
    ) {
        itemsIndexed(rows, key = { _, row -> row.key }, contentType = { _, row -> row::class }) { index, row ->
            when (row) {
                ListRow.Today -> if (today != null) TodayCard(today, index, listState, openedAt, exiting, onToday)
                ListRow.Search -> SearchRow(query, onQueryChange, searchFocus, onVoice, onFocusChange = { searchFocused = it }, onSubmit = onSearchSubmit)
                is ListRow.Answer -> AnswerRow(row.answer, index, listState, openedAt, exiting) { onAnswer(row.answer) }
                is ListRow.Recent -> ResultRow(
                    Glyph.Search, row.query, null, index, listState, openedAt, exiting, tag = "recent:${row.query}",
                ) { onQueryChange(row.query) }
                ListRow.Categories -> CategoryChips(categories, category, onCategory)
                is ListRow.Header -> HeaderRow(row.letter, index, listState, openedAt, exiting, onHeaderClick)
                is ListRow.App -> AppRow(
                    row.item, index, listState, openedAt, exiting, onLaunch, onLongPress,
                    highlight?.takeIf { it.appId == row.item.id && row.group.isEmpty() }, row.group,
                )
                is ListRow.Section -> SectionRow(row.title, if (row.title == R.string.search_section_recent) onClearRecents else null)
                is ListRow.Contact -> ResultRow(
                    if (row.match.number != null) Glyph.Phone else Glyph.People, row.match.name,
                    row.match.number ?: stringResource(R.string.search_contact_subtitle), index, listState, openedAt, exiting,
                    tag = "result:contact:${row.match.lookupUri}",
                ) {
                    val number = row.match.number
                    if (number != null) onDial(number) else onLaunch(AppItem("search:contact", row.match.name, Launch.Link(row.match.lookupUri)))
                }
                is ListRow.Setting -> ResultRow(
                    row.setting.glyph, row.setting.label, stringResource(R.string.search_setting_subtitle), index, listState, openedAt, exiting,
                    tag = "result:setting:${row.setting.id}", accent = true,
                ) { onLaunch(AppItem("search:setting", row.setting.label, Launch.Action(row.setting.action))) }
                is ListRow.Web -> ResultRow(
                    Glyph.Globe, stringResource(R.string.search_web_for, row.query, engine.label), null, index, listState, openedAt, exiting,
                    tag = "result:web", accent = true,
                ) { onLaunch(AppItem("search:web", row.query, Launch.Link(GlobalSearch.webUrl(row.query, engine)))) }
                is ListRow.LauncherSetting -> ResultRow(
                    Glyph.Settings, row.match.title, row.match.where, index, listState, openedAt, exiting,
                    tag = "result:lsetting:${row.match.id ?: "section:${row.match.section}"}", accent = true,
                ) { onOpenSetting(row.match) }
                is ListRow.Tile -> ResultRow(
                    row.match.item.glyph ?: Glyph.Star, row.match.item.label, row.match.snippet ?: stringResource(R.string.search_tile_subtitle),
                    index, listState, openedAt, exiting, tag = "result:tile:${row.match.item.id}",
                ) { onLaunch(row.match.item) }
                is ListRow.Event -> ResultRow(
                    Glyph.Calendar, row.match.title, eventWhen(row.match), index, listState, openedAt, exiting,
                    tag = "result:event:${row.match.eventId}",
                ) { onLaunch(AppItem("search:event", row.match.title, Launch.Link(CalendarSearch.uri(row.match.eventId)))) }
            }
        }
    }
    }
}

/** "Thu 12 November, 15:30" (or without the time for an event of the whole day), in the language and clock of the phone. */
@Composable
private fun eventWhen(event: EventMatch): String = momentText(event.startMillis, event.allDay)

/** A moment as the list says it: the date and the time of the day, in the language and the clock of the phone. [allDay] has no time. */
@Composable
private fun momentText(millis: Long, allDay: Boolean): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    val locale = Locale.getDefault()
    val day = java.time.Instant.ofEpochMilli(millis).atZone(if (allDay) java.time.ZoneOffset.UTC else java.time.ZoneId.systemDefault())
    val date = ClockFormat.date(day.toLocalDate(), locale)
    if (allDay) return date
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    val meridiem = ClockFormat.meridiem(day.toLocalTime(), is24, locale)
    return date + ", " + ClockFormat.time(day.toLocalTime(), is24, locale) + if (meridiem.isEmpty()) "" else " $meridiem"
}

private const val GLOW_START = 0.5f
private const val GLOW_MS = 1_400

private fun isEntering(openedAt: Long) = System.nanoTime() - openedAt < ENTRY_WINDOW_NANOS

private const val ENTRY_WINDOW_NANOS = 700_000_000L

/** 1 = turned away, 0 = facing the screen. Handles both the entrance and the exit of a row. */
@Composable
internal fun rememberRowAway(index: Int, listState: LazyListState, openedAt: Long, exiting: Boolean): Animatable<Float, *> {
    // A replay (openedAt changes) builds a new Animatable that starts turned away, so no frame shows the row in place.
    val away = remember(openedAt) { Animatable(if (isEntering(openedAt)) 1f else 0f) }
    LaunchedEffect(away) {
        if (away.value < 1f) return@LaunchedEffect
        delay((index - listState.firstVisibleItemIndex).coerceIn(0, 14) * 26L)
        away.animateTo(0f, tween(durationMillis = 380, easing = Motion.Decelerate))
    }
    LaunchedEffect(exiting, away) {
        if (!exiting) return@LaunchedEffect
        delay((index - listState.firstVisibleItemIndex).coerceIn(0, 14) * 14L)
        away.animateTo(1f, tween(durationMillis = 220, easing = Motion.Accelerate))
    }
    return away
}

/** The search box: a flat surface with a magnifier, the typed text and a button to clear it. */
@Composable
private fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    focusToken: Int,
    onVoice: (() -> Unit)?,
    onFocusChange: (Boolean) -> Unit = {},
    onSubmit: (String) -> Unit = {},
) {
    val colors = LocalMetroColors.current
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusToken) {
        if (focusToken > 0) {
            requester.requestFocus()
            keyboard?.show()
        }
    }
    val currentChange by rememberUpdatedState(onQueryChange)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .height(52.dp)
            .panel(RoundedCornerShape(16.dp).takeUnless { LocalStyle.current.isClassic } ?: RectangleShape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.Search, 24.dp, color = colors.subtle)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) MetroText(stringResource(R.string.search_apps), size = 20.sp, color = colors.subtle)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = fieldTextStyle(colors.foreground, 20.sp),
                cursorBrush = SolidColor(LocalAccent.current),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    onSubmit(query)
                    focus.clearFocus()
                }),
                modifier = Modifier.fillMaxWidth().testTag("search:field").focusRequester(requester).onFocusChanged { onFocusChange(it.isFocused) },
            )
        }
        if (query.isNotEmpty()) {
            Canvas(
                Modifier
                    .size(34.dp)
                    .pointerInput(Unit) { detectTapGestures { currentChange("") } }
                    .padding(10.dp),
            ) {
                val stroke = 2.dp.toPx()
                drawLine(colors.subtle, Offset(0f, 0f), Offset(size.width, size.height), stroke, StrokeCap.Round)
                drawLine(colors.subtle, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
            }
        } else if (onVoice != null) {
            Box(
                Modifier.size(40.dp).metroPress(onClick = onVoice).testTag("search:voice"),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(Glyph.Mic, 24.dp, color = colors.subtle)
            }
        }
    }
}

@Composable
private fun HeaderRow(
    letter: String,
    index: Int,
    listState: LazyListState,
    openedAt: Long,
    exiting: Boolean,
    onClick: () -> Unit,
) {
    val reduced = LocalReduceMotion.current
    val away = rememberRowAway(index, listState, openedAt, exiting)
    Box(
        Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .graphicsLayer { turnstile(away.value, reduced) }
            .metroPress(onClick = onClick, enabled = !exiting)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.size(SquareSize).litFill(true), contentAlignment = Alignment.Center) {
            MetroText(letter.lowercase(), size = if (LocalCompactList.current) 26.sp else 32.sp, color = onAccent())
        }
    }
}

@Composable
private fun AppRow(
    item: AppItem,
    index: Int,
    listState: LazyListState,
    openedAt: Long,
    exiting: Boolean,
    onLaunch: (AppItem) -> Unit,
    onLongPress: (AppItem, Float, Float) -> Unit,
    highlight: ListHighlight?,
    group: String = "",
) {
    val reduced = LocalReduceMotion.current
    val away = rememberRowAway(index, listState, openedAt, exiting)
    // The glow starts bright and fades; it is drawn behind the row, only while it lasts.
    val glow = remember { Animatable(0f) }
    LaunchedEffect(highlight) {
        if (highlight == null) return@LaunchedEffect
        glow.snapTo(GLOW_START)
        glow.animateTo(0f, tween(durationMillis = GLOW_MS))
    }
    val accent = LocalAccent.current
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    AppRowContent(
        item,
        Modifier
            .testTag(if (group.isEmpty()) "app:${item.id}" else "app:$group:${item.id}")
            .onGloballyPositioned { coordinates = it }
            .graphicsLayer { turnstile(away.value, reduced) }
            .drawBehind { if (glow.value > 0f) drawRect(accent.copy(alpha = glow.value)) }
            .metroPress(
                onClick = { onLaunch(item) },
                onLongClick = {
                    coordinates?.takeIf { it.isAttached }?.boundsInRoot()?.let { onLongPress(item, it.top, it.height) }
                },
                enabled = !exiting,
                longClickLabel = stringResource(R.string.a11y_more_options),
            ),
    )
}

/** The categories as chips in a row that scrolls sideways: "all", then the ones the installed apps belong to. */
@Composable
private fun CategoryChips(categories: List<AppCategory>, selected: AppCategory?, onSelect: (AppCategory?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().testTag("categories"),
    ) {
        item(key = "all") { Chip(stringResource(R.string.cat_all), selected == null, "cat:all") { onSelect(null) } }
        items(categories, key = { it.code }) { category ->
            Chip(stringResource(category.label), selected == category, "cat:${category.code}") { onSelect(if (selected == category) null else category) }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.testTag(tag).metroPress(onClick = onClick).litFill(selected).padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(text, size = 16.sp, weight = FontWeight.Normal, color = if (selected) onAccent() else LocalMetroColors.current.foreground)
    }
}

/** The heading of the results that are not apps (contacts, settings, web). */
@Composable
private fun SectionRow(@StringRes title: Int, onClear: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 18.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetroText(stringResource(title), size = 20.sp, color = LocalMetroColors.current.subtle, modifier = Modifier.weight(1f))
        if (onClear != null) {
            MetroText(
                stringResource(R.string.search_clear), size = 16.sp, weight = FontWeight.Normal, color = LocalAccent.current,
                modifier = Modifier.testTag("recent:clear").metroPress(onClick = onClear).padding(8.dp),
            )
        }
    }
}

private const val MAX_SHOWN_DAYS = 2_000_000L

/** A sum, a conversion, a number, a link, a timer: what it says and what touching it does, in an accent square. */
@Composable
private fun AnswerRow(answer: QuickAnswer, index: Int, listState: LazyListState, openedAt: Long, exiting: Boolean, onClick: () -> Unit) {
    val (glyph, title, subtitle) = when (answer) {
        is QuickAnswer.Calculation -> Triple(Glyph.Calculator, answer.result, "${answer.expression} · ${stringResource(R.string.answer_copy)}")
        is QuickAnswer.Conversion -> Triple(Glyph.Calculator, answer.text, stringResource(R.string.answer_copy))
        is QuickAnswer.Call -> Triple(Glyph.Phone, stringResource(R.string.answer_call, answer.number), null)
        is QuickAnswer.Message -> Triple(Glyph.Messaging, stringResource(R.string.answer_message, answer.number), null)
        is QuickAnswer.Link -> Triple(Glyph.Globe, stringResource(R.string.answer_open, answer.shown), null)
        is QuickAnswer.Email -> Triple(Glyph.Mail, stringResource(R.string.answer_email, answer.address), null)
        is QuickAnswer.Timer -> Triple(Glyph.Alarm, stringResource(R.string.answer_timer, QuickAnswerActions.length(answer.seconds)), null)
        is QuickAnswer.Alarm -> Triple(Glyph.Alarm, stringResource(R.string.answer_alarm, QuickAnswerActions.clock(answer.hour, answer.minute)), null)
        is QuickAnswer.Currency -> Triple(Glyph.Calculator, answer.text, stringResource(R.string.answer_currency_subtitle, answer.ratesDate))
        is QuickAnswer.WorldTime -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            val locale = Locale.getDefault()
            val is24 = android.text.format.DateFormat.is24HourFormat(context)
            val meridiem = ClockFormat.meridiem(answer.time, is24, locale)
            val time = ClockFormat.time(answer.time, is24, locale) + if (meridiem.isEmpty()) "" else " $meridiem"
            val day = when (answer.dayOffset) {
                1 -> stringResource(R.string.live_tomorrow)
                -1 -> stringResource(R.string.worldclock_yesterday)
                else -> stringResource(R.string.answer_day_same)
            }
            Triple(Glyph.Globe, stringResource(R.string.answer_worldtime, time, answer.city), "$day · ${stringResource(R.string.answer_copy)}")
        }
        is QuickAnswer.DateOffset -> {
            val days = answer.days.coerceIn(-MAX_SHOWN_DAYS, MAX_SHOWN_DAYS).toInt()
            val how = when {
                days == 0 -> stringResource(R.string.answer_today)
                days > 0 -> pluralStringResource(R.plurals.answer_in_days, days, days)
                else -> pluralStringResource(R.plurals.answer_days_ago, -days, -days)
            }
            Triple(Glyph.Calendar, QuickAnswerActions.fullDate(answer.date), "$how · ${stringResource(R.string.answer_copy)}")
        }
        is QuickAnswer.DaysUntil -> {
            val days = answer.days.coerceIn(-MAX_SHOWN_DAYS, MAX_SHOWN_DAYS).toInt()
            val date = QuickAnswerActions.fullDate(answer.target)
            when {
                days == 0 -> Triple(Glyph.Calendar, stringResource(R.string.answer_today), date)
                days > 0 -> Triple(Glyph.Calendar, pluralStringResource(R.plurals.answer_days, days, days), stringResource(R.string.answer_until, date))
                else -> Triple(Glyph.Calendar, pluralStringResource(R.plurals.answer_days, -days, -days), stringResource(R.string.answer_since, date))
            }
        }
        is QuickAnswer.Dice -> {
            val notation = "${if (answer.count > 1) answer.count else ""}d${answer.sides}" + when {
                answer.bonus > 0 -> "+${answer.bonus}"
                answer.bonus < 0 -> "${answer.bonus}"
                else -> ""
            }
            val parts = if (answer.rolls.size > 1 || answer.bonus != 0) answer.rolls.joinToString(" + ") + (if (answer.bonus != 0) " " + (if (answer.bonus > 0) "+ " else "− ") + kotlin.math.abs(answer.bonus) else "") + " · " else ""
            Triple(Glyph.Calculator, answer.total.toString(), "$notation: $parts${stringResource(R.string.answer_roll_again)}")
        }
        is QuickAnswer.Coin -> Triple(
            Glyph.Calculator, stringResource(if (answer.heads) R.string.answer_heads else R.string.answer_tails), stringResource(R.string.answer_flip_again),
        )
        is QuickAnswer.Reminder -> Triple(
            Glyph.Bell, stringResource(R.string.answer_reminder, answer.text),
            answer.at?.let { momentText(it, allDay = false) } ?: stringResource(R.string.answer_reminder_choose),
        )
        is QuickAnswer.CalendarEvent -> Triple(
            Glyph.Calendar,
            if (answer.title.isBlank()) stringResource(R.string.answer_event_new) else stringResource(R.string.answer_event, answer.title),
            answer.start?.let { momentText(it, answer.allDay) } ?: stringResource(R.string.answer_event_where),
        )
        is QuickAnswer.Note -> Triple(Glyph.OneNote, stringResource(R.string.answer_note, answer.text), stringResource(R.string.answer_note_where))
        is QuickAnswer.NearMe -> Triple(Glyph.Maps, stringResource(R.string.answer_near, answer.what), stringResource(R.string.answer_near_where))
        QuickAnswer.Scan -> Triple(Glyph.Camera, stringResource(R.string.answer_scan), stringResource(R.string.answer_scan_where))
    }
    ResultRow(glyph, title, subtitle, index, listState, openedAt, exiting, tag = "answer:${answer::class.simpleName}", accent = true, onClick = onClick)
}

/** A result that is not an app: a glyph in a square, its name, and what kind of result it is. */
@Composable
private fun ResultRow(
    glyph: Glyph,
    title: String,
    subtitle: String?,
    index: Int,
    listState: LazyListState,
    openedAt: Long,
    exiting: Boolean,
    tag: String,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val reduced = LocalReduceMotion.current
    val away = rememberRowAway(index, listState, openedAt, exiting)
    val colors = LocalMetroColors.current
    Row(
        Modifier
            .fillMaxWidth()
            // A result that says more than a line grows taller instead of being cut.
            .heightIn(min = RowHeight)
            .testTag(tag)
            .graphicsLayer { turnstile(away.value, reduced) }
            .metroPress(onClick = onClick, enabled = !exiting)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(SquareSize).litFill(accent),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(glyph, SquareSize * 0.6f, color = if (accent) onAccent() else colors.foreground)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            MetroText(title, size = 22.sp)
            if (subtitle != null) MetroText(subtitle, size = 13.sp, weight = FontWeight.Normal, color = colors.subtle)
        }
    }
}

/** One app: its square, then its name. Also drawn over the dimmed list by the context menu. */
@Composable
fun AppRowContent(item: AppItem, modifier: Modifier = Modifier, compact: Boolean = LocalCompactList.current) {
    CompositionLocalProvider(LocalCompactList provides compact) {
        Row(
            modifier.fillMaxWidth().height(RowHeight).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppSquare(item, SquareSize)
            Spacer(Modifier.width(14.dp))
            MetroText(item.label, size = if (compact) 19.sp else 22.sp, maxLines = 2, modifier = Modifier.weight(1f))
        }
    }
}
