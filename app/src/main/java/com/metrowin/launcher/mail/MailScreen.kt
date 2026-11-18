package com.metrowin.launcher.mail

import com.metrowin.launcher.ui.components.isWide
import androidx.compose.foundation.layout.PaddingValues
import com.metrowin.launcher.ui.mini.MiniFrame
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import com.metrowin.launcher.ui.components.pullToRefresh
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.actioncenter.MetroToast
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.anim.rememberRowSwivel
import com.metrowin.launcher.ui.anim.swivel
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.RetryNote
import com.metrowin.launcher.ui.components.MetroProgressBar
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.SwipeRow
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.components.SelectableRow
import com.metrowin.launcher.ui.components.centeredContent
import com.metrowin.launcher.ui.components.featherRow
import com.metrowin.launcher.ui.components.scrollIndicator
import com.metrowin.launcher.ui.components.LocalBottomBarsPadded
import com.metrowin.launcher.ui.components.navigationBottomPaddingWithButtons
import com.metrowin.launcher.ui.components.topBarsPadding
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.components.PageSwitch
import com.metrowin.launcher.ui.theme.pageBackground
import com.metrowin.launcher.ui.theme.panel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.mail.Flags

private const val INBOX = "INBOX"

/** How long a swiped message can still be taken back: as long as the banner with "undo" stays. */
private const val SWIPE_UNDO_MS = 5_000L

/** The files already fetched from the server, so that the little picture of an attachment and the touch that opens it share one download. */
private object AttachmentCache {
    private val cache = object : LruCache<String, Triple<String, String, ByteArray>>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Triple<String, String, ByteArray>) = value.third.size.coerceAtLeast(1)
    }

    fun get(key: String): Triple<String, String, ByteArray>? = synchronized(cache) { cache.get(key) }

    fun put(key: String, file: Triple<String, String, ByteArray>) = synchronized(cache) { cache.put(key, file); Unit }
}

/** A message kept through a recreation of the screen: its fields, in order. */
private val MessageSaver = listSaver<MailMessage?, Any>(
    save = { m -> m?.let { listOf(it.id, it.accountId, it.senderName, it.senderAddress, it.subject, it.snippet, it.timestamp, it.isUnread, it.isFlagged, it.hasAttachments, it.messageId, it.refs) } ?: emptyList() },
    restore = { v ->
        if (v.isEmpty()) null else MailMessage(
            v[0] as String, v[1] as String, v[2] as String, v[3] as String, v[4] as String, v[5] as String, v[6] as Long, v[7] as Boolean, v[8] as Boolean, "", v[9] as Boolean,
            v[10] as String, v[11] as String,
        )
    },
)

