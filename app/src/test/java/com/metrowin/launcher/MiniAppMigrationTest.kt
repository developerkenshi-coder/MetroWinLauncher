package com.metrowin.launcher

import com.metrowin.launcher.data.MiniAppMigration
import com.metrowin.launcher.model.Folder
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import org.junit.Assert.assertEquals
import org.junit.Test

class MiniAppMigrationTest {
    private val gmail = "app:com.google.android.gm/.ConversationListActivityGmail"

    @Test
    fun aPinnedMailAppBecomesTheStockTileWithItsSizeAndPlace() {
        val out = MiniAppMigration.apply(listOf(listOf(Tile("stock:maps", TileSize.Wide), Tile(gmail, TileSize.Large))))
        assertEquals(listOf(Tile("stock:maps", TileSize.Wide), Tile("stock:mail", TileSize.Large)), out[0])
    }

    @Test
    fun theStockTileAlreadyPinnedKeepsItAndTheOtherGoes() {
        val out = MiniAppMigration.apply(listOf(listOf(Tile("stock:mail", TileSize.Medium)), listOf(Tile(gmail, TileSize.Wide))))
        assertEquals(listOf(listOf(Tile("stock:mail", TileSize.Medium)), emptyList()), out)
    }

    @Test
    fun appsInsideFoldersAreSwappedAndOtherProfilesAndAppsAreLeftAlone() {
        val folder = Folder("f", "F", TileSize.Medium, listOf(gmail, "app:com.google.android.gm/.X@10", "app:com.whatsapp/.Main"))
        val out = MiniAppMigration.apply(listOf(listOf(folder)))
        assertEquals(listOf("stock:mail", "app:com.google.android.gm/.X@10", "app:com.whatsapp/.Main"), (out[0][0] as Folder).children)
    }

    @Test
    fun runningItTwiceChangesNothingMore() {
        val once = MiniAppMigration.apply(listOf(listOf(Tile("app:com.google.android.dialer/.Main", TileSize.Medium))))
        assertEquals(once, MiniAppMigration.apply(once))
        assertEquals("stock:phone", once[0][0].id)
    }
}
