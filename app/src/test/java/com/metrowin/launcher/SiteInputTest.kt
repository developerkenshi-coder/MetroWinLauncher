package com.metrowin.launcher

import com.metrowin.launcher.data.SiteInput
import com.metrowin.launcher.data.WidgetSizing
import com.metrowin.launcher.model.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteInputTest {
    @Test
    fun anAddressWithoutASchemeGetsHttps() {
        assertEquals("https://example.com", SiteInput.normalize("example.com"))
        assertEquals("https://www.example.com/page?q=1", SiteInput.normalize("  www.example.com/page?q=1  "))
        assertEquals("https://sub.example.co.uk", SiteInput.normalize("sub.example.co.uk"))
    }

    @Test
    fun anAddressThatAlreadyHasHttpOrHttpsIsKept() {
        assertEquals("http://example.com", SiteInput.normalize("http://example.com"))
        assertEquals("https://example.com/a", SiteInput.normalize("https://example.com/a"))
        assertEquals("HTTPS://Example.com", SiteInput.normalize("HTTPS://Example.com"))
    }

    @Test
    fun otherSchemesAreRefused() {
        assertNull(SiteInput.normalize("javascript://example.com/%0Aalert(1)"))
        assertNull(SiteInput.normalize("file:///sdcard/secret"))
        assertNull(SiteInput.normalize("intent://example.com#Intent;scheme=app;end"))
        assertNull(SiteInput.normalize("tel://123456"))
        assertNull(SiteInput.normalize("content://contacts/people"))
    }

    @Test
    fun somethingThatIsNotAnAddressIsRefused() {
        assertNull(SiteInput.normalize(""))
        assertNull(SiteInput.normalize("   "))
        assertNull("a single word is a typo", SiteInput.normalize("hello"))
        assertNull("spaces cannot be in an address", SiteInput.normalize("my site.com"))
        assertNull(SiteInput.normalize("https://"))
        assertNull(SiteInput.normalize("http:// example.com"))
    }

    @Test
    fun localhostIsAnAddress() {
        assertEquals("https://localhost:8080", SiteInput.normalize("localhost:8080"))
    }

    @Test
    fun theTitleIsTheSiteNameUntilTheUserNamesIt() {
        assertEquals("example.com", SiteInput.suggestedTitle("https://www.example.com/page"))
        assertEquals("news.example.org", SiteInput.suggestedTitle("http://news.example.org"))
    }

    @Test
    fun aNarrowWidgetGetsAMediumTileAndAWideOneAWideTile() {
        assertEquals(TileSize.Medium, WidgetSizing.tileSizeFor(minWidthDp = 110, minHeightDp = 110))
        assertEquals(TileSize.Medium, WidgetSizing.tileSizeFor(minWidthDp = 190, minHeightDp = 40))
        assertEquals(TileSize.Wide, WidgetSizing.tileSizeFor(minWidthDp = 250, minHeightDp = 110))
        assertEquals(TileSize.Wide, WidgetSizing.tileSizeFor(minWidthDp = 320, minHeightDp = 60))
    }
}
