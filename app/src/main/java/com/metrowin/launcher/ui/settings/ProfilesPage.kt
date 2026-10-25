package com.metrowin.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.ProfileInfo
import com.metrowin.launcher.data.ProfileType

/** The work profile and the private space: whether they run, and whether their apps are in the app list. */
@Composable
fun ProfilesPage(env: SettingsEnv) {
    SettingsPage {
        if (env.profiles.isEmpty()) {
            SettingHint(stringResource(R.string.profile_none), androidx.compose.ui.Modifier.testTag("profiles:none"))
        } else {
            env.profiles.forEach { profile -> ProfileSection(profile, env) }
            SettingHint(stringResource(R.string.profile_hint))
        }
    }
}

@Composable
private fun ProfileSection(profile: ProfileInfo, env: SettingsEnv) {
    val settings = env.settings
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingHeading(stringResource(profile.type.label))
        InfoSetting(
            stringResource(profile.type.label),
            stringResource(if (profile.paused) R.string.profile_paused else R.string.profile_active),
        )
        ActionSetting(
            text = stringResource(
                when {
                    !profile.paused -> R.string.profile_pause
                    profile.type == ProfileType.Private -> R.string.profile_unlock
                    else -> R.string.profile_resume
                },
            ),
            onClick = { env.onSetProfilePaused(profile.serial, !profile.paused) },
            tag = "profiles:toggle:${profile.serial}",
        )
        // Work and private apps each have a switch; any other profile follows the work one.
        val private = profile.type == ProfileType.Private
        ToggleSetting(
            label = stringResource(R.string.profile_show_apps),
            checked = if (private) settings.showPrivateApps else settings.showWorkApps,
            onChange = { value ->
                env.onChange { if (private) it.copy(showPrivateApps = value) else it.copy(showWorkApps = value) }
            },
            tag = "profiles:show:${profile.serial}",
        )
    }
}
