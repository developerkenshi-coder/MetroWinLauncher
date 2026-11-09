package com.metrowin.launcher.mail

import com.metrowin.launcher.ui.components.centeredContent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.topBarsPadding
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.start.whenText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.pageBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the buttons of the reader do. Each is one line of the screen of the mail. */
class ReaderActions(
    val onBack: () -> Unit,
    val onReply: () -> Unit,
    val onReplyAll: () -> Unit,
    val onForward: () -> Unit,
    val onDelete: () -> Unit,
    val onFlag: () -> Unit,
    val onUnread: () -> Unit,
    /** The bytes of the file number [MailAttachment.index] of the open message. */
    val fetchAttachment: suspend (Int) -> Result<Triple<String, String, ByteArray>>,
)

/**
 * A message, as the mail of Windows Phone shows it: the subject, who wrote it (the first letter on the accent colour), who it was written
 * to, when, the files that came with it, and then the message itself. A message with a page is shown as a page, with its own pictures
 * inside it; the pictures that live on other servers are not loaded until [R.string.mail_show_images] is touched, because they tell the
 * sender that the message was read.
 */
@Composable
fun MessageReaderView(message: MailMessage, body: MailBody?, failure: String?, actions: ReaderActions) {
    val colors = LocalMetroColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var images by remember(message.id) { mutableStateOf(false) }
    val html = body?.html
    val hasRemoteImages = html != null && REMOTE_IMAGE.containsMatchIn(html)
    Column(Modifier.fillMaxSize().pageBackground().topBarsPadding().navigationBarsPadding().centeredContent()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MetroText(message.subject.ifBlank { "—" }, size = 24.sp, weight = FontWeight.Normal, maxLines = 3, lineHeight = 29.sp, modifier = Modifier.testTag("mail:subject"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(LocalAccent.current), contentAlignment = Alignment.Center) {
                    MetroText(message.senderName.firstOrNull { it.isLetterOrDigit() }?.uppercase().orEmpty(), size = 22.sp, color = Color.White)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    MetroText(message.senderName.ifBlank { message.senderAddress }, size = 18.sp, weight = FontWeight.SemiBold, color = LocalAccent.current)
                    MetroText(message.senderAddress, size = 13.sp, weight = FontWeight.Normal, color = colors.subtle)
                }
                MetroText(whenText(message.timestamp), size = 12.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1)
            }
            body?.let { b ->
                if (b.to.isNotEmpty()) PeopleLine(stringResource(R.string.mail_to), b.to, "mail:to")
                if (b.cc.isNotEmpty()) PeopleLine(stringResource(R.string.mail_cc), b.cc, "mail:cc")
                if (b.attachments.isNotEmpty()) Attachments(b.attachments, context, scope, actions.fetchAttachment)
            }
            if (hasRemoteImages && !images) {
                MetroButton(stringResource(R.string.mail_show_images), Modifier.fillMaxWidth().testTag("mail:showimages")) { images = true }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                body == null && failure == null -> MetroText(stringResource(R.string.mail_loading), size = 16.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.padding(24.dp))
                failure != null -> PageNote(stringResource(R.string.mail_error, failure), Modifier.padding(24.dp))
                html != null -> HtmlBody(html, images, Modifier.fillMaxSize().testTag("mail:html"))
                else -> MetroText(
                    body?.text.orEmpty().ifBlank { "—" }, size = 16.sp, weight = FontWeight.Normal, color = colors.foreground, maxLines = Int.MAX_VALUE, lineHeight = 22.sp,
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp).testTag("mail:text"),
                )
            }
        }
        AppBar(
            listOf(
                AppBarItem("↩", stringResource(R.string.mail_reply), actions.onReply),
                AppBarItem("↩↩", stringResource(R.string.mail_reply_all), actions.onReplyAll),
                AppBarItem("↪", stringResource(R.string.mail_forward), actions.onForward),
                AppBarItem("🗑", stringResource(R.string.mail_delete), actions.onDelete),
            ),
            menu = listOf(
                AppBarItem(if (message.isFlagged) "⚐" else "⚑", stringResource(R.string.mail_flag), actions.onFlag),
                AppBarItem("✉", stringResource(R.string.mail_mark_unread), actions.onUnread),
                AppBarItem("←", stringResource(R.string.mail_back), actions.onBack),
            ),
        )
    }
}

