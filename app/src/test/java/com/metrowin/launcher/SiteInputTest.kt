package com.metrowin.launcher

import com.metrowin.launcher.data.SiteInput
import android.content.ComponentName
import com.metrowin.launcher.data.WidgetProvider
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

    // A small tile of 79.5 dp with 6 dp between tiles: Small 79.5, Medium 165, Wide 336 x 165, Large 336 x 336.
    private val unit = 79.5f
    private val gap = 6f

    @Test
    fun aWidgetGetsTheSmallestTileItFitsWhole() {
        assertEquals(TileSize.Small, WidgetSizing.tileSizeFor(neededWidthDp = 70, neededHeightDp = 70, unitDp = unit, gapDp = gap))
        assertEquals(TileSize.Medium, WidgetSizing.tileSizeFor(110, 110, unit, gap))
        assertEquals(TileSize.Medium, WidgetSizing.tileSizeFor(160, 40, unit, gap))
        assertEquals(TileSize.Wide, WidgetSizing.tileSizeFor(250, 110, unit, gap))
        assertEquals(TileSize.Wide, WidgetSizing.tileSizeFor(320, 60, unit, gap))
        // Tall as well as wide: only the large tile holds it (it was a wide one, cut at the bottom).
        assertEquals(TileSize.Large, WidgetSizing.tileSizeFor(250, 300, unit, gap))
        assertEquals(TileSize.Large, WidgetSizing.tileSizeFor(500, 500, unit, gap))
    }

    @Test
    fun theTilesOfSixColumnsAreSmallerSoTheWidgetNeedsABiggerSize() {
        val more = 51f
        assertEquals(TileSize.Wide, WidgetSizing.tileSizeFor(200, 100, more, gap))
        assertEquals(TileSize.Medium, WidgetSizing.tileSizeFor(100, 100, more, gap))
    }

    @Test
    fun aWidgetThatCanBeMadeSmallerIsPlacedByItsSmallestSize() {
        assertEquals(110, WidgetSizing.needed(minDp = 250, minResizeDp = 110))
        assertEquals(250, WidgetSizing.needed(minDp = 250, minResizeDp = 0))
        assertEquals(250, WidgetSizing.needed(minDp = 250, minResizeDp = 300))
        val provider = WidgetProvider("w", "a", ComponentName("p", "c"), 250, 250, minResizeWidthDp = 110, minResizeHeightDp = 40, needsSetup = false)
        assertEquals(TileSize.Medium, WidgetSizing.tileSizeFor(provider, unit, gap))
    }

    @Test
    fun aTileTooSmallForAWidgetShrinksItToFit() {
        assertEquals(1f, WidgetSizing.fitScale(165f, 165f, 110f, 110f), 0f)
        assertEquals(0.66f, WidgetSizing.fitScale(165f, 165f, 250f, 250f), 0.01f)
        // The tighter side decides, so nothing is cut at either.
        assertEquals(0.5f, WidgetSizing.fitScale(336f, 165f, 250f, 330f), 0.01f)
        assertEquals(1f, WidgetSizing.fitScale(165f, 165f, 0f, 0f), 0f)
    }
}
