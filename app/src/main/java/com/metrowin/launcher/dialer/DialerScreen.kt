package com.metrowin.launcher.dialer

import com.metrowin.launcher.ui.components.centeredContent
import com.metrowin.launcher.ui.components.topBarsPadding
import android.Manifest
import android.content.Context
import android.content.Intent
import android.telecom.PhoneAccountHandle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.metrowin.launcher.R
import com.metrowin.launcher.calls.DialerRole
import com.metrowin.launcher.data.ContactDirectory
import com.metrowin.launcher.data.ContactImporter
import com.metrowin.launcher.data.ContactSource
import com.metrowin.launcher.data.DialerContact
import com.metrowin.launcher.data.DialerContactReader
import com.metrowin.launcher.data.LivePermissions
import com.metrowin.launcher.data.PeopleReader
import com.metrowin.launcher.data.PhoneCalls
import com.metrowin.launcher.data.RecentCall
import com.metrowin.launcher.messages.MessagesActivity
import com.metrowin.launcher.data.SettingsStore
import com.metrowin.launcher.ui.anim.metroPress
import androidx.compose.runtime.rememberCoroutineScope
import com.metrowin.launcher.ui.apps.JumpList
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.sectionHeader
import com.metrowin.launcher.ui.components.sectionStarts
import com.metrowin.launcher.ui.components.AppBarItem
import kotlinx.coroutines.launch
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.pages.FavouriteSquare
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.start.DialogFrame
import com.metrowin.launcher.ui.start.whenText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.pageBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Green = Color(0xFF2E9E41)

/**
 * The phone of Windows Phone: a pivot with the keypad, the address book (with the places the contacts come from), the favourites and the
 * history of calls. [number] is what the keypad starts with (a number someone asked the dialer to open).
 */
@Composable
fun DialerScreen(
    number: String,
    onNumberChange: (String) -> Unit,
    onPlaced: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 0
) {
    val context = LocalContext.current
    var hasContacts by remember { mutableStateOf(LivePermissions.isGranted(context, LivePermissions.contacts)) }
    var hasCallLog by remember { mutableStateOf(LivePermissions.isGranted(context, LivePermissions.calls)) }
    var canCall by remember { mutableStateOf(LivePermissions.isGranted(context, PhoneCalls.permission)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasContacts = result[LivePermissions.contacts] ?: hasContacts
        hasCallLog = result[LivePermissions.calls] ?: hasCallLog
        canCall = result[PhoneCalls.permission] ?: canCall
    }
    // Everything the dialer reads is asked for once, at the first visit.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val missing = listOf(LivePermissions.contacts, LivePermissions.calls, PhoneCalls.permission).filter { !LivePermissions.isGranted(context, it) }
        if (missing.isNotEmpty()) ask.launch(missing.toTypedArray())
    }
    fun call(target: String, sim: PhoneAccountHandle? = null) {
        if (target.isEmpty()) return
        DialerRole.call(context, target, canCall, sim)
        onPlaced()
    }
    fun voicemail() {
        DialerRole.voicemail(context)
        onPlaced()
    }
    // As on Windows Phone, the history comes first; the keypad is a page of its own, one tap away on the bar.
    val titles = listOf(R.string.dialer_tab_history, R.string.dialer_tab_favourites, R.string.dialer_tab_contacts, R.string.dialer_tab_keypad).map { stringResource(it) }
    val pager = rememberPagerState(initialPage = initialPage.coerceIn(0, 3)) { titles.size }
    val scope = rememberCoroutineScope()
    // The numbers on the keys 2 to 9 (long press), set from the history and shown on the keypad.
    var keys by remember { mutableStateOf(NumberBookStore(context).keys()) }
    // Back from the keypad, the contacts or the favourites goes to the history, where the phone starts; only from there it leaves.
    androidx.activity.compose.BackHandler(enabled = pager.currentPage != PAGE_HISTORY) { scope.launch { pager.animateScrollToPage(PAGE_HISTORY) } }
    Column(modifier.fillMaxSize().pageBackground().topBarsPadding().navigationBarsPadding().centeredContent()) {
        PivotHeader(titles, pager, Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp))
        HorizontalPager(pager, Modifier.weight(1f), beyondViewportPageCount = 1) { page ->
            Box(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                when (page) {
                    PAGE_HISTORY -> HistoryPage(hasCallLog, keys, { keys = it }, { ask.launch(arrayOf(LivePermissions.calls)) }, ::call)
                    PAGE_FAVOURITES -> FavouritesPage(hasContacts, { ask.launch(arrayOf(LivePermissions.contacts)) }, ::call)
                    PAGE_CONTACTS -> ContactsPage(hasContacts, { ask.launch(arrayOf(LivePermissions.contacts)) }, ::call)
                    else -> KeypadPage(number, onNumberChange, hasContacts, canCall, keys, ::call, ::voicemail)
                }
            }
        }
        if (pager.currentPage != PAGE_KEYPAD) {
            AppBar(
                listOf(
                    AppBarItem("#", titles[PAGE_KEYPAD]) { scope.launch { pager.animateScrollToPage(PAGE_KEYPAD) } },
                    AppBarItem("⌕", titles[PAGE_CONTACTS]) { scope.launch { pager.animateScrollToPage(PAGE_CONTACTS) } },
                ),
                Modifier.testTag("dialer:bar"),
            )
        }
    }
}

