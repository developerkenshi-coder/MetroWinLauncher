package com.metrowin.launcher

import com.metrowin.launcher.data.WhenParser
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A moment said in words, in Italian and in English. "Now" is Sunday 25 October 2026, 15:00. */
class WhenParserTest {
    private val now = LocalDateTime.of(2026, 10, 25, 15, 0)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(year, month, day, hour, minute)

    private fun parse(text: String) = WhenParser.parse(text, now)

    private fun assertWhen(text: String, expected: LocalDateTime, rest: String) {
        val result = parse(text) ?: error("no moment found in \"$text\"")
        assertEquals(text, expected, result.at)
        assertEquals("rest of \"$text\"", rest, result.rest)
    }

    @Test fun aSentenceWithoutAMomentHasNone() {
        assertNull(parse("chiamare Anna"))
        assertNull(parse("buy milk"))
        assertNull(parse(""))
    }

    @Test fun tomorrowAtAnHour() {
        assertWhen("chiamare Anna domani alle 18", at(2026, 10, 26, 18), "chiamare Anna")
        assertWhen("buy milk tomorrow at 6pm", at(2026, 10, 26, 18), "buy milk")
        assertWhen("Chiamare Anna domani alle 18:30", at(2026, 10, 26, 18, 30), "Chiamare Anna")
    }

    @Test fun aLengthOfTimeFromNow() {
        assertWhen("tra 20 minuti spegnere il forno", at(2026, 10, 25, 15, 20), "spegnere il forno")
        assertWhen("in 2 hours call mom", at(2026, 10, 25, 17), "call mom")
        assertWhen("fra mezz'ora pasta", at(2026, 10, 25, 15, 30), "pasta")
        assertWhen("tra un'ora medicine", at(2026, 10, 25, 16), "medicine")
        assertWhen("in half an hour stretch", at(2026, 10, 25, 15, 30), "stretch")
        assertTrue(parse("tra 20 minuti")!!.hasTime)
    }

    @Test fun daysFromNowKeepTheMorning() {
        val result = parse("tra 3 giorni dentista")!!
        assertEquals(at(2026, 10, 28, 9), result.at)
        assertEquals("dentista", result.rest)
        assertFalse(result.hasTime)
    }

    @Test fun aDayOfTheMonth() {
        assertWhen("pagare l'affitto il 30", at(2026, 10, 30, 9), "pagare l'affitto")
        // The 3rd has passed this month: the next one.
        assertWhen("bolletta il 3", at(2026, 11, 3, 9), "bolletta")
        assertWhen("cena il 15 marzo alle 20", at(2027, 3, 15, 20), "cena")
        assertWhen("party on march 15 at 8 pm", at(2027, 3, 15, 20), "party")
        assertWhen("compleanno 3 novembre", at(2026, 11, 3, 9), "compleanno")
        assertWhen("laurea 15 dic 2027", at(2027, 12, 15, 9), "laurea")
    }

    @Test fun numericAndIsoDates() {
        assertWhen("dentista 15/03", at(2027, 3, 15, 9), "dentista")
        assertWhen("vacanza 2026-12-24 20:00", at(2026, 12, 24, 20), "vacanza")
        assertWhen("esame 5/11/2026 alle 10", at(2026, 11, 5, 10), "esame")
    }

    @Test fun aDayOfTheWeek() {
        assertWhen("dentista venerdì alle 15.30", at(2026, 10, 30, 15, 30), "dentista")
        assertWhen("riunione lunedì mattina", at(2026, 10, 26, 9), "riunione")
        assertWhen("next monday at 9", at(2026, 10, 26, 9), "")
        assertWhen("prossimo mercoledì sera", at(2026, 10, 28, 20), "")
        // Today is a Sunday and the morning has passed: the Sunday after.
        assertWhen("domenica", at(2026, 11, 1, 9), "")
        assertWhen("call Bob on Friday", at(2026, 10, 30, 9), "call Bob")
    }

    @Test fun thePartOfTheDay() {
        assertWhen("palestra stasera alle 7", at(2026, 10, 25, 19), "palestra")
        assertWhen("tomorrow morning", at(2026, 10, 26, 9), "")
        assertWhen("domani sera cinema", at(2026, 10, 26, 20), "cinema")
        assertWhen("domani pomeriggio alle 4", at(2026, 10, 26, 16), "")
        assertWhen("stasera", at(2026, 10, 25, 20), "")
    }

    @Test fun anHourAloneIsTheNextTimeItHappens() {
        // 3 pm now: "at 5" is the evening, "at 4" too, "alle 18" is as it says.
        assertWhen("call John at 5", at(2026, 10, 25, 17), "call John")
        assertWhen("call John at 4", at(2026, 10, 25, 16), "call John")
        assertWhen("alle 18 yoga", at(2026, 10, 25, 18), "yoga")
        // At 3 pm "alle 9" is tonight; with the morning said it is tomorrow's.
        assertWhen("alle 9 colazione", at(2026, 10, 25, 21), "colazione")
        assertWhen("alle 9 di mattina colazione", at(2026, 10, 26, 9), "colazione")
        assertWhen("at 6:30 pm tea", at(2026, 10, 25, 18, 30), "tea")
    }

    @Test fun halvesAndQuarters() {
        assertWhen("alle 6 e mezza", at(2026, 10, 25, 18, 30), "")
        assertWhen("domani alle 8 e un quarto", at(2026, 10, 26, 8, 15), "")
        assertWhen("domani alle 9 meno un quarto", at(2026, 10, 26, 8, 45), "")
        assertWhen("domani mezzogiorno", at(2026, 10, 26, 12), "")
        assertWhen("domani a mezzanotte", at(2026, 10, 26, 0), "")
    }

    @Test fun todayWithoutATimeThatHasPassedIsInAnHour() {
        assertWhen("oggi", at(2026, 10, 25, 16), "")
    }

    @Test fun anImpossibleHourIsNotAMoment() {
        assertNull(parse("alle 25"))
    }

    @Test fun theLettersOfTheRestAreKept() {
        assertWhen("Telefonare a Zoë domani alle 18", at(2026, 10, 26, 18), "Telefonare a Zoë")
        assertWhen("domani alle 18: Chiamare l'Avvocato", at(2026, 10, 26, 18), "Chiamare l'Avvocato")
    }

    @Test fun foldingKeepsTheLength() {
        val text = "Venerdì è l'ora delle ‘Città’"
        assertEquals(text.length, WhenParser.fold(text).length)
        assertEquals("venerdi e l ora delle  citta ", WhenParser.fold(text))
    }
}
