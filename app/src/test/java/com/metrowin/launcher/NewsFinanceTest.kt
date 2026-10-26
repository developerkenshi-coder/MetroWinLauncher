package com.metrowin.launcher

import com.metrowin.launcher.data.Feeds
import com.metrowin.launcher.data.Finance
import com.metrowin.launcher.data.FinanceCodec
import com.metrowin.launcher.data.FinanceData
import com.metrowin.launcher.data.FinanceItems
import com.metrowin.launcher.data.NewsAge
import com.metrowin.launcher.data.NewsCodec
import com.metrowin.launcher.data.NewsData
import com.metrowin.launcher.data.NewsEdition
import com.metrowin.launcher.data.NewsItem
import com.metrowin.launcher.data.NewsSources
import com.metrowin.launcher.data.Quote
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The News and the Finance tiles: the feed of Google News (a real answer, trimmed), the other feeds, the rates and the coins. */
class NewsFinanceTest {
    private fun fixture(name: String) = javaClass.getResource("/feeds/$name")!!.readText()

    // --- the feed of Google News

    @Test
    fun theTopStoriesOfGoogleNewsAreRead() {
        val feed = Feeds.parse(fixture("google_news_it.xml"))!!
        assertEquals("Notizie principali - Google News", feed.title)
        assertEquals(4, feed.items.size)
        val first = feed.items.first()
        // The publisher is said apart, so it comes off the headline.
        assertEquals("Elezioni Brasile, chiuse le urne. Testa a testa Lula-Bolsonaro, Flávio in leggero vantaggio", first.title)
        assertEquals("la Repubblica", first.source)
        assertTrue(first.link.startsWith("https://news.google.com/rss/articles/"))
        assertEquals(Instant.parse("2026-10-25T19:03:45Z").toEpochMilli(), first.publishedAt)
        assertEquals("Il Sole 24 ORE", feed.items[2].source)
        assertTrue(feed.items[2].title.endsWith("«Stop soldi e armi a Kiev»"))
    }

    @Test
    fun anAtomFeedIsReadToo() {
        val atom = """<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom"><title>Blog</title>
            <entry><title>Hello &amp; welcome</title><link rel="self" href="https://a.example/self"/><link rel="alternate" href="https://a.example/1"/>
            <updated>2026-10-25T10:00:00Z</updated></entry>
            <entry><title>Second</title><link href="https://a.example/2"/><updated>2026-10-24T10:00:00+02:00</updated></entry></feed>"""
        val feed = Feeds.parse(atom)!!
        assertEquals("Blog", feed.title)
        assertEquals(listOf("Hello & welcome", "Second"), feed.items.map { it.title })
        assertEquals(listOf("https://a.example/1", "https://a.example/2"), feed.items.map { it.link })
        assertEquals(Instant.parse("2026-10-24T08:00:00Z").toEpochMilli(), feed.items[1].publishedAt)
    }

    @Test
    fun aFeedWithADocumentTypeIsRefused() {
        val xxe = """<?xml version="1.0"?><!DOCTYPE rss [<!ENTITY x SYSTEM "file:///etc/passwd">]><rss><channel><title>T</title>
            <item><title>&x;</title><link>https://a.example/1</link></item></channel></rss>"""
        assertNull(Feeds.parse(xxe))
        assertNull(Feeds.parse("<html><body>not a feed</body></html>"))
        assertNull(Feeds.parse("not xml at all"))
        assertNull(Feeds.parse("<rss><channel><title>empty</title></channel></rss>"))
    }

    @Test
    fun onlyWebLinksAndPlainTextGetThrough() {
        val rss = """<rss><channel><title>T</title>
            <item><title>Bad link</title><link>javascript:alert(1)</link></item>
            <item><title>File link</title><link>file:///sdcard/x</link></item>
            <item><title>&lt;b&gt;Bold&lt;/b&gt; and &amp;amp; more&amp;nbsp;spaces   here</title><link> https://ok.example/a </link></item>
            <item><title></title><link>https://ok.example/b</link></item></channel></rss>"""
        val feed = Feeds.parse(rss)!!
        assertEquals(1, feed.items.size)
        assertEquals("Bold and & more spaces here", feed.items[0].title)
        assertEquals("https://ok.example/a", feed.items[0].link)
        assertNull(Feeds.safeLink("intent://x#Intent;end"))
        assertNull(Feeds.safeLink(null))
    }

