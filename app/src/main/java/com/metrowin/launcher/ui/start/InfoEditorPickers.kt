package com.metrowin.launcher.ui.start

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.PlaceMatch
import com.metrowin.launcher.data.SavedCities
import com.metrowin.launcher.data.SavedCity
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.kids.CheckSquare
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.delay

/**
 * A list of things to choose from, one under the other: one of them (a touch chooses it) or several (a button says it is done). [items] are a key and a name;
 * [emptyText] is said when there are none.
 */
@Composable
internal fun ChoiceDialog(
    title: String,
    items: List<Pair<String, String>>,
    selected: Set<String>,
    multi: Boolean,
    emptyText: String,
    onDone: (Set<String>) -> Unit,
    onCancel: () -> Unit,
) {
    var chosen by remember(selected) { mutableStateOf(selected) }
    DialogFrame(onCancel = onCancel, title = title) {
        Column(Modifier.fillMaxWidth().testTag("info:choice"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (items.isEmpty()) {
                MetroText(emptyText, size = 16.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, maxLines = Int.MAX_VALUE, lineHeight = 22.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            items.forEach { (key, name) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .testTag("info:choice:$key")
                        .metroPress(onClick = { if (multi) chosen = if (key in chosen) chosen - key else chosen + key else onDone(setOf(key)) })
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CheckSquare(key in chosen)
                    Spacer(Modifier.width(14.dp))
                    MetroText(name, size = 18.sp, weight = FontWeight.Normal, maxLines = 2, modifier = Modifier.weight(1f))
                }
            }
            if (multi) MetroButton(stringResource(R.string.info_done), Modifier.fillMaxWidth().padding(top = 8.dp).testTag("info:choice:done")) { onDone(chosen) }
        }
    }
}

/** A line of text to write. */
@Composable
internal fun TextDialog(title: String, initial: String, hint: String, onDone: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    DialogFrame(onCancel = onCancel, title = title) {
        Column(Modifier.fillMaxWidth().testTag("info:text"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MetroText(hint, size = 15.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, maxLines = Int.MAX_VALUE, lineHeight = 20.sp)
            MetroTextField(text, { text = it.take(60) }, title, Modifier.testTag("info:text:field"))
            MetroButton(stringResource(R.string.info_done), Modifier.fillMaxWidth().testTag("info:text:done")) { onDone(text) }
        }
    }
}

/**
 * The city the weather of the tile is of: the one of the weather tile, one that is followed already, or one found by name (it is then followed, as the
 * weather page does, so its weather is read; at most [SavedCities.MAX] are followed).
 */
@Composable
internal fun CityChoiceDialog(env: InfoEditorEnv, current: String, onPick: (key: String, name: String) -> Unit, onCancel: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<PlaceMatch>?>(null) }
    val followed = remember { env.followedCities() }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { found = null; return@LaunchedEffect }
        delay(400)
        found = env.searchPlaces(query.trim()).orEmpty()
    }
    DialogFrame(onCancel = onCancel, title = stringResource(R.string.infoe_city_title)) {
        Column(Modifier.fillMaxWidth().testTag("info:place"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PlaceRow(stringResource(R.string.infoe_city_tile), current.isEmpty(), "info:place:tile") { onPick("", "") }
            followed.forEach { city -> PlaceRow(city.name, city.key == current, "info:place:${city.key}") { onPick(city.key, city.name) } }
            MetroTextField(query, { query = it }, stringResource(R.string.infoe_city_search), Modifier.padding(top = 10.dp).testTag("info:place:search"))
            found?.let { places ->
                if (places.isEmpty()) MetroText(stringResource(R.string.infoe_city_none), size = 15.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
                places.forEach { place ->
                    val key = SavedCity(place.displayName, place.latitude, place.longitude).key
                    val room = followed.any { it.key == key } || followed.size < SavedCities.MAX
                    PlaceRow(place.displayName, false, "info:place:found:$key", enabled = room) {
                        if (followed.none { it.key == key }) env.followPlace(place)
                        onPick(key, place.displayName)
                    }
                }
                if (followed.size >= SavedCities.MAX) MetroText(stringResource(R.string.infoe_city_full), size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, maxLines = Int.MAX_VALUE)
            }
        }
    }
}

@Composable
private fun PlaceRow(name: String, selected: Boolean, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(tag)
            .then(if (enabled) Modifier.metroPress(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckSquare(selected)
        Spacer(Modifier.width(14.dp))
        MetroText(
            name, size = 18.sp, weight = FontWeight.Normal, maxLines = 2,
            color = if (enabled) LocalMetroColors.current.foreground else LocalMetroColors.current.subtle, modifier = Modifier.weight(1f),
        )
    }
}
