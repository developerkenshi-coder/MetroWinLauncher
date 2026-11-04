package com.metrowin.launcher

import android.content.Intent
import androidx.activity.ComponentActivity
import com.metrowin.launcher.ui.mini.PageTurn
import com.metrowin.launcher.ui.mini.returnToLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReturnToLauncherTest {
    private fun screen(intent: Intent) = Robolectric.buildActivity(ComponentActivity::class.java, intent).create().get()

    @Test
    fun aScreenTheLauncherOpenedGoesBackToTheLauncherByNameWhenItIsAloneInItsTask() {
        val screen = screen(Intent().putExtra(PageTurn.EXTRA, false))
        screen.returnToLauncher()
        val started = shadowOf(screen).nextStartedActivity
        assertEquals(MainActivity::class.java.name, started.component!!.className)
        // By name, never as "Home": that is what makes Android ask which Home app to use.
        assertNull(started.categories)
    }

    @Test
    fun aScreenAnotherAppOpenedLeavesTheChoiceOfWhereToGoToAndroid() {
        val screen = screen(Intent(Intent.ACTION_VIEW))
        screen.returnToLauncher()
        assertNull(shadowOf(screen).nextStartedActivity)
    }

    @Test
    fun aCallThatDidNotComeFromTheLauncherDoesNotBringItUp() {
        val screen = screen(Intent())
        screen.returnToLauncher(openedByLauncher = false)
        assertNull(shadowOf(screen).nextStartedActivity)
    }
}
