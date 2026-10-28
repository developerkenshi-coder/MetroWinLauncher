package com.metrowin.launcher.ui.start

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.AddCategory
import com.metrowin.launcher.data.AddMenuIndex
import com.metrowin.launcher.ui.anim.featherIn
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.sheet

/**
 * What can be added to Start that is not an app. Opens above the edit bar: a search box on top, then the entries filed by what they are for
 * (people, places, time, notes, information, media, system); typing in the box leaves only the entries that match. The first three are the
 * ones that were always there; each of the others is left out when its function is null.
 */
@Composable
fun AddMenu(
    onContact: () -> Unit,
    onSite: () -> Unit,
    onWidget: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onSpeedDial: (() -> Unit)? = null,
    onSetting: (() -> Unit)? = null,
    onNote: (() -> Unit)? = null,
    onCountdown: (() -> Unit)? = null,
    onCounter: (() -> Unit)? = null,
    onChecklist: (() -> Unit)? = null,
    onWorldClock: (() -> Unit)? = null,
    onMoon: (() -> Unit)? = null,
    onBirthdays: (() -> Unit)? = null,
    onPlace: (() -> Unit)? = null,
    onAlbum: (() -> Unit)? = null,
    onGroup: (() -> Unit)? = null,
    onTimer: (() -> Unit)? = null,
    onStopwatch: (() -> Unit)? = null,
    onNews: (() -> Unit)? = null,
    onFinance: (() -> Unit)? = null,
    onReminders: (() -> Unit)? = null,
    onDialGrid: (() -> Unit)? = null,
    onPeopleHub: (() -> Unit)? = null,
    onSteps: (() -> Unit)? = null,
    onRecorder: (() -> Unit)? = null,
    onCamera: (() -> Unit)? = null,
    onWallet: (() -> Unit)? = null,
    onWhereAmI: (() -> Unit)? = null,
    onParking: (() -> Unit)? = null,
    onAnalogClock: (() -> Unit)? = null,
    onToggle: (() -> Unit)? = null,
    onSun: (() -> Unit)? = null,
    onWeatherPlus: (() -> Unit)? = null,
) {
    val colors = LocalMetroColors.current
    val currentDismiss by rememberUpdatedState(onDismiss)
    val given = mapOf(
        "contact" to onContact, "speeddial" to onSpeedDial, "site" to onSite, "setting" to onSetting, "note" to onNote,
        "countdown" to onCountdown, "counter" to onCounter, "checklist" to onChecklist, "worldclock" to onWorldClock, "moon" to onMoon,
        "birthdays" to onBirthdays, "place" to onPlace, "album" to onAlbum, "group" to onGroup, "timer" to onTimer,
        "stopwatch" to onStopwatch, "reminders" to onReminders, "news" to onNews, "finance" to onFinance, "dialgrid" to onDialGrid,
        "peoplehub" to onPeopleHub, "steps" to onSteps, "recorder" to onRecorder, "camera" to onCamera, "wallet" to onWallet,
        "whereami" to onWhereAmI, "parking" to onParking, "analogclock" to onAnalogClock, "widget" to onWidget, "toggle" to onToggle, "sun" to onSun,
        "weatherplus" to onWeatherPlus,
    )
    val available = AddMenuIndex.kinds.filter { given[it.id] != null }
    var query by remember { mutableStateOf("") }
    Box(
        modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.78f))
            .pointerInput(Unit) { detectTapGestures { currentDismiss() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .sheet()
                .pointerInput(Unit) { detectTapGestures { } }
                // The sheet rises over the keyboard when the search box is used.
                .imePadding()
                .navigationBarsPadding()
                // Clear of the edit bar under it.
                .padding(top = 10.dp, bottom = 76.dp),
        ) {
            Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 6.dp).testTag("add:search")) {
                MetroTextField(query, { query = it }, stringResource(R.string.add_search))
            }
            // Many entries: they scroll, and the sheet never takes more than two thirds of a small screen.
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                val names = available.associate { it.id to stringResource(it.label) }
                val shown = available.filter { AddMenuIndex.matches(it, names.getValue(it.id), query) }
                var place = 0
                if (shown.isEmpty()) {
                    MetroText(stringResource(R.string.add_nothing), size = 18.sp, color = colors.subtle, modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp))
                }
                // A search shows what it found in one list; without it the entries are filed by what they are for.
                val groups = if (query.isBlank()) AddCategory.entries.map { it to shown.filter { kind -> kind.category == it } } else listOf(null to shown)
                groups.forEach { (category, kinds) ->
                    if (kinds.isEmpty()) return@forEach
                    if (category != null) Heading(stringResource(category.title), Modifier.featherIn(place++))
                    kinds.forEach { kind ->
                        val click = given.getValue(kind.id) ?: return@forEach
                        Entry(names.getValue(kind.id), "add:${kind.id}", click, Modifier.featherIn(place++))
                    }
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String, modifier: Modifier = Modifier) {
    MetroText(
        text, size = 14.sp, weight = FontWeight.SemiBold, color = LocalMetroColors.current.subtle,
        modifier = modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun Entry(text: String, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().testTag(tag).metroPress(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp)) {
        MetroText(text, size = 22.sp)
    }
}
