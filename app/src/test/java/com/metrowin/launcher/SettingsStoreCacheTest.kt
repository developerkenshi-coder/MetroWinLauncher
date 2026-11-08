package com.metrowin.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStoreCacheTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun loadDecodesOnceUntilTheNextSave() {
        val store = SettingsStore.of(context)
        store.save(LauncherSettings(tileColumns = 6))
        val first = store.load()
        assertSame(first, store.load())
        assertSame(first, SettingsStore(context).load())
        store.save(LauncherSettings(tileColumns = 4))
        assertEquals(4, store.load().tileColumns)
    }
}
