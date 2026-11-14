package com.metrowin.launcher.mail

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.core.net.toUri
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.MetroToggle
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** Runs [block] off the main thread, with what it throws as the result. */
internal suspend fun <T> io(block: () -> T): Result<T> = withContext(Dispatchers.IO) { runCatching(block) }

/** The servers the user wrote by hand, in place of the ones found from the address. */
class ManualServers(val imapHost: String, val imapPort: Int, val smtpHost: String, val smtpPort: Int)

/** The servers for [email]: the ones typed by hand if any, else the ones the provider publishes (when the user allowed that and the domain is not a known one), else the guess. Blocking. */
internal fun resolveServers(context: Context, email: String, manual: ManualServers?): MailProviderConfig {
    val detected = MailServiceDetector.detect(email)
    if (manual != null) return detected.copy(imapHost = manual.imapHost, imapPort = manual.imapPort, smtpHost = manual.smtpHost, smtpPort = manual.smtpPort, generic = false)
    if (detected.generic && MailOptions.on(context, MailOption.Autoconfig)) MailAutoconfig.lookup(detected.domain)?.let { return it }
    return detected
}

/** The sign in of an account: the server is asked with the password before the account is kept. Returns whether it is busy, the error text (or null) and the way to start. */
@Composable
internal fun rememberSignIn(onAdded: (MailAccount, String) -> Unit): Triple<Boolean, String?, (String, String, ManualServers?) -> Unit> {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val add = { email: String, password: String, manual: ManualServers? ->
        busy = true
        failure = null
        scope.launch {
            val account = withContext(Dispatchers.IO) {
                val found = resolveServers(context, email, manual)
                MailAccount(
                    id = UUID.randomUUID().toString(), email = email.trim(), providerName = found.displayName,
                    imapHost = found.imapHost, imapPort = found.imapPort, smtpHost = found.smtpHost, smtpPort = found.smtpPort,
                    requiresAppPassword = found.requiresAppPassword,
                )
            }
            io { MailClient(account) { password }.check() }
                .onSuccess { onAdded(account, password) }
                .onFailure { failure = MailErrors.text(context, it) }
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
internal fun rememberMicrosoftSignIn(onAdded: (MailAccount, String) -> Unit): Triple<Boolean, String?, (String) -> Unit> {
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
        }.onSuccess { refresh -> onAdded(account, refresh) }.onFailure { failure = MailErrors.text(context, it) }
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
    // A provider that takes the usual password has nothing to explain.
    if (!config.requiresAppPassword) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PageNote(stringResource(R.string.mail_app_password_hint))
        MetroButton(
            stringResource(R.string.mail_app_password_open), Modifier.fillMaxWidth().testTag("mail:apppassword"),
            onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
        )
    }
}

/**
 * The servers by hand: closed, the account uses what the address says; open, four fields (filled with what the address says) let the user
 * write the server and the port of IMAP and SMTP. Port 993 is IMAP over TLS, any other (143) starts in the clear and switches to TLS.
 */
@Composable
private fun ServerSettings(detected: MailProviderConfig, onChange: (ManualServers?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var imapHost by remember(detected.imapHost) { mutableStateOf(detected.imapHost) }
    var imapPort by remember(detected.imapPort) { mutableStateOf(detected.imapPort.toString()) }
    var smtpHost by remember(detected.smtpHost) { mutableStateOf(detected.smtpHost) }
    var smtpPort by remember(detected.smtpPort) { mutableStateOf(detected.smtpPort.toString()) }
    fun publish() {
        val ip = imapPort.toIntOrNull()
        val sp = smtpPort.toIntOrNull()
        onChange(if (open && ip != null && sp != null && imapHost.isNotBlank() && smtpHost.isNotBlank()) ManualServers(imapHost.trim(), ip, smtpHost.trim(), sp) else null)
    }
    MetroToggle(stringResource(R.string.mail_servers_manual), open, { open = it; publish() }, Modifier.testTag("mail:servers"))
    if (open) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MetroTextField(imapHost, { imapHost = it; publish() }, stringResource(R.string.mail_imap_host), Modifier.testTag("mail:imap:host"))
            MetroTextField(imapPort, { imapPort = it.filter(Char::isDigit); publish() }, stringResource(R.string.mail_imap_port), Modifier.testTag("mail:imap:port"), keyboardType = KeyboardType.Number)
            MetroTextField(smtpHost, { smtpHost = it; publish() }, stringResource(R.string.mail_smtp_host), Modifier.testTag("mail:smtp:host"))
            MetroTextField(smtpPort, { smtpPort = it.filter(Char::isDigit); publish() }, stringResource(R.string.mail_smtp_port), Modifier.testTag("mail:smtp:port"), keyboardType = KeyboardType.Number)
            PageNote(stringResource(R.string.mail_servers_hint))
        }
    }
}

