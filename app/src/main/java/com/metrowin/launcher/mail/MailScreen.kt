package com.metrowin.launcher.mail

import com.metrowin.launcher.ui.components.topBarsPadding
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.pageBackground
import java.util.UUID
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.start.whenText
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.mail.Flags

/** Runs [block] off the main thread, with what it throws as the result. */
private suspend fun <T> io(block: () -> T): Result<T> = withContext(Dispatchers.IO) { runCatching(block) }

private const val INBOX = "INBOX"

/** How many of the latest messages get a line of their text in the list. */
private const val SNIPPETS = 15

@Composable
fun MailScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { MailRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf(repository.loadAccounts()) }
    val account = accounts.firstOrNull()
    var folder by remember { mutableStateOf(INBOX) }
    var reload by remember { mutableIntStateOf(0) }
    var messages by remember { mutableStateOf<List<MailMessage>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<MailMessage?>(null) }
    var body by remember { mutableStateOf<MailBody?>(null) }
    var bodyFailure by remember { mutableStateOf<String?>(null) }
    var composing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(Draft()) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    // The messages follow the account, the folder and the refresh button.
    LaunchedEffect(account?.id, folder, reload) {
        val client = account?.let(repository::client) ?: return@LaunchedEffect
        error = null
        // What was seen last time is shown at once, and the server's answer replaces it.
        if (messages == null) messages = repository.cached(account.id, folder)
        io { client.messages(folder) }
            .onSuccess { fresh ->
                // The lines of the messages already read stay while the new list comes in.
                val known = messages.orEmpty().associate { it.id to it.snippet }
                messages = fresh.map { it.copy(snippet = known[it.id].orEmpty()) }
                io { client.snippets(folder, fresh.take(SNIPPETS).map { it.id.toLong() }) }.onSuccess { lines ->
                    messages = messages?.map { m -> lines[m.id]?.let { m.copy(snippet = it) } ?: m }
                }
                messages?.let { repository.cache(account.id, folder, it) }
            }
            .onFailure { error = it.message ?: it.javaClass.simpleName; messages = messages ?: emptyList() }
    }

    if (account == null) {
        Box(modifier.fillMaxSize().pageBackground().topBarsPadding().navigationBarsPadding().padding(24.dp)) {
            OnboardingAccountSetup(onAccountAdded = { added, password ->
                repository.saveAccount(added, password)
                accounts = repository.loadAccounts()
                messages = null
            })
        }
        return
    }
    val client = repository.client(account)

    BackHandler(enabled = selected != null || composing) { if (composing) composing = false else selected = null }

    val open = selected
    if (open != null) {
        val opened = body
        fun answer(all: Boolean, forward: Boolean) {
            val who = open.senderName.ifBlank { open.senderAddress }
            val text = opened?.text.orEmpty()
            val own = account.email
            val cc = if (all && opened != null) {
                (opened.to + opened.cc).map { it.address }.filter { !it.equals(own, true) && !it.equals(open.senderAddress, true) }.distinct().joinToString(", ")
            } else {
                ""
            }
            draft = if (forward) {
                Draft(subject = "Fwd: ${open.subject}", text = "\n\n---------- $who <${open.senderAddress}> ----------\n${open.subject}\n\n$text")
            } else {
                Draft(to = opened?.replyTo ?: open.senderAddress, cc = cc, subject = "Re: ${open.subject}", text = "\n\n$who:\n" + text.lines().joinToString("\n") { "> $it" })
            }
            selected = null
            composing = true
        }
        MessageReaderView(
            message = open, body = body, failure = bodyFailure,
            actions = ReaderActions(
                onBack = { selected = null },
                onReply = { answer(all = false, forward = false) },
                onReplyAll = { answer(all = true, forward = false) },
                onForward = { answer(all = false, forward = true) },
                onDelete = {
                    messages = messages?.filter { it.id != open.id }
                    selected = null
                    scope.launch { client?.let { c -> io { c.delete(folder, open.id.toLong()) } } }
                },
                onFlag = {
                    val flagged = !open.isFlagged
                    selected = open.copy(isFlagged = flagged)
                    messages = messages?.map { if (it.id == open.id) it.copy(isFlagged = flagged) else it }
                    scope.launch { client?.let { c -> io { c.setFlag(folder, open.id.toLong(), Flags.Flag.FLAGGED, flagged) } } }
                },
                onUnread = {
                    messages = messages?.map { if (it.id == open.id) it.copy(isUnread = true) else it }
                    selected = null
                    scope.launch { client?.let { c -> io { c.setFlag(folder, open.id.toLong(), Flags.Flag.SEEN, false) } } }
                },
                fetchAttachment = { index -> client?.let { c -> io { c.attachment(folder, open.id.toLong(), index) } } ?: Result.failure(IllegalStateException("no password")) },
            ),
        )
        return
    }

    if (composing) {
        ComposeView(
            draft = draft, onClose = { composing = false },
            onSend = { d, files -> io { client?.send(d.to.trim(), d.cc.trim(), d.subject, d.text, files) ?: error("no password") }.map { Unit } },
        )
        return
    }

    val titles = listOf(R.string.mail_tab_inbox, R.string.mail_tab_unread, R.string.mail_tab_flagged, R.string.mail_tab_folders, R.string.mail_tab_accounts)
        .map { stringResource(it) }
    val pager = rememberPagerState { titles.size }
    // Back goes one step back inside the app: the search closes, another folder gives way to the inbox, another tab to the first one.
    BackHandler(enabled = searching || folder != INBOX || pager.currentPage != 0) {
        when {
            searching -> { searching = false; query = "" }
            pager.currentPage != 0 -> scope.launch { pager.animateScrollToPage(0) }
            else -> { folder = INBOX; messages = null }
        }
    }
    fun shown(filter: (MailMessage) -> Boolean) = messages?.filter(filter)?.filter {
        query.isBlank() || it.subject.contains(query, ignoreCase = true) || it.senderName.contains(query, ignoreCase = true)
    }
    fun read(message: MailMessage) {
        selected = message
        body = null
        bodyFailure = null
        messages = messages?.map { if (it.id == message.id) it.copy(isUnread = false) else it }
        scope.launch {
            client?.let { c -> io { c.body(folder, message.id.toLong()) } }
                ?.onSuccess { body = it }?.onFailure { bodyFailure = it.message ?: it.javaClass.simpleName }
        }
    }

    Column(modifier.fillMaxSize().pageBackground().topBarsPadding().navigationBarsPadding()) {
        Column(Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp)) {
            MetroText(account.email.uppercase(), size = 14.sp, weight = FontWeight.Bold, color = LocalAccent.current, maxLines = 1)
            PivotHeader(titles, pager, Modifier.padding(top = 4.dp))
        }
        if (searching) MetroTextField(query, { query = it }, stringResource(R.string.mail_bar_search), Modifier.padding(horizontal = 24.dp, vertical = 4.dp).testTag("mail:search"))
        error?.let { PageNote(stringResource(R.string.mail_error, it), Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) }
        HorizontalPager(pager, Modifier.weight(1f), beyondViewportPageCount = 1) { page ->
            Box(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                when (page) {
                    0 -> InboxPage(shown { true }, ::read)
                    1 -> InboxPage(shown { it.isUnread }, ::read)
                    2 -> InboxPage(shown { it.isFlagged }, ::read)
                    3 -> FoldersPage(client, current = folder) { path -> folder = path; messages = null; scope.launch { pager.animateScrollToPage(0) } }
                    else -> AccountsPage(
                        accounts = accounts,
                        onAddAccount = { added, password ->
                            repository.saveAccount(added, password)
                            accounts = repository.loadAccounts()
                            if (accounts.size == 1) messages = null
                        },
                        onRemoveAccount = { id ->
                            repository.removeAccount(id)
                            accounts = repository.loadAccounts()
                            messages = null
                            folder = INBOX
                        },
                    )
                }
            }
        }
        AppBar(
            listOf(
                AppBarItem("＋", stringResource(R.string.mail_bar_new)) { draft = Draft(); composing = true },
                AppBarItem("↻", stringResource(R.string.mail_bar_sync)) { reload++ },
                AppBarItem("⌕", stringResource(R.string.mail_bar_search)) { searching = !searching; if (!searching) query = "" },
            ),
        )
    }
}

