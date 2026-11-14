package com.metrowin.launcher.datasense

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/** One media file of the phone: its name, size and the address to open it with. */
class BigFile(val name: String, val size: Long, val uri: Uri)

/**
 * The largest media files and the possible duplicates, from the media database of Android: only what the launcher was already allowed to see
 * (photos, videos, music), nothing is read from the storage and nothing is deleted. Blocking: off the main thread.
 */
object StorageScan {
    private const val BIG = 12
    private const val MIN_DUPLICATE = 1_000_000L

    /** Photos, videos and songs that share a name and a size (at least 1 MB): the same file twice, most of the time. */
    fun duplicatesOf(files: List<BigFile>): List<List<BigFile>> =
        files.filter { it.size >= MIN_DUPLICATE }.groupBy { it.name to it.size }.values.filter { it.size > 1 }.sortedByDescending { it.first().size * it.size }

    fun read(context: Context): Pair<List<BigFile>, List<List<BigFile>>> {
        val all = ArrayList<BigFile>()
        listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI).forEach { base ->
            runCatching {
                context.contentResolver.query(
                    base, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE), null, null,
                    "${MediaStore.MediaColumns.SIZE} DESC",
                )?.use { cursor ->
                    // The biggest first: the first rows are the biggest, and the duplicates are looked for in the first thousand.
                    var rows = 0
                    while (cursor.moveToNext() && rows++ < 1000) {
                        all += BigFile(cursor.getString(1).orEmpty(), cursor.getLong(2), ContentUris.withAppendedId(base, cursor.getLong(0)))
                    }
                }
            }
        }
        return all.sortedByDescending { it.size }.take(BIG) to duplicatesOf(all).take(BIG)
    }
}
