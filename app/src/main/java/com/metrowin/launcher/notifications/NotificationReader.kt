package com.metrowin.launcher.notifications

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale

/**
 * Reads what the live tiles need out of a [Notification]: the title and the line, the messages of a conversation, how far
 * a progress is, the picture of who it is from, the big picture, and the music a media session plays. Everything is
 * optional and everything is guarded: an app can put anything in a notification, and one that cannot be read is skipped,
 * never a crash. Pictures are shrunk once here ([AVATAR_PX], [PICTURE_PX]) so that the screens never decode anything.
 */
class NotificationReader(private val context: Context) {
    fun read(
        key: String,
        packageName: String,
        postTime: Long,
        notification: Notification,
        ongoing: Boolean,
        groupSummary: Boolean,
        /** False when the tiles will not show pictures: they are not decoded at all. */
        withPictures: Boolean = true,
    ): AppNotification {
        val extras = notification.extras ?: Bundle.EMPTY
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT) ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT))?.toString().orEmpty()
        val messages = messagesOf(extras)
        val playing = playingOf(extras, title, text)
        val progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val progress = if (progressMax > 0 && !extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)) {
            (extras.getInt(Notification.EXTRA_PROGRESS, 0) * 100 / progressMax).coerceIn(0, 100)
        } else {
            null
        }
        return AppNotification(
            key = key, packageName = packageName, postTime = postTime, title = title, text = text,
            ongoing = ongoing, groupSummary = groupSummary, category = notification.category,
            messages = messages.map { MessageLine(it.sender, it.text, it.time) },
            conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
            number = notification.number.coerceAtLeast(0),
            progress = progress,
            avatar = if (withPictures) avatarOf(notification, messages, extras) else null,
            picture = if (withPictures) pictureOf(extras) else null,
            playing = playing,
        )
    }

    private class Line(val sender: String, val text: String, val time: Long, val icon: Icon?)

    /** The newest message of a conversation notification, null for any other: the line to show for it, since its plain text can be an older one. */
    fun latestMessage(notification: Notification): MessageLine? =
        messagesOf(notification.extras ?: Bundle.EMPTY).lastOrNull()?.let { MessageLine(it.sender, it.text, it.time) }

    /**
     * The messages of a conversation notification (MessagingStyle), oldest first; empty for any other. Read from the bundles
     * by hand: the framework call that does it is public only from Android 11, and the keys have been the same since 7.
     */
    private fun messagesOf(extras: Bundle): List<Line> = runCatching {
        val raw = if (Build.VERSION.SDK_INT >= 33) {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        }
        raw.orEmpty().filterIsInstance<Bundle>().mapNotNull { message ->
            val body = message.getCharSequence(KEY_TEXT)?.toString().orEmpty()
            if (body.isBlank()) return@mapNotNull null
            val person = senderPerson(message)
            val name = person?.first ?: message.getCharSequence(KEY_SENDER)?.toString()
            Line(name.orEmpty(), body, message.getLong(KEY_TIME), person?.second)
        }
    }.getOrDefault(emptyList())

    /** The name and picture of the sender of a message, from the Person the framework writes, or the Bundle the compat library does. */
    private fun senderPerson(message: Bundle): Pair<String?, Icon?>? {
        val value: Any? = if (Build.VERSION.SDK_INT >= 33) {
            message.getParcelable(KEY_SENDER_PERSON, Parcelable::class.java)
        } else {
            @Suppress("DEPRECATION")
            message.getParcelable<Parcelable>(KEY_SENDER_PERSON)
        }
        return when {
            value is Bundle -> value.getCharSequence("name")?.toString() to legacyIcon(value)
            Build.VERSION.SDK_INT >= 28 && value is android.app.Person -> value.name?.toString() to value.icon
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyIcon(person: Bundle): Icon? =
        if (Build.VERSION.SDK_INT >= 33) person.getParcelable("icon", Icon::class.java) else person.getParcelable("icon") as? Icon

    /** Who it is from: the picture of the sender of the last message, or the large icon of the notification. */
    @Suppress("DEPRECATION")
    private fun avatarOf(notification: Notification, messages: List<Line>, extras: Bundle): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
        val icon = messages.lastOrNull()?.icon ?: notification.getLargeIcon()
        val bitmap = icon?.loadDrawable(context)?.toBitmap(AVATAR_PX, AVATAR_PX)
            ?: legacyBitmap(extras, Notification.EXTRA_LARGE_ICON)?.shrunk(AVATAR_PX)
        bitmap?.asImageBitmap()
    }.getOrNull()

    private fun pictureOf(extras: Bundle): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
        val fromIcon: Bitmap? = if (Build.VERSION.SDK_INT >= 31) {
            val icon = if (Build.VERSION.SDK_INT >= 33) {
                extras.getParcelable(Notification.EXTRA_PICTURE_ICON, Icon::class.java)
            } else {
                @Suppress("DEPRECATION")
                extras.getParcelable<Icon>(Notification.EXTRA_PICTURE_ICON)
            }
            icon?.loadDrawable(context)?.let { d -> d.toBitmap(PICTURE_PX, (PICTURE_PX * d.intrinsicHeight.coerceAtLeast(1) / d.intrinsicWidth.coerceAtLeast(1)).coerceIn(1, PICTURE_PX * 2)) }
        } else {
            null
        }
        (fromIcon ?: legacyBitmap(extras, Notification.EXTRA_PICTURE)?.shrunk(PICTURE_PX))?.asImageBitmap()
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun legacyBitmap(extras: Bundle, key: String): Bitmap? =
        if (Build.VERSION.SDK_INT >= 33) extras.getParcelable(key, Bitmap::class.java) else extras.getParcelable(key) as? Bitmap

    /** The music of the media session the notification belongs to, or null when it has none. */
    private fun playingOf(extras: Bundle, title: String, text: String): NowPlaying? = runCatching {
        val token = if (Build.VERSION.SDK_INT >= 33) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
        } ?: return null
        val controller = MediaController(context, token)
        val meta = controller.metadata
        nowPlayingFrom(
            metaTitle = meta?.getString(MediaMetadata.METADATA_KEY_TITLE),
            metaArtist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            art = (meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON))?.shrunk(ART_PX)?.asImageBitmap(),
            playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING,
            title = title, text = text,
        )
    }.getOrNull()

    private fun Bitmap.shrunk(side: Int): Bitmap {
        val longest = maxOf(width, height)
        if (longest <= side) return this
        val scale = side.toFloat() / longest
        return scale(maxOf(1, (width * scale).toInt()), maxOf(1, (height * scale).toInt()), true)
    }

    companion object {
        private const val KEY_TEXT = "text"
        private const val KEY_TIME = "time"
        private const val KEY_SENDER = "sender"
        private const val KEY_SENDER_PERSON = "sender_person"

        /**
         * The music as the tile says it: what the media session says, and where it says nothing the words of the notification
         * (many apps put the song and the artist there). Null when there is neither a name nor an artist to show.
         */
        fun nowPlayingFrom(metaTitle: String?, metaArtist: String?, art: androidx.compose.ui.graphics.ImageBitmap?, playing: Boolean, title: String, text: String): NowPlaying? {
            val name = metaTitle?.takeIf { it.isNotBlank() } ?: title
            val artist = metaArtist?.takeIf { it.isNotBlank() } ?: text
            if (name.isBlank() && artist.isBlank()) return null
            return NowPlaying(name, artist, art, playing)
        }

        const val AVATAR_PX = 96
        const val ART_PX = 192
        const val PICTURE_PX = 320
    }
}
