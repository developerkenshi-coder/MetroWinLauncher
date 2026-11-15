package com.metrowin.launcher.messages

import com.metrowin.launcher.ui.components.isWide
import com.metrowin.launcher.ui.theme.Danger
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.anim.swivel
import com.metrowin.launcher.ui.anim.rememberRowSwivel
import com.metrowin.launcher.ui.apps.anchor
import com.metrowin.launcher.ui.apps.RowBounds
import com.metrowin.launcher.ui.apps.MenuAction
import com.metrowin.launcher.ui.apps.LocalRowMenu
import androidx.compose.foundation.pager.HorizontalPager
import com.metrowin.launcher.ui.components.centeredContent
import com.metrowin.launcher.ui.components.topBarsPadding
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.metrowin.launcher.R
import com.metrowin.launcher.data.LivePermissions
import com.metrowin.launcher.data.SimChoice
import com.metrowin.launcher.data.SimInfo
import com.metrowin.launcher.data.SimPrefs
import com.metrowin.launcher.data.SimReader
import com.metrowin.launcher.data.SimRouting
import com.metrowin.launcher.data.SimUse
import com.metrowin.launcher.ui.components.SimPickDialog
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.AppTitleLine
import com.metrowin.launcher.ui.components.Mark
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.featherRow
import com.metrowin.launcher.ui.components.scrollIndicator
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import java.util.Date
import java.util.Locale
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.LocalStyle
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.start.whenText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.onAccent
import com.metrowin.launcher.ui.theme.pageBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val permissions = buildList {
    add(Manifest.permission.READ_SMS)
    add(Manifest.permission.SEND_SMS)
    add(Manifest.permission.RECEIVE_SMS)
    add(LivePermissions.contacts)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}

private val FAILED_RED = Danger

/** The messages of Windows Phone: the conversations, a conversation with its bubbles, and the box to write in. [address] is the open conversation. */
@Composable
fun MessagesScreen(address: String?, body: String, onAddress: (String?) -> Unit, onBody: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { SmsRepository(context) }
    val options = remember { MessagesOptions(context) }
    var allowed by remember { mutableStateOf(LivePermissions.isGranted(context, Manifest.permission.READ_SMS) && LivePermissions.isGranted(context, Manifest.permission.SEND_SMS)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        allowed = LivePermissions.isGranted(context, Manifest.permission.READ_SMS) && LivePermissions.isGranted(context, Manifest.permission.SEND_SMS)
    }
    LaunchedEffect(Unit) {
        val missing = permissions.filter { !LivePermissions.isGranted(context, it) }
        if (missing.isNotEmpty()) ask.launch(missing.toTypedArray())
    }
    val askPhone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (!granted) options.set(MessagesOptions.Key.Sims, false) }
    var refresh by remember { mutableIntStateOf(0) }
    val conversations by produceState<List<Conversation>?>(null, allowed, refresh) {
        value = if (allowed) withContext(Dispatchers.IO) { repository.conversations() } else emptyList()
    }
    // The names of the contacts are looked up again when the app comes back (a contact may have been added or renamed meanwhile).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { repository.forgetNames(); refresh++ }
    // The list follows the messages of the phone as they change, a moment after the last change.
    val live = options.on(MessagesOptions.Key.Live)
    DisposableEffect(allowed, live) {
        if (!allowed || !live) return@DisposableEffect onDispose { }
        val handler = Handler(Looper.getMainLooper())
        val bump = Runnable { refresh++ }
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) { handler.removeCallbacks(bump); handler.postDelayed(bump, 400) }
        }
        context.contentResolver.registerContentObserver("content://mms-sms/".toUri(), true, observer)
        onDispose { handler.removeCallbacks(bump); context.contentResolver.unregisterContentObserver(observer) }
    }
    // A message being forwarded waits here until a conversation is chosen for it.
    var forward by remember { mutableStateOf<String?>(null) }
    val menu = optionItems(options) { if (it) askPhone.launch(Manifest.permission.READ_PHONE_STATE) }
    val wide = options.on(MessagesOptions.Key.Panes) && isWide()
    BackHandler(enabled = address != null) { onAddress(null); refresh++ }
    MiniFrame(modifier, ime = true) {
        when {
            !allowed -> Column(Modifier.padding(24.dp)) {
                PageNote(stringResource(R.string.messages_need_sms))
                MetroButton(stringResource(R.string.page_allow), Modifier.fillMaxWidth().padding(top = 8.dp)) { ask.launch(permissions.toTypedArray()) }
            }
            wide -> Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(0.4f).fillMaxHeight()) {
                    ListPage(conversations, repository, options, menu, forward, address, onDropForward = { forward = null }, onOpen = { chosen -> forward?.let { onBody(it); forward = null }; onAddress(chosen) }, onRefresh = { refresh++ })
                }
                Box(Modifier.weight(0.6f).fillMaxHeight()) {
                    if (address != null) ThreadPage(address, body, onBody, repository, options, menu, conversations.orEmpty(), onChanged = { refresh++ }, onForward = { forward = it; onAddress(null); refresh++ })
                    else PageNote(stringResource(R.string.messages_pick), Modifier.padding(24.dp))
                }
            }
            address != null -> ThreadPage(address, body, onBody, repository, options, menu, conversations.orEmpty(), onChanged = { refresh++ }, onForward = { forward = it; onAddress(null); refresh++ })
            else -> ListPage(conversations, repository, options, menu, forward, null, onDropForward = { forward = null }, onOpen = { chosen -> forward?.let { onBody(it); forward = null }; onAddress(chosen) }, onRefresh = { refresh++ })
        }
    }
}


