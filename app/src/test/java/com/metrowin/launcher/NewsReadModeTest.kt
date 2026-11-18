package com.metrowin.launcher

import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.Feeds
import com.metrowin.launcher.data.NewsCodec
import com.metrowin.launcher.data.NewsData
import com.metrowin.launcher.data.NewsItem
import com.metrowin.launcher.news.NewsFailure
import com.metrowin.launcher.news.NewsHub
import com.metrowin.launcher.news.NewsOpts
import com.metrowin.launcher.news.ReadBlock
import com.metrowin.launcher.news.ReadResult
import com.metrowin.launcher.news.ReadableText
import com.metrowin.launcher.ui.settings.MiniOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.net.URL

private val LONG = "The council met on Tuesday evening and, after a long debate about the new bus lines, voted to keep the night service running until the end of the year. "
private val STORY = LONG.repeat(2)

private val PAGE = """
<html><head><title>Site - Story</title><meta property="og:image" content="https://cdn.example.com/lead.jpg">
<script>var x = "<p>not a paragraph, and a < sign</p>"; document.write('<div>ad</div>');</script><style>p { color: red }</style></head>
<body><header><h1>Site name</h1><nav><a href="/">Home</a></nav></header>
<div class="ad-slot"><p>Buy our amazing product now and save a lot of money today, only here, only now!</p></div>
<article><h1>The story</h1>
<p>$STORY</p><img src="/img/photo.jpg" alt="x"><img src="https://t.example.com/pixel.gif" width="1" height="1">
<h2>A heading &amp; more</h2><p>Second paragraph with an entity: caf&eacute; &#x2019;quoted&#x2019; &#8217;.</p>
<ul><li>First point</li></ul>
<iframe src="https://ads.example.com/x"></iframe><form><input name="q"><p>Subscribe to the form text that must never be shown to a reader at all, ever.</p></form>
<div class="related-posts"><p>Related story that is long enough to look like a paragraph of the article itself.</p></div>
<p>Last one.</p></article>
<aside><p>A sidebar paragraph that is also long enough to be mistaken for the real article by a naive reader.</p></aside>
<footer><p>Copyright and a long footer text, all rights reserved, privacy, cookies, terms and conditions.</p></footer>
</body></html>"""

class ReadableTextTest {
    private fun texts(blocks: List<ReadBlock>?) = blocks.orEmpty().mapNotNull { (it as? ReadBlock.Para)?.text ?: (it as? ReadBlock.Head)?.text }

    @Test
    fun onlyTheStoryRemainsWithoutAdsScriptsFramesFormsAndMenus() {
        val blocks = ReadableText.extract(PAGE, "https://news.example.com/a/b")!!
        val all = texts(blocks).joinToString("\n")
        assertTrue(all.contains("The council met on Tuesday"))
        assertTrue(all.contains("A heading & more"))
        assertTrue(all.contains("café ’quoted’ ’."))
        assertTrue(all.contains("• First point"))
        assertTrue(all.contains("Last one."))
        listOf("amazing product", "not a paragraph", "document.write", "Subscribe", "Related story", "sidebar paragraph", "Copyright", "Home", "Site name", "The story").forEach {
            assertFalse("$it must not be shown", all.contains(it))
        }
    }

    @Test
    fun theMainPicturesComeFirstAndTrackersAndOtherSchemesAreDropped() {
        val pics = ReadableText.extract(PAGE, "https://news.example.com/a/b")!!.filterIsInstance<ReadBlock.Pic>().map { it.url }
        assertEquals(listOf("https://cdn.example.com/lead.jpg", "https://news.example.com/img/photo.jpg"), pics)
        val risky = "<article><p>$STORY</p><img src=\"http://e.com/a.jpg\"><img src=\"data:image/png;base64,AAAA\"><img src=\"javascript:alert(1)\"></article>"
        assertTrue(ReadableText.extract(risky, "https://e.com/")!!.none { it is ReadBlock.Pic })
    }

    @Test
    fun aPageWithoutEnoughTextIsNotAnArticle() {
        assertNull(ReadableText.extract("<html><body><div id=\"app\"></div><script>render()</script><p>Please enable JavaScript to read this page.</p></body></html>"))
        assertNull(ReadableText.extract(""))
    }

    @Test
    fun aMisleadingClassNameDoesNotThrowTheWholePageAway() {
        val html = "<html><body><div class=\"layout with-sidebar\"><p>$STORY</p><p>$LONG</p></div></body></html>"
        assertTrue(texts(ReadableText.extract(html)).any { it.contains("council met") })
    }

    @Test
    fun withoutAnArticleTagShortLinesAreLeftOut() {
        val html = "<body><p>Share</p><p>Menu</p><p>$STORY</p></body>"
        assertEquals(1, texts(ReadableText.extract(html)).size)
    }

    @Test
    fun brokenHtmlDoesNotCrashOrHang() {
        val junk = "<p>" + "<div class=\"a\" ".repeat(5_000) + "<!-- never closed " + "<script>" + "x".repeat(10_000)
        ReadableText.extract(junk)
        ReadableText.extract("<article><p>$STORY<p><b>$STORY</article></div></p>")!!
        assertEquals("a & b < c", ReadableText.decode("a &amp; b &lt; c"))
        assertEquals("&bogus; &#99999999;", ReadableText.decode("&bogus; &#99999999;"))
    }

