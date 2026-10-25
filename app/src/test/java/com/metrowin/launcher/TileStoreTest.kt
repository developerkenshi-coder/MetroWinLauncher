package com.metrowin.launcher

import com.metrowin.launcher.data.DefaultStart
import com.metrowin.launcher.data.TileCodec
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TileStoreTest {
    private fun app(id: String) = AppItem(id = id, label = id, launch = Launch.Action("test"))

    @Test
    fun codecRoundTrips() {
        val tiles = listOf(
            Tile("stock:phone", TileSize.Medium),
            Tile("app:com.example/.Main", TileSize.Wide),
            Tile("stock:alarms", TileSize.Small),
        )
        assertEquals(tiles, TileCodec.decode(TileCodec.encode(tiles)))
    }

    @Test
    fun codecRejectsGarbage() {
        assertNull(TileCodec.decode("not json"))
        assertNull(TileCodec.decode("""[{"size":"m"}]"""))
    }

    @Test
    fun unknownSizeFallsBackToMedium() {
        assertEquals(TileSize.Medium, TileCodec.decode("""[{"id":"x","size":"zzz"}]""")!!.single().size)
    }

    @Test
    fun defaultStartOnlyPinsAppsThatExist() {
        val tiles = DefaultStart.build(
            listOf("stock:phone", "stock:photos", "stock:alarms", "app:a", "app:b", "app:c", "app:d", "app:e", "app:f")
                .map(::app),
        )
        // Three stock tiles match, three more fill Start up to the minimum so it is never bare.
        assertEquals(
            listOf("stock:phone", "stock:photos", "stock:alarms", "app:a", "app:b", "app:c"),
            tiles.map { it.id },
        )
        assertEquals(TileSize.Wide, tiles.first { it.id == "stock:photos" }.size)
        assertEquals(TileSize.Small, tiles.first { it.id == "stock:alarms" }.size)
    }

    @Test
    fun defaultStartKeepsTheWindowsPhoneOrder() {
        val ids = listOf("stock:camera", "stock:phone", "stock:people", "stock:mail", "stock:store", "stock:maps")
        val tiles = DefaultStart.build(ids.map(::app))
        assertEquals(
            listOf("stock:phone", "stock:people", "stock:mail", "stock:camera", "stock:store", "stock:maps"),
            tiles.map { it.id },
        )
    }
}
