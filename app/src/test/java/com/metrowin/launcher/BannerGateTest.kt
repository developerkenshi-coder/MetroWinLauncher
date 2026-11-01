package com.metrowin.launcher

import com.metrowin.launcher.notifications.BannerGate
import com.metrowin.launcher.notifications.NotificationEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BannerGateTest {
    private fun entry(text: String, postTime: Long, key: String = "k") =
        NotificationEntry(key, "chat", "Anna", text, postTime, contentIntent = null, autoCancel = false)

    @Test
    fun anAppPostingAgainAtOnceWithTheSameWordsIsNotNews() {
        val seen = BannerGate.seen(listOf(entry("hi", 1_000)))
        assertFalse(BannerGate.isNew(seen, entry("hi", 1_000)))
        assertFalse("the app's repost after the launcher took the notification away", BannerGate.isNew(seen, entry("hi", 1_400)))
    }

    @Test
    fun theSameWordsLaterOrOtherWordsOrAnotherChatAreNews() {
        val seen = BannerGate.seen(listOf(entry("hi", 1_000)))
        assertTrue(BannerGate.isNew(seen, entry("hi", 1_000 + BannerGate.REPOST_MS)))
        assertTrue(BannerGate.isNew(seen, entry("again", 1_200)))
        assertTrue(BannerGate.isNew(seen, entry("hi", 1_200, key = "other")))
        assertTrue(BannerGate.same(null, entry("hi", 1_000)).not())
    }
}
