package com.metrowin.launcher

import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.SettingsStore
import com.metrowin.launcher.data.ThirdPartyTiles
import com.metrowin.launcher.data.TileFeed
import com.metrowin.launcher.data.TilePublishProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThirdPartyTilesTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before
    fun clean() = TileFeed.clearAll(context)

    // --- the rules

    @Test
    fun textFromAnAppIsOneCleanLine() {
        assertEquals("two lines here", ThirdPartyTiles.clean("two\nlines\u0000   here  ", 60))
        assertEquals("", ThirdPartyTiles.clean(null, 60))
        assertEquals("a".repeat(10), ThirdPartyTiles.clean("a".repeat(500), 10))
    }

    @Test
    fun theNumberIsKeptInRange() {
        val tiles = ThirdPartyTiles.publish(emptyMap(), "a", "t", "x", -4, 1)
        assertEquals(0, tiles.getValue("a").count)
        assertEquals(ThirdPartyTiles.MAX_COUNT, ThirdPartyTiles.publish(emptyMap(), "a", "t", "x", Int.MAX_VALUE, 1).getValue("a").count)
    }

    @Test
    fun publishingAgainReplacesTheTileOfThatApp() {
        var tiles = ThirdPartyTiles.publish(emptyMap(), "app.a", "Old", "old", 1, 1)
        tiles = ThirdPartyTiles.publish(tiles, "app.a", "New", "new", 2, 2)
        assertEquals(1, tiles.size)
        assertEquals("New", tiles.getValue("app.a").title)
        assertEquals(2L, tiles.getValue("app.a").updatedAt)
    }

    @Test
    fun onlyTheMostRecentTilesAreKept() {
        var tiles = emptyMap<String, com.metrowin.launcher.data.ThirdPartyTile>()
        for (i in 1..ThirdPartyTiles.MAX_TILES + 5) tiles = ThirdPartyTiles.publish(tiles, "app.$i", "t", "x", 0, i.toLong())
        assertEquals(ThirdPartyTiles.MAX_TILES, tiles.size)
        assertFalse("app.1" in tiles)
        assertTrue("app.${ThirdPartyTiles.MAX_TILES + 5}" in tiles)
    }

    @Test
    fun peeksAndCountsFollowWhatWasPublished() {
        var tiles = ThirdPartyTiles.publish(emptyMap(), "a", "Inbox", "3 new", 3, 1)
        tiles = ThirdPartyTiles.publish(tiles, "b", "", "", 7, 2)
        tiles = ThirdPartyTiles.publish(tiles, "c", "Only text", "", 0, 3)
        assertEquals(setOf("a", "c"), ThirdPartyTiles.peeks(tiles).keys)
        assertEquals(mapOf("a" to 3, "b" to 7), ThirdPartyTiles.counts(tiles))
    }

    @Test
    fun theTilesSurviveBeingSaved() {
        val tiles = ThirdPartyTiles.publish(ThirdPartyTiles.publish(emptyMap(), "a", "A", "x", 1, 5), "b", "B", "y", 2, 6)
        assertEquals(tiles, ThirdPartyTiles.decode(ThirdPartyTiles.encode(tiles)))
    }

    @Test
    fun aDamagedSaveGivesNoTilesNotACrash() {
        assertTrue(ThirdPartyTiles.decode("not json").isEmpty())
        assertTrue(ThirdPartyTiles.decode(null).isEmpty())
        assertTrue(ThirdPartyTiles.decode("""[{"title":"no package"}]""").isEmpty())
    }

    // --- the calls

    private fun extras(title: String, text: String, count: Int) = Bundle().apply {
        putString("title", title); putString("text", text); putInt("count", count)
    }

    @Test
    fun anAppPublishesItsOwnTile() {
        val answer = TileFeed.handle(context, enabled = true, caller = "com.example.mail", method = "publish", extras = extras("Inbox", "3 new messages", 3), now = 10)
        assertTrue(answer.getBoolean("accepted"))
        val tile = TileFeed.tiles.value.getValue("com.example.mail")
        assertEquals("Inbox", tile.title)
        assertEquals("3 new messages", tile.text)
        assertEquals(3, tile.count)
    }

    @Test
    fun theTileIsAlwaysTheCallersAndNeverAnotherApps() {
        val spoof = extras("Fake", "Your bank needs you", 1).apply { putString("package", "com.bank.app") }
        TileFeed.handle(context, true, "com.evil.app", "publish", spoof)
        assertEquals(setOf("com.evil.app"), TileFeed.tiles.value.keys)
    }

    @Test
    fun anUnknownCallerIsRefused() {
        assertFalse(TileFeed.handle(context, true, null, "publish", extras("t", "x", 1)).getBoolean("accepted"))
        assertFalse(TileFeed.handle(context, true, "", "publish", extras("t", "x", 1)).getBoolean("accepted"))
        assertTrue(TileFeed.tiles.value.isEmpty())
    }

    @Test
    fun whenTheUserTurnedItOffNothingIsPublishedAndTheAppIsToldWhy() {
        val answer = TileFeed.handle(context, enabled = false, caller = "com.example.mail", method = "publish", extras = extras("t", "x", 1))
        assertFalse(answer.getBoolean("accepted"))
        assertEquals("turned off by the user", answer.getString("reason"))
        assertTrue(TileFeed.tiles.value.isEmpty())
    }

    @Test
    fun anAppCanClearItsTileButOnlyItsOwn() {
        TileFeed.handle(context, true, "app.a", "publish", extras("A", "x", 1))
        TileFeed.handle(context, true, "app.b", "publish", extras("B", "y", 2))
        assertTrue(TileFeed.handle(context, true, "app.a", "clear", null).getBoolean("accepted"))
        assertEquals(setOf("app.b"), TileFeed.tiles.value.keys)
    }

    @Test
    fun anUnknownMethodIsRefused() {
        val answer = TileFeed.handle(context, true, "app.a", "format_disk", null)
        assertFalse(answer.getBoolean("accepted"))
        assertEquals("unknown method", answer.getString("reason"))
    }

    @Test
    fun aMissingExtrasBundleMakesAnEmptyTile() {
        assertTrue(TileFeed.handle(context, true, "app.a", "publish", null).getBoolean("accepted"))
        assertEquals("", TileFeed.tiles.value.getValue("app.a").title)
    }

    // --- the provider

    @Test
    fun theProviderAnswersCallsAndNothingElse() {
        val provider = Robolectric.buildContentProvider(TilePublishProvider::class.java).create("com.metrowin.launcher.tiles").get()
        val uri = android.net.Uri.parse("content://com.metrowin.launcher.tiles")
        assertNull(provider.query(uri, null, null, null, null))
        assertNull(provider.getType(uri))
        assertNull(provider.insert(uri, null))
        assertEquals(0, provider.delete(uri, null, null))
        assertEquals(0, provider.update(uri, null, null, null))
    }

    @Test
    fun theProviderHonoursTheSettingOfTheUser() {
        SettingsStore(context).save(LauncherSettings(thirdPartyTiles = false))
        val provider = Robolectric.buildContentProvider(TilePublishProvider::class.java).create("com.metrowin.launcher.tiles").get()
        // Called from the test, the "caller" is not another app: either way nothing may be published.
        val answer = provider.call("publish", null, extras("t", "x", 1))!!
        assertFalse(answer.getBoolean("accepted"))
        assertTrue(TileFeed.tiles.value.isEmpty())
    }
}
