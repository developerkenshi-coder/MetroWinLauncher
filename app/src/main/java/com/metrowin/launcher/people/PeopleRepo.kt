package com.metrowin.launcher.people

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.Telephony
import androidx.compose.runtime.Immutable
import com.metrowin.launcher.data.ContactSource
import com.metrowin.launcher.data.LivePermissions
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce

/** Every contact of the phone as the hub shows it, read in one go. */
@Immutable
class PeopleSnapshot(val people: List<Person>, val groups: Map<Long, String>) {
    private val byTail: Map<String, List<Person>> by lazy {
        val map = HashMap<String, MutableList<Person>>()
        people.forEach { p -> p.numbers.forEach { map.getOrPut(PeopleBook.tail(it)) { ArrayList(1) } += p } }
        map
    }

    /** The contact that has [number], however it is written; null for a number nobody has. */
    fun personOf(number: String): Person? = byTail[PeopleBook.tail(number)]?.firstOrNull { p -> p.numbers.any { PeopleBook.sameNumber(it, number) } }

    companion object {
        val Empty = PeopleSnapshot(emptyList(), emptyMap())
    }
}

/** A line of the card of a contact: a number, an address, a mail with the word that tells which ([label]: "mobile", "home"). */
@Immutable
data class Labeled(val label: String, val value: String)

/** All that the contacts keep about one person: what the card shows. */
@Immutable
data class ContactDetail(
    val phones: List<Labeled> = emptyList(),
    val emails: List<Labeled> = emptyList(),
    val addresses: List<Labeled> = emptyList(),
    val websites: List<String> = emptyList(),
    val birthday: String? = null,
    val org: String = "",
    val note: String = "",
    val groups: List<String> = emptyList(),
    val accounts: List<String> = emptyList(),
)

enum class TouchKind { CallIn, CallOut, CallMissed, TextIn, TextOut, Notification }

/** One time that the user and a person met: a call, a text or (when the user wants it) a notification of theirs. */
@Immutable
data class Touch(val date: Long, val kind: TouchKind, val text: String = "")

/**
 * The one door to the contacts, the call log and the texts for the People hub (blocking: off the main thread). It keeps the last
 * list read, so the hub opens with something to show while the new one is read, and it tells when the contacts or the calls change.
 */
object PeopleRepo {
    @Volatile
    var cached: PeopleSnapshot? = null
        private set

    private val DATA_KINDS = arrayOf(
        CommonDataKinds.Phone.CONTENT_ITEM_TYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE, CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
        CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE, CommonDataKinds.Note.CONTENT_ITEM_TYPE, CommonDataKinds.Event.CONTENT_ITEM_TYPE,
        CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
    )

    private class Acc {
        val numbers = LinkedHashSet<String>()
        val emails = LinkedHashSet<String>()
        var org = ""
        val groups = HashSet<Long>()
        var birthday: String? = null
        val extra = StringBuilder()
    }