@Composable
fun MailScreen(modifier: Modifier = Modifier, link: MailLink? = null, onLinkUsed: () -> Unit = {}) {
    val context = LocalContext.current
    val repository = remember { MailRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var optionsVersion by remember { mutableIntStateOf(0) }
    val look = remember(optionsVersion) { MailLook.load(context) }
    var showOptions by rememberSaveable { mutableStateOf(false) }
    var accounts by remember { mutableStateOf(repository.loadAccounts()) }
    var accountId by rememberSaveable { mutableStateOf<String?>(null) }
    val account = accounts.firstOrNull { it.id == accountId } ?: accounts.firstOrNull()
    var folder by rememberSaveable { mutableStateOf(INBOX) }
    var reload by remember { mutableIntStateOf(0) }
    // One list for each of the three tabs: the unread and the flagged ones are asked of the server, not cut out of the 50 of the inbox.
    val lists = remember { mutableStateMapOf<MailFilter, List<MailMessage>>() }
    val limits = remember { mutableStateMapOf<MailFilter, Int>() }
    val listStates = remember { MailFilter.entries.associateWith { LazyListState() } }
    var filter by rememberSaveable { mutableStateOf(MailFilter.All) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by rememberSaveable(stateSaver = MessageSaver) { mutableStateOf<MailMessage?>(null) }
    var lastOpen by remember { mutableStateOf<MailMessage?>(null) }
    var body by remember { mutableStateOf<MailBody?>(null) }
    var bodyFailure by remember { mutableStateOf<String?>(null) }
    var composing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable(stateSaver = Draft.Saver) { mutableStateOf(Draft()) }
    // A new message is kept as it is written (encrypted) so that Back, or the phone, does not lose it; an answer is not.
    var savesDraft by rememberSaveable { mutableStateOf(false) }
    var files by remember { mutableStateOf(emptyList<Outgoing>()) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var threads by rememberSaveable { mutableStateOf(false) }
    // The server is being asked: five dots run under the title.
    var syncing by remember { mutableStateOf(false) }
    // The messages ticked with a long press, by id.
    var picked by remember { mutableStateOf(emptySet<String>()) }
    var unreadCount by remember { mutableIntStateOf(0) }
    // Ids waiting for the user's word: this folder has no trash, so deleting them is for good.
    var confirmDelete by remember { mutableStateOf<List<String>?>(null) }
    // Ids to move, while the user picks the folder.
    var moving by remember { mutableStateOf<List<String>?>(null) }
    val pager = rememberPagerState { 5 }
    // The login to the server is the one already made (see MailClients: the activity makes it while the screen is drawn), and stays for a few minutes after the screen is left.
    val client = remember(account?.id) { account?.let { MailClients.of(it, repository) } }
    DisposableEffect(Unit) { onDispose { MailClients.release() } }
    // The mail is on screen: the notification of the account is no use now. A message still waiting in the outbox is tried again.
    LaunchedEffect(account?.id) {
        account?.let { MailSync.dismiss(context, it.id) }
        if (withContext(Dispatchers.IO) { MailOutbox.waiting(context) } > 0) MailOutbox.schedule(context)
    }
    LaunchedEffect(pager.currentPage) { filter = when (pager.currentPage) { 1 -> MailFilter.Unseen; 2 -> MailFilter.Flagged; 0 -> MailFilter.All; else -> filter } }
    val folderList by produceState<List<MailFolder>?>(null, client, reload) {
        value = if (client == null) emptyList() else io { client.folders() }.getOrDefault(emptyList())
    }

    fun resetLists() { lists.clear(); limits.clear() }

    // What the Mail tile counts is what the inbox has unread, so it follows what is read here.
    suspend fun recount() {
        val c = client ?: return
        val a = account ?: return
        io { c.unread(INBOX) }.onSuccess { MailSync.setUnread(context, a.id, it); unreadCount = it }
    }

    fun tellFailure(e: Throwable) {
        MetroToast.show(context, MailErrors.text(context, e))
        // The list showed what the server did not do: it asks again and shows what is there.
        reload++
    }

    /** Changes every list at once (a message is in the inbox list and in the unread one). */
    fun mutate(change: (List<MailMessage>) -> List<MailMessage>) = lists.keys.toList().forEach { k -> lists[k] = change(lists[k].orEmpty()) }

    /** Does [change] on the server to each of [ids]; one that fails is said and the list is asked again. */
    fun onServer(ids: List<String>, change: (MailClient, Long) -> Unit) {
        val c = client ?: return
        scope.launch {
            var failed: Throwable? = null
            ids.forEach { id -> io { change(c, id.toLong()) }.onFailure { failed = it } }
            failed?.let(::tellFailure)
            recount()
        }
    }

    fun deleteIds(ids: List<String>, permanent: Boolean = false) {
        val c = client ?: return
        scope.launch {
            // A folder with no trash would delete for good: nothing is touched before the user says so.
            if (!permanent && !io { c.hasTrash(folder) }.getOrDefault(true)) {
                confirmDelete = ids
                return@launch
            }
            mutate { all -> all.filter { it.id !in ids } }
            var failed: Throwable? = null
            ids.forEach { id -> io { c.delete(folder, id.toLong(), permanent) }.onFailure { failed = it } }
            failed?.let(::tellFailure)
            recount()
        }
    }

    fun moveIds(ids: List<String>, target: String?, role: String?) {
        mutate { all -> all.filter { it.id !in ids } }
        onServer(ids) { c, id -> c.move(folder, id, target, role) }
    }

    /** A message swiped away: it leaves the list at once, the server is asked after the time to take it back ("undo") has passed; leaving Mail meanwhile does not lose the swipe. */
    fun swipeAway(message: MailMessage, archive: Boolean) {
        val c = client ?: return
        val from = folder
        scope.launch {
            // A folder with no trash would delete for good: that is asked as always, with the row still in place.
            if (!archive && !io { c.hasTrash(from) }.getOrDefault(true)) {
                confirmDelete = listOf(message.id)
                return@launch
            }
            mutate { all -> all.filter { it.id != message.id } }
            var undone = false
            MetroToast.show(context, context.getString(if (archive) R.string.mail_swiped_archive else R.string.mail_swiped_delete), context.getString(R.string.undo)) {
                undone = true
                reload++
            }
            try {
                delay(SWIPE_UNDO_MS)
            } finally {
                if (!undone) {
                    withContext(NonCancellable) {
                        val done = io { if (archive) c.move(from, message.id.toLong(), null, "\\Archive") else c.delete(from, message.id.toLong(), false) }
                        done.onFailure { tellFailure(it) }
                        recount()
                    }
                }
            }
        }
    }

    fun setFlagged(ids: List<String>, on: Boolean) {
        mutate { all -> all.map { if (it.id in ids) it.copy(isFlagged = on) else it } }
        selected = selected?.takeIf { it.id in ids }?.copy(isFlagged = on) ?: selected
        onServer(ids) { c, id -> c.setFlag(folder, id, Flags.Flag.FLAGGED, on) }
    }

    fun setUnreadIds(ids: List<String>) {
        mutate { all -> all.map { if (it.id in ids) it.copy(isUnread = true) else it } }
        onServer(ids) { c, id -> c.setFlag(folder, id, Flags.Flag.SEEN, false) }
    }

    // The messages follow the account, the folder, the search, the tab and the refresh button; more pages are asked by [loadMore], not by this.
    LaunchedEffect(account?.id, folder, reload, query, filter) {
        val c = client ?: return@LaunchedEffect
        val a = account ?: return@LaunchedEffect
        error = null
        syncing = true
        val serverSearch = query.isNotBlank()
        // Typing a search waits for a pause before the server is asked.
        if (serverSearch) delay(400)
        // What was seen last time is shown at once (read off the main thread: it is decrypted), and the server's answer replaces it.
        if (filter == MailFilter.All && lists[filter] == null && !serverSearch) withContext(Dispatchers.IO) { repository.cached(a.id, folder) }?.let { lists[filter] = it }
        if (!serverSearch) limits[filter] = MailClient.LIMIT
        val kept = lists[filter]
        val wanted = limits[filter] ?: MailClient.LIMIT
        io { MailListing.firstPage(c, folder, wanted, query, kept, filter) }
            .onSuccess { fresh ->
                lists[filter] = fresh
                // The list is there: the bar of dots stops here, and what is left (lines of text, the count) fills in behind it.
                syncing = false
                io { MailListing.lines(c, folder, fresh) }.onSuccess { lines -> lists[filter]?.let { lists[filter] = MailListing.withLines(it, lines) } }
                if (!serverSearch && filter == MailFilter.All) lists[filter]?.let { shown -> withContext(Dispatchers.IO) { repository.cache(a.id, folder, shown.take(MailClient.LIMIT)) } }
                recount()
            }
            .onFailure { error = MailErrors.text(context, it); if (lists[filter] == null) lists[filter] = emptyList(); c.close() }
        syncing = false
    }

    // A notification opened the mail: its account first, then its message as soon as the list has it.
    LaunchedEffect(link) { link?.account?.takeIf { id -> accounts.any { it.id == id } }?.let { accountId = it; folder = INBOX; picked = emptySet() } }
    val inbox = lists[MailFilter.All]
    LaunchedEffect(link, inbox, account?.id) {
        val wanted = link ?: return@LaunchedEffect
        if (wanted.account != null && wanted.account != account?.id) return@LaunchedEffect
        if (wanted.uid == null) { onLinkUsed(); return@LaunchedEffect }
        val found = inbox?.firstOrNull { it.id == wanted.uid } ?: return@LaunchedEffect
        selected = found
        onLinkUsed()
    }

    // An open message is fetched when it is chosen, and again when the screen comes back to it after being recreated.
    LaunchedEffect(selected?.id, folder, account?.id) {
        val m = selected ?: return@LaunchedEffect
        lastOpen = m
        val c = client ?: return@LaunchedEffect
        body = null
        bodyFailure = null
        io { c.body(folder, m.id.toLong()) }.onSuccess { body = it }.onFailure { bodyFailure = MailErrors.text(context, it) }
        recount()
    }

    // The new message that is being written is kept while it is written.
    LaunchedEffect(draft, savesDraft, composing) {
        if (!composing || !savesDraft) return@LaunchedEffect
        delay(600)
        val blank = draft.copy(text = draft.text.takeIf { it.trim() != MailOptions.signature(context).let { s -> if (s.isBlank()) "" else "-- \n$s" }.trim() } ?: "").isBlank()
        withContext(Dispatchers.IO) { if (blank) MailDrafts.clear(context) else MailDrafts.save(context, draft) }
    }

    /** The next page: only the older headers are asked of the server and added below, the ones shown are not fetched again. */
    fun loadMore() {
        val c = client ?: return
        val f = filter
        if (query.isNotBlank()) {
            limits[f] = (limits[f] ?: MailClient.LIMIT) + MailClient.LIMIT
            reload++
            return
        }
        val have = lists[f].orEmpty()
        syncing = true
        scope.launch {
            io { MailListing.morePage(c, folder, have, f) }
                .onSuccess { all ->
                    lists[f] = all
                    limits[f] = (limits[f] ?: MailClient.LIMIT) + MailClient.LIMIT
                    io { MailListing.lines(c, folder, all.drop(have.size)) }.onSuccess { lines -> lists[f]?.let { lists[f] = MailListing.withLines(it, lines) } }
                }
                .onFailure { error = MailErrors.text(context, it) }
            syncing = false
        }
    }

    if (account == null) {
        MiniFrame(modifier, contentPadding = PaddingValues(24.dp)) {
            OnboardingAccountSetup(onAccountAdded = { added, password ->
                repository.saveAccount(added, password)
                accounts = repository.loadAccounts()
                MailSync.schedule(context)
                resetLists()
            })
        }
        return
    }

    val wide = look.twoPanes && isWide()
    val titles = listOf(R.string.mail_tab_inbox, R.string.mail_tab_unread, R.string.mail_tab_flagged, R.string.mail_tab_folders, R.string.mail_tab_accounts)
        .map { stringResource(it) }.mapIndexed { i, t -> if (i == 1 && unreadCount > 0) "$t $unreadCount" else t }
    fun folderName(path: String) = if (path == INBOX) context.getString(R.string.mail_folder_inbox) else folderList?.firstOrNull { it.path == path }?.name ?: path

    fun fetchAttachment(open: MailMessage, index: Int): suspend () -> Result<Triple<String, String, ByteArray>> = {
        val key = "${account.id}/$folder/${open.id}/$index"
        AttachmentCache.get(key)?.let { Result.success(it) } ?: client?.let { c -> io { c.attachment(folder, open.id.toLong(), index) }.onSuccess { AttachmentCache.put(key, it) } }
            ?: Result.failure(IllegalStateException("no password"))
    }

    fun signatureBlock() = MailOptions.signature(context).let { if (it.isBlank()) "" else "\n\n-- \n$it" }

    fun answer(open: MailMessage, all: Boolean, forward: Boolean) {
        val opened = body
        val who = open.senderName.ifBlank { open.senderAddress }
        val text = opened?.text.orEmpty()
        val own = account.email
        val cc = if (all && opened != null) {
            (opened.to + opened.cc).map { it.address }.filter { !it.equals(own, true) && !it.equals(open.senderAddress, true) }.distinct().joinToString(", ")
        } else {
            ""
        }
        files = emptyList()
        savesDraft = false
        draft = if (forward) {
            Draft(subject = MailThreads.forward(open.subject), text = signatureBlock() + "\n\n---------- $who <${open.senderAddress}> ----------\n${open.subject}\n\n$text")
        } else {
            Draft(
                to = opened?.replyTo ?: open.senderAddress, cc = cc, subject = MailThreads.reply(open.subject),
                text = signatureBlock() + "\n\n$who:\n" + text.lines().joinToString("\n") { "> $it" },
                inReplyTo = opened?.messageId.orEmpty(), references = opened?.references.orEmpty(),
            )
        }
        // A forwarded message takes its files with it.
        if (forward && opened != null && opened.attachments.isNotEmpty()) scope.launch {
            val got = ArrayList<Outgoing>()
            var total = 0L
            for (file in opened.attachments) {
                val fetched = fetchAttachment(open, file.index)().getOrNull() ?: continue
                total += fetched.third.size
                if (total > MAX_ATTACHED) { MetroToast.show(context, R.string.mail_too_big); break }
                got += fetched
            }
            files = got
        }
        selected = null
        composing = true
    }

    fun read(message: MailMessage) {
        selected = message
        body = null
        bodyFailure = null
        mutate { all -> all.map { if (it.id == message.id) it.copy(isUnread = false) else it } }
    }

    fun openNew() {
        scope.launch {
            val kept = withContext(Dispatchers.IO) { MailDrafts.load(context) }
            files = emptyList()
            draft = kept ?: Draft(text = signatureBlock())
            savesDraft = true
            composing = true
        }
    }

    fun readerActions(open: MailMessage, hasArchive: Boolean, hasSpam: Boolean) = ReaderActions(
        onBack = { selected = null },
        onReply = { answer(open, all = false, forward = false) },
        onReplyAll = { answer(open, all = true, forward = false) },
        onForward = { answer(open, all = false, forward = true) },
        onDelete = { selected = null; deleteIds(listOf(open.id)) },
        onFlag = { setFlagged(listOf(open.id), !open.isFlagged) },
        onUnread = { selected = null; setUnreadIds(listOf(open.id)) },
        fetchAttachment = { index -> fetchAttachment(open, index)() },
        onArchive = if (look.move && hasArchive) ({ selected = null; moveIds(listOf(open.id), null, "\\Archive") }) else null,
        onSpam = if (look.move && hasSpam) ({ selected = null; moveIds(listOf(open.id), null, "\\Junk") }) else null,
        onMove = if (look.move) ({ moving = listOf(open.id) }) else null,
    )

    val hasArchive = folderList?.any { it.role == "\\Archive" || it.role == "\\All" } == true
    val hasSpam = folderList?.any { it.role == "\\Junk" } == true

    BackHandler(enabled = selected != null || composing || showOptions) {
        when {
            showOptions -> showOptions = false
            composing -> composing = false
            else -> selected = null
        }
    }
    // Back goes one step back inside the app: the search closes, another folder gives way to the inbox, another tab to the first one.
    BackHandler(enabled = !showOptions && !composing && selected == null && (picked.isNotEmpty() || searching || folder != INBOX || pager.currentPage != 0)) {
        when {
            picked.isNotEmpty() -> picked = emptySet()
            searching -> { searching = false; query = "" }
            pager.currentPage != 0 -> scope.launch { pager.animateScrollToPage(0) }
            else -> { folder = INBOX; resetLists() }
        }
    }

    val listPane: @Composable (Modifier) -> Unit = { m ->
        MailListScreen(
            modifier = m, account = account, accounts = accounts, folderTitle = if (folder == INBOX) null else folderName(folder), titles = titles, pager = pager,
            lists = lists, limits = limits, listStates = listStates, look = look, threads = threads, picked = picked, syncing = syncing, error = error,
            searching = searching, query = query, onQuery = { query = it }, folders = folderList, current = folder, selectedId = if (wide) selected?.id else null,
            onRead = ::read,
            onPick = { message -> picked = if (message.id in picked) picked - message.id else picked + message.id },
            onMore = ::loadMore,
            onRefresh = { reload++ },
            onSwipe = if (look.swipe) ::swipeAway else null, canArchive = look.move && hasArchive,
            onFolder = { path -> folder = path; resetLists(); scope.launch { pager.animateScrollToPage(0) } },
            onSelectAccount = { id -> accountId = id; resetLists(); folder = INBOX; error = null; scope.launch { pager.animateScrollToPage(0) } },
            onAddAccount = { added, password ->
                repository.saveAccount(added, password)
                accounts = repository.loadAccounts()
                MailSync.schedule(context)
                if (accounts.size == 1) resetLists()
            },
            onRemoveAccount = { id ->
                repository.removeAccount(id)
                MailClients.drop(id)
                MailSync.forget(context, id)
                accounts = repository.loadAccounts()
                MailSync.schedule(context)
                resetLists()
                folder = INBOX
            },
        )
    }

    val listBar: @Composable () -> Unit = {
        if (picked.isNotEmpty()) {
            val ids = picked
            AppBar(
                listOf(
                    AppBarItem("✕", stringResource(R.string.mail_delete)) { picked = emptySet(); deleteIds(ids.toList()) },
                    AppBarItem("⚑", stringResource(R.string.mail_flag)) { picked = emptySet(); setFlagged(ids.toList(), true) },
                    AppBarItem("✉", stringResource(R.string.mail_mark_unread)) { picked = emptySet(); setUnreadIds(ids.toList()) },
                    AppBarItem("✓", stringResource(R.string.rec_clear)) { picked = emptySet() },
                ),
                menu = listOfNotNull(
                    AppBarItem("☰", stringResource(R.string.mail_select_all)) { picked = lists[filter].orEmpty().map { it.id }.toSet() },
                    if (look.move && hasArchive) AppBarItem("▤", stringResource(R.string.mail_archive)) { picked = emptySet(); moveIds(ids.toList(), null, "\\Archive") } else null,
                    if (look.move && hasSpam) AppBarItem("⊘", stringResource(R.string.mail_spam)) { picked = emptySet(); moveIds(ids.toList(), null, "\\Junk") } else null,
                    if (look.move) AppBarItem("➜", stringResource(R.string.mail_move)) { picked = emptySet(); moving = ids.toList() } else null,
                ),
            )
        } else {
            AppBar(
                listOf(
                    AppBarItem("＋", stringResource(R.string.mail_bar_new)) { openNew() },
                    AppBarItem("↻", stringResource(R.string.mail_bar_sync)) { reload++ },
                    AppBarItem("⌕", stringResource(R.string.mail_bar_search)) { searching = !searching; if (!searching) query = "" }.marked(searching && query.isNotBlank()),
                    AppBarItem("☰", stringResource(R.string.mail_bar_threads)) { threads = !threads }.marked(threads),
                ),
                menu = listOf(AppBarItem("⚙", stringResource(R.string.mail_opt_title)) { showOptions = true }),
            )
        }
    }

    // What is on screen: 0 the list (with the message beside it on a wide screen), 1 a message, 2 the page to write, 3 the choices.
    val view = when {
        showOptions -> 3
        composing -> 2
        selected != null && !wide -> 1
        else -> 0
    }
    val content: @Composable (Int) -> Unit = { v ->
        when (v) {
            1 -> (selected ?: lastOpen)?.let { open ->
                MessageReaderView(open, body, bodyFailure, readerActions(open, hasArchive, hasSpam), look, modifier)
            }
            2 -> ComposeView(
                draft = draft, onChange = { draft = it }, onClose = { composing = false }, files = files, onFiles = { files = it }, suggestContacts = look.contacts,
                onSend = { d, fs ->
                    when (val outcome = MailOutbox.submit(context, account.id, d, fs)) {
                        is SendOutcome.Refused -> Result.failure(outcome.error)
                        else -> {
                            if (outcome is SendOutcome.Queued) MetroToast.show(context, R.string.mail_queued)
                            draft = Draft()
                            files = emptyList()
                            if (savesDraft) withContext(Dispatchers.IO) { MailDrafts.clear(context) }
                            Result.success(Unit)
                        }
                    }
                },
            )
            3 -> MailOptionsView(onBack = { showOptions = false }, onChanged = { optionsVersion++ })
            else -> if (wide && selected != null) {
                CompositionLocalProvider(LocalBottomBarsPadded provides true) { Column(modifier.fillMaxSize().navigationBottomPaddingWithButtons()) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        listPane(Modifier.weight(0.4f))
                        (selected ?: lastOpen)?.let { open -> MessageReaderView(open, body, bodyFailure, readerActions(open, hasArchive, hasSpam), look, Modifier.weight(0.6f)) }
                    }
                    listBar()
                } }
            } else {
                // The bar sits below the page, outside its frame: the room for the navigation bar is left here, once, not by the page.
                CompositionLocalProvider(LocalBottomBarsPadded provides true) { Column(modifier.fillMaxSize().navigationBottomPaddingWithButtons()) {
                    listPane(Modifier.weight(1f))
                    listBar()
                } }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        PageSwitch(
            page = view.takeIf { it != 0 },
            onBack = { if (showOptions) showOptions = false else if (composing) composing = false else selected = null },
            animated = look.transitions,
            list = { content(0) },
            detail = { v -> content(v) },
        )
        confirmDelete?.let { ids ->
            MailConfirm(
                stringResource(R.string.mail_delete_forever, ids.size), stringResource(R.string.mail_delete),
                onYes = { confirmDelete = null; picked = emptySet(); deleteIds(ids, permanent = true) }, onNo = { confirmDelete = null },
            )
        }
        moving?.let { ids ->
            FolderChooser(folderList.orEmpty().filter { it.path != folder }, onPick = { path -> moving = null; moveIds(ids, path, null) }, onCancel = { moving = null })
        }
    }
}

/** A question with two answers over the page: the text, the button that does it, and the button that does not. */
@Composable
private fun MailConfirm(text: String, yes: String, onYes: () -> Unit, onNo: () -> Unit) {
    val colors = LocalMetroColors.current
    Box(
        Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.85f)).pointerInput(Unit) { detectTapGestures { onNo() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).panel().pointerInput(Unit) { detectTapGestures { } }.padding(20.dp).testTag("mail:confirm"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetroText(text, size = 18.sp, weight = FontWeight.Normal, maxLines = Int.MAX_VALUE)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetroButton(yes, Modifier.weight(1f).testTag("mail:confirm:yes"), onClick = onYes)
                MetroButton(stringResource(android.R.string.cancel), Modifier.weight(1f).testTag("mail:confirm:no"), onClick = onNo)
            }
        }
    }
}

/** The folders of the account, one under the other: a touch moves the messages there. */
@Composable
private fun FolderChooser(folders: List<MailFolder>, onPick: (String) -> Unit, onCancel: () -> Unit) {
    val colors = LocalMetroColors.current
    Box(
        Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.85f)).pointerInput(Unit) { detectTapGestures { onCancel() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp).panel().pointerInput(Unit) { detectTapGestures { } }.padding(20.dp).testTag("mail:chooser"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MetroText(stringResource(R.string.mail_move), size = 22.sp)
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(folders, key = { it.path }) { f ->
                    MetroText(f.name, size = 20.sp, modifier = Modifier.fillMaxWidth().metroPress(onClick = { onPick(f.path) }).padding(vertical = 10.dp))
                }
            }
            MetroButton(stringResource(android.R.string.cancel), Modifier.fillMaxWidth(), onClick = onCancel)
        }
    }
}

