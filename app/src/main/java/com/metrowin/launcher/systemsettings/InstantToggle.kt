package com.metrowin.launcher.systemsettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/** For how long the switch keeps the wish of the user while the phone has not answered; then it shows what the phone says. */
private const val WISH_MS = 2_500L

/**
 * The toggle of the settings with the switch that moves at the touch: the command goes through Shizuku and Android answers a moment later
 * ([Nudge]); the answer confirms the switch, or, when the command was refused or too slow, puts it back.
 */
@Composable
internal fun ToggleSetting(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    enabled: Boolean = true,
    tag: String? = null,
    id: String? = null,
) {
    var wished by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(checked) { wished = null }
    LaunchedEffect(wished) {
        if (wished != null) {
            delay(WISH_MS)
            wished = null
        }
    }
    com.metrowin.launcher.ui.settings.ToggleSetting(label, wished ?: checked, { wished = it; onChange(it) }, modifier, hint, enabled, tag, id)
}
