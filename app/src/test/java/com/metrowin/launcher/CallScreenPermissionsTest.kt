package com.metrowin.launcher

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CallScreenPermissionsTest {
    // The screen of a call holds a proximity wake lock; without this permission it crashed when it opened, and Android showed the
    // call on the phone app of Google instead.
    @Test
    fun theScreenOfACallHasWhatItNeeds() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val asked = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        listOf("android.permission.WAKE_LOCK", "android.permission.CALL_PHONE", "android.permission.USE_FULL_SCREEN_INTENT").forEach {
            assertTrue("$it is asked in the manifest", it in asked)
        }
    }
}
