package com.metrowin.launcher.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.start.PlaceDialog
import com.metrowin.launcher.ui.start.GroupPicker
import com.metrowin.launcher.ui.start.AlbumPicker
import com.metrowin.launcher.ui.pages.TimerPage
import com.metrowin.launcher.ui.pages.StopwatchPage
import com.metrowin.launcher.ui.pages.RemindersPage
import com.metrowin.launcher.ui.pages.PeopleHubPage
import com.metrowin.launcher.ui.pages.NewsPage
import com.metrowin.launcher.ui.pages.FinancePage
import com.metrowin.launcher.ui.pages.DialGridPage
import com.metrowin.launcher.ui.actioncenter.VolumeHudPanel
import com.metrowin.launcher.ui.actioncenter.ToastBanner
import com.metrowin.launcher.notifications.MediaInfo
import com.metrowin.launcher.model.TimerState
import com.metrowin.launcher.model.StopwatchState
import com.metrowin.launcher.data.VolumeStream
import com.metrowin.launcher.data.VolumeRow
import com.metrowin.launcher.data.VolumeHud
import com.metrowin.launcher.data.TodayExtras
import com.metrowin.launcher.data.Today
import com.metrowin.launcher.data.Reminder
import com.metrowin.launcher.data.RecentCall
import com.metrowin.launcher.data.Quote
import com.metrowin.launcher.data.QuickBadge
import com.metrowin.launcher.data.NewsItem
import com.metrowin.launcher.data.NewsData
import com.metrowin.launcher.data.LockStatus
import com.metrowin.launcher.data.GroupInfo
import com.metrowin.launcher.data.FinanceData
import com.metrowin.launcher.data.Favourite
import com.metrowin.launcher.data.DetailLine
import com.metrowin.launcher.data.AlbumInfo
import android.media.session.MediaSession
import android.media.session.MediaController
import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.data.AppShortcut
import com.metrowin.launcher.data.BatteryInfo
import com.metrowin.launcher.data.BatteryState
import com.metrowin.launcher.data.Birthday
import com.metrowin.launcher.data.DayForecast
import com.metrowin.launcher.data.EventMatch
import com.metrowin.launcher.data.GlobalResults
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LiveEvent
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.LockInfo
import com.metrowin.launcher.data.NextAlarm
import com.metrowin.launcher.data.ProfileInfo
import com.metrowin.launcher.data.ProfileType
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.data.QuickAnswer
import com.metrowin.launcher.data.ShortcutDef
import com.metrowin.launcher.data.ShortcutKind
import com.metrowin.launcher.data.StorageInfo
import com.metrowin.launcher.data.ThemeStyle
import com.metrowin.launcher.data.Tutorial
import com.metrowin.launcher.data.WeatherCondition
import com.metrowin.launcher.data.WeatherData
import com.metrowin.launcher.data.WeatherDetails
import com.metrowin.launcher.model.AppCategory
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.LiveKind
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.model.TileStyle
import com.metrowin.launcher.notifications.NotificationEntry
import com.metrowin.launcher.ui.actioncenter.ActionCenter
import com.metrowin.launcher.ui.actioncenter.ActionControls
import com.metrowin.launcher.ui.actioncenter.QuickButton
import com.metrowin.launcher.ui.apps.AppContextMenu
import com.metrowin.launcher.ui.apps.AppListModel
import com.metrowin.launcher.ui.apps.AppListScreen
import com.metrowin.launcher.ui.apps.JumpList
import com.metrowin.launcher.ui.apps.MenuState
import com.metrowin.launcher.ui.gesture.rememberPullPanel
import com.metrowin.launcher.ui.kids.KidsHeader
import com.metrowin.launcher.ui.lock.LockOptions
import com.metrowin.launcher.ui.lock.MetroLockScreen
import com.metrowin.launcher.ui.settings.SettingsEnv
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.settings.SettingsSection
import com.metrowin.launcher.ui.start.AddMenu
import com.metrowin.launcher.ui.start.ChecklistDialog
import com.metrowin.launcher.ui.start.CityPicker
import com.metrowin.launcher.ui.start.CountdownDialog
import com.metrowin.launcher.ui.start.CounterDialog
import com.metrowin.launcher.ui.start.EditBar
import com.metrowin.launcher.ui.start.FolderChoice
import com.metrowin.launcher.ui.start.FolderChooser
import com.metrowin.launcher.ui.start.FolderNameDialog
import com.metrowin.launcher.ui.start.FolderPickBar
import com.metrowin.launcher.ui.start.NoteDialog
import com.metrowin.launcher.ui.start.SettingPicker
import com.metrowin.launcher.ui.start.SiteDialog
import com.metrowin.launcher.ui.start.TileColorSheet
import com.metrowin.launcher.ui.start.TileFace
import com.metrowin.launcher.ui.start.UndoBar
import com.metrowin.launcher.ui.theme.DefaultAccent
import com.metrowin.launcher.ui.tutorial.TutorialScreen
import com.metrowin.launcher.ui.voice.VoiceScreen
import com.metrowin.launcher.ui.voice.VoiceUiState
import com.metrowin.launcher.ui.weather.WeatherScreen
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/**
 * Whether the text was cut: a line ended in "…", or the lines do not all fit in the height they were given. (The
 * width flag of the layout is not used: it is set for every text that is laid out in a box wider than the text.)
 */
private val KEPT = setOf("TextFitItalianNormalTest", "TextFitItalianStressTest", "TextFitItalianHugeTest", "TextFitItalianLandscapeTest")

fun isCut(layout: TextLayoutResult): Boolean = layout.didOverflowHeight || (0 until layout.lineCount).any { layout.isLineEllipsized(it) }