/** The options of the "⋯" menu: each is a switch (marked when on); [onSims] is told when the SIM choice turns on, to ask for its permission. */
@Composable
private fun optionItems(options: MessagesOptions, onSims: (Boolean) -> Unit): List<AppBarItem> {
    @Composable fun item(key: MessagesOptions.Key, label: Int) = AppBarItem(if (options.on(key)) "✓" else "○", stringResource(label)) {
        options.set(key, !options.on(key))
        if (key == MessagesOptions.Key.Sims) onSims(options.on(key))
    }.marked(options.on(key))
    return listOf(
        item(MessagesOptions.Key.Bubbles, R.string.messages_opt_bubbles), item(MessagesOptions.Key.Avatars, R.string.messages_opt_avatars),
        item(MessagesOptions.Key.Live, R.string.messages_opt_live), item(MessagesOptions.Key.Panes, R.string.messages_opt_panes),
        item(MessagesOptions.Key.Quick, R.string.messages_opt_quick), item(MessagesOptions.Key.Delivery, R.string.messages_opt_delivery),
        item(MessagesOptions.Key.Sims, R.string.messages_opt_sims), item(MessagesOptions.Key.Otp, R.string.messages_opt_otp),
        item(MessagesOptions.Key.Block, R.string.messages_opt_block), item(MessagesOptions.Key.Lock, R.string.messages_opt_lock),
    )
}

/** A question with two answers over the page. */
@Composable
private fun Confirm(text: String, yes: String, onYes: () -> Unit, onCancel: () -> Unit) {
    DialogFrame(onCancel) {
        Column(Modifier.padding(20.dp)) {
            MetroText(text, size = 18.sp, weight = FontWeight.Normal)
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetroButton(yes, Modifier.weight(1f).testTag("messages:confirm")) { onYes() }
                MetroButton(stringResource(R.string.messages_cancel), Modifier.weight(1f)) { onCancel() }
            }
        }
    }
}

