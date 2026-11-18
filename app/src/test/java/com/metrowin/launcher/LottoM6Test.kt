package com.metrowin.launcher

import com.metrowin.launcher.calendar.DayRange
import com.metrowin.launcher.data.BatterySaving
import com.metrowin.launcher.data.PowerPolicy
import com.metrowin.launcher.mail.MailLink
import com.metrowin.launcher.ui.components.SwipeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LottoM6Test {
    @Test fun ultraSavingSavesWhateverTheCharge() {
        assertTrue(PowerPolicy.saving(BatterySaving.Ultra, androidBatterySaver = false, battery = null))
        assertEquals(BatterySaving.Ultra, BatterySaving.fromCode("ultra"))
    }

    @Test fun swipeNeedsDistanceOrSpeedAndASideThatDoesSomething() {
        assertEquals(1, SwipeRule.decide(400f, 0f, 1000f, canRight = true, canLeft = true))
        assertEquals(-1, SwipeRule.decide(-400f, 0f, 1000f, canRight = true, canLeft = true))
        assertEquals(0, SwipeRule.decide(100f, 0f, 1000f, canRight = true, canLeft = true))
        assertEquals(1, SwipeRule.decide(100f, 2000f, 1000f, canRight = true, canLeft = true))
        assertEquals(0, SwipeRule.decide(400f, 0f, 1000f, canRight = false, canLeft = true))
        // A fast fling the other way from where the row is does nothing.
        assertEquals(0, SwipeRule.decide(-50f, 2000f, 1000f, canRight = true, canLeft = true))
    }

    @Test fun drawnSpanIsOrderedAtLeastOneStepAndInsideTheDay() {
        assertEquals(60 to 150, DayRange.span(150, 60, 15))
        assertEquals(60 to 75, DayRange.span(60, 60, 15))
        assertEquals(1425 to 1440, DayRange.span(1440, 1440, 15))
        assertEquals(60, DayRange.snap(62f, 15) - 0)
        assertEquals(1440, DayRange.snap(5000f, 15))
    }

    @Test fun mailLinkNeedsAnAccount() {
        assertNull(MailLink.of(null))
        assertFalse(MailLink("a", null).uid != null)
    }
}
