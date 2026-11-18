package com.metrowin.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.FirstLook
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.settings.SettingHint

/** The first run: three looks to start from, each with a line about it. Touching one sets it; "keep as it is" leaves everything. */
@Composable
fun FirstLookDialog(onPick: (FirstLook?) -> Unit) {
    DialogFrame({ onPick(null) }, title = stringResource(R.string.first_look_title)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingHint(stringResource(R.string.first_look_text))
            Look(R.string.first_look_wp, R.string.first_look_wp_hint, "firstlook:wp") { onPick(FirstLook.Wp) }
            Look(R.string.first_look_glass, R.string.first_look_glass_hint, "firstlook:glass") { onPick(FirstLook.Glass) }
            Look(R.string.first_look_droid, R.string.first_look_droid_hint, "firstlook:droid") { onPick(FirstLook.Droid) }
            SettingHint(stringResource(R.string.first_look_later))
            MetroButton(stringResource(R.string.first_look_keep), Modifier.fillMaxWidth().testTag("firstlook:keep"), onClick = { onPick(null) })
        }
    }
}

@Composable
private fun Look(label: Int, hint: Int, tag: String, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MetroButton(stringResource(label), Modifier.fillMaxWidth().testTag(tag), onClick = onClick)
        SettingHint(stringResource(hint))
    }
}

/** What is new in this version, in the language of the phone (the text is a string of the launcher, written again for each version). */
@Composable
fun WhatsNewDialog(versionName: String, onClose: () -> Unit) {
    DialogFrame(onClose, title = stringResource(R.string.whatsnew_title, versionName)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // The dialog scrolls by itself.
            SettingHint(stringResource(R.string.whatsnew_body), Modifier.testTag("whatsnew:text"))
            MetroButton(stringResource(R.string.whatsnew_close), Modifier.fillMaxWidth().testTag("whatsnew:close"), onClick = onClose)
        }
    }
}
