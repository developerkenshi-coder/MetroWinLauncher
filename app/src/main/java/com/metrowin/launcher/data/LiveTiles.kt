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
import com.metrowin.launcher.model.LiveKind
import com.metrowin.launcher.notifications.NowPlaying

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
    /** The music playing in any media app, for the Music tile. */
    val nowPlaying: NowPlaying? = null,
    /** The space left on the phone, for the Storage Sense tile. */
    val storage: StorageInfo? = null,
    /** When the next alarm rings (epoch milliseconds), or 0 when there is none, for the Alarms tile. */
    val nextAlarm: Long = 0L,
    /** The birthdays of the contacts, for the Birthdays tile. */
    val birthdays: List<Birthday> = emptyList(),
    /** The latest photos of each album pinned to Start, by the id of the album. */
    val albums: Map<String, List<ImageBitmap>> = emptyMap(),
    /** The faces of the people of each group of contacts pinned to Start, by the id of the group. */
    val groups: Map<Long, List<LivePerson>> = emptyMap(),
    /** The headlines for the News tile and the quotes for the Finance tile: they come from the Internet and are set apart from the rest. */
    val news: NewsData? = null,
    val finance: FinanceData? = null,
    /** The reminders, for the Reminders tile. */
    val reminders: List<Reminder> = emptyList(),
)

/**
 * Keeps what the tiles of [kinds] show and lets the rest go: a tile that is not on Start any more has no use for its data.
 * [albumIds] and [groupIds] are the albums and the groups that are pinned.
 */
fun LiveTileData.keeping(kinds: Set<LiveKind>, albumIds: Set<String> = emptySet(), groupIds: Set<Long> = emptySet()) = copy(
    photos = if (LiveKind.Photos in kinds) photos else emptyList(),
    people = if (LiveKind.People in kinds) people else emptyList(),
    nextEvent = if (LiveKind.Calendar in kinds) nextEvent else null,
    missedCalls = if (LiveKind.Phone in kinds) missedCalls else 0,
    storage = if (LiveKind.Storage in kinds) storage else null,
    nextAlarm = if (LiveKind.Alarm in kinds) nextAlarm else 0L,
    birthdays = if (LiveKind.Birthdays in kinds) birthdays else emptyList(),
    albums = if (LiveKind.Album in kinds) albums.filterKeys { it in albumIds } else emptyMap(),
    groups = if (LiveKind.Group in kinds) groups.filterKeys { it in groupIds } else emptyMap(),
    news = if (LiveKind.News in kinds) news else null,
    finance = if (LiveKind.Finance in kinds) finance else null,
)

/** What the tiles of [kinds] show, taken from [fresh]; the rest stays as it was. */
fun LiveTileData.updatedWith(fresh: LiveTileData, kinds: Set<LiveKind>) = copy(
    photos = if (LiveKind.Photos in kinds) fresh.photos else photos,
    people = if (LiveKind.People in kinds) fresh.people else people,
    nextEvent = if (LiveKind.Calendar in kinds) fresh.nextEvent else nextEvent,
    missedCalls = if (LiveKind.Phone in kinds) fresh.missedCalls else missedCalls,
    storage = if (LiveKind.Storage in kinds) fresh.storage else storage,
    nextAlarm = if (LiveKind.Alarm in kinds) fresh.nextAlarm else nextAlarm,
    birthdays = if (LiveKind.Birthdays in kinds) fresh.birthdays else birthdays,
    albums = if (LiveKind.Album in kinds) fresh.albums else albums,
    groups = if (LiveKind.Group in kinds) fresh.groups else groups,
)

