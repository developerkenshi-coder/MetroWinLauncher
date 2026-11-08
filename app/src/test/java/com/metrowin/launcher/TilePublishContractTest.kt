package com.metrowin.launcher

import com.metrowin.launcher.data.ThirdPartyTiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The wire contract of docs/progetti/tile-sdk.md: other apps are built against these names, so changing one is a breaking change
 * that has to be made on purpose (and the document changed with it).
 */
class TilePublishContractTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun methodsAndKeysAreTheOnesOfTheDocument() {
        assertEquals(listOf("publish", "clear"), listOf(ThirdPartyTiles.METHOD_PUBLISH, ThirdPartyTiles.METHOD_CLEAR))
        assertEquals(listOf("title", "text", "count"), listOf(ThirdPartyTiles.KEY_TITLE, ThirdPartyTiles.KEY_TEXT, ThirdPartyTiles.KEY_COUNT))
        assertEquals(listOf("accepted", "reason"), listOf(ThirdPartyTiles.KEY_ACCEPTED, ThirdPartyTiles.KEY_REASON))
    }

    @Test
    fun limitsAreTheOnesOfTheDocument() {
        assertEquals(listOf(64, 60, 160, 99_999), listOf(ThirdPartyTiles.MAX_TILES, ThirdPartyTiles.MAX_TITLE, ThirdPartyTiles.MAX_TEXT, ThirdPartyTiles.MAX_COUNT))
    }

    @Test
    fun theProviderIsExportedBehindThePublishPermission() {
        val provider = Regex("<provider[^>]*TilePublishProvider[^>]*/>", RegexOption.DOT_MATCHES_ALL).find(manifest)?.value
        assertTrue("provider not in the manifest", provider != null)
        assertTrue(provider!!, "android:authorities=\"\${applicationId}.tiles\"" in provider)
        assertTrue(provider, "android:exported=\"true\"" in provider)
        assertTrue(provider, "android:permission=\"\${applicationId}.permission.PUBLISH_TILE\"" in provider)
    }

    @Test
    fun thePermissionIsNormalSoAppsGetItAtInstallWithoutAPrompt() {
        val permission = Regex("<permission[^>]*PUBLISH_TILE[^>]*/>", RegexOption.DOT_MATCHES_ALL).find(manifest)?.value
        assertTrue("permission not in the manifest", permission != null)
        assertTrue(permission!!, "android:protectionLevel=\"normal\"" in permission)
    }
}
