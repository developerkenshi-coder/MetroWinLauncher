package com.metrowin.launcher.people

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.calls.DialerRole
import com.metrowin.launcher.data.ClockFormat
import com.metrowin.launcher.messages.MessagesActivity
import com.metrowin.launcher.notifications.NotificationHub
import com.metrowin.launcher.ui.anim.featherIn
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.pageSide
import com.metrowin.launcher.ui.start.whenText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.onAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Who the card shows: a contact ([key] is its lookup address) or just a number from the call log. */
data class Target(val name: String, val key: String?, val number: String?, val photoUri: String? = null)

/**
 * The card of a person: the photo across the top (the big one, and a touch opens it whole), a way to call, to write and to mail, every number
 * with its own call and message, what the contacts keep (mails, addresses, birthday, company, groups, note, where it is kept), how much the
 * two have been in touch and the history of calls and texts as a timeline. The contacts are only read: "edit" opens the contacts app.
 */
@Composable
fun ContactCard(target: Target, snapshot: PeopleSnapshot, prefs: PeoplePrefs, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = LocalMetroColors.current
    val side = pageSide(24.dp)
    val person = remember(snapshot, target.key) { snapshot.people.firstOrNull { it.key == target.key } }
    val detail by produceState<ContactDetail?>(null, target.key, snapshot) { value = target.key?.let { withContext(Dispatchers.IO) { PeopleRepo.detail(context, it, snapshot.groups) } } }
    val big by produceState<Bitmap?>(null, target.key) { value = target.key?.let { withContext(Dispatchers.IO) { PeopleRepo.bigPhoto(context, it) } } }
    val photo = big?.asImageBitmap() ?: rememberPhoto(target.photoUri ?: person?.photoUri)
    val numbers = remember(detail, person, target.number) {
        (detail?.phones?.map { it.value } ?: person?.numbers.orEmpty()).let { list -> if (target.number != null && list.none { PeopleBook.sameNumber(it, target.number) }) list + target.number else list }
    }
    val labels = remember(detail) { detail?.phones?.associate { it.value to it.label }.orEmpty() }
    val news by NotificationHub.entries.collectAsState()
    val touches by produceState<List<Touch>?>(null, numbers) { value = withContext(Dispatchers.IO) { PeopleRepo.history(context, numbers) } }
    val withNotes = remember(touches, news, prefs.historyNotifications, prefs.newsText, target.name) {
        val notes = if (prefs.historyNotifications) news.filter { it.title.equals(target.name, ignoreCase = true) }.map { Touch(it.postTime, TouchKind.Notification, if (prefs.newsText) it.text else "") } else emptyList()
        touches?.let { (it + notes).sortedByDescending { t -> t.date } }
    }
    var simFor by remember { mutableStateOf<String?>(null) }
    var whole by remember { mutableStateOf(false) }
    val main = numbers.firstOrNull()
    val starred = person?.starred == true || (target.key != null && prefs.isFavourite(target.key))
    fun place(number: String, sim: android.telecom.PhoneAccountHandle? = null) = DialerRole.call(context, number, direct = false, sim = sim)
    fun callWithChoice(number: String) {
        if (DialerRole.sims(context).isEmpty()) place(number) else simFor = number
    }
    fun write(number: String) { runCatching { context.startActivity(Intent(context, MessagesActivity::class.java).putExtra(MessagesActivity.EXTRA_ADDRESS, number)) } }
    fun open(intent: Intent?) { if (intent != null) runCatching { context.startActivity(intent) } }

    @Composable
    fun Header(m: Modifier) {
        Box(m.background(LocalAccent.current).metroPress(onClick = { if (photo != null) whole = true }, enabled = photo != null)) {
            MetroText(PeopleBook.avatar(target.name), size = 96.sp, color = onAccent(), modifier = Modifier.align(Alignment.Center))
            if (photo != null) Image(photo, stringResource(R.string.pp_photo_of, target.name), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            // Dark over any photo, so the white name reads.
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))).padding(start = side, end = side, top = 40.dp, bottom = 12.dp)) {
                MetroText(target.name, size = 36.sp, color = Color.White, maxLines = 2, minScale = 0.6f)
            }
        }
    }

    @Composable
    fun Details(m: Modifier) {
        Column(m) {
            FlowRow(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (main != null) {
                    Round(PeopleGlyph.Phone, stringResource(R.string.ppl_call), onLongClick = { callWithChoice(main) }, modifier = Modifier.testTag("people:call")) { place(main) }
                    Round(PeopleGlyph.Message, stringResource(R.string.ppl_message)) { write(main) }
                }
                detail?.emails?.firstOrNull()?.let { mail -> Round(PeopleGlyph.Mail, stringResource(R.string.pp_mail)) { open(PeopleActions.mail(mail.value)) } }
                if (target.key != null) {
                    Round(if (starred) PeopleGlyph.Star else PeopleGlyph.StarOutline, stringResource(R.string.pp_favourite), on = starred, modifier = Modifier.testTag("people:star")) { prefs.toggleFavourite(target.key) }
                    Round(PeopleGlyph.Edit, stringResource(R.string.pp_edit)) { open(PeopleActions.edit(target.key)) }
                    Round(PeopleGlyph.Share, stringResource(R.string.pp_share)) { open(PeopleActions.share(target.key)?.let { Intent.createChooser(it, null) }) }
                }
                if (main != null) {
                    val key = target.key ?: main
                    Round(PeopleGlyph.Bell, stringResource(R.string.pp_callback), on = key in prefs.callBack, modifier = Modifier.testTag("people:callback")) { prefs.toggleCallBack(key) }
                }
            }
            Column(Modifier.padding(horizontal = side)) {
                numbers.takeIf { it.isNotEmpty() }?.let {
                    SectionLabel(stringResource(R.string.pp_numbers))
                    it.forEach { number ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).metroPress(onClick = { place(number) }, onLongClick = { callWithChoice(number) }).padding(vertical = 4.dp)) {
                                MetroText(number, size = 22.sp, maxLines = 1)
                                labels[number]?.let { MetroText(it, size = 13.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1) }
                            }
                            Box(Modifier.size(48.dp).metroPress(onClick = { write(number) }).semantics { contentDescription = context.getString(R.string.ppl_message) }, contentAlignment = Alignment.Center) { PeopleIcon(PeopleGlyph.Message, 24.dp) }
                        }
                    }
                }
                detail?.let { d ->
                    if (d.emails.isNotEmpty()) SectionLabel(stringResource(R.string.pp_emails))
                    d.emails.forEach { LineRow(it.value, it.label) { open(PeopleActions.mail(it.value)) } }
                    if (d.addresses.isNotEmpty()) SectionLabel(stringResource(R.string.pp_addresses))
                    d.addresses.forEach { LineRow(it.value, it.label) { open(PeopleActions.map(it.value)) } }
                    d.websites.forEach { LineRow(it, null) { open(PeopleActions.site(it)) } }
                    PeopleBook.birthdayOf(d.birthday)?.let { (month, day) ->
                        SectionLabel(stringResource(R.string.pp_birthday))
                        val locale = Locale.getDefault()
                        val year = PeopleBook.birthYear(d.birthday)
                        val shown = runCatching { ClockFormat.date(LocalDate.of(year ?: LEAP_YEAR, month, day), locale) }.getOrDefault("$day/$month")
                        LineRow(shown, null, glyph = PeopleGlyph.Gift) { open(PeopleActions.birthday(target.name, month, day, LocalDate.now())) }
                        MetroText(stringResource(R.string.pp_add_birthday), size = 13.sp, weight = FontWeight.Normal, color = colors.subtle)
                    }
                    if (d.org.isNotEmpty()) { SectionLabel(stringResource(R.string.pp_org)); MetroText(d.org, size = 20.sp, maxLines = Int.MAX_VALUE) }
                    if (d.groups.isNotEmpty()) { SectionLabel(stringResource(R.string.pp_groups)); MetroText(d.groups.joinToString(", "), size = 18.sp, weight = FontWeight.Normal, maxLines = Int.MAX_VALUE) }
                    if (d.note.isNotEmpty()) { SectionLabel(stringResource(R.string.pp_note)); MetroText(d.note, size = 17.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = Int.MAX_VALUE) }
                    if (d.accounts.isNotEmpty()) { SectionLabel(stringResource(R.string.pp_accounts)); MetroText(d.accounts.joinToString("\n"), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = Int.MAX_VALUE) }
                }
                SectionLabel(stringResource(R.string.ppl_history))
                val list = withNotes
                if (list == null) Unit
                else if (list.isEmpty()) MetroText(stringResource(R.string.ppl_no_history), size = 16.sp, color = colors.subtle, modifier = Modifier.padding(bottom = 8.dp))
                else {
                    val calls = list.count { it.kind in CALLS }
                    val texts = list.count { it.kind == TouchKind.TextIn || it.kind == TouchKind.TextOut }
                    MetroText(
                        stringResource(R.string.pp_stats, pluralStringResource(R.plurals.pp_calls_count, calls, calls), pluralStringResource(R.plurals.pp_texts_count, texts, texts)) + " · " + stringResource(R.string.pp_last, whenText(list.first().date)),
                        size = 14.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = Int.MAX_VALUE, modifier = Modifier.padding(bottom = 8.dp).testTag("people:stats"),
                    )
                    Timeline(list, target.key ?: "")
                }
                Box(Modifier.padding(bottom = 32.dp))
            }
        }
    }

    Box(modifier.fillMaxSize().background(colors.background).testTag("people:card")) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth > maxHeight) {
                // Held sideways the photo takes the left of the page and the rest scrolls beside it, not under it.
                Row(Modifier.fillMaxSize()) {
                    Header(Modifier.weight(0.8f).fillMaxHeight())
                    Details(Modifier.weight(1.2f).fillMaxHeight().verticalScroll(rememberScrollState()))
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Header(Modifier.fillMaxWidth().aspectRatio(1.2f).heightIn(max = 340.dp))
                    Details(Modifier)
                }
            }
        }
        simFor?.let { number ->
            Sheet(onDismiss = { simFor = null }) {
                MetroText(stringResource(R.string.pp_sim_choose), size = 20.sp, color = LocalAccent.current, modifier = Modifier.padding(bottom = 8.dp))
                DialerRole.sims(context).forEach { sim ->
                    MetroButton(DialerRole.simLabel(context, sim).ifBlank { stringResource(R.string.pp_sim_unnamed) }, Modifier.padding(bottom = 8.dp)) { simFor = null; place(number, sim) }
                }
            }
        }
        if (whole && photo != null) FullPhoto(photo, target.name) { whole = false }
    }
}