/** Whether a word was broken in the middle ("Bluetoot" / "h"): a line that ends where the word goes on. */
fun brokenWord(layout: TextLayoutResult): Boolean {
    val text = layout.layoutInput.text.text
    return (0 until layout.lineCount - 1).any { line ->
        val end = layout.getLineEnd(line)
        end in 1 until text.length && text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()
    }
}

/**
 * No text of the launcher may be cut or run off the screen, whatever the language, the size of the text or the look.
 *
 * Every screen is drawn in the worst cases (Italian, whose words are long; text 30 percent bigger; a 320 dp wide screen;
 * each of the three looks) and every piece of text is asked how it was laid out: one that was cut short ("…"), or that
 * does not fit in its box, or that sits beyond the edge of the screen, is a failure. What the user writes (the name of an
 * app, a note) may be cut in a list, so the screens here use short names.
 */
abstract class TextFitBase(private val scale: Float, private val style: ThemeStyle, private val widthDp: Int, private val heightDp: Int = 760) : ScreenshotBase() {
    private val screenWidth = widthDp.dp

    protected fun showFit(content: @Composable () -> Unit) =
        show(size = DpSize(screenWidth, heightDp.dp), textScale = scale, style = style, content = content)

    /** Draws a screen, lets it settle, and fails with every text that did not fit. */
    protected fun fit(name: String, scrollable: Set<String> = emptySet(), fixedHeight: Boolean = false, overlaps: Boolean = true, content: @Composable () -> Unit) {
        showFit(content)
        advance(2_000)
        val rootSize = compose.onRoot().fetchSemanticsNode().size
        val rootWidth = rootSize.width
        val bad = mutableListOf<String>()
        val boxes = mutableListOf<Pair<String, androidx.compose.ui.geometry.Rect>>()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
            if (node.config.contains(SemanticsProperties.EditableText)) return@forEach
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" | ") { it.text }.orEmpty()
            if (text.isBlank()) return@forEach
            val layouts = ArrayList<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            val layout = layouts.firstOrNull()
            val bounds = node.boundsInRoot
            val why = buildList {
                if (layout != null && isCut(layout)) add("cut")
                if (layout != null && brokenWord(layout)) add("word broken")
                if (bounds.left < -1f || bounds.right > rootWidth + 1f) add("off the screen")
                // A menu, a dialog or a bar does not scroll: what is below the screen cannot be reached.
                if (fixedHeight && (bounds.top < -1f || bounds.bottom > rootSize.height + 1f)) add("off the screen (height)")
            }
            if (why.isNotEmpty() && scrollable.none { text.contains(it) }) bad += "  [${why.joinToString()}] \"$text\""
            if (bounds.width > 0f && bounds.height > 0f && bounds.bottom > 0f && bounds.top < rootSize.height && bounds.right > 0f && bounds.left < rootWidth) boxes += text to bounds
        }
        // Two texts on top of each other cannot both be read.
        if (overlaps) for (i in boxes.indices) for (j in i + 1 until boxes.size) {
            val overlap = boxes[i].second.intersect(boxes[j].second)
            if (overlap.width <= 0f || overlap.height <= 0f) continue
            val smaller = minOf(boxes[i].second.width * boxes[i].second.height, boxes[j].second.width * boxes[j].second.height)
            if (overlap.width * overlap.height > 0.3f * smaller) bad += "  [overlap] \"${boxes[i].first}\" and \"${boxes[j].first}\""
        }
        // The pictures of the two Italian runs are kept, to be looked at.
        if (javaClass.simpleName in KEPT) capture("textfit_${javaClass.simpleName}_$name")
        if (bad.isNotEmpty()) {
            val label = "${javaClass.simpleName}: $name"
            File("build/textfit").apply { mkdirs() }.resolve("${javaClass.simpleName}.txt").appendText(label + "\n" + bad.joinToString("\n") + "\n")
            fail("$label\n" + bad.joinToString("\n"))
        }
    }

    // ---- sample data

    private val apps = listOf("Alpha", "Beta", "Gamma").map { AppItem("app:$it", it, Launch.Action("x"), Glyph.entries[it.length], packageName = "com.example.${it.lowercase()}") }

    private fun settingsEnv(section: SettingsSection) = SettingsEnv(
        settings = LauncherSettings(hiddenApps = setOf("app:Beta"), appLabels = mapOf("app:Alpha" to "Uno"), lockedApps = setOf("app:Gamma")),
        onChange = {}, apps = apps, onLanguage = {}, onAddRecentsTile = {}, backupFolderName = "Documents/MetroWin",
        profiles = listOf(ProfileInfo(10, ProfileType.Work, false), ProfileInfo(11, ProfileType.Private, true)),
        lockServiceEnabled = section == SettingsSection.Gestures,
    )

    @Composable
    private fun SettingsPage(section: SettingsSection?) {
        SettingsScreen(env = settingsEnv(section ?: SettingsSection.StartTheme), section = section, onSectionChange = {}, sectionAway = remember { Animatable(0f) })
    }

    // ---- the settings, one test for each section

    @Test fun settingsList() = fit("settings_list", overlaps = false) { SettingsPage(null) }
    @Test fun settingsStartTheme() = fit("settings_start_theme", overlaps = false) { SettingsPage(SettingsSection.StartTheme) }
    @Test fun settingsStartPage() = fit("settings_start_screen", overlaps = false) { SettingsPage(SettingsSection.Start) }
    @Test fun settingsSearchPage() = fit("settings_search_page", overlaps = false) { SettingsPage(SettingsSection.Search) }
    @Test fun settingsScreenPage() = fit("settings_screen", overlaps = false) { SettingsPage(SettingsSection.Screen) }
    @Test fun settingsBattery() = fit("settings_battery", overlaps = false) { SettingsPage(SettingsSection.Battery) }
    @Test fun settingsAccessibility() = fit("settings_accessibility", overlaps = false) { SettingsPage(SettingsSection.Accessibility) }
    @Test fun settingsIcons() = fit("settings_icons", overlaps = false) { SettingsPage(SettingsSection.Icons) }
    @Test fun settingsGestures() = fit("settings_gestures", overlaps = false) { SettingsPage(SettingsSection.Gestures) }
    @Test fun settingsActions() = fit("settings_actions", overlaps = false) { SettingsPage(SettingsSection.Actions) }
    @Test fun settingsLiveTiles() = fit("settings_live_tiles", overlaps = false) { SettingsPage(SettingsSection.LiveTiles) }
    @Test fun settingsApps() = fit("settings_apps", overlaps = false) { SettingsPage(SettingsSection.Apps) }
    @Test fun settingsLock() = fit("settings_lock", overlaps = false) { SettingsPage(SettingsSection.Lock) }
    @Test fun settingsVoice() = fit("settings_voice", overlaps = false) { SettingsPage(SettingsSection.Voice) }
    @Test fun settingsProfiles() = fit("settings_profiles", overlaps = false) { SettingsPage(SettingsSection.Profiles) }
    @Test fun settingsBackup() = fit("settings_backup", overlaps = false) { SettingsPage(SettingsSection.Backup) }
    @Test fun settingsKids() = fit("settings_kids", overlaps = false) { SettingsPage(SettingsSection.Kids) }
    @Test fun settingsAbout() = fit("settings_about", overlaps = false) { SettingsPage(SettingsSection.About) }

    // ---- the Action Center

    @Test fun actionCenter() = fit("action_center") {
        ActionCenter(
            slots = listOf(QuickAction.Wifi, QuickAction.Bluetooth, QuickAction.QuietHours, QuickAction.LauncherSettings),
            states = emptyMap(),
            notifications = listOf(NotificationEntry("k1", "com.example.alpha", "Anna", "Ci vediamo alle otto?", System.currentTimeMillis() - 90_000, null, false)),
            appsByPackage = emptyMap(), hasNotificationAccess = true, panel = rememberPullPanel(initialProgress = 1f),
            onAction = {}, onAllSettings = {}, onOpenNotification = {}, onDismissNotification = {}, onClearAll = {},
            onTurnOnNotificationAccess = {}, onClose = {},
            controls = ActionControls(
                volume = 0.4f, ringer = com.metrowin.launcher.data.RingerMode.Normal, brightness = 0.5f, canSetBrightness = false,
                onVolume = {}, onRinger = {}, onBrightness = {}, onAllowBrightness = {}, torch = 0.5f,
            ),
            onSnooze = { _, _ -> }, onAppNotificationSettings = {},
        )
    }

    @Test fun actionCenterWithoutAccess() = fit("action_center_no_access") {
        ActionCenter(
            slots = QuickAction.defaults, states = emptyMap(), notifications = emptyList(), appsByPackage = emptyMap(),
            hasNotificationAccess = false, panel = rememberPullPanel(initialProgress = 1f),
            onAction = {}, onAllSettings = {}, onOpenNotification = {}, onDismissNotification = {}, onClearAll = {},
            onTurnOnNotificationAccess = {}, onClose = {},
        )
    }

    @Composable
    private fun QuickButtons(actions: List<QuickAction>) {
        // The size they have in the panel: four across, with the 12 dp margins and 6 dp gaps.
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            actions.chunked(QuickAction.SLOTS).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { QuickButton(it, true, {}, Modifier.weight(1f)) }
                }
            }
        }
    }

    /**
     * Every button, in groups of two rows: the panel scrolls, but this grid does not, and on a screen that is short (a phone on its side)
     * more rows than fit would be squeezed on top of one another by the grid, not by the buttons.
     */
    private fun quickGroup(index: Int, name: String) {
        val group = QuickAction.entries.chunked(QuickAction.SLOTS * 2).getOrNull(index) ?: return
        fit(name) { QuickButtons(group + List((QuickAction.SLOTS - group.size % QuickAction.SLOTS) % QuickAction.SLOTS) { _ -> group.first() }) }
    }

    @Test fun everyQuickButton() = quickGroup(0, "quick_buttons")
    @Test fun everyQuickButtonSecondGroup() = quickGroup(1, "quick_buttons_2")
    @Test fun everyQuickButtonThirdGroup() = quickGroup(2, "quick_buttons_3")

    // ---- Start: menus, dialogs and bars

    @Test fun addMenu() = fit("add_menu", fixedHeight = true) {
        AddMenu(
            onContact = {}, onSite = {}, onWidget = {}, onDismiss = {}, onSpeedDial = {}, onSetting = {}, onNote = {}, onCountdown = {},
            onCounter = {}, onChecklist = {}, onWorldClock = {}, onMoon = {}, onBirthdays = {},
        )
    }

    @Test fun noteDialog() = fit("note_dialog", fixedHeight = true) { NoteDialog(initial = "", onDone = {}, onCancel = {}) }
    @Test fun countdownDialog() = fit("countdown_dialog", fixedHeight = true) { CountdownDialog(initialTitle = "", initialDay = LocalDate.of(2026, 12, 25).toEpochDay(), onDone = { _, _ -> }, onCancel = {}) }
    @Test fun counterDialog() = fit("counter_dialog", fixedHeight = true) { CounterDialog(initialName = "", count = 4, onDone = { _, _ -> }, onCancel = {}) }
    @Test fun checklistDialog() = fit("checklist_dialog", fixedHeight = true) { ChecklistDialog(initialRaw = "[x] pane\n[ ] latte", onChange = {}, onClose = {}) }
    @Test fun cityPicker() = fit("city_picker", fixedHeight = true) { CityPicker(onPick = {}, onDismiss = {}) }
    @Test fun siteDialog() = fit("site_dialog", fixedHeight = true) { SiteDialog(onDone = { _, _ -> true }, onCancel = {}) }
    @Test fun folderNameDialog() = fit("folder_name_dialog", fixedHeight = true) { FolderNameDialog(initial = "", onDone = {}, onCancel = {}) }
    @Test fun renameAppDialog() = fit("rename_dialog", fixedHeight = true) { FolderNameDialog(initial = "", onDone = {}, onCancel = {}, title = R.string.app_rename_title) }
    @Test fun folderChooser() = fit("folder_chooser", fixedHeight = true) { FolderChooser(listOf(FolderChoice("f1", "Giochi", 3), FolderChoice("f2", "Lavoro", 1)), onPick = {}, onCancel = {}) }
    @Test fun colorSheet() = fit("color_sheet", fixedHeight = true) { TileColorSheet(current = 0, onPick = {}, onDismiss = {}) }
    @Test fun undoBar() = fit("undo_bar", fixedHeight = true) { UndoBar(message = stringResource(R.string.undo_reset), onUndo = {}) }
    @Test fun jumpList() = fit("jump_list", fixedHeight = true) { JumpList(keys = listOf("#") + ('a'..'z').map { it.toString() }, available = setOf("a", "b", "c"), onPick = {}, onDismiss = {}, hasRecent = true) }

    @Test fun settingPicker() = fit("setting_picker", fixedHeight = true) {
        val defs = com.metrowin.launcher.data.GlobalSearch.systemSettings.map { it to stringResource(it.label) }
        SettingPicker(settings = defs, onPick = {}, onDismiss = {})
    }

    @Test fun editBar() = fit("edit_bar", fixedHeight = true) {
        EditBar(
            size = TileSize.Medium, page = 0, pageCount = 3, onSize = {}, onMovePage = {}, onDone = {}, style = TileStyle.Standard,
            onStyle = {}, onAdd = {}, onFolder = {},
        )
    }

    @Test fun folderPickBar() = fit("folder_pick_bar", fixedHeight = true) { FolderPickBar(picked = 3, canCreate = true, joining = false, onCreate = {}, onCancel = {}) }
    @Test fun folderPickBarJoining() = fit("folder_pick_bar_joining", fixedHeight = true) { FolderPickBar(picked = 2, canCreate = true, joining = true, onCreate = {}, onCancel = {}) }

    // ---- the app list

    private val shortcut = AppShortcut("compose", "com.example.alpha", "Nuovo messaggio", android.os.Process.myUserHandle())

    @Test fun appMenuFull() = fit("app_menu_full", fixedHeight = true) {
        AppContextMenu(
            state = MenuState(apps[0], top = 100f, height = 190f), pinned = false, onPinToggle = {}, onUninstall = {}, onDismiss = {},
            onHide = {}, onAddToFolder = {}, shortcuts = listOf(shortcut), onShortcut = {}, onRename = {}, onResetName = {}, onInfo = {},
            locked = false, onToggleLock = {}, onPinShortcut = {}, onStore = {}, onShare = {},
        )
    }

    @Composable
    private fun List(query: String, answers: List<QuickAnswer> = emptyList(), global: GlobalResults = GlobalResults.Empty, recent: List<String> = emptyList()) {
        val model = remember { AppListModel(AppSections.group(AppSections.sort(apps)), frequent = apps.take(1), fresh = apps.take(1), categories = AppCategory.entries.take(4)) }
        AppListScreen(
            model = model, listState = rememberLazyListState(), replayToken = 0, exiting = false, onLaunch = {}, onLongPress = { _, _, _ -> },
            onHeaderClick = {}, query = query, onQueryChange = {}, results = apps.take(1), global = global, answers = answers,
            recentSearches = recent, categories = AppCategory.entries.take(4),
        )
    }

    @Test fun appListHome() = fit("app_list", scrollable = setOf("cat:")) { List("") }

    @Test fun searchAnswers() = fit("search_answers") {
        List(
            "x",
            answers = listOf(
                QuickAnswer.Calculation("2+2", "4"), QuickAnswer.Conversion("5 km = 3.1 mi", "3.1 mi"), QuickAnswer.Call("3331234567"),
                QuickAnswer.Message("3331234567"), QuickAnswer.Link("https://a.com", "a.com"), QuickAnswer.Email("a@b.it"),
                QuickAnswer.Timer(5400), QuickAnswer.Alarm(7, 30), QuickAnswer.WorldTime("New York", LocalTime.of(17, 30), 0),
                QuickAnswer.DateOffset(LocalDate.of(2026, 12, 25), 61), QuickAnswer.DaysUntil(LocalDate.of(2026, 12, 25), 61),
                QuickAnswer.Dice(2, 6, listOf(3, 4), 2), QuickAnswer.Coin(true), QuickAnswer.Currency("100 USD = 92.59 EUR", "92.59", "2026-10-23"),
            ),
            global = GlobalResults(
                settings = com.metrowin.launcher.data.GlobalSearch.matchSettings("a") { context.getString(it.label) }.take(2), webQuery = "x",
                events = listOf(EventMatch(1, "Dentista", 1_800_000_000_000L, false)),
            ),
        )
    }

    @Test fun searchRecent() = fit("search_recent") { List("", recent = listOf("pizza", "meteo")) }

    // ---- other screens

    @Test fun voiceListening() = fit("voice_listening", fixedHeight = true) { VoiceScreen(VoiceUiState.Listening(), {}, {}, {}) }
    @Test fun voiceHeard() = fit("voice_heard", fixedHeight = true) { VoiceScreen(VoiceUiState.Understood("apri la fotocamera"), {}, {}, {}) }
    @Test fun voiceNotFound() = fit("voice_not_found", fixedHeight = true) { VoiceScreen(VoiceUiState.Problem(R.string.voice_not_found, "Xyz", "Xyz"), {}, {}, {}) }
    @Test fun voiceNoContact() = fit("voice_no_contact", fixedHeight = true) { VoiceScreen(VoiceUiState.Problem(R.string.voice_no_contact, "Marco"), {}, {}, {}) }
    @Test fun voiceBrightness() = fit("voice_brightness", fixedHeight = true) { VoiceScreen(VoiceUiState.Problem(R.string.voice_brightness_denied), {}, {}, {}) }
    @Test fun voiceSilent() = fit("voice_silent", fixedHeight = true) { VoiceScreen(VoiceUiState.Problem(R.string.voice_silent_denied), {}, {}, {}) }
    @Test fun voiceNothing() = fit("voice_nothing", fixedHeight = true) { VoiceScreen(VoiceUiState.Problem(R.string.voice_nothing), {}, {}, {}) }

    private val sunny = WeatherData(
        temperature = 21, condition = WeatherCondition.PartlyCloudy, isDay = true, fahrenheit = false, fetchedAt = System.currentTimeMillis(),
        days = listOf(
            DayForecast(LocalDate.of(2026, 10, 25), WeatherCondition.Clear, 22, 11, 10),
            DayForecast(LocalDate.of(2026, 10, 26), WeatherCondition.Thunderstorm, 17, 9, 80),
            DayForecast(LocalDate.of(2026, 10, 27), WeatherCondition.Showers, 15, 8, 60),
        ),
        details = WeatherDetails(feelsLike = 20, humidity = 55, wind = 12, rainChance = 30, sunrise = LocalTime.of(6, 40), sunset = LocalTime.of(17, 5), uvIndex = 4),
    )

    @Test fun weatherPage() = fit("weather_page") { WeatherScreen(sunny, "Torino, Piemonte, Italia", onRefresh = {}, onChooseCity = {}) }
    @Test fun weatherPageEmpty() = fit("weather_page_empty") { WeatherScreen(null, "", onRefresh = {}, onChooseCity = {}) }

    @Test fun lockScreen() = fit("lock_screen", fixedHeight = true) {
        MetroLockScreen(
            info = LockInfo(
                nowMillis = System.currentTimeMillis(), nextAlarmMillis = System.currentTimeMillis() + 86_400_000L, notifications = 3,
                battery = BatteryInfo(85, BatteryState.Charging), weather = sunny,
                nextEvent = LiveEvent("Riunione con il commercialista", System.currentTimeMillis() + 7_200_000L, false),
            ),
            photo = null, options = LockOptions(),
        )
    }

    @Test fun kidsHeader() = fit("kids_header", fixedHeight = true) { KidsHeader(onExit = {}) }

    // ---- the tiles: the words that live tiles draw on themselves

    private fun tile(item: AppItem, size: TileSize, live: LiveTileData) = @Composable {
        // Four small tiles across the screen, 12 dp from the edges and 6 dp apart: a medium tile is two of them, a wide one four.
        val unit = (screenWidth - 24.dp - 18.dp) / 4
        val w = if (size == TileSize.Wide) unit * 4 + 18.dp else if (size == TileSize.Small) unit else unit * 2 + 6.dp
        val h = if (size == TileSize.Small) unit else unit * 2 + 6.dp
        Box(Modifier.size(w, h).background(DefaultAccent)) { TileFace(item, size, w, h, live, seed = 0, notifications = 0) }
    }

    private fun live() = LiveTileData(
        battery = BatteryInfo(62, BatteryState.Charging, minutesToFull = 95),
        weather = sunny,
        storage = StorageInfo(freeBytes = 20_000_000_000L, totalBytes = 128_000_000_000L),
        nextAlarm = System.currentTimeMillis() + 3_600_000L,
        nextEvent = LiveEvent("Dentista", System.currentTimeMillis() + 86_400_000L * 2, false),
        birthdays = listOf(Birthday("Anna", LocalDate.now().plusDays(3).monthValue, LocalDate.now().plusDays(3).dayOfMonth, 1990)),
        missedCalls = 3,
    )

    @Composable
    private fun Faces(size: TileSize) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val kinds = listOf(LiveKind.Weather to "Meteo", LiveKind.Battery to "Batteria", LiveKind.Storage to "Memoria", LiveKind.Alarm to "Sveglie", LiveKind.Calendar to "Calendario", LiveKind.Birthdays to "Compleanni", LiveKind.Phone to "Telefono")
            kinds.chunked(if (size == TileSize.Wide) 1 else 2).forEach { chunk ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    chunk.forEach { (kind, label) -> tile(AppItem("t:$label", label, Launch.Action("x"), Glyph.Settings, live = kind), size, live())() }
                }
            }
        }
    }

    @Test fun liveFacesMedium() = fit("live_faces_medium") { Faces(TileSize.Medium) }
    @Test fun liveFacesWide() = fit("live_faces_wide") { Faces(TileSize.Wide) }

    @Composable
    private fun OwnTiles(size: TileSize) {
        val items = listOf(
            ShortcutDef("c", ShortcutKind.Counter, "Acqua", number = 7L), ShortcutDef("l", ShortcutKind.Checklist, "Spesa", text = "[x] pane\n[ ] latte\n[ ] uova"),
            ShortcutDef("w", ShortcutKind.WorldClock, "Tokyo", "Asia/Tokyo"), ShortcutDef("m", ShortcutKind.Moon, "Luna"),
            ShortcutDef("cd", ShortcutKind.Countdown, "Natale", epochDay = LocalDate.now().plusDays(62).toEpochDay()),
            ShortcutDef("n", ShortcutKind.Note, "Nota", text = "comprare il pane"),
        ).map { it.toItem(null) }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items.chunked(if (size == TileSize.Wide) 1 else 2).forEach { chunk ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { chunk.forEach { tile(it, size, live())() } }
            }
        }
    }

    @Test fun ownTilesMedium() = fit("own_tiles_medium") { OwnTiles(TileSize.Medium) }
    @Test fun ownTilesWide() = fit("own_tiles_wide") { OwnTiles(TileSize.Wide) }

    // ---- the third round: the faces of the new tiles, their pages, the choices that make them and what comes over the launcher

    private fun liveNew(): LiveTileData {
        val now = System.currentTimeMillis()
        return live().copy(
            news = NewsData("Prime pagine", listOf(NewsItem("Approvata la nuova legge di bilancio", "ANSA", "https://example.com/1", now - 3_600_000L)), now, "k"),
            finance = FinanceData(
                "EUR", listOf(Quote("fx:USD", 0.92, 0.4), Quote("fx:GBP", 1.17, -0.2), Quote("crypto:bitcoin", 61_000.0, -1.2), Quote("fx:JPY", 0.0061, 0.0)),
                "2026-10-23", now, "k",
            ),
            reminders = listOf(Reminder(1, "Chiamare Anna per la cena", now + 3_600_000L), Reminder(2, "Comprare il latte", now + 7_200_000L)),
        )
    }

    @Composable
    private fun NewTiles(size: TileSize) {
        val running = TimerState(10 * 60_000L, endAt = System.currentTimeMillis() + 4 * 60_000L)
        val items = listOf(
            ShortcutDef("p", ShortcutKind.Place, "Casa", "Via Giuseppe Garibaldi 12, Torino"),
            ShortcutDef("t1", ShortcutKind.Timer, "Timer", text = TimerState(5 * 60_000L).encode()),
            ShortcutDef("t2", ShortcutKind.Timer, "Pasta", text = running.encode()),
            ShortcutDef("s", ShortcutKind.Stopwatch, "Cronometro", text = StopwatchState(accumulatedMs = 83_000L, laps = listOf(40_000L, 83_000L)).encode()),
            ShortcutDef("n", ShortcutKind.News, "Notizie"), ShortcutDef("f", ShortcutKind.Finance, "Finanza"),
            ShortcutDef("r", ShortcutKind.Reminders, "Promemoria"), ShortcutDef("d", ShortcutKind.DialGrid, "Chiamata rapida"),
            ShortcutDef("h", ShortcutKind.PeopleHub, "Persone"),
        ).map { it.toItem(null) }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items.chunked(if (size == TileSize.Wide) 1 else 2).forEach { chunk ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { chunk.forEach { tile(it, size, liveNew())() } }
            }
        }
    }

    @Test fun newTilesSmall() = fit("new_tiles_small") { NewTiles(TileSize.Small) }
    @Test fun newTilesMedium() = fit("new_tiles_medium") { NewTiles(TileSize.Medium) }
    @Test fun newTilesWide() = fit("new_tiles_wide") { NewTiles(TileSize.Wide) }

    private val hour = 3_600_000L

    @Test fun timerPage() = fit("timer_page", overlaps = false) {
        TimerPage("Timer della pasta", TimerState(5 * 60_000L), {}, {}, {}, {}, {})
    }
    @Test fun timerPageRunning() = fit("timer_page_running", overlaps = false) {
        TimerPage("Timer della pasta", TimerState(10 * 60_000L, endAt = System.currentTimeMillis() + 4 * 60_000L), {}, {}, {}, {}, {})
    }
    @Test fun stopwatchPage() = fit("stopwatch_page", overlaps = false) {
        StopwatchPage("Cronometro", StopwatchState(accumulatedMs = 3_723_400L, laps = listOf(60_000L, 3_723_400L)), {}, {}, {}, {})
    }

    private fun newsSample() = NewsData(
        "Prime pagine - Google News",
        listOf(
            NewsItem("Approvata la nuova legge di bilancio", "ANSA", "https://example.com/1", System.currentTimeMillis() - hour),
            NewsItem("Maltempo al nord, allerta arancione", "Il Sole 24 Ore", "https://example.com/2", System.currentTimeMillis() - 5 * hour),
        ),
        System.currentTimeMillis() - 600_000L, "k",
    )

    @Test fun newsPage() = fit("news_page", overlaps = false) { NewsPage(newsSample(), googleSource = true, onOpen = {}, onRefresh = {}, onChooseSource = {}) }
    @Test fun newsPageEmpty() = fit("news_page_empty", overlaps = false) { NewsPage(null, googleSource = true, onOpen = {}, onRefresh = {}, onChooseSource = {}) }

    @Test fun financePage() = fit("finance_page", overlaps = false) {
        FinancePage(
            FinanceData(
                "EUR", listOf(Quote("fx:USD", 0.92, 0.4), Quote("fx:GBP", 1.17, -0.2), Quote("crypto:bitcoin", 61_000.0, -1.2), Quote("fx:JPY", 0.0061, 0.0)),
                "2026-10-23", System.currentTimeMillis(), "k",
            ),
            onOpen = {}, onRefresh = {}, onChoose = {},
        )
    }
    @Test fun financePageEmpty() = fit("finance_page_empty", overlaps = false) { FinancePage(null, onOpen = {}, onRefresh = {}, onChoose = {}) }

    @Test fun remindersPage() = fit("reminders_page", overlaps = false) {
        val now = System.currentTimeMillis()
        RemindersPage(
            listOf(
                Reminder(1, "Chiamare Anna", now + hour), Reminder(2, "Comprare il latte", now - 5 * hour), Reminder(3, "Pagare la bolletta", now - hour, done = true),
            ),
            draft = "", canExact = false, onAdd = { _, _ -> true }, onDone = {}, onRemove = {}, onClearDone = {}, onAllowExact = {},
        )
    }
    @Test fun remindersPageEmpty() = fit("reminders_page_empty", overlaps = false) {
        RemindersPage(emptyList(), draft = "", canExact = true, onAdd = { _, _ -> true }, onDone = {}, onRemove = {}, onClearDone = {}, onAllowExact = {})
    }

    private val people = listOf("Anna", "Bruno", "Carla", "Alessandro", "Elena").mapIndexed { i, name -> Favourite(name, "content://c/$i", "33$i", null) }
    private val calls = listOf(
        RecentCall("Anna", "331", android.provider.CallLog.Calls.INCOMING_TYPE, System.currentTimeMillis() - hour),
        RecentCall("Bruno", "332", android.provider.CallLog.Calls.MISSED_TYPE, System.currentTimeMillis() - 2 * hour),
    )

    @Test fun dialGridPage() = fit("dial_grid_page", overlaps = false) { DialGridPage(people, hasPermission = true, onRequestPermission = {}, onCall = {}) }
    @Test fun dialGridPageNeedsContacts() = fit("dial_grid_needs_contacts", overlaps = false) { DialGridPage(null, hasPermission = false, onRequestPermission = {}, onCall = {}) }
    @Test fun peopleHubPage() = fit("people_hub_page", overlaps = false) {
        PeopleHubPage(
            people, calls, listOf(NotificationEntry("k1", "chat", "Giulia", "Ci vediamo alle otto?", System.currentTimeMillis() - 60_000L, null, true)),
            labelOf = { "Messaggi" }, hasContacts = true, hasCallLog = true, onRequestContacts = {}, onRequestCallLog = {}, onCall = {}, onOpenNotification = {},
        )
    }
    @Test fun peopleHubPageNeedsPermissions() = fit("people_hub_needs_permissions", overlaps = false) {
        PeopleHubPage(
            null, null, emptyList(), labelOf = { "" }, hasContacts = false, hasCallLog = false,
            onRequestContacts = {}, onRequestCallLog = {}, onCall = {}, onOpenNotification = {},
        )
    }

    @Test fun addMenuWithTheNewTiles() = fit("add_menu_new_tiles") {
        AddMenu(
            onContact = {}, onSite = {}, onWidget = {}, onDismiss = {}, onSpeedDial = {}, onSetting = {}, onNote = {}, onCountdown = {},
            onCounter = {}, onChecklist = {}, onWorldClock = {}, onMoon = {}, onBirthdays = {}, onPlace = {}, onAlbum = {}, onGroup = {}, onTimer = {},
            onStopwatch = {}, onNews = {}, onFinance = {}, onReminders = {}, onDialGrid = {}, onPeopleHub = {},
        )
    }
    @Test fun placeDialog() = fit("place_dialog", fixedHeight = true) { PlaceDialog(onDone = { _, _ -> true }, onCancel = {}) }
    @Test fun albumPicker() = fit("album_picker") {
        AlbumPicker(listOf(AlbumInfo("1", "Fotocamera", 1240), AlbumInfo("2", "Vacanze in Sardegna", 1)), permissionMissing = false, onAllow = {}, onPick = {}, onDismiss = {})
    }
    @Test fun albumPickerNeedsPhotos() = fit("album_picker_needs_photos") { AlbumPicker(null, permissionMissing = true, onAllow = {}, onPick = {}, onDismiss = {}) }
    @Test fun groupPicker() = fit("group_picker") {
        GroupPicker(listOf(GroupInfo(1, "Famiglia", 5), GroupInfo(2, "Colleghi di lavoro", 1)), permissionMissing = false, onAllow = {}, onPick = {}, onDismiss = {})
    }
    @Test fun groupPickerNeedsContacts() = fit("group_picker_needs_contacts") { GroupPicker(null, permissionMissing = true, onAllow = {}, onPick = {}, onDismiss = {}) }

    @Test fun editBarWithNames() = fit("edit_bar_names", fixedHeight = true) {
        EditBar(
            size = TileSize.Medium, page = 0, pageCount = 3, onSize = {}, onMovePage = {}, onDone = {}, style = TileStyle.Standard,
            onStyle = {}, onAdd = {}, onFolder = {}, onLabels = {}, labels = true,
        )
    }

    @Test fun volumePanel() = fit("volume_panel", fixedHeight = true) {
        VolumeHudPanel(
            VolumeHud(
                token = 1, main = VolumeRow(VolumeStream.Music, null, 7, 0, 15), app = "YouTube Music",
                others = listOf(VolumeRow(VolumeStream.Ring, null, 0, 0, 7, ringerMode = android.media.AudioManager.RINGER_MODE_VIBRATE), VolumeRow(VolumeStream.Alarm, null, 5, 0, 7)),
                expanded = true,
            ),
            onSet = { _, _ -> }, onToggle = {}, onDismiss = {},
        )
    }
    @Test fun volumePanelRemote() = fit("volume_panel_remote", fixedHeight = true) {
        VolumeHudPanel(VolumeHud(token = 1, main = VolumeRow(null, "com.example.cast", 4, 0, 10), app = "Altoparlante della cucina"), onSet = { _, _ -> }, onToggle = {}, onDismiss = {})
    }
    @Test fun toastBanner() = fit("toast_banner", fixedHeight = true) {
        ToastBanner(
            NotificationEntry("k1", "chat", "Giulia Bianchi", "Ci vediamo alle otto davanti al cinema, ok?", System.currentTimeMillis(), null, true),
            app = null, hideText = false, onOpen = {}, onDismiss = {},
        )
    }

    @Test fun todayCard() = fit("today_card") {
        val now = System.currentTimeMillis()
        val model = remember { AppListModel(AppSections.group(AppSections.sort(apps)), today = true) }
        AppListScreen(
            model = model, listState = rememberLazyListState(), replayToken = 0, exiting = false, onLaunch = {}, onLongPress = { _, _, _ -> },
            onHeaderClick = {}, query = "", onQueryChange = {}, results = emptyList(),
            today = Today.info(
                TodayExtras(now, LiveEvent("Riunione con il commercialista", now + 2 * hour, false), now + 10 * hour), sunny,
                listOf(Reminder(1, "Chiamare Anna per la cena", now + hour), Reminder(2, "Comprare il latte e il pane", now + 3 * hour), Reminder(3, "Vecchio", now - 5 * hour)),
                now,
            ),
        )
    }

    @Composable
    private fun mediaInfo(): MediaInfo {
        val session = remember { MediaSession(context, "textfit") }
        return MediaInfo("player", "Una canzone", "Un artista", null, true, MediaController(context, session.sessionToken))
    }

    @Test fun lockScreenWithStatusAndMusic() = fit("lock_screen_status_music", fixedHeight = true) {
        MetroLockScreen(
            info = LockInfo(
                nowMillis = System.currentTimeMillis(), nextAlarmMillis = System.currentTimeMillis() + 86_400_000L, notifications = 3,
                battery = BatteryInfo(85, BatteryState.Charging), weather = sunny,
                nextEvent = LiveEvent("Riunione con il commercialista", System.currentTimeMillis() + 7_200_000L, false),
            ),
            photo = null, options = LockOptions(showStatus = true, showMusic = true),
            status = LockStatus(
                listOf(QuickBadge("com.example.alpha", 3), QuickBadge("com.example.beta", 12), QuickBadge("com.example.gamma", 120), QuickBadge("a", 1), QuickBadge("b", 2)),
                DetailLine("com.example.alpha", "Giulia Bianchi", "Ci vediamo alle otto davanti al cinema, ok? Porto io i biglietti."),
            ),
            media = mediaInfo(),
        )
    }
}

