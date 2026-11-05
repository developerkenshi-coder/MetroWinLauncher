package com.metrowin.launcher

import com.metrowin.launcher.data.BackupExtras
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LayoutBackup
import com.metrowin.launcher.data.LayoutBackupCodec
import com.metrowin.launcher.data.ParkedCar
import com.metrowin.launcher.data.Reminder
import com.metrowin.launcher.data.SavedLayout
import com.metrowin.launcher.data.SyncConflicts
import com.metrowin.launcher.data.SyncCrypto
import com.metrowin.launcher.data.SyncEngine
import com.metrowin.launcher.model.StartEntry
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.todo.TodoItem
import com.metrowin.launcher.wallet.Card
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupExtrasTest {
    private val pages = listOf(listOf<StartEntry>(Tile("a", TileSize.Wide)))
    private val extras = BackupExtras(
        todos = listOf(TodoItem(1, "Buy milk", due = 1_800_000_000_000L, starred = true, list = "Home", reminderId = 7)),
        reminders = listOf(Reminder(7, "Buy milk", 1_800_000_000_000L)),
        layouts = listOf(SavedLayout("w", "Work", 9, "[]", "[]")),
        car = ParkedCar(45.5, 9.2, "Via Roma", 1_700_000_000_000L),
        wallet = listOf(Card("c1", "Coop", "123456789012345")),
    )
    private val backup = LayoutBackup(pages, emptyList(), LauncherSettings(), extras)

    @Test
    fun theThingsOfTheUserComeBack() {
        val restored = LayoutBackupCodec.decode(LayoutBackupCodec.encode(backup, 1L), LauncherSettings())!!.extras!!
        assertEquals(extras.todos, restored.todos)
        assertEquals(extras.reminders, restored.reminders)
        assertEquals(extras.layouts, restored.layouts)
        assertEquals(extras.car, restored.car)
    }

    @Test
    fun theWalletIsNeverInTheClear() {
        val text = LayoutBackupCodec.encode(backup, 1L)
        assertFalse(text.contains("123456789012345"))
        assertNull(LayoutBackupCodec.decode(text, LauncherSettings())!!.extras!!.wallet)
    }

    @Test
    fun theWalletTravelsInTheEncryptedFile() {
        val text = LayoutBackupCodec.encode(backup, 0L, secret = true)
        assertEquals(extras.wallet, LayoutBackupCodec.decode(text, LauncherSettings())!!.extras!!.wallet)
        assertFalse(String(SyncCrypto.encrypt(text.toByteArray(), "pw", 1_000), Charsets.ISO_8859_1).contains("123456789012345"))
    }

    @Test
    fun noCarIsKeptAsNoCar() {
        val text = LayoutBackupCodec.encode(backup.copy(extras = extras.copy(car = null)), 1L)
        assertNull(LayoutBackupCodec.decode(text, LauncherSettings())!!.extras!!.car)
    }

    @Test
    fun aFileOfVersionOneIsStillReadAndHasNoExtras() {
        val v1 = JSONObject(LayoutBackupCodec.encode(backup.copy(extras = null), 1L)).put("version", 1).toString()
        val restored = LayoutBackupCodec.decode(v1, LauncherSettings())!!
        assertEquals(pages, restored.pages)
        assertNull(restored.extras)
    }

    @Test
    fun aFileFromTheFutureIsRefused() {
        val future = JSONObject(LayoutBackupCodec.encode(backup, 1L)).put("version", LayoutBackupCodec.VERSION + 1).toString()
        assertNull(LayoutBackupCodec.decode(future, LauncherSettings()))
    }

    @Test
    fun conflictCopiesAreFoundNewestFirstAndOpenWithThePassphrase() {
        val names = listOf("metrowin-sync.bin", "metrowin-sync-other-100.bin", "metrowin-sync-other-300.bin", "notes.txt", "metrowin-sync-other-x.bin")
        assertEquals(listOf("metrowin-sync-other-300.bin", "metrowin-sync-other-100.bin"), SyncConflicts.newestFirst(names))
        assertEquals(300L, SyncConflicts.stamp("metrowin-sync-other-300.bin"))
        val layout = LayoutBackupCodec.encode(backup, 0L, secret = true)
        val files = HashMap<String, ByteArray>()
        val storage = object : com.metrowin.launcher.data.SyncStorage {
            override fun read(name: String) = files[name]
            override fun write(name: String, bytes: ByteArray) = true.also { files[name] = bytes }
        }
        SyncEngine(storage, iterations = 1_000).sync(com.metrowin.launcher.data.SyncLocal(layout, "pw", "d", "", 0L))
        val blob = files[SyncEngine.FILE]!!
        assertNotNull(SyncConflicts.layoutOf(blob, "pw")?.let { LayoutBackupCodec.decode(it, LauncherSettings()) })
        assertNull(SyncConflicts.layoutOf(blob, "wrong"))
        assertTrue(SyncConflicts.layoutOf(byteArrayOf(1, 2, 3), "pw") == null)
    }
}