private val CALLS = setOf(TouchKind.CallIn, TouchKind.CallOut, TouchKind.CallMissed)
private const val LEAP_YEAR = 2000

/** A line of the card that opens something (a mail, an address, a date): the text and the word that tells what it is. */
@Composable
private fun LineRow(text: String, label: String?, glyph: PeopleGlyph? = null, onClick: () -> Unit) {
    val colors = LocalMetroColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).metroPress(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        glyph?.let { PeopleIcon(it, 22.dp, Modifier.padding(end = 12.dp)) }
        Column {
            MetroText(text, size = 19.sp, maxLines = 3)
            if (!label.isNullOrBlank()) MetroText(label, size = 13.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1)
        }
    }
}

/** The history as a timeline: a line down the left with a dot for each time, the days as headings, the latest first. */
@Composable
private fun Timeline(touches: List<Touch>, seed: String) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val zone = remember { ZoneId.systemDefault() }
    val locale = Locale.getDefault()
    val today = LocalDate.now()
    val days = remember(touches) { touches.groupBy { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() }.toList() }
    var index = 0
    days.forEach { (day, items) ->
        MetroText(
            if (day == today) stringResource(R.string.live_today) else ClockFormat.date(day, locale),
            size = 14.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        )
        items.forEach { touch ->
            val missed = touch.kind == TouchKind.CallMissed
            val glyph = when (touch.kind) {
                TouchKind.CallIn -> PeopleGlyph.CallIn
                TouchKind.CallOut -> PeopleGlyph.CallOut
                TouchKind.CallMissed -> PeopleGlyph.CallMissed
                TouchKind.TextIn, TouchKind.TextOut -> PeopleGlyph.Message
                TouchKind.Notification -> PeopleGlyph.Bell
            }
            val title = when (touch.kind) {
                TouchKind.CallIn -> stringResource(R.string.pp_call_in)
                TouchKind.CallOut -> stringResource(R.string.pp_call_out)
                TouchKind.CallMissed -> stringResource(R.string.pp_call_missed)
                TouchKind.Notification -> stringResource(R.string.pp_notification)
                else -> touch.text
            }
            Row(Modifier.fillMaxWidth().featherIn(index++, seed).testTag("people:history"), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 2.dp).size(32.dp).border(2.dp, if (missed) accent else colors.subtle, CircleShape).clip(CircleShape), contentAlignment = Alignment.Center) {
                    PeopleIcon(glyph, 16.dp, color = if (missed) accent else colors.foreground)
                }
                Column(Modifier.padding(start = 12.dp, bottom = 10.dp)) {
                    MetroText(title, size = 17.sp, weight = FontWeight.Normal, maxLines = 3, color = if (missed) accent else colors.foreground)
                    if (touch.kind == TouchKind.Notification && touch.text.isNotEmpty()) MetroText(touch.text, size = 14.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 2)
                    MetroText(whenText(touch.date), size = 12.sp, weight = FontWeight.Normal, color = colors.subtle)
                }
            }
        }
    }
}