/** The tour of the first start: each page by itself. */
abstract class TextFitTourBase(private val scale: Float, private val style: ThemeStyle, private val widthDp: Int) : ScreenshotBase() {
    private fun checkPage(index: Int) {
        show(size = DpSize(widthDp.dp, 760.dp), textScale = scale, style = style) {
            val pager = rememberPagerState(initialPage = index) { Tutorial.steps.size }
            TutorialScreen(pager, emptySet(), onRequest = {}, onFinish = {})
        }
        advance(2_000)
        val rootWidth = compose.onRoot().fetchSemanticsNode().size.width
        val bad = mutableListOf<String>()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" | ") { it.text }.orEmpty()
            if (text.isBlank()) return@forEach
            val layouts = ArrayList<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            val why = buildList {
                if (layouts.firstOrNull()?.let(::isCut) == true) add("cut")
                if (layouts.firstOrNull()?.let(::brokenWord) == true) add("word broken")
                if (node.boundsInRoot.left < -1f || node.boundsInRoot.right > rootWidth + 1f) add("off the screen")
            }
            if (why.isNotEmpty()) bad += "  [${why.joinToString()}] \"$text\""
        }
        if (bad.isNotEmpty()) {
            val label = "${javaClass.simpleName}: tutorial page ${Tutorial.steps[index].id}"
            File("build/textfit").apply { mkdirs() }.resolve("${javaClass.simpleName}.txt").appendText(label + "\n" + bad.joinToString("\n") + "\n")
            fail("$label\n" + bad.joinToString("\n"))
        }
    }

    @Test fun page0() = checkPage(0)
    @Test fun page1() = checkPage(1)
    @Test fun page2() = checkPage(2)
    @Test fun page3() = checkPage(3)
    @Test fun page4() = checkPage(4)
    @Test fun page5() = checkPage(5)
    @Test fun page6() = checkPage(6)
    @Test fun page7() = checkPage(7)
    @Test fun lastPage() = checkPage(Tutorial.steps.size - 1)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "it-w360dp-h760dp-xxhdpi")
