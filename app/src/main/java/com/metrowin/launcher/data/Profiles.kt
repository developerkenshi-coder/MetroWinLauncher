package com.metrowin.launcher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.metrowin.launcher.R

/** What kind of profile apps live in, besides the owner's own. */
enum class ProfileType(@param:StringRes val label: Int) {
    Work(R.string.profile_work),
    Private(R.string.profile_private),

    /** A clone of apps, or a profile of a kind this version does not know. */
    Other(R.string.profile_other);

    companion object {
        // The strings of UserManager.USER_TYPE_PROFILE_* (Android 15), written out so this can be tested.
        private const val MANAGED = "android.os.usertype.profile.MANAGED"
        private const val PRIVATE = "android.os.usertype.profile.PRIVATE"

        /**
         * From the user type Android reports (Android 15 and later). Before that it does not say, and any other
         * profile is taken for a work profile, which is what it nearly always is.
         */
        fun fromUserType(userType: String?): ProfileType = when (userType) {
            null, MANAGED -> Work
            PRIVATE -> Private
            else -> Other
        }
    }
}

/** A profile other than the owner's: its apps are listed with the others, and it can be paused. */
@Immutable
data class ProfileInfo(val serial: Long, val type: ProfileType, val paused: Boolean)

/** The profiles of the phone, and pausing them. The launcher may do that because it is the Home app. */
class ProfilesController(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val owner = Process.myUserHandle()

    fun typeOf(user: UserHandle): ProfileType {
        val userType = if (Build.VERSION.SDK_INT >= 35) {
            runCatching { launcherApps.getLauncherUserInfo(user)?.userType }.getOrNull()
        } else {
            null
        }
        return ProfileType.fromUserType(userType)
    }

    fun profiles(): List<ProfileInfo> =
        launcherApps.profiles.filter { it != owner }.map { user ->
            ProfileInfo(
                serial = userManager.getSerialNumberForUser(user),
                type = typeOf(user),
                paused = runCatching { userManager.isQuietModeEnabled(user) }.getOrDefault(false),
            )
        }

    /** Pauses or resumes a profile. Resuming the private space asks the user to authenticate. False when Android refuses. */
    fun setPaused(serial: Long, paused: Boolean): Boolean = runCatching {
        val user = userManager.getUserForSerialNumber(serial) ?: return false
        if (Build.VERSION.SDK_INT >= 28) userManager.requestQuietModeEnabled(paused, user) else false
    }.getOrDefault(false)
}
