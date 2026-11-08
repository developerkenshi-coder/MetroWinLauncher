package com.metrowin.launcher

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.BackupExtrasStore
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LayoutBackup
import com.metrowin.launcher.data.LayoutBackupCodec
import com.metrowin.launcher.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupPrefsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun theChoicesOfTheSmallAppsGoThroughTheFileAndComeBack() {
        Prefs.open(context, Prefs.SHIZUKU).edit { putStringSet("buttons", setOf("wifi", "bluetooth")); putBoolean("watch", false); putBoolean("history", true) }
        Prefs.open(context, Prefs.DATA_SENSE).edit { putLong("limit_mb", 5000L); putInt("bill_day", 17) }
        // what is not a choice stays out of the file
        Prefs.open(context, Prefs.SHIZUKU).edit { putString("saved", "secret-command") }
        Prefs.open(context, Prefs.DATA_SENSE).edit { putInt("alert_level", 2) }

        val text = LayoutBackupCodec.encode(LayoutBackup(emptyList(), emptyList(), LauncherSettings(), BackupExtrasStore.read(context)), 1L)
        assertFalse(text.contains("secret-command"))
        assertFalse(text.contains("alert_level"))

        Prefs.open(context, Prefs.SHIZUKU).edit { clear() }
        Prefs.open(context, Prefs.DATA_SENSE).edit { clear() }
        BackupExtrasStore.apply(context, LayoutBackupCodec.decode(text, LauncherSettings())!!.extras!!)

        val shizuku = Prefs.open(context, Prefs.SHIZUKU)
        assertEquals(setOf("wifi", "bluetooth"), shizuku.getStringSet("buttons", null))
        assertFalse(shizuku.getBoolean("watch", true))
        assertTrue(shizuku.getBoolean("history", false))
        assertFalse(shizuku.contains("saved"))
        val data = Prefs.open(context, Prefs.DATA_SENSE)
        assertEquals(5000L, data.getLong("limit_mb", 0L))
        assertEquals(17, data.getInt("bill_day", 0))
    }

    @Test
    fun aFileWithoutThemLeavesThePhoneAsItIs() {
        Prefs.open(context, Prefs.DATA_SENSE).edit { putInt("bill_day", 9) }
        val old = LayoutBackupCodec.encode(LayoutBackup(emptyList(), emptyList(), LauncherSettings(), BackupExtrasStore.read(context).copy(prefs = emptyMap())), 1L)
        BackupExtrasStore.apply(context, LayoutBackupCodec.decode(old, LauncherSettings())!!.extras!!)
        assertEquals(9, Prefs.open(context, Prefs.DATA_SENSE).getInt("bill_day", 0))
    }
}