/** The sign in of an account: the server is asked with the password before the account is kept. Returns the error text, or null. */
@Composable
private fun rememberSignIn(onAdded: (MailAccount, String) -> Unit): Triple<Boolean, String?, (String, String) -> Unit> {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val add = { email: String, password: String ->
        val detected = MailServiceDetector.detect(email)
        val account = MailAccount(
            id = UUID.randomUUID().toString(), email = email.trim(), providerName = detected.displayName,
            imapHost = detected.imapHost, imapPort = detected.imapPort, smtpHost = detected.smtpHost, smtpPort = detected.smtpPort,
            requiresAppPassword = detected.requiresAppPassword,
        )
        busy = true
        failure = null
        scope.launch {
            io { MailClient(account) { password }.check() }
                .onSuccess { onAdded(account, password) }
                .onFailure { failure = it.message ?: it.javaClass.simpleName }
            busy = false
        }
        Unit
    }
    return Triple(busy, failure, add)
}

/**
 * The sign in of a Microsoft account on the page of Microsoft: the browser opens, and when it comes back the code it brings is turned into
 * a token, which is tried on the mailbox before the account is kept. Returns whether it is busy, what went wrong, and the way to start.
 */
@Composable
private fun rememberMicrosoftSignIn(onAdded: (MailAccount, String) -> Unit): Triple<Boolean, String?, (String) -> Unit> {
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val redirect by MicrosoftOAuth.redirects.collectAsState()
    LaunchedEffect(redirect) {
        val uri = redirect ?: return@LaunchedEffect
        MicrosoftOAuth.redirects.value = null
        val waiting = MicrosoftOAuth.pending ?: return@LaunchedEffect
        MicrosoftOAuth.pending = null
        val code = uri.getQueryParameter("code")
        if (uri.getQueryParameter("state") != waiting.state || code == null) {
            failure = uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error") ?: "cancelled"
            busy = false
            return@LaunchedEffect
        }
        busy = true
        val detected = MailServiceDetector.detect(waiting.email)
        val account = MailAccount(
            id = UUID.randomUUID().toString(), email = waiting.email.trim(), providerName = detected.displayName,
            imapHost = detected.imapHost, imapPort = detected.imapPort, smtpHost = detected.smtpHost, smtpPort = detected.smtpPort,
            requiresAppPassword = false, oauth = "microsoft",
        )
        io {
            val tokens = MicrosoftOAuth.exchange(code, waiting)
            // The mailbox is tried with the token before the account is kept: it says at once if the permissions are not there.
            MailClient(account) { tokens.access }.check()
            tokens.refresh ?: error("no refresh token")
        }.onSuccess { refresh -> onAdded(account, refresh) }.onFailure { failure = it.message ?: it.javaClass.simpleName }
        busy = false
    }
    val start = { email: String ->
        failure = null
        busy = true
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, MicrosoftOAuth.authorizationUri(email.trim())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { busy = false; failure = it.message ?: it.javaClass.simpleName }
        Unit
    }
    return Triple(busy, failure, start)
}

