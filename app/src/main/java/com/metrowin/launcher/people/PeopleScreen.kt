package com.metrowin.launcher.people

import com.metrowin.launcher.ui.actioncenter.MetroToast
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.metrowin.launcher.R
import com.metrowin.launcher.calls.DialerRole
import com.metrowin.launcher.data.ContactImporter
import com.metrowin.launcher.data.ContactSource
import com.metrowin.launcher.data.LivePermissions
import com.metrowin.launcher.data.PeopleReader
import com.metrowin.launcher.data.RecentCall
import com.metrowin.launcher.messages.MessagesActivity
import com.metrowin.launcher.notifications.NotificationEntry
import com.metrowin.launcher.notifications.NotificationHub
import com.metrowin.launcher.ui.anim.featherIn
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.apps.JumpList
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.HubPager
import com.metrowin.launcher.ui.components.Mark
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.MetroToggle
import com.metrowin.launcher.ui.components.cutoutSidesPadding
import com.metrowin.launcher.ui.components.pageSide
import com.metrowin.launcher.ui.components.topBarsPadding
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.start.ageText
import com.metrowin.launcher.ui.start.whenText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.pageBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** How wide the page has to be for the list and the card to stand side by side (a tablet, an unfolded phone, a phone held sideways in DeX). */
private val TWO_PANES = 720.dp

private val TargetSaver = Saver<Target?, List<String>>(
    save = { t -> t?.let { listOf(it.name, it.key.orEmpty(), it.number.orEmpty(), it.photoUri.orEmpty()) } },
    restore = { l -> Target(l[0], l[1].ifEmpty { null }, l[2].ifEmpty { null }, l[3].ifEmpty { null }) },
)

/**
 * The People hub of Windows Phone: a panorama with all the contacts under their letters (the letter opens the jump list; a field and the
 * chips of the places and the groups narrow them down), what is new (the people of the latest calls, the birthdays, the people to call
 * back and who wrote) and the favourites as photo tiles. A touch on a person opens the card; on a wide page the card stands beside the list.
 * The contacts are read again when they change, wherever they change. The options are in the "…" of the bar at the foot.
 */