@Composable
private fun ListPage(
    conversations: List<Conversation>?, repository: SmsRepository, options: MessagesOptions, menuItems: List<AppBarItem>, forward: String?, current: String?,
    onDropForward: () -> Unit, onOpen: (String) -> Unit, onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isDefault by remember { mutableStateOf(SmsRole.isDefault(context)) }
    val role = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { isDefault = SmsRole.isDefault(context) }
    val roleIntent = remember { SmsRole.requestIntent(context) }
    var composing by remember(forward) { mutableStateOf(forward != null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val bodyHits by produceState(emptyList<Conversation>(), query) {
        value = if (query.trim().length >= 2) withContext(Dispatchers.IO) { repository.search(query.trim()) } else emptyList()
    }
    // A name matches too, a text matches by what it says.
    val shown = if (query.isBlank()) conversations else (conversations.orEmpty().filter { it.title.contains(query.trim(), ignoreCase = true) } + bodyHits).distinctBy { it.threadId }
    val menu = LocalRowMenu.current
    val swivel = rememberRowSwivel()
    var number by remember { mutableStateOf("") }
    val pager = rememberPagerState { 3 }
    // The conversations picked with a long press: what is done to them (read, delete, block) is done to all.
    val selected: SnapshotStateList<Long> = rememberSaveable(saver = androidx.compose.runtime.saveable.listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })) { mutableListOf<Long>().toMutableStateList() }
    var asking by remember { mutableStateOf<(() -> Unit)?>(null) }
    var askText by remember { mutableStateOf("") }
    var askYes by remember { mutableStateOf("") }
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    var bannerOpen by rememberSaveable { mutableStateOf(!landscape) }
    BackHandler(enabled = composing || searching || selected.isNotEmpty()) {
        if (selected.isNotEmpty()) selected.clear() else { composing = false; searching = false; query = ""; onDropForward() }
    }
    val avatars = options.on(MessagesOptions.Key.Avatars)
    asking?.let { run -> Confirm(askText, askYes, { asking = null; run() }, { asking = null }) }
    Column(Modifier.fillMaxSize()) {
        PivotHeader(listOf(stringResource(R.string.cat_all), stringResource(R.string.mail_tab_unread), stringResource(R.string.messages_tab_unknown)), pager, Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp))
        Column(Modifier.weight(1f)) {
          Column(Modifier.padding(horizontal = 24.dp)) {
            if (!isDefault && roleIntent != null) {
                if (bannerOpen) {
                    PageNote(stringResource(R.string.messages_default_note), Modifier.metroPress(onClick = { bannerOpen = false }))
                    MetroButton(stringResource(R.string.messages_default_button), Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp).testTag("messages:default")) { role.launch(roleIntent) }
                } else {
                    MetroText(stringResource(R.string.messages_default_button) + " ▾", size = 14.sp, weight = FontWeight.SemiBold, color = LocalAccent.current, modifier = Modifier.metroPress(onClick = { bannerOpen = true }).padding(vertical = 6.dp).testTag("messages:default"))
                }
            }
            if (composing) {
                MetroTextField(number, { number = it.filter { c -> c.isDigit() || c == '+' } }, stringResource(R.string.messages_to_hint), Modifier.padding(bottom = 6.dp).testTag("messages:to"), keyboardType = KeyboardType.Phone)
                MetroButton(stringResource(R.string.messages_open), Modifier.fillMaxWidth().padding(bottom = 8.dp)) { if (number.isNotEmpty()) { composing = false; onOpen(number); number = "" } }
            }
            if (searching) MetroTextField(query, { query = it }, stringResource(R.string.messages_search), Modifier.padding(bottom = 6.dp).testTag("messages:search"))
            if (shown?.isEmpty() == true) {
                PageNote(stringResource(R.string.messages_empty), Modifier.testTag("messages:empty"))
                PageNote(stringResource(R.string.messages_empty_hint), Modifier.padding(top = 4.dp))
            }
          }
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                val rows = when (page) {
                    1 -> shown.orEmpty().filter { it.unread > 0 }
                    2 -> shown.orEmpty().filter { it.name == null && ',' !in it.address }
                    else -> shown.orEmpty()
                }
                val listState = rememberLazyListState()
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp).scrollIndicator(listState).testTag("messages:list"), state = listState) {
                    // While the first read is on its way the lines are drawn empty, so the page does not jump.
                    if (shown == null) items(SKELETON_ROWS) { SkeletonRow() }
                    itemsIndexed(rows, key = { _, it -> it.threadId }) { index, item ->
                        val bounds = remember { RowBounds() }
                        val picked = item.threadId in selected
                        ConversationRow(
                            item, repository, avatars, picked || item.address == current, query.trim(),
                            Modifier.featherRow(index).anchor(bounds).swivel(swivel, item.threadId),
                            onClick = { if (selected.isNotEmpty()) { if (picked) selected.remove(item.threadId) else selected.add(item.threadId) } else swivel.open(item.threadId) { onOpen(item.address) } },
                            onLongClick = { if (picked) selected.remove(item.threadId) else selected.add(item.threadId) },
                        )
                    }
                }
            }
        }
        val chosen = conversations.orEmpty().filter { it.threadId in selected }
        if (selected.isNotEmpty()) {
            val delete = stringResource(R.string.messages_delete)
            val deleteAsk = stringResource(R.string.messages_delete_confirm, selected.size)
            AppBar(
                buildList {
                    if (isDefault) add(AppBarItem("✕", delete) { askText = deleteAsk; askYes = delete; asking = { scope.launch { withContext(Dispatchers.IO) { chosen.forEach { repository.delete(it.threadId) } }; selected.clear(); onRefresh() } } })
                    add(AppBarItem("✓", stringResource(R.string.messages_read)) { scope.launch { withContext(Dispatchers.IO) { chosen.forEach { repository.markRead(it.threadId) } }; selected.clear(); onRefresh() } })
                    if (options.on(MessagesOptions.Key.Block)) {
                        val allBlocked = chosen.all { options.isBlocked(it.address) }
                        add(AppBarItem("⊘", stringResource(if (allBlocked) R.string.messages_unblock else R.string.messages_block)) { chosen.filter { ',' !in it.address }.forEach { options.block(it.address, !allBlocked) }; selected.clear() })
                    }
                },
            )
        } else {
            AppBar(
                listOf(
                    AppBarItem("＋", stringResource(R.string.messages_new)) { composing = !composing },
                    AppBarItem("⌕", stringResource(R.string.messages_search)) { searching = !searching; if (!searching) query = "" },
                    AppBarItem("↻", stringResource(R.string.messages_refresh), onRefresh),
                ),
                menu = menuItems,
            )
        }
    }
}

