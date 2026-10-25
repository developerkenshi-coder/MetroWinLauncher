package com.metrowin.launcher

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.notifications.NotificationEntry
import com.metrowin.launcher.notifications.NotificationHub
import com.metrowin.launcher.notifications.ReplyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The answer reaches the app the way the system shade gives it: as the results of the action's remote input. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationReplyTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun entryWith(reply: ReplyAction?) =
        NotificationEntry("k", "app.chat", "Giulia", "ciao", postTime = 0, contentIntent = null, autoCancel = false, reply = reply)

    private fun replyAction(vararg keys: String): ReplyAction {
        val inputs = keys.map { RemoteInput.Builder(it).setLabel(it).build() }
        val pending = PendingIntent.getBroadcast(
            context, 0, Intent("test.REPLY").setPackage(context.packageName), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return ReplyAction(pending, inputs.first(), inputs)
    }

    private fun capture(): MutableList<Intent> {
        val received = mutableListOf<Intent>()
        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    received += intent
                }
            },
            IntentFilter("test.REPLY"), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return received
    }

    @Test
    fun theTypedTextArrivesAsTheResultOfTheRemoteInput() {
        val received = capture()
        val action = replyAction("reply_text")
        assertTrue(NotificationHub.reply(context, entryWith(action), "arrivo tra cinque minuti"))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, received.size)
        val results = RemoteInput.getResultsFromIntent(received.single())
        assertEquals("arrivo tra cinque minuti", results.getCharSequence("reply_text").toString())
    }

    @Test
    fun otherInputsOfTheActionAreFilledToo() {
        val received = capture()
        assertTrue(NotificationHub.reply(context, entryWith(replyAction("text", "extra")), "hi"))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val results = RemoteInput.getResultsFromIntent(received.single())
        assertEquals("hi", results.getCharSequence("text").toString())
        assertEquals("", results.getCharSequence("extra").toString())
    }

    @Test
    fun aNotificationThatCannotBeAnsweredIsNotSent() {
        val received = capture()
        assertFalse(NotificationHub.reply(context, entryWith(null), "hi"))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(received.isEmpty())
    }

    @Test
    fun anEmptyAnswerIsNotSent() {
        val received = capture()
        assertFalse(NotificationHub.reply(context, entryWith(replyAction("t")), "   "))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(received.isEmpty())
        assertNull(RemoteInput.getResultsFromIntent(Intent()))
    }

    @Test
    fun aCancelledIntentIsAFailureNotACrash() {
        val action = replyAction("t")
        action.intent.cancel()
        assertFalse(NotificationHub.reply(context, entryWith(action), "hi"))
    }
}
