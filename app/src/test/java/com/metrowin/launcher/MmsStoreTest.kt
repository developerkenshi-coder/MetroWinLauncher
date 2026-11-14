package com.metrowin.launcher

import android.app.Application
import android.app.role.RoleManager
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.messages.MmsPdu
import com.metrowin.launcher.messages.SmsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** A tiny in-memory "mms" provider: rows of three tables (pdu, addr, part), enough for what the repository writes. */
private class FakeMms : ContentProvider() {
    val pdu = LinkedHashMap<Long, ContentValues>()
    val addr = ArrayList<ContentValues>()
    val parts = ArrayList<ContentValues>()
    var failParts = false
    private var next = 1L
    override fun onCreate() = true
    private fun kind(uri: Uri) = when { "addr" in uri.pathSegments -> "addr"; "part" in uri.pathSegments -> "part"; else -> "pdu" }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val v = ContentValues(values)
        return when (kind(uri)) {
            "addr" -> { addr += v; ContentUris.withAppendedId(uri, next++) }
            "part" -> {
                if (failParts) return null
                v.put("_id", next); parts += v; Uri.parse("content://mms/part/${next++}")
            }
            else -> { val id = next++; v.put("_id", id); pdu[id] = v; Uri.parse("content://mms/$id") }
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        val cols = projection ?: arrayOf("_id")
        val c = MatrixCursor(cols)
        val rows = if (kind(uri) == "pdu") pdu.values.toList() else parts
        rows.filter { r -> selection == null || (selection.startsWith("tr_id") && r.getAsString("tr_id") == args!![0]) || (selection.startsWith("_id") && r.getAsLong("_id").toString() == args!![0]) }
            .forEach { r -> c.addRow(cols.map { r.get(it) }) }
        return c
    }

    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = if (pdu.remove(ContentUris.parseId(uri)) != null) 1 else 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
    override fun getType(uri: Uri): String? = null
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? = ParcelFileDescriptor.open(File.createTempFile("part", ".bin"), ParcelFileDescriptor.MODE_READ_WRITE)
}

/** Answers every thread lookup with thread 7. */
private class FakeThreads : ContentProvider() {
    override fun onCreate() = true
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor = MatrixCursor(arrayOf("_id")).apply { addRow(arrayOf(7L)) }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
    override fun getType(uri: Uri): String? = null
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MmsStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var mms: FakeMms
    private lateinit var repository: SmsRepository

    @Before fun setUp() {
        mms = Robolectric.setupContentProvider(FakeMms::class.java, "mms")
        Robolectric.setupContentProvider(FakeThreads::class.java, "mms-sms")
        shadowOf(context.getSystemService(RoleManager::class.java)).addHeldRole(RoleManager.ROLE_SMS)
        repository = SmsRepository(context)
    }

    private fun note(id: String = "T1") = MmsPdu.Notification(id, "http://mmsc/x", "+39333", 1000, null)
    private fun message() = MmsPdu.Retrieved(
        "+39333", listOf("+39444"), null, "m1", 100,
        listOf(MmsPdu.Part("text/plain", null, null, "hello".toByteArray(), 106), MmsPdu.Part("image/jpeg", "a.jpg", null, byteArrayOf(1, 2, 3), 0)),
    )

    @Test fun announcementIsKeptAndNotDuplicated() {
        val first = repository.saveMmsNotification(note(), -1)
        assertNotNull(first)
        assertEquals(130, mms.pdu.getValue(first!!).getAsInteger("m_type"))
        assertEquals("a push seen twice is the same row", first, repository.saveMmsNotification(note(), -1))
        assertEquals(1, mms.pdu.size)
    }

    @Test fun fetchedMessageReplacesItsAnnouncement() {
        val row = repository.saveMmsNotification(note(), -1)!!
        val id = repository.saveMms(message(), row)
        assertNotNull(id)
        assertEquals(setOf(id), mms.pdu.keys)
        assertEquals(132, mms.pdu.getValue(id!!).getAsInteger("m_type"))
        assertEquals("T1", mms.pdu.getValue(id).getAsString("tr_id"))
        assertEquals(listOf("text/plain", "image/jpeg"), mms.parts.map { it.getAsString("ct") })
        assertEquals("hello", mms.parts[0].getAsString("text"))
        assertEquals(listOf(137, 151), mms.addr.takeLast(2).map { it.getAsInteger("type") })
    }

    @Test fun failedStoreLeavesTheAnnouncementAndNoHalfMessage() {
        val row = repository.saveMmsNotification(note(), -1)!!
        mms.failParts = true
        assertNull(repository.saveMms(message(), row))
        assertEquals("only the announcement is left", setOf(row), mms.pdu.keys)
        assertTrue(mms.parts.isEmpty())
    }

    @Test fun notTheMessagingAppStoresNothing() {
        shadowOf(context.getSystemService(RoleManager::class.java)).removeHeldRole(RoleManager.ROLE_SMS)
        assertNull(repository.saveMmsNotification(note(), -1))
        assertTrue(mms.pdu.isEmpty())
    }
}
