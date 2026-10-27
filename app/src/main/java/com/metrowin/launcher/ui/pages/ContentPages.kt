package com.metrowin.launcher.ui.pages

import com.metrowin.launcher.ui.anim.metroTween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import com.metrowin.launcher.ui.anim.Motion
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroDatePicker
import com.metrowin.launcher.ui.components.MetroTimePicker
import java.time.LocalDate
import java.time.LocalTime
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.Favourite
import com.metrowin.launcher.data.Finance
import com.metrowin.launcher.data.FinanceData
import com.metrowin.launcher.data.FinanceItems
import com.metrowin.launcher.data.NewsData
import com.metrowin.launcher.data.RecentCall
import com.metrowin.launcher.data.Reminder
import com.metrowin.launcher.data.Reminders
import com.metrowin.launcher.notifications.NotificationEntry
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.start.ageText
import com.metrowin.launcher.ui.start.arrow
import com.metrowin.launcher.ui.start.whenText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Currency
import java.util.Locale

/** The page of the News tile: the headlines of the source in use, a touch opens one. */
@Composable
fun NewsPage(
    news: NewsData?,
    googleSource: Boolean,
    onOpen: (String) -> Unit,
    onRefresh: () -> Unit,
    onChooseSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMetroColors.current
    PageFrame(stringResource(R.string.news_page_header), news?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.news_default_label), modifier) {
        if (news == null || news.items.isEmpty()) {
            PageNote(stringResource(R.string.news_empty), Modifier.padding(bottom = 16.dp).testTag("news:empty"))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                news.items.forEachIndexed { index, item ->
                    Column(Modifier.fillMaxWidth().testTag("news:item:$index").metroPress(onClick = { onOpen(item.link) })) {
                        MetroText(item.title, size = 21.sp, maxLines = 4, lineHeight = 26.sp)
                        val line = listOfNotNull(item.source.takeIf { it.isNotBlank() }, ageText(item.publishedAt)).joinToString(" · ")
                        if (line.isNotEmpty()) MetroText(line, size = 14.sp, weight = FontWeight.Normal, color = colors.subtle)
                    }
                }
            }
            PageNote(
                stringResource(
                    R.string.news_updated,
                    DateUtils.getRelativeTimeSpanString(news.fetchedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString(),
                ),
                Modifier.padding(top = 20.dp, bottom = 12.dp),
            )
        }
        Row(Modifier.padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetroButton(stringResource(R.string.weather_refresh), Modifier.weight(1f).testTag("news:refresh"), onClick = onRefresh)
            MetroButton(stringResource(R.string.news_choose_source), Modifier.weight(1f).testTag("news:source"), onClick = onChooseSource)
        }
        if (googleSource) {
            MetroButton(stringResource(R.string.news_open_google), Modifier.fillMaxWidth().padding(bottom = 24.dp).testTag("news:google")) { onOpen("https://news.google.com/") }
        }
    }
}

