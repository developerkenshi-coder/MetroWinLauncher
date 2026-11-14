package com.metrowin.launcher

import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.calculator.CalcStore
import com.metrowin.launcher.data.CalcEngine
import com.metrowin.launcher.data.CalcEngine.Angle
import com.metrowin.launcher.data.CalcEngine.Error
import com.metrowin.launcher.data.CalcEngine.Result
import com.metrowin.launcher.data.CalcLimits
import com.metrowin.launcher.data.CalcSession
import com.metrowin.launcher.data.ConvertTable
import com.metrowin.launcher.data.Currencies
import com.metrowin.launcher.data.CurrencyRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CalcMoreTest {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun eval(text: String, angle: Angle = Angle.Degrees) = CalcEngine.evaluate(text, angle)
    private fun shown(text: String, angle: Angle = Angle.Degrees) = CalcEngine.format((eval(text, angle) as Result.Value).number)

    // --- the engine: tables

    @Test
    fun percentTable() {
        mapOf("50%" to "0.5", "100+10%" to "110", "100−10%" to "90", "200×10%" to "20", "100+10%+10%" to "121", "(50)%" to "0.5").forEach { (text, want) ->
            assertEquals(text, want, shown(text))
        }
    }

    @Test
    fun degreesAndRadiansTable() {
        listOf(
            Triple("sin(30)", Angle.Degrees, "0.5"), Triple("cos(60)", Angle.Degrees, "0.5"), Triple("atan(1)", Angle.Degrees, "45"),
            Triple("sin(π÷2)", Angle.Radians, "1"), Triple("atan(1)", Angle.Radians, "0.785398163397448"), Triple("cos(π)", Angle.Radians, "-1"),
        ).forEach { (text, angle, want) -> assertEquals("$text $angle", want, shown(text, angle)) }
    }

    @Test
    fun roundingTable() {
        mapOf("0.1+0.2" to "0.3", "1÷3" to "0.333333333333333", "2÷3" to "0.666666666666667", "1E15+1" to "1000000000000000")
            .forEach { (text, want) -> assertEquals(text, want, shown(text)) }
    }

    @Test
    fun modWorksLikeAnOperatorOfTheKeypad() {
        assertEquals("1", shown("7mod3"))
        assertEquals("2", shown("−7mod3"))
        val s = CalcSession()
        s.digit('7'); s.mod(); s.digit('3')
        assertEquals("7mod3", s.expression)
        assertEquals(1.0, s.preview!!, 0.0)
        s.backspace(); s.backspace()
        assertEquals("one press takes the whole word", "7", s.expression)
        s.mod(); s.operator('+')
        assertEquals("an operator replaces mod", "7+", s.expression)
        val empty = CalcSession(); empty.mod()
        assertEquals("", empty.expression)
    }

    // --- deep nesting

    @Test
    fun nestingStopsAtTheDepthLimitWithoutCrashing() {
        fun nested(n: Int) = "(".repeat(n) + "1" + ")".repeat(n)
        assertEquals(1.0, (eval(nested(CalcLimits.DEPTH)) as Result.Value).number, 0.0)
        assertEquals(Error.Syntax, (eval(nested(CalcLimits.DEPTH + 1)) as Result.Failed).error)
        assertEquals("a tower of powers", Error.Syntax, (eval("2" + "^2".repeat(CalcLimits.DEPTH + 1)) as Result.Failed).error)
        assertEquals("sin of sin of sin…", Error.Syntax, (eval("sin(".repeat(CalcLimits.DEPTH + 1) + "1") as Result.Failed).error)
        assertEquals("too long", Error.Syntax, (eval("1+".repeat(CalcLimits.EXPRESSION) + "1") as Result.Failed).error)
    }

    // --- the session

    @Test
    fun theHistoryKeepsWhatWasTypedAndWhen() {
        var now = 1_000L
        val s = CalcSession(clock = { now })
        s.openBracket(); s.digit('2'); s.operator('+'); s.digit('3')
        s.equals()
        assertEquals("(2+3", s.history[0].expression)
        assertEquals("5", s.history[0].result)
        assertEquals(1_000L, s.history[0].at)
        now = 2_000L
        s.digit('1'); s.operator('+'); s.digit('1'); s.operator('+')
        s.equals()
        assertEquals("a dangling operator is not kept", "1+1", s.history[0].expression)
    }

    @Test
    fun clearingTheHistoryCanBeUndoneAndOneLineCanGo() {
        val s = CalcSession()
        for (d in "123") { s.digit(d); s.equals(); s.clear() }
        assertEquals(3, s.history.size)
        val line = s.history[1]
        s.removeFromHistory(line)
        assertEquals(2, s.history.size)
        val gone = s.clearHistory()
        assertTrue(s.history.isEmpty())
        s.restoreHistory(gone)
        assertEquals(2, s.history.size)
    }

    @Test
    fun theHistoryNeverHoldsMoreThanTheLimit() {
        val s = CalcSession()
        repeat(CalcLimits.HISTORY + 5) { s.digit('1'); s.equals(); s.clear() }
        assertEquals(CalcLimits.HISTORY, s.history.size)
    }

    @Test
    fun pasteTakesTheSignsOfTheKeypadAndRefusesWhatTheEngineDoesNotKnow() {
        assertEquals("1.5+2×3", CalcSession.pasteable("1,5 + 2*3"))
        assertEquals("1000.5−2", CalcSession.pasteable("1,000.5-2"))
        assertNull(CalcSession.pasteable(""))
        assertNull(CalcSession.pasteable("<script>"))
        assertNull(CalcSession.pasteable("1".repeat(CalcLimits.EXPRESSION + 1)))
        val s = CalcSession()
        s.digit('2'); s.operator('+')
        s.paste("3*4")
        assertEquals("2+3×4", s.expression)
        s.equals()
        s.paste("5")
        assertEquals("a result is replaced by what is pasted", "5", s.expression)
    }

    @Test
    fun aHardwareKeyboardTypesLikeTheKeypad() {
        val s = CalcSession()
        "2+3*4=".forEach { assertTrue("$it", s.type(it)) }
        assertEquals("14", s.expression)
        assertTrue(s.finished)
        assertFalse(s.type('q'))
        assertEquals("a letter changes nothing", "14", s.expression)
    }

    // --- the store

    @Test
    fun theStoreKeepsTheExpressionTheHistoryAndTheMemory() {
        val store = CalcStore(app)
        val first = CalcSession(Angle.Radians, 4.5, emptyList(), clock = { 77L })
        first.digit('6'); first.operator('×'); first.digit('7'); first.equals()
        first.operator('+'); first.digit('1')
        store.save(first)
        val second = CalcStore(app).load()
        assertEquals("42+1", second.expression)
        assertFalse(second.finished)
        assertEquals(Angle.Radians, second.angle)
        assertEquals(4.5, second.memory, 0.0)
        assertEquals(CalcSession.Entry("6×7", "42", 77L), second.history.single())
        assertEquals(42.0, second.ans, 0.0)
    }

    @Test
    fun aKeptResultStaysAResult() {
        val store = CalcStore(app)
        val s = CalcSession()
        s.digit('9'); s.equals()
        store.save(s)
        val back = store.load()
        assertTrue(back.finished)
        back.digit('1')
        assertEquals("a digit after a result starts again", "1", back.expression)
    }

    @Test
    fun aDamagedHistoryIsIgnoredNotACrash() {
        app.getSharedPreferences("metrowin_calc", android.content.Context.MODE_PRIVATE).edit().putString("history", "{not json").putString("memory", "x").commit()
        val s = CalcStore(app).load()
        assertTrue(s.history.isEmpty())
        assertEquals(0.0, s.memory, 0.0)
    }

    // --- the converter

    @Test
    fun theAmountFieldKeepsOneSeparatorAndTheSignOnlyAtTheStart() {
        assertEquals("1.5", ConvertTable.cleanAmount("1.5"))
        assertEquals("1.57", ConvertTable.cleanAmount("1.5.7"))
        assertEquals("1,57", ConvertTable.cleanAmount("1,5,7"))
        assertEquals("-5", ConvertTable.cleanAmount("-5"))
        assertEquals("55", ConvertTable.cleanAmount("5-5"))
        assertEquals("12", ConvertTable.cleanAmount("1a2"))
        assertEquals(CalcLimits.AMOUNT_CHARS, ConvertTable.cleanAmount("1".repeat(50)).length)
    }

    @Test
    fun anAmountThatIsNotANumberIsNotZero() {
        assertEquals(1.5, ConvertTable.parseAmount("1,5")!!, 0.0)
        assertEquals(-2.0, ConvertTable.parseAmount("-2")!!, 0.0)
        assertNull(ConvertTable.parseAmount(""))
        assertNull(ConvertTable.parseAmount("-"))
        assertNull(ConvertTable.parseAmount("."))
    }

    @Test
    fun everyUnitIsFoundBySymbol() {
        ConvertTable.units.forEach { (kind, list) ->
            assertEquals(kind.name, list.size, ConvertTable.bySymbol.getValue(kind).size)
            list.forEach { assertTrue(it.symbol, ConvertTable.bySymbol.getValue(kind)[it.symbol] === it) }
        }
    }

    @Test
    fun theRatesCoverEveryCurrencyTheSourceListsExceptNoMoney() {
        val json = """{"base":"EUR","date":"d","rates":{"USD":1.08,"GBP":0.84,"JPY":175.0,"CHF":0.92,"CAD":1.56,"AED":3.9,"XXX":2.0,"ABC":1.0}}"""
        val rates = Currencies.parseRates(json, 1L)!!
        assertNotNull(rates.perEuro["AED"])
        assertNull(rates.perEuro["XXX"])
        assertNull(rates.perEuro["ABC"])
    }

    @Test
    fun theRepositoryKeepsTheLastRatesAndTheTimeOfThem() {
        val repo = CurrencyRepository(app)
        assertNull(repo.cached())
        val json = """{"base":"EUR","date":"2026-10-23","rates":{"USD":1.08,"GBP":0.84,"JPY":175.0,"CHF":0.92,"CAD":1.56}}"""
        app.getSharedPreferences("metrowin", android.content.Context.MODE_PRIVATE).edit().putString("currency_rates", json).putLong("currency_rates_at", 99L).commit()
        assertEquals(99L, repo.cached()!!.fetchedAt)
        app.getSharedPreferences("metrowin", android.content.Context.MODE_PRIVATE).edit().putString("currency_rates", "garbage").commit()
        assertNull(repo.cached())
    }
}

/** Every language of the calculator has every string of the default ones, with the same placeholders. */
class CalcStringsTest {
    private fun read(folder: String): Map<String, String> {
        val file = java.io.File("src/main/res/$folder/strings_calcolatrice.xml")
        val root = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val nodes = root.getElementsByTagName("string")
        return (0 until nodes.length).associate { (nodes.item(it) as org.w3c.dom.Element).let { e -> e.getAttribute("name") to e.textContent } }
    }

    private fun placeholders(text: String) = Regex("%\\d\\$[sd]").findAll(text).map { it.value }.sorted().toList()

    @Test
    fun everyLanguageHasEveryString() {
        val base = read("values")
        assertTrue(base.size > 100)
        for (language in listOf("it", "de", "fr", "es", "pt")) {
            val other = read("values-$language")
            assertEquals(language, base.keys, other.keys)
            base.forEach { (key, text) -> assertEquals("$language $key", placeholders(text), placeholders(other.getValue(key))) }
            other.forEach { (key, text) -> assertTrue("$language $key is empty", text.isNotBlank()) }
        }
    }
}
