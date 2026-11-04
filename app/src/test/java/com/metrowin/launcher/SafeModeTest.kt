package com.metrowin.launcher

import android.view.accessibility.AccessibilityEvent
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.SectionReset
import com.metrowin.launcher.data.SettingsCodec
import com.metrowin.launcher.gestures.LockAccessibilityService
import com.metrowin.launcher.gestures.SafeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The launcher takes its windows away while a bank is in front: which apps count, what the user can change, and what the service does with a window that changes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafeModeTest {
    @After
    fun tearDown() {
        LockAccessibilityService.safeSettingsChanged(LauncherSettings(bankSafe = false))
        LockAccessibilityService.safeSettingsChanged(LauncherSettings())
    }

    @Test
    fun banksAreRecognisedByTheirNameAndOtherAppsAreNot() {
        val settings = LauncherSettings()
        listOf("es.bbva.bbvacontigo", "com.santander.app", "it.nexi.wallet", "com.paypal.android.p2pmobile", "com.revolut.revolut", "com.bancosantander.x", "it.bnl.apps.banking")
            .forEach { assertTrue(it, SafeMode.isSensitive(it, settings)) }
        listOf("org.telegram.messenger", "com.reddit.frontpage", "com.google.android.apps.maps", "com.android.chrome")
            .forEach { assertFalse(it, SafeMode.isSensitive(it, settings)) }
    }

    @Test
    fun theUserAddsAnAppAndTakesOutOneThatIsNotABank() {
        val settings = LauncherSettings(bankSafeAdd = setOf("com.example.coupons"), bankSafeSkip = setOf("com.game.piggybank"))
        assertTrue(SafeMode.isSensitive("com.example.coupons", settings))
        assertTrue("it looks like a bank by its name", SafeMode.looksLikeBank("com.game.piggybank"))
        assertFalse("but the user took it out", SafeMode.isSensitive("com.game.piggybank", settings))
    }

    @Test
    fun withTheSwitchOffNothingCounts() {
        val off = LauncherSettings(bankSafe = false, bankSafeAdd = setOf("com.example.coupons"))
        assertFalse(SafeMode.isSensitive("es.bbva.bbvacontigo", off))
        assertFalse(SafeMode.isSensitive("com.example.coupons", off))
    }

    @Test
    fun theSettingsAreKeptInABackupAndAResetOfGesturesBringsTheDefaults() {
        val custom = LauncherSettings(bankSafe = false, bankSafeAdd = setOf("a.b"), bankSafeSkip = setOf("c.d"))
        val back = SettingsCodec.decode(SettingsCodec.encode(custom))!!
        assertFalse(back.bankSafe)
        assertEquals(setOf("a.b"), back.bankSafeAdd)
        assertEquals(setOf("c.d"), back.bankSafeSkip)
        assertTrue(LauncherSettings().bankSafe)
        assertTrue("the section that holds the accessibility switches resets it", SectionReset.canReset("Gestures"))
    }

    private fun windowOf(service: LockAccessibilityService, pkg: String) =
        service.onAccessibilityEvent(AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply { packageName = pkg })

    @Test
    fun theWindowsGoWhileABankIsInFrontAndComeBackWhenTheLauncherIs() {
        val service = Robolectric.setupService(LockAccessibilityService::class.java)
        assertFalse(LockAccessibilityService.safe)
        windowOf(service, "org.telegram.messenger")
        assertFalse("a chat is not a bank", LockAccessibilityService.safe)
        windowOf(service, "es.bbva.bbvacontigo")
        assertTrue(LockAccessibilityService.safe)
        windowOf(service, "com.android.systemui")
        assertTrue("the shade of Android over the bank does not end it", LockAccessibilityService.safe)
        windowOf(service, "com.metrowin.launcher")
        assertFalse(LockAccessibilityService.safe)
    }

    @Test
    fun openingABankFromTheLauncherTakesTheWindowsFirstAndTurningTheSwitchOffBringsThemBack() {
        LockAccessibilityService.opening("com.revolut.revolut")
        assertTrue(LockAccessibilityService.safe)
        LockAccessibilityService.safeSettingsChanged(LauncherSettings(bankSafe = false))
        assertFalse(LockAccessibilityService.safe)
        LockAccessibilityService.opening("com.revolut.revolut")
        assertFalse("the switch is off: nothing is taken away", LockAccessibilityService.safe)
    }
}
