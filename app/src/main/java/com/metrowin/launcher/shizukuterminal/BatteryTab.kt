package com.metrowin.launcher.shizukuterminal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.data.BatteryDeep
import com.metrowin.launcher.data.BatteryDoctor
import com.metrowin.launcher.data.BatterySection
import com.metrowin.launcher.ui.anim.LaunchedWhileStarted
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The battery doctor: every group of facts, the live ones read again every few seconds, the ones from the shell when the page opens and on refresh. */
@Composable
internal fun BatteryTab(state: ShizukuState, refresh: Int, opts: TerminalOptions, onReport: (() -> String) -> Unit) {
    val context = LocalContext.current
    var beat by remember { mutableIntStateOf(0) }
    LaunchedWhileStarted(Unit) {
        while (true) {
            delay(5_000)
            beat++
        }
    }
    val deep by produceState<BatteryDeep?>(null, state, refresh) { value = withContext(Dispatchers.IO) { BatteryDeep.read() } }
    val sections by produceState(emptyList<BatterySection>(), deep, beat, refresh) { value = withContext(Dispatchers.IO) { BatteryDoctor.collect(context, deep) } }
    SideEffect { onReport { sections.joinToString("\n\n") { s -> s.title + "\n" + s.rows.joinToString("\n") { (k, v) -> if (k.isEmpty()) v else "$k: $v" } } } }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item(key = "history") { HistoryBlock(refresh, beat, opts) }
        infoSections(sections, "bat", opts.sticky.on)
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** A fact and its value, with a thin line under it; a row without a label is a line of text (a wake lock). */
@Composable
internal fun KeyValueRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (label.isNotEmpty()) MetroText(label, size = 16.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, modifier = Modifier.weight(0.55f))
            MetroText(value, size = 16.sp, weight = FontWeight.Normal, textAlign = if (label.isEmpty()) TextAlign.Start else TextAlign.End, modifier = Modifier.weight(if (label.isEmpty()) 1f else 0.45f))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LocalMetroColors.current.inactive))
    }
}
