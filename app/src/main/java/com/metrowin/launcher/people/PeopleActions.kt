package com.metrowin.launcher.people

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.core.net.toUri
import java.time.LocalDate
import java.time.ZoneOffset

/** What the People app asks other apps to do. The contacts of the phone are only read here: to change one, the contacts app is opened on it. */
object PeopleActions {
    /** The contacts app opened on the contact, to change it (or take it away: the card does not, it holds no right to write contacts). */
    fun edit(lookupUri: String): Intent = Intent(Intent.ACTION_EDIT).setDataAndType(lookupUri.toUri(), ContactsContract.Contacts.CONTENT_ITEM_TYPE)

    /** The card of the contact as a .vcf file to send, with the right to read it given to the app that takes it. Null when the address has no key. */
    fun share(lookupUri: String): Intent? {
        val key = PeopleBook.lookupKey(lookupUri) ?: return null
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, Uri.decode(key))
        return Intent(Intent.ACTION_SEND).setType("text/x-vcard").putExtra(Intent.EXTRA_STREAM, uri)
            .also { it.clipData = ClipData.newRawUri("", uri) }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun mail(address: String): Intent = Intent(Intent.ACTION_SENDTO, "mailto:${Uri.encode(address)}".toUri())

    fun map(address: String): Intent = Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(address)}".toUri())

    fun site(address: String): Intent = Intent(Intent.ACTION_VIEW, (if ("://" in address) address else "https://$address").toUri())

    /** A yearly all-day event for the birthday of [name], from its next day on, in the calendar app that the user has. */
    fun birthday(name: String, month: Int, day: Int, today: LocalDate): Intent {
        val next = PeopleBook.daysUntil(month, day, today).let { today.plusDays(it.toLong()) }
        return Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, name).putExtra(CalendarContract.Events.ALL_DAY, true)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, next.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
            .putExtra(CalendarContract.Events.RRULE, "FREQ=YEARLY")
    }

    /** The vCard text of [keys] (the lookup keys, still encoded), in groups so no address is too long, written to [out]. Returns how many contacts went. */
    fun exportVcf(context: Context, keys: List<String>, out: java.io.OutputStream): Int {
        var written = 0
        keys.chunked(EXPORT_CHUNK).forEach { chunk ->
            val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_MULTI_VCARD_URI, Uri.encode(chunk.joinToString(":") { Uri.decode(it) }))
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.copyTo(out) }; written += chunk.size }
        }
        return written
    }

    private const val EXPORT_CHUNK = 50
}
