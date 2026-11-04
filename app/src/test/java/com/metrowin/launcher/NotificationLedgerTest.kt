package com.metrowin.launcher

import android.service.notification.NotificationListenerService
import com.metrowin.launcher.notes.NoteCrypt
import com.metrowin.launcher.notifications.LedgerItem
import com.metrowin.launcher.notifications.NotificationEntry
import com.metrowin.launcher.notifications.NotificationHub
import com.metrowin.launcher.notifications.NotificationLedger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * What the register of notifications promises: a notification written when it is posted stays until the user takes it away, whatever the
 * app that posted it does and whatever happens to the process or the listener.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationLedgerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private object Plain : NoteCrypt {
        override fun encrypt(plain: String) = "enc:$plain"
        override fun decrypt(text: String) = text.removePrefix("enc:").takeIf { text.startsWith("enc:") }
    }

    private lateinit var file: File
    private val now = System.currentTimeMillis()

    @Before
    fun setUp() {
        file = File(folder.root, "notifications.dat")
        NotificationHub.clearHeld()
        NotificationHub.update(emptyList())
    }

    @After
    fun tearDown() {
        NotificationHub.clearHeld()
        NotificationHub.update(emptyList())
    }

    private fun ledger() = NotificationLedger(file, Plain) { it.run() }

    private fun entry(
        key: String, pkg: String = "org.telegram.messenger", title: String = "Anna", text: String = "ciao", at: Long = now,
        group: String? = null, summary: Boolean = false, long: String? = null,
    ) = NotificationEntry(key, pkg, title, text, at, null, false, longText = long, group = group, summary = summary)

    private fun shown() = NotificationHub.entries.value.map { it.key }

    @Test
    fun `a notification the app takes back after it came stays`() {
        NotificationHub.attach(ledger())
        NotificationHub.keep(entry("tg|1"))
        // Telegram withdraws it (the chat was read on another device): Android says reason 8 (the app cancelled it) and the launcher keeps it.
        assertFalse(NotificationHub.removedByUser(NotificationListenerService.REASON_APP_CANCEL))
        assertFalse(NotificationHub.removedByUser(NotificationListenerService.REASON_APP_CANCEL_ALL))
        assertFalse(NotificationHub.removedByUser(NotificationListenerService.REASON_TIMEOUT))
        // The screen comes on and the service reads what Android has up: nothing. The notification is still there.
        NotificationHub.update(emptyList())
        assertEquals(listOf("tg|1"), shown())
    }

    @Test
    fun `only the user taking it away in the shade of Android ends it`() {
        assertTrue(NotificationHub.removedByUser(NotificationListenerService.REASON_CLICK))
        assertTrue(NotificationHub.removedByUser(NotificationListenerService.REASON_CANCEL))
        assertTrue(NotificationHub.removedByUser(NotificationListenerService.REASON_CANCEL_ALL))
        assertFalse(NotificationHub.removedByUser(NotificationListenerService.REASON_LISTENER_CANCEL))
        NotificationHub.attach(ledger())
        NotificationHub.keep(entry("tg|1"))
        NotificationHub.keep(entry("tg|2"))
        NotificationHub.forget("tg|1")
        NotificationHub.update(emptyList())
        assertEquals(listOf("tg|2"), shown())
    }

    @Test
    fun `a notification that came while no screen of the launcher was on is in the list at the first read`() {
        NotificationHub.attach(ledger())
        // Written at the post, with nothing read: the pacer was idle and Android never told the screens anything.
        NotificationHub.keep(entry("reddit|1", pkg = "com.reddit.frontpage", title = "r/android", text = "new post"))
        NotificationHub.update(emptyList())
        assertEquals(listOf("reddit|1"), shown())
        assertEquals(mapOf("com.reddit.frontpage" to 1), NotificationHub.countsByPackage(NotificationHub.entries.value))
    }

    @Test
    fun `several messages of the same chat are one notification with the newest on top and all of them in the text`() {
        NotificationHub.attach(ledger())
        NotificationHub.keep(entry("tg|chat", text = "uno", at = now - 3000, long = null))
        NotificationHub.keep(entry("tg|chat", text = "due", at = now - 2000, long = "Anna: uno\nAnna: due"))
        NotificationHub.keep(entry("tg|chat", text = "tre", at = now - 1000, long = "Anna: uno\nAnna: due\nAnna: tre"))
        NotificationHub.update(emptyList())
        val all = NotificationHub.entries.value
        assertEquals(1, all.size)
        assertEquals("tre", all.single().text)
        assertEquals("Anna: uno\nAnna: due\nAnna: tre", all.single().longText)
    }

    @Test
    fun `a live notification wins over its kept copy and the copy is not lost`() {
        NotificationHub.attach(ledger())
        NotificationHub.keep(entry("tg|1", text = "old"))
        NotificationHub.update(listOf(entry("tg|1", text = "live")))
        assertEquals(listOf("live"), NotificationHub.entries.value.map { it.text })
        NotificationHub.update(emptyList())
        assertEquals(listOf("old"), NotificationHub.entries.value.map { it.text })
    }

    @Test
    fun `a group heading is shown only while none of its own notifications is, and goes with the last one`() {
        NotificationHub.attach(ledger())
        NotificationHub.keep(entry("sum", pkg = "mail", title = "Mail", text = "2 new messages", group = "g", summary = true))
        NotificationHub.update(emptyList())
        // The heading came alone, with words of its own: it is not lost.
        assertEquals(listOf("sum"), shown())
        NotificationHub.keep(entry("a", pkg = "mail", group = "g"))
        NotificationHub.keep(entry("b", pkg = "mail", group = "g", at = now + 1))
        NotificationHub.update(emptyList())
        assertEquals(listOf("b", "a"), shown())
        NotificationHub.forget("a")
        assertEquals(listOf("b"), shown())
        // The last one: the heading has nothing left to head and goes, it does not come back as a ghost.
        NotificationHub.forget("b")
        NotificationHub.update(emptyList())
        assertTrue(shown().isEmpty())
    }

    @Test
    fun `what was written survives the process`() {
        // The process that posted these is gone: its memory with it, only the file is left.
        ledger().run {
            record(LedgerItem("tg|1", "org.telegram.messenger", "Anna", "ciao", null, now, false, false, null, false))
            record(LedgerItem("rd|1", "com.reddit.frontpage", "r/android", "post", null, now + 5, false, false, null, false))
        }
        assertTrue(shown().isEmpty())
        // The next process: the listener connects, the register is read, the notifications are in the list before anything is read from Android.
        NotificationHub.attach(ledger())
        NotificationHub.update(emptyList())
        assertEquals(listOf("rd|1", "tg|1"), shown())
        assertEquals("ciao", NotificationHub.entries.value.last().text)
    }

    @Test
    fun `a kept notification that has no intent any more still opens something`() {
        NotificationHub.attach(ledger())
        NotificationHub.keep(entry("tg|1"))
        NotificationHub.update(emptyList())
        assertNull(NotificationHub.entries.value.single().contentIntent)
    }

    @Test
    fun `the file is encrypted and holds no words in the clear`() {
        val l = ledger()
        l.record(LedgerItem("k", "pkg", "Titolo segreto", "testo segreto", null, now, false, false, null, false))
        assertTrue(file.readText().startsWith("enc:"))
        val unreadable = NotificationLedger(file, object : NoteCrypt {
            override fun encrypt(plain: String) = ""
            override fun decrypt(text: String): String? = null
        }) { it.run() }
        assertTrue(unreadable.load().isEmpty())
        // A file that cannot be read is put aside, not written over.
        assertTrue(File(folder.root, "notifications.unreadable").exists())
    }

    @Test
    fun `old ones go after the long time and the register has a limit`() {
        val l = ledger()
        val old = now - NotificationLedger.MAX_AGE_MS - 1000
        l.record(LedgerItem("old", "p", "t", "x", null, old, false, false, null, false), now)
        assertTrue(l.all(now).isEmpty())
        repeat(NotificationLedger.MAX_ITEMS + 20) { n ->
            l.record(LedgerItem("k$n", "p", "t", "x", null, now - n, false, false, null, false), now)
        }
        assertEquals(NotificationLedger.MAX_ITEMS, l.all(now).size)
        assertTrue(l.all(now).none { it.key == "old" })
    }

    @Test
    fun `the journal keeps packages and reasons and never words`() {
        val l = ledger()
        l.note("posted org.telegram.messenger kept=true")
        l.note("removed org.telegram.messenger reason=8 kept=true")
        l.record(LedgerItem("k", "p", "t", "x", null, now, false, false, null, false))
        val back = ledger().also { it.load() }.journalLines()
        assertEquals(listOf("posted org.telegram.messenger kept=true", "removed org.telegram.messenger reason=8 kept=true"), back)
    }
}