/** The title, the tabs and what each shows. */
@Composable
private fun MailListScreen(
    modifier: Modifier, account: MailAccount, accounts: List<MailAccount>, folderTitle: String?, titles: List<String>, pager: PagerState,
    lists: Map<MailFilter, List<MailMessage>>, limits: Map<MailFilter, Int>, listStates: Map<MailFilter, LazyListState>, look: MailLook, threads: Boolean,
    picked: Set<String>, syncing: Boolean, error: String?, searching: Boolean, query: String, onQuery: (String) -> Unit, folders: List<MailFolder>?, current: String,
    selectedId: String?, onRead: (MailMessage) -> Unit, onPick: (MailMessage) -> Unit, onMore: () -> Unit, onFolder: (String) -> Unit, onRefresh: () -> Unit,
    onSelectAccount: (String) -> Unit, onAddAccount: (MailAccount, String) -> Unit, onRemoveAccount: (String) -> Unit,
    onSwipe: ((MailMessage, Boolean) -> Unit)? = null, canArchive: Boolean = false,
) {
    MiniFrame(modifier) {
        Column(Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp)) {
            MetroText(
                (account.email + (folderTitle?.let { " · $it" } ?: "")).uppercase(), size = 14.sp, weight = FontWeight.Bold, color = LocalAccent.current, maxLines = 1,
                modifier = Modifier.testTag("mail:account"),
            )
            PivotHeader(titles, pager, Modifier.padding(top = 4.dp))
        }
        if (syncing) MetroProgressBar(Modifier.padding(horizontal = 24.dp, vertical = 4.dp).testTag("mail:syncing"), patient = false)
        if (searching) MetroTextField(query, onQuery, stringResource(R.string.mail_bar_search), Modifier.padding(horizontal = 24.dp, vertical = 4.dp).testTag("mail:search"))
        error?.let { RetryNote(stringResource(R.string.mail_error, it), onRefresh, Modifier.padding(horizontal = 24.dp, vertical = 4.dp), tag = "mail:retry") }
        HorizontalPager(pager, Modifier.weight(1f), beyondViewportPageCount = 1) { page ->
            Box(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                val tab = when (page) { 0 -> MailFilter.All; 1 -> MailFilter.Unseen; 2 -> MailFilter.Flagged; else -> null }
                when {
                    tab != null -> {
                        // Marking a message read or unflagging it takes it out of its tab at once, before the server is asked again.
                        val shown = lists[tab]?.let { all -> when (tab) { MailFilter.Unseen -> all.filter { it.isUnread }; MailFilter.Flagged -> all.filter { it.isFlagged }; else -> all } }
                        InboxPage(
                            shown, tab, onRead, (lists[tab]?.size ?: 0) >= (limits[tab] ?: MailClient.LIMIT), threads, picked, onPick, onMore, listStates.getValue(tab), look, selectedId, query.isNotBlank(), onRefresh,
                            onSwipe, canArchive,
                        )
                    }
                    page == 3 -> FoldersPage(folders, current, onFolder)
                    else -> AccountsPage(accounts, account.id, onSelectAccount, onAddAccount, onRemoveAccount)
                }
            }
        }
    }
}