@Composable
fun PeopleScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PeoplePrefs(context) }
    var allowed by remember { mutableStateOf(LivePermissions.isGranted(context, LivePermissions.contacts)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    var calls by remember { mutableStateOf(LivePermissions.isGranted(context, LivePermissions.calls)) }
    val askCalls = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { calls = it }
    var contactTick by remember { mutableIntStateOf(0) }
    var callTick by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(allowed) { if (allowed) PeopleRepo.contactChanges(context).collect { contactTick++ } }
    androidx.compose.runtime.LaunchedEffect(calls) { if (calls) PeopleRepo.callChanges(context).collect { callTick++ } }
    // Coming back from the contacts app (or any app) shows what changed there, even where the phone sent no notice.
    var started by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (started) { contactTick++; callTick++ } else started = true }
    val snapshot by produceState<PeopleSnapshot?>(PeopleRepo.cached, allowed, contactTick) {
        value = if (allowed) withContext(Dispatchers.IO) { PeopleRepo.read(context) } else PeopleSnapshot.Empty
    }
    val recent by produceState<List<RecentCall>>(emptyList(), calls, callTick) { value = if (calls) withContext(Dispatchers.IO) { PeopleReader(context).recentCalls() } else emptyList() }
    val news by NotificationHub.entries.collectAsState()
    var target by rememberSaveable(stateSaver = TargetSaver) { mutableStateOf<Target?>(null) }
    var shown by remember { mutableStateOf<Target?>(null) }
    if (target != null) shown = target
    var query by rememberSaveable { mutableStateOf("") }
    var quick by remember { mutableStateOf<Person?>(null) }
    var options by remember { mutableStateOf(false) }
    var dupes by remember { mutableStateOf(false) }
    val pager = rememberPagerState { 3 }

    val vcfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) runCatching { context.startActivity(ContactImporter.createVcfImportIntent(uri)) } }
    val vcfSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard")) { uri ->
        if (uri != null) scope.launch {
            val keys = snapshot?.people.orEmpty().mapNotNull { PeopleBook.lookupKey(it.key) }
            val n = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(uri)?.use { PeopleActions.exportVcf(context, keys, it) } ?: 0 }.getOrDefault(0) }
            MetroToast.show(context, context.getString(R.string.pp_exported, n))
        }
    }

    BackHandler(enabled = target != null) { target = null }
    BackHandler(enabled = quick != null || options || dupes) { quick = null; options = false; dupes = false }

    val pages = remember(prefs.order) { prefs.order.takeIf { o -> o.length == 3 && o.toSet() == setOf('0', '1', '2') }.orEmpty().ifEmpty { "012" }.map { it.digitToInt() } }
    val names = listOf(stringResource(R.string.ppl_all), stringResource(R.string.peoplehub_whats_new), stringResource(R.string.peoplehub_favourites))
    val open: (Person) -> Unit = { target = Target(it.name, it.key, null, it.photoUri) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= TWO_PANES
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxSize()) {
                Frame {
                    HubPager(stringResource(R.string.peoplehub_default_label), pages.map { names[it] }, pager, userScrollEnabled = allowed) { slot ->
                        if (!allowed) {
                            if (slot == 0) Column(Modifier.padding(horizontal = pageSide(24.dp)).testTag("people:permission"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                MetroText(stringResource(R.string.page_needs_contacts), size = 18.sp, color = LocalMetroColors.current.subtle, maxLines = Int.MAX_VALUE)
                                MetroButton(stringResource(R.string.photos_allow), onClick = { ask.launch(LivePermissions.contacts) })
                            }
                            return@HubPager
                        }
                        when (pages[slot]) {
                            0 -> AllPage(snapshot, prefs, query, { query = it }, open) { quick = it }
                            1 -> NewPage(snapshot ?: PeopleSnapshot.Empty, recent, calls, news, prefs, onAllowCalls = { askCalls.launch(LivePermissions.calls) }) { target = it }
                            else -> FavouritesPage(snapshot, prefs, open)
                        }
                    }
                    if (allowed) AppBar(
                        listOf(AppBarItem("＋", stringResource(R.string.pp_add)) { runCatching { context.startActivity(ContactImporter.createInsertIntent()) } }),
                        Modifier.background(LocalMetroColors.current.background),
                        menu = listOf(
                            AppBarItem("≡", stringResource(R.string.pp_options)) { options = true },
                            AppBarItem("", stringResource(R.string.pp_import)) { vcfPicker.launch("text/x-vcard") }.icon(Mark.Down),
                            AppBarItem("", stringResource(R.string.pp_export)) { vcfSaver.launch("contacts.vcf") }.icon(Mark.Up),
                            AppBarItem("≈", stringResource(R.string.pp_duplicates)) { dupes = true },
                        ),
                    )
                }
                if (!wide) CardOverlay(target != null, shown, snapshot ?: PeopleSnapshot.Empty, prefs)
            }
            if (wide) Box(Modifier.weight(1f).fillMaxSize().pageBackground().topBarsPadding().navigationBarsPadding()) {
                val t = target
                if (t == null) EmptyState(PeopleGlyph.Person, stringResource(R.string.pp_pick), Modifier.align(Alignment.Center))
                else ContactCard(t, snapshot ?: PeopleSnapshot.Empty, prefs)
            }
        }
        quick?.let { QuickSheet(it, prefs, onOpen = { p -> quick = null; open(p) }, onClose = { quick = null }) }
        if (options) OptionsSheet(prefs, pager.currentPage.let { pages[it] }) { options = false }
        if (dupes) DuplicatesSheet(snapshot?.people.orEmpty(), onOpen = { p -> dupes = false; open(p) }) { dupes = false }
    }
}

/** The card over the list on a narrow page: it slides in from the right (it is just there with reduced motion). */
@Composable
private fun CardOverlay(visible: Boolean, target: Target?, snapshot: PeopleSnapshot, prefs: PeoplePrefs) {
    val reduced = LocalReduceMotion.current
    AnimatedVisibility(
        visible,
        enter = if (reduced) EnterTransition.None else slideInHorizontally { it / 3 } + fadeIn(),
        exit = if (reduced) ExitTransition.None else slideOutHorizontally { it / 3 } + fadeOut(),
    ) { target?.let { ContactCard(it, snapshot, prefs) } }
}

/** The page of the hub: the colour of the look in use, clear of the bars and the camera hole, and lifted by the keyboard. */
@Composable
private fun Frame(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().pageBackground().topBarsPadding().navigationBarsPadding().cutoutSidesPadding().imePadding().padding(horizontal = pageSide(0.dp)), content = content)
}

