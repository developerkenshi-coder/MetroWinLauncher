package com.metrowin.launcher

import com.metrowin.launcher.data.AppFilter
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.ProfileType
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfilesTest {
    @Test
    fun theUserTypeTellsTheKindOfProfile() {
        assertEquals(ProfileType.Work, ProfileType.fromUserType("android.os.usertype.profile.MANAGED"))
        assertEquals(ProfileType.Private, ProfileType.fromUserType("android.os.usertype.profile.PRIVATE"))
        assertEquals(ProfileType.Other, ProfileType.fromUserType("android.os.usertype.profile.CLONE"))
    }

    @Test
    fun beforeAndroid15AnyProfileIsTakenForTheWorkOne() {
        assertEquals(ProfileType.Work, ProfileType.fromUserType(null))
    }

    private fun app(id: String, work: Boolean = false, private: Boolean = false) =
        AppItem(id, id, Launch.Action("x"), isWork = work, isPrivate = private)

    @Test
    fun workAndPrivateAppsCanBeLeftOutOfTheListSeparately() {
        val apps = listOf(app("own"), app("work", work = true), app("private", private = true))
        fun visible(settings: LauncherSettings) = AppFilter.visible(apps, settings).map { it.id }
        assertEquals(listOf("own", "work", "private"), visible(LauncherSettings()))
        assertEquals(listOf("own", "private"), visible(LauncherSettings(showWorkApps = false)))
        assertEquals(listOf("own", "work"), visible(LauncherSettings(showPrivateApps = false)))
    }
}
