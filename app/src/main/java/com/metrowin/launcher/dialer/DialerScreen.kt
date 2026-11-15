package com.metrowin.launcher.dialer

import com.metrowin.launcher.ui.components.cutoutSidesPadding
import com.metrowin.launcher.ui.components.WindowClass
import com.metrowin.launcher.ui.components.windowClass
import android.Manifest
import android.content.Intent
import android.telecom.PhoneAccountHandle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import com.metrowin.launcher.data.Favourite
import com.metrowin.launcher.data.LivePermissions
import com.metrowin.launcher.data.MissedCalls
import com.metrowin.launcher.data.PeopleReader
import com.metrowin.launcher.data.PhoneCalls
import com.metrowin.launcher.data.RecentCall
import com.metrowin.launcher.data.SettingsStore
import com.metrowin.launcher.data.SimChoice
import com.metrowin.launcher.data.SimPrefs
import com.metrowin.launcher.data.SimReader
import com.metrowin.launcher.data.SimRouting
import com.metrowin.launcher.data.SimUse
import com.metrowin.launcher.ui.components.SimPickDialog
import com.metrowin.launcher.messages.MessagesActivity
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.ui.actioncenter.MetroToast
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.apps.JumpList
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.ButtonRole
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.components.centeredContent
import com.metrowin.launcher.ui.components.sectionHeader
import com.metrowin.launcher.ui.components.sectionStarts
import com.metrowin.launcher.ui.components.topBarsPadding
import com.metrowin.launcher.ui.pages.FavouriteSquare
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.start.whenText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.pageBackground
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Work that must finish after a page is gone (the missed calls are marked seen when the history is left); it belongs to no screen. */
private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * The phone of Windows Phone: a pivot with the keypad, the address book (with the places the contacts come from), the favourites and the
 * history of calls. [number] is what the keypad starts with (a number someone asked the dialer to open). On a wide screen the keypad stays
 * on one side and the other three pages are on the other.
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
    val prefs = remember { PhonePrefs(context) }
    var hasContacts by remember { mutableStateOf(LivePermissions.isGranted(context, LivePermissions.contacts)) }
    var hasCallLog by remember { mutableStateOf(LivePermissions.isGranted(context, LivePermissions.calls)) }
    var canCall by remember { mutableStateOf(LivePermissions.isGranted(context, PhoneCalls.permission)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasContacts = result[LivePermissions.contacts] ?: hasContacts
        hasCallLog = result[LivePermissions.calls] ?: hasCallLog
        canCall = result[PhoneCalls.permission] ?: canCall
    }
    // Everything the dialer reads is asked for once, at the first visit; before the three questions of Android, one word on why.
    var explain by remember { mutableStateOf(false) }
    val missing = remember { listOf(LivePermissions.contacts, LivePermissions.calls, PhoneCalls.permission).filter { !LivePermissions.isGranted(context, it) } }
    LaunchedEffect(Unit) {
        if (missing.isNotEmpty()) if (prefs.explained) ask.launch(missing.toTypedArray()) else explain = true
    }
    val sims = remember(canCall) { DialerRole.sims(context) }
    var simFor by remember { mutableStateOf<String?>(null) }
    fun place(target: String, sim: PhoneAccountHandle?) {
        DialerRole.call(context, target, canCall, sim)
        onPlaced()
    }
    // Two SIMs: the one kept for this contact, else the default for calls, else the user is asked (unless asking is turned off).
    val simInfos = remember(canCall) { SimReader.read(context).filter { it.handle != null } }
    val simPrefs = remember { SimPrefs(context) }
    fun call(target: String, sim: PhoneAccountHandle? = null) {
        if (target.isEmpty()) return
        if (sim != null || simInfos.size < 2) { place(target, sim); return }
        when (val choice = SimRouting.choose(simInfos, simPrefs.forContact(SimUse.Call, target), simPrefs.default(SimUse.Call))) {
            is SimChoice.Use -> place(target, choice.sim.handle)
            SimChoice.Ask -> if (prefs.askSim) simFor = target else place(target, null)
            SimChoice.System -> place(target, null)
        }
    }
    fun voicemail() {
        DialerRole.voicemail(context)
        onPlaced()
    }
    // Back in the phone from another screen (the form of a new contact, the import): the lists are read again.
    var fresh by remember { mutableIntStateOf(0) }
    var started by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (started) fresh++ else started = true }
    // The pivot keeps the order of Windows Phone, but the phone opens on the page it was asked for (the keypad); the history is a swipe away.
    val titles = listOf(R.string.dialer_tab_history, R.string.dialer_tab_favourites, R.string.dialer_tab_contacts, R.string.dialer_tab_keypad).map { stringResource(it) }
    var dialogs by remember { mutableStateOf(Dialogs.None) }
    val scope = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val done = withContext(Dispatchers.IO) {
                runCatching {
                    val text = CallHistory.csv(PeopleReader(context.applicationContext).recentCalls(EXPORT))
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null
                }.getOrDefault(false)
            }
            MetroToast.show(context, if (done) R.string.dialer_export_done else R.string.dialer_export_failed)
        }
    }
    // The numbers on the keys 2 to 9 (long press), set from the history and shown on the keypad.
    var keys by remember { mutableStateOf(NumberBookStore(context).keys()) }
    BoxWithConstraints(modifier.fillMaxSize().pageBackground()) {
        val wide = maxWidth.windowClass == WindowClass.Expanded
        val pages = if (wide) 3 else 4
        val home = (if (wide && initialPage == PAGE_KEYPAD) PAGE_CONTACTS else initialPage).coerceIn(0, pages - 1)
        val pager = rememberPagerState(initialPage = home) { pages }
        // The missed calls are seen when the history is: the number on the Phone tile goes (and goes again when the history is left, for what came meanwhile).
        val onHistory = pager.currentPage == PAGE_HISTORY && hasCallLog
        DisposableEffect(onHistory) {
            if (onHistory) background.launch { MissedCalls.markSeen(context.applicationContext) }
            onDispose { if (onHistory) background.launch { MissedCalls.markSeen(context.applicationContext) } }
        }
        // Back from another page goes to the one the phone opened on; only from there it leaves.
        BackHandler(enabled = pager.currentPage != home) { scope.launch { pager.animateScrollToPage(home) } }
        Column(Modifier.fillMaxSize().topBarsPadding().navigationBarsPadding().cutoutSidesPadding().then(if (wide) Modifier else Modifier.centeredContent())) {
            PivotHeader(titles.take(pages), pager, Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp))
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (wide) Box(Modifier.weight(0.8f).fillMaxHeight().padding(horizontal = 24.dp)) { KeypadPage(number, onNumberChange, hasContacts, canCall, keys, prefs, ::call, ::voicemail, fresh) }
                HorizontalPager(pager, Modifier.weight(1f).fillMaxHeight(), beyondViewportPageCount = 1) { page ->
                    Box(
                        Modifier.fillMaxSize().padding(horizontal = 24.dp).then(if (prefs.pageFade && !LocalReduceMotion.current) Modifier.turning(pager, page) else Modifier),
                    ) {
                        when (page) {
                            PAGE_HISTORY -> HistoryPage(hasCallLog, keys, { keys = it }, { ask.launch(arrayOf(LivePermissions.calls)) }, prefs, sims, fresh, ::call, ::place)
                            PAGE_FAVOURITES -> FavouritesPage(hasContacts, { ask.launch(arrayOf(LivePermissions.contacts)) }, fresh, ::call)
                            PAGE_CONTACTS -> ContactsPage(hasContacts, { ask.launch(arrayOf(LivePermissions.contacts)) }, fresh, ::call)
                            else -> KeypadPage(number, onNumberChange, hasContacts, canCall, keys, prefs, ::call, ::voicemail, fresh)
                        }
                    }
                }
            }
            val shown = pager.currentPage
            AppBar(
                if (shown == PAGE_KEYPAD && !wide) {
                    listOf(
                        AppBarItem("◷", titles[PAGE_HISTORY]) { scope.launch { pager.animateScrollToPage(PAGE_HISTORY) } },
                        AppBarItem("⌕", titles[PAGE_CONTACTS]) { scope.launch { pager.animateScrollToPage(PAGE_CONTACTS) } },
                    )
                } else if (wide) {
                    listOf(AppBarItem("⌕", titles[PAGE_CONTACTS]) { scope.launch { pager.animateScrollToPage(PAGE_CONTACTS) } })
                } else {
                    listOf(
                        AppBarItem("#", titles[PAGE_KEYPAD]) { scope.launch { pager.animateScrollToPage(PAGE_KEYPAD) } },
                        AppBarItem("⌕", titles[PAGE_CONTACTS]) { scope.launch { pager.animateScrollToPage(PAGE_CONTACTS) } },
                    )
                },
                Modifier.testTag("dialer:bar"),
                menu = listOf(
                    AppBarItem("⚙", stringResource(R.string.dialer_options)) { dialogs = Dialogs.Options },
                    AppBarItem("☰", stringResource(R.string.dialer_stats_title)) { dialogs = Dialogs.Stats },
                    AppBarItem("⊘", stringResource(R.string.dialer_blocked_title)) { dialogs = Dialogs.Blocked },
                    AppBarItem("⇧", stringResource(R.string.dialer_export)) { export.launch("calls-" + LocalDate.now() + ".csv") },
                ),
            )
        }
        when (dialogs) {
            Dialogs.Options -> OptionsDialog(prefs, onTexts = { dialogs = Dialogs.Texts }, onDismiss = { dialogs = Dialogs.None })
            Dialogs.Texts -> DeclineTextsDialog(prefs) { dialogs = Dialogs.Options }
            Dialogs.Stats -> {
                val calls by produceState<List<RecentCall>>(emptyList()) { value = withContext(Dispatchers.IO) { PeopleReader(context.applicationContext).recentCalls(STATS_SCAN) } }
                StatsDialog(calls) { dialogs = Dialogs.None }
            }
            Dialogs.Blocked -> BlockedDialog { dialogs = Dialogs.None }
            Dialogs.None -> Unit
        }
        simFor?.let { target ->
            SimPickDialog(
                SimUse.Call, simInfos, SimRouting.key(target).isNotEmpty(),
                { sim, keep -> simFor = null; if (keep) simPrefs.setForContact(SimUse.Call, target, sim.slot); place(target, sim.handle) },
                { simFor = null },
            )
        }
        if (explain) {
            DialogFrame(onCancel = { explain = false; prefs.explained = true; ask.launch(missing.toTypedArray()) }, title = stringResource(R.string.dialer_rationale_title)) {
                PageNote(stringResource(R.string.dialer_rationale), Modifier.testTag("dialer:rationale"))
                MetroButton(stringResource(android.R.string.ok), Modifier.fillMaxWidth().testTag("dialer:rationale:ok")) { explain = false; prefs.explained = true; ask.launch(missing.toTypedArray()) }
            }
        }
    }
}

