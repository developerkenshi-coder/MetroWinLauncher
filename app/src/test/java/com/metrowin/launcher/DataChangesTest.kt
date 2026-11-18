package com.metrowin.launcher

import android.Manifest
import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.DataChanges
import com.metrowin.launcher.data.DataChanges.Source
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataChangesTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val scope = CoroutineScope(Dispatchers.Default)

    private fun observers(source: Source) = shadowOf(app.contentResolver).getContentObservers(source.uri).size

    private fun listen(source: Source, count: AtomicInteger, enabled: Boolean = true): Job =
        scope.launch { DataChanges.flow(app, source, enabled).collect { count.incrementAndGet() } }

    /** The flow registers on the collector's thread: wait for it (or for the first moment it surely would have). */
    private fun settle(ms: Long = 300) = Thread.sleep(ms)

    private fun burst(source: Source, times: Int = 5) {
        repeat(times) { app.contentResolver.notifyChange(source.uri, null) }
        shadowOf(Looper.getMainLooper()).idle()
        settle(DataChanges.PAUSE_MS + 400)
    }

    @Test
    fun aBurstIsOneChangeAndAllListenersShareOneObserver() = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CALENDAR)
        val a = AtomicInteger(); val b = AtomicInteger()
        val one = listen(Source.Calendar, a); val two = listen(Source.Calendar, b)
        settle()
        assertEquals(1, observers(Source.Calendar))
        assertEquals(2, DataChanges.listeners(Source.Calendar))
        burst(Source.Calendar)
        assertEquals(1, a.get()); assertEquals(1, b.get())
        one.cancelAndJoin(); settle()
        assertEquals(1, observers(Source.Calendar))
        two.cancelAndJoin(); settle()
        assertEquals(0, observers(Source.Calendar))
        assertEquals(0, DataChanges.listeners(Source.Calendar))
    }

    @Test
    fun withoutPermissionNothingIsRegisteredAndNothingFires() = runBlocking {
        val count = AtomicInteger()
        val job = listen(Source.Contacts, count)
        settle()
        assertEquals(0, observers(Source.Contacts))
        burst(Source.Contacts)
        assertEquals(0, count.get())
        job.cancelAndJoin()
    }

    @Test
    fun messagesFollowTheCallerNotAPermission() = runBlocking {
        val off = AtomicInteger(); val on = AtomicInteger()
        val a = listen(Source.Messages, off, enabled = false)
        settle()
        assertEquals(0, observers(Source.Messages))
        val b = listen(Source.Messages, on)
        settle()
        assertEquals(1, observers(Source.Messages))
        burst(Source.Messages)
        assertEquals(0, off.get()); assertEquals(1, on.get())
        a.cancelAndJoin(); b.cancelAndJoin(); settle()
        assertEquals(0, observers(Source.Messages))
    }
}
