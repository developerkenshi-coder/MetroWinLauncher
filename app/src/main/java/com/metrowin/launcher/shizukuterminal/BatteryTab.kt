package com.metrowin.launcher.shizukuterminal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
fun BatteryTab(state: ShizukuState, refresh: Int, onReport: (() -> String) -> Unit) {
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
        item(key = "history") { HistoryBlock(refresh, beat) }
        sections.forEach { section ->
            item(key = "h:${section.title}") {
                MetroText(section.title, size = 24.sp, color = LocalAccent.current, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
            }
            itemsIndexed(section.rows, key = { i, _ -> "r:${section.title}:$i" }) { _, (label, value) ->
                KeyValueRow(label, value)
            }
        }
        item { Row(Modifier.padding(bottom = 24.dp)) {} }
    }
}

/** A fact and its value; a row without a label is a line of text (a wake lock). */
@Composable
internal fun KeyValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (label.isNotEmpty()) MetroText(label, size = 16.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, modifier = Modifier.weight(0.55f))
        MetroText(value, size = 16.sp, weight = FontWeight.Normal, textAlign = if (label.isEmpty()) TextAlign.Start else TextAlign.End, modifier = Modifier.weight(if (label.isEmpty()) 1f else 0.45f))
    }
}
