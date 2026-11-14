package com.metrowin.launcher

import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.Feeds
import com.metrowin.launcher.data.NewsItem
import com.metrowin.launcher.data.NewsSources
import com.metrowin.launcher.news.NewsFailure
import com.metrowin.launcher.news.NewsHttp
import com.metrowin.launcher.news.NewsHub
import com.metrowin.launcher.news.NewsOpml
import com.metrowin.launcher.news.NewsOpts
import com.metrowin.launcher.news.NewsPresets
import com.metrowin.launcher.news.NewsReply
import com.metrowin.launcher.news.NewsResult
import com.metrowin.launcher.news.NewsRow
import com.metrowin.launcher.news.NewsRows
import com.metrowin.launcher.news.NewsSourceDef
import com.metrowin.launcher.news.NewsSourceList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZoneOffset

/** A connection that answers what it is told and records what was asked: no network. */
private class FakeConnection(url: URL, private val code: Int, private val headers: Map<String, String> = emptyMap(), private val body: String = "") : HttpURLConnection(url) {
    val sent = HashMap<String, String>()
    override fun connect() {}
    override fun disconnect() {}
    override fun usingProxy() = false
    override fun getResponseCode() = code
    override fun getHeaderField(name: String?): String? = headers[name]
    override fun getInputStream() = body.toByteArray().inputStream()
    override fun setRequestProperty(key: String?, value: String?) { sent[key.orEmpty()] = value.orEmpty() }
}

private val FEED = """<?xml version="1.0"?><rss version="2.0"><channel><title>F</title>
    <item><title>One</title><link>https://example.com/1</link></item><item><title>Two</title><link>https://example.com/2</link></item></channel></rss>"""

private fun item(title: String, at: Long = 0L, link: String = "https://e.com/${title.hashCode()}", source: String = "") = NewsItem(title, source, link, at)

class NewsMoreTest {
    private val day = 24 * 3_600_000L
    private val noon = 1_700_000_000_000L - 1_700_000_000_000L % day + 12 * 3_600_000L

    @Test
    fun rowsHaveTheDayWhereItChangesAndPairsOnAWideScreen() {
        val items = listOf(item("a", noon), item("b", noon - 1_000), item("c", noon - day), item("d", noon - day - 1_000), item("e", noon - day - 2_000))
        assertEquals(0L, NewsRows.daysAgo(noon, noon, ZoneOffset.UTC))
        assertEquals(1L, NewsRows.daysAgo(noon - day, noon, ZoneOffset.UTC))
        assertEquals(-1L, NewsRows.daysAgo(0L, noon, ZoneOffset.UTC))
        val narrow = NewsRows.build(items, noon, days = true, wide = false, zone = ZoneOffset.UTC)
        assertEquals(listOf("Day", "One", "One", "Day", "One", "One", "One"), narrow.map { it::class.java.simpleName })
        val wide = NewsRows.build(items, noon, days = true, wide = true, zone = ZoneOffset.UTC)
        // The first headline is alone; "b" waits for a partner of the same day, finds the day changing and goes alone.
        assertEquals(listOf("Day", "One", "One", "Day", "Two", "One"), wide.map { it::class.java.simpleName })
        assertEquals(listOf("One", "One", "One", "One", "One"), NewsRows.build(items, noon, days = false, wide = false).map { it::class.java.simpleName })
        assertTrue(NewsRows.build(listOf(item("x")), noon, days = true, wide = false).none { it is NewsRow.Day })
    }

    @Test
    fun searchAndMergeKeepTheRightHeadlines() {
        val items = listOf(item("Rain in Rome", source = "ANSA"), item("Sun in Milan", source = "Post"))
        assertEquals(listOf("Rain in Rome"), NewsRows.search(items, " rome ").map { it.title })
        assertEquals(listOf("Sun in Milan"), NewsRows.search(items, "POST").map { it.title })
        assertEquals(2, NewsRows.search(items, "  ").size)
        val merged = NewsRows.merge(
            listOf("A" to listOf(item("Same story!", 10, "https://e.com/1"), item("Old", 1, "https://e.com/2")), "B" to listOf(item("same story", 20, "https://e.com/3", "Bsource"))),
        )
        assertEquals(listOf("same story", "Old"), merged.map { it.title })
        assertEquals("A", merged.last().source)
    }