private val SOURCE_COLOURS = mapOf(ContactSource.Google to Color(0xFF4285F4), ContactSource.Phone to Color(0xFF34A853), ContactSource.Sim to Color(0xFFFBBC05), ContactSource.Other to Color(0xFF9E9E9E))

@Composable
private fun AllPage(
    snapshot: PeopleSnapshot?, prefs: PeoplePrefs, query: String, onQuery: (String) -> Unit, onOpen: (Person) -> Unit, onQuick: (Person) -> Unit,
) {
    if (snapshot == null) return SkeletonRows()
    val side = pageSide(24.dp)
    val colors = LocalMetroColors.current
    var group by rememberSaveable { mutableStateOf<Long?>(null) }
    val present = remember(snapshot) { ContactSource.entries.filter { s -> snapshot.people.any { ContactSource.has(it.sources, s) } } }
    val mask = prefs.sources
    val shown = remember(snapshot, query, mask, group) {
        snapshot.people.filter { p -> (present.size < 2 || p.sources and mask != 0) && (group == null || group in p.groups) && PeopleBook.matches(p, query) }
    }
    val groups = remember(shown) { PeopleBook.grouped(shown) }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var jump by remember { mutableStateOf(false) }
    val showHint = !prefs.hintSeen && snapshot.people.isNotEmpty()
    val groupChips = prefs.groupChips && snapshot.groups.isNotEmpty()
    val chips = present.size > 1 || groupChips
    val lead = 1 + (if (chips) 1 else 0) + (if (showHint) 1 else 0)
    // Where each letter starts in the list: the rows above the people, then for each letter its header and its people.
    val starts = remember(groups, lead) { var at = lead; groups.associate { (letter, list) -> letter to at.also { at += list.size + 1 } } }
    val dots = prefs.sourceDots && present.size > 1
    val line = colors.foreground.copy(alpha = 0.12f)
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().scrollIndicator(state, colors.subtle.copy(alpha = 0.5f)).testTag("people:all"), state, contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "search") { MetroTextField(query, onQuery, stringResource(R.string.pp_search), Modifier.padding(horizontal = side, vertical = 4.dp).testTag("people:search")) }
            if (chips) item(key = "chips") {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = side, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (present.size > 1) present.forEach { s -> PageChip(stringResource(s.label), ContactSource.has(mask, s), "people:source:${s.name}") { prefs.sources = ContactSource.toggle(mask, s) } }
                    if (groupChips) snapshot.groups.forEach { (id, title) -> PageChip(title, group == id, "people:group:$id") { group = if (group == id) null else id } }
                }
            }
            if (showHint) item(key = "hint") {
                Column(Modifier.fillMaxWidth().padding(horizontal = side, vertical = 8.dp).border(2.dp, LocalAccent.current).padding(12.dp).testTag("people:hint"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetroText(stringResource(R.string.pp_hint), size = 16.sp, weight = FontWeight.Normal, maxLines = Int.MAX_VALUE)
                    MetroButton(stringResource(R.string.pp_got_it)) { prefs.hintSeen = true }
                }
            }
            if (shown.isEmpty()) item(key = "empty") {
                EmptyState(PeopleGlyph.Person, stringResource(if (snapshot.people.isEmpty()) R.string.pp_empty else R.string.pp_no_match), tag = "people:empty")
            }
            groups.forEach { (letter, list) ->
                item(key = "letter:$letter") {
                    if (prefs.plainLetters) MetroText(letter.lowercase(), size = 26.sp, color = LocalAccent.current, modifier = Modifier.padding(start = side, top = 12.dp, bottom = 2.dp).metroPress(onClick = { jump = true }).testTag("people:letter:$letter"))
                    else Box(Modifier.padding(start = side, top = 8.dp, bottom = 4.dp).size(52.dp).background(LocalAccent.current).metroPress(onClick = { jump = true }).semantics { contentDescription = letter }.testTag("people:letter:$letter"), contentAlignment = Alignment.Center) {
                        MetroText(letter.lowercase(), size = 30.sp, color = com.metrowin.launcher.ui.theme.onAccent())
                    }
                }
                itemsIndexed(list, key = { _, p -> p.key }) { index, person ->
                    PersonRow(person, query, prefs, dots, line, side, Modifier.featherIn(index, person.key), onOpen = { onOpen(person) }, onQuick = { onQuick(person) })
                }
            }
        }
        if (jump) {
            // The whole alphabet and the letters of other alphabets that are in the list; the letters that have people are the ones that answer.
            val keys = remember(groups) { ('A'..'Z').map { it.toString() } + groups.map { it.first }.filter { it != "#" && it[0] !in 'A'..'Z' } + "#" }
            JumpList(
                keys = keys, available = groups.map { it.first }.toSet(), showRecent = false,
                onPick = { letter -> jump = false; scope.launch { state.scrollToItem(starts[letter] ?: 0) } },
                onDismiss = { jump = false },
            )
        }
    }
}