@Composable
private fun KeypadPage(number: String, onNumberChange: (String) -> Unit, hasContacts: Boolean, canCall: Boolean, keys: Map<Char, String>, call: (String, PhoneAccountHandle?) -> Unit, voicemail: () -> Unit) {
    val context = LocalContext.current
    // With two SIMs the green bar is split, one half for each; with one the phone chooses.
    val sims = remember(canCall) { DialerRole.sims(context) }
    val colors = LocalMetroColors.current
    val directory = remember { ContactDirectory(context.applicationContext) }
    val suggestions by produceState(emptyList(), number, hasContacts) {
        value = withContext(Dispatchers.IO) { directory.search(number.filter { it.isDigit() }, 3) }
    }
    var isDefault by remember { mutableStateOf(DialerRole.isDefault(context)) }
    val role = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { isDefault = DialerRole.isDefault(context) }
    val roleIntent = remember { DialerRole.requestIntent(context) }
    var needsFullScreen by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { needsFullScreen = DialerRole.needsFullScreenGrant(context) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            if (needsFullScreen) {
                PageNote(stringResource(R.string.dialer_fullscreen_note))
                MetroButton(stringResource(R.string.dialer_fullscreen_button), Modifier.fillMaxWidth().padding(top = 6.dp).testTag("dialer:fullscreen")) {
                    runCatching { context.startActivity(DialerRole.fullScreenSettings(context)) }
                }
            }
            if (!isDefault && roleIntent != null) {
                PageNote(stringResource(R.string.dialer_default_note))
                MetroButton(stringResource(R.string.dialer_default_button), Modifier.fillMaxWidth().padding(top = 6.dp).testTag("dialer:default")) { role.launch(roleIntent) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                MetroText(
                    number.ifEmpty { stringResource(R.string.dialer_number_hint) }, size = 36.sp, maxLines = 1, textAlign = TextAlign.Center,
                    color = if (number.isEmpty()) colors.subtle else colors.foreground, modifier = Modifier.weight(1f).testTag("dialer:number"),
                )
                MetroText(
                    "⌫", size = 30.sp, weight = FontWeight.Normal,
                    modifier = Modifier.testTag("dialer:delete").metroPress(onClick = { onNumberChange(number.dropLast(1)) }, onLongClick = { onNumberChange("") }).padding(12.dp),
                )
            }
            suggestions.forEach { entry ->
                Row(Modifier.fillMaxWidth().testTag("dialer:suggest").metroPress(onClick = { call(entry.number, null) }).padding(vertical = 5.dp)) {
                    MetroText(entry.name, size = 20.sp, modifier = Modifier.weight(1f))
                    MetroText(entry.number, size = 16.sp, weight = FontWeight.Normal, color = colors.subtle)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            DialPad(onKey = { onNumberChange((number + it).take(MAX_NUMBER)) }, onVoicemail = voicemail, onLongKey = { key -> keys[key]?.let { call(it, null) } }, marked = keys.keys)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (sims.ifEmpty { listOf(null) }).forEachIndexed { index, sim ->
                    Box(
                        Modifier.weight(1f).height(60.dp).testTag(if (sim == null) "dialer:call" else "dialer:call:sim$index")
                            .metroPress(onClick = { call(number, sim) }, enabled = number.isNotEmpty())
                            .background(if (number.isEmpty()) Green.copy(alpha = 0.4f) else Green),
                        contentAlignment = Alignment.Center,
                    ) {
                        MetroText(
                            if (sim == null) stringResource(R.string.dialer_call) else DialerRole.simLabel(context, sim).ifBlank { "SIM ${index + 1}" },
                            size = 22.sp, weight = FontWeight.Normal, color = Color.White, maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactsPage(hasPermission: Boolean, onRequest: () -> Unit, call: (String) -> Unit) {
    val context = LocalContext.current
    val store = remember { SettingsStore.of(context.applicationContext) }
    var mask by remember { mutableIntStateOf(store.load().dialerSources) }
    var query by remember { mutableStateOf("") }
    val vcfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching { context.startActivity(ContactImporter.createVcfImportIntent(uri)) }
        }
    }
    val contacts by produceState<List<DialerContact>?>(null, hasPermission) {
        value = if (hasPermission) withContext(Dispatchers.IO) { DialerContactReader(context.applicationContext).read() } else emptyList()
    }
    if (!hasPermission) {
        Column { PageNote(stringResource(R.string.page_needs_contacts)); MetroButton(stringResource(R.string.page_allow), Modifier.fillMaxWidth().padding(top = 8.dp), onClick = onRequest) }
        return
    }
    val shown = contacts.orEmpty().filter { it.shownIn(mask) && (query.isBlank() || it.name.contains(query.trim(), ignoreCase = true)) }
    val groups = shown.groupBy { it.name.first().uppercaseChar() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var jumping by remember { mutableStateOf(false) }
    // The list is the box with the search and the buttons, a note when there is nobody, then each letter with its people.
    val starts = sectionStarts(groups.values.map { it.size }, first = 1 + if (contacts != null && shown.isEmpty()) 1 else 0)
    val startOf = groups.keys.map { it.toString() }.zip(starts).toMap()
    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().testTag("dialer:contacts"), state = listState) {
        item {
            Column(Modifier.padding(bottom = 8.dp)) {
                MetroTextField(query, { query = it }, stringResource(R.string.dialer_search_hint), Modifier.testTag("dialer:search"))
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetroButton(
                        text = stringResource(R.string.dialer_add_contact),
                        modifier = Modifier.weight(1f).testTag("dialer:addcontact"),
                        onClick = { runCatching { context.startActivity(ContactImporter.createInsertIntent()) } }
                    )
                    MetroButton(
                        text = stringResource(R.string.dialer_import_vcf),
                        modifier = Modifier.weight(1f).testTag("dialer:importvcf"),
                        onClick = { vcfPicker.launch("text/x-vcard") }
                    )
                }
                PageNote(stringResource(R.string.dialer_sources_hint), Modifier.padding(top = 10.dp, bottom = 6.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ContactSource.entries.forEach { source ->
                        PageChip(stringResource(source.label), ContactSource.has(mask, source), "dialer:source:${source.name}") {
                            mask = ContactSource.toggle(mask, source)
                            store.save(store.load().copy(dialerSources = mask))
                        }
                    }
                }
                PageNote(stringResource(R.string.dialer_sources_note), Modifier.padding(top = 6.dp))
            }
        }
        if (contacts != null && shown.isEmpty()) item { PageNote(stringResource(R.string.dialer_no_contacts), Modifier.padding(top = 8.dp)) }
        groups.forEach { (letter, people) ->
            sectionHeader("h$letter", onClick = { jumping = true }) { MetroText(letter.toString(), size = 26.sp, color = LocalAccent.current, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)) }
            items(people, key = { it.lookupUri }) { person ->
                Column(
                    Modifier.fillMaxWidth().testTag("dialer:contact").metroPress(
                        onClick = { call(person.number) },
                        onLongClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, person.lookupUri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                    ).padding(vertical = 6.dp),
                ) {
                    MetroText(person.name, size = 22.sp)
                    MetroText(person.number, size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
                }
            }
        }
    }
    if (jumping) {
        JumpList(
            keys = ('A'..'Z').map { it.toString() } + startOf.keys.filter { it.single() !in 'A'..'Z' },
            available = startOf.keys,
            onPick = { key ->
                jumping = false
                startOf[key]?.let { index -> scope.launch { listState.scrollToItem(index) } }
            },
            onDismiss = { jumping = false },
            showRecent = false,
            modifier = Modifier.testTag("dialer:jump"),
        )
    }
    }
}

@Composable
private fun FavouritesPage(hasPermission: Boolean, onRequest: () -> Unit, call: (String) -> Unit) {
    val context = LocalContext.current
    val favourites by produceState<List<com.metrowin.launcher.data.Favourite>?>(null, hasPermission) {
        value = if (hasPermission) withContext(Dispatchers.IO) { PeopleReader(context.applicationContext).favourites() } else emptyList()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        when {
            !hasPermission -> { PageNote(stringResource(R.string.page_needs_contacts)); MetroButton(stringResource(R.string.page_allow), Modifier.fillMaxWidth().padding(top = 8.dp), onClick = onRequest) }
            favourites == null -> Unit
            favourites!!.isEmpty() -> PageNote(stringResource(R.string.dialgrid_empty), Modifier.testTag("dialer:nofavourites"))
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                favourites!!.chunked(3).forEachIndexed { row, group ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.forEachIndexed { column, person -> Box(Modifier.weight(1f)) { FavouriteSquare(person, "dialer:fav:${row * 3 + column}", call) } }
                        repeat(3 - group.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryPage(hasPermission: Boolean, keys: Map<Char, String>, onKeys: (Map<Char, String>) -> Unit, onRequest: () -> Unit, call: (String) -> Unit) {
    val context = LocalContext.current
    val colors = LocalMetroColors.current
    val scope = rememberCoroutineScope()
    val book = remember { NumberBookStore(context) }
    var notes by remember { mutableStateOf(book.notes()) }
    var reload by remember { mutableIntStateOf(0) }
    var missedOnly by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<CallGroup?>(null) }
    val calls by produceState<List<RecentCall>?>(null, hasPermission, reload) {
        value = if (hasPermission) withContext(Dispatchers.IO) { PeopleReader(context.applicationContext).recentCalls(HISTORY) } else emptyList()
    }
    // Taking calls out of the log needs the permission to write it, which is asked for the first time it is wanted.
    var waiting by remember { mutableStateOf<CallGroup?>(null) }
    fun remove(group: CallGroup) {
        scope.launch { withContext(Dispatchers.IO) { PeopleReader(context.applicationContext).let { reader -> group.members.forEach(reader::deleteCall) } }; reload++ }
    }
    val askWrite = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) waiting?.let(::remove); waiting = null }
    if (!hasPermission) {
        Column { PageNote(stringResource(R.string.page_needs_calls)); MetroButton(stringResource(R.string.page_allow), Modifier.fillMaxWidth().padding(top = 8.dp), onClick = onRequest) }
        return
    }
    val shown = calls?.filter { !missedOnly || it.missed }?.let(NumberBook::group)
    // The filter chips sit above the list, not over it.
    Column(Modifier.fillMaxSize()) {
    Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PageChip(stringResource(R.string.dialer_filter_all), !missedOnly, "dialer:filter:all") { missedOnly = false }
        PageChip(stringResource(R.string.dialer_filter_missed), missedOnly, "dialer:filter:missed") { missedOnly = true }
    }
    if (shown?.isEmpty() == true) PageNote(stringResource(R.string.dialer_no_history), Modifier.testTag("dialer:nohistory"))
    LazyColumn(Modifier.weight(1f).testTag("dialer:history")) {
        items(shown.orEmpty()) { group ->
            val item = group.latest
            Row(Modifier.fillMaxWidth().testTag("dialer:call:${item.date}").metroPress(onClick = { call(item.number) }, onLongClick = { target = group }).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                MetroText(
                    when {
                        item.missed -> "✕"
                        item.outgoing -> "↗"
                        else -> "↙"
                    },
                    size = 22.sp, weight = FontWeight.Normal, color = if (item.missed) LocalAccent.current else colors.subtle, modifier = Modifier.width(34.dp),
                )
                Column(Modifier.weight(1f)) {
                    MetroText(if (group.count > 1) "${item.name} (${group.count})" else item.name, size = 21.sp)
                    MetroText(if (item.name == item.number) whenText(item.date) else item.number + " · " + whenText(item.date), size = 13.sp, weight = FontWeight.Normal, color = colors.subtle)
                    notes[PhoneCalls.clean(item.number)]?.let { MetroText(it, size = 13.sp, weight = FontWeight.Normal, color = LocalAccent.current, maxLines = 1) }
                }
            }
        }
    }
    }
    // Last, so it is drawn over the list.
    target?.let { group ->
        val call = group.latest
        val clean = PhoneCalls.clean(call.number)
        // The last 20 calls of this number, whatever the kind, from the log (the list of the page only has the latest 100 of all).
        val history by produceState<List<RecentCall>>(emptyList(), clean) {
            value = withContext(Dispatchers.IO) {
                PeopleReader(context.applicationContext).recentCalls(DETAILS_SCAN).filter { PhoneCalls.clean(it.number) == clean }.take(DETAILS)
            }
        }
        DialogFrame(onCancel = { target = null }) {
            MetroText(call.name, size = 22.sp, maxLines = 1)
            if (call.name != call.number) MetroText(call.number, size = 14.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1)
            MetroTextField(
                notes[clean].orEmpty(),
                { text -> notes = NumberBook.withNote(notes, call.number, text.take(NumberBook.MAX_NOTE)).also(book::saveNotes) },
                stringResource(R.string.dialer_note_hint), Modifier.testTag("dialer:note"),
            )
            history.forEach { item ->
                Row(Modifier.fillMaxWidth().testTag("dialer:detail"), verticalAlignment = Alignment.CenterVertically) {
                    MetroText(if (item.missed) "✕" else if (item.outgoing) "↗" else "↙", size = 16.sp, weight = FontWeight.Normal, color = if (item.missed) LocalAccent.current else colors.subtle, modifier = Modifier.width(28.dp))
                    MetroText(whenText(item.date), size = 14.sp, weight = FontWeight.Normal, color = colors.subtle)
                }
            }
            MetroButton(stringResource(R.string.dialer_send_sms), Modifier.fillMaxWidth().testTag("dialer:sms")) {
                target = null
                runCatching { context.startActivity(Intent(context, MessagesActivity::class.java).putExtra(MessagesActivity.EXTRA_ADDRESS, call.number).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            // A caller the contacts do not know is shown by its number.
            if (call.name == call.number) {
                MetroButton(stringResource(R.string.dialer_add_contact), Modifier.fillMaxWidth().testTag("dialer:add_known")) {
                    target = null
                    runCatching { context.startActivity(ContactImporter.createInsertIntent(number = call.number)) }
                }
            }
            if (hasPermission) {
                PageNote(stringResource(R.string.dialer_assign_key))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberBook.KEYS.forEach { key ->
                        val mine = keys[key] == clean
                        PageChip(key.toString(), mine, "dialer:assign:$key") { onKeys(NumberBook.withKey(keys, key, if (mine) "" else call.number).also(book::saveKeys)) }
                    }
                }
            }
            MetroButton(stringResource(R.string.dialer_delete_call), Modifier.fillMaxWidth().testTag("dialer:delete_call")) {
                target = null
                if (LivePermissions.isGranted(context, Manifest.permission.WRITE_CALL_LOG)) remove(group) else { waiting = group; askWrite.launch(Manifest.permission.WRITE_CALL_LOG) }
            }
            if (DialerRole.canBlock(context)) {
                MetroButton(stringResource(R.string.dialer_block), Modifier.fillMaxWidth().testTag("dialer:block")) { DialerRole.block(context, call.number); target = null }
            }
        }
    }
}

const val PAGE_HISTORY = 0
const val PAGE_FAVOURITES = 1
const val PAGE_CONTACTS = 2
const val PAGE_KEYPAD = 3

private const val MAX_NUMBER = 32
private const val HISTORY = 100
private const val DETAILS = 20
private const val DETAILS_SCAN = 1000
