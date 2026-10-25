package com.metrowin.launcher

import com.metrowin.launcher.data.Calculator
import com.metrowin.launcher.data.GlobalSearch
import com.metrowin.launcher.data.QuickAnswer
import com.metrowin.launcher.data.QuickAnswerActions
import com.metrowin.launcher.data.QuickAnswers
import com.metrowin.launcher.data.RecentSearches
import com.metrowin.launcher.data.SearchEngine
import com.metrowin.launcher.data.UnitConverter
import com.metrowin.launcher.data.VoiceCommand
import com.metrowin.launcher.data.VoiceCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** What the search box works out by itself, with words and numbers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuickAnswersTest {
    private val us = Locale.US
    private val it = Locale.ITALY

    private fun calc(text: String) = Calculator.evaluate(text)

    // --- the calculator

    @Test
    fun theFourOperationsFollowTheOrderOfOperations() {
        assertEquals(40.0, calc("12*3+4")!!, 1e-9)
        assertEquals(14.0, calc("2+3*4")!!, 1e-9)
        assertEquals(20.0, calc("(2+3)*4")!!, 1e-9)
        assertEquals(2.5, calc("10/4")!!, 1e-9)
        assertEquals(-3.0, calc("2-5")!!, 1e-9)
    }

    @Test
    fun otherWaysOfWritingTheSigns() {
        assertEquals(6.0, calc("2 x 3")!!, 1e-9)
        assertEquals(6.0, calc("2×3")!!, 1e-9)
        assertEquals(5.0, calc("10÷2")!!, 1e-9)
        assertEquals(8.0, calc("2^3")!!, 1e-9)
        assertEquals(512.0, calc("2^3^2")!!, 1e-9)
        assertEquals(4.0, calc("sqrt(16)")!!, 1e-9)
        assertEquals(4.0, calc("radice(16)")!!, 1e-9)
        assertEquals(Math.PI * 2, calc("2*pi")!!, 1e-9)
    }

    @Test
    fun aSignOnTheFirstNumberIsNotACalculationByItself() {
        assertNull(calc("-5"))
        assertNull(calc("42"))
        assertNull(calc("3.14"))
        assertNull(calc("(5)"))
        assertEquals(-2.0, calc("-5+3")!!, 1e-9)
        assertEquals(-4.0, calc("2*-2")!!, 1e-9)
    }

    @Test
    fun percentages() {
        assertEquals(12.0, calc("15% of 80")!!, 1e-9)
        assertEquals(12.0, calc("15% di 80")!!, 1e-9)
        assertEquals(110.0, calc("100+10%")!!, 1e-9)
        assertEquals(90.0, calc("100-10%")!!, 1e-9)
        assertEquals(5.0, calc("50%*10")!!, 1e-9)
    }

    @Test
    fun theDecimalCommaAndThePointAreBothRead() {
        assertEquals(3.0, calc("1,5*2")!!, 1e-9)
        assertEquals(3.0, calc("1.5*2")!!, 1e-9)
        assertEquals(2001.0, calc("1.000,5*2")!!, 1e-9)
        assertEquals(2001.0, calc("1,000.5*2")!!, 1e-9)
    }

    @Test
    fun whatCannotBeWorkedOutGivesNothing() {
        assertNull(calc("1/0"))
        assertNull(calc("sqrt(-4)"))
        assertNull(calc("2+"))
        assertNull(calc("(2+3"))
        assertNull(calc("2+3)"))
        assertNull(calc("hello+world"))
        assertNull(calc("9^9^9"))
        assertNull(calc(""))
        assertNull(calc("2*" + "9".repeat(200)))
    }

    @Test
    fun aDateIsNotASubtraction() {
        assertNull(calc("2024-05-12"))
        assertNull(calc("12/05/2024"))
        assertNull(calc("12.05.2024"))
    }

    @Test
    fun resultsAreWrittenInTheDecimalSeparatorOfTheLanguageWithoutNoise() {
        assertEquals("2,5", Calculator.format(2.5, it))
        assertEquals("2.5", Calculator.format(2.5, us))
        assertEquals("0.3", Calculator.format(0.1 + 0.2, us))
        assertEquals("40", Calculator.format(40.0, us))
        assertEquals("0.3333333333", Calculator.format(1.0 / 3.0, us))
        assertEquals("0", Calculator.format(-0.0, us))
    }

    // --- unit conversions

    private fun convert(text: String, locale: Locale = us) = UnitConverter.convert(text, locale)

    @Test
    fun lengthsMassesAndVolumes() {
        assertEquals("5 km = 3.10686 mi", convert("5 km in miles")!!.text)
        assertEquals("5 km = 3,10686 mi", convert("5 km in miglia", it)!!.text)
        assertEquals("1 mi = 1.60934 km", convert("1 mile to km")!!.text)
        assertEquals("70 kg = 154.324 lb", convert("70 kg to lb")!!.text)
        assertEquals("1 gal = 3.78541 l", convert("1 gallon in litri")!!.text)
        assertEquals("100 cm = 1 m", convert("100 cm = m")!!.text)
        assertEquals("12 in = 30.48 cm", convert("12 pollici in cm")!!.text)
    }

    @Test
    fun temperatures() {
        assertEquals("30 °C = 86 °F", convert("30 c in f")!!.text)
        assertEquals("86 °F = 30 °C", convert("86°F to °C")!!.text)
        assertEquals("0 °C = 273.15 K", convert("0 celsius in kelvin")!!.text)
        assertEquals("-40 °C = -40 °F", convert("-40 c in f")!!.text)
        assertEquals("30 °C = 86 °F", convert("30 gradi celsius in fahrenheit")!!.text)
    }

    @Test
    fun speedDataAndTime() {
        assertEquals("100 km/h = 62.1371 mph", convert("100 km/h in mph")!!.text)
        assertEquals("2 GB = 2000 MB", convert("2 gb in mb")!!.text)
        assertEquals("90 min = 1.5 h", convert("90 minuti in ore")!!.text)
        assertEquals("2 d = 48 h", convert("2 giorni in h")!!.text)
    }

    @Test
    fun withoutSpacesAndWithAnEqualsSign() {
        assertEquals("5 km = 3.10686 mi", convert("5km in mi")!!.text)
        assertEquals("5 km = 3.10686 mi", convert("5km=mi")!!.text)
    }

    @Test
    fun kindsThatDoNotMatchAreNotConverted() {
        assertNull(convert("5 km in kg"))
        assertNull(convert("5 km in km"))
        assertNull(convert("5 apples in pears"))
        assertNull(convert("km in miles"))
        assertNull(convert("5 km in"))
        assertEquals("the result alone is what gets copied", "3.10686 mi", convert("5 km in mi")!!.to)
    }

    // --- numbers, links, email

    @Test
    fun aPhoneNumberOffersToCallAndToWrite() {
        val answers = QuickAnswers.of("+39 333 123 4567")
        assertEquals(listOf(QuickAnswer.Call("+393331234567"), QuickAnswer.Message("+393331234567")), answers)
        assertEquals(listOf(QuickAnswer.Call("0114567890"), QuickAnswer.Message("0114567890")), QuickAnswers.of("011 456 78 90"))
    }

    @Test
    fun shortNumbersAndWordsAreNotPhoneNumbers() {
        assertEquals(emptyList<QuickAnswer>(), QuickAnswers.of("12345"))
        assertEquals(emptyList<QuickAnswer>(), QuickAnswers.of("pizza"))
        assertEquals(emptyList<QuickAnswer>(), QuickAnswers.of(""))
        assertEquals(emptyList<QuickAnswer>(), QuickAnswers.of("   "))
    }

    @Test
    fun anAddressOfAPageOffersToOpenIt() {
        assertEquals(QuickAnswer.Link("https://www.example.com", "www.example.com"), QuickAnswers.link("www.example.com"))
        assertEquals(QuickAnswer.Link("https://example.com/a?b=1", "example.com/a?b=1"), QuickAnswers.link("example.com/a?b=1"))
        assertEquals(QuickAnswer.Link("http://example.org", "http://example.org"), QuickAnswers.link("http://example.org"))
        assertEquals(QuickAnswer.Link("https://ansa.it", "ansa.it"), QuickAnswers.link("ansa.it"))
    }

    @Test
    fun aFileNameOrAnAbbreviationIsNotAnAddress() {
        assertNull(QuickAnswers.link("notes.txt"))
        assertNull(QuickAnswers.link("dr.house"))
        assertNull(QuickAnswers.link("hello world.com"))
        assertNull(QuickAnswers.link("holiday.jpg"))
        assertNotNull(QuickAnswers.link("www.dr.house"))
    }

    @Test
    fun anEmailAddressOffersToWriteToIt() {
        assertEquals(listOf(QuickAnswer.Email("anna.rossi@example.com")), QuickAnswers.of("anna.rossi@example.com"))
        assertNull(QuickAnswers.email("anna@"))
        assertNull(QuickAnswers.email("anna rossi@example.com"))
    }

    // --- timers and alarms

    private fun heard(text: String) = QuickAnswers.timerOrAlarm(VoiceCommands.normalize(text))

    @Test
    fun timersInManyWays() {
        assertEquals(QuickAnswer.Timer(600), heard("timer 10 minuti"))
        assertEquals(QuickAnswer.Timer(600), heard("timer 10 min"))
        assertEquals(QuickAnswer.Timer(600), heard("Imposta un timer di 10 minuti"))
        assertEquals(QuickAnswer.Timer(90), heard("set a timer for 90 seconds"))
        assertEquals(QuickAnswer.Timer(3600), heard("timer 1 ora"))
        assertEquals(QuickAnswer.Timer(3600), heard("metti un timer di un'ora"))
        assertEquals(QuickAnswer.Timer(5400), heard("timer 1h30"))
        assertEquals(QuickAnswer.Timer(5400), heard("timer 1 ora e 30"))
        assertEquals(QuickAnswer.Timer(5400), heard("timer di un ora e 30"))
        assertEquals(QuickAnswer.Timer(5400), heard("timer 1 hour 30 minutes"))
        assertEquals(QuickAnswer.Timer(1800), heard("timer mezz'ora"))
        assertEquals(QuickAnswer.Timer(600), heard("timer 10"))
        assertEquals(QuickAnswer.Timer(45), heard("timer 45 s"))
    }

    @Test
    fun aTimerNeedsALengthAndHasALimit() {
        assertNull(heard("timer"))
        assertNull(heard("timer app"))
        assertNull(heard("timer 0 minuti"))
        assertNull(heard("timer 30 ore"))
        assertEquals(QuickAnswer.Timer(24 * 3600), heard("timer 24 ore"))
    }

    @Test
    fun alarmsInManyWays() {
        assertEquals(QuickAnswer.Alarm(7, 30), heard("sveglia alle 7:30"))
        assertEquals(QuickAnswer.Alarm(7, 30), heard("sveglia alle 7 e 30"))
        assertEquals(QuickAnswer.Alarm(7, 30), heard("svegliami alle 7 e mezza"))
        assertEquals(QuickAnswer.Alarm(7, 15), heard("sveglia alle 7 e un quarto"))
        assertEquals(QuickAnswer.Alarm(6, 50), heard("sveglia alle 7 meno 10"))
        assertEquals(QuickAnswer.Alarm(7, 0), heard("sveglia 7"))
        assertEquals(QuickAnswer.Alarm(7, 0), heard("imposta una sveglia per le 7"))
        assertEquals(QuickAnswer.Alarm(18, 45), heard("alarm for 6:45 pm"))
        assertEquals(QuickAnswer.Alarm(6, 45), heard("set an alarm for 6:45 am"))
        assertEquals(QuickAnswer.Alarm(6, 0), heard("wake me up at 6"))
        assertEquals(QuickAnswer.Alarm(19, 0), heard("sveglia alle 7 di sera"))
        assertEquals(QuickAnswer.Alarm(0, 30), heard("sveglia alle 12 e 30 di notte"))
        assertEquals(QuickAnswer.Alarm(23, 59), heard("sveglia alle 23 59"))
    }

    @Test
    fun anAlarmNeedsATimeOfDay() {
        assertNull(heard("sveglia"))
        assertNull(heard("alarm clock"))
        assertNull(heard("sveglia alle 25"))
        assertNull("there are not 75 minutes in an hour", heard("sveglia alle 7 e 75"))
    }

    @Test
    fun theSearchBoxUnderstandsThemToo() {
        assertEquals(listOf<QuickAnswer>(QuickAnswer.Timer(600)), QuickAnswers.of("timer 10 minuti"))
        assertEquals(listOf<QuickAnswer>(QuickAnswer.Alarm(7, 30)), QuickAnswers.of("sveglia 7:30"))
        assertEquals(listOf<QuickAnswer>(QuickAnswer.Alarm(7, 30)), QuickAnswers.of("alarm 7.30"))
    }

    @Test
    fun theVoiceTakesATimerAndAnAlarmBeforeAnythingElse() {
        val none = emptyList<com.metrowin.launcher.model.AppItem>()
        val label = { _: com.metrowin.launcher.data.SystemSettingDef -> "" }
        assertEquals(VoiceCommand.Clock(QuickAnswer.Timer(300)), VoiceCommands.parse("imposta un timer di 5 minuti", none, label))
        assertEquals(VoiceCommand.Clock(QuickAnswer.Alarm(7, 30)), VoiceCommands.parse("sveglia alle 7 e 30", none, label))
        assertEquals(VoiceCommand.Web("timer app"), VoiceCommands.parse("cerca timer app", none, label))
    }

    // --- the order and the actions

    @Test
    fun aCalculationComesBeforeAConversionAndAnythingElse() {
        assertTrue(QuickAnswers.of("2+2", us).single() is QuickAnswer.Calculation)
        assertEquals("4", (QuickAnswers.of("2+2", us).single() as QuickAnswer.Calculation).result)
        assertEquals("2,5", (QuickAnswers.of("10/4", it).single() as QuickAnswer.Calculation).result)
        assertTrue(QuickAnswers.of("5 km in miles", us).single() is QuickAnswer.Conversion)
    }

    @Test
    fun theAnswersThatCopyHaveTheResultAndTheOthersHaveAnIntent() {
        assertEquals("4", QuickAnswerActions.copyText(QuickAnswer.Calculation("2+2", "4")))
        assertEquals("3.1 mi", QuickAnswerActions.copyText(QuickAnswer.Conversion("5 km = 3.1 mi", "3.1 mi")))
        assertNull(QuickAnswerActions.copyText(QuickAnswer.Call("123456")))
        assertNull(QuickAnswerActions.intent(QuickAnswer.Calculation("2+2", "4")))

        assertEquals("android.intent.action.DIAL", QuickAnswerActions.intent(QuickAnswer.Call("+39333"))!!.action)
        assertEquals("sms", QuickAnswerActions.intent(QuickAnswer.Message("+39333"))!!.data!!.scheme)
        assertEquals("mailto", QuickAnswerActions.intent(QuickAnswer.Email("a@b.it"))!!.data!!.scheme)
        assertEquals("https://example.com", QuickAnswerActions.intent(QuickAnswer.Link("https://example.com", "example.com"))!!.dataString)
        val timer = QuickAnswerActions.intent(QuickAnswer.Timer(90))!!
        assertEquals(android.provider.AlarmClock.ACTION_SET_TIMER, timer.action)
        assertEquals(90, timer.getIntExtra(android.provider.AlarmClock.EXTRA_LENGTH, 0))
        val alarm = QuickAnswerActions.intent(QuickAnswer.Alarm(7, 5))!!
        assertEquals(android.provider.AlarmClock.ACTION_SET_ALARM, alarm.action)
        assertEquals(7, alarm.getIntExtra(android.provider.AlarmClock.EXTRA_HOUR, -1))
        assertEquals(5, alarm.getIntExtra(android.provider.AlarmClock.EXTRA_MINUTES, -1))
    }

    @Test
    fun aClockAndALengthAreSaidPlainly() {
        assertEquals("7:05", QuickAnswerActions.clock(7, 5))
        assertEquals("18:45", QuickAnswerActions.clock(18, 45))
        assertEquals("1 h 30 min", QuickAnswerActions.length(5400))
        assertEquals("45 s", QuickAnswerActions.length(45))
        assertEquals("10 min", QuickAnswerActions.length(600))
    }

    // --- the engine and the recent searches

    @Test
    fun everyEngineMakesItsOwnAddressAndEncodesTheQuery() {
        assertEquals("https://duckduckgo.com/?q=caff%C3%A8+%26+co", SearchEngine.DuckDuckGo.url("  caffè & co "))
        assertEquals("https://www.google.com/search?q=meteo+torino", GlobalSearch.webUrl("meteo torino", SearchEngine.Google))
        assertEquals("https://www.bing.com/search?q=a", SearchEngine.Bing.url("a"))
        assertEquals(SearchEngine.entries.size, SearchEngine.entries.map { it.code }.distinct().size)
        assertEquals(SearchEngine.DuckDuckGo, SearchEngine.fromCode("nope"))
        assertEquals(SearchEngine.Qwant, SearchEngine.fromCode("qwant"))
        SearchEngine.entries.forEach { assertTrue(it.url("x").startsWith("https://")) }
    }

    @Test
    fun theLastSearchesAreKeptMostRecentFirstWithoutRepeats() {
        var recent = emptyList<String>()
        listOf("pizza", "meteo", "Pizza", "caffè").forEach { recent = RecentSearches.record(recent, it) }
        assertEquals(listOf("caffè", "Pizza", "meteo"), recent)
        // The same search with other accents or capitals is the same search: it moves, it is not listed twice.
        assertEquals(listOf("cafe", "Pizza"), RecentSearches.record(listOf("Café", "Pizza"), "cafe"))
        assertEquals(listOf("PIZZA", "caffè", "meteo"), RecentSearches.record(recent, "PIZZA"))
    }

    @Test
    fun onlySixAreKeptAndShortOnesAreIgnored() {
        var recent = emptyList<String>()
        (1..9).forEach { recent = RecentSearches.record(recent, "search $it") }
        assertEquals(6, recent.size)
        assertEquals("search 9", recent.first())
        assertEquals(recent, RecentSearches.record(recent, "a"))
        assertEquals(recent, RecentSearches.record(recent, "   "))
    }

    @Test
    fun theRecentSearchesSurviveSavingAndBadDataGivesNone() {
        val recent = listOf("pizza", "meteo torino")
        assertEquals(recent, RecentSearches.decode(RecentSearches.encode(recent)))
        assertEquals(emptyList<String>(), RecentSearches.decode("nope"))
        assertEquals(emptyList<String>(), RecentSearches.decode(null))
    }
}
