package com.metrowin.launcher.messages

import com.metrowin.launcher.ui.components.topBarsPadding
import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.LivePermissions
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.start.whenText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.pageBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val permissions = buildList {
    add(Manifest.permission.READ_SMS)
    add(Manifest.permission.SEND_SMS)
    add(Manifest.permission.RECEIVE_SMS)
    add(LivePermissions.contacts)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}

/** The messages of Windows Phone: the conversations, a conversation with its bubbles, and the box to write in. [address] is the open conversation. */
@Composable
fun MessagesScreen(address: String?, body: String, onAddress: (String?) -> Unit, onBody: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { SmsRepository(context) }
    var allowed by remember { mutableStateOf(LivePermissions.isGranted(context, Manifest.permission.READ_SMS) && LivePermissions.isGranted(context, Manifest.permission.SEND_SMS)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        allowed = LivePermissions.isGranted(context, Manifest.permission.READ_SMS) && LivePermissions.isGranted(context, Manifest.permission.SEND_SMS)
    }
    LaunchedEffect(Unit) {
        val missing = permissions.filter { !LivePermissions.isGranted(context, it) }
        if (missing.isNotEmpty()) ask.launch(missing.toTypedArray())
    }
    var refresh by remember { mutableIntStateOf(0) }
    val conversations by produceState<List<Conversation>?>(null, allowed, refresh) {
        value = if (allowed) withContext(Dispatchers.IO) { repository.conversations() } else emptyList()
    }
    BackHandler(enabled = address != null) { onAddress(null); refresh++ }
    Column(modifier.fillMaxSize().pageBackground().topBarsPadding().navigationBarsPadding().imePadding()) {
        when {
            !allowed -> Column(Modifier.padding(24.dp)) {
                PageNote(stringResource(R.string.messages_need_sms))
                MetroButton(stringResource(R.string.page_allow), Modifier.fillMaxWidth().padding(top = 8.dp)) { ask.launch(permissions.toTypedArray()) }
            }
            address != null -> ThreadPage(address, body, onBody, repository, conversations.orEmpty(), onChanged = { refresh++ })
            else -> ListPage(conversations, repository, onOpen = onAddress, onRefresh = { refresh++ })
        }
    }
}

@Composable
private fun ListPage(conversations: List<Conversation>?, repository: SmsRepository, onOpen: (String) -> Unit, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalMetroColors.current
    var isDefault by remember { mutableStateOf(SmsRole.isDefault(context)) }
    val role = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { isDefault = SmsRole.isDefault(context) }
    val roleIntent = remember { SmsRole.requestIntent(context) }
    var composing by remember { mutableStateOf(false) }
    var number by remember { mutableStateOf("") }
    val pager = rememberPagerState { 1 }
    BackHandler(enabled = composing) { composing = false }
    Column(Modifier.fillMaxSize()) {
        PivotHeader(listOf(stringResource(R.string.messages_title)), pager, Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp))
        Column(Modifier.weight(1f).padding(horizontal = 24.dp)) {
            if (!isDefault && roleIntent != null) {
                PageNote(stringResource(R.string.messages_default_note))
                MetroButton(stringResource(R.string.messages_default_button), Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp).testTag("messages:default")) { role.launch(roleIntent) }
            }
            if (composing) {
                MetroTextField(number, { number = it.filter { c -> c.isDigit() || c == '+' } }, stringResource(R.string.messages_to_hint), Modifier.padding(bottom = 6.dp).testTag("messages:to"), keyboardType = KeyboardType.Phone)
                MetroButton(stringResource(R.string.messages_open), Modifier.fillMaxWidth().padding(bottom = 8.dp)) { if (number.isNotEmpty()) { composing = false; onOpen(number); number = "" } }
            }
            if (conversations?.isEmpty() == true) PageNote(stringResource(R.string.messages_empty), Modifier.testTag("messages:empty"))
            LazyColumn(Modifier.fillMaxSize().testTag("messages:list")) {
                items(conversations.orEmpty(), key = { it.threadId }) { item ->
                    Row(
                        Modifier.fillMaxWidth().testTag("messages:thread").metroPress(
                            onClick = { onOpen(item.address) },
                            onLongClick = { if (isDefault) { repository.delete(item.threadId); onRefresh() } },
                        ).padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            MetroText(item.title, size = 22.sp, weight = if (item.unread > 0) FontWeight.SemiBold else FontWeight.Light)
                            MetroText(item.snippet, size = 14.sp, weight = FontWeight.Normal, color = colors.subtle)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            MetroText(whenText(item.date), size = 12.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1)
                            if (item.unread > 0) MetroText(item.unread.toString(), size = 16.sp, color = LocalAccent.current)
                        }
                    }
                }
            }
        }
        AppBar(
            listOf(
                AppBarItem("＋", stringResource(R.string.messages_new)) { composing = !composing },
                AppBarItem("↻", stringResource(R.string.messages_refresh), onRefresh),
            ),
        )
    }
}

