package com.metrowin.launcher.shizukuterminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.data.BatterySection
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors

/** The titled lists of a page: a heading in the accent (it stays at the top while its rows scroll, when [sticky]), then a row for each fact. */
internal fun LazyListScope.infoSections(sections: List<BatterySection>, prefix: String, sticky: Boolean = false) {
    sections.filter { it.rows.isNotEmpty() }.forEach { section ->
        if (sticky) {
            stickyHeader(key = "$prefix:h:${section.title}") {
                Box(Modifier.fillMaxWidth().background(LocalMetroColors.current.background.copy(alpha = 0.94f))) {
                    MetroText(section.title, size = 24.sp, color = LocalAccent.current, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                }
            }
        } else item(key = "$prefix:h:${section.title}") {
            MetroText(section.title, size = 24.sp, color = LocalAccent.current, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
        }
        itemsIndexed(section.rows, key = { i, _ -> "$prefix:${section.title}:$i" }) { _, (label, value) -> KeyValueRow(label, value) }
    }
}
