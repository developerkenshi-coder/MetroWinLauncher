package com.metrowin.launcher.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.PageSwitch
import com.metrowin.launcher.ui.components.isWide
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.actioncenter.BANNER_MS
import android.app.Activity
import android.app.KeyguardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.actioncenter.MetroToast
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.DialogFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/** Which screen of the notes is open: the list, a note, or the sketch of a note. */
private sealed interface Page {
    data object List : Page
    data class Edit(val id: String) : Page
    data class Sketch(val id: String) : Page
}

/** The page is kept as text ("", "E<id>", "S<id>") across a restart of the screen or of the process; the note itself is on disk. */
private val PageSaver = Saver<Page, String>(
    save = { when (it) { Page.List -> ""; is Page.Edit -> "E" + it.id; is Page.Sketch -> "S" + it.id } },
    restore = { when { it.startsWith("E") -> Page.Edit(it.drop(1)); it.startsWith("S") -> Page.Sketch(it.drop(1)); else -> Page.List } },
)

/** What the password dialog is for: a backup about to be written, or one that is sealed and about to be read. */
private sealed interface Ask {
    data object Backup : Ask
    data class Restore(val uri: android.net.Uri) : Ask
}

@Composable
fun NotesScreen(openId: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { NoteStore(context) }
    val noteFiles = remember { NoteFiles(context) }
    val prefs = remember { NotePrefs(context) }
    // What is saved is saved by a scope of its own: when the screen closes with a save under way, the save is not cancelled with it.
    val saver = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    val lock = remember { Mutex() }
    val pending = remember { AtomicReference<List<Note>?>(null) }
    var notes by remember { mutableStateOf<List<Note>?>(null) }
    // A file of notes that the key of the phone no longer opens is kept aside; the list says so, and says what can be done.
    var lost by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    var page by rememberSaveable(stateSaver = PageSaver) { mutableStateOf<Page>(Page.List) }
    var ask by remember { mutableStateOf<Ask?>(null) }
    var password by remember { mutableStateOf("") }
    // The notes that asked for the lock of the phone and got it, until the app is closed.
    var unlocked by remember { mutableStateOf(emptySet<String>()) }
    var waiting by remember { mutableStateOf<String?>(null) }

    fun toast(id: Int, vararg args: Any) = runCatching { MetroToast.show(context, context.getString(id, *args)) }

    fun toastCount(id: Int, count: Int) = MetroToast.show(context, context.resources.getQuantityString(id, count, count))

    /** The notes are what [list] says from now on, and what is on the phone follows: in order, the last list wins, and a save that fails says so once. */
    fun commit(list: List<Note>) {
        notes = list
        pending.set(list)
        saver.launch {
            lock.withLock {
                val next = pending.getAndSet(null) ?: return@withLock
                val saved = store.save(next)
                withContext(Dispatchers.Main) {
                    if (!saved && !saveFailed) toast(R.string.notes_save_failed)
                    saveFailed = !saved
                }
            }
        }
    }

    fun replace(note: Note) {
        val list = notes.orEmpty()
        commit(if (list.any { it.id == note.id }) list.map { if (it.id == note.id) note else it } else list + note)
    }

    fun create(): Note = Note(UUID.randomUUID().toString(), created = System.currentTimeMillis(), updated = System.currentTimeMillis())

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { store.load().also { lost = store.hasUnreadable() } }
        notes = loaded
        // Not when the file could not be read, nor when there is nothing: then every file would look like one no note holds.
        if (lost || loaded.isEmpty()) return@LaunchedEffect
        val gone = NoteStore.expired(loaded, System.currentTimeMillis())
        val kept = loaded - gone.toSet()
        withContext(Dispatchers.IO) {
            gone.flatMap { it.files }.forEach(noteFiles::delete)
            noteFiles.prune(kept.flatMap { it.files }.toSet())
        }
        if (gone.isNotEmpty()) commit(kept)
    }

    val backupName = stringResource(R.string.notes_backup_name)
    val backupNote = stringResource(R.string.notes_file_default)
    fun writeBackup(uri: android.net.Uri, sealWith: String?) {
        val snapshot = notes.orEmpty()
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val raw = context.contentResolver.openOutputStream(uri, "wt")!!
                    (if (sealWith == null) raw else NoteSeal.seal(raw, sealWith)).use { NoteBackup.write(snapshot, it, noteFiles::file, backupNote) { note -> sketchPng(note.strokes, paper = note.paper) } }
                }.isSuccess
            }
            if (ok) toastCount(R.plurals.notes_backup_done, snapshot.size) else toast(R.string.notes_backup_failed)
        }
    }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let { writeBackup(it, null) } }
    val sealedBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { writeBackup(it, password) }
        password = ""
    }

    fun readBackup(uri: android.net.Uri, sealedWith: String?) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    BufferedInputStream(context.contentResolver.openInputStream(uri)!!).use { raw ->
                        val source = if (sealedWith == null) raw else NoteSeal.open(raw, sealedWith)
                        NoteBackup.read(source) { name, file -> noteFiles.restore(name, file) }
                    }
                }
            }
            val incoming = result.getOrNull()
            when {
                incoming != null -> {
                    commit(NoteBackup.merge(notes.orEmpty(), incoming))
                    toastCount(R.plurals.notes_restore_done, incoming.size)
                }
                result.exceptionOrNull() is SealException -> toast(R.string.notes_wrong_password)
                else -> toast(R.string.notes_restore_failed)
            }
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val sealed = withContext(Dispatchers.IO) {
                runCatching {
                    BufferedInputStream(context.contentResolver.openInputStream(uri)!!).use { raw ->
                        raw.mark(NoteSeal.headSize)
                        NoteSeal.isSealed(ByteArray(NoteSeal.headSize).also { raw.read(it) })
                    }
                }.getOrDefault(false)
            }
            if (sealed) ask = Ask.Restore(uri) else readBackup(uri, null)
        }
    }

    val credential = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = waiting
        waiting = null
        if (result.resultCode == Activity.RESULT_OK && id != null) { unlocked = unlocked + id; page = Page.Edit(id) }
    }
    val lockedTitle = stringResource(R.string.notes_locked_title)
    fun open(note: Note) {
        if (!note.locked || note.id in unlocked) { page = Page.Edit(note.id); return }
        val intent = context.getSystemService(KeyguardManager::class.java)?.takeIf { it.isDeviceSecure }?.createConfirmDeviceCredentialIntent(lockedTitle, null)
        if (intent == null) { toast(R.string.notes_lock_none); return }
        waiting = note.id
        credential.launch(intent)
    }
    // A page given back after the screen was recreated is shown once the notes are read: a locked note stays locked (the unlock is not kept) and goes back to the list.
    var vetted by remember { mutableStateOf(page is Page.List) }
    LaunchedEffect(notes != null) {
        val all = notes ?: return@LaunchedEffect
        val id = (page as? Page.Edit)?.id ?: (page as? Page.Sketch)?.id
        if (id != null && !vetted && all.firstOrNull { it.id == id }?.let { it.locked && id !in unlocked } != false) page = Page.List
        vetted = true
    }
    // A result of the search opens its note as soon as the notes are read (a locked one asks for the lock first, as in the list), once: not again after the screen is recreated.
    var openDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openId, notes != null) {
        if (openId != null && !openDone && notes != null) { openDone = true; notes?.firstOrNull { it.id == openId && it.deleted == 0L }?.let(::open) }
    }

    // What a page shows is kept while it turns away: the note may be gone from the list by then.
    val shownNote = remember { arrayOfNulls<Note>(1) }
    val listPane: @Composable () -> Unit = {
        NoteList(
            notes = notes,
            prefs = prefs,
            noteFiles = noteFiles,
            saveFailed = saveFailed,
            onOpen = ::open,
            onNew = { val n = create(); replace(n); page = Page.Edit(n.id) },
            onNewSketch = { val n = create(); replace(n); page = Page.Sketch(n.id) },
            onBackup = {
                if (prefs.on(NotePrefs.SEAL)) { password = ""; ask = Ask.Backup } else backup.launch("$backupName-" + java.time.LocalDate.now() + ".zip")
            },
            onRestore = { restore.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
            lost = lost && notes.orEmpty().isEmpty(),
            onDiscard = { scope.launch(Dispatchers.IO) { store.discardUnreadable(); lost = false } },
            onBin = { ids, on -> commit(notes.orEmpty().map { if (it.id in ids) it.copy(deleted = if (on) System.currentTimeMillis() else 0L) else it }) },
            onForever = { ids ->
                val gone = notes.orEmpty().filter { it.id in ids }
                gone.flatMap { it.files }.forEach(noteFiles::delete)
                commit(notes.orEmpty().filter { it.id !in ids })
            },
            onPin = { ids, on -> commit(notes.orEmpty().map { if (it.id in ids) it.copy(pinned = on) else it }) },
        )
    }
    val detailPane: @Composable (Page) -> Unit = { open ->
        val id = (open as? Page.Edit)?.id ?: (open as Page.Sketch).id
        val found = notes?.firstOrNull { it.id == id }
        if (found != null) shownNote[0] = found
        if (page !is Page.List && notes != null && (found == null || found.deleted > 0)) page = Page.List
        val note = found ?: shownNote[0]
        if (note != null) PageSwitch(
            page = open as? Page.Sketch,
            onBack = { page = Page.Edit(id) },
            list = {
                NoteEditor(
                    note = note,
                    noteFiles = noteFiles,
                    prefs = prefs,
                    folders = notes.orEmpty().filter { it.deleted == 0L }.map { it.folder }.filter { it.isNotBlank() }.distinct(),
                    onChange = ::replace,
                    onSketch = { page = Page.Sketch(note.id) },
                    onDelete = {
                        if (prefs.on(NotePrefs.BIN)) commit(notes.orEmpty().map { if (it.id == note.id) it.copy(deleted = System.currentTimeMillis()) else it })
                        else {
                            // The note leaves the list now, and its files when the banner that offers to bring it back is gone.
                            // shortcut: closing the app in those seconds leaves the files of the note on the phone, unseen; upgrade with a sweep of orphans.
                            commit(notes.orEmpty().filter { it.id != note.id })
                            MetroToast.show(context, context.getString(R.string.notes_deleted), context.getString(R.string.undo)) { commit(notes.orEmpty() + note) }
                            scope.launch {
                                delay(BANNER_MS + 1_000L)
                                if (notes.orEmpty().none { it.id == note.id }) note.files.forEach(noteFiles::delete)
                            }
                        }
                        page = Page.List
                    },
                    onClose = {
                        // A note with nothing in it is not kept.
                        if (note.isBlank) commit(notes.orEmpty().filter { it.id != note.id })
                        page = Page.List
                    },
                )
            },
            detail = { shown ->
                SketchEditor(
                    initial = note.strokes,
                    initialPaper = note.paper,
                    prefs = prefs,
                    onDraft = { strokes, paper -> notes?.firstOrNull { it.id == shown.id }?.let { replace(it.copy(strokes = strokes, paper = paper, updated = System.currentTimeMillis())) } },
                    onDone = { strokes, paper ->
                        notes?.firstOrNull { it.id == shown.id }?.let { replace(it.copy(strokes = strokes, paper = paper, updated = System.currentTimeMillis())) }
                        page = Page.Edit(shown.id)
                    },
                )
            },
        )
    }
    val openPage = page.takeIf { it !is Page.List && vetted }
    if (isWide()) {
        // A wide screen (a tablet, a folding phone opened): the list stays at the left and the note opens beside it.
        BackHandler(enabled = openPage != null) { page = Page.List }
        Row(Modifier.fillMaxSize().testTag("notes:panes")) {
            Box(Modifier.weight(0.4f).fillMaxHeight()) { listPane() }
            Box(Modifier.weight(0.6f).fillMaxHeight()) {
                if (openPage != null) androidx.compose.runtime.key((openPage as? Page.Edit)?.id ?: (openPage as Page.Sketch).id) { detailPane(openPage) }
                else MetroText(stringResource(R.string.notes_pick), Modifier.padding(horizontal = 24.dp, vertical = 48.dp).testTag("notes:pick"), size = 18.sp, color = LocalMetroColors.current.subtle)
            }
        }
    } else PageSwitch(
        page = openPage,
        onBack = { page = Page.List },
        list = listPane,
        detail = detailPane,
    )

    ask?.let { what ->
        DialogFrame(onCancel = { ask = null; password = "" }) {
            MetroTextField(password, { password = it.take(200) }, stringResource(R.string.notes_password_hint), Modifier.testTag("notes:password"), password = true)
            MetroButton(
                stringResource(if (what is Ask.Backup) R.string.notes_password_backup else R.string.notes_password_restore),
                Modifier.fillMaxWidth().testTag("notes:password:go"),
            ) {
                // Four letters at least: a backup under a password anyone guesses is worse than the plain one, which says what it is.
                if (password.length < 4) { toast(R.string.notes_password_short); return@MetroButton }
                ask = null
                when (what) {
                    Ask.Backup -> sealedBackup.launch("$backupName-" + java.time.LocalDate.now() + ".mwnotes")
                    is Ask.Restore -> { readBackup(what.uri, password); password = "" }
                }
            }
        }
    }
}
