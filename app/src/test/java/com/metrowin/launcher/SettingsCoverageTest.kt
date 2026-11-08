package com.metrowin.launcher

import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.SectionReset
import com.metrowin.launcher.data.SettingsCodec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * A new setting is written in four places (the field, [SettingsCodec], [SectionReset], the page). This fails when a field is missing from
 * the saved file, or when a saved key is neither in a resettable page nor listed here as data the user keeps.
 */
class SettingsCoverageTest {
    private val fields = LauncherSettings::class.java.declaredFields
        .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }.map { it.name }.toSet()

    private val saved = JSONObject(SettingsCodec.encode(LauncherSettings())).keys().asSequence().toSet()

    @Test
    fun everyFieldIsSavedUnderItsOwnName() {
        assertEquals("fields the codec does not write", emptySet<String>(), fields - saved - WRITTEN_WHEN_SET)
    }

    @Test
    fun everySavedKeyIsAFieldOrKnown() {
        assertEquals("keys the codec writes that are not fields", emptySet<String>(), saved - fields - NOT_FIELDS)
    }

    @Test
    fun everySavedKeyIsInAResetPageOrKept() {
        val paged = SectionReset.keys.values.flatten().toSet()
        assertEquals("settings no page resets (add to a page, or to KEPT here)", emptySet<String>(), saved - paged - KEPT - NOT_FIELDS)
    }

    @Test
    fun noPageNamesAKeyThatIsNotSaved() {
        assertEquals(emptySet<String>(), SectionReset.keys.values.flatten().toSet() - saved)
    }

    private companion object {
        val NOT_FIELDS = setOf("peekOnByDefault")
        /** Written only once the user has chosen a position. */
        val WRITTEN_WHEN_SET = setOf("weatherLatitude", "weatherLongitude")

        /**
         * Settings no page puts back: data of the user's own (lists, hidden/locked apps, weather cities, layouts), the state of backup and
         * sync, and the ones no page owns yet. shortcut: the last group (tileTransition, lockShowMusic, lockQuickStatus, lockQuickApps,
         * lockDetailApp, showPhoneInfo, notifControl, notifHideCenter, notifHideLock, notifNoBanner, mutedUntil, updateAuto) is kept out of
         * the reset on purpose for now; move each to its page in SectionReset when its page should reset it.
         */
        val KEPT = setOf(
            "showWorkApps", "deviceId", "syncEnabled", "activeLayout", "weatherCities", "lockPhotoRevision", "lockedApps", "dialerSources",
            "financeItems", "financeBase", "kidsApps", "backupFolder", "weatherCity", "homePage", "kidsMode", "backgroundRevision", "syncLastStamp",
            "newsSource", "newsUrl", "hiddenApps", "appLists", "backupEveryDays", "lastBackupAt", "showPrivateApps", "syncLastAt", "syncLastHash",
            "volumeProfiles", "appListLeft", "backupCopies", "startPages", "syncPassphrase",
            "tileTransition", "lockShowMusic", "lockQuickStatus", "lockQuickApps", "lockDetailApp", "showPhoneInfo", "notifControl",
            "notifHideCenter", "notifHideLock", "notifNoBanner", "mutedUntil", "updateAuto",
        )
    }
}
