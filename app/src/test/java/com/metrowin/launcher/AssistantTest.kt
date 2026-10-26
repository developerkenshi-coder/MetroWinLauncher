package com.metrowin.launcher

import com.metrowin.launcher.data.Assistant
import com.metrowin.launcher.data.QuickAnswer
import com.metrowin.launcher.data.VoiceCommand
import com.metrowin.launcher.data.VoiceCommands
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the search box does with a sentence that asks for something. "Now" is Sunday 25 October 2026, 15:00, in Rome. */
class AssistantTest {
    private val now = LocalDateTime.of(2026, 10, 25, 15, 0)
    private val rome = ZoneId.of("Europe/Rome")

    private fun of(query: String) = Assistant.of(query, now, rome)

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(year, month, day, hour, minute).atZone(rome).toInstant().toEpochMilli()

    @Test
    fun aReminderWithItsTimeInItalian() {
        assertEquals(
            listOf(QuickAnswer.Reminder("Chiamare Anna", millis(2026, 10, 26, 18))),
            of("ricordami di chiamare Anna domani alle 18"),
        )
        assertEquals(listOf(QuickAnswer.Reminder("Spegnere il forno", millis(2026, 10, 25, 15, 20))), of("Ricordami tra 20 minuti di spegnere il forno"))
        assertEquals(listOf(QuickAnswer.Reminder("Pagare la bolletta", millis(2026, 10, 30, 9))), of("imposta un promemoria per il 30 pagare la bolletta"))
    }

    @Test
    fun aReminderWithItsTimeInEnglish() {
        assertEquals(listOf(QuickAnswer.Reminder("Call mom", millis(2026, 10, 25, 15, 20))), of("remind me to call mom in 20 minutes"))
        assertEquals(listOf(QuickAnswer.Reminder("Take the medicine", millis(2026, 10, 26, 8))), of("reminder: take the medicine tomorrow at 8"))
        assertEquals(listOf(QuickAnswer.Reminder("Buy flowers", millis(2026, 10, 25, 17))), of("set a reminder to buy flowers at 5"))
    }

    @Test
    fun aReminderWithNoTimeWaitsForOne() {
        assertEquals(listOf(QuickAnswer.Reminder("Comprare il latte", null)), of("promemoria: comprare il latte"))
        assertEquals(listOf(QuickAnswer.Reminder("Call the plumber", null)), of("remind me to call the plumber"))
    }

    @Test
    fun aReminderNeedsSomethingToRemember() {
        assertTrue(of("ricordami").isEmpty())
        assertTrue(of("remind me").isEmpty())
        // A time alone is not a thing to remember.
        assertTrue(of("ricordami domani alle 18").isEmpty())
    }

    @Test
    fun anEventGoesToTheCalendar() {
        assertEquals(
            listOf(QuickAnswer.CalendarEvent("Cena con Luca", millis(2026, 10, 30, 20), false)),
            of("evento cena con Luca venerdì alle 20"),
        )
        assertEquals(
            listOf(QuickAnswer.CalendarEvent("Dentist", millis(2026, 10, 26, 10), false)),
            of("add event dentist tomorrow at 10"),
        )
        assertEquals(
            listOf(QuickAnswer.CalendarEvent("Riunione", millis(2026, 10, 26, 15), false)),
            of("aggiungi riunione domani alle 15 al calendario"),
        )
    }

