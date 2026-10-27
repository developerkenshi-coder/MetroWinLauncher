package com.metrowin.launcher.ui.start

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.sheet

/** One line of a [ChoiceSheet]: an [id] to tell it by (the test tag), a mark, and the words. */
data class Choice(val id: String, val glyph: Glyph?, val label: String)

/** A list that comes up from the bottom to choose one thing from: the switches to keep on Start, the layouts of Start. */
@Composable
fun ChoiceSheet(title: String, choices: List<Choice>, onPick: (Choice) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier, tagPrefix: String = "choice") {
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
                .sheet()
                .pointerInput(Unit) { detectTapGestures { } }
                .navigationBarsPadding()
                .padding(top = 12.dp),
        ) {
            MetroText(title, size = 15.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(bottom = 76.dp)) {
                choices.forEach { choice ->
                    Row(
                        Modifier.fillMaxWidth().testTag("$tagPrefix:${choice.id}").metroPress(onClick = { onPick(choice) }).padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (choice.glyph != null) {
                            GlyphIcon(choice.glyph, 28.dp)
                            Spacer(Modifier.width(16.dp))
                        }
                        MetroText(choice.label, size = 21.sp)
                    }
                }
            }
        }
    }
}