/** The way in for an account that signs in on the page of its provider: no password is asked for in the app. */
@Composable
private fun MicrosoftSignInBlock(email: String, busy: Boolean, failure: String?, onStart: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PageNote(stringResource(R.string.mail_microsoft_hint))
        failure?.let { PageNote(stringResource(R.string.mail_login_failed, it)) }
        MetroButton(
            stringResource(if (busy) R.string.mail_signing_in else R.string.mail_sign_in_microsoft), Modifier.fillMaxWidth().testTag("mail:microsoft"),
        ) { if (!busy && email.contains("@")) onStart(email) }
    }
}

/** For an account with two-step verification: the way to the page of the provider where a password for apps is made, and back here to paste it. */
@Composable
private fun AppPasswordHelp(config: MailProviderConfig) {
    val context = LocalContext.current
    val url = config.appPasswordUrl ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PageNote(stringResource(R.string.mail_app_password_hint))
        MetroButton(
            stringResource(R.string.mail_app_password_open), Modifier.fillMaxWidth().testTag("mail:apppassword"),
            onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
        )
    }
}

/** Mandatory onboarding screen when no accounts exist, with auto-detection of email provider. */
@Composable
private fun OnboardingAccountSetup(onAccountAdded: (MailAccount, String) -> Unit) {
    val colors = LocalMetroColors.current
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    val detected = remember(emailInput) { MailServiceDetector.detect(emailInput) }
    val (busy, failure, signIn) = rememberSignIn(onAccountAdded)
    val (msBusy, msFailure, msStart) = rememberMicrosoftSignIn(onAccountAdded)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MetroText(stringResource(R.string.mail_setup_title), size = 34.sp, weight = FontWeight.Light, modifier = Modifier.padding(top = 16.dp))
        MetroText(stringResource(R.string.mail_setup_subtitle), size = 16.sp, color = colors.subtle, maxLines = Int.MAX_VALUE)
        Spacer(Modifier.height(8.dp))
        MetroTextField(emailInput, { emailInput = it }, stringResource(R.string.mail_email_address), Modifier.testTag("mail:onboarding:email"), keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
        if (emailInput.contains("@") && detected.domain.isNotBlank()) {
            Box(Modifier.fillMaxWidth().background(colors.containers.getOrElse(0) { colors.surface }).border(1.dp, LocalAccent.current).padding(12.dp)) {
                Column {
                    MetroText(stringResource(R.string.mail_detected_service, detected.displayName), size = 16.sp, weight = FontWeight.Bold, color = LocalAccent.current)
                    MetroText("IMAP: ${detected.imapHost}:${detected.imapPort} (TLS/SSL)", size = 13.sp, color = colors.subtle)
                }
            }
        }
        if (emailInput.contains("@") && detected.oauth != null) {
            // Microsoft does not take passwords for apps from personal accounts any more: the sign in is on its page.
            MicrosoftSignInBlock(emailInput, msBusy, msFailure, msStart)
        } else {
            if (emailInput.contains("@")) AppPasswordHelp(detected)
            MetroTextField(passwordInput, { passwordInput = it }, stringResource(R.string.mail_password), Modifier.testTag("mail:onboarding:password"), password = true)
            PageNote(stringResource(R.string.mail_security_note), Modifier.padding(top = 4.dp))
            failure?.let { PageNote(stringResource(R.string.mail_login_failed, it)) }
            Spacer(Modifier.height(12.dp))
            MetroButton(
                text = stringResource(if (busy) R.string.mail_signing_in else R.string.mail_add_account),
                modifier = Modifier.fillMaxWidth().testTag("mail:onboarding:save"),
            ) { if (!busy && emailInput.contains("@") && passwordInput.isNotEmpty()) signIn(emailInput, passwordInput) }
        }
    }
}

