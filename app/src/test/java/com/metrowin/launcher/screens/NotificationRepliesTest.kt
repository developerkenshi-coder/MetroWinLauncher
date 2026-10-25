package com.metrowin.launcher.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.notifications.NotificationEntry
import com.metrowin.launcher.notifications.ReplyAction
import com.metrowin.launcher.ui.actioncenter.ActionCenter
import com.metrowin.launcher.ui.gesture.rememberPullPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class NotificationRepliesTest : ScreenshotBase() {
    private val replies = mutableListOf<Pair<String, String>>()
    private val dismissed = mutableListOf<String>()
    private var accept = true

    private fun reply() = ReplyAction(
        PendingIntent.getBroadcast(context, 0, Intent("x"), PendingIntent.FLAG_IMMUTABLE),
        RemoteInput.Builder("t").build(), listOf(RemoteInput.Builder("t").build()),
    )

    private fun note(key: String, pkg: String, answerable: Boolean = false) = NotificationEntry(
        key, pkg, "Title $key", "Text $key", postTime = System.currentTimeMillis() - 60_000, contentIntent = null,
        autoCancel = false, reply = if (answerable) reply() else null,
    )

    @Composable
    private fun Center(list: List<NotificationEntry>, grouped: Boolean, canReply: Boolean) {
        ActionCenter(
            slots = listOf(QuickAction.Wifi, QuickAction.Bluetooth, QuickAction.Airplane, QuickAction.Flashlight),
            states = emptyMap(), notifications = list, appsByPackage = emptyMap(), hasNotificationAccess = true,
            panel = rememberPullPanel(initialProgress = 1f),
            onAction = {}, onAllSettings = {}, onOpenNotification = {}, onDismissNotification = { dismissed += it.key },
            onClearAll = {}, onTurnOnNotificationAccess = {}, onClose = {},
            onReply = if (canReply) ({ entry, text -> replies += entry.key to text; accept }) else null,
            groupConversations = grouped,
        )
    }

    @Test
    fun aNotificationThatCanBeAnsweredHasAReplyLinkThatOpensAField() {
        show { Center(listOf(note("n1", "app.chat", answerable = true), note("n2", "app.mail")), grouped = false, canReply = true) }
        advance(300)
        capture("notification_reply_link")
        compose.onNodeWithTag("notification:reply:n1").assertExists()
        compose.onAllNodesWithTag("notification:reply:n2").assertCountEquals(0)
        compose.onNodeWithTag("notification:reply:n1").performClick()
        advance(200)
        compose.onNodeWithTag("notification:replyfield:n1").assertExists()
        capture("notification_reply_field")
    }

    @Test
    fun theTypedAnswerIsSentAndTheFieldCloses() {
        show { Center(listOf(note("n1", "app.chat", answerable = true)), grouped = false, canReply = true) }
        advance(300)
        compose.onNodeWithTag("notification:reply:n1").performClick()
        advance(200)
        compose.onNode(hasSetTextAction()).performTextInput("arrivo")
        advance(100)
        compose.onNodeWithTag("notification:send:n1").performClick()
        advance(200)
        assertEquals(listOf("n1" to "arrivo"), replies)
        compose.onAllNodesWithTag("notification:replyfield:n1").assertCountEquals(0)
        compose.onNodeWithTag("notification:reply:n1").assertExists()
    }

    @Test
    fun anAnswerTheAppRefusesStaysInTheFieldWithAMessage() {
        accept = false
        show { Center(listOf(note("n1", "app.chat", answerable = true)), grouped = false, canReply = true) }
        advance(300)
        compose.onNodeWithTag("notification:reply:n1").performClick()
        advance(200)
        compose.onNode(hasSetTextAction()).performTextInput("ciao")
        advance(100)
        compose.onNodeWithTag("notification:send:n1").performClick()
        advance(200)
        compose.onNodeWithTag("notification:replyfailed:n1").assertExists()
        compose.onNodeWithTag("notification:replyfield:n1").assertExists()
    }

    @Test
    fun withTheSettingOffThereIsNoReplyLink() {
        show { Center(listOf(note("n1", "app.chat", answerable = true)), grouped = false, canReply = false) }
        advance(300)
        compose.onAllNodesWithTag("notification:reply:n1").assertCountEquals(0)
    }

    @Test
    fun anEmptyAnswerIsNotSent() {
        show { Center(listOf(note("n1", "app.chat", answerable = true)), grouped = false, canReply = true) }
        advance(300)
        compose.onNodeWithTag("notification:reply:n1").performClick()
        advance(200)
        compose.onNodeWithTag("notification:send:n1").performClick()
        advance(200)
        assertTrue(replies.isEmpty())
    }

    // --- grouping

    private val several = listOf(note("a2", "app.a"), note("b1", "app.b"), note("a1", "app.a"))

    @Test
    fun anAppWithSeveralNotificationsIsOneLineThatOpens() {
        show { Center(several, grouped = true, canReply = false) }
        advance(300)
        capture("notification_group_closed")
        compose.onNodeWithTag("notification:group:app.a").assertExists()
        compose.onNodeWithTag("notification:group:count:app.a", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithTag("notification:a2").assertCountEquals(0)
        compose.onNodeWithTag("notification:b1").assertExists()
        compose.onNodeWithTag("notification:group:app.a").performClick()
        advance(300)
        capture("notification_group_open")
        compose.onNodeWithTag("notification:a2").assertExists()
        compose.onNodeWithTag("notification:a1").assertExists()
        compose.onNodeWithTag("notification:group:app.a").performClick()
        advance(300)
        compose.onAllNodesWithTag("notification:a2").assertCountEquals(0)
    }

    @Test
    fun withoutGroupingEveryNotificationShows() {
        show { Center(several, grouped = false, canReply = false) }
        advance(300)
        listOf("a2", "b1", "a1").forEach { compose.onNodeWithTag("notification:$it").assertExists() }
        compose.onAllNodesWithTag("notification:group:app.a").assertCountEquals(0)
    }

    @Test
    fun swipingAGroupAwayClearsAllOfItsNotifications() {
        show { Center(several, grouped = true, canReply = false) }
        advance(300)
        compose.onNodeWithTag("notification:group:app.a").performTouchInput { swipeRight() }
        advance(1_000)
        assertEquals(listOf("a2", "a1"), dismissed)
    }
}
