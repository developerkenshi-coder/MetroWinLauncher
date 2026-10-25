package com.metrowin.launcher.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.AppShortcut
import com.metrowin.launcher.data.Birthday
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.ShortcutDef
import com.metrowin.launcher.data.ShortcutKind
import com.metrowin.launcher.data.TileSpacing
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.StartEntry
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.apps.AppContextMenu
import com.metrowin.launcher.ui.apps.MenuState
import com.metrowin.launcher.ui.settings.SettingsEnv
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.settings.SettingsSection
import com.metrowin.launcher.ui.start.AddMenu
import com.metrowin.launcher.ui.start.ChecklistDialog
import com.metrowin.launcher.ui.start.CityPicker
import com.metrowin.launcher.ui.start.CounterDialog
import com.metrowin.launcher.ui.start.StartActions
import com.metrowin.launcher.ui.start.StartScreen
import com.metrowin.launcher.ui.start.TileFace
import com.metrowin.launcher.ui.start.buildStartCells
import com.metrowin.launcher.ui.theme.DefaultAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** The faces of the counter, the list, the clock, the moon and the birthdays, their dialogs, the add menu and the Start settings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class StartMoreUiTest : ScreenshotBase() {
    private fun counter(count: Long) = ShortcutDef("c", ShortcutKind.Counter, "Water", number = count).toItem(null)
    private fun list(raw: String) = ShortcutDef("l", ShortcutKind.Checklist, "Shop", text = raw).toItem(null)
    private val tokyo = ShortcutDef("w", ShortcutKind.WorldClock, "Tokyo", "Asia/Tokyo").toItem(null)
    private val moon = ShortcutDef("m", ShortcutKind.Moon, "Moon").toItem(null)
    private val birthdays = ShortcutDef("b", ShortcutKind.Birthdays, "Birthdays").toItem(null)

    private fun showFace(
        item: AppItem,
        size: TileSize = TileSize.Medium,
        live: LiveTileData = LiveTileData(),
        onStep: (Int) -> Unit = {},
    ) = show {
        val w = if (size == TileSize.Wide) 408.dp else if (size == TileSize.Small) 96.dp else 200.dp
        val h = if (size == TileSize.Small) 96.dp else 200.dp
        Box(Modifier.size(w, h).background(DefaultAccent)) {
            TileFace(item, size, w, h, live, seed = 0, notifications = 0, onCounterStep = onStep)
        }
    }

    // --- the counter

    @Test
    fun aCounterShowsItsCountAndItsName() {
        showFace(counter(7))
        advance(500)
        capture("tile_counter")
        compose.onNodeWithTag("counter:count").assertTextContains("7")
        compose.onAllNodesWithText("Water").assertCountEquals(1)
    }

    @Test
    fun theMinusButtonCountsOneLessAndIsOnlyThereWhenThereIsSomethingToTake() {
        val steps = mutableListOf<Int>()
        showFace(counter(3), onStep = { steps += it })
        advance(500)
        compose.onNodeWithTag("counter:minus").performClick()
        assertEquals(listOf(-1), steps)
    }

    @Test
    fun aCounterAtZeroHasNoMinusAndASmallOneNeverHas() {
        showFace(counter(0))
        advance(500)
        compose.onAllNodesWithTag("counter:minus").assertCountEquals(0)
        compose.onNodeWithTag("counter:count").assertTextContains("0")
    }

    @Test
    fun aSmallCounterIsJustTheNumber() {
        showFace(counter(12), TileSize.Small)
        advance(500)
        compose.onNodeWithTag("counter:count").assertTextContains("12")
        compose.onAllNodesWithTag("counter:minus").assertCountEquals(0)
        compose.onAllNodesWithText("Water").assertCountEquals(0)
    }

    // --- the list

    @Test
    fun aListShowsHowManyAreDoneAndTheFirstOnesLeft() {
        showFace(list("[x] eggs\n[ ] milk\n[ ] bread"))
        advance(500)
        capture("tile_checklist")
        compose.onNodeWithTag("checklist:progress").assertTextContains("1 of 3")
        compose.onAllNodesWithTag("checklist:pending").assertCountEquals(2)
        compose.onAllNodesWithText("· milk").assertCountEquals(1)
    }

    @Test
    fun aListWithNothingLeftSaysSo() {
        showFace(list("[x] a\n[x] b"))
        advance(500)
        compose.onNodeWithTag("checklist:alldone").assertExists()
        compose.onAllNodesWithTag("checklist:pending").assertCountEquals(0)
    }

    @Test
    fun aSmallListIsTheFraction() {
        showFace(list("[x] a\n[ ] b\n[ ] c"), TileSize.Small)
        advance(500)
        compose.onNodeWithTag("checklist:progress").assertTextContains("1/3")
    }

    @Test
    fun anEmptyListShowsItsNameLikeAnyTile() {
        showFace(list(""))
        advance(500)
        compose.onAllNodesWithTag("checklist:progress").assertCountEquals(0)
        compose.onAllNodesWithText("Shop").assertCountEquals(1)
    }

    // --- the world clock, the moon and the birthdays

    @Test
    fun theWorldClockShowsTheTimeAndTheCity() {
        showFace(tokyo)
        advance(500)
        capture("tile_worldclock")
        compose.onNodeWithTag("worldclock:time").assertExists()
        compose.onNodeWithTag("worldclock:city").assertTextContains("Tokyo")
    }

    @Test
    fun aSmallWorldClockOnlyHasTheTime() {
        showFace(tokyo, TileSize.Small)
        advance(500)
        compose.onNodeWithTag("worldclock:time").assertExists()
        compose.onAllNodesWithTag("worldclock:city").assertCountEquals(0)
    }

    @Test
    fun aCityThatDoesNotExistFallsBackToTheNameOfTheTile() {
        showFace(ShortcutDef("w", ShortcutKind.WorldClock, "Nowhere", "Mars/Olympus").toItem(null))
        advance(500)
        compose.onAllNodesWithTag("worldclock:time").assertCountEquals(0)
        compose.onAllNodesWithText("Nowhere").assertCountEquals(1)
    }

    @Test
    fun theMoonIsDrawnWithItsName() {
        showFace(moon, TileSize.Wide)
        advance(500)
        capture("tile_moon")
        compose.onNodeWithTag("moon:disc").assertExists()
        compose.onNodeWithTag("moon:name").assertExists()
    }

    @Test
    fun aSmallMoonIsOnlyTheDisc() {
        showFace(moon, TileSize.Small)
        advance(500)
        compose.onNodeWithTag("moon:disc").assertExists()
        compose.onAllNodesWithTag("moon:name").assertCountEquals(0)
    }

    private fun birthdayIn(days: Long, name: String, turning: Int? = 30): Birthday {
        val day = LocalDate.now().plusDays(days)
        return Birthday(name, day.monthValue, day.dayOfMonth, turning?.let { day.year - it })
    }

    @Test
    fun theBirthdaysShowTheNearestOneWithHowFarAndTheAge() {
        showFace(birthdays, TileSize.Wide, LiveTileData(birthdays = listOf(birthdayIn(5, "Marco"), birthdayIn(2, "Anna"))))
        advance(500)
        capture("tile_birthdays")
        compose.onNodeWithTag("birthday:name").assertTextContains("Anna")
        compose.onNodeWithTag("birthday:when").assertTextContains("2 days", substring = true)
        compose.onAllNodesWithText("turns 30").assertCountEquals(1)
    }

    @Test
    fun aBirthdayTodayIsToday() {
        showFace(birthdays, TileSize.Wide, LiveTileData(birthdays = listOf(birthdayIn(0, "Anna"))))
        advance(500)
        compose.onNodeWithTag("birthday:when").assertTextContains("today")
    }

    @Test
    fun theBirthdaysFlipToTheNextOnes() {
        showFace(birthdays, TileSize.Wide, LiveTileData(birthdays = listOf(birthdayIn(1, "Anna"), birthdayIn(3, "Marco"), birthdayIn(9, "Zoe"))))
        advance(3_000)
        compose.onAllNodesWithTag("birthday:next").assertCountEquals(3)
    }

    @Test
    fun noBirthdaysIsATileWithJustItsName() {
        showFace(birthdays, TileSize.Wide, LiveTileData())
        advance(500)
        compose.onAllNodesWithTag("birthday:name").assertCountEquals(0)
        compose.onAllNodesWithText("Birthdays").assertCountEquals(1)
    }

    // --- the dialogs

    /** The test clock is paused: what a touch changed is drawn once the clock moves. */
    private fun settle() {
        compose.waitForIdle()
        advance(100)
    }

    @Test
    fun aCounterIsNamedAndCanBeResetToZero() {
        var result: Pair<String, Boolean>? = null
        show { CounterDialog(initialName = "Water", count = 8, onDone = { n, r -> result = n to r }, onCancel = {}) }
        advance(300)
        capture("counter_dialog")
        compose.onNodeWithTag("counter:value").assertTextContains("8")
        compose.onNodeWithTag("counter:reset").performClick()
        compose.waitForIdle()
        advance(100)
        compose.onNodeWithTag("counter:value").assertTextContains("0")
        compose.onNode(hasSetTextAction()).performTextReplacement("Water and tea")
        compose.waitForIdle()
        advance(200)
        compose.onNodeWithTag("counter:ok").performClick()
        assertEquals("Water and tea" to true, result)
    }

    @Test
    fun aCounterDialogLeavesTheCountAloneIfNotReset() {
        var result: Pair<String, Boolean>? = null
        var cancelled = 0
        show { CounterDialog(initialName = "Coffee", count = 2, onDone = { n, r -> result = n to r }, onCancel = { cancelled++ }) }
        advance(300)
        compose.onNodeWithTag("counter:ok").performClick()
        assertEquals("Coffee" to false, result)
        compose.onNodeWithTag("counter:cancel").performClick()
        assertEquals(1, cancelled)
    }

    @Test
    fun aListIsFilledTickedAndCleanedInItsDialog() {
        var saved = ""
        var closed = 0
        show { ChecklistDialog(initialRaw = "[ ] bread", onChange = { saved = it }, onClose = { closed++ }) }
        advance(300)
        capture("checklist_dialog")
        compose.onNode(hasSetTextAction()).performTextInput("milk")
        compose.waitForIdle()
        advance(200)
        compose.onNodeWithTag("checklist:add").performClick()
        settle()
        assertEquals("[ ] bread\n[ ] milk", saved)

        compose.onNodeWithTag("checklist:item:0").performClick()
        settle()
        assertEquals("[x] bread\n[ ] milk", saved)

        compose.onNodeWithTag("checklist:cleardone").performClick()
        settle()
        assertEquals("[ ] milk", saved)
        compose.onAllNodesWithTag("checklist:cleardone").assertCountEquals(0)

        compose.onNodeWithTag("checklist:remove:0").performClick()
        settle()
        assertEquals("", saved)
        compose.onAllNodesWithText("Nothing on the list yet.").assertCountEquals(1)

        compose.onNodeWithTag("checklist:done").performClick()
        assertEquals(1, closed)
    }

    @Test
    fun aBlankItemIsNotAdded() {
        var saved = "unchanged"
        show { ChecklistDialog(initialRaw = "", onChange = { saved = it }, onClose = {}) }
        advance(300)
        compose.onNodeWithTag("checklist:add").performClick()
        assertEquals("unchanged", saved)
    }

    @Test
    fun theCitiesAreSearchedAndOneIsPicked() {
        var picked: String? = null
        show { CityPicker(onPick = { picked = it.name }, onDismiss = {}) }
        advance(300)
        capture("city_picker")
        compose.onNodeWithTag("city:Tokyo").assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("tok")
        compose.waitForIdle()
        advance(200)
        compose.onAllNodesWithTag("city:Rome").assertCountEquals(0)
        compose.onNodeWithTag("city:Tokyo").performClick()
        assertEquals("Tokyo", picked)
    }

    // --- the add menu

    @Test
    fun theAddMenuOffersTheNewTilesWhenItIsGivenThem() {
        var chosen: String? = null
        fun pick(name: String): () -> Unit = { chosen = name }
        show {
            AddMenu(
                onContact = pick("contact"), onSite = pick("site"), onWidget = pick("widget"), onDismiss = {},
                onNote = pick("note"), onCountdown = pick("countdown"), onCounter = pick("counter"), onChecklist = pick("checklist"),
                onWorldClock = pick("worldclock"), onMoon = pick("moon"), onBirthdays = pick("birthdays"),
            )
        }
        advance(300)
        capture("add_menu")
        listOf("counter", "checklist", "worldclock", "moon", "birthdays").forEach { compose.onNodeWithTag("add:$it").assertExists() }
        compose.onNodeWithTag("add:contact").performClick()
        assertEquals("contact", chosen)
        // The menu scrolls: the last entries are reached with a swipe.
        compose.onRoot().performTouchInput { swipeUp(startY = height * 0.85f, endY = height * 0.3f) }
        compose.waitForIdle()
        advance(500)
        compose.onNodeWithTag("add:birthdays").performClick()
        assertEquals("birthdays", chosen)
    }

    @Test
    fun theAddMenuLeavesOutWhatItIsNotGiven() {
        show { AddMenu(onContact = {}, onSite = {}, onWidget = {}, onDismiss = {}) }
        advance(300)
        listOf("counter", "checklist", "worldclock", "moon", "birthdays").forEach { compose.onAllNodesWithTag("add:$it").assertCountEquals(0) }
    }

    // --- Start

    private val apps = listOf("A", "B").mapIndexed { i, id -> AppItem(id, "App $id", Launch.Action("x"), Glyph.entries[i + 5]) }

    @Composable
    private fun Start(
        entries: List<StartEntry>,
        items: List<AppItem> = apps,
        actions: StartActions,
        editingKey: String? = null,
        showNames: Boolean = true,
        spacing: Float = 1f,
    ) = StartScreen(
        cells = buildStartCells(entries, items, null), live = LiveTileData(), badges = emptyMap(), columns = 4, background = null,
        enterToken = 0, exitRequest = null, editingKey = editingKey, scrollState = rememberScrollState(),
        actions = actions, showNames = showNames, spacing = spacing,
    )

    private fun actions(
        onEditContent: () -> Unit = {},
        onCounterStep: (String, Int) -> Unit = { _, _ -> },
        onBackgroundLongPress: (() -> Unit)? = null,
    ) = StartActions(
        onClick = {}, onLongClick = {}, onUnpin = {}, onResize = {}, onRename = {}, onMove = { _, _ -> }, onMerge = { _, _ -> },
        onBackgroundTap = {}, onNextPage = {},
        onEditContent = { onEditContent() }, onCounterStep = onCounterStep, onBackgroundLongPress = onBackgroundLongPress,
    )

    @Test
    fun theNamesUnderTheTilesCanBeTurnedOff() {
        val entries = listOf(Tile("A", TileSize.Medium), Tile("B", TileSize.Medium))
        show { Start(entries, actions = actions(), showNames = false) }
        advance(1500)
        compose.onAllNodesWithText("App A").assertCountEquals(0)
        compose.onAllNodesWithText("App B").assertCountEquals(0)
    }

    @Test
    fun theNamesAreThereByDefault() {
        val entries = listOf(Tile("A", TileSize.Medium), Tile("B", TileSize.Medium))
        show { Start(entries, actions = actions()) }
        advance(1500)
        compose.onAllNodesWithText("App A").assertCountEquals(1)
    }

    @Test
    fun theSpaceBetweenTheTilesFollowsTheSetting() {
        val entries = listOf(Tile("A", TileSize.Medium), Tile("B", TileSize.Medium))
        // One screen reads the gap through a state that changes.
        var spacing by mutableStateOf(0.5f)
        show { Start(entries, actions = actions(), spacing = spacing) }
        advance(1500)
        fun between(): Float {
            val a = compose.onNodeWithTag("tile:A").getUnclippedBoundsInRoot()
            val b = compose.onNodeWithTag("tile:B").getUnclippedBoundsInRoot()
            return (b.left - a.right).value
        }
        val tight = between()
        spacing = 1f
        compose.waitForIdle()
        advance(500)
        val normal = between()
        spacing = 1.7f
        compose.waitForIdle()
        advance(500)
        val wide = between()
        assertTrue("tight $tight < normal $normal < wide $wide", tight < normal && normal < wide)
    }

    @Test
    fun aLongPressOnTheEmptyPartOfStartAsksForTheAddMenu() {
        var asked = 0
        show { Start(emptyList(), actions = actions(onBackgroundLongPress = { asked++ })) }
        advance(500)
        compose.onRoot().performTouchInput { longClick(center) }
        compose.waitForIdle()
        advance(500)
        assertEquals(1, asked)
    }

    @Test
    fun withoutTheCallbackALongPressOnTheEmptyPartDoesNothing() {
        var asked = 0
        show { Start(emptyList(), actions = actions(onBackgroundLongPress = null)) }
        advance(500)
        compose.onRoot().performTouchInput { longClick(center) }
        compose.waitForIdle()
        advance(500)
        assertEquals(0, asked)
    }

    @Test
    fun aSelectedCounterOffersThePencilAndAnAppDoesNot() {
        var edits = 0
        val item = counter(4)
        val entries = listOf(Tile(item.id, TileSize.Medium), Tile("A", TileSize.Medium))
        show { Start(entries, items = apps + item, actions = actions(onEditContent = { edits++ }), editingKey = item.id) }
        advance(1500)
        compose.onAllNodesWithTag("tile:edit", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag("tile:edit", useUnmergedTree = true).performClick()
        assertEquals(1, edits)
    }

    @Test
    fun theMinusOfACounterOnStartTellsWhichCounter() {
        val steps = mutableListOf<Pair<String, Int>>()
        val item = counter(4)
        show { Start(listOf(Tile(item.id, TileSize.Medium)), items = apps + item, actions = actions(onCounterStep = { id, d -> steps += id to d })) }
        advance(1500)
        compose.onNodeWithTag("counter:minus", useUnmergedTree = true).performClick()
        assertEquals(listOf("c" to -1), steps)
    }

    // --- the app menu

    private val shortcut = AppShortcut("compose", "com.example.mail", "New message", android.os.Process.myUserHandle())

    @Test
    fun anAppShortcutHasAPlusThatPutsItOnStart() {
        var started: String? = null
        var pinned: String? = null
        show {
            AppContextMenu(
                state = MenuState(AppItem("com.example.mail", "Mail", Launch.Action("x"), Glyph.Mail), top = 100f, height = 80f),
                pinned = false, onPinToggle = {}, onUninstall = {}, onDismiss = {},
                shortcuts = listOf(shortcut), onShortcut = { started = it.id }, onPinShortcut = { pinned = it.id },
            )
        }
        advance(300)
        capture("app_menu_shortcut_pin")
        compose.onNodeWithTag("menu:shortcutpin:compose").performClick()
        assertEquals("compose", pinned)
        assertEquals(null, started)
        compose.onNodeWithTag("menu:shortcut:compose").performClick()
        assertEquals("compose", started)
    }

    @Test
    fun withoutAWayToPinThereIsNoPlus() {
        show {
            AppContextMenu(
                state = MenuState(AppItem("com.example.mail", "Mail", Launch.Action("x"), Glyph.Mail), top = 100f, height = 80f),
                pinned = false, onPinToggle = {}, onUninstall = {}, onDismiss = {}, shortcuts = listOf(shortcut),
            )
        }
        advance(300)
        compose.onAllNodesWithTag("menu:shortcutpin:compose").assertCountEquals(0)
        compose.onNodeWithTag("menu:shortcut:compose").assertExists()
    }

    // --- the settings

    private var current = LauncherSettings()
    private var resets = 0

    @Composable
    private fun Page() {
        var settings by remember { mutableStateOf(current) }
        SettingsScreen(
            env = SettingsEnv(settings = settings, onChange = { change -> settings = change(settings); current = settings }, onResetStart = { resets++ }),
            section = SettingsSection.Start, onSectionChange = {}, sectionAway = remember { Animatable(0f) },
        )
    }

    private fun tap(tag: String) {
        scrollIntoView(tag, topMargin = 0.2f)
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
        advance(100)
    }

    @Test
    fun theNamesAndTheSpacingAreChosenInTheSettings() {
        current = LauncherSettings()
        show { Page() }
        advance(500)
        tap("start:tilenames")
        assertFalse(current.showTileNames)
        tap("start:tilenames")
        assertTrue(current.showTileNames)
        tap("start:spacing:header")
        tap("start:spacing:${TileSpacing.entries.indexOf(TileSpacing.Wide)}")
        assertEquals(TileSpacing.Wide, current.tileSpacing)
    }

    @Test
    fun startIsRestoredOnlyAfterTheSecondTouch() {
        current = LauncherSettings()
        resets = 0
        show { Page() }
        advance(500)
        tap("start:resetstart")
        assertEquals("the first touch only asks", 0, resets)
        compose.onAllNodesWithText("Touch again to restore Start").assertCountEquals(1)
        capture("settings_reset_start")
        tap("start:resetstart")
        assertEquals(1, resets)
        compose.onAllNodesWithText("Touch again to restore Start").assertCountEquals(0)
    }
}
