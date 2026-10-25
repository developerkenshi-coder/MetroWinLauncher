package com.metrowin.launcher.ui.start

import com.metrowin.launcher.ui.theme.sheet
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalMetroColors

/** What can be added to Start that is not an app: a contact, a web page, a widget. Opens above the edit bar. */
@Composable
fun AddMenu(
    onContact: () -> Unit,
    onSite: () -> Unit,
    onWidget: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** The entries that follow the three first ones; each is left out when null. */
    onSpeedDial: (() -> Unit)? = null,
    onSetting: (() -> Unit)? = null,
    onNote: (() -> Unit)? = null,
    onCountdown: (() -> Unit)? = null,
    onCounter: (() -> Unit)? = null,
    onChecklist: (() -> Unit)? = null,
    onWorldClock: (() -> Unit)? = null,
    onMoon: (() -> Unit)? = null,
    onBirthdays: (() -> Unit)? = null,
) {
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
                // Clear of the edit bar under it.
                .padding(top = 6.dp, bottom = 76.dp),
        ) {
            // Many entries: they scroll, and the sheet never takes more than two thirds of a small screen.
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Entry(R.string.add_contact, "add:contact", onContact)
                if (onSpeedDial != null) Entry(R.string.add_speed_dial, "add:speeddial", onSpeedDial)
                Entry(R.string.add_site, "add:site", onSite)
                if (onSetting != null) Entry(R.string.add_setting, "add:setting", onSetting)
                if (onNote != null) Entry(R.string.add_note, "add:note", onNote)
                if (onCountdown != null) Entry(R.string.add_countdown, "add:countdown", onCountdown)
                if (onCounter != null) Entry(R.string.add_counter, "add:counter", onCounter)
                if (onChecklist != null) Entry(R.string.add_checklist, "add:checklist", onChecklist)
                if (onWorldClock != null) Entry(R.string.add_worldclock, "add:worldclock", onWorldClock)
                if (onMoon != null) Entry(R.string.add_moon, "add:moon", onMoon)
                if (onBirthdays != null) Entry(R.string.add_birthdays, "add:birthdays", onBirthdays)
                Entry(R.string.add_widget, "add:widget", onWidget)
            }
        }
    }
}

@Composable
private fun Entry(text: Int, tag: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().testTag(tag).metroPress(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp)) {
        MetroText(stringResource(text), size = 22.sp)
    }
}