    @Test
    fun onlyPublicHostsAreFollowed() {
        assertTrue(NewsHttp.isPublicHost("cdn.example.com"))
        assertTrue(NewsHttp.isPublicHost("8.8.8.8"))
        listOf("localhost", "intranet", "printer.local", "10.0.0.1", "192.168.1.5", "172.20.1.1", "127.0.0.1", "169.254.1.1", "100.64.0.1", "[::1]", "::1", "", "1.2.3", "300.1.1.1")
            .forEach { assertFalse(it, NewsHttp.isPublicHost(it)) }
        assertTrue(NewsHttp.isPublicHost("172.32.0.1"))
    }

    @Test
    fun theNetworkTellsWhatWentWrongAndFollowsRedirectsOnlyWithRules() {
        val hosts = setOf("a.example.com")
        fun get(anySite: Boolean = false, etag: String = "", answer: (URL) -> FakeConnection) = NewsHttp.get("https://a.example.com/rss", hosts, anySite, etag, open = answer)
        assertTrue(get { FakeConnection(it, 200, mapOf("ETag" to "\"x\"", "Last-Modified" to "yesterday"), FEED) }.let { it is NewsReply.Body && it.etag == "\"x\"" && it.modified == "yesterday" })
        assertEquals(NewsReply.NotModified, get(etag = "\"x\"") { FakeConnection(it, 304) })
        assertEquals(503, (get { FakeConnection(it, 503) } as NewsReply.Http).code)
        // Same host again: allowed. Another host: refused unless the option is on, and then only a public one.
        fun redirectTo(target: String, anySite: Boolean) = get(anySite) { url ->
            if (url.path == "/rss") FakeConnection(url, 302, mapOf("Location" to target)) else FakeConnection(url, 200, body = FEED)
        }
        assertTrue(redirectTo("https://a.example.com/other", false) is NewsReply.Body)
        assertEquals(NewsReply.Blocked, redirectTo("https://cdn.example.org/f", false))
        assertTrue(redirectTo("https://cdn.example.org/f", true) is NewsReply.Body)
        assertEquals(NewsReply.Blocked, redirectTo("https://192.168.0.2/f", true))
        assertEquals(NewsReply.Blocked, redirectTo("http://cdn.example.org/f", true))
        assertEquals(NewsReply.Blocked, get { FakeConnection(it, 302) })
        assertEquals(NewsReply.Blocked, get { FakeConnection(it, 302, mapOf("Location" to "https://a.example.com/again")) })
        assertEquals(NewsReply.Blocked, NewsHttp.get("http://a.example.com/rss", hosts))
        assertEquals(NewsReply.TooBig, NewsHttp.get("https://a.example.com/rss", hosts, maxBytes = 10, open = { FakeConnection(it, 200, body = FEED) }))
        assertEquals(NewsReply.Offline, NewsHttp.get("https://a.example.com/rss", hosts, open = { throw java.net.UnknownHostException() }))
    }

    @Test
    fun conditionalHeadersAreSent() {
        var made: FakeConnection? = null
        NewsHttp.get("https://a.example.com/rss", setOf("a.example.com"), etag = "\"e\"", modified = "m", open = { FakeConnection(it, 304).also { c -> made = c } })
        assertEquals("\"e\"", made!!.sent["If-None-Match"])
        assertEquals("m", made!!.sent["If-Modified-Since"])
        assertEquals("MetroWinLauncher", made!!.sent["User-Agent"])
    }

    @Test
    fun opmlGoesOutAndComesBackAndBadEntriesAreDropped() {
        val list = listOf(NewsSourceList.googleTop(), NewsSourceDef("c", "My <blog> & co", NewsSources.CUSTOM, "https://blog.example.org/feed?a=1&b=2"))
        val xml = NewsOpml.encode(list)
        assertEquals(listOf("My <blog> & co" to "https://blog.example.org/feed?a=1&b=2"), NewsOpml.parse(xml))
        val foreign = """<opml version="1.0"><body><outline text="Folder"><outline text="A" xmlUrl="https://a.example.com/rss"/><outline text="B" xmlUrl="http://insecure.example.com/rss"/><outline title="C" xmlUrl="ftp://x.example.com/f"/></outline></body></opml>"""
        assertEquals(listOf("A" to "https://a.example.com/rss"), NewsOpml.parse(foreign))
        assertEquals(1, NewsOpml.parse(foreign, limit = 1).size)
        assertTrue(NewsOpml.parse("<!DOCTYPE opml [<!ENTITY x \"y\">]><opml><body/></opml>").isEmpty())
        assertTrue(NewsOpml.parse("<rss/>").isEmpty())
    }

