package com.metrowin.launcher.ui.start

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.notifications.LiveCard
import com.metrowin.launcher.notifications.LiveCards
import com.metrowin.launcher.ui.anim.FlipContainer
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalMetroColors











/**
 * The tile of an app with something going on: its usual face, then one face for each thing (a conversation, a picture, the
 * song, a download), turning over one after the other.
 */
@Composable
internal fun CardsFace(item: AppItem, size: TileSize, width: Dp, height: Dp, cards: List<LiveCard>, seed: Int) {
    // The list of everything waiting comes first and does not count among the faces of the app.
    val all = cards.filterIsInstance<LiveCard.Inbox>() + cards.filter { it !is LiveCard.Inbox }.take(LiveCards.MAX_CARDS)
    // A small tile has room for who wrote and a few words: the messages, the notifications and the pictures, not the list or the songs.
    val shown = if (size == TileSize.Small) all.filter { it is LiveCard.Message || it is LiveCard.Digest || it is LiveCard.Picture } else all
    // What the app has to say comes first, as on the phone: the tile shows the newest thing at once, and again whenever something new comes in.
    // Two or more things to say (WhatsApp with two chats): only the inside turns over, the name of the app stays where it is.
    // One thing: the whole face turns, name included, as before.
    val inner = shown.size >= 2 && size != TileSize.Small
    Box(Modifier.fillMaxSize()) {
        FlipContainer(1 + shown.size, seed, startFace = if (shown.isNotEmpty()) 1 else 0, resetKey = shown.firstOrNull()) { face ->
            if (face == 0) {
                StaticFace(item, size, width, height, label = !inner)
            } else {
                Box(Modifier.fillMaxSize().testTag("card:${shown[face - 1]::class.simpleName}")) {
                    when (val card = shown[face - 1]) {
                        is LiveCard.Message -> if (size == TileSize.Small) SmallCard(card.sender, card.text) else MessageCard(card, size)
                        is LiveCard.Digest -> if (size == TileSize.Small) SmallCard(card.title, card.text) else DigestCard(card)
                        is LiveCard.Picture -> PictureCard(card)
                        is LiveCard.Progress -> ProgressCard(card, width)
                        is LiveCard.Playing -> PlayingCard(card, size, height)
                        is LiveCard.Inbox -> InboxCard(card, height)
                    }
                    if (size != TileSize.Small && !inner) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
                }
            }
        }
        if (inner) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}


/** What a small tile says of a notification: who, in a line, and the first words of it. */
@Composable
internal fun SmallCard(title: String, text: String) {
    val ink = LocalMetroColors.current.tileContent
    Column(Modifier.fillMaxSize().padding(start = 7.dp, top = 6.dp, end = 17.dp, bottom = 5.dp)) {
        MetroText(title, size = 13.sp, maxLines = 1)
        if (text.isNotBlank()) {
            MetroText(text, size = 11.sp, weight = FontWeight.Normal, maxLines = 5, lineHeight = 14.sp, color = ink.copy(alpha = 0.9f), modifier = Modifier.padding(top = 2.dp))
        }
    }
}


/** The notifications waiting in the app, newest first: who, when, and the first line of what they said, as many as the tile holds. */
@Composable
internal fun InboxCard(card: LiveCard.Inbox, height: Dp) {
    val ink = LocalMetroColors.current.tileContent
    val fit = ((height - INBOX_LABEL) / INBOX_ROW).toInt().coerceIn(1, LiveCards.MAX_ROWS)
    Column(Modifier.fillMaxSize().padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = INBOX_LABEL), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        card.rows.take(fit).forEach { row ->
            Column(Modifier.testTag("card:row")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetroText(row.sender, size = 16.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    MetroText(whenText(row.time), size = 11.sp, weight = FontWeight.Normal, color = ink.copy(alpha = 0.75f), maxLines = 1, modifier = Modifier.padding(start = 6.dp))
                }
                if (row.text.isNotBlank()) MetroText(row.text, size = 13.sp, weight = FontWeight.Normal, maxLines = 1, color = ink.copy(alpha = 0.85f))
            }
        }
    }
}


internal val INBOX_ROW = 40.dp


internal val INBOX_LABEL = 28.dp


