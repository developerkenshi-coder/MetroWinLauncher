package com.metrowin.launcher.ui.start

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.theme.LocalMetroColors

/**
 * Asks for the name and the address of a web page to pin. [onDone] says whether the address was accepted; when it
 * was not, the dialog stays and says so.
 */
@Composable
fun SiteDialog(onDone: (title: String, address: String) -> Boolean, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalMetroColors.current
    var title by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    fun submit() {
        invalid = !onDone(title, address)
    }
    Box(
        modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.85f))
            .pointerInput(Unit) { detectTapGestures { onCancel() } }
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .background(colors.surface)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MetroText(stringResource(R.string.add_site), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle)
            MetroTextField(
                title, { title = it.take(MAX_TITLE) }, stringResource(R.string.site_title_hint),
                Modifier.testTag("site:title"),
            )
            MetroTextField(
                address, { address = it; invalid = false }, stringResource(R.string.site_address_hint),
                Modifier.testTag("site:address"), onSearch = ::submit,
            )
            if (invalid) {
                MetroText(
                    stringResource(R.string.site_invalid), size = 14.sp, weight = FontWeight.Normal,
                    color = colors.subtle, modifier = Modifier.testTag("site:invalid"),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetroButton(stringResource(android.R.string.ok), Modifier.weight(1f).testTag("site:ok")) { submit() }
                MetroButton(stringResource(android.R.string.cancel), Modifier.weight(1f), onClick = onCancel)
            }
        }
    }
}

private const val MAX_TITLE = 24
