package com.metrowin.launcher

import com.metrowin.launcher.data.Feeds
import com.metrowin.launcher.data.NewsCodec
import com.metrowin.launcher.data.NewsData
import com.metrowin.launcher.data.NewsItem
import com.metrowin.launcher.news.NewsImages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewsImagesTest {
    private fun rss(item: String) = """<?xml version="1.0"?><rss xmlns:media="http://search.yahoo.com/mrss/" version="2.0"><channel><title>Feed</title>$item</channel></rss>"""

    @Test
    fun theMediaContentOfAnRssItemIsItsPicture() {
        val xml = rss("""<item><title>A</title><link>https://example.com/a</link><media:content url="https://cdn.example.com/a.jpg" medium="image"/></item>""")
        assertEquals("https://cdn.example.com/a.jpg", Feeds.parse(xml)!!.items.single().image)
    }

    @Test
    fun aThumbnailAnEnclosureAndAPictureInTheTextAreFoundToo() {
        val thumb = rss("""<item><title>A</title><link>https://example.com/a</link><media:thumbnail url="https://cdn.example.com/t.jpg"/></item>""")
        assertEquals("https://cdn.example.com/t.jpg", Feeds.parse(thumb)!!.items.single().image)
        val enclosure = rss("""<item><title>A</title><link>https://example.com/a</link><enclosure url="https://cdn.example.com/e.png" type="image/png" length="1"/></item>""")
        assertEquals("https://cdn.example.com/e.png", Feeds.parse(enclosure)!!.items.single().image)
        val html = rss("""<item><title>A</title><link>https://example.com/a</link><description><![CDATA[<p><img src="https://cdn.example.com/h.jpg?a=1&amp;b=2" width="10"></p>text]]></description></item>""")
        assertEquals("https://cdn.example.com/h.jpg?a=1&b=2", Feeds.parse(html)!!.items.single().image)
    }

    @Test
    fun anAudioEnclosureIsNotAPictureAndNothingInTheClearOrOddIsFetched() {
        val audio = rss("""<item><title>A</title><link>https://example.com/a</link><enclosure url="https://cdn.example.com/p.mp3" type="audio/mpeg" length="1"/></item>""")
        assertEquals("", Feeds.parse(audio)!!.items.single().image)
        val clear = rss("""<item><title>A</title><link>https://example.com/a</link><media:content url="http://cdn.example.com/a.jpg" medium="image"/></item>""")
        assertEquals("", Feeds.parse(clear)!!.items.single().image)
        assertNull(Feeds.safeImage("https://user:pass@cdn.example.com/a.jpg"))
        assertNull(Feeds.safeImage("data:image/png;base64,AAAA"))
        assertNull(Feeds.safeImage("https://" + "a".repeat(1_100)))
        assertNull(Feeds.safeImage(null))
    }

    @Test
    fun anAtomEntryHasItsPictureToo() {
        val atom = """<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom" xmlns:media="http://search.yahoo.com/mrss/"><title>F</title>
            <entry><title>A</title><link href="https://example.com/a"/><media:thumbnail url="https://cdn.example.com/a.jpg"/></entry>
            <entry><title>B</title><link href="https://example.com/b"/><link rel="enclosure" type="image/jpeg" href="https://cdn.example.com/b.jpg"/></entry></feed>"""
        assertEquals(listOf("https://cdn.example.com/a.jpg", "https://cdn.example.com/b.jpg"), Feeds.parse(atom)!!.items.map { it.image })
    }

    @Test
    fun theKeptCopyKeepsThePictureAndDropsABadOne() {
        val data = NewsData("t", listOf(NewsItem("a", "s", "https://example.com/a", 5, "https://cdn.example.com/a.jpg"), NewsItem("b", "s", "https://example.com/b", 6)), 9, "k")
        assertEquals(data.items, NewsCodec.decode(NewsCodec.encode(data))!!.items)
        val old = """{"title":"t","fetchedAt":1,"key":"k","items":[{"t":"x","s":"","l":"https://example.com/x","p":0,"i":"http://insecure/x.jpg"}]}"""
        assertEquals("", NewsCodec.decode(old)!!.items.single().image)
        // A copy written before the pictures had a field for them still reads.
        val before = """{"title":"t","fetchedAt":1,"key":"k","items":[{"t":"x","s":"","l":"https://example.com/x","p":0}]}"""
        assertEquals("", NewsCodec.decode(before)!!.items.single().image)
    }

    @Test
    fun aPictureIsShrunkOnlyAsFarAsItStaysAsWideAsItIsShownAt() {
        assertEquals(1, NewsImages.sampleSize(width = 1000, maxWidth = 1080))
        assertEquals(2, NewsImages.sampleSize(width = 2200, maxWidth = 1080))
        assertEquals(8, NewsImages.sampleSize(width = 4000, maxWidth = 300))
        assertEquals(40, NewsImages.key("https://cdn.example.com/a.jpg").length)
        assertNotEquals(NewsImages.key("https://cdn.example.com/a.jpg"), NewsImages.key("https://cdn.example.com/b.jpg"))
    }
}
