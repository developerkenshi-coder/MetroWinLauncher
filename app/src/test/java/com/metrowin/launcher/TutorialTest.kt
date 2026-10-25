package com.metrowin.launcher

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.HomeApp
import com.metrowin.launcher.data.LivePermissions
import com.metrowin.launcher.data.Need
import com.metrowin.launcher.data.NeedChecker
import com.metrowin.launcher.data.Tutorial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.regex.Pattern

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TutorialTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    // --- the steps

    @Test
    fun everyStepHasItsOwnIdAndSomethingToSay() {
        val ids = Tutorial.steps.map { it.id }
        assertEquals("ids are unique", ids.size, ids.toSet().size)
        Tutorial.steps.forEach { step ->
            assertTrue("${step.id} has advice", step.tips.isNotEmpty())
            assertEquals("${step.id}: no advice twice", step.tips.size, step.tips.toSet().size)
        }
    }

    @Test
    fun theTourStartsAndEndsWithoutAskingForAnything() {
        assertTrue("welcome asks for nothing", Tutorial.steps.first().needs.isEmpty())
        assertTrue("the last page, about the settings, asks for nothing", Tutorial.steps.last().needs.isEmpty())
    }

    @Test
    fun everyNeedIsAskedForExactlyOnceAndOnThePageOfItsFeature() {
        val asked = Tutorial.needs
        assertEquals("each need once", Need.entries.toSet(), asked.toSet())
        assertEquals(asked.size, asked.toSet().size)
        fun stepOf(need: Need) = Tutorial.steps.first { need in it.needs }.id
        assertEquals("home", stepOf(Need.HomeApp))
        assertEquals("live", stepOf(Need.Photos))
        assertEquals("live", stepOf(Need.Calendar))
        assertEquals("live", stepOf(Need.Contacts))
        assertEquals("live", stepOf(Need.CallLog))
        assertEquals("weather", stepOf(Need.Location))
        assertEquals("apps", stepOf(Need.Microphone))
        assertEquals("action", stepOf(Need.NotificationAccess))
        assertEquals("gestures", stepOf(Need.LockService))
        assertEquals("alive", stepOf(Need.Battery))
    }

    @Test
    fun theHomeAppIsAskedForBeforeAnythingElse() {
        assertEquals(Need.HomeApp, Tutorial.needs.first())
    }

    @Test
    fun runtimePermissionsAreNamedAndTheOthersAreGrantedInTheSettings() {
        val inSettings = setOf(Need.HomeApp, Need.NotificationAccess, Need.LockService, Need.Battery)
        Need.entries.forEach { need ->
            assertEquals("$need", need in inSettings, need.permissions.isEmpty())
        }
        assertTrue(Need.Photos.permissions.contains(LivePermissions.photos))
        assertEquals(listOf(Manifest.permission.RECORD_AUDIO), Need.Microphone.permissions)
        assertEquals(listOf(Manifest.permission.ACCESS_COARSE_LOCATION), Need.Location.permissions)
    }

    @Test
    fun everyPermissionTheTourAsksForIsInTheManifest() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        Need.entries.flatMap { it.permissions }.filterNot { it.endsWith("VISUAL_USER_SELECTED") }.forEach {
            assertTrue("$it is declared", manifest.contains("android:name=\"$it\""))
        }
        // What is asked for in the settings screens needs its own permission or service to be declared.
        assertTrue(manifest.contains("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"))
        assertTrue(manifest.contains("LockAccessibilityService"))
        assertTrue(manifest.contains("NotificationService"))
    }

    // --- what is met

    @Test
    fun withNothingGrantedNothingIsMet() {
        assertTrue(NeedChecker(context).met().none { it != Need.HomeApp })
    }

    @Test
    fun aGrantedPermissionIsMet() {
        shadowOf(context).grantPermissions(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CALENDAR)
        val met = NeedChecker(context).met()
        assertTrue(Need.Microphone in met)
        assertTrue(Need.Calendar in met)
        assertFalse(Need.Contacts in met)
        assertFalse(Need.Location in met)
    }

    @Test
    fun partialPhotoAccessOfAndroid14CountsForPhotos() {
        shadowOf(context).grantPermissions("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
        assertTrue(Need.Photos in NeedChecker(context).met())
    }

    @Test
    fun theBatteryNeedFollowsTheOptimizationList() {
        assertFalse(NeedChecker(context).isMet(Need.Battery))
        shadowOf(context.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(context.packageName, true)
        assertTrue(NeedChecker(context).isMet(Need.Battery))
    }

    @Test
    fun theNotificationNeedFollowsTheListOfListeners() {
        assertFalse(NeedChecker(context).isMet(Need.NotificationAccess))
        Settings.Secure.putString(
            context.contentResolver, "enabled_notification_listeners",
            "${context.packageName}/com.metrowin.launcher.notifications.NotificationService",
        )
        assertTrue(NeedChecker(context).isMet(Need.NotificationAccess))
    }

    @Test
    fun theLockNeedFollowsTheAccessibilityList() {
        assertFalse(NeedChecker(context).isMet(Need.LockService))
        Settings.Secure.putString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "${context.packageName}/com.metrowin.launcher.gestures.LockAccessibilityService",
        )
        assertTrue(NeedChecker(context).isMet(Need.LockService))
    }

    // --- the Home app

    @Test
    fun theHomeRoleDecidesOnAndroid10AndLater() {
        val roles = context.getSystemService(RoleManager::class.java)
        // The role is on offer and not held: not the Home app, whatever else would answer Home.
        shadowOf(roles).addAvailableRole(RoleManager.ROLE_HOME)
        assertFalse(HomeApp.isDefault(context))
        shadowOf(roles).addHeldRole(RoleManager.ROLE_HOME)
        assertTrue(HomeApp.isDefault(context))
    }

    @Test
    @Config(sdk = [28])
    fun beforeAndroid10TheAppThatAnswersHomeDecides() {
        // Here the manifest of the launcher, which answers Home, is what Android would open.
        assertTrue(HomeApp.isDefault(context))
    }

    @Test
    fun theRequestIsTheDialogOfTheRoleOrTheSettingsOfTheHomeApp() {
        assertEquals(Settings.ACTION_HOME_SETTINGS, HomeApp.requestIntent(context).action)
        val roles = context.getSystemService(RoleManager::class.java)
        shadowOf(roles).addAvailableRole(RoleManager.ROLE_HOME)
        assertNotEquals(Settings.ACTION_HOME_SETTINGS, HomeApp.requestIntent(context).action)
    }

    @Test
    fun onlyOurOwnPackageIsOurs() {
        assertTrue(HomeApp.isOurs("com.metrowin.launcher", "com.metrowin.launcher"))
        assertFalse(HomeApp.isOurs("android", "com.metrowin.launcher"))
        assertFalse(HomeApp.isOurs(null, "com.metrowin.launcher"))
    }

    // --- the words

    private fun strings(dir: String): Map<String, String> {
        val text = File("src/main/res/$dir/strings.xml").readText()
        return Regex("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL).findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun placeholders(text: String): List<String> =
        Pattern.compile("%(\\d\\$)?[sd]").matcher(text).let { m -> buildList { while (m.find()) add(m.group().replace(Regex("^%\\d\\$"), "%")) } }.sorted()

    @Test
    fun everyStringIsInBothLanguagesWithTheSamePlaceholders() {
        val english = strings("values")
        val italian = strings("values-it")
        assertEquals("the same names", english.keys, italian.keys)
        english.forEach { (name, text) -> assertEquals("placeholders of $name", placeholders(text), placeholders(italian.getValue(name))) }
    }

    @Test
    fun theTourIsNotLeftInEnglishInItalian() {
        val english = strings("values")
        val italian = strings("values-it")
        // The long sentences of the tour are translated, not copied.
        english.filterKeys { it.startsWith("tut_") && english.getValue(it).length > 40 }.forEach { (name, text) ->
            assertTrue("$name is translated", italian.getValue(name) != text)
        }
    }
}
