package com.metrowin.launcher

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OneLauncherIconTest {
    // Phone, Messages and Mail are parts of the launcher: another launcher must not list them as three more apps.
    @Test
    fun onlyTheLauncherHasAnIconOfItsOwn() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val icons = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0,
        ).map { it.activityInfo.name }
        assertEquals(listOf("com.metrowin.launcher.MainActivity"), icons)
    }
}
