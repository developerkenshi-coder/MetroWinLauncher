package com.metrowin.launcher

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.notifications.BannerGate
import com.metrowin.launcher.notifications.NotificationEntry
import com.metrowin.launcher.notifications.NotificationHub
import com.metrowin.launcher.notifications.NotificationReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationFixesTest {
    private fun entry(key: String, text: String = "hi", postTime: Long = 100L, title: String = "Anna") =
        NotificationEntry(key, "chat", title, text, postTime, contentIntent = null, autoCancel = false)

    @Before
    fun clean() = NotificationHub.dismissAll()

    // --- a chat posts its notification again at each message

    @Test
    fun aNewMessageInTheSameChatIsNewsEvenWithTheSameKey() {
        val first = entry("k", "first", postTime = 100)
        val seen = BannerGate.seen(listOf(first))
        assertFalse("the same post is not news", BannerGate.isNew(seen, first))
        assertTrue("another text", BannerGate.isNew(seen, entry("k", "second", postTime = 200)))
        assertTrue("the same text said again", BannerGate.isNew(seen, entry("k", "first", postTime = 300)))
        assertTrue("another chat", BannerGate.isNew(seen, entry("other", "first", postTime = 100)))
    }

    @Test
    fun theLineOfAChatIsItsNewestMessageAndAnyOtherNotificationHasNone() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val anna = Person.Builder().setName("Anna").build()
        val chat = NotificationCompat.Builder(context, "c").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setStyle(NotificationCompat.MessagingStyle(anna).addMessage("first", 1L, anna).addMessage("second", 2L, anna))
            .build()
        val reader = NotificationReader(context)
        assertEquals("second", reader.latestMessage(chat)?.text)
        val plain = NotificationCompat.Builder(context, "c").setSmallIcon(android.R.drawable.ic_dialog_info).setContentText("hello").build()
        assertNull(reader.latestMessage(plain))
    }

    // --- the notification of Android taken away, and kept for the Action Center

    @Test
    fun aHeldNotificationStaysInTheActionCenterUntilItIsDismissedOrPostedAgain() {
        val kept = entry("a", "kept", postTime = 100)
        NotificationHub.hold(kept)
        // Android no longer has it: the list the service reads does not, and the Action Center still shows it, newest first.
        NotificationHub.update(listOf(entry("b", "live", postTime = 200)))
        assertEquals(listOf("b", "a"), NotificationHub.entries.value.map { it.key })
        // Posted again: the live one takes its place.
        NotificationHub.update(listOf(entry("a", "again", postTime = 300)))
        assertEquals(listOf("again"), NotificationHub.entries.value.map { it.text })
        // Once the app's own is gone it is not brought back.
        NotificationHub.update(emptyList())
        assertTrue(NotificationHub.entries.value.isEmpty())
    }

    @Test
    fun dismissingOrClearingForgetsWhatWasHeld() {
        NotificationHub.hold(entry("a"))
        NotificationHub.hold(entry("b"))
        NotificationHub.update(emptyList())
        assertEquals(2, NotificationHub.entries.value.size)
        NotificationHub.dismiss("a")
        assertEquals(listOf("b"), NotificationHub.entries.value.map { it.key })
        NotificationHub.dismissAll()
        NotificationHub.update(emptyList())
        assertTrue(NotificationHub.entries.value.isEmpty())
    }

    @Test
    fun theSwitchIsOffByDefaultAndKept() {
        assertFalse(com.metrowin.launcher.data.LauncherSettings().hideSystemNotifications)
        val on = com.metrowin.launcher.data.LauncherSettings(hideSystemNotifications = true)
        assertTrue(com.metrowin.launcher.data.SettingsCodec.decode(com.metrowin.launcher.data.SettingsCodec.encode(on))!!.hideSystemNotifications)
    }

    @Test
    fun aQuietNotificationIsTakenFromAndroidToo() {
        // What the banner does not show (a silent channel, a locked screen) is still moved to the Action Center.
        assertTrue(NotificationHub.mayHide(ongoing = false, clearable = true, fromUs = false, category = null, fullScreen = false, hiddenFromCenter = false))
        assertTrue(NotificationHub.mayHide(false, true, false, android.app.Notification.CATEGORY_MESSAGE, false, false))
    }

    @Test
    fun whatMustStayInTheShadeStays() {
        fun may(ongoing: Boolean = false, clearable: Boolean = true, fromUs: Boolean = false, category: String? = null, fullScreen: Boolean = false, hidden: Boolean = false) =
            NotificationHub.mayHide(ongoing, clearable, fromUs, category, fullScreen, hidden)
        assertFalse("a music player is ongoing", may(ongoing = true))
        assertFalse("one that cannot be cleared", may(clearable = false))
        assertFalse("ours", may(fromUs = true))
        assertFalse("a call", may(category = android.app.Notification.CATEGORY_CALL))
        assertFalse("an alarm", may(category = android.app.Notification.CATEGORY_ALARM))
        assertFalse("a ringing call opens a full screen", may(fullScreen = true))
        assertFalse("an app kept out of the Action Center would be lost", may(hidden = true))
    }
}
