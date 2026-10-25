package com.metrowin.launcher.data

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.MediaStore
import android.util.Size
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat

/** The runtime permissions behind the live tiles. All of them are optional. */
object LivePermissions {
    val photos: String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
    const val calendar = Manifest.permission.READ_CALENDAR
    const val contacts = Manifest.permission.READ_CONTACTS
    const val calls = Manifest.permission.READ_CALL_LOG

    /** Android 14 lets the user share only some photos; reading those needs this one as well. */
    private const val SELECTED_PHOTOS = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

    val all: List<String>
        get() = buildList {
            add(photos)
            if (Build.VERSION.SDK_INT >= 34) add(SELECTED_PHOTOS)
            add(calendar)
            add(contacts)
            add(calls)
        }

    /** Full photo access, or the partial access Android 14 offers. */
    fun canReadPhotos(context: Context): Boolean =
        isGranted(context, photos) || (Build.VERSION.SDK_INT >= 34 && isGranted(context, SELECTED_PHOTOS))

    fun isGranted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

@Immutable
data class LivePerson(val name: String, val photo: ImageBitmap)

@Immutable
data class LiveEvent(val title: String, val startMillis: Long, val allDay: Boolean)

/** What the live tiles show right now. Empty values mean "no permission" or "nothing to show". */
@Immutable
data class LiveTileData(
    val photos: List<ImageBitmap> = emptyList(),
    val people: List<LivePerson> = emptyList(),
    val nextEvent: LiveEvent? = null,
    val missedCalls: Int = 0,
    /** The flashlight is on, for the torch tile. Not read from the phone with the rest: it comes from the torch callback. */
    val torchOn: Boolean = false,
    val battery: BatteryInfo? = null,
    val weather: WeatherData? = null,
)

class LiveTileRepository(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    /** Blocking, call it off the main thread. */
    fun load() = LiveTileData(
        photos = photos(),
        people = people(),
        nextEvent = nextEvent(),
        missedCalls = missedCalls(),
    )

    private fun granted(permission: String) = LivePermissions.isGranted(context, permission)

    private fun photos(limit: Int = 8): List<ImageBitmap> {
        if (!LivePermissions.canReadPhotos(context)) return emptyList()
        return runCatching {
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val args = Bundle().apply {
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Images.Media.DATE_ADDED))
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            }
            val uris = ArrayList<Uri>(limit)
            resolver.query(collection, arrayOf(MediaStore.Images.Media._ID), args, null)?.use { cursor ->
                while (cursor.moveToNext()) uris += ContentUris.withAppendedId(collection, cursor.getLong(0))
            }
            uris.mapNotNull { thumbnail(it, PHOTO_PX)?.asImageBitmap() }
        }.getOrDefault(emptyList())
    }

    private fun people(limit: Int = 8): List<LivePerson> {
        if (!granted(LivePermissions.contacts)) return emptyList()
        return runCatching {
            val args = Bundle().apply {
                putInt(ContentResolver.QUERY_ARG_LIMIT, 60)
                putString(
                    ContentResolver.QUERY_ARG_SQL_SELECTION,
                    "${ContactsContract.Contacts.PHOTO_THUMBNAIL_URI} IS NOT NULL",
                )
                putString(
                    ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                    "${ContactsContract.Contacts.STARRED} DESC",
                )
            }
            val projection = arrayOf(
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                ContactsContract.Contacts.STARRED,
            )
            val starred = ArrayList<Pair<String, Uri>>()
            val others = ArrayList<Pair<String, Uri>>()
            resolver.query(ContactsContract.Contacts.CONTENT_URI, projection, args, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: continue
                    val photo = cursor.getString(1)?.let(Uri::parse) ?: continue
                    (if (cursor.getInt(2) == 1) starred else others) += name to photo
                }
            }
            (starred.shuffled() + others.shuffled()).take(limit).mapNotNull { (name, uri) ->
                val bitmap = resolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) ?: return@mapNotNull null
                LivePerson(name, bitmap.asImageBitmap())
            }
        }.getOrDefault(emptyList())
    }

    private fun nextEvent(): LiveEvent? {
        if (!granted(LivePermissions.calendar)) return null
        return runCatching {
            val now = System.currentTimeMillis()
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .also {
                    ContentUris.appendId(it, now)
                    ContentUris.appendId(it, now + WEEK_MILLIS)
                }
                .build()
            val projection = arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
            )
            var next: LiveEvent? = null
            resolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
                while (next == null && cursor.moveToNext()) {
                    if (cursor.getLong(2) <= now) continue
                    next = LiveEvent(cursor.getString(0).orEmpty(), cursor.getLong(1), cursor.getInt(3) == 1)
                }
            }
            next
        }.getOrNull()
    }

    private fun missedCalls(): Int {
        if (!granted(LivePermissions.calls)) return 0
        return runCatching {
            resolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID),
                "${CallLog.Calls.TYPE}=? AND ${CallLog.Calls.NEW}=1",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
                null,
            )?.use { it.count } ?: 0
        }.getOrDefault(0)
    }

    private fun thumbnail(uri: Uri, size: Int): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 29) resolver.loadThumbnail(uri, Size(size, size), null)
        else decodeSampled(uri, size)
    }.getOrNull()

    private fun decodeSampled(uri: Uri, size: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private companion object {
        const val PHOTO_PX = 512
        const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