@Composable
private fun PersonRow(person: Person, query: String, prefs: PeoplePrefs, dots: Boolean, line: Color, side: androidx.compose.ui.unit.Dp, modifier: Modifier, onOpen: () -> Unit, onQuick: () -> Unit) {
    val colors = LocalMetroColors.current
    val favourite = person.starred || prefs.isFavourite(person.key)
    val sub = person.org.ifBlank { person.numbers.firstOrNull() ?: person.emails.firstOrNull().orEmpty() }
    Row(
        modifier.divider(prefs.dividers, line).metroPress(onClick = onOpen, onLongClick = onQuick, longClickLabel = stringResource(R.string.pp_quick))
            .listRow(person.name).padding(horizontal = side, vertical = 6.dp).testTag("people:person"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhotoTile(person.name, rememberPhoto(person.photoUri), Modifier.size(56.dp), label = false)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            MetroText(person.name, size = 22.sp, maxLines = 2, highlight = query.trim().substringBefore(' '))
            if (sub.isNotBlank()) MetroText(sub, size = 14.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1)
        }
        if (dots) ContactSource.entries.firstOrNull { ContactSource.has(person.sources, it) }?.let {
            Box(Modifier.padding(start = 8.dp).size(8.dp).clip(CircleShape).background(SOURCE_COLOURS.getValue(it)))
        }
        if (favourite) PeopleIcon(PeopleGlyph.Star, 18.dp, Modifier.padding(start = 8.dp), color = LocalAccent.current)
    }
}

