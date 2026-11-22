package com.metrowin.launcher.music

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import com.metrowin.launcher.data.logged

/** Reads the songs of the phone from MediaStore (read only). Needs the permission to read audio; without it the list is empty. */
object MusicReader {
    /** The most songs read: a phone with a hundred thousand of them must not make the page wait. */
    const val LIMIT = 20_000

    val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    fun uriOf(id: Long): Uri = ContentUris.withAppendedId(uri, id)

    private fun projection() = buildList {
        add(MediaStore.Audio.Media._ID)
        add(MediaStore.Audio.Media.TITLE)
        add(MediaStore.Audio.Media.ARTIST)
        add(MediaStore.Audio.Media.ALBUM)
        add(MediaStore.Audio.Media.ALBUM_ID)
        add(MediaStore.Audio.Media.DURATION)
        add(MediaStore.Audio.Media.TRACK)
        add(MediaStore.Audio.Media.YEAR)
        add(MediaStore.Audio.Media.DATE_ADDED)
        // The album artist is a column of Android 11 and later.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.Audio.Media.ALBUM_ARTIST)
    }.toTypedArray()

    /** Blocking, off the main thread. */
    fun read(context: Context, limit: Int = LIMIT): List<Track> = runCatching {
        val args = Bundle().apply {
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.Audio.Media.IS_MUSIC} != 0")
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.Audio.Media.DATE_ADDED} DESC")
        }
        context.contentResolver.query(uri, projection(), args, null)?.use(::parse).orEmpty()
    }.logged("music.read", withMessage = false).getOrDefault(emptyList())

    /**
     * The rows of [cursor] as songs; a column that is missing is read as empty. A title that is missing is "Unknown" only on the screen, so here it
     * stays empty, and the tag "<unknown>" that some phones write is read as empty too.
     */
    fun parse(cursor: Cursor): List<Track> {
        fun col(name: String) = cursor.getColumnIndex(name)
        val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val title = col(MediaStore.Audio.Media.TITLE)
        val artist = col(MediaStore.Audio.Media.ARTIST)
        val albumArtist = col("album_artist")
        val album = col(MediaStore.Audio.Media.ALBUM)
        val albumId = col(MediaStore.Audio.Media.ALBUM_ID)
        val duration = col(MediaStore.Audio.Media.DURATION)
        val track = col(MediaStore.Audio.Media.TRACK)
        val year = col(MediaStore.Audio.Media.YEAR)
        val added = col(MediaStore.Audio.Media.DATE_ADDED)
        fun text(i: Int) = i.takeIf { it >= 0 }?.let(cursor::getString).orEmpty().trim().takeUnless { it == "<unknown>" }.orEmpty()
        fun number(i: Int) = i.takeIf { it >= 0 }?.let(cursor::getInt) ?: 0
        fun long(i: Int) = i.takeIf { it >= 0 }?.let(cursor::getLong) ?: 0L
        val out = ArrayList<Track>()
        while (cursor.moveToNext()) {
            out += Track(
                id = cursor.getLong(id), title = text(title), artist = text(artist), albumArtist = text(albumArtist), album = text(album),
                albumId = long(albumId), durationMs = long(duration), trackNo = number(track), year = number(year), addedAt = long(added),
            )
        }
        return out
    }
}