private const val SKELETON_ROWS = 6

@Composable
private fun SkeletonRow() {
    val colors = LocalMetroColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth(0.5f).height(22.dp).background(colors.subtle.copy(alpha = 0.18f)))
        Box(Modifier.fillMaxWidth(0.8f).height(14.dp).background(colors.subtle.copy(alpha = 0.12f)))
    }
}

/** The face of a conversation: the photo of the contact when the phone has one, else its first letter (a number is a hash). */
@Composable
private fun Avatar(name: String, photo: String?, repository: SmsRepository, size: Dp) {
    val accent = LocalAccent.current
    val shape = if (LocalStyle.current.isClassic) RectangleShape else CircleShape
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, photo) { value = photo?.let { withContext(Dispatchers.IO) { repository.picture(it.toUri(), 160) } } }
    Box(Modifier.size(size).clip(shape).background(accent), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: MetroText(name.firstOrNull { it.isLetter() }?.uppercase() ?: "#", size = (size.value * 0.5f).sp, weight = FontWeight.SemiBold, color = onAccent(), maxLines = 1)
    }
}

@Composable
private fun ConversationRow(
    item: Conversation, repository: SmsRepository, avatars: Boolean, lit: Boolean, query: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null,
) {
    val colors = LocalMetroColors.current
    Row(
        modifier.fillMaxWidth().testTag("messages:thread").then(if (lit) Modifier.background(LocalAccent.current.copy(alpha = 0.25f)) else Modifier)
            .then(if (onClick != null) Modifier.metroPress(onClick = onClick, onLongClick = onLongClick) else Modifier).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (avatars) {
            Avatar(item.title, item.photo, repository, 48.dp)
            Box(Modifier.size(10.dp))
        }
        Column(Modifier.weight(1f)) {
            MetroText(item.title, size = 22.sp, weight = if (item.unread > 0) FontWeight.SemiBold else FontWeight.Light, maxLines = 1, highlight = query)
            MetroText(item.snippet, size = 14.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 2, highlight = query)
        }
        Column(horizontalAlignment = Alignment.End) {
            MetroText(whenText(item.date), size = 12.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1)
            if (item.unread > 0) {
                Box(Modifier.padding(top = 2.dp).heightIn(min = 22.dp).widthIn(min = 22.dp).clip(CircleShape).background(LocalAccent.current).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                    MetroText(item.unread.toString(), size = 13.sp, weight = FontWeight.SemiBold, color = onAccent(), maxLines = 1)
                }
            }
        }
    }
}

/** What the box of a conversation shows of the text: the characters left in the part it ends, and the number of messages it will take. */
private fun counter(text: String): String? = if (text.length < 100) null else MessagesLogic.parts(text).let { (n, left) -> "$left / $n" }

