package com.metrowin.launcher

import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.AppLinks
import com.metrowin.launcher.data.CalendarSearch
import com.metrowin.launcher.data.Currencies
import com.metrowin.launcher.data.CurrencyRepository
import com.metrowin.launcher.data.DateAnswers
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.QuickAnswer
import com.metrowin.launcher.data.QuickAnswerActions
import com.metrowin.launcher.data.QuickAnswers
import com.metrowin.launcher.data.Rolls
import com.metrowin.launcher.data.SafeHttp
import com.metrowin.launcher.data.SettingsCodec
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlin.random.Random

/** Currencies, dice and coins, dates, the time in other cities, the calendar in the search, and the links of the store. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchMoreTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.of(2026, 10, 25)
    private val en = Locale.US
    private val itl = Locale.ITALY

    // --- currencies

    @Test
    fun aConversionIsReadFromCodesNamesAndSymbols() {
        assertEquals(Currencies.Query(100.0, "USD", "EUR"), Currencies.parse("100 usd in eur"))
        assertEquals(Currencies.Query(50.0, "USD", "EUR"), Currencies.parse("50 dollari in euro"))
        assertEquals(Currencies.Query(20.5, "GBP", "EUR"), Currencies.parse("20,5 sterline a euro"))
        assertEquals(Currencies.Query(10.0, "EUR", "JPY"), Currencies.parse("€10 to yen"))
        assertEquals(Currencies.Query(5.0, "USD", "CHF"), Currencies.parse("$5 in franchi svizzeri"))
        assertEquals(Currencies.Query(100.0, "AUD", "EUR"), Currencies.parse("100 australian dollars to euros".replace("euros", "euro")))
        assertEquals("no amount is one", Currencies.Query(1.0, "EUR", "USD"), Currencies.parse("eur to usd"))
        assertEquals(Currencies.Query(100.0, "USD", "EUR"), Currencies.parse("100USD in EUR"))
    }

    @Test
    fun whatIsNotAConversionOfCurrenciesIsLeftAlone() {
        assertNull(Currencies.parse("5 km in miles"))
        assertNull(Currencies.parse("5 pounds in kg"))
        assertNull(Currencies.parse("100 usd in usd"))
        assertNull("a currency the rates do not have", Currencies.parse("100 usd in xyz"))
        assertNull(Currencies.parse("pizza"))
        assertNull(Currencies.parse(""))
        assertNull(Currencies.parse("1000000000000 usd in eur"))
    }

    private val rates = Currencies.Rates("2026-10-23", mapOf("USD" to 1.08, "GBP" to 0.84, "JPY" to 175.0), fetchedAt = 1L)

    @Test
    fun theAmountIsConvertedThroughTheEuro() {
        assertEquals(92.5926, Currencies.convert(Currencies.Query(100.0, "USD", "EUR"), rates)!!, 1e-3)
        assertEquals(108.0, Currencies.convert(Currencies.Query(100.0, "EUR", "USD"), rates)!!, 1e-9)
        assertEquals(100.0 / 1.08 * 0.84, Currencies.convert(Currencies.Query(100.0, "USD", "GBP"), rates)!!, 1e-9)
        assertNull("a currency without a rate", Currencies.convert(Currencies.Query(1.0, "CHF", "EUR"), rates))
    }

    @Test
    fun theAnswerSaysWhatWasAskedAndGivesTheNumberToCopy() {
        val answer = Currencies.answer(Currencies.Query(100.0, "EUR", "USD"), rates, en)!!
        assertEquals("100 EUR = 108.00 USD", answer.text)
        assertEquals("108.00", answer.result)
        assertEquals("2026-10-23", answer.ratesDate)
        assertEquals("92.59", Currencies.answer(Currencies.Query(100.0, "USD", "EUR"), rates, en)!!.result)
        assertEquals("a small amount keeps more digits", "0.0057", Currencies.format(0.0057, en).substring(0, 6))
        assertEquals("92,59", Currencies.answer(Currencies.Query(100.0, "USD", "EUR"), rates, itl)!!.result)
    }

    @Test
    fun theRatesAreReadFromTheAnswerOfTheServerAndNothingElse() {
        val json = """{"amount":1.0,"base":"EUR","date":"2026-10-23","rates":{"AUD":1.59,"BRL":5.6,"CAD":1.56,"CHF":0.92,"USD":1.08,"XXX":2.0}}"""
        val read = Currencies.parseRates(json, 42L)!!
        assertEquals("2026-10-23", read.date)
        assertEquals(42L, read.fetchedAt)
        assertEquals(1.08, read.perEuro["USD"]!!, 0.0)
        assertFalse("only the currencies that are known", "XXX" in read.perEuro)
        assertNull("another base", Currencies.parseRates(json.replace("EUR\"", "USD\""), 1L))
        assertNull("not json", Currencies.parseRates("<html>", 1L))
        assertNull("too few rates", Currencies.parseRates("""{"base":"EUR","date":"x","rates":{"USD":1.0}}""", 1L))
        assertNull("a rate that is not a number", Currencies.parseRates("""{"base":"EUR","date":"x","rates":{"USD":"a","GBP":"b","JPY":"c","CHF":"d","CAD":"e","AUD":"f"}}""", 1L))
    }

    @Test
    fun theRatesAreOnlyAskedFromTheirOwnHostOverHttps() {
        assertNull(SafeHttp.get("http://api.frankfurter.dev/v1/latest", setOf(Currencies.HOST)))
        assertNull(SafeHttp.get("https://example.com/v1/latest", setOf(Currencies.HOST)))
        assertNull(SafeHttp.get("not an address", setOf(Currencies.HOST)))
        assertTrue(Currencies.RATES_URL.startsWith("https://${Currencies.HOST}/"))
    }

    @Test
    fun theRatesKeptFromBeforeAreUsedWithoutAskingAgain() {
        val repo = CurrencyRepository(app)
        assertNull(repo.cached())
        val prefs = app.getSharedPreferences("metrowin", android.content.Context.MODE_PRIVATE)
        val json = """{"base":"EUR","date":"2026-10-23","rates":{"USD":1.08,"GBP":0.84,"JPY":175.0,"CHF":0.92,"CAD":1.56}}"""
        prefs.edit().putString("currency_rates", json).putLong("currency_rates_at", System.currentTimeMillis()).commit()
        assertEquals(1.08, repo.cached()!!.perEuro["USD"]!!, 0.0)

        val vm = LauncherViewModel(app)
        shadowOf(Looper.getMainLooper()).idle()
        val answer = runBlocking { vm.currencyAnswer(Currencies.Query(100.0, "EUR", "USD")) }
        assertNotNull(answer)
        assertEquals("2026-10-23", answer!!.ratesDate)
    }

    // --- dice and coins

    private fun roll(text: String, seed: Int = 1) = Rolls.of(text, Random(seed))

    @Test
    fun aDieIsAskedInWordsAndInNotation() {
        val one = roll("dado") as QuickAnswer.Dice
        assertEquals(1, one.count)
        assertEquals(6, one.sides)
        assertTrue(one.total in 1..6)
        assertEquals(3, (roll("lancia 3 dadi") as QuickAnswer.Dice).count)
        assertEquals(1, (roll("roll a dice") as QuickAnswer.Dice).count)
        val d20 = roll("d20") as QuickAnswer.Dice
        assertEquals(20, d20.sides)
        val three = roll("2d6+3") as QuickAnswer.Dice
        assertEquals(2, three.count)
        assertEquals(3, three.bonus)
        assertEquals(three.rolls.sum() + 3, three.total)
        val minus = roll("3d8 - 2") as QuickAnswer.Dice
        assertEquals(-2, minus.bonus)
        assertEquals(3, minus.rolls.size)
    }

    @Test
    fun theDiceStayInsideTheirFaces() {
        val random = Random(7)
        repeat(300) {
            val dice = Rolls.of("4d10", random) as QuickAnswer.Dice
            assertTrue(dice.rolls.all { it in 1..10 })
        }
        val seen = (1..400).map { (Rolls.of("d6", Random(it)) as QuickAnswer.Dice).total }.toSet()
        assertEquals("every face comes up", (1..6).toSet(), seen)
    }

    @Test
    fun aThrowCanBeRepeatedWithTheSameSeed() {
        assertEquals(roll("2d6", 5), roll("2d6", 5))
    }

    @Test
    fun aCoinIsAskedInEnglishAndItalian() {
        listOf("moneta", "lancia una moneta", "coin", "flip a coin", "testa o croce", "heads or tails", "toss a coin").forEach {
            assertTrue(it, roll(it) is QuickAnswer.Coin)
        }
        val faces = (1..60).map { (Rolls.of("moneta", Random(it)) as QuickAnswer.Coin).heads }.toSet()
        assertEquals("both sides come up", setOf(true, false), faces)
    }

    @Test
    fun wordsThatAreNotAThrowAreNotOne() {
        listOf("dadi di plastica", "0d6", "d1", "d", "21d6", "2d1001", "1d6+1000", "moneta antica", "dado e", "pizza", "").forEach {
            assertNull(it, roll(it))
        }
        assertNull("too many dice", roll("tira 99 dadi"))
    }

    // --- dates

    private fun dates(text: String, locale: Locale = Locale.ITALY) = DateAnswers.of(com.metrowin.launcher.data.VoiceCommands.normalize(text), today, locale)

    @Test
    fun aDateAheadOrBehindIsCountedFromToday() {
        assertEquals(QuickAnswer.DateOffset(today.plusDays(30), 30), dates("tra 30 giorni"))
        assertEquals(QuickAnswer.DateOffset(today.plusWeeks(2), 14), dates("in 2 weeks"))
        assertEquals(QuickAnswer.DateOffset(today.plusMonths(3), java.time.temporal.ChronoUnit.DAYS.between(today, today.plusMonths(3))), dates("fra 3 mesi"))
        assertEquals(QuickAnswer.DateOffset(today.plusYears(1), 365), dates("tra 1 anno"))
        assertEquals(QuickAnswer.DateOffset(today.minusDays(45), -45), dates("45 days ago"))
        assertEquals(QuickAnswer.DateOffset(today.minusDays(10), -10), dates("10 giorni fa"))
        assertEquals(QuickAnswer.DateOffset(today.plusDays(7), 7), dates("7 days from today"))
    }

    @Test
    fun theDaysToADateAreCounted() {
        assertEquals(QuickAnswer.DaysUntil(LocalDate.of(2026, 12, 25), 61), dates("giorni al 25 dicembre"))
        assertEquals(QuickAnswer.DaysUntil(LocalDate.of(2026, 12, 25), 61), dates("quanti giorni mancano a natale"))
        assertEquals(QuickAnswer.DaysUntil(LocalDate.of(2026, 12, 25), 61), dates("days until christmas"))
        assertEquals(QuickAnswer.DaysUntil(LocalDate.of(2026, 12, 25), 61), dates("how many days until december 25th", en))
        assertEquals(QuickAnswer.DaysUntil(LocalDate.of(2026, 12, 25), 61), dates("giorni al 25/12"))
        assertEquals(QuickAnswer.DaysUntil(LocalDate.of(2027, 1, 1), 68), dates("giorni a capodanno"))
        assertEquals(QuickAnswer.DaysUntil(LocalDate.of(2028, 5, 1), java.time.temporal.ChronoUnit.DAYS.between(today, LocalDate.of(2028, 5, 1))), dates("days to 1 may 2028"))
    }

    @Test
    fun aDayThatHasPassedThisYearIsTheNextYears() {
        val answer = dates("giorni al 1 gennaio") as QuickAnswer.DaysUntil
        assertEquals(LocalDate.of(2027, 1, 1), answer.target)
        assertEquals("today is not past", QuickAnswer.DaysUntil(today, 0), dates("giorni al 25 ottobre"))
        assertEquals("the day before today waits a year", LocalDate.of(2027, 10, 24), (dates("giorni al 24 ottobre") as QuickAnswer.DaysUntil).target)
    }

    @Test
    fun aWrittenYearIsTakenAsItIsEvenWhenPast() {
        val answer = dates("giorni al 25 12 2020") as QuickAnswer.DaysUntil
        assertEquals(LocalDate.of(2020, 12, 25), answer.target)
        assertTrue(answer.days < 0)
        assertEquals(LocalDate.of(2027, 3, 14), (dates("days until 2027 3 14") as QuickAnswer.DaysUntil).target)
    }

    @Test
    fun theOrderOfDayAndMonthFollowsTheCountry() {
        assertEquals(LocalDate.of(2027, 3, 4), (dates("giorni al 4/3/2027", Locale.ITALY) as QuickAnswer.DaysUntil).target)
        assertEquals(LocalDate.of(2027, 4, 3), (dates("days until 4/3/2027", Locale.US) as QuickAnswer.DaysUntil).target)
        assertEquals("a day above twelve is the day", LocalDate.of(2026, 12, 25), (dates("days until 25/12", Locale.US) as QuickAnswer.DaysUntil).target)
    }

    @Test
    fun theFeastsAreKnownAndEasterIsWorkedOut() {
        assertEquals(LocalDate.of(2026, 10, 31), (dates("days until halloween") as QuickAnswer.DaysUntil).target)
        assertEquals(LocalDate.of(2027, 8, 15), (dates("giorni a ferragosto") as QuickAnswer.DaysUntil).target)
        assertEquals(LocalDate.of(2027, 3, 28), (dates("giorni a pasqua") as QuickAnswer.DaysUntil).target)
        assertEquals(LocalDate.of(2027, 3, 29), (dates("giorni a pasquetta") as QuickAnswer.DaysUntil).target)
        assertEquals(LocalDate.of(2025, 4, 20), DateAnswers.easter(2025))
        assertEquals(LocalDate.of(2026, 4, 5), DateAnswers.easter(2026))
        assertEquals(LocalDate.of(2024, 3, 31), DateAnswers.easter(2024))
        assertEquals(LocalDate.of(2038, 4, 25), DateAnswers.easter(2038))
    }

    @Test
    fun the29thOfFebruaryWaitsForALeapYear() {
        assertEquals(LocalDate.of(2028, 2, 29), (dates("giorni al 29 febbraio") as QuickAnswer.DaysUntil).target)
    }

    @Test
    fun whatIsNotADateGivesNothing() {
        listOf("giorni al 32 dicembre", "giorni al 31 novembre", "giorni a pippo", "days until", "tra 30 mele", "tra 30 minuti", "30 giorni", "giorni").forEach {
            assertNull(it, dates(it))
        }
        assertNull("a year far from now", dates("tra 99999 anni"))
    }

    // --- the time in another city, and the order of the answers

    private val now = Instant.parse("2026-01-15T22:30:00Z") // 23:30 in Rome
    private val rome = ZoneId.of("Europe/Rome")
    private fun answers(text: String) = QuickAnswers.of(text, itl, now, rome, Random(3))

    @Test
    fun theTimeInACityIsAnsweredInItalianAndEnglish() {
        listOf("ora a Tokyo", "che ore sono a Tokyo", "che ora è a tokyo", "time in tokyo", "what time is it in Tokyo?", "tokyo time", "ora tokyo").forEach {
            val answer = answers(it).single() as QuickAnswer.WorldTime
            assertEquals(it, "Tokyo", answer.city)
            assertEquals(LocalTime.of(7, 30), answer.time)
            assertEquals("tomorrow there", 1, answer.dayOffset)
        }
        val newYork = answers("ora a New York").single() as QuickAnswer.WorldTime
        assertEquals(LocalTime.of(17, 30), newYork.time)
        assertEquals(0, newYork.dayOffset)
        assertEquals("Londra is London", "London", (answers("ora a londra").single() as QuickAnswer.WorldTime).city)
    }

    @Test
    fun aWordThatIsNotACityIsNotATimeQuestion() {
        listOf("ora legale", "ora", "time", "orario treni", "ore 7", "time machine").forEach { assertTrue(it, answers(it).isEmpty()) }
    }

    @Test
    fun theEarlierAnswersStillWinAndTheNewOnesDoNotCatchSums() {
        assertTrue(answers("12*3+4").single() is QuickAnswer.Calculation)
        assertTrue(answers("5 km in miles").single() is QuickAnswer.Conversion)
        assertTrue(answers("timer 10 minuti").single() is QuickAnswer.Timer)
        assertTrue(answers("dado").single() is QuickAnswer.Dice)
        assertTrue(answers("tra 30 giorni").single() is QuickAnswer.DateOffset)
        assertTrue(answers("giorni al 25 dicembre").single() is QuickAnswer.DaysUntil)
        assertTrue(answers("whatsapp").isEmpty())
    }

    // --- what touching an answer does

    @Test
    fun theNewAnswersCopyOrThrowAgainAndOpenNothing() {
        val worldTime = QuickAnswer.WorldTime("Tokyo", LocalTime.of(7, 5), 1)
        assertEquals("7:05", QuickAnswerActions.copyText(worldTime))
        val date = QuickAnswer.DateOffset(LocalDate.of(2026, 12, 25), 61)
        assertTrue(QuickAnswerActions.copyText(date, en)!!.contains("December 25, 2026"))
        assertTrue(QuickAnswerActions.copyText(date, itl)!!.contains("25 dicembre 2026"))
        assertEquals("61", QuickAnswerActions.copyText(QuickAnswer.DaysUntil(LocalDate.of(2026, 12, 25), 61)))
        assertEquals("108.00", QuickAnswerActions.copyText(QuickAnswer.Currency("100 EUR = 108.00 USD", "108.00", "2026-10-23")))
        val dice = QuickAnswer.Dice(1, 6, listOf(4), 0)
        assertNull(QuickAnswerActions.copyText(dice))
        assertNull(QuickAnswerActions.copyText(QuickAnswer.Coin(true)))
        listOf<QuickAnswer>(worldTime, date, dice, QuickAnswer.Coin(false), QuickAnswer.Currency("a", "b", "c")).forEach {
            assertNull(QuickAnswerActions.intent(it))
        }
    }

    @Test
    fun theTotalOfADieIsTheRollsPlusTheBonus() {
        assertEquals(9, QuickAnswer.Dice(2, 6, listOf(3, 4), 2).total)
        assertEquals(5, QuickAnswer.Dice(2, 6, listOf(3, 4), -2).total)
    }

    // --- the calendar in the search

    @Test
    fun theCalendarFindsNothingWithoutPermissionOrWithAShortWord() {
        val search = CalendarSearch(app)
        assertTrue(search.search("dentist").isEmpty())
        shadowOf(app).grantPermissions(android.Manifest.permission.READ_CALENDAR)
        assertTrue("a single letter is no search", search.search("d").isEmpty())
        assertTrue("no calendar on the test phone", search.search("dentist").isEmpty())
        assertEquals("content://com.android.calendar/events/42", CalendarSearch.uri(42))
    }

    // --- the store

    @Test
    fun theStoreLinksAreMadeOnlyForRealPackageNames() {
        assertEquals("market://details?id=com.example.app", AppLinks.storeUri("com.example.app"))
        assertEquals("https://play.google.com/store/apps/details?id=com.example.app", AppLinks.webUrl("com.example.app"))
        assertEquals("Mail https://play.google.com/store/apps/details?id=com.example.app", AppLinks.shareText(" Mail ", "com.example.app"))
        listOf("", "app", "com.example.app&x=1", "com example", "../etc", "1com.a").forEach {
            assertFalse(it, AppLinks.valid(it))
            assertNull(AppLinks.storeUri(it))
            assertNull(AppLinks.webUrl(it))
            assertNull(AppLinks.shareText("x", it))
        }
        assertTrue(AppLinks.valid("com.example.app_2"))
    }

    @Test
    fun theStoreAndTheShareSheetOpenForAnApp() {
        val vm = LauncherViewModel(app)
        shadowOf(Looper.getMainLooper()).idle()
        val item = AppItem("com.example.app", "Example", Launch.Action("x"), packageName = "com.example.app")
        vm.openInStore(item)
        val store = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, store.action)
        assertEquals("market://details?id=com.example.app", store.dataString)

        vm.shareApp(item)
        val chooser = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val sent = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, sent.action)
        assertEquals("Example https://play.google.com/store/apps/details?id=com.example.app", sent.getStringExtra(Intent.EXTRA_TEXT))

        // An entry that is not an app has no page.
        vm.openInStore(AppItem("x", "x", Launch.Action("x")))
        vm.shareApp(AppItem("x", "x", Launch.Action("x")))
        assertNull(shadowOf(app).nextStartedActivity)
    }

    // --- the settings

    @Test
    fun theNewSearchSettingsAreSavedAndOffByDefault() {
        val defaults = LauncherSettings()
        assertFalse(defaults.searchCurrency)
        assertFalse(defaults.searchCalendar)
        assertFalse(defaults.compactList)
        val custom = LauncherSettings(searchCurrency = true, searchCalendar = true, compactList = true)
        assertEquals(custom, SettingsCodec.decode(SettingsCodec.encode(custom)))
        assertEquals(custom, SettingsCodec.decode(SettingsCodec.encode(custom, portable = true)))
        assertEquals(defaults, SettingsCodec.decode("{}"))
    }
}