@Composable
private fun InboxPage(
    messages: List<MailMessage>?, tab: MailFilter, onSelectMessage: (MailMessage) -> Unit, hasMore: Boolean, grouped: Boolean,
    picked: Set<String>, onPick: (MailMessage) -> Unit, onMore: () -> Unit, listState: LazyListState, look: MailLook, selectedId: String?, searchingNow: Boolean, onRefresh: () -> Unit,
    onSwipe: ((MailMessage, Boolean) -> Unit)?, canArchive: Boolean,
) {
    if (messages == null) {
        Column(Modifier.padding(top = 16.dp)) {
            MetroProgressBar(Modifier.fillMaxWidth())
            PageNote(stringResource(R.string.mail_loading), Modifier.padding(top = 8.dp))
        }
        return
    }
    if (messages.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MailGlyphIcon(MailGlyph.Envelope, 72.dp, color = LocalMetroColors.current.subtle)
            PageNote(
                stringResource(
                    when {
                        searchingNow -> R.string.mail_empty_search
                        tab == MailFilter.Unseen -> R.string.mail_empty_unread
                        tab == MailFilter.Flagged -> R.string.mail_empty_flagged
                        else -> R.string.mail_empty_inbox
                    },
                ),
            )
        }
        return
    }
    // A conversation opens in place, to show the messages of it one under the other.
    var open by remember { mutableStateOf(emptySet<String>()) }
    val threads = remember(messages, grouped) { if (grouped) MailThreads.group(messages) else messages.map { listOf(it) } }
    val swivel = rememberRowSwivel()
    val selecting = picked.isNotEmpty()
    // A touch opens the message, with the swivel of the row; while some are ticked it ticks instead.
    fun openOrPick(key: Any, message: MailMessage) { if (selecting) onPick(message) else swivel.open(key) { onSelectMessage(message) } }
    LazyColumn(Modifier.fillMaxSize().pullToRefresh(onRefresh).scrollIndicator(listState).testTag("mail:inbox"), state = listState) {
        threads.forEachIndexed { index, thread ->
            val head = thread.first()
            val expanded = thread.size > 1 && head.id in open
            item(key = head.id) {
                val row: @Composable () -> Unit = {
                    MessageRow(
                        head, if (thread.size > 1) thread.size else 0, Modifier.featherRow(index).swivel(swivel, head.id), look, selecting, head.id in picked, head.id == selectedId,
                        onLongClick = { onPick(head) },
                        onClick = { if (thread.size == 1) openOrPick(head.id, head) else if (selecting) onPick(head) else open = if (expanded) open - head.id else open + head.id },
                    )
                }
                // A message on its own goes away sideways: to the right to the archive, to the left to the trash, with a way back. A conversation or a ticked message does not.
                if (onSwipe != null && thread.size == 1 && !selecting) {
                    SwipeRow(
                        Modifier.animateItem(),
                        onRight = if (canArchive) ({ onSwipe(head, true) }) else null, rightLabel = stringResource(R.string.mail_archive), rightColor = LocalAccent.current,
                        onLeft = { onSwipe(head, false) }, leftLabel = stringResource(R.string.mail_delete), leftColor = Color(0xFFE51400),
                    ) { row() }
                } else {
                    Box(Modifier.animateItem()) { row() }
                }
            }
            if (expanded) items(thread, key = { "${head.id}/${it.id}" }) {
                val key = "${head.id}/${it.id}"
                MessageRow(it, 0, Modifier.padding(start = 20.dp).animateItem().swivel(swivel, key), look, selecting, it.id in picked, it.id == selectedId, onLongClick = { onPick(it) }) { openOrPick(key, it) }
            }
        }
        if (hasMore) item {
            MetroButton(stringResource(R.string.mail_load_more), Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("mail:more"), onClick = onMore)
        }
    }
}

