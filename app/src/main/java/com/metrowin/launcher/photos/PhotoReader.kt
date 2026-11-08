package com.metrowin.launcher.photos

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import com.metrowin.launcher.data.logged

/** Reads the pictures of the phone from MediaStore (read only). Needs the permission to read images; without it the list is empty. */
object PhotoReader {
    /** The most pictures read: a phone with a hundred thousand of them must not make the page wait. */
    const val LIMIT = 3000

    private val images: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    fun uriOf(id: Long): Uri = ContentUris.withAppendedId(images, id)

    private fun projection() = buildList {
        add(MediaStore.Images.Media._ID)
        add(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        add(MediaStore.Images.Media.DATE_TAKEN)
        add(MediaStore.Images.Media.DATE_ADDED)
        // Favourites exist from Android 11.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.MediaColumns.IS_FAVORITE)
    }.toTypedArray()

    /** Blocking, off the main thread. The newest pictures first, at most [limit]. */
    fun read(context: Context, limit: Int = LIMIT): List<Photo> = runCatching {
        // The limit goes in the query arguments: a "LIMIT" in the sort order is refused by Android 10 and later.
        val args = Bundle().apply {
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.Images.Media.DATE_ADDED} DESC")
        }
        context.contentResolver.query(images, projection(), args, null)?.use(::parse).orEmpty()
    }.logged("photos.read", withMessage = false).getOrDefault(emptyList())

    /** The rows of [cursor] as pictures; a column that is missing is read as empty. The date taken falls back to the date added (in seconds). */
    fun parse(cursor: Cursor): List<Photo> {
        val id = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val album = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        val taken = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
        val added = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
        val favorite = cursor.getColumnIndex(MediaStore.MediaColumns.IS_FAVORITE)
        val out = ArrayList<Photo>()
        while (cursor.moveToNext()) {
            val takenAt = taken.takeIf { it >= 0 }?.let(cursor::getLong)?.takeIf { it > 0 }
                ?: added.takeIf { it >= 0 }?.let { cursor.getLong(it) * 1000 } ?: 0L
            out += Photo(
                id = cursor.getLong(id),
                album = album.takeIf { it >= 0 }?.let(cursor::getString).orEmpty(),
                takenAt = takenAt,
                favorite = favorite >= 0 && cursor.getInt(favorite) == 1,
            )
        }
        return out
    }
}