/** Mandatory onboarding screen when no accounts exist, with auto-detection of email provider. */
@Composable
internal fun OnboardingAccountSetup(onAccountAdded: (MailAccount, String) -> Unit) {
    val colors = LocalMetroColors.current
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf<ManualServers?>(null) }
    val detected = remember(emailInput) { MailServiceDetector.detect(emailInput) }
    val (busy, failure, signIn) = rememberSignIn(onAccountAdded)
    val (msBusy, msFailure, msStart) = rememberMicrosoftSignIn(onAccountAdded)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MetroText(stringResource(R.string.mail_setup_title), size = 34.sp, weight = FontWeight.Light, modifier = Modifier.padding(top = 16.dp))
        MetroText(stringResource(R.string.mail_setup_subtitle), size = 16.sp, color = colors.subtle, maxLines = Int.MAX_VALUE)
        Spacer(Modifier.height(8.dp))
        MetroTextField(emailInput, { emailInput = it }, stringResource(R.string.mail_email_address), Modifier.testTag("mail:onboarding:email"), keyboardType = KeyboardType.Email)
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
            if (emailInput.contains("@")) ServerSettings(detected) { manual = it }
            PageNote(stringResource(R.string.mail_security_note), Modifier.padding(top = 4.dp))
            failure?.let { PageNote(stringResource(R.string.mail_login_failed, it)) }
            Spacer(Modifier.height(12.dp))
            MetroButton(
                text = stringResource(if (busy) R.string.mail_signing_in else R.string.mail_add_account),
                modifier = Modifier.fillMaxWidth().testTag("mail:onboarding:save"),
            ) { if (!busy && emailInput.contains("@") && passwordInput.isNotEmpty()) signIn(emailInput, passwordInput, manual) }
        }
    }
}

/** The accounts: a touch on one shows its mail, "✕" removes it; under them the way to add another. */
@Composable
internal fun AccountsPage(
    accounts: List<MailAccount>, currentId: String?, onSelect: (String) -> Unit,
    onAddAccount: (MailAccount, String) -> Unit, onRemoveAccount: (String) -> Unit,
) {
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf<ManualServers?>(null) }
    val detected = remember(emailInput) { MailServiceDetector.detect(emailInput) }
    val (busy, failure, signIn) = rememberSignIn { account, password -> onAddAccount(account, password); emailInput = ""; passwordInput = "" }
    val (msBusy, msFailure, msStart) = rememberMicrosoftSignIn { account, refresh -> onAddAccount(account, refresh); emailInput = ""; passwordInput = "" }

    LazyColumn(Modifier.fillMaxSize().testTag("mail:accounts")) {
        items(accounts, key = { it.id }) { acc ->
            Row(Modifier.fillMaxWidth().metroPress(onClick = { onSelect(acc.id) }).padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    MetroText(acc.email, size = 20.sp, weight = FontWeight.SemiBold, color = if (acc.id == currentId) LocalAccent.current else LocalMetroColors.current.foreground)
                    MetroText("${acc.providerName} · ${acc.imapHost}", size = 14.sp, color = LocalMetroColors.current.subtle)
                }
                MetroButton("✕", onClick = { onRemoveAccount(acc.id) })
            }
        }
        if (accounts.isNotEmpty()) item {
            val context = LocalContext.current
            var realtime by remember { mutableStateOf(MailIdle.enabled(context)) }
            Column(Modifier.padding(vertical = 16.dp)) {
                MetroToggle(stringResource(R.string.mail_realtime), realtime, { realtime = it; MailIdle.setEnabled(context, it) }, Modifier.testTag("mail:realtime"))
                PageNote(stringResource(R.string.mail_realtime_hint))
            }
        }
        item {
            Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PageNote(stringResource(R.string.mail_add_account))
                MetroTextField(emailInput, { emailInput = it }, stringResource(R.string.mail_email_address), keyboardType = KeyboardType.Email)
                if (emailInput.contains("@") && detected.domain.isNotBlank()) {
                    MetroText(stringResource(R.string.mail_detected_service, detected.displayName), size = 14.sp, color = LocalAccent.current, weight = FontWeight.SemiBold)
                }
                if (emailInput.contains("@") && detected.oauth != null) {
                    MicrosoftSignInBlock(emailInput, msBusy, msFailure, msStart)
                } else {
                    if (emailInput.contains("@")) AppPasswordHelp(detected)
                    MetroTextField(passwordInput, { passwordInput = it }, stringResource(R.string.mail_password), password = true)
                    if (emailInput.contains("@")) ServerSettings(detected) { manual = it }
                    failure?.let { PageNote(stringResource(R.string.mail_login_failed, it)) }
                    MetroButton(stringResource(if (busy) R.string.mail_signing_in else R.string.mail_add_account), Modifier.padding(top = 4.dp)) {
                        if (!busy && emailInput.contains("@") && passwordInput.isNotEmpty()) signIn(emailInput, passwordInput, manual)
                    }
                }
            }
        }
    }
}
