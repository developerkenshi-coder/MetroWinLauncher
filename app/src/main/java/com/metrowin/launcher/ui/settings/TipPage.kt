package com.metrowin.launcher.ui.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroType
import com.metrowin.launcher.ui.components.Spacing
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.onAccent

const val KOFI_URL = "https://ko-fi.com/kenshi1299"

/** A thank-you from the author: a word, the link to leave a tip for his cat, and who made the launcher. */
@Composable
fun TipPage() {
    val context = LocalContext.current
    SettingsPage {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            SettingHeading(stringResource(R.string.tip_title))
            SettingHint(stringResource(R.string.tip_p1), size = MetroType.Body, lineHeight = 22.sp, minScale = 1f)
        }
        ActionSetting(
            stringResource(R.string.tip_button), id = "tip.kofi", tag = "tip:link",
            onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, KOFI_URL.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
        )
        SettingHint(stringResource(R.string.tip_p2), size = MetroType.Body, lineHeight = 22.sp, minScale = 1f)
        SettingHint(stringResource(R.string.tip_p3), size = MetroType.Body, lineHeight = 22.sp, minScale = 1f)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingHeading(stringResource(R.string.tip_credits))
            Credit("K", "Kenshi", stringResource(R.string.tip_kenshi), "tip:kenshi")
            Credit("M", "Maty", stringResource(R.string.tip_maty), "tip:maty")
        }
    }
}

/** A person (or a cat) in the thanks: an accent tile with the initial, the name, and what they did. */
@Composable
private fun Credit(initial: String, name: String, role: String, tag: String) {
    val accent = LocalAccent.current
    Row(Modifier.testTag(tag), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(64.dp).background(accent), contentAlignment = Alignment.Center) {
            MetroText(initial, size = MetroType.Title, color = onAccent())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MetroText(name, size = MetroType.Heading, color = accent)
            SettingHint(role)
        }
    }
}
