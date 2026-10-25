package com.metrowin.launcher.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroToggle
import com.metrowin.launcher.ui.components.SettingLabel
import com.metrowin.launcher.ui.kids.CheckSquare
import com.metrowin.launcher.ui.theme.LocalMetroColors

/** A scrolling page of settings, with the spacing every page shares. */
@Composable
fun SettingsPage(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
        content = {
            content()
            Spacer(Modifier.height(24.dp))
        },
    )
}

/** A line under a setting that says what it does. */
@Composable
fun SettingHint(text: String, modifier: Modifier = Modifier) {
    MetroText(
        text, size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle,
        maxLines = Int.MAX_VALUE, lineHeight = 19.sp, modifier = modifier,
    )
}

/** A heading that groups settings inside a page. */
@Composable
fun SettingHeading(text: String, modifier: Modifier = Modifier) {
    MetroText(text, size = 26.sp, modifier = modifier)
}

/** The Windows Phone toggle with, optionally, a line under it that says what it does. */
@Composable
fun ToggleSetting(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    enabled: Boolean = true,
    tag: String? = null,
) {
    Column(modifier.alpha(if (enabled) 1f else 0.4f).semantics { if (!enabled) disabled() }) {
        MetroToggle(
            label, checked,
            onChange = { if (enabled) onChange(it) },
            modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
        )
        if (hint != null) SettingHint(hint, Modifier.padding(top = 2.dp))
    }
}

/** Mutually exclusive options one under the other, for choices that are too long to sit side by side. */
@Composable
fun OptionList(
    label: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    tag: String? = null,
) {
    Column(modifier.then(if (tag != null) Modifier.testTag(tag) else Modifier)) {
        SettingLabel(label)
        options.forEachIndexed { index, text ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .testTag("${tag ?: "option"}:$index")
                    .metroPress(onClick = { onSelect(index) })
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckSquare(index == selected)
                Spacer(Modifier.width(14.dp))
                MetroText(text, size = 20.sp, modifier = Modifier.weight(1f))
            }
        }
        if (hint != null) SettingHint(hint, Modifier.padding(top = 4.dp))
    }
}

/**
 * A choice that shows only what is chosen until it is touched, then unfolds the options one under the other. For
 * choices with many options, where [OptionList] would take the whole page.
 */
@Composable
fun DropdownSetting(
    label: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "dropdown",
) {
    var open by remember { mutableStateOf(false) }
    val colors = LocalMetroColors.current
    Column(modifier) {
        SettingLabel(label)
        Row(
            Modifier
                .fillMaxWidth()
                .testTag("$tag:header")
                .metroPress(onClick = { open = !open })
                .border(2.dp, colors.foreground)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetroText(options.getOrElse(selected) { "" }, size = 19.sp, weight = FontWeight.Normal, modifier = Modifier.weight(1f))
            MetroText(if (open) "−" else "+", size = 24.sp, weight = FontWeight.Normal, color = colors.subtle)
        }
        if (open) {
            Column(Modifier.padding(top = 6.dp)) {
                options.forEachIndexed { index, text ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .testTag("$tag:$index")
                            .metroPress(onClick = {
                                onSelect(index)
                                open = false
                            })
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CheckSquare(index == selected)
                        Spacer(Modifier.width(14.dp))
                        MetroText(text, size = 18.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** A number with a button each side to make it smaller or bigger. */
@Composable
fun StepperSetting(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    canDecrement: Boolean = true,
    canIncrement: Boolean = true,
    hint: String? = null,
    tag: String? = null,
) {
    Column(modifier.then(if (tag != null) Modifier.testTag(tag) else Modifier)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MetroText(label, size = 20.sp, modifier = Modifier.weight(1f))
            StepButton("−", stringResource(R.string.stepper_minus), canDecrement, onDecrement, tag?.let { "$it:minus" })
            MetroText(value, size = 22.sp, modifier = Modifier.width(64.dp).padding(horizontal = 6.dp))
            StepButton("+", stringResource(R.string.stepper_plus), canIncrement, onIncrement, tag?.let { "$it:plus" })
        }
        if (hint != null) SettingHint(hint, Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun StepButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit, tag: String?) {
    Box(
        Modifier
            .size(44.dp)
            .semantics { contentDescription = description }
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .alpha(if (enabled) 1f else 0.35f)
            .then(if (enabled) Modifier.metroPress(onClick = onClick) else Modifier)
            .border(2.dp, LocalMetroColors.current.foreground),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(symbol, size = 24.sp, weight = FontWeight.Normal)
    }
}

/** A button that does something, with a line under it that says what. */
@Composable
fun ActionSetting(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    enabled: Boolean = true,
    tag: String? = null,
) {
    Column(modifier) {
        MetroButton(
            text,
            Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.4f)
                // TalkBack says "dimmed" for a button that does nothing.
                .semantics { if (!enabled) disabled() }
                .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        ) { if (enabled) onClick() }
        if (hint != null) SettingHint(hint, Modifier.padding(top = 8.dp))
    }
}

/** A fact the user cannot change: a label and its value. */
@Composable
fun InfoSetting(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MetroText(label, size = 18.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, modifier = Modifier.weight(1f))
        MetroText(value, size = 18.sp, weight = FontWeight.Normal)
    }
}