@Composable
private fun NewPage(
    snapshot: PeopleSnapshot, recent: List<RecentCall>, hasCalls: Boolean, news: List<NotificationEntry>, prefs: PeoplePrefs,
    onAllowCalls: () -> Unit, onOpen: (Target) -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalMetroColors.current
    val side = pageSide(24.dp)
    val today = remember { LocalDate.now() }
    val birthdays = remember(snapshot, prefs.birthdays) { if (prefs.birthdays) PeopleBook.upcomingBirthdays(snapshot.people, today).take(5) else emptyList() }
    val callBack = prefs.callBack
    val toCall = remember(snapshot, callBack) { snapshot.people.filter { it.key in callBack }.sortedBy { callBack[it.key] } }
    // The people of the latest calls, once each, as a mosaic of photos.
    val faces = remember(snapshot, recent) { recent.mapNotNull { snapshot.personOf(it.number) }.distinctBy { it.key }.take(6) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = side).testTag("people:new")) {
        if (faces.size >= 2) {
            faces.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { p -> PhotoTile(p.name, rememberPhoto(p.photoUri), Modifier.weight(1f).metroPress(onClick = { onOpen(Target(p.name, p.key, null, p.photoUri)) })) }
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
        if (birthdays.isNotEmpty()) {
            MetroText(stringResource(R.string.pp_birthdays), size = 18.sp, color = colors.subtle, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            birthdays.forEach { (p, days) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).metroPress(onClick = { onOpen(Target(p.name, p.key, null, p.photoUri)) }), verticalAlignment = Alignment.CenterVertically) {
                    PeopleIcon(PeopleGlyph.Gift, 22.dp, Modifier.padding(end = 12.dp), color = LocalAccent.current)
                    Column(Modifier.weight(1f)) {
                        MetroText(p.name, size = 20.sp, maxLines = 1)
                        MetroText(if (days == 0) stringResource(R.string.live_today) else pluralStringResource(R.plurals.pp_in_days, days, days), size = 13.sp, weight = FontWeight.Normal, color = colors.subtle)
                    }
                }
            }
        }
        if (toCall.isNotEmpty()) {
            MetroText(stringResource(R.string.pp_callback_list), size = 18.sp, color = colors.subtle, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            toCall.forEach { p ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).metroPress(onClick = { onOpen(Target(p.name, p.key, null, p.photoUri)) }), verticalAlignment = Alignment.CenterVertically) {
                    PeopleIcon(PeopleGlyph.Bell, 22.dp, Modifier.padding(end = 12.dp), color = LocalAccent.current)
                    MetroText(p.name, size = 20.sp, maxLines = 1)
                }
            }
        }
        MetroText(stringResource(R.string.peoplehub_recent), size = 18.sp, color = colors.subtle, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
        when {
            !hasCalls -> {
                MetroText(stringResource(R.string.page_needs_calls), size = 16.sp, color = colors.subtle, maxLines = Int.MAX_VALUE, modifier = Modifier.padding(bottom = 8.dp))
                MetroButton(stringResource(R.string.photos_allow), onClick = onAllowCalls)
            }
            recent.isEmpty() -> MetroText(stringResource(R.string.peoplehub_no_calls), size = 16.sp, color = colors.subtle)
            else -> recent.take(10).forEachIndexed { index, call ->
                val person = snapshot.personOf(call.number)
                val accent = LocalAccent.current
                Row(Modifier.fillMaxWidth().featherIn(index, call.date).heightIn(min = 56.dp).metroPress(onClick = { onOpen(Target(person?.name ?: call.name, person?.key, call.number, person?.photoUri)) }).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    PhotoTile(person?.name ?: call.name, rememberPhoto(person?.photoUri), Modifier.size(44.dp), label = false, letterSize = 20)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        MetroText(person?.name ?: call.name, size = 20.sp, maxLines = 1, color = if (call.missed) accent else colors.foreground)
                        MetroText(whenText(call.date), size = 13.sp, weight = FontWeight.Normal, color = colors.subtle)
                    }
                    PeopleIcon(if (call.missed) PeopleGlyph.CallMissed else if (call.outgoing) PeopleGlyph.CallOut else PeopleGlyph.CallIn, 20.dp, color = if (call.missed) accent else colors.subtle)
                }
            }
        }
        MetroText(stringResource(R.string.peoplehub_whats_new), size = 18.sp, color = colors.subtle, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
        // Only the messages and calls, and only who they are from: the words of a notification are private and stay out unless the user turns them on.
        val shown = news.filter { it.urgent && !it.summary }.take(10)
        if (shown.isEmpty()) MetroText(stringResource(R.string.peoplehub_nothing_new), size = 16.sp, color = colors.subtle)
        shown.forEach { entry ->
            Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).metroPress(onClick = { NotificationHub.open(context, entry) }).padding(bottom = 10.dp)) {
                MetroText(entry.title.ifBlank { entry.packageName }, size = 19.sp, maxLines = 1)
                if (prefs.newsText && entry.text.isNotBlank()) MetroText(entry.text, size = 15.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 2)
                ageText(entry.postTime)?.let { MetroText(it, size = 12.sp, weight = FontWeight.Normal, color = colors.subtle) }
            }
        }
    }
}

