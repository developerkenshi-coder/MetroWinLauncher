package com.metrowin.launcher

import com.metrowin.launcher.data.BackupExtras
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LayoutBackup
import com.metrowin.launcher.data.ThemeStyle
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize.Small
import com.metrowin.launcher.ui.settings.RestorePreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestorePreviewTest {
    private fun backup(settings: LauncherSettings = LauncherSettings(), extras: BackupExtras? = null) =
        LayoutBackup(listOf(listOf(Tile("a", Small), Tile("b", Small)), listOf(Tile("c", Small))), emptyList(), settings, extras)

    @Test
    fun tilesAreCountedOverAllThePages() = assertEquals(3, RestorePreview.tiles(backup()))

    @Test
    fun theLookIsTheSameOnlyWhenTheThemeFieldsAgree() {
        assertTrue(RestorePreview.sameLook(backup(), LauncherSettings()))
        assertFalse(RestorePreview.sameLook(backup(LauncherSettings(themeStyle = ThemeStyle.Droid)), LauncherSettings()))
    }

    @Test
    fun aFileWithoutExtrasHasNoDataToShow() {
        assertNull(RestorePreview.data(backup()))
        assertEquals(Triple(0, 0, 0), RestorePreview.data(backup(extras = BackupExtras(emptyList(), emptyList(), emptyList(), null))))
    }
}
