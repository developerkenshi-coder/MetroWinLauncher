package com.metrowin.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.model.AppItem

import com.metrowin.launcher.data.AppSearch
import com.metrowin.launcher.data.Assistant
import com.metrowin.launcher.data.Currencies
import com.metrowin.launcher.data.GlobalResults
import com.metrowin.launcher.data.GlobalSearch
import com.metrowin.launcher.data.LauncherSettingMatch
import com.metrowin.launcher.data.QuickAnswer
import com.metrowin.launcher.data.QuickAnswers
import com.metrowin.launcher.data.SearchOperators
import com.metrowin.launcher.data.SystemCommands
import com.metrowin.launcher.data.TileMatch
import com.metrowin.launcher.data.TileSearch
import com.metrowin.launcher.ui.settings.SettingsIndex

/** What the search box of the app list shows for the text typed in it: the apps, the answers it gives by itself, and the rest. */
@Stable
class SearchState(
    val results: List<AppItem>,
    val answers: List<QuickAnswer>,
    val global: GlobalResults,
    val recent: List<String>,
    /** A throw of dice or a coin is made again. */
    val roll: () -> Unit,
)

@Composable
fun rememberSearch(vm: LauncherViewModel, query: String, listItems: List<AppItem>, items: List<AppItem>, usage: Map<String, Int>, settings: LauncherSettings): SearchState {
    val appContext = LocalContext.current
    // The apps are indexed once (and again when they or the counts of the opens change), not read again for every key.
    val appIndex = remember(listItems, usage) {
        AppSearch.index(
            listItems, usage,
            categoryLabel = { appContext.getString(it.label) },
            profileWords = AppSearch.ProfileWords(appContext.getString(R.string.profile_work), appContext.getString(R.string.profile_private)),
        )
    }
    // A word and a colon in front (`app:`, `contact:`, `file:`...) narrows the search to one kind of result.
    val scoped = remember(query, settings.searchOperators) { if (settings.searchOperators) SearchOperators.parse(query) else SearchOperators.Parsed(null, query) }
    val searchResults = remember(scoped, appIndex, listItems) {
        when {
            query.isBlank() -> listItems
            !scoped.allows(SearchOperators.Scope.Apps) -> emptyList()
            scoped.text.isBlank() -> listItems
            else -> appIndex.search(scoped.text)
        }
    }
    val launcherSettingsIndex = remember { SettingsIndex.searchIndex(appContext) }
    val tileIndex = remember(items) { TileSearch.index(items) }
    val recentSearches by vm.recentSearches.collectAsStateWithLifecycle()
    // What the search box answers by itself: a sum, a conversion, a number, a link, a timer or an alarm.
    // A throw of dice or a coin is made again when it is touched.
    var rollSeed by remember { mutableIntStateOf(0) }
    val localAnswers = remember(scoped, settings.searchAnswers, rollSeed) {
        if (settings.searchAnswers && scoped.scope == null) QuickAnswers.of(query) else emptyList()
    }
    // A currency conversion needs the rates: they come once the typing pauses, and only when the user allowed it.
    var currencyAnswer by remember { mutableStateOf<QuickAnswer.Currency?>(null) }
    LaunchedEffect(scoped, settings.searchAnswers, settings.searchCurrency) {
        currencyAnswer = null
        val asked = if (settings.searchAnswers && settings.searchCurrency && scoped.scope == null) Currencies.parse(query) else null
        if (asked != null) {
            delay(CURRENCY_DELAY_MS)
            currencyAnswer = vm.currencyAnswer(asked)
        }
    }
    // A sentence that asks for something (a reminder, an event, a note, a place near, the camera) comes before the plain answers.
    val assistantAnswers = remember(scoped, settings.searchAssistant) {
        if (settings.searchAssistant && scoped.scope == null) Assistant.of(query) else emptyList()
    }
    val commandAnswers = remember(scoped, settings.searchCommands) {
        if (settings.searchCommands && scoped.scope == null) listOfNotNull(SystemCommands.of(query)) else emptyList()
    }
    val answers = commandAnswers + assistantAnswers + (currencyAnswer?.let { listOf(it) + localAnswers } ?: localAnswers)

    // What the search finds besides apps. The settings and the web offer are known at once; the contacts follow
    // when the typing pauses, so each key does not query the contacts.
    var globalResults by remember { mutableStateOf(GlobalResults.Empty) }
    LaunchedEffect(
        scoped, settings.searchContacts, settings.searchSettings, settings.searchWeb, settings.searchCalendar,
        settings.searchLauncherSettings, settings.searchTiles, tileIndex, settings.searchFileTypes, settings.searchNotifications,
    ) {
        // What is looked for, without the operator in front of it.
        val query = scoped.text
        if (query.isBlank()) {
            globalResults = GlobalResults.Empty
            return@LaunchedEffect
        }
        globalResults = GlobalResults(
            settings = if (settings.searchSettings && scoped.allows(SearchOperators.Scope.Settings)) {
                GlobalSearch.matchSettings(query) { def -> appContext.getString(def.label) }.take(MAX_SETTING_RESULTS)
            } else {
                emptyList()
            },
            launcherSettings = if (settings.searchLauncherSettings && scoped.allows(SearchOperators.Scope.Settings)) {
                launcherSettingsIndex.search(query).take(MAX_SETTING_RESULTS).map { hit ->
                    val place = appContext.getString(hit.section.title)
                    LauncherSettingMatch(
                        id = hit.setting?.id,
                        section = hit.section.name,
                        title = appContext.getString(hit.setting?.title ?: hit.section.title),
                        where = if (hit.setting == null) appContext.getString(hit.section.description) else appContext.getString(R.string.search_launcher_setting_where, place),
                    )
                }
            } else {
                emptyList()
            },
            tiles = if (settings.searchTiles && scoped.allows(SearchOperators.Scope.Tiles)) {
                tileIndex.search(query).take(MAX_TILE_RESULTS).map { TileMatch(it, TileSearch.snippet(it, query)) }
            } else {
                emptyList()
            },
            webQuery = query.trim().takeIf { settings.searchWeb && scoped.allows(SearchOperators.Scope.Web) },
            notifications = if (settings.searchNotifications && scoped.allows(SearchOperators.Scope.Notifications)) vm.searchNotifications(query) else emptyList(),
        )
        val contacts = settings.searchContacts && scoped.allows(SearchOperators.Scope.Contacts)
        val events = settings.searchCalendar && scoped.allows(SearchOperators.Scope.Events)
        val files = settings.searchFileTypes.isNotEmpty() && scoped.allows(SearchOperators.Scope.Files)
        if (contacts) {
            delay(CONTACT_SEARCH_DELAY_MS)
            globalResults = globalResults.copy(contacts = vm.searchContacts(query))
        }
        if (events) {
            if (!contacts) delay(CONTACT_SEARCH_DELAY_MS)
            globalResults = globalResults.copy(events = vm.searchEvents(query))
        }
        if (files) {
            if (!contacts && !events) delay(CONTACT_SEARCH_DELAY_MS)
            globalResults = globalResults.copy(files = vm.searchFiles(query))
        }
    }
    return SearchState(searchResults, answers, globalResults, recentSearches, roll = { rollSeed++ })
}

/** The contacts are looked for once the typing has paused this long. */
private const val CONTACT_SEARCH_DELAY_MS = 220L

/** How many settings of Android, settings of the launcher and tiles of Start the search lists, at most: the best ones. */
private const val MAX_SETTING_RESULTS = 6
private const val MAX_TILE_RESULTS = 5
private const val CURRENCY_DELAY_MS = 350L
