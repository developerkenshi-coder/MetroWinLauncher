package com.metrowin.launcher.music

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.actioncenter.MetroToast

/**
 * Playing goes through the music app the user already has: no service of the launcher, no new permission. A song is opened with the audio
 * viewer of Android; an album (or a song the viewer cannot take) is asked of the player as a search, as voice commands do.
 */
object MusicPlay {
    fun song(context: Context, track: Track) {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(MusicReader.uriOf(track.id), "audio/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(view) }.isSuccess) return
        search(context, track.title, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE, track)
    }

    fun album(context: Context, album: MusicAlbum) {
        val first = album.tracks.firstOrNull() ?: return
        search(context, album.name.ifEmpty { first.title }, MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE, first)
    }

    private fun search(context: Context, query: String, focus: String, track: Track) {
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, focus)
            .putExtra(SearchManager.QUERY, query)
            .putExtra(MediaStore.EXTRA_MEDIA_TITLE, track.title)
            .putExtra(MediaStore.EXTRA_MEDIA_ARTIST, track.artist)
            .putExtra(MediaStore.EXTRA_MEDIA_ALBUM, track.album)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isFailure) MetroToast.show(context, R.string.music_no_player)
    }
}

/** The small pictures of the albums, decoded off the main thread and kept in memory (24 of them). */
object MusicCovers {
    private val memory = LruCache<Long, Bitmap>(24)
    private val missing = HashSet<Long>()

    /** Blocking. The cover of the album of [track] about [px] pixels wide, or null when there is none. */
    @Synchronized
    fun load(context: Context, track: Track, px: Int): Bitmap? {
        val key = if (track.albumId != 0L) track.albumId else -track.id
        memory.get(key)?.let { return it }
        if (key in missing) return null
        val bitmap = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) context.contentResolver.loadThumbnail(MusicReader.uriOf(track.id), Size(px, px), null)
            else context.contentResolver.openInputStream(android.net.Uri.parse("content://media/external/audio/albumart/${track.albumId}"))?.use(BitmapFactory::decodeStream)
        }.getOrNull()
        if (bitmap == null) missing += key else memory.put(key, bitmap)
        return bitmap
    }

    fun trim() = memory.evictAll()
}
