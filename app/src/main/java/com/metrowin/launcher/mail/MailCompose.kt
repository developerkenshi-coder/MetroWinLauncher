package com.metrowin.launcher.mail

import com.metrowin.launcher.ui.components.centeredContent
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import com.metrowin.launcher.data.readAtMost
import androidx.compose.runtime.LaunchedEffect
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
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroProgressBar
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.topBarsPadding
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.pageBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A message being written: who it goes to (in copy, in hidden copy), the subject and the text, which may start with what it answers.
 * [inReplyTo] and [references] are the ids of the message it answers, to put it in the same conversation.
 */
data class Draft(
    val to: String = "", val cc: String = "", val bcc: String = "", val subject: String = "", val text: String = "",
    val inReplyTo: String = "", val references: String = "",
) {
    fun isBlank() = to.isBlank() && cc.isBlank() && bcc.isBlank() && subject.isBlank() && text.isBlank()

    companion object {
        /** For the state that survives the screen being recreated. */
        val Saver = androidx.compose.runtime.saveable.listSaver<Draft, String>(
            save = { listOf(it.to, it.cc, it.bcc, it.subject, it.text, it.inReplyTo, it.references) },
            restore = { Draft(it[0], it[1], it[2], it[3], it[4], it[5], it[6]) },
        )
    }
}

/** A file chosen to go with a message: its name, its kind and its bytes. */
typealias Outgoing = Triple<String, String, ByteArray>

/** The most that goes with one message: mail servers refuse a message much bigger than this. */
internal const val MAX_ATTACHED = 20L * 1024 * 1024

/**
 * The screen where a message is written: the addresses, the subject, the text and the files to send with it. The page scrolls, so a keyboard
 * in landscape does not squeeze the text to nothing. The text and the files are kept by the caller ([files] and [onFiles]), so Back loses neither.
 */
@Composable
fun ComposeView(
    draft: Draft, onChange: (Draft) -> Unit, onClose: () -> Unit, files: List<Outgoing>, onFiles: (List<Outgoing>) -> Unit,
    suggestContacts: Boolean, onSend: suspend (Draft, List<Outgoing>) -> Result<Unit>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf(draft) }
    LaunchedEffect(current) { onChange(current) }
    var sending by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var tooBig by remember { mutableStateOf(false) }
    var showBcc by remember { mutableStateOf(draft.bcc.isNotBlank()) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        scope.launch {
            val read = withContext(Dispatchers.IO) { uris.mapNotNull { runCatching { readFile(context, it) }.getOrNull() } }
            val all = files + read
            tooBig = read.size < uris.size || all.sumOf { it.third.size.toLong() } > MAX_ATTACHED
            if (!tooBig) onFiles(all)
        }
    }
    Column(
        Modifier.fillMaxSize().pageBackground().topBarsPadding().navigationBarsPadding().imePadding().centeredContent().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            MetroButton(stringResource(R.string.mail_back), onClick = onClose)
            MetroButton(stringResource(if (sending) R.string.mail_sending else R.string.mail_send), Modifier.testTag("mail:send")) {
                if (sending || current.to.isBlank()) return@MetroButton
                sending = true
                failure = null
                scope.launch {
                    onSend(current, files).onSuccess { onClose() }.onFailure { failure = MailErrors.text(context, it) }
                    sending = false
                }
            }
        }
        if (sending) MetroProgressBar(Modifier.fillMaxWidth().testTag("mail:sending"))
        // The page scrolls; the text has a height of its own so it is never squeezed to nothing.
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            failure?.let { PageNote(stringResource(R.string.mail_send_failed, it)) }
            if (tooBig) PageNote(stringResource(R.string.mail_too_big))
            RecipientField(current.to, { current = current.copy(to = it) }, stringResource(R.string.mail_to_hint), "mail:compose:to", suggestContacts)
            RecipientField(current.cc, { current = current.copy(cc = it) }, stringResource(R.string.mail_cc_hint), "mail:compose:cc", suggestContacts)
            if (showBcc) RecipientField(current.bcc, { current = current.copy(bcc = it) }, stringResource(R.string.mail_bcc_hint), "mail:compose:bcc", suggestContacts)
            else MetroText(stringResource(R.string.mail_bcc_add), size = 14.sp, color = LocalAccent.current, modifier = Modifier.metroPress(onClick = { showBcc = true }).padding(vertical = 4.dp).testTag("mail:compose:bccadd"))
            MetroTextField(current.subject, { current = current.copy(subject = it) }, stringResource(R.string.mail_subject))
            files.forEachIndexed { i, (name, mime, bytes) ->
                Row(Modifier.fillMaxWidth().metroPress(onClick = { onFiles(files.filterIndexed { j, _ -> j != i }) }).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    FileKindIcon(name, mime, 24.dp, LocalAccent.current)
                    MetroText(name, size = 15.sp, weight = FontWeight.Normal, color = LocalAccent.current, maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    MetroText("✕ ${bytes.size / 1024} KB", size = 12.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
                }
            }
            MetroButton(stringResource(R.string.mail_attach), Modifier.fillMaxWidth().testTag("mail:attach")) { pick.launch("*/*") }
            MetroTextField(current.text, { current = current.copy(text = it) }, stringResource(R.string.mail_body), singleLine = false, modifier = Modifier.heightIn(min = 220.dp))
        }
    }
}

/** A field of addresses; with [suggest] on, the contacts that start with what is being typed are listed under it, and a touch completes. */
@Composable
private fun RecipientField(value: String, onValue: (String) -> Unit, hint: String, tag: String, suggest: Boolean) {
    val context = LocalContext.current
    var found by remember { mutableStateOf(emptyList<MailAddress>()) }
    if (suggest) {
        LaunchedEffect(value) {
            delay(250)
            found = withContext(Dispatchers.IO) { MailContacts.suggest(context, MailContacts.lastPart(value)) }
        }
    }
    Column {
        MetroTextField(value, onValue, hint, Modifier.testTag(tag), keyboardType = KeyboardType.Email)
        if (suggest) found.forEach { who ->
            MetroText(
                if (who.name.isBlank()) who.address else "${who.name}  ${who.address}", size = 14.sp, weight = FontWeight.Normal, color = LocalAccent.current, maxLines = 1,
                modifier = Modifier.fillMaxWidth().metroPress(onClick = { onValue(MailContacts.complete(value, who)); found = emptyList() }).padding(vertical = 6.dp),
            )
        }
    }
}

private fun readFile(context: Context, uri: Uri): Outgoing {
    val resolver = context.contentResolver
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "file"
    // Stops at the limit instead of loading a file of any size into memory.
    val bytes = resolver.openInputStream(uri)?.use { it.readAtMost(MAX_ATTACHED.toInt()) } ?: error("unreadable or too big")
    return Triple(name, resolver.getType(uri) ?: "application/octet-stream", bytes)
}
