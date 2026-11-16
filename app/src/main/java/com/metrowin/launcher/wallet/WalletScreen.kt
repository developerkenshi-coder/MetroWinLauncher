package com.metrowin.launcher.wallet

import com.metrowin.launcher.ui.components.PageSwitch
import android.app.Activity
import android.app.KeyguardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.metrowin.launcher.R
import com.metrowin.launcher.data.readAtMost
import com.metrowin.launcher.ui.actioncenter.MetroToast
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroToggle
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/** The page that is open, as a short text so it can be kept over a turn of the phone: "" the list, "v:id" a card, "e:id" an edit, "n:id" a new card. */
private const val LIST = ""

/** For how long an opened card stays open to the next one without asking for the lock of the phone again. */
private const val UNLOCK_MS = 60_000L

private class ExportJob(val cards: List<Card>, val passphrase: String?)

@Composable
fun WalletScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vault = remember { WalletVault(context) }
    val prefs = remember { WalletPrefs(context) }
    val lock = remember { Mutex() }
    val latest = remember { AtomicReference<List<Card>>(emptyList()) }
    var cards by remember { mutableStateOf<List<Card>?>(null) }
    var unreadable by remember { mutableStateOf(false) }
    var pageKey by rememberSaveable { mutableStateOf(LIST) }
    var lastUnlock by rememberSaveable { mutableLongStateOf(0L) }
    var pendingOpen by rememberSaveable { mutableStateOf<String?>(null) }
    var options by rememberSaveable { mutableStateOf(false) }
    val today = rememberToday()
    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { vault.load() to vault.unreadable }
        latest.set(loaded.first)
        cards = loaded.first
        unreadable = loaded.second
        if (loaded.second) MetroToast.show(context, R.string.wal_unreadable)
    }
    val all = cards.orEmpty()
    KeepSecure(prefs.secure.on && pageKey != LIST)

    /** Every change is written, in the order it was made: each write takes the newest list, so an older one never lands on top of a newer. */
    fun commit(next: List<Card>) {
        cards = next
        latest.set(next)
        scope.launch(Dispatchers.IO) {
            if (!lock.withLock { vault.save(latest.get()) }) withContext(Dispatchers.Main) { MetroToast.show(context, R.string.wal_save_failed) }
        }
    }

    // --- the lock of the phone, if the user asked for it
    val keyguard = remember { context.getSystemService(KeyguardManager::class.java) }
    val unlockIntent = remember { runCatching { keyguard?.createConfirmDeviceCredentialIntent(context.getString(R.string.wal_unlock_title), context.getString(R.string.wal_unlock_text)) }.getOrNull() }
    fun needsUnlock() = prefs.lock.on && unlockIntent != null && System.currentTimeMillis() - lastUnlock > UNLOCK_MS

    fun openNow(id: String) {
        if (prefs.history.on) commit(all.map { if (it.id == id) it.copy(lastUsed = System.currentTimeMillis(), uses = it.uses + 1) else it })
        pageKey = "v:$id"
    }
    val unlock = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pendingOpen
        pendingOpen = null
        if (result.resultCode == Activity.RESULT_OK) {
            lastUnlock = System.currentTimeMillis()
            if (id != null) openNow(id)
        }
    }
    fun open(card: Card) {
        if (needsUnlock()) { pendingOpen = card.id; unlock.launch(unlockIntent!!) } else openNow(card.id)
    }
    // A card restored by the system after the lock ran out is not shown without it.
    LaunchedEffect(pageKey, prefs.lock.on) { if (pageKey.startsWith("v:") && needsUnlock() && pendingOpen == null) pageKey = LIST }

    // --- backups
    var exportJob by remember { mutableStateOf<ExportJob?>(null) }
    var askExport by remember { mutableStateOf<List<Card>?>(null) }
    var pendingImport by remember { mutableStateOf<ByteArray?>(null) }
    val written: (android.net.Uri?) -> Unit = { uri ->
        val job = exportJob
        exportJob = null
        if (uri != null && job != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(WalletBackup.write(job.cards, job.passphrase)) } }.isSuccess }
                MetroToast.show(context, if (ok) R.string.wal_export_ok else R.string.wal_export_failed)
            }
        }
    }
    val exportPlain = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), written)
    val exportSealed = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream"), written)
    fun launchExport(list: List<Card>, passphrase: String?) {
        exportJob = ExportJob(list, passphrase)
        if (passphrase == null) exportPlain.launch("metrowin-wallet-" + LocalDate.now() + ".json") else exportSealed.launch("metrowin-wallet-" + LocalDate.now() + ".mwallet")
    }
    fun startExport(list: List<Card>, sealed: Boolean) { if (sealed || prefs.encryptedExport.on) askExport = list else launchExport(list, null) }

    fun finishImport(bytes: ByteArray?, passphrase: String?) {
        scope.launch {
            val result = withContext(Dispatchers.Default) { WalletBackup.read(bytes, passphrase) }
            when (result) {
                is Imported.Cards -> {
                    pendingImport = null
                    val incoming = result.rgb?.let { rgb -> result.cards.map { it.copy(color = nearestAccent(rgb)) } } ?: result.cards
                    val merged = Wallet.merge(all, incoming)
                    val count = Wallet.accepted(merged, incoming)
                    commit(merged)
                    MetroToast.show(context, context.resources.getQuantityString(R.plurals.wal_imported, count, count))
                }
                Imported.NeedsPassphrase -> pendingImport = bytes
                Imported.WrongPassphrase -> { pendingImport = bytes; MetroToast.show(context, R.string.wal_import_wrong_pass) }
                Imported.TooBig -> MetroToast.show(context, R.string.wal_import_too_big)
                Imported.Unreadable -> { pendingImport = null; MetroToast.show(context, R.string.wal_import_unreadable) }
            }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            // null from the bounded read is "too long"; a stream that cannot be opened is "unreadable".
            val read = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readAtMost(Wallet.MAX_IMPORT_BYTES) } } }
            if (read.isFailure) MetroToast.show(context, R.string.wal_import_unreadable)
            else if (read.getOrNull() == null) MetroToast.show(context, R.string.wal_import_too_big)
            else finishImport(read.getOrNull(), null)
        }
    }

    // --- the pages
    val still = LocalReduceMotion.current || !prefs.motion.on
    Box(Modifier.background(LocalMetroColors.current.background)) {
        PageSwitch(
            page = pageKey.takeIf { it != LIST },
            onBack = { pageKey = LIST },
            animated = !still,
            list = {
                CardList(
                    cards, prefs, today, unreadable && all.isEmpty(), onOpen = ::open,
                    onNew = { pageKey = "n:${UUID.randomUUID()}" },
                    onExport = { startExport(all, sealed = false) },
                    onImport = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "application/vnd.apple.pkpass")) },
                    onOptions = { options = true },
                )
            },
            detail = { key ->
                val id = key.drop(2)
                when {
                    key.startsWith("v:") -> {
                        val found = all.firstOrNull { it.id == id }
                        // The page that is leaving after a delete still has the card to draw.
                        val kept = remember(id) { arrayOfNulls<Card>(1) }
                        if (found != null) kept[0] = found
                        val card = found ?: kept[0]
                        if (card == null) LaunchedEffect(Unit) { pageKey = LIST }
                        else CardView(
                            card, prefs, today, onEdit = { pageKey = "e:${card.id}" },
                            onDelete = {
                                val index = all.indexOfFirst { it.id == card.id }
                                commit(all.filter { it.id != card.id }); pageKey = LIST
                                // The card that was deleted can be brought back while the banner is up.
                                MetroToast.show(context, context.getString(R.string.wal_deleted), context.getString(R.string.wal_undo)) {
                                    commit(latest.get().toMutableList().apply { add(index.coerceIn(0, size), card) })
                                }
                            },
                            onBack = { pageKey = LIST },
                            onFavorite = { commit(all.map { if (it.id == card.id) it.copy(favorite = !it.favorite) else it }) },
                            onExport = { startExport(listOf(card), sealed = true) },
                        )
                    }
                    key.startsWith("e:") || key.startsWith("n:") -> {
                        val existing = all.firstOrNull { it.id == id }
                        val card = existing ?: remember(id) { Card(id, "", "", created = System.currentTimeMillis()) }
                        CardEdit(
                            card, prefs, today,
                            onSave = { saved ->
                                commit(if (all.any { it.id == saved.id }) all.map { if (it.id == saved.id) saved else it } else all + saved)
                                pageKey = "v:${saved.id}"
                            },
                            onCancel = { pageKey = if (existing != null) "v:$id" else LIST },
                        )
                    }
                    else -> Unit
                }
            },
        )
    }
    askExport?.let { list -> PassphraseDialog(twice = true, onDone = { askExport = null; launchExport(list, it) }, onCancel = { askExport = null }) }
    pendingImport?.let { bytes -> PassphraseDialog(twice = false, onDone = { finishImport(bytes, it) }, onCancel = { pendingImport = null }) }
    if (options) OptionsDialog(prefs) { options = false }
}

@Composable
private fun OptionsDialog(prefs: WalletPrefs, onClose: () -> Unit) {
    DialogFrame(onClose, title = stringResource(R.string.wal_opt_title)) {
        listOf(
            R.string.wal_opt_secure to prefs.secure, R.string.wal_opt_enc_export to prefs.encryptedExport, R.string.wal_opt_lock to prefs.lock,
            R.string.wal_opt_history to prefs.history, R.string.wal_opt_motion to prefs.motion, R.string.wal_opt_expiry to prefs.expiry,
            R.string.wal_opt_preview to prefs.preview, R.string.wal_opt_rich to prefs.rich, R.string.wal_opt_grid to prefs.grid,
            R.string.wal_opt_group to prefs.group, R.string.wal_opt_scan to prefs.scanExtras,
        ).forEach { (label, flag) -> MetroToggle(stringResource(label), flag.on, flag::set) }
        MetroButton(stringResource(android.R.string.ok), Modifier.fillMaxWidth().testTag("wal:options:ok"), onClick = onClose)
    }
}
