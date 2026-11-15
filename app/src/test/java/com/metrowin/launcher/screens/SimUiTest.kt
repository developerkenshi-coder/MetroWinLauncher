package com.metrowin.launcher.screens

import android.Manifest
import android.content.ComponentName
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.metrowin.launcher.data.SimInfo
import com.metrowin.launcher.data.SimPrefs
import com.metrowin.launcher.data.SimUse
import com.metrowin.launcher.dialer.OptionsDialog
import com.metrowin.launcher.dialer.PhonePrefs
import com.metrowin.launcher.ui.components.SimPickDialog
import com.metrowin.launcher.ui.components.SimSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSubscriptionManager.SubscriptionInfoBuilder

/** The screens of the two-SIM choice: the question, the row that picks one, and the default SIMs in the options of the Phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class SimUiTest : ScreenshotBase() {
    private val one = SimInfo(0, 11, "Alpha", null)
    private val two = SimInfo(1, 12, "Beta", null)

    private fun sub(id: Int, slot: Int, name: String, icc: String): SubscriptionInfo =
        SubscriptionInfoBuilder.newBuilder().setId(id).setSimSlotIndex(slot).setDisplayName(name).setIccId(icc).buildSubscriptionInfo()

    /** The phone as it would be with these SIMs (the fake Telecom lists only enabled accounts; enabling is a hidden call of the builder). */
    private fun phone(vararg subs: SubscriptionInfo) {
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.READ_PHONE_STATE)
        shadowOf(context.getSystemService(SubscriptionManager::class.java)).setActiveSubscriptionInfoList(subs.toList())
        val telecom = context.getSystemService(TelecomManager::class.java)
        subs.forEach { s ->
            val handle = PhoneAccountHandle(ComponentName(context, "com.android.phone.TelephonyConnectionService"), s.iccId)
            val builder = PhoneAccount.builder(handle, "acct").setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER or PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
            PhoneAccount.Builder::class.java.getMethod("setIsEnabled", Boolean::class.javaPrimitiveType).invoke(builder, true)
            telecom.registerPhoneAccount(builder.build())
        }
    }

    @Test
    fun theQuestionListsEachSimAndGivesBackTheOnePickedWithTheSwitch() {
        var picked: Pair<SimInfo, Boolean>? = null
        show { SimPickDialog(SimUse.Call, listOf(one, two), canRemember = true, onPick = { sim, keep -> picked = sim to keep }, onDismiss = {}) }
        advance(400)
        compose.onNodeWithText("Call with which SIM?", ignoreCase = true).assertExists()
        compose.onNodeWithText("SIM 1 · Alpha").assertExists()
        compose.onNodeWithText("SIM 2 · Beta").assertExists()
        compose.onNodeWithTag("sim:remember").performClick()
        advance(200)
        compose.onNodeWithTag("sim:pick:1").performClick()
        advance(400)
        assertEquals(two to true, picked)
    }

    @Test
    fun withoutTheSwitchTheSimIsNotKept() {
        var picked: Pair<SimInfo, Boolean>? = null
        show { SimPickDialog(SimUse.Sms, listOf(one, two), canRemember = true, onPick = { sim, keep -> picked = sim to keep }, onDismiss = {}) }
        advance(400)
        compose.onNodeWithText("Send with which SIM?", ignoreCase = true).assertExists()
        compose.onNodeWithTag("sim:pick:0").performClick()
        advance(400)
        assertEquals(one to false, picked)
    }

    @Test
    fun aNumberWithNoDigitsCannotKeepASim() {
        show { SimPickDialog(SimUse.Call, listOf(one, two), canRemember = false, onPick = { _, _ -> }, onDismiss = {}) }
        advance(400)
        compose.onAllNodesWithTag("sim:remember").assertCountEquals(0)
    }

    @Test
    fun theRowOffersAskingAndEachSim() {
        val chosen = ArrayList<Int?>()
        show { SimSelector("Calls", listOf(one, two), selected = 1, onSelect = { chosen += it }, tag = "t") }
        advance(400)
        compose.onNodeWithTag("t:ask").performClick()
        compose.onNodeWithTag("t:0").performClick()
        compose.onNodeWithTag("t:1").performClick()
        assertEquals(listOf(null, 0, 1), chosen)
    }

    @Test
    fun theOptionsOfThePhoneAskWhichSimByDefaultAndKeepTheDefaultSims() {
        phone(sub(11, 0, "Alpha", "8900001"), sub(12, 1, "Beta", "8900002"))
        assertTrue(PhonePrefs(context).askSim)
        show { OptionsDialog(PhonePrefs(context), onTexts = {}, onDismiss = {}) }
        advance(400)
        scrollIntoView("sim:default:call")
        compose.onNodeWithTag("sim:default:call:1").performClick()
        advance(200)
        compose.onNodeWithTag("sim:default:sms:0").performClick()
        advance(200)
        val prefs = SimPrefs(context)
        assertEquals(1, prefs.default(SimUse.Call))
        assertEquals(0, prefs.default(SimUse.Sms))
        // Back to asking.
        compose.onNodeWithTag("sim:default:call:ask").performClick()
        advance(200)
        assertNull(prefs.default(SimUse.Call))
    }

    @Test
    fun withOneSimTheOptionsHaveNoDefaultSims() {
        phone(sub(11, 0, "Alpha", "8900001"))
        show { OptionsDialog(PhonePrefs(context), onTexts = {}, onDismiss = {}) }
        advance(400)
        compose.onAllNodesWithTag("sim:defaults").assertCountEquals(0)
    }

    @Test
    fun theKeypadHasACallButtonForEachSimAndCallsWithTheOnePressed() {
        phone(sub(11, 0, "Alpha", "8900001"), sub(12, 1, "Beta", "8900002"))
        shadowOf(context as android.app.Application).grantPermissions(
            Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG, Manifest.permission.CALL_PHONE, Manifest.permission.WRITE_CALL_LOG,
        )
        var placed = 0
        show { com.metrowin.launcher.dialer.DialerScreen(number = "3331234567", onNumberChange = {}, onPlaced = { placed++ }, initialPage = com.metrowin.launcher.dialer.PAGE_KEYPAD) }
        advance(600)
        compose.onNodeWithTag("dialer:call:sim0").assertExists()
        compose.onNodeWithTag("dialer:call:sim1").performClick()
        advance(300)
        val intent = shadowOf(context as android.app.Application).nextStartedActivity
        @Suppress("DEPRECATION")
        val handle = intent.getParcelableExtra<PhoneAccountHandle>(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE)
        assertEquals("8900002", handle?.id)
        assertEquals(1, placed)
    }
}