    @Test
    fun keptTextComesBackAndTooMuchIsNotKept() {
        val blocks = listOf(ReadBlock.Head("H"), ReadBlock.Para(STORY), ReadBlock.Pic("https://e.com/a.jpg"))
        val back = ReadableText.decodeStored(ReadableText.encode(blocks))
        assertEquals(listOf("H", STORY), texts(back))
        assertEquals("https://e.com/a.jpg", (back.last() as ReadBlock.Pic).url)
        assertEquals("", ReadableText.encode(listOf(ReadBlock.Para("x".repeat(13_000)))))
        assertTrue(ReadableText.decodeStored("P\tok\nX\tbad\nI\thttp://insecure.example.com/a.jpg").size == 1)
    }

    @Test
    fun aFeedKeepsOnlyItsFullTextNotItsSummary() {
        val rss = """<?xml version="1.0"?><rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/"><channel><title>F</title>
            <item><title>Full</title><link>https://e.com/1</link><description>Short summary</description><content:encoded><![CDATA[<p>$STORY</p>]]></content:encoded></item>
            <item><title>Summary</title><link>https://e.com/2</link><description><![CDATA[<p>$STORY</p>]]></description></item></channel></rss>"""
        val items = Feeds.parse(rss, withBody = true)!!.items
        assertTrue(items[0].body.contains("council met"))
        assertEquals("", items[1].body)
        assertEquals("", Feeds.parse(rss)!!.items[0].body)
        val back = NewsCodec.decode(NewsCodec.encode(NewsData("F", items, 1L, "k")))!!
        assertEquals(items[0].body, back.items[0].body)
    }
}

private class PageConnection(url: URL, private val code: Int, private val body: String) : HttpURLConnection(url) {
    override fun connect() {}
    override fun disconnect() {}
    override fun usingProxy() = false
    override fun getResponseCode() = code
    override fun getHeaderField(name: String?): String? = null
    override fun getInputStream() = body.toByteArray().inputStream()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NewsReadModeHubTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private fun hub(code: Int = 200, body: String = PAGE, seen: MutableList<URL>? = null) = NewsHub(context) { url -> seen?.add(url); PageConnection(url, code, body) }
    private val story = NewsItem("The story", "ANSA", "https://news.example.com/a/b", 0L)

    @Test
    fun theSizeIsKeptAndStepsStopAtTheEnds() {
        val h = hub()
        assertEquals(100, h.options.readPercent)
        h.options = h.options.copy(readPercent = h.options.readStep(1))
        assertEquals(110, hub().options.readPercent)
        assertEquals(80, NewsOpts(readPercent = 80).readStep(-1))
        assertEquals(200, NewsOpts(readPercent = 200).readStep(1))
        context.getSharedPreferences("metrowin_news", android.content.Context.MODE_PRIVATE).edit().putInt("read_percent", 77).commit()
        assertEquals(100, hub().options.readPercent)
    }

    @Test
    fun theSwitchIsOnAndTheSettingsPageAndTheMenuShareIt() {
        assertTrue(hub().options.readMode)
        val option = MiniOptions.byId.getValue("mini.news.read_mode")
        option.write(context, false)
        assertFalse(hub().options.readMode)
        assertFalse(option.read(context))
        hub().let { it.options = it.options.copy(readMode = true) }
        assertTrue(option.read(context))
    }

    @Test
    fun theArticleIsReadFromThePageOnlyWhenTheFeedHadNone() {
        val seen = ArrayList<URL>()
        val ok = hub(seen = seen).article(story) as ReadResult.Ok
        assertEquals(listOf("https://news.example.com/a/b"), seen.map { it.toString() })
        assertTrue(ok.blocks.any { it is ReadBlock.Para && it.text.contains("council met") })
        val kept = story.copy(body = ReadableText.encode(listOf(ReadBlock.Para(STORY))))
        val offline = ArrayList<URL>()
        assertNotNull(hub(code = 500, seen = offline).article(kept) as? ReadResult.Ok)
        assertTrue("a kept article asks nothing", offline.isEmpty())
    }

    @Test
    fun anHttpAddressIsAskedOverHttpsAndThePlacesOfTheLocalNetworkAreNotAsked() {
        val seen = ArrayList<URL>()
        hub(seen = seen).article(story.copy(link = "http://news.example.com/x"))
        assertEquals("https://news.example.com/x", seen.single().toString())
        val none = ArrayList<URL>()
        listOf("https://192.168.1.1/a", "https://localhost/a", "https://printer.local/a", "https://nas/a").forEach {
            assertTrue(hub(seen = none).article(story.copy(link = it)) is ReadResult.Empty)
        }
        assertTrue(none.isEmpty())
    }

    @Test
    fun errorsAndPagesWithoutAnArticleAreToldApart() {
        val failed = hub(code = 503).article(story) as ReadResult.Failed
        assertEquals(NewsFailure.Http, failed.why)
        assertEquals(503, failed.code)
        assertTrue(NewsHub(context) { throw java.io.IOException("down") }.article(story).let { it is ReadResult.Failed && it.why == NewsFailure.Offline })
        assertTrue(hub(body = "<html><body><p>Enable JavaScript.</p></body></html>").article(story) is ReadResult.Empty)
        assertTrue(hub(code = 302).article(story) is ReadResult.Empty)
    }
}