/** Someone wrote: their picture (when there is one), their name, the chat it came in, and the last line. */
@Composable
internal fun MessageCard(card: LiveCard.Message, size: TileSize) {
    val ink = LocalMetroColors.current.tileContent
    Box(Modifier.fillMaxSize()) {
    if (card.more > 0) {
        MetroText(
            "+${card.more}", size = 15.sp, color = ink.copy(alpha = 0.9f),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 10.dp).testTag("card:more"),
        )
    }
    Row(Modifier.fillMaxSize().padding(start = 10.dp, top = 10.dp, end = if (card.more > 0) 38.dp else 10.dp, bottom = 26.dp)) {
        card.avatar?.let { avatar ->
            val side = if (size == TileSize.Wide) 64.dp else 44.dp
            Image(avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(side).testTag("card:avatar"))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            MetroText(card.sender, size = 20.sp, maxLines = 1)
            card.conversation?.let { chat ->
                MetroText(chat, size = 13.sp, weight = FontWeight.Normal, maxLines = 1, color = ink.copy(alpha = 0.75f))
            }
            if (card.text.isNotBlank()) {
                MetroText(
                    card.text, size = 15.sp, weight = FontWeight.Normal, maxLines = 3, lineHeight = 19.sp,
                    color = ink.copy(alpha = 0.9f), modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
    }
}


/** A notification said in a title and a line, with the picture of who it is from. A dot says it is going on now. */
@Composable
internal fun DigestCard(card: LiveCard.Digest) {
    val ink = LocalMetroColors.current.tileContent
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 10.dp, end = 10.dp)) {
            card.avatar?.let { avatar ->
                Image(avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp).testTag("card:avatar"))
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                if (card.title.isNotBlank()) MetroText(card.title, size = 20.sp, maxLines = 2, lineHeight = 24.sp)
                if (card.text.isNotBlank()) {
                    MetroText(
                        card.text, size = 15.sp, weight = FontWeight.Normal, maxLines = 3, lineHeight = 19.sp,
                        color = ink.copy(alpha = 0.85f), modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
        if (card.live) Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(9.dp).background(ink, CircleShape).testTag("card:live"))
    }
}


/** A notification with a picture: the picture fills the tile, with its words in a shade at the bottom. */
@Composable
internal fun PictureCard(card: LiveCard.Picture) {
    Box(Modifier.fillMaxSize()) {
        Image(card.picture, contentDescription = null, contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low, modifier = Modifier.fillMaxSize())
        if (card.caption.isNotBlank()) {
            // Its words sit in a shade over the picture, clear of the name of the app at the bottom.
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                    .padding(start = 9.dp, end = 9.dp, top = 22.dp, bottom = 28.dp),
            ) {
                MetroText(card.caption, size = 14.sp, weight = FontWeight.Normal, maxLines = 2, lineHeight = 18.sp, color = Color.White)
            }
        }
    }
}


/** A download, an upload: what it is and how far along, as a bar and a number. */
@Composable
internal fun ProgressCard(card: LiveCard.Progress, width: Dp) {
    val ink = LocalMetroColors.current.tileContent
    Column(Modifier.fillMaxSize().padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 32.dp)) {
        MetroText(card.title, size = 18.sp, maxLines = 2, lineHeight = 22.sp)
        Spacer(Modifier.weight(1f))
        if (card.percent != null) {
            MetroText("${card.percent}%", size = 34.sp)
            BatteryBar(card.percent, width * 0.7f)
        } else {
            MetroText("…", size = 34.sp, color = ink.copy(alpha = 0.85f))
        }
    }
}


/** The song: its cover (when there is one), its name and its artist, and whether it plays. */
@Composable
internal fun PlayingCard(card: LiveCard.Playing, size: TileSize, height: Dp) {
    val ink = LocalMetroColors.current.tileContent
    Row(Modifier.fillMaxSize().padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 26.dp)) {
        card.art?.let { art ->
            val side = (height * 0.55f).coerceAtMost(if (size == TileSize.Wide) 96.dp else 72.dp)
            Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(side).testTag("card:art"))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            MetroText(card.title, size = 20.sp, maxLines = 2, lineHeight = 24.sp)
            if (card.artist.isNotBlank()) {
                MetroText(card.artist, size = 15.sp, weight = FontWeight.Normal, maxLines = 1, color = ink.copy(alpha = 0.85f), modifier = Modifier.padding(top = 3.dp))
            }
        }
        PlayMark(card.playing, ink)
    }
}


/** A triangle when the music plays, two bars when it is paused. */
@Composable
internal fun PlayMark(playing: Boolean, color: Color) {
    Canvas(Modifier.size(18.dp).testTag(if (playing) "card:playing" else "card:paused")) {
        if (playing) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.15f, size.height * 0.05f)
                lineTo(size.width * 0.95f, size.height * 0.5f)
                lineTo(size.width * 0.15f, size.height * 0.95f)
                close()
            }
            drawPath(path, color)
        } else {
            drawRect(color, androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.05f), androidx.compose.ui.geometry.Size(size.width * 0.28f, size.height * 0.9f))
            drawRect(color, androidx.compose.ui.geometry.Offset(size.width * 0.6f, size.height * 0.05f), androidx.compose.ui.geometry.Size(size.width * 0.28f, size.height * 0.9f))
        }
    }
}