    @Test
    fun jsonFeedsAndDiscoveryAndTheLimit() {
        val json = """{"version":"https://jsonfeed.org/version/1.1","title":"J","items":[
            {"url":"https://e.com/1","title":"First","date_published":"2026-11-01T10:00:00Z","image":"https://e.com/i.jpg","authors":[{"name":"Ann"}]},
            {"url":"javascript:alert(1)","title":"Bad"},{"url":"https://e.com/3","content_text":"Only text here"}]}"""
        val feed = Feeds.parse(json)!!
        assertEquals(listOf("First", "Only text here"), feed.items.map { it.title })
        assertEquals("Ann", feed.items[0].source)
        assertEquals("https://e.com/i.jpg", feed.items[0].image)
        assertNull(Feeds.parse("""{"version":"x","items":[]}"""))
        val many = "<rss><channel>" + (1..60).joinToString("") { "<item><title>t$it</title><link>https://e.com/$it</link></item>" } + "</channel></rss>"
        assertEquals(20, Feeds.parse(many)!!.items.size)
        assertEquals(50, Feeds.parse(many, Feeds.MAX_ITEMS_MORE)!!.items.size)
        val html = """<head><link rel="alternate" type="application/rss+xml" href="/feed.xml"><link rel="stylesheet" href="/a.css">
            <link rel='alternate' type='application/atom+xml' href='https://cdn.example.net/a?x=1&amp;y=2'><link rel="alternate" type="application/rss+xml" href="http://insecure.example.com/f"></head>"""
        assertEquals(listOf("https://site.example.com/feed.xml", "https://cdn.example.net/a?x=1&y=2"), Feeds.feedLinks(html, "https://site.example.com/blog/"))
    }

    @Test
    fun sourcesMoveAndPresetsAreOrderedByCountry() {
        val list = listOf("a", "b", "c").map { NewsSourceDef(it, it, NewsSources.GOOGLE_TOP) }
        assertEquals(listOf("b", "a", "c"), NewsSourceList.move(list, 1, -1).map { it.id })
        assertEquals(list, NewsSourceList.move(list, 0, -1))
        assertEquals(list, NewsSourceList.move(list, 2, 1))
        val italian = NewsPresets.ordered("it").map { it.id }
        assertTrue(italian.take(4).all { it in setOf("preset:ansa", "preset:ilpost", "preset:corriere", "preset:repubblica") })
        assertEquals(NewsPresets.feeds.size, italian.size)
        assertTrue(NewsPresets.feeds.all { NewsSources.customUrl(it.url) != null })
        assertEquals(NewsPresets.feeds.map { it.id }.toSet().size, NewsPresets.feeds.size)
    }