@Composable
private fun FavouritesPage(snapshot: PeopleSnapshot?, prefs: PeoplePrefs, onOpen: (Person) -> Unit) {
    if (snapshot == null) return SkeletonRows(3)
    val side = pageSide(24.dp)
    val mine = prefs.favourites
    val favourites = remember(snapshot, mine) { snapshot.people.filter { it.starred || it.key in mine } }
    if (favourites.isEmpty()) return EmptyState(PeopleGlyph.StarOutline, stringResource(R.string.pp_no_favourites), tag = "people:favourites:empty")
    val min = when (prefs.tileSize) { 0 -> 90.dp; 2 -> 150.dp; else -> 110.dp }
    LazyVerticalGrid(
        GridCells.Adaptive(min), Modifier.fillMaxSize().testTag("people:favourites"), contentPadding = PaddingValues(start = side, end = side, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(favourites, key = { it.key }) { person ->
            PhotoTile(person.name, rememberPhoto(person.photoUri), Modifier.metroPress(onClick = { onOpen(person) }).semantics { contentDescription = person.name }.testTag("people:fav"))
        }
    }
}

/** What a long touch on a person offers without opening the card. */
@Composable
private fun QuickSheet(person: Person, prefs: PeoplePrefs, onOpen: (Person) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val number = person.numbers.firstOrNull()
    Sheet(onClose, Modifier.testTag("people:quick")) {
        MetroText(person.name, size = 24.sp, color = LocalAccent.current, maxLines = 2, modifier = Modifier.padding(bottom = 8.dp))
        val gap = Modifier.padding(bottom = 8.dp)
        MetroButton(stringResource(R.string.pp_open), gap) { onOpen(person) }
        if (number != null) {
            MetroButton(stringResource(R.string.ppl_call), gap) { onClose(); DialerRole.call(context, number, direct = false) }
            MetroButton(stringResource(R.string.ppl_message), gap) { onClose(); runCatching { context.startActivity(Intent(context, MessagesActivity::class.java).putExtra(MessagesActivity.EXTRA_ADDRESS, number)) } }
        }
        MetroButton(stringResource(if (person.starred || prefs.isFavourite(person.key)) R.string.pp_unfavourite else R.string.pp_favourite), gap) { prefs.toggleFavourite(person.key); onClose() }
        PeopleActions.share(person.key)?.let { share -> MetroButton(stringResource(R.string.pp_share), gap) { onClose(); runCatching { context.startActivity(Intent.createChooser(share, null)) } } }
    }
}

/** The switches of the app, and the little things that are not a switch. */
@Composable
private fun OptionsSheet(prefs: PeoplePrefs, page: Int, onClose: () -> Unit) {
    Sheet(onClose, Modifier.testTag("people:options")) {
        MetroText(stringResource(R.string.pp_options), size = 24.sp, color = LocalAccent.current, modifier = Modifier.padding(bottom = 8.dp))
        MetroToggle(stringResource(R.string.pp_opt_birthdays), prefs.birthdays, { prefs.birthdays = it })
        MetroToggle(stringResource(R.string.pp_opt_news_text), prefs.newsText, { prefs.newsText = it })
        MetroToggle(stringResource(R.string.pp_opt_history_notes), prefs.historyNotifications, { prefs.historyNotifications = it })
        MetroToggle(stringResource(R.string.pp_opt_groups), prefs.groupChips, { prefs.groupChips = it })
        MetroToggle(stringResource(R.string.pp_opt_dividers), prefs.dividers, { prefs.dividers = it })
        MetroToggle(stringResource(R.string.pp_opt_plain_letters), prefs.plainLetters, { prefs.plainLetters = it })
        MetroToggle(stringResource(R.string.pp_opt_source_dots), prefs.sourceDots, { prefs.sourceDots = it })
        val size = stringResource(listOf(R.string.pp_size_small, R.string.pp_size_medium, R.string.pp_size_large)[prefs.tileSize.coerceIn(0, 2)])
        MetroButton(stringResource(R.string.pp_opt_tile_size, size), Modifier.padding(top = 8.dp)) { prefs.tileSize = (prefs.tileSize + 1) % 3 }
        MetroButton(stringResource(R.string.pp_opt_move_page), Modifier.padding(top = 8.dp)) { prefs.moveRight(page) }
        MetroButton(stringResource(R.string.pp_opt_hint_again), Modifier.padding(top = 8.dp, bottom = 8.dp)) { prefs.hintSeen = false; onClose() }
    }
}

/** The contacts that may be one person, for the user to join in the contacts app (the hub holds no right to change them). */
@Composable
private fun DuplicatesSheet(people: List<Person>, onOpen: (Person) -> Unit, onClose: () -> Unit) {
    val groups = remember(people) { PeopleBook.duplicates(people) }
    val colors = LocalMetroColors.current
    Sheet(onClose, Modifier.testTag("people:duplicates")) {
        MetroText(stringResource(R.string.pp_duplicates), size = 24.sp, color = LocalAccent.current, modifier = Modifier.padding(bottom = 4.dp))
        if (groups.isEmpty()) MetroText(stringResource(R.string.pp_no_duplicates), size = 16.sp, color = colors.subtle, maxLines = Int.MAX_VALUE)
        else MetroText(stringResource(R.string.pp_duplicates_note), size = 14.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = Int.MAX_VALUE, modifier = Modifier.padding(bottom = 8.dp))
        groups.forEach { group ->
            Column(Modifier.padding(bottom = 12.dp)) {
                group.forEach { p -> MetroText(p.name + (p.numbers.firstOrNull()?.let { " · $it" } ?: ""), size = 18.sp, maxLines = 2, modifier = Modifier.heightIn(min = 40.dp).metroPress(onClick = { onOpen(p) })) }
            }
        }
    }
}
