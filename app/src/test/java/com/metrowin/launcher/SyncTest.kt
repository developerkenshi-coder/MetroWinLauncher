package com.metrowin.launcher

import com.metrowin.launcher.data.SyncAction
import com.metrowin.launcher.data.SyncCrypto
import com.metrowin.launcher.data.SyncEngine
import com.metrowin.launcher.data.SyncLocal
import com.metrowin.launcher.data.SyncOutcome
import com.metrowin.launcher.data.SyncPlanner
import com.metrowin.launcher.data.SyncStorage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTest {
    // A few iterations: the real number would make every test take seconds.
    private val fast = 1_000

    private class MemoryStorage : SyncStorage {
        val files = LinkedHashMap<String, ByteArray>()
        var failWrites = false
        override fun read(name: String) = files[name]
        override fun write(name: String, bytes: ByteArray): Boolean {
            if (failWrites) return false
            files[name] = bytes
            return true
        }
    }

    // --- crypto

    @Test
    fun whatIsEncryptedComesBackWithTheSamePassphrase() {
        val plain = "the layout, with accents: àèìòù, and 日本語".toByteArray()
        val blob = SyncCrypto.encrypt(plain, "correct horse", fast)
        assertArrayEquals(plain, SyncCrypto.decrypt(blob, "correct horse"))
    }

    @Test
    fun theFileDoesNotShowTheContent() {
        val blob = SyncCrypto.encrypt("secret layout secret layout".toByteArray(), "pw", fast)
        assertFalse(String(blob, Charsets.ISO_8859_1).contains("secret"))
    }

    @Test
    fun anotherPassphraseOpensNothing() {
        val blob = SyncCrypto.encrypt("x".toByteArray(), "one", fast)
        assertNull(SyncCrypto.decrypt(blob, "two"))
        assertNull(SyncCrypto.decrypt(blob, ""))
    }

    @Test
    fun everyEncryptionIsDifferentEvenForTheSameData() {
        val a = SyncCrypto.encrypt("same".toByteArray(), "pw", fast)
        val b = SyncCrypto.encrypt("same".toByteArray(), "pw", fast)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun anyChangeToTheFileIsNoticed() {
        val blob = SyncCrypto.encrypt("data that matters".toByteArray(), "pw", fast)
        // Flip one bit in each part: the header (salt), the nonce, the data, the tag.
        for (index in listOf(12, 25, 40, blob.size - 1)) {
            val changed = blob.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertNull("byte $index", SyncCrypto.decrypt(changed, "pw"))
        }
    }

    @Test
    fun theNumberOfIterationsInTheFileCannotBeUsedToStallThePhone() {
        val blob = SyncCrypto.encrypt("x".toByteArray(), "pw", fast)
        // Rewrite the iteration count (bytes 7..10) to something huge: refused at once, before any key is made.
        val greedy = blob.copyOf().also { it[7] = 0x7F; it[8] = 0xFF.toByte(); it[9] = 0xFF.toByte(); it[10] = 0xFF.toByte() }
        assertNull(SyncCrypto.decrypt(greedy, "pw"))
    }

    @Test
    fun otherFilesAndShortOnesAreRefused() {
        assertNull(SyncCrypto.decrypt(ByteArray(0), "pw"))
        assertNull(SyncCrypto.decrypt("not a sync file at all, but long enough to have a header and more".toByteArray(), "pw"))
        assertFalse(SyncCrypto.isSyncFile("hello".toByteArray()))
        assertTrue(SyncCrypto.isSyncFile(SyncCrypto.encrypt("x".toByteArray(), "pw", fast)))
    }

    // --- the plan

    @Test
    fun withNoFileInTheFolderThePhoneUploads() {
        assertEquals(SyncAction.Upload, SyncPlanner.decide(false, "h", "", 0, 0))
        assertEquals(SyncAction.Upload, SyncPlanner.decide(false, "h", "h", 5, 5))
    }

    @Test
    fun aPhoneThatNeverSyncedTakesWhatIsInTheFolder() {
        assertEquals(SyncAction.Apply, SyncPlanner.decide(true, "mine", "", 100, 0))
    }

    @Test
    fun theFourCasesAfterAnEarlierSync() {
        assertEquals(SyncAction.Nothing, SyncPlanner.decide(true, "h", "h", 7, 7))
        assertEquals(SyncAction.Upload, SyncPlanner.decide(true, "new", "h", 7, 7))
        assertEquals(SyncAction.Apply, SyncPlanner.decide(true, "h", "h", 9, 7))
        assertEquals(SyncAction.UploadKeepingTheOther, SyncPlanner.decide(true, "new", "h", 9, 7))
    }

    // --- the engine, with two phones sharing a folder

    private fun local(layout: String, last: String = "", stamp: Long = 0, pass: String = "pw", device: String = "A") =
        SyncLocal(layout, pass, device, last, stamp)

    @Test
    fun theFirstPhoneUploadsAndTheSecondOneJoins() {
        val folder = MemoryStorage()
        var clock = 1_000L
        val engine = SyncEngine(folder, { clock }, fast)

        val up = engine.sync(local("""{"tiles":["a","b"]}""")) as SyncOutcome.Uploaded
        assertEquals(1_000L, up.stamp)
        assertEquals(SyncEngine.hash("""{"tiles":["a","b"]}"""), up.hash)
        assertEquals(listOf(SyncEngine.FILE), folder.files.keys.toList())

        clock = 2_000L
        val joined = engine.sync(local("""{"tiles":[]}""", device = "B")) as SyncOutcome.Applied
        assertEquals("""{"tiles":["a","b"]}""", joined.layoutJson)
        assertEquals(1_000L, joined.stamp)
    }

    @Test
    fun aChangeTravelsFromOnePhoneToTheOther() {
        val folder = MemoryStorage()
        var clock = 1_000L
        val engine = SyncEngine(folder, { clock }, fast)
        val first = engine.sync(local("v1")) as SyncOutcome.Uploaded
        val second = engine.sync(local("old", device = "B")) as SyncOutcome.Applied

        // Phone A changes its layout and syncs.
        clock = 5_000L
        val pushed = engine.sync(local("v2", last = first.hash, stamp = first.stamp)) as SyncOutcome.Uploaded
        // Phone B, which has v1 (and has not changed it), picks it up.
        val pulled = engine.sync(local("v1", last = SyncEngine.hash("v1"), stamp = second.stamp, device = "B")) as SyncOutcome.Applied
        assertEquals("v2", pulled.layoutJson)
        assertEquals(pushed.stamp, pulled.stamp)
        // Nothing left to do for either.
        assertEquals(SyncOutcome.Nothing, engine.sync(local("v2", last = SyncEngine.hash("v2"), stamp = pushed.stamp, device = "B")))
    }

    @Test
    fun whenBothChangedThisPhoneWinsAndTheOtherLayoutIsKept() {
        val folder = MemoryStorage()
        var clock = 1_000L
        val engine = SyncEngine(folder, { clock }, fast)
        val first = engine.sync(local("v1")) as SyncOutcome.Uploaded
        clock = 2_000L
        engine.sync(local("from B", last = first.hash, stamp = first.stamp, device = "B")) // B uploads
        clock = 3_000L
        val outcome = engine.sync(local("from A", last = first.hash, stamp = first.stamp)) as SyncOutcome.Uploaded
        assertEquals("metrowin-sync-other-2000.bin", outcome.keptTheOtherAs)
        // The copy is still readable with the passphrase: nothing was lost.
        val copy = SyncCrypto.decrypt(folder.files.getValue("metrowin-sync-other-2000.bin"), "pw")!!.decodeToString()
        assertTrue(copy.contains("from B"))
        // And the main file now holds A's layout.
        val main = SyncCrypto.decrypt(folder.files.getValue(SyncEngine.FILE), "pw")!!.decodeToString()
        assertTrue(main.contains("from A"))
    }

    @Test
    fun theWrongPassphraseTouchesNothing() {
        val folder = MemoryStorage()
        val engine = SyncEngine(folder, { 1L }, fast)
        engine.sync(local("v1", pass = "right"))
        val before = folder.files.getValue(SyncEngine.FILE).copyOf()
        assertEquals(SyncOutcome.WrongPassphrase, engine.sync(local("v2", pass = "wrong")))
        assertArrayEquals(before, folder.files.getValue(SyncEngine.FILE))
    }

    @Test
    fun aFileThatIsNotASyncFileIsLeftAlone() {
        val folder = MemoryStorage().apply { files[SyncEngine.FILE] = "just some text".toByteArray() }
        assertEquals(SyncOutcome.NotASyncFile, SyncEngine(folder, { 1L }, fast).sync(local("v1")))
        assertEquals("just some text", folder.files.getValue(SyncEngine.FILE).decodeToString())
    }

    @Test
    fun aFolderThatRefusesWritesIsAFailureNotACrash() {
        val folder = MemoryStorage().apply { failWrites = true }
        assertEquals(SyncOutcome.Failed, SyncEngine(folder, { 1L }, fast).sync(local("v1")))
    }

    @Test
    fun theHashTellsLayoutsApart() {
        assertEquals(SyncEngine.hash("a"), SyncEngine.hash("a"))
        assertNotEquals(SyncEngine.hash("a"), SyncEngine.hash("b"))
        assertEquals(64, SyncEngine.hash("a").length)
    }
}