    @Test
    fun anEventWithADayAndNoHourLastsTheWholeDay() {
        val midnight = LocalDateTime.of(2026, 11, 3, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(listOf(QuickAnswer.CalendarEvent("Compleanno Marco", midnight, true)), of("evento compleanno Marco il 3 novembre"))
    }

    @Test
    fun anEventWithNoMomentOpensTheCalendarForOne() {
        assertEquals(listOf(QuickAnswer.CalendarEvent("Cena", null, false)), of("nuovo evento cena"))
    }

    @Test
    fun aNoteGoesToKeep() {
        assertEquals(listOf(QuickAnswer.Note("Comprare il latte")), of("nota: comprare il latte"))
        assertEquals(listOf(QuickAnswer.Note("Buy milk and eggs")), of("take a note buy milk and eggs"))
        assertEquals(listOf(QuickAnswer.Note("Idea per il progetto")), of("appunta idea per il progetto"))
    }

    @Test
    fun aLoneWordAfterNoteIsAnAppBeingLookedFor() {
        assertTrue(of("note").isEmpty())
        assertTrue(of("nota latte").isEmpty())
        assertTrue(of("notes").isEmpty())
    }

    @Test
    fun placesNearTheUser() {
        assertEquals(listOf(QuickAnswer.NearMe("farmacia")), of("farmacia vicino a me"))
        assertEquals(listOf(QuickAnswer.NearMe("pharmacy")), of("pharmacy near me"))
        assertEquals(listOf(QuickAnswer.NearMe("pizzeria")), of("la pizzeria qui vicino"))
        assertEquals(listOf(QuickAnswer.NearMe("farmacia")), of("dov'è la farmacia più vicina"))
        assertEquals(listOf(QuickAnswer.NearMe("ATM")), of("nearest ATM"))
        assertEquals(listOf(QuickAnswer.NearMe("Caffè")), of("Caffè nei dintorni"))
    }

    @Test
    fun theCameraThatReadsCodes() {
        assertEquals(listOf(QuickAnswer.Scan), of("scansiona qr"))
        assertEquals(listOf(QuickAnswer.Scan), of("QR code"))
        assertEquals(listOf(QuickAnswer.Scan), of("google lens"))
        assertEquals(listOf(QuickAnswer.Scan), of("Scan"))
    }

    @Test
    fun anOrdinaryWordAsksForNothing() {
        assertTrue(of("chrome").isEmpty())
        assertTrue(of("ab").isEmpty())
        assertTrue(of("").isEmpty())
        assertTrue(of("calcolatrice").isEmpty())
        assertTrue(of("12*3").isEmpty())
        assertTrue(of("vicino").isEmpty())
    }

    // --- the same sentences by voice

    private val apps = listOf(AppItem("id:maps", "Maps", Launch.Action("x")), AppItem("id:note", "Note", Launch.Action("x")))
    private fun said(heard: String) = VoiceCommands.parse(heard, apps, { it.id }, now)
    private fun here(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(year, month, day, hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun theSameSentencesWorkByVoice() {
        assertEquals(
            VoiceCommand.Assist(QuickAnswer.Reminder("Chiamare anna", here(2026, 10, 26, 18))),
            said("ricordami di chiamare anna domani alle 18"),
        )
        assertEquals(
            VoiceCommand.Assist(QuickAnswer.Reminder("Spegnere il forno", here(2026, 10, 25, 15, 20))),
            said("ricordami tra 20 minuti di spegnere il forno"),
        )
        assertEquals(VoiceCommand.Assist(QuickAnswer.NearMe("farmacia")), said("farmacia vicino a me"))
        assertEquals(VoiceCommand.Assist(QuickAnswer.Note("Comprare il latte")), said("nota comprare il latte"))
        assertEquals(VoiceCommand.Assist(QuickAnswer.Scan), said("scansiona qr"))
        assertEquals(
            VoiceCommand.Assist(QuickAnswer.CalendarEvent("Cena", here(2026, 10, 30, 20), false)),
            said("evento cena venerdì alle 20"),
        )
    }

    @Test
    fun aFillerInFrontOfTheSentenceDoesNotHideIt() {
        assertEquals(VoiceCommand.Assist(QuickAnswer.Reminder("Comprare il latte", null)), said("ehi ricordami di comprare il latte"))
        assertEquals(VoiceCommand.Assist(QuickAnswer.NearMe("pharmacy")), said("please pharmacy near me"))
    }

    @Test
    fun theOtherVoiceCommandsAreNotTakenForSentencesOfTheAssistant() {
        assertTrue(said("apri Maps") is VoiceCommand.OpenApp)
        assertTrue(said("chiama Anna") is VoiceCommand.Call)
        assertTrue(said("timer 10 minuti") is VoiceCommand.Clock)
        assertTrue(said("cerca ricette di pizza") is VoiceCommand.Web)
        // One word after "note" is the app, not a note.
        assertTrue(said("apri note") is VoiceCommand.OpenApp)
    }

    @Test
    fun theBestOfTheAlternativesIsTheAssistantsWhenTheRestIsAWebSearch() {
        val heard = listOf("ricordo mi bere acqua", "ricordami di bere tra 20 minuti")
        assertEquals(
            VoiceCommand.Assist(QuickAnswer.Reminder("Bere", here(2026, 10, 25, 15, 20))),
            VoiceCommands.best(heard, apps, { it.id }, now),
        )
    }
}
