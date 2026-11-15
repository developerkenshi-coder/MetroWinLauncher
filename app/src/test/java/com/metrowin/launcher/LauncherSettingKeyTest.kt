package com.metrowin.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.LauncherSettingMatch
import com.metrowin.launcher.ui.settings.SettingsIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The results of the settings search sit in a list that wants a unique key for each: a category and its first page are the same section. */
@RunWith(RobolectricTestRunner::class)
class LauncherSettingKeyTest {
    @Test
    fun aCategoryAndItsFirstPageHaveDifferentKeys() {
        val page = LauncherSettingMatch(null, "StartTheme", "theme", "where")
        val category = LauncherSettingMatch(null, "StartTheme", "look", "where", category = true)
        assertNotEquals(page.key, category.key)
        assertEquals("theme.oled", LauncherSettingMatch("theme.oled", "StartTheme", "OLED", "w").key)
    }

    @Test
    fun everyWordTypedGivesOnlyUniqueRows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val index = SettingsIndex.searchIndex(context)
        listOf("theme", "tema", "start", "gestures", "battery", "a", "e", "o", "icon", "app").forEach { query ->
            val keys = index.search(query).map { hit -> LauncherSettingMatch(hit.setting?.id, hit.section.name, "", "", hit.category != null).key }
            assertEquals("duplicate key for \"$query\"", keys.size, keys.toSet().size)
        }
    }
}