class TextFitItalianNormalTest : TextFitBase(1f, ThemeStyle.Classic, 360)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "it-w320dp-h760dp-xxhdpi")
class TextFitItalianStressTest : TextFitBase(1.3f, ThemeStyle.Classic, 320)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "it-w320dp-h760dp-xxhdpi")
class TextFitItalianGlassTest : TextFitBase(1.3f, ThemeStyle.Glass, 320)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "it-w320dp-h760dp-xxhdpi")
class TextFitItalianDroidTest : TextFitBase(1.3f, ThemeStyle.Droid, 320)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-w320dp-h760dp-xxhdpi")
class TextFitEnglishStressTest : TextFitBase(1.3f, ThemeStyle.Classic, 320)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "it-w320dp-h760dp-xxhdpi")
class TextFitItalianTourTest : TextFitTourBase(1.3f, ThemeStyle.Classic, 320)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-w320dp-h760dp-xxhdpi")
class TextFitEnglishTourTest : TextFitTourBase(1.3f, ThemeStyle.Classic, 320)

/** The biggest text the phone can ask for (130 percent in the settings, and the font size of Android on top of it). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "it-w320dp-h760dp-xxhdpi")
class TextFitItalianHugeTest : TextFitBase(1.7f, ThemeStyle.Classic, 320)

/** A phone on its side: a screen that is wide and short. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "it-w700dp-h340dp-land-xxhdpi")
class TextFitItalianLandscapeTest : TextFitBase(1f, ThemeStyle.Classic, 700, 340)
