package com.metrowin.launcher

import com.metrowin.launcher.notifications.AppNotification
import com.metrowin.launcher.notifications.LiveCard
import com.metrowin.launcher.notifications.LiveCardOptions
import com.metrowin.launcher.notifications.LiveCards
import com.metrowin.launcher.notifications.MessageLine
import com.metrowin.launcher.notifications.NowPlaying
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveCardsTest {
    private val whatsapp = "com.whatsapp"
    private val telegram = "org.telegram.messenger"
    private val instagram = "com.instagram.android"
    private val reddit = "com.reddit.frontpage"
    private val spotify = "com.spotify.music"

    private fun n(
        key: String, pkg: String, time: Long = 1_000, title: String = "", text: String = "",
        messages: List<MessageLine> = emptyList(), conversation: String? = null, number: Int = 0, ongoing: Boolean = false,
        summary: Boolean = false, category: String? = null, progress: Int? = null, playing: NowPlaying? = null,
    ) = AppNotification(
        key, pkg, time, title, text, ongoing = ongoing, groupSummary = summary, category = category, messages = messages,
        conversationTitle = conversation, number = number, progress = progress, playing = playing,
    )

    private fun lines(vararg l: Pair<String, String>) = l.mapIndexed { i, (who, what) -> MessageLine(who, what, i.toLong()) }

    // --- messages

    @Test
    fun aConversationBecomesTheLastMessageAndCountsAllItsMessages() {
        val info = LiveCards.build(
            listOf(n("1", whatsapp, title = "Giulia", messages = lines("Giulia" to "Ciao", "Giulia" to "Ci vediamo alle otto?"))),
        )[whatsapp]!!
        assertEquals(3 - 1, info.unread)
        val card = info.cards.single() as LiveCard.Message
        assertEquals("Giulia", card.sender)
        assertEquals("Ci vediamo alle otto?", card.text)
        assertEquals("one more message waits in the same chat", 1, card.more)
        assertNull("the chat has no name of its own beside its sender", card.conversation)
    }

    @Test
    fun aGroupChatKeepsItsNameAndTheSenderOfTheLastMessage() {
        val card = LiveCards.build(
            listOf(n("1", telegram, title = "Famiglia", conversation = "Famiglia", messages = lines("Anna" to "Pronti?", "Marco" to "Arrivo"))),
        )[telegram]!!.cards.single() as LiveCard.Message
        assertEquals("Marco", card.sender)
        assertEquals("Famiglia", card.conversation)
    }

    @Test
    fun severalChatsAreSeveralFacesNewestFirstAndTheirMessagesAreAdded() {
        val info = LiveCards.build(
            listOf(
                n("1", whatsapp, time = 100, title = "A", messages = lines("A" to "uno")),
                n("2", whatsapp, time = 300, title = "B", messages = lines("B" to "due", "B" to "tre")),
                n("3", whatsapp, time = 200, title = "C", messages = lines("C" to "quattro")),
            ),
        )[whatsapp]!!
        assertEquals(listOf("B", "C", "A"), info.cards.map { (it as LiveCard.Message).sender })
        assertEquals(4, info.unread)
    }

    @Test
    fun theSummaryOfAGroupStandsAsideWhenItsNotificationsAreThere() {
        val info = LiveCards.build(
            listOf(
                n("s", whatsapp, time = 500, title = "WhatsApp", text = "3 messages from 2 chats", summary = true, number = 3),
                n("1", whatsapp, time = 100, title = "A", messages = lines("A" to "uno")),
            ),
        )[whatsapp]!!
        assertEquals(1, info.cards.size)
        assertEquals(1, info.unread)
    }

    @Test
    fun aSummaryAloneIsUsed() {
        val info = LiveCards.build(listOf(n("s", whatsapp, title = "WhatsApp", text = "3 new messages", summary = true, number = 3)))[whatsapp]!!
        assertEquals(LiveCard.Digest("WhatsApp", "3 new messages"), info.cards.single())
        assertEquals(3, info.unread)
    }

    // --- other apps

    @Test
    fun aSocialNotificationIsADigestAndCountsOne() {
        val info = LiveCards.build(
            listOf(
                n("1", instagram, time = 10, title = "marco", text = "liked your photo"),
                n("2", reddit, time = 20, title = "u/anna", text = "replied to your comment", number = 2),
            ),
        )
        assertEquals(LiveCard.Digest("marco", "liked your photo"), info[instagram]!!.cards.single())
        assertEquals(1, info[instagram]!!.unread)
        assertEquals(LiveCard.Digest("u/anna", "replied to your comment"), info[reddit]!!.cards.single())
        assertEquals("the number the app gave", 2, info[reddit]!!.unread)
    }

    @Test
    fun theSameThingTwiceIsOneFace() {
        val info = LiveCards.build(
            listOf(n("1", instagram, time = 10, title = "marco", text = "liked"), n("2", instagram, time = 20, title = "marco", text = "liked")),
        )[instagram]!!
        assertEquals(1, info.cards.size)
        assertEquals(2, info.unread)
    }

    @Test
    fun noMoreThanFiveFaces() {
        val info = LiveCards.build((1..9).map { n("$it", instagram, time = it.toLong(), title = "u$it", text = "x") })[instagram]!!
        assertEquals(LiveCards.MAX_CARDS, info.cards.size)
        assertEquals("u9", (info.cards.first() as LiveCard.Digest).title)
        assertEquals(9, info.unread)
    }

    @Test
    fun anEmptyNotificationHasNothingToShowButStillCounts() {
        val info = LiveCards.build(listOf(n("1", instagram)))[instagram]!!
        assertTrue(info.cards.isEmpty())
        assertEquals(1, info.unread)
    }

    // --- ongoing: music, progress, something going on

    private val song = NowPlaying("Song", "Artist", null, playing = true)

    @Test
    fun theMusicThatPlaysIsAFaceAndNotAnUnreadNotification() {
        val info = LiveCards.build(listOf(n("1", spotify, ongoing = true, title = "Song", text = "Artist", playing = song)))[spotify]!!
        assertEquals(LiveCard.Playing("Song", "Artist", null, true), info.cards.single())
        assertEquals(0, info.unread)
    }

    @Test
    fun onlyOneMusicFacePerApp() {
        val info = LiveCards.build(
            listOf(n("1", spotify, time = 1, ongoing = true, playing = song), n("2", spotify, time = 2, ongoing = true, playing = song.copy(title = "Other"))),
        )[spotify]!!
        assertEquals("Other", (info.cards.single() as LiveCard.Playing).title)
    }

    @Test
    fun aDownloadIsAProgressFaceWithItsPercent() {
        val info = LiveCards.build(listOf(n("1", "com.android.chrome", ongoing = true, title = "film.mp4", progress = 45, category = "progress")))
        assertEquals(LiveCard.Progress("film.mp4", 45), info["com.android.chrome"]!!.cards.single())
        assertEquals(0, info["com.android.chrome"]!!.unread)
    }

    @Test
    fun aProgressThatDoesNotKnowHowFarItIsHasNoPercent() {
        val info = LiveCards.build(listOf(n("1", "com.x", ongoing = true, title = "Syncing", category = "progress")))["com.x"]!!
        assertEquals(LiveCard.Progress("Syncing", null), info.cards.single())
    }

    @Test
    fun aNavigationOrACallThatIsGoingOnIsAFaceMarkedLive() {
        val info = LiveCards.build(listOf(n("1", "com.google.android.apps.maps", ongoing = true, title = "5 min", text = "Turn left onto Via Roma", category = "navigation")))
        assertEquals(LiveCard.Digest("5 min", "Turn left onto Via Roma", live = true), info["com.google.android.apps.maps"]!!.cards.single())
    }

    @Test
    fun otherOngoingNotificationsAreLeftAlone() {
        // "App is running" and the like say nothing worth a tile face.
        assertTrue(LiveCards.build(listOf(n("1", "com.x", ongoing = true, title = "Running in the background")))["com.x"] == null)
    }

    // --- what the user lets show

    @Test
    fun hidingTheTextKeepsWhoWroteButNotWhat() {
        val options = LiveCardOptions(hideText = true)
        val msg = LiveCards.build(listOf(n("1", whatsapp, title = "Giulia", messages = lines("Giulia" to "Segreto"))), options)[whatsapp]!!.cards.single() as LiveCard.Message
        assertEquals("Giulia", msg.sender)
        assertEquals("", msg.text)
        val digest = LiveCards.build(listOf(n("2", reddit, title = "u/anna", text = "private words")), options)[reddit]!!.cards.single() as LiveCard.Digest
        assertEquals("", digest.text)
    }

    @Test
    fun turningMessagesOffLeavesTheCounterButNoFaces() {
        val info = LiveCards.build(
            listOf(n("1", whatsapp, title = "A", messages = lines("A" to "uno", "A" to "due"))),
            LiveCardOptions(messages = false),
        )[whatsapp]!!
        assertTrue(info.cards.isEmpty())
        assertEquals(2, info.unread)
    }

    @Test
    fun anAppTheUserSwitchedOffShowsNothingAtAll() {
        val all = listOf(n("1", whatsapp, title = "A", messages = lines("A" to "uno")), n("2", reddit, title = "x", text = "y"))
        val info = LiveCards.build(all, LiveCardOptions(offApps = setOf(whatsapp)))
        assertNull(info[whatsapp])
        assertTrue(info[reddit] != null)
    }

    @Test
    fun theLauncherDoesNotLookAtItsOwnNotifications() {
        val info = LiveCards.build(listOf(n("1", "com.metrowin.launcher", title = "a", text = "b")), LiveCardOptions(ownPackage = "com.metrowin.launcher"))
        assertTrue(info.isEmpty())
    }

    @Test
    fun theMusicAndProgressSwitchesWork() {
        val all = listOf(
            n("1", spotify, ongoing = true, playing = song),
            n("2", "com.dl", ongoing = true, title = "file", progress = 10, category = "progress"),
        )
        val off = LiveCards.build(all, LiveCardOptions(playing = false, progress = false))
        assertTrue(off.values.all { it.cards.isEmpty() })
        assertEquals(setOf(spotify, "com.dl"), LiveCards.build(all).keys)
    }

    @Test
    fun theMusicOfAnyAppIsFoundForTheMusicTile() {
        val all = listOf(n("1", reddit, time = 5, title = "x"), n("2", spotify, time = 1, ongoing = true, playing = song), n("3", "com.yt", time = 3, ongoing = true, playing = song.copy(title = "Newer")))
        assertEquals("Newer", LiveCards.nowPlaying(all)!!.title)
        assertNull(LiveCards.nowPlaying(listOf(n("1", reddit))))
    }
}
