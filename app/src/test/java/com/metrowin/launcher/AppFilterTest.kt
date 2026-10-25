package com.metrowin.launcher

import com.metrowin.launcher.data.AppFilter
import com.metrowin.launcher.data.AppRepository
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.RecentApps
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFilterTest {
    private fun app(id: String, work: Boolean = false, private: Boolean = false, pinOnly: Boolean = false) =
        AppItem(id, id, Launch.Action("x"), isWork = work, isPrivate = private, pinOnly = pinOnly)

    private val items = listOf(app("a"), app("b"), app("w", work = true), app("p", private = true), app("s", pinOnly = true))

    private fun ids(list: List<AppItem>) = list.map { it.id }

    @Test
    fun theListShowsEveryAppButTheShortcutsThatLiveOnStart() {
        assertEquals(listOf("a", "b", "w", "p"), ids(AppFilter.visible(items, LauncherSettings())))
    }

    @Test
    fun hiddenAppsAreLeftOut() {
        assertEquals(listOf("a", "w", "p"), ids(AppFilter.visible(items, LauncherSettings(hiddenApps = setOf("b")))))
    }

    @Test
    fun workAndPrivateAppsCanBeSwitchedOff() {
        assertEquals(listOf("a", "b", "p"), ids(AppFilter.visible(items, LauncherSettings(showWorkApps = false))))
        assertEquals(listOf("a", "b", "w"), ids(AppFilter.visible(items, LauncherSettings(showPrivateApps = false))))
        assertEquals(listOf("a", "b"), ids(AppFilter.visible(items, LauncherSettings(showWorkApps = false, showPrivateApps = false))))
    }

    @Test
    fun theSettingsOfTheLauncherCannotBeHidden() {
        val own = AppItem(AppRepository.SETTINGS_ID, "settings", Launch.Internal("settings"))
        assertFalse(AppFilter.canHide(own))
        assertTrue(AppFilter.canHide(app("a")))
        assertFalse("a shortcut is not in the list to begin with", AppFilter.canHide(app("s", pinOnly = true)))
    }

    @Test
    fun theHiddenListHasWhatWasHiddenAndStillExists() {
        val settings = LauncherSettings(hiddenApps = setOf("b", "gone", "s"))
        assertEquals(listOf("b"), ids(AppFilter.hidden(items, settings)))
    }

    // --- recents

    @Test
    fun theAppOpenedLastGoesFirstAndIsNeverListedTwice() {
        assertEquals(listOf("c", "a", "b"), RecentApps.record(listOf("a", "b"), "c"))
        assertEquals(listOf("b", "a", "c"), RecentApps.record(listOf("a", "b", "c"), "b"))
    }

    @Test
    fun onlyTheLastFewAreKept() {
        val many = (1..15).map { "app$it" }
        val recorded = many.fold(emptyList<String>()) { list, id -> RecentApps.record(list, id) }
        assertEquals(RecentApps.MAX, recorded.size)
        assertEquals("app15", recorded.first())
    }

    @Test
    fun theStarGoesToTheLastOpenedAppThatIsStillThere() {
        val visible = setOf("a", "c")
        assertEquals("c", RecentApps.lastVisible(listOf("x", "c", "a"), visible))
        assertEquals("the first ones are gone or hidden", "a", RecentApps.lastVisible(listOf("x", "y", "a"), visible))
        assertNull(RecentApps.lastVisible(listOf("x", "y"), visible))
        assertNull(RecentApps.lastVisible(emptyList(), visible))
    }

    @Test
    fun recentsSurviveSavingAndBadDataGivesNone() {
        assertEquals(listOf("a:b", "c@1"), RecentApps.decode(RecentApps.encode(listOf("a:b", "c@1"))))
        assertEquals(emptyList<String>(), RecentApps.decode("nonsense"))
        assertEquals(emptyList<String>(), RecentApps.decode(null))
    }
}