class LiveTileRepository(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    /**
     * Blocking, call it off the main thread. Reads the data of the kinds in [wanted] only: a Start with no photo tile has no use
     * for the photos. [albumIds] and [groupIds] are the albums and the groups whose tiles are pinned.
     */
    fun load(wanted: Set<LiveKind> = LiveSources.all, albumIds: Set<String> = emptySet(), groupIds: Set<Long> = emptySet()) = LiveTileData(
        photos = if (LiveKind.Photos in wanted) photos() else emptyList(),
        people = if (LiveKind.People in wanted) people() else emptyList(),
        nextEvent = if (LiveKind.Calendar in wanted) nextEvent() else null,
        missedCalls = if (LiveKind.Phone in wanted) missedCalls() else 0,
        storage = if (LiveKind.Storage in wanted) StorageInfo.read() else null,
        nextAlarm = if (LiveKind.Alarm in wanted) NextAlarm.read(context) else 0L,
        birthdays = if (LiveKind.Birthdays in wanted) birthdays() else emptyList(),
        albums = if (LiveKind.Album in wanted) albumIds.associateWith { album(it) } else emptyMap(),
        groups = if (LiveKind.Group in wanted) groupIds.associateWith { group(it) } else emptyMap(),
    )

    private fun granted(permission: String) = LivePermissions.isGranted(context, permission)

    /** The photos and faces already decoded, by where they come from: a read finds the same ones most times and decodes none. */
    private val photoCache = object : LinkedHashMap<Uri, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Uri, ImageBitmap>?) = size > MAX_PHOTOS
    }
    private val faceCache = HashMap<Uri, ImageBitmap>()

    @Synchronized
    private fun photos(limit: Int = 8): List<ImageBitmap> = recentPhotos(null, limit)

    /** The latest photos of one album of the phone ([bucket] is the id of the album), as thumbnails. */
    @Synchronized
    private fun album(bucket: String, limit: Int = 8): List<ImageBitmap> = recentPhotos(bucket, limit)

    /** The latest photos of the phone, or of one of its albums, as thumbnails. */
    private fun recentPhotos(bucket: String?, limit: Int): List<ImageBitmap> {
        if (!LivePermissions.canReadPhotos(context)) return emptyList()
        return runCatching {
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val args = Bundle().apply {
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Images.Media.DATE_ADDED))
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                if (bucket != null) {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.Images.Media.BUCKET_ID}=?")
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(bucket))
                }
            }
            val uris = ArrayList<Uri>(limit)
            resolver.query(collection, arrayOf(MediaStore.Images.Media._ID), args, null)?.use { cursor ->
                while (cursor.moveToNext()) uris += ContentUris.withAppendedId(collection, cursor.getLong(0))
            }
            uris.mapNotNull { uri -> photoCache[uri] ?: thumbnail(uri, PHOTO_PX)?.asImageBitmap()?.also { photoCache[uri] = it } }
        }.getOrDefault(emptyList())
    }

    /** The faces of the people of one group of the contacts, in a different order each time. */
    @Synchronized
    private fun group(groupId: Long, limit: Int = 8): List<LivePerson> {
        if (!granted(LivePermissions.contacts)) return emptyList()
        return runCatching {
            val rows = ArrayList<Pair<String, Uri>>()
            resolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI),
                "${ContactsContract.Data.MIMETYPE}=? AND ${ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID}=? AND " +
                    "${ContactsContract.Contacts.PHOTO_THUMBNAIL_URI} IS NOT NULL",
                arrayOf(ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE, groupId.toString()),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext() && rows.size < MAX_GROUP_ROWS) {
                    val name = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: continue
                    val photo = cursor.getString(1)?.let(Uri::parse) ?: continue
                    rows += name to photo
                }
            }
            if (faceCache.size > MAX_FACES) faceCache.clear()
            rows.distinctBy { it.second }.shuffled().take(limit).mapNotNull { (name, uri) ->
                val face = faceCache[uri]
                    ?: resolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)?.asImageBitmap()?.also { faceCache[uri] = it }
                    ?: return@mapNotNull null
                LivePerson(name, face)
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
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
            if (faceCache.size > MAX_FACES) faceCache.clear()
            (starred.shuffled() + others.shuffled()).take(limit).mapNotNull { (name, uri) ->
                val face = faceCache[uri]
                    ?: resolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)?.asImageBitmap()?.also { faceCache[uri] = it }
                    ?: return@mapNotNull null
                LivePerson(name, face)
            }
        }.getOrDefault(emptyList())
    }

    private fun nextEvent(): LiveEvent? = CalendarSearch(context).next()

    /** The birthdays of the contacts (needs the contacts permission): every contact with a date of birth. */
    private fun birthdays(): List<Birthday> {
        if (!granted(LivePermissions.contacts)) return emptyList()
        return runCatching {
            val found = ArrayList<Birthday>()
            resolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.CommonDataKinds.Event.START_DATE),
                "${ContactsContract.Data.MIMETYPE}=? AND ${ContactsContract.CommonDataKinds.Event.TYPE}=?",
                arrayOf(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext() && found.size < MAX_BIRTHDAYS) {
                    val name = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: continue
                    Birthdays.parse(name, cursor.getString(1))?.let { found += it }
                }
            }
            found
        }.getOrDefault(emptyList())
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
        const val MAX_BIRTHDAYS = 2_000
        const val MAX_FACES = 40
        const val MAX_PHOTOS = 32
        const val MAX_GROUP_ROWS = 2_000
        const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
