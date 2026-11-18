package com.metrowin.launcher.sharein

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.notes.Note
import com.metrowin.launcher.notes.NoteFiles
import com.metrowin.launcher.notes.NoteStore
import com.metrowin.launcher.todo.TodoQuick
import com.metrowin.launcher.ui.actioncenter.MetroToast
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.mini.MiniActivity
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.mini.miniPageSide
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.wallet.Card
import com.metrowin.launcher.wallet.CodeFormat
import com.metrowin.launcher.wallet.Wallet
import com.metrowin.launcher.wallet.WalletVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** What "Share" in another app brings: the words, and the pictures. */
class SharedIn(val text: String, val subject: String, val pictures: List<Uri>) {
    /** The first line of the words, or the subject: what a note or a card is called. */
    val title: String get() = subject.trim().ifEmpty { text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim() }

    companion object {
        const val MAX_PICTURES = 10

        @Suppress("DEPRECATION")
        fun of(intent: Intent): SharedIn {
            val one = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            val many = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            return SharedIn(
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty(),
                intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty(),
                (listOfNotNull(one) + many).distinct().take(MAX_PICTURES),
            )
        }
    }
}

/** "Share" in any app lists MetroWin: what comes is kept as a note, a thing to do or a card of the wallet. */
class ShareInActivity : MiniActivity() {
    private val shared by lazy { SharedIn.of(intent) }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val colors = LocalMetroColors.current
        val got = remember { shared }
        fun run(done: Int, work: suspend () -> Boolean) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { runCatching { work() }.getOrDefault(false) }
                MetroToast.show(context, if (ok) done else R.string.share_in_failed)
                if (ok) finish()
            }
        }
        MiniFrame {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).miniPageSide(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MetroText(stringResource(R.string.share_in_title), size = 34.sp, modifier = Modifier.padding(top = 14.dp))
                if (got.text.isNotBlank()) MetroText(got.text.trim(), size = 16.sp, color = colors.subtle, maxLines = 6)
                if (got.pictures.isNotEmpty()) MetroText(stringResource(R.string.share_in_pictures, got.pictures.size), size = 16.sp, color = colors.subtle)
                if (got.text.isBlank() && got.pictures.isEmpty()) MetroText(stringResource(R.string.share_in_nothing), size = 18.sp)
                else {
                    MetroButton(stringResource(R.string.share_in_note), Modifier.fillMaxWidth().testTag("share:note")) { run(R.string.share_in_done_note) { saveNote(context, got) } }
                    if (got.text.isNotBlank()) MetroButton(stringResource(R.string.share_in_todo), Modifier.fillMaxWidth().testTag("share:todo")) {
                        run(R.string.share_in_done_todo) { saveTodo(context, got) }
                    }
                    MetroButton(stringResource(R.string.share_in_card), Modifier.fillMaxWidth().testTag("share:card")) {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { runCatching { saveCard(context, got) }.getOrDefault(CardResult.Failed) }
                            when (result) {
                                CardResult.Saved -> { MetroToast.show(context, R.string.share_in_done_card); finish() }
                                CardResult.NoCode -> MetroToast.show(context, R.string.share_in_no_code)
                                CardResult.Failed -> MetroToast.show(context, R.string.share_in_failed)
                            }
                        }
                    }
                }
            }
        }
    }

    private enum class CardResult { Saved, NoCode, Failed }

    private fun saveNote(context: Context, got: SharedIn): Boolean {
        val files = NoteFiles(context)
        val names = got.pictures.mapNotNull { uri ->
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(context.contentResolver.getType(uri)) ?: "jpg"
            context.contentResolver.openInputStream(uri)?.use { files.add(it, ext) }
        }
        val store = NoteStore(context)
        val now = System.currentTimeMillis()
        val note = Note(UUID.randomUUID().toString(), got.subject.trim().take(60), got.text.trim(), files = names, created = now, updated = now)
        if (note.isBlank) return false
        return store.save(store.load() + note)
    }

    private fun saveTodo(context: Context, got: SharedIn): Boolean {
        val lines = got.text.trim().lines().filter { it.isNotBlank() }
        val head = got.subject.trim().ifEmpty { lines.firstOrNull().orEmpty() }
        val rest = if (got.subject.isBlank()) lines.drop(1) else lines
        return TodoQuick.add(context, head, note = rest.joinToString("\n"))
    }

    private fun saveCard(context: Context, got: SharedIn): CardResult {
        // A picture of a code is read; the words of a text are the code itself, drawn as a QR code.
        val read = got.pictures.firstNotNullOfOrNull { uri ->
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }?.let { Wallet.read(it) }
        } ?: got.text.trim().takeIf { Wallet.valid(it, CodeFormat.Qr) }?.let { it to CodeFormat.Qr }
        val (code, format) = read ?: return CardResult.NoCode
        val vault = WalletVault(context)
        val cards = vault.load()
        if (vault.unreadable || cards.size >= Wallet.MAX_CARDS) return CardResult.Failed
        val name = got.title.ifBlank { code }.take(Wallet.MAX_NAME)
        val card = Card(UUID.randomUUID().toString(), name, code.take(Wallet.MAX_CODE), format, created = System.currentTimeMillis())
        return if (vault.save(cards + card)) CardResult.Saved else CardResult.Failed
    }
}
