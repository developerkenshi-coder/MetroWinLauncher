package com.metrowin.launcher.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import com.metrowin.launcher.R

/** The quick profiles: all the settings kept as they are now under a name, and put back with one touch from the Action Center. */
enum class QuickProfile(val id: String, @param:StringRes val label: Int) {
    Home("home", R.string.profile_home),
    Work("work", R.string.profile_work_quick),
    Night("night", R.string.profile_night),
}

/** Which profiles hold something, and which one was put on last. */
@Immutable
data class ProfileState(val saved: Set<QuickProfile> = emptySet(), val active: QuickProfile? = null)

/** The profiles on the phone: each is the text of the settings (see [SettingsCodec], without what belongs to this phone alone). */
class SettingProfileStore(context: Context) {
    private val prefs = Prefs.main(context)

    fun state(): ProfileState = ProfileState(
        QuickProfile.entries.filterTo(HashSet()) { prefs.contains(key(it)) },
        QuickProfile.entries.firstOrNull { it.id == prefs.getString(ACTIVE, null) },
    )

    fun read(profile: QuickProfile): String? = prefs.getString(key(profile), null)

    fun save(profile: QuickProfile, settings: LauncherSettings) = prefs.edit {
        putString(key(profile), SettingsCodec.encode(settings, portable = true))
        putString(ACTIVE, profile.id)
    }

    fun forget(profile: QuickProfile) = prefs.edit {
        remove(key(profile))
        if (prefs.getString(ACTIVE, null) == profile.id) remove(ACTIVE)
    }

    fun setActive(profile: QuickProfile) = prefs.edit { putString(ACTIVE, profile.id) }

    private fun key(profile: QuickProfile) = "setting_profile_${profile.id}"

    private companion object {
        const val ACTIVE = "setting_profile_active"
    }
}
