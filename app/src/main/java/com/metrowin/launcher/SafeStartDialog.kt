package com.metrowin.launcher

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.CrashLog
import com.metrowin.launcher.data.SafeStart
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.settings.SettingHint

/** Tells the user why the launcher came up plain after two crashes at start, and offers the normal start. */
@Composable
internal fun SafeStartDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    // The second line of the saved crash is the error itself (the first is the version and the time).
    val error = remember { CrashLog.read(context.filesDir)?.lineSequence()?.drop(1)?.firstOrNull()?.take(160).orEmpty() }
    DialogFrame(onClose, title = stringResource(R.string.safe_title)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingHint(stringResource(R.string.safe_text))
            if (error.isNotBlank()) SettingHint(error)
            MetroButton(stringResource(R.string.safe_leave), Modifier.fillMaxWidth().testTag("safe:leave"), onClick = { restartNormally(context) })
            MetroButton(stringResource(R.string.safe_stay), Modifier.fillMaxWidth().testTag("safe:stay"), onClick = onClose)
        }
    }
}

/** The next start is the normal one: the count is cleared, the launcher is asked to open again, and this process ends. */
private fun restartNormally(context: Context) {
    SafeStart.leave(context)
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: Intent(context, MainActivity::class.java)
    context.startActivity(Intent.makeRestartActivityTask(launch.component))
    android.os.Process.killProcess(android.os.Process.myPid())
}