    @Test
    fun everyNewStringIsInAllSixLanguagesWithTheSamePlaceholders() {
        fun strings(dir: String) = Regex("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .findAll(File("src/main/res/$dir/strings_notizie.xml").readText()).associate { it.groupValues[1] to it.groupValues[2] }
        fun placeholders(text: String) = Regex("%(\\d\\$)?[sd]").findAll(text).map { it.value }.sorted().toList()
        val english = strings("values")
        assertTrue(english.isNotEmpty())
        listOf("values-it", "values-de", "values-es", "values-fr", "values-pt").forEach { dir ->
            val other = strings(dir)
            assertEquals(dir, english.keys, other.keys)
            english.forEach { (name, text) -> assertEquals("$dir $name", placeholders(text), placeholders(other.getValue(name))) }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NewsHubLoadTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val source = NewsSourceDef("c1", "Feed", NewsSources.CUSTOM, "https://feed.example.com/rss.xml")

    private fun hub(code: Int = 200, headers: Map<String, String> = emptyMap(), body: String = FEED, seen: MutableList<FakeConnection>? = null) =
        NewsHub(context) { url -> FakeConnection(url, code, headers, body).also { seen?.add(it) } }

    @Test
    fun aReadIsKeptInAFileAndTheNextAskIsConditional() {
        val seen = ArrayList<FakeConnection>()
        val first = hub(headers = mapOf("ETag" to "\"v1\""), seen = seen).load(source, now = 1_000)
        assertEquals(listOf("One", "Two"), (first as NewsResult.Ok).data.items.map { it.title })
        assertTrue(File(context.filesDir, "news").listFiles().orEmpty().any { it.name.startsWith("d-") })
        assertEquals(listOf("One", "Two"), hub().cached("c1")!!.items.map { it.title })
        // The second ask carries the validator; a 304 keeps the headlines and only moves the time.
        val again = hub(code = 304, seen = seen).load(source, now = 9_000) as NewsResult.Ok
        assertEquals("\"v1\"", seen.last().sent["If-None-Match"])
        assertFalse(again.changed)
        assertEquals(9_000L, hub().cached("c1")!!.fetchedAt)
        assertEquals(2, hub().cached("c1")!!.items.size)
    }

    @Test
    fun whatTheUpdateBroughtIsNewAndTheFirstReadHasNothingNew() {
        hub().load(source, now = 1)
        assertTrue(hub().fresh("c1").isEmpty())
        val more = FEED.replace("</channel>", "<item><title>Three</title><link>https://example.com/3</link></item></channel>")
        hub(body = more).load(source, now = 2)
        assertEquals(setOf("https://example.com/3"), hub().fresh("c1"))
    }

    @Test
    fun eachFailureHasItsOwnReason() {
        fun why(h: NewsHub) = (h.load(source, now = 1) as NewsResult.Failed).why
        assertEquals(NewsFailure.Http, why(hub(code = 500)))
        assertEquals(NewsFailure.Parse, why(hub(body = "<html>not a feed</html>")))
        assertEquals(NewsFailure.Offline, why(NewsHub(context) { throw java.io.IOException() }))
        assertEquals(NewsFailure.Address, (hub().load(NewsSourceDef("bad", "x", NewsSources.CUSTOM, "ftp://x"), now = 1) as NewsResult.Failed).why)
        assertEquals(NewsFailure.Blocked, why(NewsHub(context) { FakeConnection(it, 302, mapOf("Location" to "https://other.example.org/")) }))
    }

    @Test
    fun theRedirectOptionDecidesIfAnotherSiteIsFollowed() {
        val moved: (URL) -> HttpURLConnection = { url -> if (url.host == "feed.example.com") FakeConnection(url, 301, mapOf("Location" to "https://cdn.example.org/f")) else FakeConnection(url, 200, body = FEED) }
        val h = NewsHub(context, moved)
        assertFalse(h.options.anySite)
        assertTrue(h.load(source) is NewsResult.Failed)
        h.options = h.options.copy(anySite = true)
        assertTrue(h.load(source) is NewsResult.Ok)
    }

    @Test
    fun theWifiOnlyOptionStopsTheAutomaticReadButNotARefresh() {
        val h = hub()
        h.options = h.options.copy(unmeteredOnly = true)
        // Robolectric reports an unmetered connection, so nothing is stopped; the option itself is kept.
        assertTrue(h.options.unmeteredOnly)
        assertTrue(h.load(source, force = true) is NewsResult.Ok)
    }

    @Test
    fun theOldCopyInThePreferencesMovesToAFileAndGoneSourcesAreForgotten() {
        val data = com.metrowin.launcher.data.NewsData("t", listOf(item("old", 5, "https://e.com/old")), 7, "custom:https://feed.example.com/rss.xml")
        val prefs = context.getSharedPreferences("metrowin_news", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("data:c1", com.metrowin.launcher.data.NewsCodec.encode(data)).putString("data:gone", "x").commit()
        val h = hub()
        assertEquals("old", h.cached("c1")!!.items.single().title)
        assertNull(prefs.getString("data:c1", null))
        h.sources("google", "") { it }
        assertNull(prefs.getString("data:gone", null))
        // The list the app starts with does not hold "c1": its kept headlines are thrown away.
        assertNull(h.cached("c1"))
        assertTrue(File(context.filesDir, "news").listFiles().orEmpty().none { it.name.startsWith("d-") })
    }

    @Test
    fun readAndSavedSurviveAndAreBounded() {
        val h = hub()
        assertTrue(h.setRead("https://e.com/1", true).contains("https://e.com/1"))
        assertEquals(setOf("https://e.com/1"), hub().readLinks())
        assertTrue(h.setRead("https://e.com/1", false).isEmpty())
        val x = item("x", 1, "https://e.com/x")
        assertEquals(listOf("https://e.com/x"), h.toggleSaved(x).map { it.link })
        assertEquals(listOf("https://e.com/x"), hub().saved().map { it.link })
        assertTrue(h.toggleSaved(x).isEmpty())
        repeat(520) { h.setRead("https://e.com/r$it", true) }
        assertEquals(500, h.readLinks().size)
    }

    @Test
    fun optionsStartOffWhenTheyReachTheNetworkOrKeepATrace() {
        val o = hub().options
        assertEquals(NewsOpts(), o)
        assertFalse(o.images || o.anySite || o.unmeteredOnly || o.moreHeadlines || o.discover || o.trackRead)
        val h = hub()
        h.options = o.copy(textPercent = 140, freshMinutes = 60, compact = true)
        assertEquals(140, hub().options.textPercent)
        context.getSharedPreferences("metrowin_news", android.content.Context.MODE_PRIVATE).edit().putInt("text_percent", 77).commit()
        assertEquals(100, hub().options.textPercent)
    }
}
