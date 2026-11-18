package com.metrowin.launcher

import android.content.ComponentName
import android.os.Process
import com.metrowin.launcher.data.LiveEvent
import com.metrowin.launcher.data.LiveTodo
import com.metrowin.launcher.data.StartCache
import com.metrowin.launcher.data.StartLive
import com.metrowin.launcher.data.StartSnapshot
import com.metrowin.launcher.model.AppCategory
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.LiveKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StartCacheTest {
    @get:Rule val folder = TemporaryFolder()

    private val owner = Process.myUserHandle()
    private val event = LiveEvent("Dentist", 1_000L, false, 2_000L, 7L, "Via Roma", null, 3L)
    private val apps = listOf(
        AppItem("stock:phone", "Phone", Launch.Activity(ComponentName("com.x", "com.x.Main"), owner), Glyph.Phone, LiveKind.Phone, "com.x", removable = true, category = AppCategory.Social, installedAt = 5L),
        AppItem("settings", "Settings", Launch.Internal("start+settings"), Glyph.Settings),
        AppItem("stock:battery", "Battery", Launch.Action("android.settings.BATTERY_SAVER"), Glyph.Battery, LiveKind.Battery),
        // Not kept: the profile of work, a shortcut that lives on Start only.
        AppItem("app:a@10", "Work", Launch.Activity(ComponentName("com.y", "com.y.Main"), owner), isWork = true),
        AppItem("pin:1", "Site", Launch.Link("https://x.y"), pinOnly = true),
    )
    private val live = StartLive(event, listOf(event), 2, 5, LiveTodo(3, listOf("a", "b")), 9_000L)

    @Test fun writeThenRead() {
        StartCache.write(folder.root, StartSnapshot(apps, live), 62L, "en-US")
        val read = StartCache.read(folder.root, 62L, "en-US", owner)!!
        assertEquals(apps.take(3), read.apps)
        assertEquals(live, read.live)
    }

    @Test fun anotherVersionOrLanguageIsNoCache() {
        val json = StartCache.encode(StartSnapshot(apps, live), 62L, "en-US")
        assertNotNull(StartCache.decode(json, 62L, "en-US", owner))
        assertNull(StartCache.decode(json, 63L, "en-US", owner))
        assertNull(StartCache.decode(json, 62L, "it-IT", owner))
    }

    @Test fun damagedOrMissingFileIsNoCache() {
        assertNull(StartCache.read(folder.root, 62L, "en-US", owner))
        folder.newFile("start_cache.json").writeText("{garbage")
        assertNull(StartCache.read(folder.root, 62L, "en-US", owner))
        val good = StartCache.encode(StartSnapshot(apps, live), 62L, "en-US")
        assertNull(StartCache.decode(good.take(good.length / 2), 62L, "en-US", owner))
        assertNull(StartCache.decode(good.replace("\"Phone\"", "\"Phone\",\"glyph\":\"NoSuchGlyph\""), 62L, "en-US", owner))
        assertNull(StartCache.decode(null, 62L, "en-US", owner))
    }
}