@Composable
private fun InboxPage(messages: List<MailMessage>?, onSelectMessage: (MailMessage) -> Unit) {
    val colors = LocalMetroColors.current
    if (messages == null) {
        PageNote(stringResource(R.string.mail_loading), Modifier.padding(top = 16.dp))
        return
    }
    if (messages.isEmpty()) {
        PageNote(stringResource(R.string.mail_empty_inbox), Modifier.padding(top = 16.dp))
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("mail:inbox")) {
        items(messages, key = { it.id }) { msg ->
            Row(Modifier.fillMaxWidth().metroPress(onClick = { onSelectMessage(msg) }).padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    MetroText(msg.senderName, size = 22.sp, weight = if (msg.isUnread) FontWeight.SemiBold else FontWeight.Light)
                    MetroText(msg.subject, size = 16.sp, weight = FontWeight.Normal, color = if (msg.isUnread) LocalAccent.current else colors.subtle)
                    if (msg.snippet.isNotBlank()) MetroText(msg.snippet, size = 13.sp, weight = FontWeight.Normal, color = colors.subtle.copy(alpha = 0.8f))
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                    MetroText(whenText(msg.timestamp), size = 12.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1)
                    if (msg.hasAttachments) MetroText("📎", size = 14.sp, color = colors.subtle)
                    if (msg.isFlagged) MetroText("⚑", size = 16.sp, color = LocalAccent.current)
                }
            }
        }
    }
}

