package com.metrowin.launcher.dialer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.litFill

private val KEYS = listOf(
    listOf('1' to "", '2' to "ABC", '3' to "DEF"),
    listOf('4' to "GHI", '5' to "JKL", '6' to "MNO"),
    listOf('7' to "PQRS", '8' to "TUV", '9' to "WXYZ"),
    listOf('*' to "", '0' to "+", '#' to ""),
)

/**
 * The twelve keys of a phone, in the flat Metro style. A touch gives [onKey]; a long press on the 0 gives the plus, and on the 1 (when
 * [onVoicemail] is set) calls the voicemail, as on every phone. A long press on 2 to 9 gives [onLongKey] (the quick dial), and the keys in
 * [marked] show a dot to say they have a number.
 * Used by the dialer and, to send tones into a call, by the screen of a call.
 */
@Composable
fun DialPad(onKey: (Char) -> Unit, modifier: Modifier = Modifier, keyHeight: androidx.compose.ui.unit.Dp = 64.dp, onVoicemail: (() -> Unit)? = null,
    onLongKey: ((Char) -> Unit)? = null,
    marked: Set<Char> = emptySet(),
) {
    val colors = LocalMetroColors.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KEYS.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (key, letters) ->
                    Box(
                        Modifier.weight(1f).height(keyHeight).testTag("dial:key:$key")
                            .metroPress(
                                onClick = { onKey(key) },
                                onLongClick = when {
                                    key == '0' -> ({ onKey('+') })
                                    key == '1' && onVoicemail != null -> onVoicemail
                                    key in '2'..'9' && onLongKey != null -> ({ onLongKey(key) })
                                    else -> null
                                },
                            )
                            .litFill(false),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (key in marked) MetroText("●", size = 8.sp, color = LocalAccent.current, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MetroText(key.toString(), size = 30.sp, weight = FontWeight.Normal)
                            if (letters.isNotEmpty()) MetroText(letters, size = 10.sp, weight = FontWeight.Normal, color = colors.subtle)
                        }
                    }
                }
            }
        }
    }
}
