package com.metrowin.launcher.notifications

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/** One message of a conversation notification, as the app wrote it. */
@Immutable
data class MessageLine(val sender: String, val text: String, val time: Long = 0L)

/** What a media app is playing, read from its media session. */
@Immutable
data class NowPlaying(val title: String, val artist: String, val art: ImageBitmap?, val playing: Boolean)

/**
 * Everything the live tiles need to know about one notification that is up now, read by the notification service. The
 * pictures are small copies, made once, so the screens never decode anything.
 */
@Immutable
data class AppNotification(
    val key: String,
    val packageName: String,
    val postTime: Long,
    val title: String,
    val text: String,
    /** Ongoing: it does not go away by itself and cannot be swiped (music, a download, a navigation). */
    val ongoing: Boolean = false,
    /** The line that stands for a group of notifications of an app: the notifications of the group are used instead. */
    val groupSummary: Boolean = false,
    /** What the app says the notification is: "msg", "email", "social", "progress", "navigation", "call"... */
    val category: String? = null,
    /** The messages of a conversation, oldest first, when the app wrote it as a conversation. */
    val messages: List<MessageLine> = emptyList(),
    val conversationTitle: String? = null,
    /** The number the app put on it ("3 new"), or 0. */
    val number: Int = 0,
    /** How far along it is, 0 to 100, when it has a progress bar that knows. */
    val progress: Int? = null,
    /** The picture of who it is from (a contact, a profile), when there is one. */
    val avatar: ImageBitmap? = null,
    /** The big picture of the notification, when there is one. */
    val picture: ImageBitmap? = null,
    val playing: NowPlaying? = null,
)

/** One face of a live tile. */
sealed interface LiveCard {
    /** Someone wrote: the last message of a conversation. [more] is how many more of that conversation wait. */
    @Immutable
    data class Message(val sender: String, val text: String, val conversation: String?, val avatar: ImageBitmap?, val more: Int) : LiveCard

    /** Any other notification, said in a title and a line. [live] marks something going on now (a navigation, a call). */
    @Immutable
    data class Digest(val title: String, val text: String, val avatar: ImageBitmap? = null, val live: Boolean = false) : LiveCard

    /** A notification with a picture: the picture fills the tile, with its words under it. */
    @Immutable
    data class Picture(val picture: ImageBitmap, val caption: String) : LiveCard

    /** A download, an upload, a sync: [percent] is null when the app does not know how far along it is. */
    @Immutable
    data class Progress(val title: String, val percent: Int?) : LiveCard

    /** The music that is playing (or paused) in a media app. */
    @Immutable
    data class Playing(val title: String, val artist: String, val art: ImageBitmap?, val playing: Boolean) : LiveCard
}

/** What a tile of an app shows: its faces, newest first, and the number for its counter (0 when there is none to add). */
@Immutable
data class LiveTileInfo(val cards: List<LiveCard>, val unread: Int)

/** What the user lets the tiles show: each kind, the text of the messages, and the apps that show nothing. */
@Immutable
data class LiveCardOptions(
    val messages: Boolean = true,
    val pictures: Boolean = true,
    val playing: Boolean = true,
    val progress: Boolean = true,
    /** Say who wrote, but not what. */
    val hideText: Boolean = false,
    val offApps: Set<String> = emptySet(),
    val ownPackage: String = "",
)

/**
 * The rules of the live tiles of other apps: which notifications become which faces, and the number for the counter. Pure,
 * so it can be tested without a phone: the notification service reads the notifications, this decides what they say.
 */
object LiveCards {
    const val MAX_CARDS = 5

    /** What ongoing notifications of these categories are about: something going on now, worth a face. */
    private val liveCategories = setOf("navigation", "call", "stopwatch", "transport_ride", "workout", "location_sharing")

    fun build(notifications: List<AppNotification>, options: LiveCardOptions = LiveCardOptions()): Map<String, LiveTileInfo> {
        val byPackage = notifications
            .filter { it.packageName != options.ownPackage && it.packageName !in options.offApps }
            .groupBy { it.packageName }
        val result = LinkedHashMap<String, LiveTileInfo>()
        for ((pkg, all) in byPackage) {
            // The summary of a group stands for its notifications: leave it out when they are there, use it when alone.
            val children = all.filterNot { it.groupSummary }
            val used = (children.ifEmpty { all }).sortedByDescending { it.postTime }
            val cards = ArrayList<LiveCard>()
            var unread = 0
            var seenPlaying = false
            for (n in used) {
                val music = n.playing
                if (music != null) {
                    if (options.playing && !seenPlaying) {
                        seenPlaying = true
                        cards += LiveCard.Playing(music.title, music.artist, music.art, music.playing)
                    }
                    continue
                }
                if (n.ongoing) {
                    when {
                        n.progress != null || n.category == "progress" ->
                            if (options.progress) cards += LiveCard.Progress(n.title.ifBlank { n.text }, n.progress)
                        n.category in liveCategories && options.messages ->
                            cards += LiveCard.Digest(n.title, text(n.text, options), n.avatar, live = true)
                    }
                    continue
                }
                if (n.messages.isNotEmpty()) {
                    unread += n.messages.size
                    if (options.messages) {
                        val last = n.messages.last()
                        val picture = n.picture
                        if (picture != null && options.pictures) {
                            cards += LiveCard.Picture(picture, caption(last.sender, last.text, options))
                        } else {
                            cards += LiveCard.Message(
                                last.sender.ifBlank { n.title }, text(last.text, options),
                                n.conversationTitle?.takeIf { it.isNotBlank() && it != last.sender }, n.avatar, n.messages.size - 1,
                            )
                        }
                    }
                    continue
                }
                unread += n.number.coerceAtLeast(1)
                val picture = n.picture
                when {
                    picture != null && options.pictures -> cards += LiveCard.Picture(picture, caption(n.title, n.text, options))
                    options.messages && (n.title.isNotBlank() || n.text.isNotBlank()) ->
                        cards += LiveCard.Digest(n.title, text(n.text, options), n.avatar)
                }
            }
            val distinct = cards.distinctBy { c ->
                when (c) {
                    is LiveCard.Message -> Triple("m", c.sender, c.text)
                    is LiveCard.Digest -> Triple("d", c.title, c.text)
                    is LiveCard.Picture -> Triple("p", c.caption, c.picture)
                    is LiveCard.Progress -> Triple("g", c.title, c.percent)
                    is LiveCard.Playing -> Triple("n", c.title, c.artist)
                }
            }.take(MAX_CARDS)
            if (distinct.isNotEmpty() || unread > 0) result[pkg] = LiveTileInfo(distinct, unread)
        }
        return result
    }

    private fun text(value: String, options: LiveCardOptions) = if (options.hideText) "" else value

    private fun caption(title: String, text: String, options: LiveCardOptions): String =
        if (options.hideText) title else listOf(title, text).filter { it.isNotBlank() }.joinToString(": ")

    /** The music playing in any app, the most recent notification first, or null when nothing is. */
    fun nowPlaying(notifications: List<AppNotification>): NowPlaying? =
        notifications.sortedByDescending { it.postTime }.firstNotNullOfOrNull { it.playing }
}
