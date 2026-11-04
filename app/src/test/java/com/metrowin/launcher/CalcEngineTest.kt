package com.metrowin.launcher

import com.metrowin.launcher.data.CalcEngine
import com.metrowin.launcher.data.CalcEngine.Angle
import com.metrowin.launcher.data.CalcEngine.Error
import com.metrowin.launcher.data.CalcEngine.Result
import com.metrowin.launcher.data.CalcSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CalcEngineTest {
    private fun eval(text: String, angle: Angle = Angle.Degrees, ans: Double = 0.0) = CalcEngine.evaluate(text, angle, ans)

    private fun number(text: String, angle: Angle = Angle.Degrees): String {
        val result = eval(text, angle)
        assertTrue("$text -> $result", result is Result.Value)
        return CalcEngine.format((result as Result.Value).number)
    }

    private fun error(text: String) = (eval(text) as? Result.Failed)?.error

    @Test
    fun `the four operations follow the order of precedence`() {
        assertEquals("14", number("2+3×4"))
        assertEquals("20", number("(2+3)×4"))
        assertEquals("2.5", number("10÷4"))
        assertEquals("-3", number("2−5"))
        assertEquals("7", number("1+2*3"))
    }

    @Test
    fun `floating point noise is not shown`() {
        assertEquals("0.3", number("0.1+0.2"))
        assertEquals("0", number("0.1+0.2−0.3"))
        assertEquals("0.5", number("sin(30)"))
        assertEquals("0", number("sin(180)"))
        assertEquals("1", number("sin(90)"))
        assertEquals("0", number("cos(90)"))
    }

    @Test
    fun `power is right to left and a minus before it binds weaker`() {
        assertEquals("512", number("2^3^2"))
        assertEquals("-4", number("−2^2"))
        assertEquals("0.125", number("2^−3"))
        assertEquals("8", number("2^3"))
    }

    @Test
    fun `percent is a share of the first number after a plus or a minus`() {
        assertEquals("110", number("100+10%"))
        assertEquals("90", number("100−10%"))
        assertEquals("5", number("50×10%"))
        assertEquals("0.5", number("50%"))
    }

    @Test
    fun `functions work in degrees and in radians`() {
        assertEquals("1", number("tan(45)"))
        assertEquals("90", number("asin(1)"))
        assertEquals("45", number("atan(1)"))
        assertEquals("60", number("acos(0.5)"))
        assertEquals("1", number("sin(π÷2)", Angle.Radians))
        assertEquals("1.5707963267949", number("asin(1)", Angle.Radians).take(15))
        assertEquals("4", number("√(16)"))
        assertEquals("3", number("∛(27)"))
        assertEquals("2", number("log(100)"))
        assertEquals("1", number("ln(e)"))
        assertEquals("5", number("abs(−5)"))
        assertEquals("1", number("exp(0)"))
    }

    @Test
    fun `factorial and constants`() {
        assertEquals("120", number("5!"))
        assertEquals("1", number("0!"))
        assertEquals("3.14159265358979", number("π"))
        assertEquals("6.28318530717959", number("2π"))
        assertEquals("15", number("3(4+1)"))
        assertEquals("1", number("7 mod 3"))
        assertEquals("2", number("−1 mod 3"))
        assertEquals(Result.Value(8.0), eval("Ans×2", ans = 4.0))
    }

    @Test
    fun `a bracket left open is closed`() {
        assertEquals("5", number("(2+3"))
    }

    @Test
    fun `what cannot be worked out says why`() {
        assertEquals(Error.Domain, error("1÷0"))
        assertEquals(Error.Domain, error("√(−1)"))
        assertEquals(Error.Domain, error("ln(0)"))
        assertEquals(Error.Domain, error("asin(2)"))
        assertEquals(Error.Domain, error("tan(90)"))
        assertEquals(Error.Domain, error("tan(270)"))
        assertEquals(Error.Domain, error("(−8)^0.5"))
        assertEquals(Error.Domain, error("(−1)!"))
        assertEquals(Error.Domain, error("1.5!"))
        assertEquals(Error.Overflow, error("171!"))
        assertEquals(Error.Overflow, error("10^400"))
        assertEquals(Error.Syntax, error(""))
        assertEquals(Error.Syntax, error("2+"))
        assertEquals(Error.Syntax, error("2..3"))
        assertEquals(Error.Syntax, error("2)"))
        assertEquals(Error.Syntax, error("hello"))
    }

    @Test
    fun `a result is written with up to fifteen digits and in scientific form when huge or tiny`() {
        assertEquals("1E+20", CalcEngine.format(1e20))
        assertEquals("1.5E-12", CalcEngine.format(1.5e-12))
        assertEquals("-2.5E+30", CalcEngine.format(-2.5e30))
        assertEquals("1000000000000000", CalcEngine.format(1e15))
        assertEquals("0.00000001", CalcEngine.format(1e-8))
        assertEquals("0,5", CalcEngine.format(0.5, Locale.ITALY))
        assertEquals("0", CalcEngine.format(-0.0))
        // A result written that way can be typed back in.
        assertEquals("2E+20", number("1E+20×2"))
        assertEquals("3E-12", number("1.5E−12×2"))
    }

    // --- the keypad

    private fun CalcSession.type(keys: String) {
        for (k in keys) when (k) {
            in '0'..'9' -> digit(k)
            '.' -> point()
            '+', '−', '×', '÷', '^' -> operator(k)
            '=' -> equals()
            '(' -> openBracket()
            ')' -> closeBracket()
            '%', '!' -> postfix(k)
            '±' -> negate()
            '<' -> backspace()
            'C' -> clear()
        }
    }

    private fun session() = CalcSession()

    @Test
    fun `typing and equals`() {
        val s = session()
        s.type("12+30=")
        assertEquals("42", s.expression)
        assertTrue(s.finished)
        assertEquals("12+30", s.history.first().expression)
        assertEquals("42", s.history.first().result)
    }

    @Test
    fun `a digit after a result starts again and an operator goes on`() {
        val s = session()
        s.type("2+2=")
        s.type("5")
        assertEquals("5", s.expression)
        s.type("+1=")
        assertEquals("6", s.expression)
        s.type("×2=")
        assertEquals("12", s.expression)
    }

    @Test
    fun `an operator replaces the one before it and a minus after times is a sign`() {
        val s = session()
        s.type("5+×3")
        assertEquals("5×3", s.expression)
        s.type("×−2")
        assertEquals("5×3×−2", s.expression)
        s.type("=")
        assertEquals("−30", s.expression)
    }

    @Test
    fun `leading zeros and points`() {
        val s = session()
        s.type("007")
        assertEquals("7", s.expression)
        s.type("..5")
        assertEquals("7.5", s.expression)
        s.clear()
        s.type(".5")
        assertEquals("0.5", s.expression)
    }

    @Test
    fun `plus minus flips the number at the end`() {
        val s = session()
        s.type("5±")
        assertEquals("−5", s.expression)
        s.type("±")
        assertEquals("5", s.expression)
        s.type("+3±")
        assertEquals("5+(−3", s.expression)
        s.type("±")
        assertEquals("5+3", s.expression)
        s.type("=")
        assertEquals("8", s.expression)
        s.type("±")
        assertEquals("−8", s.expression)
    }

    @Test
    fun `backspace takes away a whole function`() {
        val s = session()
        s.function("sin")
        s.type("30")
        s.type("<<<")
        assertEquals("", s.expression)
        s.function("asin")
        s.type("<")
        assertEquals("", s.expression)
    }

    @Test
    fun `an error keeps the expression so it can be corrected`() {
        val s = session()
        s.type("1÷0=")
        assertEquals(CalcEngine.Error.Domain, s.failure)
        assertEquals("1÷0", s.expression)
        s.type("<")
        s.type("2=")
        assertNull(s.failure)
        assertEquals("0.5", s.expression)
    }

    @Test
    fun `memory adds, subtracts, recalls and clears`() {
        val s = session()
        s.type("5")
        s.memoryAdd(1)
        s.clear()
        s.type("2")
        s.memoryAdd(1)
        s.clear()
        s.memoryRecall()
        assertEquals("7", s.expression)
        s.memoryAdd(-1)
        assertEquals(0.0, s.memory, 0.0)
        s.memoryClear()
        assertEquals(0.0, s.memory, 0.0)
    }

    @Test
    fun `brackets are only closed when there is one open`() {
        val s = session()
        s.type("2)")
        assertEquals("2", s.expression)
        s.type("×(3+4)")
        assertEquals("2×(3+4)", s.expression)
        s.type("=")
        assertEquals("14", s.expression)
    }

    @Test
    fun `reciprocal and power keys`() {
        val s = session()
        s.type("4")
        s.reciprocal()
        s.type("=")
        assertEquals("0.25", s.expression)
        s.clear()
        s.type("7")
        s.power("2")
        s.type("=")
        assertEquals("49", s.expression)
    }

    @Test
    fun `the history keeps the newest first and at most a hundred`() {
        val s = session()
        repeat(105) { s.clear(); s.type("$it+1=") }
        assertEquals(CalcSession.MAX_HISTORY, s.history.size)
        assertEquals("105", s.history.first().result)
    }

    @Test
    fun `angle can be changed`() {
        val s = session()
        s.toggleAngle()
        s.function("sin")
        s.constant("π")
        s.type("÷2=")
        assertEquals("1", s.expression)
    }
}
