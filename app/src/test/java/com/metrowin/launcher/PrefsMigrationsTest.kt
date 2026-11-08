package com.metrowin.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.Prefs
import com.metrowin.launcher.data.PrefsMigrations
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrefsMigrationsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun eachStepRunsOnceInOrderAndTheVersionIsKept() {
        val ran = mutableListOf<Int>()
        val steps = listOf<(Context) -> Unit>({ ran += 0 }, { ran += 1 })
        PrefsMigrations.run(context, steps)
        PrefsMigrations.run(context, steps)
        assertEquals(listOf(0, 1), ran)
        assertEquals(2, Prefs.main(context).getInt(PrefsMigrations.VERSION_KEY, -1))
        PrefsMigrations.run(context, steps + { ran += 2 })
        assertEquals(listOf(0, 1, 2), ran)
    }
}
