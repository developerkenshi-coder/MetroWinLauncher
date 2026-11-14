package com.metrowin.launcher

import com.metrowin.launcher.data.CalcEngine
import com.metrowin.launcher.data.CalcTools
import com.metrowin.launcher.data.CalcTools.Op
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalcToolsTest {
    @Test
    fun groupsOnlyTheWholePartOfFourDigitsOrMore() {
        assertEquals("1 234 567", CalcTools.group("1234567"))
        assertEquals("999", CalcTools.group("999"))
        assertEquals("1 000+0.12345", CalcTools.group("1000+0.12345"))
        assertEquals("1.5E+20", CalcTools.group("1.5E+20"))
        assertEquals("12 345.6789", CalcTools.group("12345.6789"))
    }

    @Test
    fun programmerReadsAndWritesInEveryBase() {
        assertEquals(255L, CalcTools.parseInt("FF", 16))
        assertEquals(5L, CalcTools.parseInt("101", 2))
        assertEquals(-8L, CalcTools.parseInt("-10", 8))
        assertNull(CalcTools.parseInt("2", 2))
        assertNull(CalcTools.parseInt("", 10))
        assertNull(CalcTools.parseInt("FFFFFFFFFFFFFFFFF", 16))
        assertEquals("FF", CalcTools.formatInt(255, 16))
        assertEquals("-101", CalcTools.formatInt(-5, 2))
    }

    @Test
    fun programmerOperations() {
        assertEquals(4L, CalcTools.apply(12, Op.And, 6))
        assertEquals(14L, CalcTools.apply(12, Op.Or, 6))
        assertEquals(10L, CalcTools.apply(12, Op.Xor, 6))
        assertEquals(48L, CalcTools.apply(12, Op.Shl, 2))
        assertEquals(3L, CalcTools.apply(12, Op.Shr, 2))
        assertEquals(-13L, CalcTools.apply(12, Op.Not, 0))
        assertEquals(4L, CalcTools.apply(13, Op.Div, 3))
        assertNull(CalcTools.apply(1, Op.Div, 0))
        assertNull(CalcTools.apply(1, Op.Shl, 64))
        assertNull(CalcTools.apply(1, Op.Shl, -1))
    }

    @Test
    fun datesDifferenceAndOrder() {
        val gap = CalcTools.between(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 11, 14))
        assertEquals(0, gap.years); assertEquals(9, gap.months); assertEquals(14, gap.days); assertEquals(287L, gap.totalDays)
        val back = CalcTools.between(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 1, 31))
        assertEquals("the same gap, the other way", 9, back.months)
        assertEquals(-287L, back.totalDays)
        assertEquals(LocalDate.of(2026, 2, 28), CalcTools.parseDate(" 2026-02-28 "))
        assertNull(CalcTools.parseDate("2026-02-30"))
        assertNull(CalcTools.parseDate("14/11/2026"))
    }

    @Test
    fun billWithTipAndPeople() {
        val bill = CalcTools.bill(80.0, 10.0, 4)!!
        assertEquals(8.0, bill.tip, 1e-9); assertEquals(88.0, bill.total, 1e-9); assertEquals(22.0, bill.perPerson, 1e-9)
        assertNull(CalcTools.bill(80.0, 10.0, 0))
        assertNull(CalcTools.bill(-1.0, 10.0, 2))
        assertNull(CalcTools.bill(Double.NaN, 10.0, 2))
    }

    @Test
    fun statistics() {
        assertEquals(listOf(1.5, 2.0, 3.0), CalcTools.parseList("1,5 2;3"))
        assertEquals(listOf(1.0, 2.0), CalcTools.parseList("1\n2"))
        assertNull(CalcTools.parseList("1 a"))
        assertNull(CalcTools.parseList("  "))
        val s = CalcTools.stats(listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0))!!
        assertEquals(5.0, s.mean, 1e-9); assertEquals(4.5, s.median, 1e-9); assertEquals(2.0, s.deviationOfPopulation, 1e-9)
        assertEquals(2.138, s.deviationOfSample!!, 1e-3)
        assertNull(CalcTools.stats(listOf(3.0))!!.deviationOfSample)
        assertNull(CalcTools.stats(emptyList()))
    }

    @Test
    fun regressionLine() {
        val line = CalcTools.regression(listOf(1.0, 2.0, 3.0), listOf(3.0, 5.0, 7.0))!!
        assertEquals(2.0, line.slope, 1e-9); assertEquals(1.0, line.intercept, 1e-9); assertEquals(1.0, line.correlation, 1e-9)
        assertNull(CalcTools.regression(listOf(1.0, 1.0), listOf(1.0, 2.0)))
        assertNull(CalcTools.regression(listOf(1.0), listOf(1.0)))
        assertNull(CalcTools.regression(listOf(1.0, 2.0), listOf(1.0)))
    }

    @Test
    fun equations() {
        val two = CalcTools.quadratic(1.0, -3.0, 2.0) as CalcTools.Roots.Real
        assertEquals(2.0, two.x1, 1e-9); assertEquals(1.0, two.x2, 1e-9)
        val complex = CalcTools.quadratic(1.0, 0.0, 1.0) as CalcTools.Roots.Complex
        assertEquals(0.0, complex.re, 1e-9); assertEquals(1.0, complex.im, 1e-9)
        assertNull(CalcTools.quadratic(0.0, 1.0, 1.0))
        val xy = CalcTools.solve(listOf(listOf(2.0, 3.0, 5.0), listOf(1.0, -1.0, 0.0)))!!
        assertEquals(1.0, xy[0], 1e-9); assertEquals(1.0, xy[1], 1e-9)
        assertNull("the same equation twice", CalcTools.solve(listOf(listOf(1.0, 1.0, 2.0), listOf(2.0, 2.0, 4.0))))
        assertNull("not square", CalcTools.solve(listOf(listOf(1.0, 1.0, 2.0))))
        assertNotNull(CalcTools.solve(listOf(listOf(4.0, 8.0))))
    }

    @Test
    fun theGraphBreaksWhereThereIsNoValue() {
        val values = CalcTools.sample("1÷x", -1.0, 1.0, 5, CalcEngine.Angle.Degrees)
        assertEquals(-1.0, values[0], 1e-9)
        assertTrue("1÷0 has no value", values[2].isNaN())
        assertEquals(1.0, values[4], 1e-9)
        assertTrue("x alone is an error without a graph", CalcEngine.evaluate("x") is CalcEngine.Result.Failed)
        assertEquals(9.0, (CalcEngine.evaluate("x^2", x = 3.0) as CalcEngine.Result.Value).number, 1e-9)
    }
}