    @Test
    fun aLongFeedIsKeptShort() {
        val items = (1..60).joinToString("") { "<item><title>News $it</title><link>https://a.example/$it</link></item>" }
        val feed = Feeds.parse("<rss><channel><title>T</title>$items</channel></rss>")!!
        assertEquals(Feeds.MAX_ITEMS, feed.items.size)
        assertEquals("News 1", feed.items.first().title)
    }

    // --- where the news come from

    @Test
    fun theEditionFollowsTheLanguageAndTheRegionOfThePhone() {
        assertEquals(NewsEdition("it", "IT", "IT:it"), NewsEdition.of(Locale.ITALY))
        assertEquals(NewsEdition("en-US", "US", "US:en"), NewsEdition.of(Locale.US))
        assertEquals(NewsEdition("en-GB", "GB", "GB:en"), NewsEdition.of(Locale.UK))
        assertEquals(NewsEdition("de", "DE", "DE:de"), NewsEdition.of(Locale("de")))
        assertEquals(NewsEdition("en-US", "US", "US:en"), NewsEdition.of(Locale.ROOT))
    }

    @Test
    fun googleNewsIsTheDefaultSourceAndItsTopicsAreAChoice() {
        val it = NewsEdition.of(Locale.ITALY)
        assertEquals("google", NewsSources.GOOGLE_TOP)
        assertEquals("https://news.google.com/rss?hl=it&gl=IT&ceid=IT:it", NewsSources.url("google", "", it))
        assertEquals(
            "https://news.google.com/rss/headlines/section/topic/TECHNOLOGY?hl=it&gl=IT&ceid=IT:it",
            NewsSources.url(NewsSources.topic("TECHNOLOGY"), "", it),
        )
        assertEquals(10, NewsSources.choices.size)
        assertEquals("google", NewsSources.normalize("nonsense"))
        assertEquals("google", NewsSources.normalize(null))
        assertEquals("google:SPORTS", NewsSources.normalize("google:SPORTS"))
        assertEquals("google", NewsSources.normalize("google:NOPE"))
        assertTrue(NewsSources.isGoogle("google:SCIENCE") && NewsSources.isGoogle("google") && !NewsSources.isGoogle("custom"))
        assertEquals(setOf("news.google.com", "www.news.google.com"), NewsSources.hosts("https://news.google.com/rss"))
    }

    @Test
    fun aFeedOfYourOwnMustBeAWebAddress() {
        val it = NewsEdition.of(Locale.ITALY)
        assertEquals("https://www.ansa.it/sito/ansait_rss.xml", NewsSources.url("custom", "https://www.ansa.it/sito/ansait_rss.xml", it))
        assertEquals("https://example.com/feed", NewsSources.url("custom", " example.com/feed ", it))
        assertNull(NewsSources.customUrl("http://example.com/feed"))
        assertNull(NewsSources.customUrl("ftp://example.com/feed"))
        assertNull(NewsSources.customUrl("javascript:alert(1)"))
        assertNull(NewsSources.customUrl("https://user:pw@example.com/feed"))
        assertNull(NewsSources.customUrl("two words"))
        assertNull(NewsSources.customUrl("localhost/feed"))
        assertNull(NewsSources.customUrl(""))
        assertNull(NewsSources.url("custom", "", it))
        assertEquals(setOf("example.com", "www.example.com"), NewsSources.hosts("https://example.com/feed"))
        assertEquals(setOf("www.example.com", "example.com"), NewsSources.hosts("https://www.example.com/feed"))
    }

    @Test
    fun changingTheSourceOrTheLanguageMakesNewHeadlinesNecessary() {
        val it = NewsEdition.of(Locale.ITALY)
        val us = NewsEdition.of(Locale.US)
        assertFalse(NewsSources.key("google", "", it) == NewsSources.key("google", "", us))
        assertFalse(NewsSources.key("google", "", it) == NewsSources.key("google:WORLD", "", it))
        assertFalse(NewsSources.key("custom", "a.example/x", it) == NewsSources.key("custom", "b.example/x", it))
    }

    @Test
    fun theHeadlinesSurviveTheTripToDisk() {
        val data = NewsData("T", listOf(NewsItem("A", "S", "https://a.example/1", 5L), NewsItem("B", "", "https://a.example/2", 0L)), 99L, "google:it:IT")
        assertEquals(data, NewsCodec.decode(NewsCodec.encode(data)))
        assertNull(NewsCodec.decode("{}"))
        assertNull(NewsCodec.decode(null))
        assertNull(NewsCodec.decode("""{"title":"T","fetchedAt":1,"key":"k","items":[{"t":"x","s":"","l":"javascript:1","p":0}]}"""))
    }

