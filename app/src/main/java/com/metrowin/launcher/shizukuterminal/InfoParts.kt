package com.metrowin.launcher.shizukuterminal

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.data.BatterySection
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent

/** The titled lists of a page: a heading in the accent, then a row for each fact. */
internal fun LazyListScope.infoSections(sections: List<BatterySection>, prefix: String) {
    sections.filter { it.rows.isNotEmpty() }.forEach { section ->
        item(key = "$prefix:h:${section.title}") {
            MetroText(section.title, size = 24.sp, color = LocalAccent.current, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
        }
        itemsIndexed(section.rows, key = { i, _ -> "$prefix:${section.title}:$i" }) { _, (label, value) -> KeyValueRow(label, value) }
    }
}
