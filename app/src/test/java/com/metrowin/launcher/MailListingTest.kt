package com.metrowin.launcher

import com.metrowin.launcher.mail.MailListing
import com.metrowin.launcher.mail.MailMessage
import com.metrowin.launcher.mail.MailSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The list of the inbox is brought in with the fewest calls to the server: a fake mailbox counts them. */
class MailListingTest {
    private fun mail(id: Int, line: String = "") = MailMessage(id.toString(), "a", "n", "n@x", "s$id", line, id.toLong(), false, false, "")

    private class Fake(val box: List<MailMessage>) : MailSource {
        val calls = ArrayList<String>()
        override fun messages(path: String, limit: Int, query: String, skip: Int): List<MailMessage> {
            calls += "messages limit=$limit skip=$skip"
            return box.sortedByDescending { it.timestamp }.drop(skip).take(limit)
        }
        override fun snippets(path: String, uids: List<Long>): Map<String, String> {
            calls += "snippets ${uids.size}"
            return uids.associate { it.toString() to "line $it" }
        }
        override fun unread(path: String): Int = 0.also { calls += "unread" }
    }

    @Test
    fun `the first page is one call and keeps the lines already known`() {
        val fake = Fake((1..120).map { mail(it) })
        val kept = listOf(mail(120, "kept line"))
        val page = MailListing.firstPage(fake, "INBOX", 50, "", kept)
        assertEquals(listOf("messages limit=50 skip=0"), fake.calls)
        assertEquals(50, page.size)
        assertEquals("kept line", page.first().snippet)
    }

    @Test
    fun `lines are asked only for the newest ones that have none, and not at all when all have one`() {
        val fake = Fake(emptyList())
        val list = (50 downTo 1).map { mail(it, if (it > 45) "has" else "") }
        val lines = MailListing.lines(fake, "INBOX", list)
        assertEquals(listOf("snippets ${MailListing.SNIPPETS}"), fake.calls)
        assertEquals((45 downTo 36).map { it.toString() }.toSet(), lines.keys)
        fake.calls.clear()
        assertTrue(MailListing.lines(fake, "INBOX", list.map { it.copy(snippet = "x") }).isEmpty())
        assertTrue("nothing asked of the server", fake.calls.isEmpty())
    }

    @Test
    fun `the next page asks only for the older headers and lists none twice`() {
        val fake = Fake((1..120).map { mail(it) })
        val first = MailListing.firstPage(fake, "INBOX", 50, "", null)
        fake.calls.clear()
        val all = MailListing.morePage(fake, "INBOX", first)
        assertEquals(listOf("messages limit=50 skip=50"), fake.calls)
        assertEquals(100, all.size)
        assertEquals(100, all.map { it.id }.distinct().size)
    }
}
