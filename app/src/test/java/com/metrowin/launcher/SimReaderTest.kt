package com.metrowin.launcher

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.SimChoice
import com.metrowin.launcher.data.SimPrefs
import com.metrowin.launcher.data.SimReader
import com.metrowin.launcher.data.SimRouting
import com.metrowin.launcher.data.SimUse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSubscriptionManager.SubscriptionInfoBuilder

/** A phone with two SIMs, made up: the SIMs are read by slot, matched to the accounts that call, and the choice follows what is kept. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SimReaderTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun sub(id: Int, slot: Int, name: String, icc: String): SubscriptionInfo =
        SubscriptionInfoBuilder.newBuilder().setId(id).setSimSlotIndex(slot).setDisplayName(name).setIccId(icc).buildSubscriptionInfo()

    private fun handle(id: String) = PhoneAccountHandle(ComponentName(app, "com.android.phone.TelephonyConnectionService"), id)

    // The fake Telecom lists only enabled accounts; enabling is a hidden call of the builder.
    private fun account(id: String): PhoneAccount {
        val builder = PhoneAccount.builder(handle(id), "acct $id").setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER or PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
        PhoneAccount.Builder::class.java.getMethod("setIsEnabled", Boolean::class.javaPrimitiveType).invoke(builder, true)
        return builder.build()
    }

    private fun phone(subs: List<SubscriptionInfo>, accountIds: List<String>, permission: Boolean = true) {
        if (permission) shadowOf(app).grantPermissions(Manifest.permission.READ_PHONE_STATE)
        shadowOf(app.getSystemService(SubscriptionManager::class.java)).setActiveSubscriptionInfoList(subs)
        val telecom = app.getSystemService(TelecomManager::class.java)
        accountIds.forEach { telecom.registerPhoneAccount(account(it)) }
    }

    @Test
    fun twoSimsAreReadBySlotWithTheirAccounts() {
        // Given in the "wrong" order on purpose: slot 1 first.
        phone(listOf(sub(12, 1, "Beta", "8900002"), sub(11, 0, "Alpha", "8900001")), listOf("8900001", "8900002"))
        val sims = SimReader.read(app)
        assertEquals(listOf(0, 1), sims.map { it.slot })
        assertEquals(listOf(11, 12), sims.map { it.subId })
        assertEquals(listOf("SIM 1 · Alpha", "SIM 2 · Beta"), sims.map { it.label })
        assertEquals(listOf("8900001", "8900002"), sims.map { it.handle?.id })
    }

    @Test
    fun anAccountNamedByTheSubscriptionIdIsMatchedToo() {
        phone(listOf(sub(11, 0, "Alpha", "8900001"), sub(12, 1, "Beta", "8900002")), listOf("12", "11"))
        val sims = SimReader.read(app)
        assertEquals(listOf("11", "12"), sims.map { it.handle?.id })
    }

    @Test
    fun oneSimOrNoPermissionGivesNoChoice() {
        phone(listOf(sub(11, 0, "Alpha", "8900001")), listOf("8900001"))
        assertTrue(SimReader.read(app).isEmpty())
    }

    @Test
    fun withoutThePhonePermissionNothingIsRead() {
        phone(listOf(sub(11, 0, "Alpha", "8900001"), sub(12, 1, "Beta", "8900002")), listOf("8900001", "8900002"), permission = false)
        assertTrue(SimReader.read(app).isEmpty())
    }

    @Test
    fun theChoiceFollowsTheContactThenTheDefaultThenAsks() {
        phone(listOf(sub(11, 0, "Alpha", "8900001"), sub(12, 1, "Beta", "8900002")), listOf("8900001", "8900002"))
        val sims = SimReader.read(app)
        val prefs = SimPrefs(app)
        val number = "+39 333 123 4567"
        fun choose(use: SimUse) = SimRouting.choose(sims, prefs.forContact(use, number), prefs.default(use))

        assertEquals(SimChoice.Ask, choose(SimUse.Call))

        prefs.setDefault(SimUse.Call, 0)
        assertEquals(SimChoice.Use(sims[0]), choose(SimUse.Call))
        // A text is a different use: it still asks.
        assertEquals(SimChoice.Ask, choose(SimUse.Sms))

        prefs.setForContact(SimUse.Call, "3331234567", 1)
        val use = choose(SimUse.Call) as SimChoice.Use
        assertEquals(1, use.sim.slot)
        assertNotNull(use.sim.handle)
        assertEquals("8900002", use.sim.handle?.id)

        prefs.setForContact(SimUse.Sms, "333 123 4567", 1)
        assertEquals(12, (choose(SimUse.Sms) as SimChoice.Use).sim.subId)

        // The SIM of the contact is taken out: back to the default.
        phone(listOf(sub(11, 0, "Alpha", "8900001")), listOf("8900001"))
        assertEquals(SimChoice.System, SimRouting.choose(SimReader.read(app), prefs.forContact(SimUse.Call, number), prefs.default(SimUse.Call)))
    }

    @Test
    fun theCallCarriesTheAccountOfTheSimChosen() {
        phone(listOf(sub(11, 0, "Alpha", "8900001"), sub(12, 1, "Beta", "8900002")), listOf("8900001", "8900002"))
        val sims = SimReader.read(app)
        com.metrowin.launcher.calls.DialerRole.call(app, "3331234567", direct = true, sim = sims[1].handle)
        val intent = shadowOf(app).nextStartedActivity
        @Suppress("DEPRECATION")
        val handle = intent.getParcelableExtra<PhoneAccountHandle>(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE)
        assertEquals("8900002", handle?.id)
    }

    @Test
    fun theTextGoesOutOnTheSubscriptionChosen() {
        phone(listOf(sub(11, 0, "Alpha", "8900001"), sub(12, 1, "Beta", "8900002")), listOf("8900001", "8900002"))
        val sims = SimReader.read(app)
        val sent = com.metrowin.launcher.messages.SmsRepository(app).send("3331234567", "hello", sims[1].subId)
        assertTrue(sent)
        val manager = android.telephony.SmsManager.getSmsManagerForSubscriptionId(sims[1].subId)
        assertEquals("3331234567", shadowOf(manager).lastSentMultipartTextMessageParams?.destinationAddress)
        assertEquals(null, shadowOf(android.telephony.SmsManager.getSmsManagerForSubscriptionId(sims[0].subId)).lastSentMultipartTextMessageParams)
    }
}
