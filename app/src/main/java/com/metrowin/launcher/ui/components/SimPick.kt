package com.metrowin.launcher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.SimInfo
import com.metrowin.launcher.data.SimUse
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors

/**
 * Which SIM places the call or sends the text: one row per SIM and, when there is a number to keep it for, a switch to use that SIM for it
 * from now on. [onPick] is told the SIM and whether to keep it.
 */
@Composable
fun SimPickDialog(use: SimUse, sims: List<SimInfo>, canRemember: Boolean, onPick: (SimInfo, Boolean) -> Unit, onDismiss: () -> Unit) {
    var keep by remember { mutableStateOf(false) }
    DialogFrame(onCancel = onDismiss, title = stringResource(if (use == SimUse.Call) R.string.sim_pick_call else R.string.sim_pick_sms)) {
        Column(Modifier.testTag("sim:pick")) {
            sims.forEach { sim ->
                MetroText(
                    sim.label, size = 22.sp,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sim:pick:${sim.slot}").metroPress(onClick = { onPick(sim, keep) }).padding(vertical = 10.dp),
                )
            }
            if (canRemember) MetroToggle(stringResource(R.string.sim_remember), keep, { keep = it },Modifier.testTag("sim:remember"))
        }
    }
}

/** A row to choose one of: ask each time, or one of the SIMs ([selected] is the slot, null for asking). */
@Composable
fun SimSelector(title: String, sims: List<SimInfo>, selected: Int?, onSelect: (Int?) -> Unit, tag: String, modifier: Modifier = Modifier) {
    Column(modifier.testTag(tag)) {
        MetroText(title, size = MetroType.Body, weight = FontWeight.SemiBold, modifier = Modifier.padding(top = Spacing.Medium))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            val options = listOf<Pair<Int?, String>>(null to stringResource(R.string.sim_ask)) + sims.map { it.slot to it.label }
            options.forEach { (slot, label) ->
                val on = slot == selected
                MetroText(
                    label, size = MetroType.Lead, weight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) LocalAccent.current else LocalMetroColors.current.foreground,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("$tag:${slot ?: "ask"}").metroPress(onClick = { onSelect(slot) }).padding(vertical = Spacing.Large),
                )
            }
        }
    }
}
