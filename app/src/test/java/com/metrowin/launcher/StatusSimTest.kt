package com.metrowin.launcher

import com.metrowin.launcher.data.CellState
import com.metrowin.launcher.data.SecondSim
import com.metrowin.launcher.data.SimReader
import com.metrowin.launcher.ui.settings.SettingsCategory
import com.metrowin.launcher.ui.settings.SettingsNews
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusSimTest {
    private val two = CellState(level = 3, label = "4G", second = SecondSim(1, "3G", slot = 1, roaming = true, data = false), roaming = false, data = true, slot = 0)

    @Test
    fun theChosenSimTakesTheMainPlaceAndTheOtherGoesUnder() {
        val swapped = two.shown(2)
        assertEquals(1, swapped.level); assertEquals("3G", swapped.label); assertTrue(swapped.roaming); assertFalse(swapped.data); assertEquals(1, swapped.slot)
        val under = swapped.second!!
        assertEquals(3, under.level); assertEquals("4G", under.label); assertTrue(under.data); assertEquals(0, under.slot)
        // Twice is back where it was.
        assertEquals(two, swapped.shown(1))
    }

    @Test
    fun noChoiceTheMainChoiceOrOneSimChangeNothing() {
        assertSame(two, two.shown(0))
        assertSame(two, two.shown(1))
        val one = CellState(level = 2, label = "5G")
        assertSame(one, one.shown(2))
    }

    @Test
    fun theLineOfOperators() {
        assertNull(SimReader.operatorLine(emptyList()))
        assertNull(SimReader.operatorLine(listOf(0 to " ")))
        assertEquals("Vodafone", SimReader.operatorLine(listOf(0 to "Vodafone")))
        assertEquals("SIM 1 · A   SIM 2 · B", SimReader.operatorLine(listOf(1 to "B", 0 to "A")))
        assertEquals("SIM 2 · B", SimReader.operatorLine(listOf(0 to "", 1 to "B")))
    }

    @Test
    fun newSettingsAreMarkedForAWeekAfterAnUpdateOnly() {
        val day = 24 * 60 * 60 * 1000L
        val first = 1_000_000L
        assertTrue(SettingsNews.active(first, first + 10 * day, first + 12 * day))
        assertFalse(SettingsNews.active(first, first + 10 * day, first + 18 * day))
        // A fresh install (first install and last update together) marks nothing, nor does a clock that went back.
        assertFalse(SettingsNews.active(first, first + 1_000L, first + 2_000L))
        assertFalse(SettingsNews.active(first, first + 10 * day, first + 9 * day))
    }

    @Test
    fun aCategoryIsMarkedWhenOneOfItsSettingsIs() {
        assertTrue(SettingsNews.marks(SettingsCategory.Bars, setOf("statusbar.mainsim")))
        assertFalse(SettingsNews.marks(SettingsCategory.Look, setOf("statusbar.mainsim")))
        assertFalse(SettingsNews.marks(SettingsCategory.Bars, emptySet()))
    }
}