/** The page of the Finance tile: what is followed, its value in the base currency and the move since yesterday; a touch opens Google Finance. */
@Composable
fun FinancePage(
    finance: FinanceData?,
    onOpen: (String) -> Unit,
    onRefresh: () -> Unit,
    onChoose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMetroColors.current
    val locale = Locale.getDefault()
    PageFrame(stringResource(R.string.finance_page_header), stringResource(R.string.finance_default_label), modifier) {
        if (finance == null || finance.quotes.isEmpty()) {
            PageNote(stringResource(R.string.finance_empty), Modifier.padding(bottom = 16.dp).testTag("finance:empty"))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                finance.quotes.forEachIndexed { index, quote ->
                    Row(
                        Modifier.fillMaxWidth().testTag("finance:item:$index").metroPress(onClick = { onOpen(Finance.quoteUrl(quote.item, finance.base)) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            MetroText(FinanceItems.symbol(quote.item), size = 26.sp)
                            MetroText(nameOf(quote.item), size = 14.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            MetroText("${Finance.format(quote.value, locale)} ${finance.base}", size = 24.sp, maxLines = 1)
                            val change = Finance.formatChange(quote.changePercent, locale)
                            if (change != null) {
                                MetroText(arrow(Finance.trend(quote.changePercent)) + " " + change, size = 16.sp, weight = FontWeight.Normal, color = colors.subtle)
                            }
                        }
                    }
                }
            }
            PageNote(
                stringResource(
                    R.string.finance_updated,
                    DateUtils.getRelativeTimeSpanString(finance.fetchedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString(),
                ),
                Modifier.padding(top = 20.dp),
            )
        }
        PageNote(stringResource(R.string.finance_sources), Modifier.padding(top = 4.dp, bottom = 12.dp))
        Row(Modifier.padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetroButton(stringResource(R.string.weather_refresh), Modifier.weight(1f).testTag("finance:refresh"), onClick = onRefresh)
            MetroButton(stringResource(R.string.finance_choose), Modifier.weight(1f).testTag("finance:choose"), onClick = onChoose)
        }
    }
}

/** The name of a currency or a coin, in the language of the phone. */
private fun nameOf(item: String): String =
    if (item.startsWith(FinanceItems.CRYPTO)) {
        item.removePrefix(FinanceItems.CRYPTO).replaceFirstChar { it.uppercase() }
    } else {
        runCatching { Currency.getInstance(item.removePrefix(FinanceItems.FX)).getDisplayName(Locale.getDefault()) }.getOrDefault("")
    }

/**
 * The reminders: a form for a new one (what, and when from a few quick times or the pickers of Android) and the list of the
 * ones to come, the ones that were missed and the ones that are done. The alarms are Android's, set when a reminder is made.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemindersPage(
    reminders: List<Reminder>,
    draft: String,
    canExact: Boolean,
    onAdd: (text: String, at: Long) -> Boolean,
    onDone: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onClearDone: () -> Unit,
    onAllowExact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMetroColors.current
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    var text by remember(draft) { mutableStateOf(draft) }
    val now = remember(reminders) { System.currentTimeMillis() }
    fun millis(at: LocalDateTime) = at.atZone(zone).toInstant().toEpochMilli()
    fun add(at: Long) {
        if (onAdd(text, at)) text = ""
    }
    // The day and the hour of a reminder are chosen on wheels, one above the other, in the page itself.
    var choosing by remember { mutableStateOf(false) }
    var chosenDate by remember { mutableStateOf(LocalDate.now()) }
    var chosenTime by remember { mutableStateOf(LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)) }
    fun choose() {
        if (Reminders.clean(text) == null) return
        val start = LocalDateTime.now().plusHours(1)
        chosenDate = start.toLocalDate()
        chosenTime = start.toLocalTime().withMinute(0).withSecond(0).withNano(0)
        choosing = true
    }
    PageFrame(stringResource(R.string.reminders_page_header), stringResource(R.string.reminders_default_label), modifier) {
        MetroTextField(
            text, { text = it.take(Reminders.MAX_TEXT) }, stringResource(R.string.reminders_hint), Modifier.testTag("reminders:field"),
            onSearch = { if (Reminders.clean(text) != null) add(millis(Reminders.quickTime(Reminders.QuickTime.InOneHour, LocalDateTime.now()))) },
        )
        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Reminders.QuickTime.InOneHour to R.string.reminders_in_hour,
                Reminders.QuickTime.ThisEvening to R.string.reminders_this_evening,
                Reminders.QuickTime.TomorrowMorning to R.string.reminders_tomorrow_morning,
            ).forEach { (which, label) ->
                PageChip(stringResource(label), false, "reminders:quick:${which.name}") {
                    if (Reminders.clean(text) != null) add(millis(Reminders.quickTime(which, LocalDateTime.now())))
                }
            }
            PageChip(stringResource(R.string.reminders_choose), false, "reminders:choose") { choose() }
        }
        AnimatedVisibility(
            choosing,
            enter = expandVertically(metroTween(durationMillis = 300, easing = Motion.Decelerate)) + fadeIn(metroTween(200)),
            exit = shrinkVertically(metroTween(durationMillis = 200, easing = Motion.Accelerate)) + fadeOut(metroTween(140)),
        ) {
            Column(Modifier.padding(top = 12.dp).testTag("reminders:wheels"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MetroDatePicker(chosenDate, { chosenDate = it }, years = LocalDate.now().year..LocalDate.now().year + 20, tag = "reminders:date")
                MetroTimePicker(chosenTime, android.text.format.DateFormat.is24HourFormat(context), { chosenTime = it }, tag = "reminders:time")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetroButton(stringResource(android.R.string.ok), Modifier.weight(1f).testTag("reminders:wheels:ok")) {
                        choosing = false
                        add(millis(LocalDateTime.of(chosenDate, chosenTime)))
                    }
                    MetroButton(stringResource(android.R.string.cancel), Modifier.weight(1f).testTag("reminders:wheels:cancel")) { choosing = false }
                }
            }
        }
        if (!canExact) {
            PageNote(stringResource(R.string.reminders_inexact), Modifier.padding(top = 14.dp))
            MetroButton(stringResource(R.string.reminders_allow_exact), Modifier.fillMaxWidth().padding(top = 8.dp).testTag("reminders:exact"), onClick = onAllowExact)
        }
        val upcoming = Reminders.upcoming(reminders, now)
        val missed = Reminders.missed(reminders, now)
        val done = reminders.filter { it.done }
        if (reminders.isEmpty()) PageNote(stringResource(R.string.reminders_empty), Modifier.padding(top = 18.dp).testTag("reminders:empty"))
        ReminderSection(R.string.reminders_missed, missed, now, onDone, onRemove)
        ReminderSection(R.string.reminders_upcoming, upcoming, now, onDone, onRemove)
        if (done.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                MetroText(stringResource(R.string.reminders_done), size = 18.sp, color = colors.subtle, modifier = Modifier.weight(1f))
                MetroText(
                    stringResource(R.string.reminders_clear), size = 16.sp, weight = FontWeight.Normal, color = LocalAccent.current,
                    modifier = Modifier.testTag("reminders:clear").metroPress(onClick = onClearDone).padding(8.dp),
                )
            }
            done.forEach { reminder ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    MetroText(reminder.text, size = 19.sp, color = colors.subtle, maxLines = 2, modifier = Modifier.weight(1f))
                    RoundButton("×", stringResource(R.string.reminders_remove), "reminders:remove:${reminder.id}") { onRemove(reminder.id) }
                }
            }
        }
        Spacer(Modifier.size(24.dp))
    }
}

@Composable
private fun ReminderSection(title: Int, list: List<Reminder>, now: Long, onDone: (Long) -> Unit, onRemove: (Long) -> Unit) {
    if (list.isEmpty()) return
    MetroText(stringResource(title), size = 18.sp, color = LocalMetroColors.current.subtle, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))
    list.forEach { reminder ->
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).testTag("reminders:item:${reminder.id}"), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                MetroText(reminder.text, size = 21.sp, maxLines = 3, lineHeight = 26.sp)
                MetroText(whenText(reminder.at, now), size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
            }
            RoundButton("✓", stringResource(R.string.reminder_done), "reminders:done:${reminder.id}") { onDone(reminder.id) }
            RoundButton("×", stringResource(R.string.reminders_remove), "reminders:remove:${reminder.id}") { onRemove(reminder.id) }
        }
    }
}

@Composable
private fun RoundButton(glyph: String, description: String, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.testTag(tag).semantics { contentDescription = description }.metroPress(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(glyph, size = 26.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.foreground)
    }
}

/** What to say when the people cannot be read: the permission they need, and a button to give it. */
@Composable
private fun PermissionNote(text: String, tag: String, onRequest: () -> Unit) {
    PageNote(text, Modifier.padding(bottom = 8.dp))
    MetroButton(stringResource(R.string.page_allow), Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag(tag), onClick = onRequest)
}

/** The favourites as a grid of photos, three to a line: a touch calls. */
@Composable
fun DialGridPage(
    favourites: List<Favourite>?,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onCall: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PageFrame(stringResource(R.string.dialgrid_page_header), stringResource(R.string.dialgrid_default_label), modifier) {
        when {
            !hasPermission -> PermissionNote(stringResource(R.string.page_needs_contacts), "dialgrid:allow", onRequestPermission)
            favourites == null -> Unit
            favourites.isEmpty() -> PageNote(stringResource(R.string.dialgrid_empty), Modifier.testTag("dialgrid:empty"))
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                favourites.chunked(3).forEachIndexed { row, group ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.forEachIndexed { column, person ->
                            Box(Modifier.weight(1f)) { FavouriteSquare(person, "dialgrid:${row * 3 + column}", onCall) }
                        }
                        repeat(3 - group.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** One person as a square: the photo (or the first letter) and the name over it. */
@Composable
private fun FavouriteSquare(person: Favourite, tag: String, onCall: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().aspectRatio(1f).testTag(tag).metroPress(onClick = { onCall(person.number) }).background(LocalAccent.current),
    ) {
        if (person.photo != null) {
            Image(person.photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            MetroText(person.name.take(1).uppercase(), size = 44.sp, color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))).padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
        ) {
            MetroText(person.name, size = 13.sp, weight = FontWeight.Normal, maxLines = 1, color = Color.White)
        }
    }
}

/**
 * The people hub of Windows Phone: the favourites, the latest calls and what is new (the latest notifications). Each part says
 * what it needs when the permission for it is missing.
 */
@Composable
fun PeopleHubPage(
    favourites: List<Favourite>?,
    calls: List<RecentCall>?,
    news: List<NotificationEntry>,
    labelOf: (String) -> String,
    hasContacts: Boolean,
    hasCallLog: Boolean,
    onRequestContacts: () -> Unit,
    onRequestCallLog: () -> Unit,
    onCall: (String) -> Unit,
    onOpenNotification: (NotificationEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMetroColors.current
    PageFrame(stringResource(R.string.peoplehub_page_header), stringResource(R.string.peoplehub_default_label), modifier) {
        MetroText(stringResource(R.string.peoplehub_favourites), size = 18.sp, color = colors.subtle, modifier = Modifier.padding(bottom = 8.dp))
        when {
            !hasContacts -> PermissionNote(stringResource(R.string.page_needs_contacts), "peoplehub:allow:contacts", onRequestContacts)
            favourites == null -> Unit
            favourites.isEmpty() -> PageNote(stringResource(R.string.dialgrid_empty), Modifier.padding(bottom = 8.dp))
            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp).testTag("peoplehub:favourites")) {
                items(favourites, key = { it.lookupUri }) { person -> Box(Modifier.width(96.dp)) { FavouriteSquare(person, "peoplehub:fav:${person.lookupUri}", onCall) } }
            }
        }
        MetroText(stringResource(R.string.peoplehub_recent), size = 18.sp, color = colors.subtle, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
        when {
            !hasCallLog -> PermissionNote(stringResource(R.string.page_needs_calls), "peoplehub:allow:calls", onRequestCallLog)
            calls == null -> Unit
            calls.isEmpty() -> PageNote(stringResource(R.string.peoplehub_no_calls))
            else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                calls.take(8).forEachIndexed { index, call ->
                    Row(Modifier.fillMaxWidth().testTag("peoplehub:call:$index").metroPress(onClick = { onCall(call.number) }).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        MetroText(
                            when {
                                call.missed -> "✕"
                                call.outgoing -> "↗"
                                else -> "↙"
                            },
                            size = 20.sp, weight = FontWeight.Normal, color = if (call.missed) LocalAccent.current else colors.subtle, modifier = Modifier.width(30.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            MetroText(call.name, size = 20.sp, maxLines = 1)
                            MetroText(whenText(call.date), size = 13.sp, weight = FontWeight.Normal, color = colors.subtle)
                        }
                    }
                }
            }
        }
        MetroText(stringResource(R.string.peoplehub_whats_new), size = 18.sp, color = colors.subtle, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
        if (news.isEmpty()) {
            PageNote(stringResource(R.string.peoplehub_nothing_new), Modifier.padding(bottom = 24.dp))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                news.take(8).forEachIndexed { index, entry ->
                    Column(Modifier.fillMaxWidth().testTag("peoplehub:news:$index").metroPress(onClick = { onOpenNotification(entry) })) {
                        MetroText(entry.title.ifBlank { labelOf(entry.packageName) }, size = 19.sp, maxLines = 1)
                        if (entry.text.isNotBlank()) MetroText(entry.text, size = 15.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 2)
                        MetroText(labelOf(entry.packageName) + " · " + (ageText(entry.postTime) ?: ""), size = 12.sp, weight = FontWeight.Normal, color = colors.subtle)
                    }
                }
            }
        }
    }
}
