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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.topBarsPadding
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.pageBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A message being written: who it goes to (and in copy), the subject and the text, which may start with what it answers. */
data class Draft(val to: String = "", val cc: String = "", val subject: String = "", val text: String = "")

/** A file chosen to go with a message: its name, its kind and its bytes. */
typealias Outgoing = Triple<String, String, ByteArray>

/** The most that goes with one message: mail servers refuse a message much bigger than this. */
private const val MAX_ATTACHED = 20L * 1024 * 1024

/** The screen where a message is written: the addresses, the subject, the text and the files to send with it. */
@Composable
fun ComposeView(draft: Draft, onChange: (Draft) -> Unit, onClose: () -> Unit, onSend: suspend (Draft, List<Outgoing>) -> Result<Unit>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf(draft) }
    // The text is kept by the screen, so Back does not lose it; the files are not (shortcut: re-attach after Back).
    LaunchedEffect(current) { onChange(current) }
    var files by remember { mutableStateOf(emptyList<Outgoing>()) }
    var sending by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var tooBig by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        scope.launch {
            val read = withContext(Dispatchers.IO) { uris.mapNotNull { runCatching { readFile(context, it) }.getOrNull() } }
            val all = files + read
            tooBig = read.size < uris.size || all.sumOf { it.third.size.toLong() } > MAX_ATTACHED
            if (!tooBig) files = all
        }
    }
    Column(Modifier.fillMaxSize().pageBackground().topBarsPadding().navigationBarsPadding().imePadding().centeredContent().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            MetroButton(stringResource(R.string.mail_back), onClick = onClose)
            MetroButton(stringResource(if (sending) R.string.mail_sending else R.string.mail_send), Modifier.testTag("mail:send")) {
                if (sending || current.to.isBlank()) return@MetroButton
                sending = true
                failure = null
                scope.launch {
                    onSend(current, files).onSuccess { onClose() }.onFailure { failure = it.message ?: it.javaClass.simpleName }
                    sending = false
                }
            }
        }
        failure?.let { PageNote(stringResource(R.string.mail_send_failed, it)) }
        if (tooBig) PageNote(stringResource(R.string.mail_too_big))
        MetroTextField(current.to, { current = current.copy(to = it) }, stringResource(R.string.mail_to_hint), Modifier.testTag("mail:compose:to"), keyboardType = KeyboardType.Email)
        MetroTextField(current.cc, { current = current.copy(cc = it) }, stringResource(R.string.mail_cc_hint), Modifier.testTag("mail:compose:cc"), keyboardType = KeyboardType.Email)
        MetroTextField(current.subject, { current = current.copy(subject = it) }, stringResource(R.string.mail_subject))
        files.forEachIndexed { i, (name, _, bytes) ->
            Row(Modifier.fillMaxWidth().metroPress(onClick = { files = files.filterIndexed { j, _ -> j != i } }).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                MetroText("📎", size = 16.sp)
                MetroText(name, size = 15.sp, weight = FontWeight.Normal, color = LocalAccent.current, maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                MetroText("✕ ${bytes.size / 1024} KB", size = 12.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
            }
        }
        MetroButton(stringResource(R.string.mail_attach), Modifier.fillMaxWidth().testTag("mail:attach")) { pick.launch("*/*") }
        MetroTextField(current.text, { current = current.copy(text = it) }, stringResource(R.string.mail_body), singleLine = false, modifier = Modifier.weight(1f))
    }
}

private fun readFile(context: Context, uri: Uri): Outgoing {
    val resolver = context.contentResolver
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "file"
    // Stops at the limit instead of loading a file of any size into memory.
    val bytes = resolver.openInputStream(uri)?.use { it.readAtMost(MAX_ATTACHED.toInt()) } ?: error("unreadable or too big")
    return Triple(name, resolver.getType(uri) ?: "application/octet-stream", bytes)
}
