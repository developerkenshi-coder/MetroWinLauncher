package com.metrowin.launcher.data

import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.MediaStore

/** A folder of the photos of the phone, as the gallery shows it. */
data class AlbumInfo(val id: String, val name: String, val count: Int)

/** A label of the contacts ("Family") and how many contacts carry it. */
data class GroupInfo(val id: Long, val name: String, val count: Int)

/**
 * The albums of photos and the groups of contacts a tile can stand for. Blocking: call it off the main thread, once when the user
 * is choosing one, never in the background.
 */
class MediaCollections(private val context: Context) {
    /** The albums with the most photos first; empty without the permission to read photos. */
    fun albums(): List<AlbumInfo> {
        if (!LivePermissions.canReadPhotos(context)) return emptyList()
        return runCatching {
            val counts = LinkedHashMap<String, Pair<String, Int>>()
            val args = Bundle().apply {
                putInt(ContentResolver.QUERY_ARG_LIMIT, MAX_SCAN)
                putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Images.Media.DATE_ADDED))
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            }
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media.BUCKET_ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME), args, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    val known = counts[id]
                    counts[id] = if (known == null) name to 1 else known.first to known.second + 1
                }
            }
            counts.map { (id, value) -> AlbumInfo(id, value.first, value.second) }.sortedWith(compareByDescending<AlbumInfo> { it.count }.thenBy { it.name.lowercase() })
        }.getOrDefault(emptyList())
    }

    /** The labels the user made for the contacts, the ones with people in them first; empty without the permission to read contacts. */
    fun groups(): List<GroupInfo> {
        if (!LivePermissions.isGranted(context, LivePermissions.contacts)) return emptyList()
        return runCatching {
            val found = ArrayList<GroupInfo>()
            context.contentResolver.query(
                ContactsContract.Groups.CONTENT_SUMMARY_URI,
                arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.TITLE, ContactsContract.Groups.SUMMARY_COUNT, ContactsContract.Groups.SYSTEM_ID),
                "${ContactsContract.Groups.DELETED}=0 AND ${ContactsContract.Groups.GROUP_VISIBLE}=1", null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val title = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    // The groups Android makes by itself ("My Contacts") are not labels of the user.
                    if (!cursor.getString(3).isNullOrEmpty()) continue
                    found += GroupInfo(cursor.getLong(0), title, cursor.getInt(2))
                }
            }
            // The same label in two accounts is one choice for the user.
            found.groupBy { it.name.lowercase() }.values.map { same -> same.maxBy { it.count } }
                .sortedWith(compareByDescending<GroupInfo> { it.count }.thenBy { it.name.lowercase() })
        }.getOrDefault(emptyList())
    }

    private companion object {
        /** The albums are found among the latest photos: enough to know every album in use, little enough to read at once. */
        const val MAX_SCAN = 20_000
    }
}
