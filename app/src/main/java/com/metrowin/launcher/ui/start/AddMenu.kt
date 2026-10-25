package com.metrowin.launcher.ui.start

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalMetroColors

/** What can be added to Start that is not an app: a contact, a web page, a widget. Opens above the edit bar. */
@Composable
fun AddMenu(
    onContact: () -> Unit,
    onSite: () -> Unit,
    onWidget: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMetroColors.current
    val currentDismiss by rememberUpdatedState(onDismiss)
    Box(
        modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.78f))
            .pointerInput(Unit) { detectTapGestures { currentDismiss() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .pointerInput(Unit) { detectTapGestures { } }
                .navigationBarsPadding()
                // Clear of the edit bar under it.
                .padding(top = 6.dp, bottom = 76.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Entry(R.string.add_contact, "add:contact", onContact)
            Entry(R.string.add_site, "add:site", onSite)
            Entry(R.string.add_widget, "add:widget", onWidget)
        }
    }
}

@Composable
private fun Entry(text: Int, tag: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().testTag(tag).metroPress(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp)) {
        MetroText(stringResource(text), size = 22.sp)
    }
}
