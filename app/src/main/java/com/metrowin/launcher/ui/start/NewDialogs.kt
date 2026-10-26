package com.metrowin.launcher.ui.start

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.AlbumInfo
import com.metrowin.launcher.data.GroupInfo
import com.metrowin.launcher.data.Places
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.sheet

/** The name and the address of a place: a touch on its tile starts the navigation there (Google Maps where it is installed). */
@Composable
fun PlaceDialog(onDone: (name: String, address: String) -> Boolean, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalMetroColors.current
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val home = stringResource(R.string.place_home)
    val work = stringResource(R.string.place_work)
    DialogFrame(onCancel, modifier) {
        MetroText(stringResource(R.string.place_title), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle)
        MetroTextField(
            name, { name = it.take(Places.MAX_NAME) }, stringResource(R.string.place_name_hint), Modifier.testTag("place:name"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PageChip(home, name == home, "place:home") { name = home }
            PageChip(work, name == work, "place:work") { name = work }
        }
        MetroTextField(
            address, { address = it.take(Places.MAX_ADDRESS).also { wrong = false } }, stringResource(R.string.place_address_hint), Modifier.testTag("place:address"),
        )
        MetroText(
            stringResource(R.string.place_note), size = 14.sp, weight = FontWeight.Normal, color = if (wrong) com.metrowin.launcher.ui.theme.LocalAccent.current else colors.subtle,
            maxLines = Int.MAX_VALUE, lineHeight = 19.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetroButton(stringResource(android.R.string.ok), Modifier.weight(1f).testTag("place:ok")) { wrong = !onDone(name, address) }
            MetroButton(stringResource(android.R.string.cancel), Modifier.weight(1f).testTag("place:cancel"), onClick = onCancel)
        }
    }
}

/** A sheet from the bottom with a title and [content], over a dimmed page; a touch outside it calls [onDismiss]. */
@Composable
private fun PickerSheet(title: String, onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = LocalMetroColors.current
    val currentDismiss by rememberUpdatedState(onDismiss)
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
                .navigationBarsPadding()
                .padding(top = 12.dp),
        ) {
            MetroText(title, size = 15.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(bottom = 76.dp)) { content() }
        }
    }
}

/** The albums of photos: a touch on one pins it as a Cycle tile. With no permission to read the photos it says so and offers to ask. */
@Composable
fun AlbumPicker(
    albums: List<AlbumInfo>?,
    permissionMissing: Boolean,
    onAllow: () -> Unit,
    onPick: (AlbumInfo) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PickerSheet(stringResource(R.string.album_picker_title), onDismiss, modifier) {
        when {
            permissionMissing -> NeedsPermission(R.string.album_needs_photos, "album:allow", onAllow)
            albums == null -> PickerNote(R.string.album_loading)
            albums.isEmpty() -> PickerNote(R.string.album_empty)
            else -> albums.forEach { album ->
                PickerRow(album.name, pluralStringResource(R.plurals.album_photos, album.count, album.count), "album:${album.id}") { onPick(album) }
            }
        }
    }
}

/** The groups of contacts (the labels the user made): a touch on one pins it. */
@Composable
fun GroupPicker(
    groups: List<GroupInfo>?,
    permissionMissing: Boolean,
    onAllow: () -> Unit,
    onPick: (GroupInfo) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PickerSheet(stringResource(R.string.group_picker_title), onDismiss, modifier) {
        when {
            permissionMissing -> NeedsPermission(R.string.group_needs_contacts, "group:allow", onAllow)
            groups == null -> PickerNote(R.string.group_loading)
            groups.isEmpty() -> PickerNote(R.string.group_empty)
            else -> groups.forEach { group ->
                PickerRow(group.name, pluralStringResource(R.plurals.group_people, group.count, group.count), "group:${group.id}") { onPick(group) }
            }
        }
    }
}

@Composable
private fun PickerRow(title: String, detail: String, tag: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag(tag).metroPress(onClick = onClick).padding(horizontal = 24.dp, vertical = 10.dp)) {
        MetroText(title, size = 21.sp, maxLines = 1)
        MetroText(detail, size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
    }
}

@Composable
private fun PickerNote(text: Int) {
    MetroText(
        stringResource(text), size = 16.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle,
        maxLines = Int.MAX_VALUE, lineHeight = 22.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

@Composable
private fun NeedsPermission(text: Int, tag: String, onAllow: () -> Unit) {
    PickerNote(text)
    MetroButton(stringResource(R.string.page_allow), Modifier.padding(horizontal = 24.dp, vertical = 4.dp).fillMaxWidth().testTag(tag), onClick = onAllow)
}