@Composable
private fun ThreadPage(
    address: String, prefill: String, onBody: (String) -> Unit, repository: SmsRepository, options: MessagesOptions, menuItems: List<AppBarItem>,
    conversations: List<Conversation>, onChanged: () -> Unit, onForward: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val classic = LocalStyle.current.isClassic
    val rich = options.on(MessagesOptions.Key.Bubbles)
    val thread = repository.threadOf(address, conversations)
    var version by remember { mutableIntStateOf(0) }
    // The newest messages first; more are read when the top of the conversation is reached.
    var limit by rememberSaveable(address) { mutableIntStateOf(PAGE) }
    val messages by produceState(emptyList<Sms>(), thread, version, limit) {
        value = withContext(Dispatchers.IO) { repository.messages(thread?.threadId ?: -1, limit, address) }
    }
    val ordered = remember(messages) { messages.sortedByDescending { it.date } }
    var text by rememberSaveable(address) { mutableStateOf(prefill) }
    LaunchedEffect(address) { if (text.isEmpty()) text = withContext(Dispatchers.IO) { repository.draft(address) } }
    var failed by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<android.net.Uri?>(null) }
    var deleting by remember { mutableStateOf<Sms?>(null) }
    // Two SIMs: the one picked in this conversation, else the one kept for the contact, else the default for texts, else the user is asked.
    // The permission to read the SIMs is asked once, and only on a phone built for two.
    var phoneOk by remember { mutableStateOf(LivePermissions.isGranted(context, android.Manifest.permission.READ_PHONE_STATE)) }
    val askPhone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> phoneOk = ok; if (!ok) options.set(MessagesOptions.Key.Sims, false) }
    LaunchedEffect(Unit) {
        @Suppress("DEPRECATION")
        val dual = context.getSystemService(android.telephony.TelephonyManager::class.java)?.phoneCount.let { it != null && it > 1 }
        if (dual && options.on(MessagesOptions.Key.Sims) && !phoneOk) askPhone.launch(android.Manifest.permission.READ_PHONE_STATE)
    }
    val simPrefs = remember { SimPrefs(context) }
    val sims by produceState(emptyList<SimInfo>(), options.on(MessagesOptions.Key.Sims), phoneOk) { value = if (options.on(MessagesOptions.Key.Sims)) withContext(Dispatchers.IO) { SimReader.read(context) } else emptyList() }
    var picked by rememberSaveable { mutableStateOf<Int?>(null) }
    var choosing by remember { mutableStateOf<String?>(null) }
    val choice: SimChoice = picked?.let { slot -> sims.firstOrNull { it.slot == slot }?.let { SimChoice.Use(it) } }
        ?: SimRouting.choose(sims, simPrefs.forContact(SimUse.Sms, address), simPrefs.default(SimUse.Sms))
    val sub = (choice as? SimChoice.Use)?.sim?.subId ?: -1
    val report = options.on(MessagesOptions.Key.Delivery)
    val menu = LocalRowMenu.current
    val retryText = stringResource(R.string.messages_retry)
    val copyText = stringResource(R.string.messages_copy)
    val forwardText = stringResource(R.string.messages_forward)
    val deleteText = stringResource(R.string.messages_delete_message)
    val isDefault = remember { SmsRole.isDefault(context) }
    val unread = thread?.unread ?: 0
    LaunchedEffect(thread?.threadId, unread) {
        thread?.let {
            if (unread > 0 || it.threadId > 0) withContext(Dispatchers.IO) { repository.markRead(it.threadId) }
            SmsReceiver.cancel(context, address)
        }
    }
    // The draft is kept when the screen goes away, and when the app goes to the background (the process can be ended there).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { repository.saveDraft(address, text) }
    DisposableEffect(address) { onDispose { repository.saveDraft(address, text); onBody("") } }
    val contact by produceState<Pair<String, String?>?>(null, address) { value = withContext(Dispatchers.IO) { repository.contactOf(address) } }
    val title = thread?.title ?: contact?.first ?: address
    deleting?.let { sms -> Confirm(stringResource(R.string.messages_delete_message_confirm), deleteText, { deleting = null; scope.launch { withContext(Dispatchers.IO) { repository.delete(sms) }; version++; onChanged() } }, { deleting = null }) }
    BackHandler(enabled = viewing != null) { viewing = null }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        ThreadTitle(title, classic, if (options.on(MessagesOptions.Key.Avatars)) contact?.second ?: "" else null, repository)
        val listState = rememberLazyListState()
        val group = ',' in address
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp).testTag("messages:bubbles"), state = listState, reverseLayout = true, verticalArrangement = Arrangement.spacedBy(if (rich) 2.dp else 6.dp)) {
            itemsIndexed(ordered, key = { _, it -> "${it.mms}:${it.id}:${it.date}" }) { index, sms ->
                val bounds = remember { RowBounds() }
                val older = ordered.getOrNull(index + 1)
                val newer = ordered.getOrNull(index - 1)
                val firstOfDay = rich && (older == null || MessagesLogic.day(older.date) != MessagesLogic.day(sms.date))
                val startsRun = !rich || !MessagesLogic.continues(older, sms)
                val endsRun = !rich || !MessagesLogic.continues(sms, newer ?: sms.copy(outgoing = !sms.outgoing))
                Column(Modifier.then(if (LocalReduceMotion.current) Modifier else Modifier.animateItem()).padding(top = if (rich && startsRun) 6.dp else 0.dp)) {
                    if (firstOfDay) DayHeader(sms.date)
                    BubbleRow(
                        sms, repository, rich, endsRun, if (group && !sms.outgoing && startsRun) sms.sender else null, Modifier.anchor(bounds),
                        onOpenPicture = { viewing = it },
                        onFixed = {
                            when {
                                sms.failed -> scope.launch { failed = !withContext(Dispatchers.IO) { repository.retry(address, sms, sub, report) }; version++; onChanged() }
                                sms.pending -> scope.launch { withContext(Dispatchers.IO) { repository.redownload(sms.id) } }
                            }
                        },
                    ) {
                        val actions = buildList {
                            if (sms.failed) add(MenuAction(retryText) { scope.launch { failed = !withContext(Dispatchers.IO) { repository.retry(address, sms, sub, report) }; version++; onChanged() } })
                            if (sms.body.isNotEmpty()) {
                                add(MenuAction(copyText) { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("sms", sms.body)) })
                                add(MenuAction(forwardText) { onForward(sms.body) })
                            }
                            if (isDefault && sms.id > 0) add(MenuAction(deleteText) { deleting = sms })
                        }
                        menu.show(bounds, actions) { BubbleRow(sms, repository, rich, true, null, onOpenPicture = {}, onFixed = {}) }
                    }
                }
            }
            // The top of the list is the oldest message read so far: reaching it reads the next page.
            if (ordered.size >= limit) item(key = "more") { LaunchedEffect(limit) { limit += PAGE } }
        }
        if (failed) PageNote(stringResource(R.string.messages_failed), Modifier.padding(horizontal = 24.dp))
        // A group is read here: writing to it is a multimedia message, which this app does not send.
        if (',' !in address) {
            fun deliver(body: String, subId: Int) {
                if (body.isEmpty() || sending) return
                sending = true
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { repository.send(address, body, subId, report) }
                    sending = false
                    failed = !ok
                    if (ok) { text = ""; version++; onChanged() }
                }
            }
            // Two SIMs and no SIM said for this contact: the text waits for the answer, then goes.
            fun send() {
                val body = text.trim()
                if (body.isEmpty() || sending) return
                if (choice == SimChoice.Ask) choosing = body else deliver(body, sub)
            }
            choosing?.let { body ->
                SimPickDialog(
                    SimUse.Sms, sims, SimRouting.key(address).isNotEmpty(),
                    { sim, keep ->
                        choosing = null
                        picked = sim.slot
                        if (keep) simPrefs.setForContact(SimUse.Sms, address, sim.slot)
                        deliver(body, sim.subId)
                    },
                    { choosing = null },
                )
            }
            if (options.on(MessagesOptions.Key.Quick)) QuickReplies { text = if (text.isBlank()) it else "${text.trimEnd()} $it" }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Box(Modifier.heightIn(max = 140.dp)) { MetroTextField(text, { text = it }, stringResource(R.string.messages_hint), Modifier.testTag("messages:field"), singleLine = false) }
                    counter(text)?.let { MetroText(it, size = 11.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, modifier = Modifier.padding(start = 4.dp, top = 2.dp)) }
                }
                if (sims.size > 1) {
                    // The SIM that will send; a tap asks again and changes it for this conversation (the question also offers to keep it for the contact).
                    MetroText(
                        (choice as? SimChoice.Use)?.sim?.label ?: stringResource(R.string.sim_choose), size = 12.sp, weight = FontWeight.SemiBold, maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp).testTag("messages:sim").metroPress(onClick = { choosing = "" }).padding(8.dp),
                    )
                }
                if (!classic) MetroButton(stringResource(R.string.messages_send), Modifier.padding(start = 8.dp).testTag("messages:send"), onClick = ::send)
            }
            // In Windows Phone sending is a button of the application bar, an arrow.
            if (classic) AppBar(listOf(AppBarItem("→", stringResource(R.string.messages_send), ::send).icon(Mark.Arrow)), menu = menuItems)
        }
    }
    viewing?.let { PictureViewer(it, repository) { viewing = null } }
    }
}