@Composable
private fun ThreadPage(address: String, prefill: String, onBody: (String) -> Unit, repository: SmsRepository, conversations: List<Conversation>, onChanged: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val thread = conversations.firstOrNull { it.address == address }
    var version by remember { mutableIntStateOf(0) }
    val messages by produceState(emptyList<Sms>(), thread?.threadId, version) {
        value = thread?.let { withContext(Dispatchers.IO) { repository.messages(it.threadId) } }.orEmpty()
    }
    // What was sent while the launcher is not the messaging app is not written by Android: it stays here, for this visit.
    var sentHere by remember { mutableStateOf(emptyList<Sms>()) }
    var text by remember(address) { mutableStateOf(prefill.ifEmpty { repository.draft(address) }) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(thread?.threadId) {
        thread?.let {
            withContext(Dispatchers.IO) { repository.markRead(it.threadId) }
            SmsReceiver.cancel(context, address)
        }
    }
    DisposableEffect(address) { onDispose { repository.saveDraft(address, text); onBody("") } }
    val title = thread?.title ?: repository.nameOf(address) ?: address
    Column(Modifier.fillMaxSize()) {
        MetroText(title, size = 30.sp, maxLines = 1, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp))
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp).testTag("messages:bubbles"), reverseLayout = true, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items((messages + sentHere).sortedByDescending { it.date }, key = { "${it.id}:${it.date}" }) { sms ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (sms.outgoing) Arrangement.End else Arrangement.Start) {
                    Column(
                        Modifier.widthIn(max = 280.dp).background(if (sms.outgoing) accent else colors.surface).padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        MetroText(sms.body, size = 18.sp, weight = FontWeight.Normal, color = if (sms.outgoing) androidx.compose.ui.graphics.Color.White else colors.foreground)
                        MetroText(
                            if (sms.failed) stringResource(R.string.messages_failed) else whenText(sms.date), size = 11.sp, weight = FontWeight.Normal,
                            color = if (sms.outgoing) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f) else colors.subtle,
                        )
                    }
                }
            }
        }
        if (failed) PageNote(stringResource(R.string.messages_failed), Modifier.padding(horizontal = 24.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { MetroTextField(text, { text = it }, stringResource(R.string.messages_hint), Modifier.testTag("messages:field"), singleLine = false) }
            MetroButton(stringResource(R.string.messages_send), Modifier.padding(start = 8.dp).testTag("messages:send")) {
                val body = text.trim()
                if (body.isEmpty()) return@MetroButton
                if (repository.send(address, body)) {
                    failed = false
                    text = ""
                    if (!SmsRole.isDefault(context)) sentHere = sentHere + Sms(-System.currentTimeMillis(), body, System.currentTimeMillis(), outgoing = true)
                    version++
                    onChanged()
                } else {
                    failed = true
                }
            }
        }
    }
}
