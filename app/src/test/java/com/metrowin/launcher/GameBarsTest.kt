package com.metrowin.launcher

import android.content.pm.ApplicationInfo
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.SettingsCodec
import com.metrowin.launcher.gestures.GameApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameBarsTest {
    @Suppress("DEPRECATION")
    @Test
    fun anAppIsAGameByItsCategoryOrByTheFlagGamesCarry() {
        assertTrue(GameApps.isGame(ApplicationInfo.CATEGORY_GAME, 0))
        assertTrue(GameApps.isGame(ApplicationInfo.CATEGORY_UNDEFINED, ApplicationInfo.FLAG_IS_GAME))
        assertFalse(GameApps.isGame(ApplicationInfo.CATEGORY_SOCIAL, 0))
        assertFalse(GameApps.isGame(ApplicationInfo.CATEGORY_UNDEFINED, 0))
    }

    @Test
    fun theSwitchIsOnByDefaultAndSurvivesTheBackup() {
        assertTrue(LauncherSettings().hideBarsInGames)
        val off = LauncherSettings(hideBarsInGames = false)
        assertFalse(SettingsCodec.decode(SettingsCodec.encode(off), LauncherSettings())!!.hideBarsInGames)
        assertEquals(true, SettingsCodec.decode(SettingsCodec.encode(LauncherSettings()), off)!!.hideBarsInGames)
    }
}