@Composable
private fun FoldersPage(client: MailClient?, current: String, onPick: (String) -> Unit) {
    val folders by androidx.compose.runtime.produceState<List<MailFolder>?>(null, client) {
        value = client?.let { c -> io { c.folders() }.getOrDefault(emptyList()) } ?: emptyList()
    }
    if (folders == null) {
        PageNote(stringResource(R.string.mail_loading), Modifier.padding(top = 16.dp))
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("mail:folders")) {
        items(folders.orEmpty(), key = { it.path }) { f ->
            Row(Modifier.fillMaxWidth().metroPress(onClick = { onPick(f.path) }).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                MetroText(if (f.path == INBOX) stringResource(R.string.mail_folder_inbox) else f.name, size = 22.sp, color = if (f.path == current) LocalAccent.current else LocalMetroColors.current.foreground)
            }
        }
    }
}


@Composable
private fun AccountsPage(accounts: List<MailAccount>, onAddAccount: (MailAccount, String) -> Unit, onRemoveAccount: (String) -> Unit) {
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    val detected = remember(emailInput) { MailServiceDetector.detect(emailInput) }
    val (busy, failure, signIn) = rememberSignIn { account, password -> onAddAccount(account, password); emailInput = ""; passwordInput = "" }
    val (msBusy, msFailure, msStart) = rememberMicrosoftSignIn { account, refresh -> onAddAccount(account, refresh); emailInput = ""; passwordInput = "" }

    LazyColumn(Modifier.fillMaxSize().testTag("mail:accounts")) {
        item {
            Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PageNote(stringResource(R.string.mail_add_account))
                MetroTextField(emailInput, { emailInput = it }, stringResource(R.string.mail_email_address), keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
                if (emailInput.contains("@") && detected.domain.isNotBlank()) {
                    MetroText(stringResource(R.string.mail_detected_service, detected.displayName), size = 14.sp, color = LocalAccent.current, weight = FontWeight.SemiBold)
                }
                if (emailInput.contains("@") && detected.oauth != null) {
                    MicrosoftSignInBlock(emailInput, msBusy, msFailure, msStart)
                } else {
                    if (emailInput.contains("@")) AppPasswordHelp(detected)
                    MetroTextField(passwordInput, { passwordInput = it }, stringResource(R.string.mail_password), password = true)
                    failure?.let { PageNote(stringResource(R.string.mail_login_failed, it)) }
                    MetroButton(stringResource(if (busy) R.string.mail_signing_in else R.string.mail_add_account), Modifier.padding(top = 4.dp)) {
                        if (!busy && emailInput.contains("@") && passwordInput.isNotEmpty()) signIn(emailInput, passwordInput)
                    }
                }
            }
        }
        items(accounts, key = { it.id }) { acc ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    MetroText(acc.email, size = 20.sp, weight = FontWeight.SemiBold)
                    MetroText("${acc.providerName} · ${acc.imapHost}", size = 14.sp, color = LocalMetroColors.current.subtle)
                }
                MetroButton("✕", onClick = { onRemoveAccount(acc.id) })
            }
        }
    }
}

