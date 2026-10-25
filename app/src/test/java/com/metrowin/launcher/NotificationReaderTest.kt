package com.metrowin.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Person
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.session.MediaSession
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.notifications.NotificationReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What is read out of real notifications: the messages of a chat, who sent them, progress, pictures and music. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationReaderTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var reader: NotificationReader

    @Before
    fun setUp() {
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("test", "test", NotificationManager.IMPORTANCE_DEFAULT))
        reader = NotificationReader(context)
    }

    private fun builder() = Notification.Builder(context, "test").setSmallIcon(android.R.drawable.sym_def_app_icon)

    private fun bitmap(color: Int, side: Int = 64) = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private fun read(n: Notification, ongoing: Boolean = false, summary: Boolean = false, pictures: Boolean = true) =
        reader.read("key", "com.whatsapp", 123L, n, ongoing, summary, pictures)

    // --- text

    @Test
    fun theTitleTheLineAndTheNumberAreRead() {
        val n = builder().setContentTitle("marco").setContentText("liked your photo").setNumber(3).setCategory(Notification.CATEGORY_SOCIAL).build()
        val read = read(n)
        assertEquals("marco", read.title)
        assertEquals("liked your photo", read.text)
        assertEquals(3, read.number)
        assertEquals(Notification.CATEGORY_SOCIAL, read.category)
        assertTrue(read.messages.isEmpty())
        assertNull(read.progress)
        assertNull(read.playing)
    }

    @Test
    fun aNotificationWithNothingInItIsReadEmptyNotCrashed() {
        val read = read(builder().build())
        assertEquals("", read.title)
        assertEquals("", read.text)
        assertEquals(0, read.number)
    }

    @Test
    fun theFlagsOfTheServiceAreKept() {
        val n = builder().setContentTitle("t").build()
        val read = read(n, ongoing = true, summary = true)
        assertTrue(read.ongoing)
        assertTrue(read.groupSummary)
        assertEquals("com.whatsapp", read.packageName)
        assertEquals(123L, read.postTime)
        assertEquals("key", read.key)
    }

    // --- conversations

    private fun chat(title: String? = null, group: Boolean = false): Notification {
        val me = Person.Builder().setName("Me").build()
        val giulia = Person.Builder().setName("Giulia").setIcon(Icon.createWithBitmap(bitmap(0xFFCC2222.toInt()))).build()
        val style = Notification.MessagingStyle(me)
            .addMessage("Ciao", 1_000L, giulia)
            .addMessage("Ci vediamo alle otto?", 2_000L, giulia)
        if (title != null) style.setConversationTitle(title).setGroupConversation(group)
        return builder().setStyle(style).setContentTitle(title ?: "Giulia").build()
    }

    @Test
    fun theMessagesOfAChatAreReadWithTheirSenders() {
        val read = read(chat())
        assertEquals(2, read.messages.size)
        assertEquals("Giulia", read.messages.last().sender)
        assertEquals("Ci vediamo alle otto?", read.messages.last().text)
        assertEquals(2_000L, read.messages.last().time)
        assertNull(read.conversationTitle)
    }

    @Test
    fun aChatWrittenByTheCompatLibraryIsReadToo() {
        // Older apps and the compat library write the sender as a name, or as a Bundle, not as a Person.
        val withName = Bundle().apply { putCharSequence("text", "ci sei?"); putLong("time", 5L); putCharSequence("sender", "Luca") }
        val withBundle = Bundle().apply {
            putCharSequence("text", "si"); putLong("time", 6L)
            putParcelable("sender_person", Bundle().apply { putCharSequence("name", "Anna") })
        }
        val blank = Bundle().apply { putCharSequence("text", "  "); putLong("time", 7L) }
        val n = builder().setContentTitle("chat").build()
        n.extras.putParcelableArray(Notification.EXTRA_MESSAGES, arrayOf(withName, withBundle, blank))
        val messages = read(n).messages
        assertEquals(listOf("Luca", "Anna"), messages.map { it.sender })
        assertEquals(listOf("ci sei?", "si"), messages.map { it.text })
        assertEquals(listOf(5L, 6L), messages.map { it.time })
    }

    @Test
    fun theNameOfAGroupChatIsRead() {
        assertEquals("Famiglia", read(chat(title = "Famiglia", group = true)).conversationTitle)
    }

    @Test
    fun thePictureOfTheSenderIsReadAndShrunk() {
        val avatar = read(chat()).avatar
        assertNotNull(avatar)
        assertTrue(avatar!!.width <= NotificationReader.AVATAR_PX && avatar.height <= NotificationReader.AVATAR_PX)
    }

    @Test
    fun withoutPicturesNoPictureIsDecoded() {
        val read = read(chat(), pictures = false)
        assertNull(read.avatar)
        assertNull(read.picture)
        // The words are still read.
        assertEquals(2, read.messages.size)
    }

    // --- progress

    @Test
    fun aProgressBarIsReadAsAPercent() {
        val read = read(builder().setContentTitle("film.mp4").setProgress(200, 90, false).build(), ongoing = true)
        assertEquals(45, read.progress)
    }

    @Test
    fun aProgressThatDoesNotKnowHasNone() {
        assertNull(read(builder().setContentTitle("Syncing").setProgress(0, 0, true).build(), ongoing = true).progress)
    }

    @Test
    fun aProgressIsKeptBetweenZeroAndAHundred() {
        assertEquals(100, read(builder().setProgress(10, 99, false).build()).progress)
        assertEquals(0, read(builder().setProgress(10, 0, false).build()).progress)
    }

    // --- pictures

    @Test
    fun theBigPictureOfANotificationIsReadAndShrunk() {
        val n = builder().setContentTitle("Anna").setStyle(Notification.BigPictureStyle().bigPicture(bitmap(0xFF2222CC.toInt(), side = 1200))).build()
        val picture = read(n).picture
        assertNotNull(picture)
        assertTrue("shrunk to the size of a tile", picture!!.width <= NotificationReader.PICTURE_PX * 2 && picture.height <= NotificationReader.PICTURE_PX * 2)
    }

    // --- music

    @Test
    fun theMusicTakesWhatTheSessionSaysAndTheNotificationWhereItSaysNothing() {
        val song = NotificationReader.nowPlayingFrom("Bohemian Rhapsody", "Queen", null, true, "notification title", "notification text")!!
        assertEquals("Bohemian Rhapsody", song.title)
        assertEquals("Queen", song.artist)
        assertTrue(song.playing)
        // No metadata: the words of the notification stand in.
        val fallback = NotificationReader.nowPlayingFrom(null, "  ", null, false, "Track", "Band")!!
        assertEquals("Track", fallback.title)
        assertEquals("Band", fallback.artist)
        assertFalse(fallback.playing)
        // Only one of them is enough.
        assertEquals("Song", NotificationReader.nowPlayingFrom("Song", null, null, true, "", "")!!.title)
        // Neither: nothing to show.
        assertNull(NotificationReader.nowPlayingFrom(null, null, null, true, "", ""))
    }

    @Test
    fun aMediaNotificationIsRecognisedByItsSessionAndFallsBackToItsWords() {
        // A phone gives the metadata of the session; here there is no session service, so the words of the notification are used.
        val session = MediaSession(context, "test")
        val n = builder().setContentTitle("Track").setContentText("Band").setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken)).build()
        val playing = read(n, ongoing = true).playing
        assertNotNull(playing)
        assertEquals("Track", playing!!.title)
        assertEquals("Band", playing.artist)
        session.release()
    }

    @Test
    fun anotherNotificationHasNoMusic() {
        assertNull(read(builder().setContentTitle("x").build()).playing)
    }
}