private const val PAGE = 100

@Composable
private fun QuickReplies(onPick: (String) -> Unit) {
    val replies = listOf(R.string.messages_quick_1, R.string.messages_quick_2, R.string.messages_quick_3, R.string.messages_quick_4).map { stringResource(it) }
    androidx.compose.foundation.lazy.LazyRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(replies) { r ->
            MetroText(r, size = 14.sp, weight = FontWeight.Normal, maxLines = 1, modifier = Modifier.background(LocalMetroColors.current.surface).metroPress(onClick = { onPick(r) }).padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
}

/** The title of a conversation; the face of the contact is beside it when [photo] is not null (empty for the initial). */
@Composable
private fun ThreadTitle(title: String, classic: Boolean, photo: String?, repository: SmsRepository) {
    Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (photo != null) {
            Avatar(title, photo.ifEmpty { null }, repository, 44.dp)
            Box(Modifier.size(12.dp))
        }
        if (classic) {
            // The page title of Windows Phone: the name of the app in small capitals, and the title lowercase and large under it.
            Column {
                AppTitleLine()
                MetroText(title.lowercase(Locale.getDefault()), size = 40.sp, maxLines = 1)
            }
        } else {
            MetroText(title, size = 30.sp, maxLines = 1)
        }
    }
}

@Composable
private fun DayHeader(millis: Long) {
    val context = LocalContext.current
    val today = MessagesLogic.day(System.currentTimeMillis())
    val day = MessagesLogic.day(millis)
    val text = when (day) {
        today -> stringResource(R.string.live_today)
        today.minusDays(1) -> stringResource(R.string.messages_yesterday)
        else -> DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL)
    }
    MetroText(text, size = 12.sp, weight = FontWeight.SemiBold, color = LocalMetroColors.current.subtle, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
}

/** The bubble of Windows Phone: square corners and a small tail under the side of whoever wrote it. */
private class BubbleShape(private val outgoing: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val tail = with(density) { TAIL.toPx() }
        val inset = with(density) { 10.dp.toPx() }
        val body = size.height - tail
        val path = Path().apply {
            addRect(Rect(0f, 0f, size.width, body))
            val edge = if (outgoing) size.width - inset else inset
            val toward = if (outgoing) -tail else tail
            moveTo(edge + toward, body)
            lineTo(edge, body)
            lineTo(edge, body + tail)
            close()
        }
        return Outline.Generic(path)
    }

    companion object {
        val TAIL = 8.dp
    }
}