    /** The contacts that have a name (those with only an email too), in the order of the language of the phone; empty without the permission to read them. */
    fun read(context: Context): PeopleSnapshot {
        if (!LivePermissions.isGranted(context, LivePermissions.contacts)) return PeopleSnapshot.Empty
        val snapshot = runCatching {
            val resolver = context.contentResolver
            val acc = HashMap<Long, Acc>()
            // One pass over the data of every contact: numbers (the main one first), mails, company, groups, birthday, and the words the search needs.
            resolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1, ContactsContract.Data.DATA2, ContactsContract.Data.DATA4),
                "${ContactsContract.Data.MIMETYPE} IN (${DATA_KINDS.joinToString(",") { "?" }})", DATA_KINDS,
                "${ContactsContract.Data.IS_SUPER_PRIMARY} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val a = acc.getOrPut(c.getLong(0)) { Acc() }
                    val v = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                    when (c.getString(1)) {
                        CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> clean(v).takeIf { it.isNotEmpty() }?.let(a.numbers::add)
                        CommonDataKinds.Email.CONTENT_ITEM_TYPE -> a.emails += v.trim()
                        CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> if (a.org.isEmpty()) a.org = v.trim()
                        CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE -> v.toLongOrNull()?.let(a.groups::add)
                        CommonDataKinds.Event.CONTENT_ITEM_TYPE -> if (c.getInt(3) == CommonDataKinds.Event.TYPE_BIRTHDAY && a.birthday == null) a.birthday = v
                        else -> if (a.extra.length < EXTRA_MAX) a.extra.append(v.take(EXTRA_MAX)).append(' ')
                    }
                }
            }
            val sources = HashMap<Long, Int>()
            resolver.query(
                ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts.CONTACT_ID, ContactsContract.RawContacts.ACCOUNT_TYPE),
                "${ContactsContract.RawContacts.DELETED}=0", null, null,
            )?.use { c -> while (c.moveToNext()) sources.merge(c.getLong(0), ContactSource.of(c.getString(1)).bit, Int::or) }
            val groups = readGroups(context)
            val out = ArrayList<Person>()
            resolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.STARRED, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                ),
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} IS NOT NULL", null,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                    val key = c.getString(1) ?: continue
                    val id = c.getLong(0)
                    val a = acc[id] ?: Acc()
                    val numbers = a.numbers.toList()
                    val emails = a.emails.toList()
                    out += Person(
                        ContactsContract.Contacts.getLookupUri(id, key).toString(), name, c.getInt(3) == 1, c.getString(4), numbers, emails, a.org,
                        sources[id] ?: ContactSource.Phone.bit, a.groups, a.birthday, PeopleBook.haystackOf(name, numbers, emails, a.org, a.extra.toString()),
                    )
                }
            }
            PeopleSnapshot(out, groups)
        }.getOrDefault(PeopleSnapshot.Empty)
        cached = snapshot
        return snapshot
    }

    private fun readGroups(context: Context): Map<Long, String> = runCatching {
        val found = LinkedHashMap<Long, String>()
        context.contentResolver.query(
            ContactsContract.Groups.CONTENT_URI, arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.TITLE),
            "${ContactsContract.Groups.DELETED}=0 AND ${ContactsContract.Groups.GROUP_VISIBLE}=1", null, "${ContactsContract.Groups.TITLE} COLLATE LOCALIZED ASC",
        )?.use { c -> while (c.moveToNext()) c.getString(1)?.takeIf { it.isNotBlank() }?.let { found[c.getLong(0)] = it } }
        found
    }.getOrDefault(emptyMap())

    private fun clean(number: String) = number.filter { it.isDigit() || it == '+' }

    /** The contact id behind a lookup address (which survives merges), or null when the contact is gone. */
    private fun idOf(context: Context, lookupUri: String): Long? = runCatching {
        ContactsContract.Contacts.lookupContact(context.contentResolver, Uri.parse(lookupUri))?.let(ContentUris::parseId)
    }.getOrNull()

    /** All the data of the contact at [lookupUri], for its card. */
    fun detail(context: Context, lookupUri: String, groupTitles: Map<Long, String>): ContactDetail? {
        val id = idOf(context, lookupUri) ?: return null
        return runCatching {
            val res = context.resources
            val phones = ArrayList<Labeled>()
            val emails = ArrayList<Labeled>()
            val addresses = ArrayList<Labeled>()
            val sites = ArrayList<String>()
            val groups = ArrayList<String>()
            var birthday: String? = null
            var org = ""
            var note = ""
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1, ContactsContract.Data.DATA2, ContactsContract.Data.DATA3, ContactsContract.Data.DATA4),
                "${ContactsContract.Data.CONTACT_ID}=?", arrayOf(id.toString()), "${ContactsContract.Data.IS_SUPER_PRIMARY} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val v = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    val type = c.getInt(2)
                    val custom = c.getString(3)
                    when (c.getString(0)) {
                        CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> clean(v).takeIf { n -> n.isNotEmpty() && phones.none { PeopleBook.sameNumber(it.value, n) } }
                            ?.let { phones += Labeled(CommonDataKinds.Phone.getTypeLabel(res, type, custom).toString(), it) }
                        CommonDataKinds.Email.CONTENT_ITEM_TYPE -> if (emails.none { it.value.equals(v.trim(), true) }) emails += Labeled(CommonDataKinds.Email.getTypeLabel(res, type, custom).toString(), v.trim())
                        CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> addresses += Labeled(CommonDataKinds.StructuredPostal.getTypeLabel(res, type, custom).toString(), v.trim())
                        CommonDataKinds.Website.CONTENT_ITEM_TYPE -> sites += v.trim()
                        CommonDataKinds.Event.CONTENT_ITEM_TYPE -> if (type == CommonDataKinds.Event.TYPE_BIRTHDAY && birthday == null) birthday = v
                        CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> if (org.isEmpty()) org = listOf(v.trim(), c.getString(4).orEmpty().trim()).filter { it.isNotEmpty() }.joinToString(" · ")
                        CommonDataKinds.Note.CONTENT_ITEM_TYPE -> if (note.isEmpty()) note = v.trim()
                        CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE -> v.toLongOrNull()?.let { groupTitles[it] }?.let(groups::add)
                    }
                }
            }
            val accounts = LinkedHashSet<String>()
            context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts.ACCOUNT_TYPE, ContactsContract.RawContacts.ACCOUNT_NAME),
                "${ContactsContract.RawContacts.CONTACT_ID}=? AND ${ContactsContract.RawContacts.DELETED}=0", arrayOf(id.toString()), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val source = context.getString(ContactSource.of(c.getString(0)).label)
                    accounts += listOf(source, c.getString(1).orEmpty()).filter { it.isNotBlank() }.distinct().joinToString(" · ")
                }
            }
            ContactDetail(phones, emails, addresses, sites, birthday, org, note, groups, accounts.toList())
        }.getOrNull()
    }

    /** Fires when the contacts change (anywhere, the contacts app, a sync), after a short pause so a burst of writes is one. */
    @OptIn(FlowPreview::class)
    fun contactChanges(context: Context): Flow<Unit> = changes(context, ContactsContract.Contacts.CONTENT_URI, LivePermissions.contacts)

    /** Fires when the call log changes. */
    @OptIn(FlowPreview::class)
    fun callChanges(context: Context): Flow<Unit> = changes(context, CallLog.Calls.CONTENT_URI, LivePermissions.calls)

    @OptIn(FlowPreview::class)
    private fun changes(context: Context, uri: Uri, permission: String): Flow<Unit> = callbackFlow {
        val resolver = context.applicationContext.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        val watching = LivePermissions.isGranted(context, permission) && runCatching { resolver.registerContentObserver(uri, true, observer) }.isSuccess
        awaitClose { if (watching) resolver.unregisterContentObserver(observer) }
    }.debounce(CHANGE_PAUSE_MS)

    /** The photo of a contact at a size for the card (the big one when the contact has it), or null. */
    fun bigPhoto(context: Context, lookupUri: String): Bitmap? = runCatching {
        decodeSampled({ ContactsContract.Contacts.openContactPhotoInputStream(context.contentResolver, Uri.parse(lookupUri), true) }, BIG_PX)
    }.getOrNull()

    /** A picture from a stream, read at no more than about [maxSide] pixels: the file is never loaded whole at its own size. */
    fun decodeSampled(open: () -> java.io.InputStream?, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / (sample * 2) >= maxSide) sample *= 2
        return open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }

    /**
     * The calls and the texts with a person who has [numbers], the latest first. The log is asked only for the lines that end like the
     * numbers (not the 200 latest calls and every message of every thread), then each line is checked with [PeopleBook.sameNumber].
     * shortcut: a text whose address has spaces inside its last six digits is missed, upgrade when a phone is seen that stores them so.
     */
    fun history(context: Context, numbers: List<String>): List<Touch> {
        val tails = numbers.map { it.filter { ch -> ch.isDigit() }.takeLast(TAIL_LIKE) }.filter { it.length >= MIN_LIKE }.distinct()
        if (tails.isEmpty()) return emptyList()
        val where = tails.joinToString(" OR ") { "%s LIKE ?" }
        val args = tails.map { "%$it" }.toTypedArray()
        val out = ArrayList<Touch>()
        if (LivePermissions.isGranted(context, LivePermissions.calls)) runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE),
                where.format(*Array(tails.size) { CallLog.Calls.NUMBER }), args, "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                var n = 0
                while (n < LINES && c.moveToNext()) {
                    if (numbers.none { PeopleBook.sameNumber(it, c.getString(0).orEmpty()) }) continue
                    n++
                    val kind = when (c.getInt(1)) {
                        CallLog.Calls.OUTGOING_TYPE -> TouchKind.CallOut
                        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> TouchKind.CallMissed
                        else -> TouchKind.CallIn
                    }
                    out += Touch(c.getLong(2), kind)
                }
            }
        }
        if (LivePermissions.isGranted(context, Manifest.permission.READ_SMS)) runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                where.format(*Array(tails.size) { Telephony.Sms.ADDRESS }), args, "${Telephony.Sms.DATE} DESC",
            )?.use { c ->
                var n = 0
                while (n < LINES && c.moveToNext()) {
                    if (numbers.none { PeopleBook.sameNumber(it, c.getString(0).orEmpty()) }) continue
                    n++
                    out += Touch(c.getLong(2), if (c.getInt(3) == Telephony.Sms.MESSAGE_TYPE_INBOX) TouchKind.TextIn else TouchKind.TextOut, c.getString(1).orEmpty())
                }
            }
        }
        return out.sortedByDescending { it.date }
    }

    private const val CHANGE_PAUSE_MS = 400L
    private const val EXTRA_MAX = 300
    private const val BIG_PX = 1024
    private const val LINES = 40
    private const val TAIL_LIKE = 6
    private const val MIN_LIKE = 5
}
