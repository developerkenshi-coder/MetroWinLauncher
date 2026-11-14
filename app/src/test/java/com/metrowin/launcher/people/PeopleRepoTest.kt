package com.metrowin.launcher.people

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.ContactSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** A simulated address book: a provider that answers like the contacts of Android, with three contacts (one has only an email). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PeopleRepoTest {
    class FakeContacts : ContentProvider() {
        private fun cursor(projection: Array<String>?, rows: List<Map<String, Any?>>): Cursor {
            val columns = projection ?: emptyArray()
            return MatrixCursor(columns).also { c -> rows.forEach { row -> c.addRow(columns.map { row[it] }) } }
        }

        override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sort: String?): Cursor = when (uri.lastPathSegment) {
            "data" -> cursor(projection, listOf(
                data(1, CommonDataKinds.Phone.CONTENT_ITEM_TYPE, "+39 333 123 4567"),
                data(1, CommonDataKinds.Organization.CONTENT_ITEM_TYPE, "Acme"),
                data(1, CommonDataKinds.Event.CONTENT_ITEM_TYPE, "1980-05-17", CommonDataKinds.Event.TYPE_BIRTHDAY),
                data(1, CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE, "7"),
                data(2, CommonDataKinds.Email.CONTENT_ITEM_TYPE, "only@mail.example"),
                data(3, CommonDataKinds.Phone.CONTENT_ITEM_TYPE, "3331234567"),
            ))
            "raw_contacts" -> cursor(projection, listOf(raw(1, "com.google"), raw(2, null), raw(3, "com.android.sim")))
            "groups" -> cursor(projection, listOf(mapOf(ContactsContract.Groups._ID to 7L, ContactsContract.Groups.TITLE to "Family")))
            "contacts" -> cursor(projection, listOf(contact(1, "Zed Anna", 1), contact(2, "Bob Mail", 0), contact(3, "Anna Twin", 0)))
            else -> cursor(projection, emptyList())
        }

        private fun data(id: Long, mime: String, v: String, type: Int = 0) =
            mapOf(ContactsContract.Data.CONTACT_ID to id, ContactsContract.Data.MIMETYPE to mime, ContactsContract.Data.DATA1 to v, ContactsContract.Data.DATA2 to type, ContactsContract.Data.DATA4 to null)

        private fun raw(id: Long, type: String?) = mapOf(ContactsContract.RawContacts.CONTACT_ID to id, ContactsContract.RawContacts.ACCOUNT_TYPE to type)

        private fun contact(id: Long, name: String, starred: Int) = mapOf(
            ContactsContract.Contacts._ID to id, ContactsContract.Contacts.LOOKUP_KEY to "key$id", ContactsContract.Contacts.DISPLAY_NAME_PRIMARY to name,
            ContactsContract.Contacts.STARRED to starred, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI to null,
        )

        override fun onCreate() = true
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, args: Array<String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?) = 0
    }

    @Before
    fun setUp() {
        Robolectric.setupContentProvider(FakeContacts::class.java, ContactsContract.AUTHORITY)
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).grantPermissions(Manifest.permission.READ_CONTACTS)
    }

    @Test
    fun everyContactIsListedAlsoTheOneWithOnlyAnEmail() {
        val snapshot = PeopleRepo.read(ApplicationProvider.getApplicationContext())
        assertEquals(listOf("Zed Anna", "Bob Mail", "Anna Twin"), snapshot.people.map { it.name })
        val bob = snapshot.people.first { it.name == "Bob Mail" }
        assertTrue(bob.numbers.isEmpty())
        assertEquals(listOf("only@mail.example"), bob.emails)
    }

    @Test
    fun theDataOfAContactComesWithIt() {
        val anna = PeopleRepo.read(ApplicationProvider.getApplicationContext()).people.first { it.name == "Zed Anna" }
        assertTrue(anna.starred)
        assertEquals(listOf("+393331234567"), anna.numbers)
        assertEquals("Acme", anna.org)
        assertEquals("1980-05-17", anna.birthday)
        assertEquals(setOf(7L), anna.groups)
        assertEquals(ContactSource.Google.bit, anna.sources)
        assertTrue(PeopleBook.matches(anna, "acme"))
    }

    @Test
    fun theSnapshotIsKeptAndItsGroupsAreRead() {
        val snapshot = PeopleRepo.read(ApplicationProvider.getApplicationContext())
        assertEquals(mapOf(7L to "Family"), snapshot.groups)
        assertEquals(snapshot, PeopleRepo.cached)
        assertNotNull(snapshot.personOf("333 1234567"))
    }

    @Test
    fun twoContactsWithTheSameNumberAreDuplicates() {
        val people = PeopleRepo.read(ApplicationProvider.getApplicationContext()).people
        assertEquals(1, PeopleBook.duplicates(people).size)
    }
}