/**
 * One bubble of a conversation, on the side it belongs to; a long press (when [onLongClick] is given) opens its menu. In the rich look
 * ([rich]) the width follows the screen, the time shows once under the last bubble of a run, the look follows the style, links open,
 * and [sender] names who wrote in a group. A failed message is red and a tap tries again; one to download says so and a tap fetches it ([onFixed]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BubbleRow(
    sms: Sms, repository: SmsRepository, rich: Boolean, showTime: Boolean, sender: String?, modifier: Modifier = Modifier,
    onOpenPicture: (android.net.Uri) -> Unit, onFixed: () -> Unit, onLongClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val style = LocalStyle.current
    val classic = style.isClassic
    val ink = onAccent()
    val width = if (rich) (LocalConfiguration.current.screenWidthDp * 0.8f).coerceIn(240f, 520f).dp else 280.dp
    val fill = if (sms.failed) FAILED_RED else if (sms.outgoing) accent else colors.surface
    val textColor = if (sms.failed) Color.White else if (sms.outgoing) ink else colors.foreground
    val faint = if (sms.failed) Color.White.copy(alpha = 0.85f) else if (sms.outgoing) ink.copy(alpha = 0.75f) else colors.subtle
    val shape: Shape = when {
        classic || !rich -> if (classic) BubbleShape(sms.outgoing) else RectangleShape
        sms.outgoing -> RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
        else -> RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }
    val tail = if (classic) BubbleShape.TAIL else 0.dp
    val time = if (rich) DateFormat.getTimeFormat(context).format(Date(sms.date)) else whenText(sms.date)
    val state = when {
        sms.failed -> stringResource(R.string.messages_failed)
        sms.pending -> stringResource(R.string.messages_mms_pending)
        else -> time + if (sms.delivered) " ✓" else ""
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = if (sms.outgoing) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = width).background(fill, shape)
                .then(if (onLongClick != null) Modifier.metroPress(onClick = { onFixed() }, onLongClick = onLongClick) else Modifier)
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp + tail),
        ) {
            sender?.let {
                // The contact is looked up off the main thread; the number shows until it is found.
                val who by produceState(it, it) { value = withContext(Dispatchers.IO) { repository.nameOf(it) } ?: it }
                MetroText(who, size = 12.sp, weight = FontWeight.SemiBold, color = textColor, maxLines = 1)
            }
            sms.image?.let { Picture(it, repository, onOpenPicture, onLongClick) }
            if (sms.pending) MetroText(stringResource(R.string.messages_mms_download), size = 16.sp, weight = FontWeight.Normal, color = textColor)
            sms.attachment?.let { MetroText(stringResource(R.string.messages_attachment, it), size = 14.sp, weight = FontWeight.Normal, color = textColor) }
            if (sms.body.isNotBlank()) BubbleText(sms.body, textColor, rich)
            if (showTime || sms.failed || sms.pending || !rich) MetroText(state, size = 11.sp, weight = FontWeight.Normal, color = faint)
            if (sms.failed) MetroText(stringResource(R.string.messages_retry), size = 12.sp, weight = FontWeight.SemiBold, color = textColor, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** The text of a bubble; with [links] the web addresses, e-mails and numbers in it open. */
