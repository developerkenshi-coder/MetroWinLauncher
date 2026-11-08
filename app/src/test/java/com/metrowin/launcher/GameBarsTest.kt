package com.metrowin.launcher

import android.content.pm.ApplicationInfo
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.SettingsCodec
import com.metrowin.launcher.gestures.GameApps
import com.metrowin.launcher.gestures.GameBars
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

    @Test
    fun theUsersWordComesBeforeThePhones() {
        // Added: a game though the phone does not say so. Skipped: not a game though it does. Neither: the phone decides.
        assertTrue(GameApps.isGame("a", setOf("a"), emptySet()) { false })
        assertFalse(GameApps.isGame("a", emptySet(), setOf("a")) { true })
        assertTrue(GameApps.isGame("a", emptySet(), emptySet()) { true })
        assertFalse(GameApps.isGame("a", emptySet(), emptySet()) { false })
    }

    @Test
    fun theListsSurviveTheBackupAndAreClearedWithTheirPage() {
        val lists = LauncherSettings(gameAdd = setOf("com.x.game"), gameSkip = setOf("com.y.app"))
        assertEquals(lists, SettingsCodec.decode(SettingsCodec.encode(lists), LauncherSettings()))
        val reset = com.metrowin.launcher.data.SectionReset.reset(lists, "Gestures")
        assertTrue(reset.gameAdd.isEmpty() && reset.gameSkip.isEmpty())
    }

    @Test
    fun aGameThatDoesNotSayItIsOneIsKnownByItsManifest() {
        assertTrue(GameApps.isGameKey("com.google.android.gms.games.APP_ID"))
        assertTrue(GameApps.isGameKey("unity.splash-mode"))
        assertTrue(GameApps.isGameKey("com.epicgames.unreal.GameActivity.bUseExternalFilesDir"))
        assertFalse(GameApps.isGameKey("com.google.android.gms.version"))
        assertTrue(GameApps.isLandscapeOnly(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE))
        assertTrue(GameApps.isLandscapeOnly(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE))
        assertFalse(GameApps.isLandscapeOnly(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT))
        // Free to turn (the default of most apps) and "sensor" are not landscape only.
        assertFalse(GameApps.isLandscapeOnly(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED))
        assertFalse(GameApps.isLandscapeOnly(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR))
    }

    @Test
    fun theAssistantsOfTheMakersAndGooglePlayGamesSayAGameIsUp() {
        assertTrue("com.vivo.gamecube" in GameApps.HELPERS)
        assertTrue("com.google.android.play.games" in GameApps.HELPERS)
        assertFalse("com.google.android.gms" in GameApps.HELPERS)
    }

    @Test
    fun theFirstScreenOfAGameEngineIsAGame() {
        assertTrue(GameApps.isGameActivity("com.unity3d.player.UnityPlayerActivity"))
        assertTrue(GameApps.isGameActivity("com.pokemon.pokemontcgl.UnityPlayerActivity"))
        assertTrue(GameApps.isGameActivity("com.epicgames.ue4.GameActivity"))
        assertFalse(GameApps.isGameActivity("com.android.chrome.Main"))
        assertFalse(GameApps.isGameActivity("com.example.MainActivity"))
    }

    @Test
    fun aDialogOrPopupOfAnotherAppDoesNotTakeTheGamesPlace() {
        assertTrue(GameApps.isPopup("android.app.AlertDialog"))
        assertTrue(GameApps.isPopup("androidx.appcompat.app.AlertDialog"))
        assertTrue(GameApps.isPopup("android.widget.PopupWindow\$PopupDecorView"))
        assertFalse(GameApps.isPopup("com.example.MainActivity"))
        assertFalse(GameApps.isPopup("com.unity3d.player.UnityPlayerActivity"))
        assertFalse(GameApps.isPopup(null))
    }

    @Test
    fun oneSwipeCallsBothBarsAndTheStripsWaitWhileTheyAreOnScreen() {
        // A game in front, both bars there: both strips wait (bottom, top).
        assertEquals(true to true, GameBars.strips(active = true, recalled = false, navShown = true, statusShown = true))
        // A bar that is not there has no strip; no game or bars called back: no strips at all.
        assertEquals(true to false, GameBars.strips(true, false, navShown = true, statusShown = false))
        assertEquals(false to false, GameBars.strips(false, false, true, true))
        assertEquals(false to false, GameBars.strips(true, true, true, true))
        // Thin: a swipe from the very edge, and the game keeps the touches near it.
        assertTrue(GameBars.STRIP_DP in 8..12)
    }
}
