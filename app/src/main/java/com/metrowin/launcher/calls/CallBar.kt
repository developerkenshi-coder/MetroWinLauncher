package com.metrowin.launcher.calls

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroText

/** Opens the screen of the calls, from anywhere: the way back to a call whose screen was closed. */
fun openCallScreen(context: Context) {
    runCatching { context.startActivity(Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
}

/**
 * A green strip over the launcher for as long as a call is on (while the launcher is the phone app): the call, its time, and a touch
 * goes back to its screen. It does not depend on the notification, which the user may have turned off or swiped away.
 */
@Composable
fun CallBar(modifier: Modifier = Modifier) {
    val lines by CallManager.lines.collectAsState()
    val main = lines.firstOrNull { !it.ended && !it.ringing } ?: return
    val context = LocalContext.current
    Row(
        modifier.fillMaxWidth().height(40.dp).background(Color(0xFF339933)).testTag("callbar").metroPress(onClick = { openCallScreen(context) }).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MetroText(
            main.title.ifEmpty { stringResource(R.string.incall_unknown) }, size = 16.sp, color = Color.White, weight = FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        MetroText(
            if (main.connectedAt > 0) elapsed(main.connectedAt) else stringResource(R.string.incall_calling), size = 16.sp, color = Color.White,
            weight = FontWeight.Normal, modifier = Modifier.testTag("callbar:time"),
        )
    }
}
