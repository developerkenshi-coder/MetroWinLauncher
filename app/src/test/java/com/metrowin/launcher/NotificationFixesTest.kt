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
    fun clean() {
        NotificationHub.dismissAll()
        // Android no longer lists what was cleared: the notes about it are let go.
        NotificationHub.update(emptyList())
    }

    // --- a chat posts its notification again at each message

    @Test
    fun aNewMessageInTheSameChatIsNewsEvenWithTheSameKey() {
        val first = entry("k", "first", postTime = 100)
        val seen = BannerGate.seen(listOf(first))
        assertFalse("the same post is not news", BannerGate.isNew(seen, first))
        assertTrue("another text", BannerGate.isNew(seen, entry("k", "second", postTime = 200)))
        assertTrue("the same text said again later", BannerGate.isNew(seen, entry("k", "first", postTime = 100 + BannerGate.REPOST_MS)))
        assertFalse("posted again at once by the app", BannerGate.isNew(seen, entry("k", "first", postTime = 300)))
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
    fun aHeldNotificationStaysInTheActionCenterUntilTheUserDismissesItOrItIsPostedAgain() {
        val kept = entry("a", "kept", postTime = 100)
        NotificationHub.hold(kept)
        // Android no longer has it: the list the service reads does not, and the Action Center still shows it, newest first.
        NotificationHub.update(listOf(entry("b", "live", postTime = 200)))
        assertEquals(listOf("b", "a"), NotificationHub.entries.value.map { it.key })
        // Posted again: the live one takes its place.
        NotificationHub.update(listOf(entry("a", "again", postTime = 300)))
        assertEquals(listOf("again", "live"), NotificationHub.entries.value.map { it.text })
        // Once the app's own is gone the copies stay (the live "b" was written down too): only the user taking them away ends them.
        NotificationHub.update(emptyList())
        assertEquals(listOf("again", "live"), NotificationHub.entries.value.map { it.text })
        NotificationHub.dismiss("a")
        NotificationHub.dismiss("b")
        assertTrue(NotificationHub.entries.value.isEmpty())
    }

    @Test
    fun aDismissedNotificationAndroidStillListsIsNotReadBackButANewerPostOfItIs() {
        NotificationHub.update(listOf(entry("a", "old", postTime = 100)))
        NotificationHub.dismiss("a")
        assertTrue(NotificationHub.entries.value.isEmpty())
        // The service read again before Android finished the cancel: still the same post.
        NotificationHub.update(listOf(entry("a", "old", postTime = 100)))
        assertTrue("it is not back", NotificationHub.entries.value.isEmpty())
        assertTrue("and it was not written down again", NotificationHub.heldKeys().isEmpty())
        // The same chat posts again.
        NotificationHub.update(listOf(entry("a", "new", postTime = 200)))
        assertEquals(listOf("new"), NotificationHub.entries.value.map { it.text })
        // Android let go of it: a post of the same key and time (cannot happen, but) is then judged as new news.
        NotificationHub.update(emptyList())
        NotificationHub.dismiss("a")
        NotificationHub.update(emptyList())
        NotificationHub.update(listOf(entry("a", "old", postTime = 100)))
        assertEquals(listOf("old"), NotificationHub.entries.value.map { it.text })
    }

    @Test
    fun clearingAllIsNotUndoneByARead() {
        NotificationHub.update(listOf(entry("a"), entry("b")))
        NotificationHub.dismissAll()
        NotificationHub.update(listOf(entry("a"), entry("b")))
        assertTrue(NotificationHub.entries.value.isEmpty())
    }

    @Test
    fun aHeldNotificationTheAppTakesBackStaysAndOneTheUserSwipesAwayInAndroidGoes() {
        NotificationHub.hold(entry("a"))
        NotificationHub.hold(entry("b"))
        NotificationHub.update(emptyList())
        // The app took "a" back (reason 8): nothing happens to the copy. The user swiped "b" in the shade of Android (reason 2): it goes.
        assertFalse(NotificationHub.removedByUser(android.service.notification.NotificationListenerService.REASON_APP_CANCEL))
        assertTrue(NotificationHub.removedByUser(android.service.notification.NotificationListenerService.REASON_CANCEL))
        NotificationHub.forget("b")
        NotificationHub.update(emptyList())
        assertEquals(listOf("a"), NotificationHub.entries.value.map { it.key })
        assertEquals(mapOf("chat" to 1), NotificationHub.countsByPackage(NotificationHub.entries.value))
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
    fun aMessageOrMailOfTheLauncherIsNewsButItsOtherNoticesAreNot() {
        assertFalse(NotificationHub.isOwn("l", "l", com.metrowin.launcher.messages.SmsReceiver.CHANNEL))
        assertFalse(NotificationHub.isOwn("l", "l", "mail_new"))
        for (channel in listOf("data_limit", "timer", "reminder")) assertFalse(channel, NotificationHub.isOwn("l", "l", channel))
        assertTrue("a quiet notice", NotificationHub.isOwn("l", "l", "update"))
        assertTrue(NotificationHub.isOwn("l", "l", "incall_ongoing"))
        assertTrue(NotificationHub.isOwn("l", "l", null))
        assertFalse(NotificationHub.isOwn("other", "l", "incall_ongoing"))
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

    // --- the sound of a notification that comes with the screen off

    @Test
    fun `a notification stays in Android while the screen is off or locked so its sound is not cut`() {
        assertTrue("screen off", NotificationHub.keepForSound(interactive = false, locked = false))
        assertTrue("locked", NotificationHub.keepForSound(interactive = true, locked = true))
        assertFalse("screen on and unlocked", NotificationHub.keepForSound(interactive = true, locked = false))
    }
}
