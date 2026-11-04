package com.metrowin.launcher

import com.metrowin.launcher.data.NewsSources
import com.metrowin.launcher.news.NewsPresets
import com.metrowin.launcher.news.NewsSourceDef
import com.metrowin.launcher.news.NewsSourceList
import com.metrowin.launcher.wallet.Card
import com.metrowin.launcher.wallet.CardKind
import com.metrowin.launcher.wallet.CodeFormat
import com.metrowin.launcher.wallet.Wallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletAndNewsTest {
    @Test
    fun `a code is valid only in a format that can write it`() {
        assertTrue(Wallet.valid("ABC-123", CodeFormat.Code128))
        assertTrue(Wallet.valid("https://example.com/ticket/42", CodeFormat.Qr))
        assertTrue(Wallet.valid("590123412345", CodeFormat.Ean13))
        assertFalse("letters in an EAN", Wallet.valid("ABCDEF", CodeFormat.Ean13))
        assertFalse("wrong length", Wallet.valid("12345", CodeFormat.Ean13))
        assertFalse("blank", Wallet.valid("  ", CodeFormat.Qr))
        assertTrue(Wallet.valid("HELLO 39", CodeFormat.Code39))
        assertTrue(Wallet.valid("some text", CodeFormat.Aztec))
        assertTrue(Wallet.valid("some text", CodeFormat.Pdf417))
    }

    @Test
    fun `a matrix of a one dimensional code is one row wide enough to scan`() {
        val m = Wallet.matrix("12345678", CodeFormat.Code128)!!
        assertEquals(1, m.height)
        assertTrue(m.width >= 100)
        val qr = Wallet.matrix("hello", CodeFormat.Qr)!!
        assertEquals(qr.width, qr.height)
    }

    @Test
    fun `cards come back from their JSON and a damaged one is skipped`() {
        val cards = listOf(Card("a", "Coop", "123456", CodeFormat.Qr, CardKind.Ticket, 20_000, "note", 3, 5), Card("b", "Shop", "X"))
        assertEquals(cards, Wallet.decode(Wallet.encode(cards)))
        assertTrue(Wallet.decode("nope").isEmpty())
        assertEquals(listOf("ok"), Wallet.decode("""[{"x":1},{"id":"ok","name":"n","code":"c"}]""").map { it.id })
    }

    @Test
    fun `days left count to the last day`() {
        val card = Card("a", "n", "c", expiry = 100)
        assertEquals(5L, Wallet.daysLeft(card, 95))
        assertEquals(-1L, Wallet.daysLeft(card, 101))
        assertNull(Wallet.daysLeft(Card("b", "n", "c"), 0))
    }

    @Test
    fun `restoring replaces the card with the same id and adds the others`() {
        val old = Card("a", "old", "1")
        val merged = Wallet.merge(listOf(old, Card("b", "b", "2")), listOf(Card("a", "new", "1"), Card("c", "c", "3")))
        assertEquals(listOf("new", "b", "c"), merged.map { it.name })
    }

    // --- the news sources

    @Test
    fun `the news app starts with Google and the source the settings had`() {
        assertEquals(listOf("google"), NewsSourceList.initial(NewsSources.GOOGLE_TOP, "") { it }.map { it.id })
        val topic = NewsSourceList.initial(NewsSources.topic("SPORTS"), "") { "sport" }
        assertEquals(listOf("google", "google:SPORTS"), topic.map { it.id })
        val custom = NewsSourceList.initial(NewsSources.CUSTOM, "example.com/feed.xml") { it }
        assertEquals("https://example.com/feed.xml", custom.last().url)
        assertEquals(1, NewsSourceList.initial(NewsSources.CUSTOM, "ftp://bad") { it }.size)
    }

    @Test
    fun `a feed of the user is added only with a good address and only once`() {
        val list = listOf(NewsSourceList.googleTop())
        val added = NewsSourceList.addFeed(list, " My blog ", "blog.example.org/rss")!!
        assertEquals("My blog", added.last().name)
        assertEquals("https://blog.example.org/rss", added.last().url)
        assertNull(NewsSourceList.addFeed(added, "x", "https://blog.example.org/rss"))
        assertNull(NewsSourceList.addFeed(list, "x", "http://insecure.example.org/rss"))
        assertNull(NewsSourceList.addFeed(list, "x", "not a url"))
        assertEquals("example.org", NewsSourceList.addFeed(list, "", "https://www.example.org/feed")!!.last().name)
    }

    @Test
    fun `the list is never empty and comes back from its JSON`() {
        assertEquals(listOf("google"), NewsSourceList.remove(listOf(NewsSourceDef("x", "x", NewsSources.GOOGLE_TOP)), "x").map { it.id })
        val list = NewsSourceList.add(listOf(NewsSourceList.googleTop()), NewsPresets.feeds.first())
        assertEquals(list, NewsSourceList.decode(NewsSourceList.encode(list)))
        assertTrue(NewsSourceList.decode("[{\"id\":\"c\",\"name\":\"c\",\"code\":\"custom\",\"url\":\"ftp://x\"}]").isEmpty())
        assertNotNull(NewsPresets.feeds.all { NewsSources.customUrl(it.url) != null }.takeIf { it })
    }
}
