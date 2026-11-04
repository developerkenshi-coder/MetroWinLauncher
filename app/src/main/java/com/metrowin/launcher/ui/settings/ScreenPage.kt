package com.metrowin.launcher.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.metrowin.launcher.R
import androidx.annotation.StringRes
import com.metrowin.launcher.data.AppLanguages
import com.metrowin.launcher.data.LaunchAnimation
import com.metrowin.launcher.data.OrientationMode
import com.metrowin.launcher.data.PagePadding

/** How the launcher uses the screen: rotation (and with it landscape) and the refresh rate. */
@Composable
fun ScreenPage(env: SettingsEnv) {
    val settings = env.settings
    val modes = OrientationMode.entries
    SettingsPage {
        OptionList(
            label = stringResource(R.string.screen_orientation),
            options = modes.map { stringResource(it.label) },
            selected = modes.indexOf(settings.orientation),
            onSelect = { i -> env.onChange { it.copy(orientation = modes[i]) } },
            hint = stringResource(R.string.screen_orient_hint),
            tag = "screen:orientation", id = "screen.orientation",
        )
        val animations = LaunchAnimation.entries
        OptionList(
            label = stringResource(R.string.screen_launch),
            options = animations.map { stringResource(it.label) },
            selected = animations.indexOf(settings.launchAnimation),
            onSelect = { i -> env.onChange { it.copy(launchAnimation = animations[i]) } },
            hint = stringResource(R.string.screen_launch_hint) + " " + stringResource(R.string.screen_launch_hint_more),
            tag = "screen:launch", id = "screen.launch",
        )
        val paddings = PagePadding.entries
        OptionList(
            label = stringResource(R.string.screen_padding),
            options = paddings.map { stringResource(it.label) },
            selected = paddings.indexOf(settings.pagePadding),
            onSelect = { i -> env.onChange { it.copy(pagePadding = paddings[i]) } },
            hint = stringResource(R.string.screen_padding_hint),
            tag = "screen:padding", id = "screen.padding",
        )
        env.onLanguage?.let { change ->
            val tags = AppLanguages.tags
            OptionList(
                label = stringResource(R.string.screen_language),
                options = tags.map { stringResource(languageName(it)) },
                selected = tags.indexOf(AppLanguages.normalize(env.language)).coerceAtLeast(0),
                onSelect = { i -> change(tags[i]) },
                hint = stringResource(R.string.screen_language_hint),
                tag = "screen:language", id = "screen.language",
            )
        }
        ToggleSetting(
            stringResource(R.string.screen_high_refresh), settings.highRefreshRate,
            onChange = { value -> env.onChange { it.copy(highRefreshRate = value) } },
            hint = stringResource(R.string.screen_high_refresh_hint), tag = "screen:refresh", id = "screen.refresh",
        )
    }
}

@StringRes
private fun languageName(tag: String): Int = when (tag) {
    "en" -> R.string.language_en
    "it" -> R.string.language_it
    "fr" -> R.string.language_fr
    "es" -> R.string.language_es
    "de" -> R.string.language_de
    "pt" -> R.string.language_pt
    else -> R.string.language_system
}
