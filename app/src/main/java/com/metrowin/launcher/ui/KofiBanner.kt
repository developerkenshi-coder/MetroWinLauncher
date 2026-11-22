package com.metrowin.launcher.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.components.ButtonRole
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.settings.SettingHint

/**
 * The banner that invites people to follow the author on Ko-fi, where the progress of the new features is told. It is shown **once**:
 *  - to everyone who **updates** the app to 26.10.5S or later, the first time the launcher is opened, whatever else is true of their phone;
 *  - to a **fresh install**, after 48 hours (counted from the install).
 * It is marked seen only when it really is on screen (see `LauncherViewModel.kofiShown`), so nothing that closes the app first can lose it.
 */
object KofiBanner {
    /** The page of the author. The one place to change it. */
    const val URL = "https://ko-fi.com/kenshi1299"

    /** The first version code that shows it: 26.10.5S. */
    const val FROM_VERSION_CODE = 78L

    /** A fresh install waits this long (48 hours) before it is shown. */
    const val WAIT_MS = 48L * 3_600_000L

    /** An app whose last update is later than its first install by more than this was updated, not installed anew (both times are the same on a fresh install). */
    const val UPDATED_GAP_MS = 1_000L

    /** Whether to show it now. [firstInstall] and [lastUpdate] are the times Android keeps for the package. */
    fun due(seen: Boolean, versionCode: Long, firstInstall: Long, lastUpdate: Long, now: Long): Boolean {
        if (seen || versionCode < FROM_VERSION_CODE) return false
        val updated = lastUpdate - firstInstall > UPDATED_GAP_MS
        return updated || now - firstInstall >= WAIT_MS
    }

    /** Opens the page in the browser of the user; nothing happens when there is no browser. */
    fun open(context: Context) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** The banner in the style of the message box of Windows Phone: the sign of Ko-fi, what it is for, a button that opens the page and one to leave. */
@Composable
fun KofiBannerDialog(onOpen: () -> Unit, onClose: () -> Unit) {
    DialogFrame(onClose, title = stringResource(R.string.kofi_title)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.ic_kofi), null, Modifier.size(72.dp).align(Alignment.CenterHorizontally).testTag("kofi:image"))
            SettingHint(stringResource(R.string.kofi_text), Modifier.testTag("kofi:text"))
            MetroButton(stringResource(R.string.kofi_open), Modifier.fillMaxWidth().testTag("kofi:open"), role = ButtonRole.Primary, onClick = onOpen)
            MetroButton(stringResource(R.string.kofi_close), Modifier.fillMaxWidth().testTag("kofi:close"), onClick = onClose)
        }
    }
}