@Composable
private fun BubbleText(text: String, color: Color, links: Boolean) {
    val context = LocalContext.current
    val style = LocalStyle.current
    val found = remember(text, links) { if (links) MessagesLogic.links(text) else emptyList() }
    if (found.isEmpty()) {
        MetroText(text, size = 18.sp, weight = FontWeight.Normal, color = color)
        return
    }
    val annotated = remember(text, found) {
        buildAnnotatedString {
            append(text)
            found.forEach { l ->
                addLink(
                    LinkAnnotation.Clickable(l.url, TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, l.url.toUri())) }
                    },
                    l.start, l.end,
                )
            }
        }
    }
    BasicText(annotated, style = TextStyle(color = color, fontSize = 18.sp, fontWeight = style.weight(FontWeight.Normal), fontFamily = style.font))
}

/** A picture of a multimedia message, read down to the size of a bubble; its place has the proportions of the picture from the start. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Picture(uri: android.net.Uri, repository: SmsRepository, onOpen: (android.net.Uri) -> Unit, onLongClick: (() -> Unit)?) {
    val size by produceState<Pair<Int, Int>?>(null, uri) { value = withContext(Dispatchers.IO) { repository.pictureSize(uri) } }
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, uri) { value = withContext(Dispatchers.IO) { repository.picture(uri, 560) } }
    val ratio = size?.let { it.first.toFloat() / it.second }?.coerceIn(0.5f, 2.5f) ?: 1.33f
    Box(Modifier.fillMaxWidth().padding(bottom = 4.dp).aspectRatio(ratio).background(LocalMetroColors.current.subtle.copy(alpha = 0.2f)).combinedClickable(onClick = { onOpen(uri) }, onLongClick = onLongClick)) {
        bitmap?.let { Image(it, stringResource(R.string.messages_image), Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

/** A picture over the whole page, as large as the screen allows; a tap or Back closes it. */
@Composable
private fun PictureViewer(uri: android.net.Uri, repository: SmsRepository, onClose: () -> Unit) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, uri) { value = withContext(Dispatchers.IO) { repository.picture(uri, 1600) } }
    Box(Modifier.fillMaxSize().background(Color.Black).metroPress(onClick = onClose), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it, stringResource(R.string.messages_image), Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
    }
}
