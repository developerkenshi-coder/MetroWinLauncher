package com.metrowin.launcher

import com.metrowin.launcher.data.BatterySaving
import com.metrowin.launcher.data.GestureAction
import com.metrowin.launcher.data.LaunchAnimation
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.OrientationMode
import com.metrowin.launcher.data.SettingsCodec
import com.metrowin.launcher.data.ThemeMode
import com.metrowin.launcher.data.ThemeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCodecTest {
    private val custom = LauncherSettings(
        themeMode = ThemeMode.Schedule, darkFromHour = 20, darkToHour = 6, accentIndex = 11, autoAccent = true,
        moreTiles = true, backgroundRevision = 3, parallax = false, startPages = 3, showClock = true,
        orientation = OrientationMode.Auto, highRefreshRate = false, launchAnimation = LaunchAnimation.Expand,
        edgePull = false, doubleTapAction = GestureAction.LockScreen, twoFingerDownAction = GestureAction.OpenSettings,
        twoFingerUpAction = GestureAction.Recents,
        tapSound = true, quickActions = listOf("wifi", "camera", "vpn", "quiet"), notificationPeek = false,
        weatherCity = "Torino", weatherLatitude = 45.07, weatherLongitude = 7.69, weatherFahrenheit = true,
        hiddenApps = setOf("app:b", "app:a"), searchWeb = false, iconPack = "com.example.pack",
        showPrivateApps = false, voiceButton = false, voiceOffline = false, reduceMotion = true, highContrast = true,
        lockPhotoRevision = 2, lockShowAlarm = false, lockShowNotifications = false, lockShowBattery = false,
        backupFolder = "content://tree/backup", backupEveryDays = 7, lastBackupAt = 123L,
        syncEnabled = true, syncPassphrase = "secret", deviceId = "phone-1",
        kidsApps = setOf("stock:camera"), kidsMode = true,
        themeStyle = ThemeStyle.Droid, customAccent = 0xFF3366CC.toInt(), dynamicColor = false,
        batterySaving = BatterySaving.Always, oledMode = true,
        liveMessages = false, liveHideText = true, livePictures = false, liveNowPlaying = false, liveProgress = false,
        liveOffApps = setOf("com.whatsapp", "com.reddit.frontpage"),
        voiceCallDirect = true,
        searchEngine = com.metrowin.launcher.data.SearchEngine.Brave, searchAnswers = false, rememberSearches = false,
        showFrequentApps = false, showNewApps = false, showCategories = false,
        appLabels = mapOf("app:a" to "Alfa"), lockedApps = setOf("app:b"),
    )

    @Test
    fun everySettingSurvivesAnEncodeAndDecode() {
        assertEquals(custom, SettingsCodec.decode(SettingsCodec.encode(custom)))
    }

    @Test
    fun theLookIsClassicWithTheWallpaperColorsByDefault() {
        val defaults = LauncherSettings()
        assertEquals(ThemeStyle.Classic, defaults.themeStyle)
        assertEquals(0, defaults.customAccent)
        assertTrue(defaults.dynamicColor)
    }

    @Test
    fun everyLookSurvivesAndAnUnknownOneIsClassic() {
        ThemeStyle.entries.forEach { style ->
            assertEquals(style, SettingsCodec.decode(SettingsCodec.encode(LauncherSettings(themeStyle = style)))!!.themeStyle)
        }
        assertEquals(ThemeStyle.Classic, SettingsCodec.decode("""{"themeStyle":"neon"}""")!!.themeStyle)
    }

    @Test
    fun aCustomColorIsAlwaysOpaque() {
        // A file with no alpha would otherwise paint everything transparent.
        assertEquals(0xFF112233.toInt(), SettingsCodec.decode("""{"customAccent":${0x00112233}}""")!!.customAccent)
        assertEquals(0, SettingsCodec.decode("""{"customAccent":0}""")!!.customAccent)
    }

    @Test
    fun batterySavingIsAutomaticByDefaultAndAnUnknownModeIsToo() {
        assertEquals(BatterySaving.Auto, LauncherSettings().batterySaving)
        assertEquals(BatterySaving.Auto, SettingsCodec.decode("""{"batterySaving":"turbo"}""")!!.batterySaving)
        assertEquals(BatterySaving.Never, SettingsCodec.decode("""{"batterySaving":"never"}""")!!.batterySaving)
    }

    @Test
    fun everyGestureActionHasItsOwnCodeAndComesBackFromIt() {
        assertEquals(GestureAction.entries.size, GestureAction.entries.map { it.code }.toSet().size)
        GestureAction.entries.forEach { assertEquals(it, GestureAction.fromCode(it.code)) }
        assertEquals(GestureAction.Recents, GestureAction.fromCode("recents"))
    }

    @Test
    fun oledIsOffByDefaultAndOnlyOnWhenTheFileSaysSo() {
        assertFalse(LauncherSettings().oledMode)
        assertTrue(SettingsCodec.decode("""{"oledMode":true}""")!!.oledMode)
        assertFalse(SettingsCodec.decode("""{"themeMode":"dark"}""")!!.oledMode)
    }

    @Test
    fun theLiveTilesOfOtherAppsShowEverythingExceptTheTextIsNotHiddenByDefault() {
        val d = LauncherSettings()
        assertFalse("off until asked for: it puts the text of messages on Start", d.notificationPeek)
        assertTrue(d.liveMessages && d.livePictures && d.liveNowPlaying && d.liveProgress)
        assertFalse(d.liveHideText)
        assertEquals(emptySet<String>(), d.liveOffApps)
        assertEquals(setOf("a", "b"), SettingsCodec.decode("""{"liveOffApps":["b","a"]}""")!!.liveOffApps)
    }

    @Test
    fun theDefaultsSurviveToo() {
        assertEquals(LauncherSettings(), SettingsCodec.decode(SettingsCodec.encode(LauncherSettings())))
    }

    @Test
    fun aPortableFileLeavesOutWhatBelongsToThisPhone() {
        val json = SettingsCodec.encode(custom, portable = true)
        for (key in listOf("backupFolder", "syncPassphrase", "deviceId", "lastBackupAt", "weatherLatitude", "weatherLongitude", "backgroundRevision", "lockPhotoRevision")) {
            assertFalse("$key must not travel", json.contains("\"$key\""))
        }
        // Restoring it keeps this phone's own values.
        val restored = SettingsCodec.decode(
            json,
            base = custom.copy(backupFolder = "content://mine", syncPassphrase = "mine", deviceId = "me", backgroundRevision = 7, lockPhotoRevision = 5),
        )!!
        assertEquals("the photos are files of this phone", 7, restored.backgroundRevision)
        assertEquals(5, restored.lockPhotoRevision)
        assertEquals("content://mine", restored.backupFolder)
        assertEquals("mine", restored.syncPassphrase)
        assertEquals("me", restored.deviceId)
        assertEquals(custom.accentIndex, restored.accentIndex)
        assertEquals(custom.hiddenApps, restored.hiddenApps)
    }

    @Test
    fun missingKeysTakeTheirDefaultAndUnknownOnesAreIgnored() {
        val s = SettingsCodec.decode("""{"accentIndex": 7, "somethingFromTheFuture": [1, 2, 3]}""")!!
        assertEquals(7, s.accentIndex)
        assertEquals(LauncherSettings().themeMode, s.themeMode)
        assertEquals(LauncherSettings().startPages, s.startPages)
    }

    @Test
    fun valuesOutOfRangeAreBroughtBack() {
        val s = SettingsCodec.decode("""{"accentIndex": 99, "startPages": 9, "darkFromHour": 31, "backupEveryDays": -4}""")!!
        assertEquals(19, s.accentIndex)
        assertEquals(LauncherSettings.MAX_START_PAGES, s.startPages)
        assertEquals(23, s.darkFromHour)
        assertEquals(0, s.backupEveryDays)
    }

    @Test
    fun unknownCodesFallBackToTheDefaultChoice() {
        val s = SettingsCodec.decode("""{"themeMode": "plaid", "doubleTapAction": "explode", "orientation": "upside-down"}""")!!
        assertEquals(ThemeMode.Dark, s.themeMode)
        assertEquals(GestureAction.None, s.doubleTapAction)
        assertEquals(OrientationMode.Portrait, s.orientation)
    }

    @Test
    fun somethingThatIsNotJsonIsRefused() {
        assertNull(SettingsCodec.decode("not json at all"))
    }

    @Test
    fun quickActionsAreAlwaysFourKnownOnes() {
        val s = SettingsCodec.decode("""{"quickActions": ["wifi", "nonsense"]}""")!!
        assertEquals(4, s.quickActions.size)
        assertEquals("wifi", s.quickActions.first())
        assertEquals(s.quickActions.size, s.quickActions.toSet().size)
    }

    // --- the theme mode

    private fun dark(mode: ThemeMode, system: Boolean = false, hour: Int = 12, from: Int = 19, to: Int = 7) =
        ThemeMode.isDark(mode, system, hour, from, to)

    @Test
    fun fixedModesIgnoreTheClockAndAndroid() {
        assertTrue(dark(ThemeMode.Dark, system = false, hour = 12))
        assertFalse(dark(ThemeMode.Light, system = true, hour = 23))
    }

    @Test
    fun systemModeFollowsAndroid() {
        assertTrue(dark(ThemeMode.System, system = true))
        assertFalse(dark(ThemeMode.System, system = false))
    }

    @Test
    fun theScheduleCanCrossMidnight() {
        // Dark from 19 to 7.
        assertTrue(dark(ThemeMode.Schedule, hour = 19))
        assertTrue(dark(ThemeMode.Schedule, hour = 23))
        assertTrue(dark(ThemeMode.Schedule, hour = 0))
        assertTrue(dark(ThemeMode.Schedule, hour = 6))
        assertFalse(dark(ThemeMode.Schedule, hour = 7))
        assertFalse(dark(ThemeMode.Schedule, hour = 12))
        assertFalse(dark(ThemeMode.Schedule, hour = 18))
    }

    @Test
    fun theScheduleCanAlsoStayWithinTheDay() {
        // Dark from 9 to 17.
        assertFalse(dark(ThemeMode.Schedule, hour = 8, from = 9, to = 17))
        assertTrue(dark(ThemeMode.Schedule, hour = 9, from = 9, to = 17))
        assertTrue(dark(ThemeMode.Schedule, hour = 16, from = 9, to = 17))
        assertFalse(dark(ThemeMode.Schedule, hour = 17, from = 9, to = 17))
    }

    @Test
    fun equalHoursMeanAlwaysDark() {
        assertTrue(dark(ThemeMode.Schedule, hour = 3, from = 8, to = 8))
        assertTrue(dark(ThemeMode.Schedule, hour = 15, from = 8, to = 8))
    }
}