    @Test
    fun anAgeIsInTheUnitThatMeansSomething() {
        val now = Instant.parse("2026-10-25T12:00:00Z").toEpochMilli()
        fun age(ago: Long) = NewsAge.of(now - ago, now)!!
        assertEquals(NewsAge.Unit.Now, age(20_000).unit)
        assertEquals(12, age(12 * 60_000L).value)
        assertEquals(NewsAge.Unit.Hours, age(3 * 3_600_000L).unit)
        assertEquals(3, age(3 * 3_600_000L).value)
        assertEquals(NewsAge.Unit.Days, age(50 * 3_600_000L).unit)
        assertEquals(2, age(50 * 3_600_000L).value)
        assertNull(NewsAge.of(0L, now))
        // A date in the future (the clock was set back) is "now".
        assertEquals(NewsAge.Unit.Now, NewsAge.of(now + 1_000_000, now)!!.unit)
    }

    // --- finance

    private val today = LocalDate.of(2026, 10, 25)
    private val rates = """{"amount":1.0,"base":"EUR","start_date":"2026-10-19","end_date":"2026-10-23","rates":{
        "2026-10-19":{"GBP":0.84205,"USD":1.1225},"2026-10-20":{"GBP":0.84028,"USD":1.1215},"2026-10-21":{"GBP":0.83825,"USD":1.1234},
        "2026-10-22":{"GBP":0.8377,"USD":1.1205},"2026-10-23":{"GBP":0.83728,"USD":1.1193}}}"""

    @Test
    fun theRatesAreAskedForOnlyWhatIsFollowed() {
        assertEquals(
            "https://api.frankfurter.dev/v1/2026-10-19..?base=EUR&symbols=GBP,USD",
            Finance.fxUrl("EUR", listOf("fx:USD", "fx:GBP", "crypto:bitcoin"), today),
        )
        // With the dollar as the base the euro needs no symbol of its own and the dollar must be there.
        assertEquals("https://api.frankfurter.dev/v1/2026-10-19..?base=EUR&symbols=JPY,USD", Finance.fxUrl("USD", listOf("fx:EUR", "fx:JPY"), today))
        assertNull(Finance.fxUrl("EUR", listOf("crypto:bitcoin"), today))
        assertEquals(
            "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum&vs_currencies=eur&include_24hr_change=true",
            Finance.coinUrl("EUR", listOf("fx:USD", "crypto:bitcoin", "crypto:ethereum")),
        )
        assertNull(Finance.coinUrl("EUR", listOf("fx:USD")))
    }

    @Test
    fun aCurrencyIsWorthWhatTheLatestRatesSayAndMovedSinceTheDayBefore() {
        val parsed = Finance.parseFx(rates, "EUR", listOf("fx:USD", "fx:GBP"))!!
        assertEquals("2026-10-23", parsed.date)
        val usd = parsed.quotes.first { it.item == "fx:USD" }
        assertEquals(1.0 / 1.1193, usd.value, 1e-9)
        assertEquals((1.1205 / 1.1193 - 1) * 100, usd.changePercent, 1e-9)
        val gbp = parsed.quotes.first { it.item == "fx:GBP" }
        assertEquals(1.0 / 0.83728, gbp.value, 1e-9)
        assertTrue(gbp.changePercent > 0)
    }

    @Test
    fun theBaseCurrencyMayBeAnyOfTheRates() {
        // One euro in dollars, and one pound in dollars.
        val parsed = Finance.parseFx(rates, "USD", listOf("fx:EUR", "fx:GBP"))!!
        assertEquals(1.1193, parsed.quotes.first { it.item == "fx:EUR" }.value, 1e-9)
        assertEquals(1.1193 / 0.83728, parsed.quotes.first { it.item == "fx:GBP" }.value, 1e-9)
        assertTrue(parsed.quotes.first { it.item == "fx:EUR" }.changePercent < 0)
    }

    @Test
    fun aSingleDayOfRatesHasNoChange() {
        val one = """{"base":"EUR","rates":{"2026-10-23":{"USD":1.1193}}}"""
        val quote = Finance.parseFx(one, "EUR", listOf("fx:USD"))!!.quotes.single()
        assertTrue(quote.changePercent.isNaN())
        assertNull(Finance.formatChange(quote.changePercent))
        assertNull(Finance.parseFx("{}", "EUR", listOf("fx:USD")))
        assertNull(Finance.parseFx("""{"rates":{}}""", "EUR", listOf("fx:USD")))
        assertNull(Finance.parseFx("nonsense", "EUR", listOf("fx:USD")))
    }

