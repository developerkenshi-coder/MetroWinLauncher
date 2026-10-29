package com.metrowin.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.AppCategories
import com.metrowin.launcher.data.AppList
import com.metrowin.launcher.data.AppLists
import com.metrowin.launcher.data.AppLabels
import com.metrowin.launcher.data.AppLock
import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.data.AppShortcuts
import com.metrowin.launcher.data.AppUsage
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.NewApps
import com.metrowin.launcher.data.SettingsCodec
import com.metrowin.launcher.model.AppCategory
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.ui.apps.AppListModel
import com.metrowin.launcher.ui.apps.ListRow
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The groups at the top of the app list, the categories, the names the user gives, the lock, the shortcuts of an app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppListGroupsTest {
    private fun app(id: String, label: String = id, installed: Long = 0L, category: AppCategory? = null) =
        AppItem(id, label, Launch.Action("x"), installedAt = installed, category = category)

    // --- most used

    @Test
    fun anAppOpenedOftenIsCountedAndOnlyThoseOpenedEnoughShow() {
        var counts = emptyMap<String, Int>()
        repeat(5) { counts = AppUsage.record(counts, "a") }
        repeat(3) { counts = AppUsage.record(counts, "b") }
        repeat(2) { counts = AppUsage.record(counts, "c") }
        assertEquals(listOf("a", "b"), AppUsage.top(counts, setOf("a", "b", "c")))
    }

    @Test
    fun theMostUsedGroupHoldsFourAtMostAndSkipsWhatIsHiddenOrGone() {
        val counts = ('a'..'g').mapIndexed { i, c -> c.toString() to 10 - i }.toMap()
        assertEquals(listOf("a", "b", "c", "d"), AppUsage.top(counts, counts.keys))
        assertEquals(listOf("b", "d", "e", "f"), AppUsage.top(counts, setOf("b", "d", "e", "f", "g")).take(4).sortedBy { it })
        assertEquals(listOf("a", "c", "d", "e"), AppUsage.top(counts, counts.keys - "b"))
    }

    @Test
    fun equalCountsKeepAStableOrder() {
        val counts = mapOf("b" to 4, "a" to 4, "c" to 4)
        assertEquals(listOf("a", "b", "c"), AppUsage.top(counts, counts.keys))
    }

    @Test
    fun theCountsSurviveSavingAndBadDataGivesNone() {
        val counts = mapOf("app:a" to 7, "app:b" to 3)
        assertEquals(counts, AppUsage.decode(AppUsage.encode(counts)))
        assertEquals(emptyMap<String, Int>(), AppUsage.decode("not json"))
        assertEquals(emptyMap<String, Int>(), AppUsage.decode(null))
    }

    @Test
    fun onlyTheMostUsedHundredAndFiftyAreKept() {
        var counts = (1..150).associate { "app$it" to it }
        counts = AppUsage.record(counts, "newcomer")
        assertEquals(150, counts.size)
        assertTrue("the least used is forgotten", "app1" !in counts)
        assertTrue("newcomer" in counts)
    }

    // --- new apps

    @Test
    fun theAppsInstalledThisWeekAreNewWithTheNewestFirst() {
        val now = 100L * DAY
        val items = listOf(
            app("old", installed = now - 30 * DAY), app("two", installed = now - 2 * DAY),
            app("one", installed = now - DAY), app("six", installed = now - 6 * DAY), app("unknown"),
        )
        assertEquals(listOf("one", "two", "six"), NewApps.recent(items, now).map { it.id })
    }

    @Test
    fun onlyThreeAreNewAndTheOnesInTheFutureAreNot() {
        val now = 100L * DAY
        val items = (1..5).map { app("n$it", installed = now - it * 3_600_000L) } + app("future", installed = now + DAY)
        assertEquals(listOf("n1", "n2", "n3"), NewApps.recent(items, now).map { it.id })
    }

    @Test
    fun aPhoneJustSetUpHasNothingNew() {
        val now = 100L * DAY
        val items = (1..40).map { app("a$it", installed = now - DAY) }
        assertTrue(NewApps.recent(items, now).isEmpty())
    }

    // --- categories

    @Test
    fun theChipsAreTheCategoriesOfTheAppsInTheOrderOfTheEnum() {
        val items = listOf(
            app("a", category = AppCategory.Social), app("b", category = AppCategory.Game),
            app("c"), app("d", category = AppCategory.Social),
        )
        assertEquals(listOf(AppCategory.Game, AppCategory.Social), AppCategories.present(items))
        assertEquals(listOf("a", "d"), AppCategories.filter(items, AppCategory.Social).map { it.id })
    }

    // --- the lists

    @Test
    fun theListsOfTheFirstStartAreTheCategoriesAndShowOnlyWhileAnAppBelongsToThem() {
        val items = listOf(app("a", category = AppCategory.Social), app("b"))
        assertEquals(AppCategory.entries.size, AppLists.DEFAULTS.size)
        assertEquals(listOf("social"), AppLists.visible(AppLists.DEFAULTS, items).map { it.id })
        // A list the user made shows even while it is empty, and takes the apps put in by hand.
        val mine = AppLists.create(AppLists.DEFAULTS, "  Work  ", "u1")
        assertEquals(listOf("social", "u1"), AppLists.visible(mine, items).map { it.id })
        val filled = AppLists.toggle(mine, "u1", "b")
        assertEquals(listOf("b"), AppLists.members(filled.last(), items).map { it.id })
        // The same touch takes it out again.
        assertEquals(emptySet<String>(), AppLists.toggle(filled, "u1", "b").last().items)
    }

    @Test
    fun aListNeedsANameAndAnyListCanBeRenamedOrDeleted() {
        assertEquals(AppLists.DEFAULTS, AppLists.create(AppLists.DEFAULTS, "   ", "u1"))
        assertEquals("a".repeat(AppLists.MAX_NAME), AppLists.create(emptyList(), "a".repeat(100), "u1").single().name)
        val renamed = AppLists.rename(AppLists.DEFAULTS, "game", "Play")
        assertEquals("Play", renamed.first { it.id == "game" }.name)
        // An empty name gives a list of a category its own name back; a made list keeps the one it had.
        assertEquals("", AppLists.rename(renamed, "game", " ").first { it.id == "game" }.name)
        assertEquals("Work", AppLists.rename(AppLists.create(emptyList(), "Work", "u1"), "u1", "").single().name)
        assertEquals(AppLists.DEFAULTS.size - 1, AppLists.delete(AppLists.DEFAULTS, "game").size)
    }

    @Test
    fun theListsSurviveSavingAndADeletedDefaultStaysDeleted() {
        val lists = AppLists.toggle(AppLists.create(AppLists.delete(AppLists.DEFAULTS, "game"), "Work", "u1"), "u1", "app:x")
        val saved = SettingsCodec.decode(SettingsCodec.encode(LauncherSettings(appLists = lists)))!!
        assertEquals(lists, saved.appLists)
        assertEquals(emptyList<AppList>(), SettingsCodec.decode(SettingsCodec.encode(LauncherSettings(appLists = emptyList())))!!.appLists)
        // A file from before the lists has none of them: the first start's lists.
        assertEquals(AppLists.DEFAULTS, SettingsCodec.decode("{}")!!.appLists)
    }

    @Test
    fun androidsCategoryNumbersMapToOurs() {
        assertEquals(AppCategory.Game, AppCategory.fromAndroid(0))
        assertEquals(AppCategory.Social, AppCategory.fromAndroid(4))
        assertEquals(AppCategory.Accessibility, AppCategory.fromAndroid(8))
        assertNull("undefined", AppCategory.fromAndroid(-1))
        assertNull("not known yet", AppCategory.fromAndroid(42))
    }

    // --- the model of the list

    @Test
    fun theGroupsComeAfterTheSearchBoxAndTheChipsAndBeforeTheLetters() {
        val items = AppSections.sort(listOf(app("a", "Alpha"), app("b", "Bravo")))
        val model = AppListModel(
            AppSections.group(items), frequent = listOf(items[1]), fresh = listOf(items[0]),
            lists = AppLists.DEFAULTS.take(1),
        )
        val kinds = model.rows.map { it::class.simpleName }
        assertEquals(listOf("Search", "Categories", "Section", "App", "Section", "App", "Header", "App", "Header", "App"), kinds)
        // An app in a group has its own key, so the list can show it twice.
        assertEquals(model.rows.size, model.rows.map { it.key }.distinct().size)
        // The jump list goes to the alphabetical row, not to the group.
        val row = model.rows[model.indexOfApp("b")!!] as ListRow.App
        assertEquals("", row.group)
    }

    @Test
    fun withNothingToGroupTheListIsAsItWas() {
        val model = AppListModel(AppSections.group(listOf(app("a", "Alpha"))))
        assertEquals(listOf("Search", "Header", "App"), model.rows.map { it::class.simpleName })
    }

    // --- names

    @Test
    fun aNewNameIsShownEverywhereAndTheListIsInOrderAgain() {
        val items = AppSections.sort(listOf(app("a", "Alpha"), app("b", "Bravo"), app("c", "Charlie")))
        val renamed = AppLabels.apply(items, mapOf("a" to "Zebra"))
        assertEquals(listOf("Bravo", "Charlie", "Zebra"), renamed.map { it.label })
        assertEquals("a", renamed.last().id)
        assertEquals("nothing renamed leaves the very same list", items, AppLabels.apply(items, emptyMap()))
    }

    @Test
    fun aNameIsTrimmedCutAndABlankOneGivesTheOriginalBack() {
        assertEquals("Mappe", AppLabels.clean("  Mappe  "))
        assertEquals(AppLabels.MAX, AppLabels.clean("x".repeat(100))!!.length)
        assertNull(AppLabels.clean("   "))
        assertNull(AppLabels.clean(""))
    }

    @Test
    fun theNamesSurviveSavingAndTheBlankOnesAreDropped() {
        val settings = LauncherSettings(
            appLabels = mapOf("app:a" to "Alpha 2", "app:b" to "Beta"), lockedApps = setOf("app:a"),
            showFrequentApps = false, showNewApps = false, showCategories = false,
        )
        assertEquals(settings, SettingsCodec.decode(SettingsCodec.encode(settings)))
        val withBlank = SettingsCodec.decode("""{"appLabels":{"app:a":"  ","app:b":"Beta"}}""")!!
        assertEquals(mapOf("app:b" to "Beta"), withBlank.appLabels)
        val defaults = LauncherSettings()
        assertTrue(defaults.showFrequentApps && defaults.showNewApps && defaults.showCategories)
        assertTrue(defaults.appLabels.isEmpty() && defaults.lockedApps.isEmpty())
        assertTrue(AppLabels.encode(mapOf("b" to "2", "a" to "1")).let { it is JSONObject && it.length() == 2 })
    }

    // --- lock

    @Test
    fun aLockedAppAsksForTheScreenLockOnlyWhenThePhoneHasOne() {
        val a = AppItem("app:a", "A", Launch.Activity(ComponentName("p", "p.A"), Process.myUserHandle()))
        assertTrue(AppLock.mustConfirm(a, setOf("app:a"), deviceSecure = true))
        assertFalse("nothing to confirm with", AppLock.mustConfirm(a, setOf("app:a"), deviceSecure = false))
        assertFalse(AppLock.mustConfirm(a, emptySet(), deviceSecure = true))
    }

    @Test
    fun onlyRealAppsCanBeLocked() {
        val real = AppItem("app:a", "A", Launch.Activity(ComponentName("p", "p.A"), Process.myUserHandle()))
        assertTrue(AppLock.canLock(real))
        assertFalse(AppLock.canLock(AppItem("x", "x", Launch.Internal("settings"))))
        assertFalse(AppLock.canLock(AppItem("y", "y", Launch.Link("https://example.com"), pinOnly = true)))
    }

    // --- the shortcuts apps offer

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun shortcut(id: String, label: String, rank: Int = 0): ShortcutInfo =
        ShortcutInfo.Builder(context, id).setShortLabel(label).setIntent(Intent(Intent.ACTION_VIEW)).setRank(rank).build()

    @Test
    fun theShortcutsAreOfferedInTheOrderTheAppRanksThemWithoutRepeats() {
        val found = listOf(shortcut("c", "Third", rank = 3), shortcut("a", "First", rank = 1), shortcut("b", "Second", rank = 2), shortcut("a2", "First", rank = 4))
        val chosen = AppShortcuts.choose(found, Process.myUserHandle())
        assertEquals(listOf("First", "Second", "Third"), chosen.map { it.label })
    }

    @Test
    fun onlyFourShortcutsAreOffered() {
        val found = (1..9).map { shortcut("s$it", "Shortcut $it", rank = it) }
        assertEquals(4, AppShortcuts.choose(found, Process.myUserHandle()).size)
    }

    @Test
    fun anAppAsksTheSystemForItsShortcutsOnlyWhenTheLauncherMayAsk() {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val component = ComponentName(context.packageName, "${context.packageName}.Main")
        val item = AppItem("app:x", "X", Launch.Activity(component, Process.myUserHandle()))
        shadowOf(launcherApps).addDynamicShortcut(shortcut("compose", "New message"))
        shadowOf(launcherApps).setHasShortcutHostPermission(false)
        assertTrue("another launcher is the home app: nothing is offered", AppShortcuts(context).of(item).isEmpty())
    }

    @Test
    fun aRefusalOfTheSystemGivesNoShortcutsInsteadOfACrash() {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val component = ComponentName(context.packageName, "${context.packageName}.Main")
        val item = AppItem("app:x", "X", Launch.Activity(component, Process.myUserHandle()))
        shadowOf(launcherApps).setHasShortcutHostPermission(true)
        // The test double of the system does not answer this query: it throws, as a real system can (SecurityException).
        assertTrue(AppShortcuts(context).of(item).isEmpty())
    }

    @Test
    fun anItemThatIsNotAnAppHasNoShortcuts() {
        assertTrue(AppShortcuts(context).of(AppItem("x", "x", Launch.Internal("settings"))).isEmpty())
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
