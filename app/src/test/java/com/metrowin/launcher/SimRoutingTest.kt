package com.metrowin.launcher

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.SimChoice
import com.metrowin.launcher.data.SimInfo
import com.metrowin.launcher.data.SimPrefs
import com.metrowin.launcher.data.SimRouting
import com.metrowin.launcher.data.SimUse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Which SIM places a call or sends a text: the one of the contact, the default, or a question. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SimRoutingTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val one = SimInfo(0, 11, "Alpha", null)
    private val two = SimInfo(1, 12, "Beta", null)

    @Test
    fun withOneSimOrNoneAndroidDecides() {
        assertEquals(SimChoice.System, SimRouting.choose(emptyList(), 0, 0))
        assertEquals(SimChoice.System, SimRouting.choose(listOf(one), 0, null))
    }

    @Test
    fun withTwoSimsAndNothingSaidTheUserIsAsked() {
        assertEquals(SimChoice.Ask, SimRouting.choose(listOf(one, two), null, null))
    }

    @Test
    fun theSimOfTheContactComesBeforeTheDefault() {
        assertEquals(SimChoice.Use(two), SimRouting.choose(listOf(one, two), contactSlot = 1, defaultSlot = 0))
    }

    @Test
    fun theDefaultIsUsedWhenTheContactHasNone() {
        assertEquals(SimChoice.Use(one), SimRouting.choose(listOf(one, two), contactSlot = null, defaultSlot = 0))
    }

    @Test
    fun aSimThatIsNotThereAnymoreFallsBackThenAsks() {
        assertEquals(SimChoice.Use(one), SimRouting.choose(listOf(one, two), contactSlot = 5, defaultSlot = 0))
        assertEquals(SimChoice.Ask, SimRouting.choose(listOf(one, two), contactSlot = 5, defaultSlot = 7))
    }

    @Test
    fun aNumberIsKeptByItsLastNineDigits() {
        assertEquals("123456789", SimRouting.key("+39 123456789"))
        assertEquals(SimRouting.key("+39 333 123 4567"), SimRouting.key("333-123-4567"))
        assertEquals("", SimRouting.key("abc"))
    }

    @Test
    fun theChoiceOfAContactIsKeptPerUseAndForTheSameNumberWrittenAnyWay() {
        val prefs = SimPrefs(app)
        prefs.setForContact(SimUse.Call, "+39 333 123 4567", 1)
        assertEquals(1, prefs.forContact(SimUse.Call, "3331234567"))
        assertNull(prefs.forContact(SimUse.Sms, "3331234567"))
        prefs.setForContact(SimUse.Sms, "3331234567", 0)
        assertEquals(0, prefs.forContact(SimUse.Sms, "+393331234567"))
        prefs.setForContact(SimUse.Call, "3331234567", null)
        assertNull(prefs.forContact(SimUse.Call, "+39 333 123 4567"))
        assertEquals(0, prefs.forContact(SimUse.Sms, "3331234567"))
    }

    @Test
    fun theDefaultIsKeptPerUse() {
        val prefs = SimPrefs(app)
        assertNull(prefs.default(SimUse.Call))
        prefs.setDefault(SimUse.Call, 1)
        assertEquals(1, prefs.default(SimUse.Call))
        assertNull(prefs.default(SimUse.Sms))
        prefs.setDefault(SimUse.Call, null)
        assertNull(prefs.default(SimUse.Call))
    }

    @Test
    fun aNumberWithoutDigitsKeepsNothing() {
        val prefs = SimPrefs(app)
        prefs.setForContact(SimUse.Call, "", 1)
        assertNull(prefs.forContact(SimUse.Call, ""))
    }

    @Test
    fun theLabelNamesTheSlotAndTheOperator() {
        assertEquals("SIM 1 · Alpha", one.label)
        assertEquals("SIM 2", SimInfo(1, 12, "", null).label)
    }
}