private val REMOTE_IMAGE = Regex("""(?i)<img[^>]+src\s*=\s*["']?\s*https?:""")

@Composable
private fun PeopleLine(label: String, people: List<MailAddress>, tag: String) {
    MetroText(
        "$label  " + people.joinToString(", ") { it.shown }, size = 13.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle,
        maxLines = 3, modifier = Modifier.testTag(tag),
    )
}

/** The files that came with the message: a touch downloads one (into the Downloads of the phone) and opens it. */
@Composable
private fun Attachments(
    files: List<MailAttachment>,
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    fetch: suspend (Int) -> Result<Triple<String, String, ByteArray>>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("mail:attachments")) {
        files.forEach { file ->
            var busy by remember(file.index) { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().testTag("mail:attachment:${file.index}").metroPress(onClick = {
                    if (busy) return@metroPress
                    busy = true
                    scope.launch {
                        fetch(file.index).onSuccess { (name, mime, bytes) ->
                            val saved = withContext(Dispatchers.IO) { runCatching { saveDownload(context, name, mime, bytes) }.getOrNull() }
                            if (saved == null) {
                                Toast.makeText(context, R.string.mail_download_failed, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.mail_saved, name), Toast.LENGTH_SHORT).show()
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(saved, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }
                        }.onFailure { Toast.makeText(context, R.string.mail_download_failed, Toast.LENGTH_SHORT).show() }
                        busy = false
                    }
                }).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AttachmentThumb(file, fetch)
                MetroText(file.name, size = 15.sp, weight = FontWeight.Normal, color = LocalAccent.current, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                MetroText(if (busy) "…" else sizeText(file.size), size = 12.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
            }
        }
    }
}

private fun sizeText(bytes: Long): String = when {
    bytes < 0 -> ""
    bytes < 1_024 -> "$bytes B"
    bytes < 1_048_576 -> "${bytes / 1_024} KB"
    else -> "%.1f MB".format(bytes / 1_048_576.0)
}

/** Writes the file into the Downloads of the phone and gives the address it can be opened at. */
private fun saveDownload(context: Context, name: String, mime: String, bytes: ByteArray): Uri {
    val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "attachment" }
    if (Build.VERSION.SDK_INT >= 29) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, safe)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("no place")
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("no place")
        return uri
    }
    // Before Android 10 a file in the Downloads would need a permission of its own: it goes to the folder of the app, and is opened from there.
    val dir = java.io.File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "").apply { mkdirs() }
    val file = java.io.File(dir, safe).apply { writeBytes(bytes) }
    return androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

/**
 * A message that is a page. No script runs in it, no file of the phone can be read by it, and a link goes to the browser, not into the
 * page. The pictures that live on other servers load only when [allowRemoteImages] says so (the page itself says it in the header it is
 * given, which the browser obeys): they tell the sender that the message was read.
 */
@Composable
private fun HtmlBody(html: String, allowRemoteImages: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val page = remember(html, allowRemoteImages) {
        val images = if (allowRemoteImages) "data: https: http:" else "data:"
        """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src $images; style-src 'unsafe-inline'; font-src data:">
<style>body{margin:12px;word-wrap:break-word;font-family:sans-serif;font-size:16px}img{max-width:100%;height:auto}table{max-width:100%}</style>
</head><body>$html</body></html>"""
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                setBackgroundColor(android.graphics.Color.WHITE)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val uri = request.url
                        if (uri.scheme in setOf("http", "https", "mailto", "tel")) {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                        return true
                    }
                }
            }
        },
        update = { view ->
            if (view.tag != page) {
                view.tag = page
                view.loadDataWithBaseURL(null, page, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
}
