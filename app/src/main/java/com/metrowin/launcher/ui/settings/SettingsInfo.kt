package com.metrowin.launcher.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.frame

/**
 * What a setting is for, in a few lines: what it does, when it matters, what it costs (battery, privacy, a permission). The text of a setting
 * is found by its id in the [SettingsIndex]; a test checks that every setting has one, in every language.
 */
object SettingsInfo {
    /** The text of the setting [id], or null when it has none (a test says every setting of the index has one). */
    @StringRes
    fun of(id: String): Int? = byId[id] ?: MiniInfoTable.of(id) ?: SettingsInfoTable.byPrefix.entries.firstOrNull { id.startsWith(it.key) }?.value ?: com.metrowin.launcher.ui.start.InfoIndex.infoOf(id)

    internal val byId: Map<String, Int> = SettingsInfoTable.byId
}

/** What an "i" asks to show: the name and the text of what it explains. */
class InfoRequest(val title: String, @param:StringRes val text: Int, val tag: String)

/** Where an "i" sends its request: the settings screen shows the box over everything. Null outside the settings: the "i" then does nothing. */
internal val LocalInfoHost = compositionLocalOf<((InfoRequest) -> Unit)?> { null }

/**
 * The small "i" of Windows Phone: touched, it asks the settings to open a box with [text] ([title] over it). It sits after the name of a setting or of a
 * category, and is as big to touch as a button even though it is drawn small.
 */
@Composable
fun InfoMark(@StringRes text: Int, title: String, modifier: Modifier = Modifier, tag: String = "info") {
    val host = LocalInfoHost.current
    val colors = LocalMetroColors.current
    val label = stringResource(R.string.settings_info_for, title)
    Box(
        modifier
            .size(44.dp)
            .testTag(tag)
            .semantics { contentDescription = label; role = Role.Button }
            .metroPress(onClick = { host?.invoke(InfoRequest(title, text, tag)) }),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(22.dp).frame(2.dp, colors.subtle, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
            MetroText("i", size = 14.sp, weight = FontWeight.SemiBold, color = colors.subtle)
        }
    }
}

/** The box of an "i": its title, what the setting is for, and a button that closes it. */
@Composable
internal fun InfoBox(request: InfoRequest, onClose: () -> Unit) {
    val colors = LocalMetroColors.current
    DialogFrame(onCancel = onClose, title = request.title) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MetroText(request.title, size = 24.sp, maxLines = 3)
            MetroText(
                stringResource(request.text), size = 16.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = Int.MAX_VALUE, lineHeight = 23.sp,
                modifier = Modifier.testTag("${request.tag}:text"),
            )
            MetroButton(stringResource(R.string.settings_info_close), Modifier.testTag("${request.tag}:close"), onClick = onClose)
        }
    }
}