    @Test
    fun theCoinsAreReadFromCoinGecko() {
        val json = """{"bitcoin":{"eur":71712,"eur_24h_change":-0.24775777919314163},"ethereum":{"eur":2086.71,"eur_24h_change":-0.2931048523361436}}"""
        val quotes = Finance.parseCoins(json, "EUR", listOf("crypto:bitcoin", "crypto:ethereum", "crypto:solana"))
        assertEquals(listOf("crypto:bitcoin", "crypto:ethereum"), quotes.map { it.item })
        assertEquals(71712.0, quotes[0].value, 0.0)
        assertEquals(-0.24775777919314163, quotes[0].changePercent, 1e-12)
        // A currency CoinGecko does not know gives nothing, not a wrong number.
        assertTrue(Finance.parseCoins("{}", "BGN", listOf("crypto:bitcoin")).isEmpty())
        assertTrue(Finance.parseCoins("garbage", "EUR", listOf("crypto:bitcoin")).isEmpty())
    }

    @Test
    fun theQuotesComeInTheOrderTheUserChose() {
        val quotes = listOf(Quote("crypto:bitcoin", 1.0, 0.0), Quote("fx:USD", 2.0, 0.0), Quote("fx:GBP", 3.0, 0.0))
        assertEquals(listOf("fx:GBP", "crypto:bitcoin"), Finance.ordered(listOf("fx:GBP", "crypto:bitcoin", "fx:JPY"), quotes).map { it.item })
    }

    @Test
    fun theBaseCurrencyIsTheOneChosenOrTheOneOfTheRegion() {
        assertEquals("USD", Finance.baseFor("USD", Locale.ITALY))
        assertEquals("USD", Finance.baseFor("", Locale.US))
        assertEquals("EUR", Finance.baseFor("", Locale.ITALY))
        assertEquals("JPY", Finance.baseFor("", Locale.JAPAN))
        // A currency the rates do not cover, and a phone with no region: the euro.
        assertEquals("EUR", Finance.baseFor("", Locale("en", "AE")))
        assertEquals("EUR", Finance.baseFor("", Locale.ROOT))
        assertEquals("EUR", Finance.baseFor("XXX", Locale.ROOT))
    }

    @Test
    fun whatIsFollowedIsCheckedAndLimited() {
        val many = listOf("fx:USD", "fx:USD", "fx:GBP", "fx:EUR", "fx:JPY", "fx:CHF", "crypto:bitcoin", "crypto:nonsense", "fx:XXX", "stocks:AAPL")
        assertEquals(listOf("fx:USD", "fx:GBP", "fx:JPY", "fx:CHF"), FinanceItems.normalize(many, "EUR"))
        assertEquals("USD", FinanceItems.symbol("fx:USD"))
        assertEquals("BTC", FinanceItems.symbol("crypto:bitcoin"))
        assertFalse(FinanceItems.available("EUR").contains("fx:EUR"))
        assertTrue(FinanceItems.available("EUR").containsAll(listOf("fx:USD", "crypto:ethereum")))
        assertTrue(FinanceItems.DEFAULT.all(FinanceItems::isValid))
    }

    @Test
    fun numbersAreWrittenWithTheDigitsThatHelp() {
        val us = Locale.US
        assertEquals("71,712", Finance.format(71712.4, us))
        assertEquals("2,087", Finance.format(2086.71, us))
        assertEquals("0.8934", Finance.format(0.89337, us))
        assertEquals("1.119", Finance.format(1.1193, us))
        assertEquals("+0.11%", Finance.formatChange(0.1072, us))
        assertEquals("-0.25%", Finance.formatChange(-0.2477, us))
        assertEquals(1, Finance.trend(0.2))
        assertEquals(-1, Finance.trend(-0.2))
        assertEquals(0, Finance.trend(0.001))
        assertEquals(0, Finance.trend(Double.NaN))
    }

    @Test
    fun theQuotesSurviveTheTripToDisk() {
        val data = FinanceData("EUR", listOf(Quote("fx:USD", 0.89, 0.1), Quote("crypto:bitcoin", 71712.0, Double.NaN)), "2026-10-23", 5L, "EUR|fx:USD,crypto:bitcoin")
        val back = FinanceCodec.decode(FinanceCodec.encode(data))!!
        assertEquals(data.quotes[0], back.quotes[0])
        assertTrue(back.quotes[1].changePercent.isNaN())
        assertEquals(data.key, back.key)
        assertNotNull(back)
        assertNull(FinanceCodec.decode("{}"))
        assertNull(FinanceCodec.decode("""{"base":"EUR","fetchedAt":1,"quotes":[{"i":"stocks:AAPL","v":1}]}"""))
    }
}