private enum class Dialogs { None, Options, Texts, Stats, Blocked }

/** The page fades and slides a little as the finger turns it, so the pages do not just slide one over the other. */
private fun Modifier.turning(pager: PagerState, page: Int): Modifier = graphicsLayer {
    val away = ((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(-1f, 1f)
    alpha = 1f - 0.6f * kotlin.math.abs(away)
    translationX = -away * size.width * 0.08f
}

@Composable
private fun KeypadPage(
    number: String, onNumberChange: (String) -> Unit, hasContacts: Boolean, canCall: Boolean, keys: Map<Char, String>, prefs: PhonePrefs,
    call: (String, PhoneAccountHandle?) -> Unit, voicemail: () -> Unit, fresh: Int,
) {
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
    // A long press on a key 2 to 9 calls its number, after one more touch unless the user has turned that off.
    var confirm by remember { mutableStateOf<String?>(null) }
    val names by produceState(emptyMap<String, String>(), hasContacts, keys, fresh) {
        value = if (hasContacts && keys.isNotEmpty()) withContext(Dispatchers.IO) { DialerContactReader(context.applicationContext).read().associate { it.number to it.name } } else emptyMap()
    }
    val ready = number.isNotEmpty()
    val scale = remember { Animatable(1f) }
    val dim by animateFloatAsState(if (ready) 1f else 0.4f, label = "call")
    // The green bar swells a little when there is a number to call.
    LaunchedEffect(ready) { if (ready) { scale.animateTo(1.04f); scale.animateTo(1f) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight && maxWidth.windowClass != WindowClass.Expanded
        val showCard = maxHeight >= 700.dp
        val notes = (if (needsFullScreen) 90 else 0) + (if (!isDefault && roleIntent != null) (if (showCard) 190 else 90) else 0)
        // The keys give way when the room is short: what is over them (number, suggestions, notes) and the green bar under them are taken off first.
        val reserved = 76 + suggestions.size * 32 + notes + 60 + 8 + 8 + 18 + (if (prefs.quickRow && keys.isNotEmpty()) 40 else 0)
        val keyHeight = ((maxHeight.value - reserved) / 4).coerceIn(40f, 64f).dp
        val top: @Composable () -> Unit = {
            if (needsFullScreen) {
                PageNote(stringResource(R.string.dialer_fullscreen_note))
                MetroButton(stringResource(R.string.dialer_fullscreen_button), Modifier.fillMaxWidth().padding(top = 6.dp).testTag("dialer:fullscreen")) {
                    runCatching { context.startActivity(DialerRole.fullScreenSettings(context)) }
                }
            }
            if (!isDefault && roleIntent != null) {
                if (showCard) {
                    PageNote(stringResource(R.string.dialer_onboard_title))
                    PageNote(stringResource(R.string.dialer_onboard_steps), Modifier.padding(top = 4.dp))
                } else {
                    PageNote(stringResource(R.string.dialer_default_note))
                }
                MetroButton(stringResource(R.string.dialer_default_button), Modifier.fillMaxWidth().padding(top = 6.dp).testTag("dialer:default")) { role.launch(roleIntent) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                MetroText(
                    if (number.isEmpty()) stringResource(R.string.dialer_number_hint) else prettyNumber(number), size = numberSize(number.length, prefs.bigText).sp, maxLines = 1, textAlign = TextAlign.Center,
                    color = if (number.isEmpty()) colors.subtle else colors.foreground, modifier = Modifier.weight(1f).testTag("dialer:number"),
                )
                val delete = stringResource(R.string.dialer_delete)
                BackspaceIcon(
                    colors.foreground,
                    modifier = Modifier.testTag("dialer:delete").semantics { contentDescription = delete }
                        .metroPress(onClick = { onNumberChange(number.dropLast(1)) }, onLongClick = { onNumberChange("") }).padding(12.dp).size(28.dp),
                )
            }
            if (prefs.quickRow && keys.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    keys.toSortedMap().forEach { (key, target) ->
                        PageChip("$key  ${names[target] ?: prettyNumber(target)}", false, "dialer:quick:$key") { call(target, null) }
                    }
                }
            }
            suggestions.forEach { entry ->
                Row(Modifier.fillMaxWidth().testTag("dialer:suggest").metroPress(onClick = { call(entry.number, null) }).padding(vertical = 5.dp)) {
                    MetroText(entry.name, size = 20.sp, modifier = Modifier.weight(1f))
                    MetroText(prettyNumber(entry.number), size = 16.sp, weight = FontWeight.Normal, color = colors.subtle)
                }
            }
        }
        val keyboard: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                DialPad(
                    onKey = { onNumberChange((number + it).take(MAX_NUMBER)) }, onVoicemail = voicemail, keyHeight = keyHeight, marked = keys.keys, tones = prefs.keyTones, big = prefs.bigText,
                    onLongKey = { key ->
                        keys[key]?.let { target -> if (prefs.confirmQuickDial) confirm = key.toString() else call(target, null) }
                    },
                )
                Row(Modifier.fillMaxWidth().graphicsLayer { scaleX = scale.value; scaleY = scale.value }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (sims.ifEmpty { listOf(null) }).forEachIndexed { index, sim ->
                        Box(
                            Modifier.weight(1f).height(60.dp).testTag(if (sim == null) "dialer:call" else "dialer:call:sim$index")
                                .metroPress(onClick = { call(number, sim) }, enabled = ready)
                                .background(CallGreen.copy(alpha = dim)),
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
        if (landscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { top() }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) { keyboard() }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f, fill = false)) { top() }
                keyboard()
            }
        }
    }
    confirm?.let { key ->
        val target = keys[key.single()].orEmpty()
        QuickDialConfirm(names[target].orEmpty(), target, onYes = { confirm = null; call(target, null) }, onDismiss = { confirm = null })
    }
}

@Composable
private fun ContactsPage(hasPermission: Boolean, onRequest: () -> Unit, fresh: Int, call: (String) -> Unit) {
    val context = LocalContext.current
    val store = remember { SettingsStore.of(context.applicationContext) }
    var mask by remember { mutableIntStateOf(store.load().dialerSources) }
    var query by rememberSaveable { mutableStateOf("") }
    var choosing by remember { mutableStateOf<DialerContact?>(null) }
    val vcfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching { context.startActivity(ContactImporter.createVcfImportIntent(uri)) }
        }
    }
    val contacts by produceState<List<DialerContact>?>(null, hasPermission, fresh) {
        value = if (hasPermission) withContext(Dispatchers.IO) { DialerContactReader(context.applicationContext).read() } else emptyList()
    }
    if (!hasPermission) {
        Column { PageNote(stringResource(R.string.page_needs_contacts)); MetroButton(stringResource(R.string.page_allow), Modifier.fillMaxWidth().padding(top = 8.dp), onClick = onRequest) }
        return
    }
    val shown = contacts.orEmpty().filter { it.shownIn(mask) && (query.isBlank() || it.name.contains(query.trim(), ignoreCase = true)) }
    // The name is never blank (the reader leaves those out), but the letter is taken without assuming it.
    val groups = shown.groupBy { it.name.firstOrNull()?.uppercaseChar() ?: '#' }
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
        if (contacts != null && shown.isEmpty()) item { EmptyState(Glyph.People, stringResource(R.string.dialer_no_contacts)) }
        groups.forEach { (letter, people) ->
            sectionHeader("h$letter", onClick = { jumping = true }) { MetroText(letter.toString(), size = 26.sp, color = LocalAccent.current, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)) }
            items(people, key = { it.lookupUri }) { person ->
                Row(
                    Modifier.fillMaxWidth().testTag("dialer:contact").metroPress(
                        onClick = { if (person.numbers.size > 1) choosing = person else call(person.number) },
                        onLongClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, person.lookupUri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                    ).padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ContactAvatar(person.name, Modifier.padding(end = 12.dp), photoUri = person.photoUri, size = 44.dp)
                    Column {
                        MetroText(person.name, size = 22.sp)
                        MetroText(
                            prettyNumber(person.number) + if (person.numbers.size > 1) "  +${person.numbers.size - 1}" else "", size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle,
                        )
                    }
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
    choosing?.let { person -> NumberChooser(person.name, person.numbers, { target -> choosing = null; call(target) }, { choosing = null }) }
}

@Composable
private fun FavouritesPage(hasPermission: Boolean, onRequest: () -> Unit, fresh: Int, call: (String) -> Unit) {
    val context = LocalContext.current
    val favourites by produceState<List<Favourite>?>(null, hasPermission, fresh) {
        value = if (hasPermission) withContext(Dispatchers.IO) { PeopleReader(context.applicationContext).favourites() } else emptyList()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        when {
            !hasPermission -> { PageNote(stringResource(R.string.page_needs_contacts)); MetroButton(stringResource(R.string.page_allow), Modifier.fillMaxWidth().padding(top = 8.dp), onClick = onRequest) }
            favourites == null -> PageNote(stringResource(R.string.dialer_loading), Modifier.testTag("dialer:loading"))
            favourites!!.isEmpty() -> EmptyState(Glyph.Star, stringResource(R.string.dialgrid_empty), Modifier.testTag("dialer:nofavourites"))
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

/** The label of a day of the history: today, yesterday, or the date. */
@Composable
private fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> stringResource(R.string.dialer_today)
        today.minusDays(1) -> stringResource(R.string.dialer_yesterday)
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

/** What a row of the history is keyed on: the row of the log when it is known, else the moment. A key is kept, so a row keeps its state when the list changes. */
private fun keyOf(group: CallGroup): Any = group.latest.id.takeIf { it > 0 } ?: (group.latest.date to group.latest.number)

@Composable
private fun HistoryPage(
    hasPermission: Boolean, keys: Map<Char, String>, onKeys: (Map<Char, String>) -> Unit, onRequest: () -> Unit, prefs: PhonePrefs,
    sims: List<PhoneAccountHandle>, fresh: Int, call: (String) -> Unit, place: (String, PhoneAccountHandle?) -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalMetroColors.current
    val scope = rememberCoroutineScope()
    val book = remember { NumberBookStore(context) }
    var notes by remember { mutableStateOf(book.notes()) }
    var reload by remember { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var limit by rememberSaveable { mutableIntStateOf(HISTORY) }
    var target by remember { mutableStateOf<CallGroup?>(null) }
    var selected by remember { mutableStateOf(emptySet<Any>()) }
    val calls by produceState<List<RecentCall>?>(null, hasPermission, reload, limit, fresh) {
        value = if (hasPermission) withContext(Dispatchers.IO) { PeopleReader(context.applicationContext).recentCalls(limit) } else emptyList()
    }
    // Taking calls out of the log needs the permission to write it, which is asked for the first time it is wanted.
    var waiting by remember { mutableStateOf<List<CallGroup>?>(null) }
    fun remove(groups: List<CallGroup>) {
        scope.launch { withContext(Dispatchers.IO) { PeopleReader(context.applicationContext).let { reader -> groups.forEach { g -> g.members.forEach(reader::deleteCall) } } }; reload++ }
    }
    val askWrite = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) waiting?.let(::remove); waiting = null }
    fun removeAsked(groups: List<CallGroup>) {
        if (LivePermissions.isGranted(context, Manifest.permission.WRITE_CALL_LOG)) remove(groups) else { waiting = groups; askWrite.launch(Manifest.permission.WRITE_CALL_LOG) }
    }
    if (!hasPermission) {
        Column { PageNote(stringResource(R.string.page_needs_calls)); MetroButton(stringResource(R.string.page_allow), Modifier.fillMaxWidth().padding(top = 8.dp), onClick = onRequest) }
        return
    }
    val which = HistoryFilter.entries[filter]
    val shown = calls?.filter { which.matches(it) }?.let(NumberBook::group)
    val selecting = selected.isNotEmpty()
    BackHandler(enabled = selecting) { selected = emptySet() }
    val listState = rememberLazyListState()
    // The end of the list was reached and the log has more: the next hundred are read.
    val atEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= listState.layoutInfo.totalItemsCount - 2 } == true } }
    LaunchedEffect(atEnd, calls?.size) { if (atEnd && (calls?.size ?: 0) >= limit) limit += HISTORY }
    // The filter chips sit above the list, not over it.
    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MetroText(stringResource(R.string.dialer_selected, selected.size), size = 18.sp, weight = FontWeight.Normal, modifier = Modifier.weight(1f).testTag("dialer:selected"))
                PageChip(stringResource(R.string.dialer_delete_selected), false, "dialer:delete_selected") {
                    val chosen = shown.orEmpty().filter { keyOf(it) in selected }
                    selected = emptySet()
                    removeAsked(chosen)
                }
                PageChip(stringResource(android.R.string.cancel), false, "dialer:cancel_selection") { selected = emptySet() }
            }
        } else {
            Row(Modifier.padding(bottom = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PageChip(stringResource(R.string.dialer_filter_all), which == HistoryFilter.All, "dialer:filter:all") { filter = 0 }
                PageChip(stringResource(R.string.dialer_filter_missed), which == HistoryFilter.Missed, "dialer:filter:missed") { filter = 1 }
                PageChip(stringResource(R.string.dialer_filter_received), which == HistoryFilter.Received, "dialer:filter:received") { filter = 2 }
                PageChip(stringResource(R.string.dialer_filter_sent), which == HistoryFilter.Sent, "dialer:filter:sent") { filter = 3 }
            }
        }
        if (shown?.isEmpty() == true) EmptyState(Glyph.Phone, stringResource(R.string.dialer_no_history), Modifier.testTag("dialer:nohistory"))
        LazyColumn(Modifier.weight(1f).testTag("dialer:history"), state = listState) {
            val row: @Composable (CallGroup) -> Unit = { group -> HistoryRow(group, notes, prefs, sims.size > 1, group.let { keyOf(it) } in selected, selecting,
                onClick = { if (selecting) selected = if (keyOf(group) in selected) selected - keyOf(group) else selected + keyOf(group) else call(group.latest.number) },
                onLongClick = { if (prefs.multiSelect && !selecting) selected = setOf(keyOf(group)) else if (selecting) selected = selected + keyOf(group) else target = group },
            ) }
            if (prefs.dayHeaders) {
                CallHistory.byDay(shown.orEmpty()).forEach { (day, groups) ->
                    sectionHeader("d$day") { MetroText(dayLabel(day), size = 16.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.padding(vertical = 4.dp)) }
                    items(groups, key = { keyOf(it) }) { row(it) }
                }
            } else {
                items(shown.orEmpty(), key = { keyOf(it) }) { row(it) }
            }
        }
    }
    // Last, so it is drawn over the list.
    target?.let { group ->
        val latest = group.latest
        val clean = PhoneCalls.clean(latest.number)
        // The last 20 calls of this number, whatever the kind, read from the log with a filter on the number (not the whole log scanned each time).
        val history by produceState<List<RecentCall>>(emptyList(), clean) {
            value = withContext(Dispatchers.IO) { PeopleReader(context.applicationContext).callsOf(latest.number, DETAILS) }
        }
        DialogFrame(onCancel = { target = null }) {
            MetroText(latest.name, size = 22.sp, maxLines = 1)
            if (latest.name != latest.number) MetroText(prettyNumber(latest.number), size = 14.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1)
            MetroTextField(
                notes[clean].orEmpty(),
                { text -> notes = NumberBook.withNote(notes, latest.number, text.take(NumberBook.MAX_NOTE)).also(book::saveNotes) },
                stringResource(R.string.dialer_note_hint), Modifier.testTag("dialer:note"),
            )
            history.forEach { item ->
                Row(Modifier.fillMaxWidth().testTag("dialer:detail"), verticalAlignment = Alignment.CenterVertically) {
                    CallKindIcon(kindOf(item), if (item.missed) CallRed else colors.subtle, 16.dp, Modifier.padding(end = 12.dp))
                    MetroText(whenText(item.date), size = 14.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.weight(1f))
                    if (item.duration > 0) MetroText(clock(item.duration), size = 14.sp, weight = FontWeight.Normal, color = colors.subtle)
                }
            }
            SectionTitle(stringResource(R.string.dialer_section_contact))
            // With two SIMs, one call button for each; with one, the phone chooses.
            if (sims.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sims.forEachIndexed { index, sim ->
                        MetroButton(DialerRole.simLabel(context, sim).ifBlank { "SIM ${index + 1}" }, Modifier.weight(1f).testTag("dialer:detailcall:$index")) { target = null; place(latest.number, sim) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetroButton(stringResource(R.string.dialer_send_sms), Modifier.weight(1f).testTag("dialer:sms")) {
                    target = null
                    runCatching { context.startActivity(Intent(context, MessagesActivity::class.java).putExtra(MessagesActivity.EXTRA_ADDRESS, latest.number).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                // A caller the contacts do not know is shown by its number.
                if (latest.name == latest.number) {
                    MetroButton(stringResource(R.string.dialer_add_contact), Modifier.weight(1f).testTag("dialer:add_known")) {
                        target = null
                        runCatching { context.startActivity(ContactImporter.createInsertIntent(number = latest.number)) }
                    }
                }
            }
            SectionTitle(stringResource(R.string.dialer_section_quick))
            PageNote(stringResource(R.string.dialer_assign_key))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberBook.KEYS.forEach { key ->
                    val mine = keys[key] == clean
                    PageChip(key.toString(), mine, "dialer:assign:$key") { onKeys(NumberBook.withKey(keys, key, if (mine) "" else latest.number).also(book::saveKeys)) }
                }
            }
            SectionTitle(stringResource(R.string.dialer_section_manage))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetroButton(stringResource(R.string.dialer_delete_call), Modifier.weight(1f).testTag("dialer:delete_call"), role = ButtonRole.Danger) { target = null; removeAsked(listOf(group)) }
                if (DialerRole.canBlock(context)) {
                    MetroButton(stringResource(R.string.dialer_block), Modifier.weight(1f).testTag("dialer:block")) { DialerRole.block(context, latest.number); target = null }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    MetroText(text, size = 14.sp, weight = FontWeight.SemiBold, color = LocalAccent.current, modifier = Modifier.padding(top = 6.dp))
}

private fun kindOf(call: RecentCall) = if (call.missed) CallKind.Missed else if (call.outgoing) CallKind.Outgoing else CallKind.Incoming

private fun clock(seconds: Int) = if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)

/** One row of the history: the picture, who, what kind of call and when, how long, which SIM; a missed call is in red. */
@Composable
private fun HistoryRow(
    group: CallGroup, notes: Map<String, String>, prefs: PhonePrefs, twoSims: Boolean, selected: Boolean, selecting: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit,
) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val item = group.latest
    val tint = if (item.missed) CallRed else colors.subtle
    val rail = colors.subtle.copy(alpha = 0.5f)
    Row(
        Modifier.fillMaxWidth().testTag("dialer:call:${item.date}").metroPress(onClick = onClick, onLongClick = onLongClick)
            .then(if (selected) Modifier.background(accent.copy(alpha = 0.25f)) else Modifier)
            .then(
                if (prefs.timeline) Modifier.drawBehind {
                    val x = 8.dp.toPx()
                    drawLine(rail, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                    drawCircle(if (item.missed) CallRed else accent, 5.dp.toPx(), Offset(x, size.height / 2))
                } else Modifier,
            )
            .padding(start = if (prefs.timeline) 24.dp else 0.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.padding(end = 12.dp)) {
            ContactAvatar(item.name, photoUri = null, number = item.number, size = 44.dp)
            if (selected) MetroText("✓", size = 18.sp, color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetroText(if (item.name == item.number) prettyNumber(item.number) else item.name, size = 21.sp, color = if (item.missed) CallRed else colors.foreground, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (group.count > 1) {
                    MetroText(
                        group.count.toString(), size = 13.sp, weight = FontWeight.SemiBold, color = Color.White,
                        modifier = Modifier.padding(start = 8.dp).background(tint).padding(horizontal = 6.dp, vertical = 1.dp).testTag("dialer:count"),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                CallKindIcon(kindOf(item), tint, 16.dp, Modifier.padding(end = 6.dp))
                MetroText(
                    (if (item.name == item.number) "" else prettyNumber(item.number) + " · ") + whenText(item.date), size = 13.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.duration > 0) MetroText(" · " + clock(item.duration), size = 13.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1)
                if (twoSims) item.accountId?.let { id -> DialerRole.simIndex(LocalContext.current, id) }?.let { slot ->
                    MetroText(" · SIM $slot", size = 13.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1)
                }
            }
            notes[PhoneCalls.clean(item.number)]?.let { MetroText(it, size = 13.sp, weight = FontWeight.Normal, color = accent, maxLines = 1) }
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
private const val EXPORT = 5000
private const val STATS_SCAN = 2000
