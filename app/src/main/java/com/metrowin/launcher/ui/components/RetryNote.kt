package com.metrowin.launcher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.pages.PageNote

/** What went wrong, in words that say what to do, with one touch to try again (for a connection that was missing). */
@Composable
fun RetryNote(text: String, onRetry: () -> Unit, modifier: Modifier = Modifier, tag: String = "retry") {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PageNote(text)
        MetroButton(stringResource(R.string.retry_button), Modifier.testTag(tag), onClick = onRetry)
    }
}
