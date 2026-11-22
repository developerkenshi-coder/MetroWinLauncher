package com.metrowin.launcher

import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.shizukuservice.TaskRef
import com.metrowin.launcher.shizukuservice.TaskThumbs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskThumbsTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun aTaskLineFromTheServiceIsReadAndAnOddOneIsNothing() {
        val task = TaskRef.parse("42|com.example.app|1")!!
        assertEquals(42, task.id)
        assertEquals("com.example.app", task.packageName)
        assertTrue(task.running)
        assertFalse(TaskRef.parse("7|com.example.app|0")!!.running)
        assertNull(TaskRef.parse("x|com.example.app|1"))
        assertNull(TaskRef.parse("42|com.example.app"))
        assertNull(TaskRef.parse("42||1"))
        assertNull(TaskRef.parse("42|com.ex ample|1"))
        assertNull(TaskRef.parse("42|a|b|c"))
    }

    @Test
    fun thePreviewsAreOffUntilTheUserTurnsThemOnAndGiveNothingWithoutShizuku() {
        assertFalse(TaskThumbs.enabled(context))
        // Off: not even Shizuku is asked.
        assertTrue(TaskThumbs.load(context).isEmpty())
        TaskThumbs.setEnabled(context, true)
        assertTrue(TaskThumbs.enabled(context))
        // On, but no Shizuku in a unit test: no pictures, and no crash.
        assertTrue(TaskThumbs.load(context).isEmpty())
        TaskThumbs.setEnabled(context, false)
        assertFalse(TaskThumbs.enabled(context))
    }
}
