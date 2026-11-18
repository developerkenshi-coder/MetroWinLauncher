package com.metrowin.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.MiniTiles
import com.metrowin.launcher.data.MiniWords
import com.metrowin.launcher.mail.MailMessage
import com.metrowin.launcher.mail.MailSync
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.notes.Note
import com.metrowin.launcher.notes.NoteCrypt
import com.metrowin.launcher.notifications.LiveCard
import com.metrowin.launcher.ui.settings.MiniOptions
import com.metrowin.launcher.wallet.Card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The live tile of the mini apps (see MiniTiles): what each face says, what it never says, and the switches. */
@RunWith(RobolectricTestRunner::class)
class MiniTilesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val w = MiniWords({ "$it mail" }, { "$it texts" }, { "$it missed" }, "locked", "last recording", "top card")

    private fun digest(faces: List<LiveCard>) = faces.single() as LiveCard.Digest

    // --- the faces

    @Test fun mailSaysTheCountAndTheLastArrivalAndNothingWhenAllIsRead() {
        assertEquals(LiveCard.Digest("3 mail", "Anna: Lunch?"), digest(MiniTiles.mail(w, 3, "Anna" to "Lunch?")))
        assertEquals("1 mail", digest(MiniTiles.mail(w, 1, null)).title)
        assertTrue(MiniTiles.mail(w, 0, "Anna" to "Lunch?").isEmpty())
    }

    @Test fun textsLeaveTheWordsOutWhenTheTextIsHidden() {
        assertEquals("Anna: see you", digest(MiniTiles.texts(w, 2, "Anna", "see you", hideText = false)).text)
        val hidden = digest(MiniTiles.texts(w, 2, "Anna", "see you", hideText = true))
        assertEquals("Anna", hidden.text)
        assertFalse("see you" in hidden.title + hidden.text)
        assertTrue(MiniTiles.texts(w, 0, "Anna", "x", false).isEmpty())
    }

    @Test fun missedCallsNameTheLastCaller() {
        assertEquals(LiveCard.Digest("2 missed", "Bob"), digest(MiniTiles.missed(w, 2, "Bob")))
        assertTrue(MiniTiles.missed(w, 0, "Bob").isEmpty())
    }

    @Test fun theNoteChangedLastShowsItsTitleAndFirstLine() {
        val notes = listOf(
            Note("a", "Old", "old text", updated = 1L),
            Note("b", "Plan", "\n first line \nsecond", updated = 9L),
            Note("c", "Binned", "bin", updated = 99L, deleted = 5L),
        )
        assertEquals(LiveCard.Digest("Plan", "first line"), digest(MiniTiles.note(w, notes)))
        // No title: the first line is the title.
        assertEquals(LiveCard.Digest("Milk", "eggs"), digest(MiniTiles.note(w, listOf(Note("d", "", "Milk\neggs", updated = 3L)))))
        assertTrue(MiniTiles.note(w, emptyList()).isEmpty())
        assertTrue(MiniTiles.note(w, listOf(Note("e", "x", "y", deleted = 4L))).isEmpty())
    }

    @Test fun aLockedNoteShowsNeitherTitleNorText() {
        val face = digest(MiniTiles.note(w, listOf(Note("a", "Bank PIN", "1234 secret", updated = 9L, locked = true), Note("b", "Open", "x", updated = 1L))))
        assertEquals(LiveCard.Digest("locked", ""), face)
        assertFalse("Bank" in face.title + face.text || "1234" in face.title + face.text)
    }

    @Test fun theLastRecordingShowsItsDateAndName() {
        assertEquals("last recording", digest(MiniTiles.recording(w, "20261118-101500 Lesson.m4a")).title)
        assertTrue("Lesson" in digest(MiniTiles.recording(w, "20261118-101500 Lesson.m4a")).text)
        assertTrue(MiniTiles.recording(w, null).isEmpty())
    }

    @Test fun theWalletSaysTheNameOfOneCardAndNeverItsCode() {
        val cards = listOf(
            Card("1", "Gym", "CODE-GYM", uses = 2, note = "secret note"),
            Card("2", "Shop", "CODE-SHOP", uses = 7),
            Card("3", "Fav", "CODE-FAV", favorite = true),
        )
        val face = digest(MiniTiles.wallet(w, cards, locked = false))
        assertEquals(LiveCard.Digest("top card", "Shop"), face)
        assertEquals("Fav", digest(MiniTiles.wallet(w, listOf(Card("3", "Fav", "CODE-FAV", favorite = true)), false)).text)
        // Without history or favourite there is nothing to say; with the lock of the wallet on, nothing either.
        assertTrue(MiniTiles.wallet(w, listOf(Card("4", "Plain", "x")), false).isEmpty())
        assertTrue(MiniTiles.wallet(w, cards, locked = true).isEmpty())
        cards.forEach { c -> assertFalse(c.code in face.title + face.text || c.note.isNotEmpty() && c.note in face.text) }
    }

    @Test fun theWordsComeFromTheResourcesWithTheirPlurals() {
        val real = MiniWords.of(context)
        assertEquals("1 unread email", real.unreadMail(1))
        assertEquals("4 unread emails", real.unreadMail(4))
        assertEquals("2 missed calls", real.missedCalls(2))
        assertEquals("1 unread text", real.unreadTexts(1))
    }

    // --- the switches and the reading

    @Test fun whatIsPersonalIsOffUntilTheUserTurnsItOn() {
        listOf("mail", "messaging", "notes", "voicerecorder", "wallet").forEach { assertFalse("$it off by default", MiniTiles.on(context, it)) }
        listOf("phone", "nowplaying").forEach { assertTrue("$it on by default", MiniTiles.on(context, it)) }
        MiniTiles.set(context, "notes", true)
        assertTrue(MiniTiles.on(context, "notes"))
        MiniTiles.set(context, "notes", false)
        assertFalse(MiniTiles.on(context, "notes"))
    }

    @Test fun everyMiniAppWithAFaceHasItsSwitchInTheSettings() {
        MiniTiles.apps.forEach { app ->
            val option = MiniOptions.byId["mini.$app.live_tile"]!!
            option.write(context, true)
            assertTrue(option.read(context))
            option.write(context, false)
            assertFalse(option.read(context))
        }
    }

    private fun tile(app: String) = AppItem("stock:$app", app, Launch.Action("x"), Glyph.Notes)

    @Test fun onlyTheAppsPinnedToStartWithTheSwitchOnAreAsked() {
        MiniTiles.set(context, "notes", true)
        val items = listOf(tile("notes"), tile("mail"), tile("phone"), tile("calendar"))
        // Mail is off, Calendar has a tile of its own, Phone is not pinned.
        assertEquals(setOf("notes"), MiniTiles.wanted(context, setOf("stock:notes", "stock:mail", "stock:calendar"), items))
        assertEquals(setOf("notes", "phone"), MiniTiles.wanted(context, setOf("stock:notes", "stock:phone"), items))
        assertEquals(emptySet<String>(), MiniTiles.wanted(context, emptySet(), items))
    }

    @Test fun withoutThePermissionOrTheSwitchNothingIsRead() {
        MiniTiles.set(context, "messaging", true)
        // Robolectric grants no permission: no calls and no texts are read, whatever the phone holds.
        assertTrue(MiniTiles.read(context, setOf("messaging", "phone"), hideText = false).isEmpty())
        // A switch that is off reads nothing for its app even when it is asked for.
        MiniTiles.set(context, "wallet", false)
        assertTrue(MiniTiles.read(context, setOf("wallet", "notes"), hideText = false).isEmpty())
    }

    @Test fun theSourcesWatchedAreTheOnesTheFacesNeed() {
        assertEquals(setOf(com.metrowin.launcher.data.DataChanges.Source.Calls, com.metrowin.launcher.data.DataChanges.Source.Messages), MiniTiles.sources(setOf("phone", "messaging", "notes")))
        assertTrue(MiniTiles.sources(setOf("notes", "wallet")).isEmpty())
    }

    // --- the last mail, kept encrypted

    private object Fake : NoteCrypt {
        override fun encrypt(plain: String) = "enc:" + plain.reversed()
        override fun decrypt(text: String) = text.removePrefix("enc:").reversed().takeIf { text.startsWith("enc:") }
    }

    @Test fun theLastMailIsKeptEncryptedAndOnLine() {
        assertNull(MailSync.last(context, Fake))
        MailSync.remember(context, MailMessage("1", "acc", "Anna\nRossi", "a@x.it", "Lunch\ntomorrow?", "", 0L, true, bodyText = "BODY"), Fake)
        assertEquals("Anna Rossi" to "Lunch tomorrow?", MailSync.last(context, Fake))
        val raw = context.getSharedPreferences("metrowin_mail_sync", Context.MODE_PRIVATE).all.values.joinToString { it.toString() }
        assertFalse("kept in the clear", "Anna" in raw || "Lunch" in raw || "BODY" in raw)
        MailSync.forget(context, "acc")
        assertNull(MailSync.last(context, Fake))
    }
}
