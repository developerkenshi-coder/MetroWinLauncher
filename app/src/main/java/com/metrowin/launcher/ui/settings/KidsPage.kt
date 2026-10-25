package com.metrowin.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppSquare
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.SettingLabel
import com.metrowin.launcher.ui.kids.CheckSquare
import com.metrowin.launcher.ui.theme.LocalMetroColors

/** Which apps the Kid's Corner shows, and the button that starts it. */
@Composable
fun KidsPage(env: SettingsEnv) {
    val colors = LocalMetroColors.current
    val settings = env.settings
    val choosable = remember(env.apps) { env.apps.filter { it.launch !is Launch.Internal && !it.pinOnly } }
    val ready = settings.kidsApps.any { id -> choosable.any { it.id == id } }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        item {
            MetroText(
                stringResource(R.string.kids_intro),
                size = 16.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = Int.MAX_VALUE, lineHeight = 22.sp,
            )
            Spacer(Modifier.height(18.dp))
            MetroButton(
                stringResource(R.string.kids_start),
                Modifier.fillMaxWidth().testTag("kids:start").settingAnchor("kids.start").alpha(if (ready) 1f else 0.4f),
            ) { if (ready) env.onStartKidsCorner() }
            MetroText(
                stringResource(R.string.kids_pin_hint),
                size = 13.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = Int.MAX_VALUE,
                modifier = Modifier.padding(top = 10.dp),
            )
            Spacer(Modifier.height(22.dp))
            SettingLabel(stringResource(R.string.kids_apps), Modifier.settingAnchor("kids.apps"))
        }
        items(choosable, key = { it.id }) { app ->
            val checked = app.id in settings.kidsApps
            Row(
                Modifier
                    .fillMaxWidth()
                    .testTag("kids:app:${app.id}")
                    .metroPress(onClick = {
                        env.onChange { s -> s.copy(kidsApps = if (checked) s.kidsApps - app.id else s.kidsApps + app.id) }
                    })
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppSquare(app, 44.dp)
                Spacer(Modifier.width(14.dp))
                MetroText(app.label, size = 20.sp, modifier = Modifier.weight(1f))
                CheckSquare(checked)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