/** A line of the list; [count] is how many messages the conversation it stands for has (0 for a message on its own). */
@Composable
private fun MessageRow(
    msg: MailMessage, count: Int, modifier: Modifier, look: MailLook, selecting: Boolean, checked: Boolean, current: Boolean,
    onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val s = look.scale
    val who = msg.senderName.ifBlank { msg.senderAddress }
    SelectableRow(
        selecting, checked, onClick, modifier.padding(vertical = if (look.compact) 3.dp else 8.dp), onLongClick, verticalAlignment = Alignment.Top,
    ) {
        if (look.rows) {
            // The first letter on the accent, and a dot that says unread louder than the bold type does.
            Box(Modifier.padding(end = 12.dp).size((if (look.compact) 32 else 44).dp).background(if (msg.isUnread) accent else colors.inactive), contentAlignment = Alignment.Center) {
                MetroText(who.firstOrNull { it.isLetterOrDigit() }?.uppercase().orEmpty(), size = (if (look.compact) 16 else 22).sp, color = Color.White)
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (look.rows && msg.isUnread) Box(Modifier.padding(end = 6.dp).size(8.dp).clip(CircleShape).background(accent))
                MetroText(
                    who, size = (22 * s).sp, weight = if (msg.isUnread) FontWeight.SemiBold else FontWeight.Light, maxLines = 1,
                    color = if (current) accent else colors.foreground,
                )
            }
            MetroText(msg.subject, size = (16 * s).sp, weight = if (msg.isUnread) FontWeight.SemiBold else FontWeight.Normal, color = if (msg.isUnread) accent else colors.subtle, maxLines = if (look.compact) 1 else 2)
            if (!look.compact && msg.snippet.isNotBlank()) MetroText(msg.snippet, size = (13 * s).sp, weight = FontWeight.Normal, color = colors.subtle.copy(alpha = 0.8f), maxLines = 2)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
            MetroText(mailWhen(msg.timestamp), size = (12 * s).sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1)
            if (count > 0) MetroText(count.toString(), size = 16.sp, color = accent)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (msg.hasAttachments) MailGlyphIcon(MailGlyph.File, 16.dp, color = colors.subtle)
                if (msg.isFlagged) MailGlyphIcon(MailGlyph.Flag, 18.dp, color = accent)
            }
        }
    }
}

@Composable
private fun FoldersPage(folders: List<MailFolder>?, current: String, onPick: (String) -> Unit) {
    if (folders == null) {
        Column(Modifier.padding(top = 16.dp)) {
            MetroProgressBar(Modifier.fillMaxWidth())
            PageNote(stringResource(R.string.mail_loading), Modifier.padding(top = 8.dp))
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("mail:folders")) {
        items(folders, key = { it.path }) { f ->
            Row(Modifier.fillMaxWidth().metroPress(onClick = { onPick(f.path) }).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val name = if (f.path == INBOX) stringResource(R.string.mail_folder_inbox) else f.name
                MetroText(name, size = 22.sp, color = if (f.path == current) LocalAccent.current else LocalMetroColors.current.foreground, modifier = Modifier.weight(1f))
                if (f.unread > 0) MetroText(f.unread.toString(), size = 18.sp, color = LocalAccent.current)
            }
        }
    }
}
