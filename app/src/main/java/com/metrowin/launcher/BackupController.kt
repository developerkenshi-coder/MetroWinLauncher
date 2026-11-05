package com.metrowin.launcher

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import com.metrowin.launcher.data.BackupCopies
import com.metrowin.launcher.data.BackupSchedule
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LayoutBackup
import com.metrowin.launcher.data.LayoutBackupCodec
import com.metrowin.launcher.data.RestorePart
import com.metrowin.launcher.data.SafFolder
import com.metrowin.launcher.data.SafSyncStorage
import com.metrowin.launcher.data.SyncConflicts
import com.metrowin.launcher.data.SyncEngine
import com.metrowin.launcher.data.SyncLocal
import com.metrowin.launcher.data.SyncOutcome
import com.metrowin.launcher.data.ThemeCodec
import com.metrowin.launcher.data.readAtMost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The biggest layout or app list file the launcher reads from a file the user chose. */
internal const val MAX_LAYOUT_BYTES = 2 * 1024 * 1024

/**
 * The backup folder, the backup copies, the sync with other phones and the files of a layout. What the layout is, and how a backup is put
 * back on Start, stays with the [LauncherViewModel]: it is reached through [snapshot], [restore] and [restoreParts].
 */
internal class BackupController(
    private val app: Application,
    private val scope: CoroutineScope,
    private val settings: StateFlow<LauncherSettings>,
    private val change: ((LauncherSettings) -> LauncherSettings) -> Unit,
    private val snapshot: (withWallet: Boolean) -> LayoutBackup,
    private val restore: (LayoutBackup) -> Unit,
    private val restoreParts: (LayoutBackup, Set<RestorePart>) -> Unit,
    private val tell: (Int) -> Unit,
) {
    /** The name of the backup folder, for the settings; null while none is chosen or it cannot be read. */
    val folderName: StateFlow<String?> = settings
        .map { it.backupFolder }
        .distinctUntilChanged()
        .map { uri -> if (uri.isBlank()) null else withContext(Dispatchers.IO) { SafFolder(app, uri.toUri()).displayName() } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val _copies = MutableStateFlow<List<String>>(emptyList())

    /** The names of the copies of the backup in the folder, the newest first (read when the page of the backup opens and after a backup). */
    val copies: StateFlow<List<String>> = _copies.asStateFlow()

    private val _syncOthers = MutableStateFlow<List<String>>(emptyList())

    /** The copies a sync with a conflict left in the folder, the newest first (read when the page of the backup opens). */
    val syncOthers: StateFlow<List<String>> = _syncOthers.asStateFlow()

    /** A backup that has been read and waits for the user to say which parts of it to put back. */
    private val _pendingRestore = MutableStateFlow<LayoutBackup?>(null)
    val pendingRestore: StateFlow<LayoutBackup?> = _pendingRestore.asStateFlow()

    private var syncing = false

    /** Keeps [uri] (a folder the user picked) for the backups: the permission is kept past a restart of the phone. */
    fun setFolder(uri: Uri) {
        runCatching {
            app.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        // Another folder is another sync: what was known about the old one does not apply.
        change { it.copy(backupFolder = uri.toString(), lastBackupAt = 0L, syncLastHash = "", syncLastStamp = 0L, syncLastAt = 0L) }
    }

    fun clearFolder() {
        val old = settings.value.backupFolder
        if (old.isNotBlank()) {
            runCatching {
                app.contentResolver.releasePersistableUriPermission(
                    old.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        change {
            it.copy(backupFolder = "", backupEveryDays = 0, lastBackupAt = 0L, syncEnabled = false, syncLastHash = "", syncLastStamp = 0L, syncLastAt = 0L)
        }
    }

    /** Saves the layout in the backup folder. [announce] says so on screen; an automatic backup does not. */
    fun backupNow(announce: Boolean = true) {
        val folder = settings.value.backupFolder
        if (folder.isBlank()) return
        val keep = settings.value.backupCopies
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                val bytes = LayoutBackupCodec.encode(snapshot(false), System.currentTimeMillis()).toByteArray()
                val files = SafFolder(app, folder.toUri())
                val written = files.write(SafFolder.BACKUP_FILE, "application/json", bytes)
                // With more than one copy asked for, the same file is also kept under the minute it was made, and the oldest ones go.
                if (written && keep > 1) {
                    val copy = BackupCopies.name(java.time.LocalDateTime.now())
                    if (files.write(copy, "application/json", bytes)) BackupCopies.excess(files.names() + copy, keep).forEach(files::delete)
                }
                written
            }
            if (saved) change { it.copy(lastBackupAt = System.currentTimeMillis()) }
            if (announce) tell(if (saved) R.string.backup_saved else R.string.backup_failed)
            if (saved) refreshCopies()
        }
    }

    fun refreshCopies() {
        val folder = settings.value.backupFolder
        if (folder.isBlank()) {
            _copies.value = emptyList()
            return
        }
        scope.launch {
            _copies.value = withContext(Dispatchers.IO) { BackupCopies.newestFirst(SafFolder(app, folder.toUri()).names()) }
        }
    }

    /** Reads one of the copies and asks which parts of it to put back. */
    fun restoreFromCopy(name: String) {
        val folder = settings.value.backupFolder
        if (folder.isBlank() || BackupCopies.parse(name) == null) return
        readForRestore(folder, name)
    }

    /** Puts back the layout saved in the backup folder. */
    fun restoreFromFolder() {
        val folder = settings.value.backupFolder
        if (folder.isBlank()) return
        readForRestore(folder, SafFolder.BACKUP_FILE)
    }

    private fun readForRestore(folder: String, name: String) {
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { SafFolder(app, folder.toUri()).read(name) }
            val backup = bytes?.let { LayoutBackupCodec.decode(it.decodeToString(), settings.value) }
            when {
                bytes == null -> tell(R.string.backup_no_file)
                backup == null -> tell(R.string.backup_not_layout)
                else -> _pendingRestore.value = backup
            }
        }
    }

    fun refreshSyncOthers() {
        val folder = settings.value.backupFolder
        scope.launch {
            _syncOthers.value = if (folder.isBlank()) emptyList() else withContext(Dispatchers.IO) { SyncConflicts.newestFirst(SafFolder(app, folder.toUri()).names()) }
        }
    }

    /** Opens one of the copies with the sync passphrase and asks which parts of it to put back. */
    fun recoverSyncOther(name: String) {
        val s = settings.value
        if (s.backupFolder.isBlank() || SyncConflicts.stamp(name) == null) return
        scope.launch {
            val blob = withContext(Dispatchers.IO) { SafFolder(app, s.backupFolder.toUri()).read(name) }
            val layout = blob?.let { SyncConflicts.layoutOf(it, s.syncPassphrase) }
            val backup = layout?.let { LayoutBackupCodec.decode(it, s) }
            when {
                blob == null -> tell(R.string.backup_no_file)
                layout == null -> tell(R.string.sync_wrong_passphrase)
                backup == null -> tell(R.string.sync_not_file)
                else -> _pendingRestore.value = backup
            }
        }
    }

    fun clearSyncOthers() {
        val folder = settings.value.backupFolder
        if (folder.isBlank()) return
        scope.launch {
            withContext(Dispatchers.IO) { SafFolder(app, folder.toUri()).let { files -> SyncConflicts.newestFirst(files.names()).forEach(files::delete) } }
            refreshSyncOthers()
        }
    }

    fun cancelRestore() {
        _pendingRestore.value = null
    }

    fun applyRestore(parts: Set<RestorePart>) {
        val backup = _pendingRestore.value ?: return
        _pendingRestore.value = null
        restoreParts(backup, parts)
        tell(R.string.backup_imported)
    }

    /** Sends the layout (see [LayoutBackupCodec]) to another app or person as a file, through the share sheet. */
    fun shareLayout() = shareFile("metrowin-layout.json") { LayoutBackupCodec.encode(snapshot(false), System.currentTimeMillis()) }

    /** The same for the look alone (see [ThemeCodec]): the file the Icons & themes page also reads. */
    fun shareTheme() = shareFile("metrowin-theme.json") { ThemeCodec.encode(settings.value) }

    private fun shareFile(name: String, text: () -> String) {
        scope.launch {
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = java.io.File(app.cacheDir, "shared").apply { mkdirs() }
                    val file = java.io.File(dir, name).apply { writeText(text()) }
                    androidx.core.content.FileProvider.getUriForFile(app, app.packageName + ".files", file)
                }.getOrNull()
            }
            if (uri == null) {
                tell(R.string.backup_failed)
                return@launch
            }
            val send = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { app.startActivity(chooser) }.isFailure) tell(R.string.launch_failed)
        }
    }

    /** Called when the launcher goes to the background: makes the automatic backup if it is time. */
    fun backupIfDue() {
        val s = settings.value
        if (BackupSchedule.isDue(s.backupEveryDays, s.backupFolder, s.lastBackupAt, System.currentTimeMillis())) backupNow(announce = false)
        syncIfDue(SYNC_MIN_GAP_ON_LEAVE_MS)
    }

    /** An automatic sync, unless one ran a moment ago. */
    fun syncIfDue(minGapMs: Long) {
        val s = settings.value
        if (!s.syncEnabled || s.syncPassphrase.isEmpty() || s.backupFolder.isBlank()) return
        if (System.currentTimeMillis() - s.syncLastAt < minGapMs) return
        syncNow(announce = false)
    }

    /**
     * Brings this phone and the sync file in the backup folder in line (see [SyncEngine]). [announce] says how it
     * went on screen; an automatic sync only speaks when something needs the user.
     */
    fun syncNow(announce: Boolean = true) {
        val s = settings.value
        if (!s.syncEnabled || s.backupFolder.isBlank() || s.syncPassphrase.isEmpty()) {
            if (announce) tell(R.string.sync_not_ready)
            return
        }
        if (syncing) return
        syncing = true
        scope.launch {
            try {
                if (s.deviceId.isEmpty()) change { it.copy(deviceId = java.util.UUID.randomUUID().toString()) }
                val folder = SafFolder(app, s.backupFolder.toUri())
                val outcome = withContext(Dispatchers.IO) {
                    val layout = LayoutBackupCodec.encode(snapshot(true), 0L, secret = true)
                    val local = SyncLocal(layout, s.syncPassphrase, settings.value.deviceId, s.syncLastHash, s.syncLastStamp)
                    SyncEngine(SafSyncStorage(folder)).sync(local)
                }
                applySyncOutcome(outcome, folder, announce)
            } finally {
                syncing = false
            }
        }
    }

    private suspend fun applySyncOutcome(outcome: SyncOutcome, folder: SafFolder, announce: Boolean) {
        val now = System.currentTimeMillis()
        when (outcome) {
            SyncOutcome.Nothing -> {
                change { it.copy(syncLastAt = now) }
                if (announce) tell(R.string.sync_up_to_date)
            }
            is SyncOutcome.Uploaded -> {
                change { it.copy(syncLastHash = outcome.hash, syncLastStamp = outcome.stamp, syncLastAt = now) }
                if (outcome.keptTheOtherAs != null) {
                    // Something the user should know: the other phone's layout did not win.
                    Toast.makeText(app, app.getString(R.string.sync_conflict, outcome.keptTheOtherAs), Toast.LENGTH_LONG).show()
                } else if (announce) {
                    tell(R.string.sync_uploaded)
                }
            }
            is SyncOutcome.Applied -> {
                val backup = LayoutBackupCodec.decode(outcome.layoutJson, settings.value)
                if (backup == null) {
                    tell(R.string.sync_not_file)
                    return
                }
                if (settings.value.syncLastHash.isEmpty()) {
                    // Joining a sync that already has data: what this phone had is kept as the backup file.
                    withContext(Dispatchers.IO) {
                        folder.write(SafFolder.BACKUP_FILE, "application/json", LayoutBackupCodec.encode(snapshot(false), now).toByteArray())
                    }
                }
                restore(backup)
                val hash = withContext(Dispatchers.IO) { SyncEngine.hash(LayoutBackupCodec.encode(snapshot(true), 0L, secret = true)) }
                change { it.copy(syncLastHash = hash, syncLastStamp = outcome.stamp, syncLastAt = now) }
                if (announce) tell(R.string.sync_applied)
            }
            SyncOutcome.WrongPassphrase -> tell(R.string.sync_wrong_passphrase)
            SyncOutcome.NotASyncFile -> tell(R.string.sync_not_file)
            SyncOutcome.Failed -> if (announce) tell(R.string.sync_failed)
        }
    }

    /** Writes the layout to [uri] (a file the user chose). */
    fun exportLayout(uri: Uri) {
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    val text = LayoutBackupCodec.encode(snapshot(false), System.currentTimeMillis())
                    app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                }.isSuccess
            }
            tell(if (written) R.string.backup_exported else R.string.backup_failed)
        }
    }

    /** Reads a layout file and puts it in place. */
    fun importLayout(uri: Uri) {
        scope.launch {
            val backup = withContext(Dispatchers.IO) {
                runCatching {
                    val text = app.contentResolver.openInputStream(uri)!!.use { it.readAtMost(MAX_LAYOUT_BYTES) }!!.decodeToString()
                    LayoutBackupCodec.decode(text, settings.value)
                }.getOrNull()
            }
            if (backup == null) tell(R.string.backup_not_layout) else _pendingRestore.value = backup
        }
    }

    private companion object {
        const val SYNC_MIN_GAP_ON_LEAVE_MS = 15 * 60_000L
    }
}
