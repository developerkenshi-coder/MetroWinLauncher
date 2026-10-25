package com.metrowin.launcher

import androidx.lifecycle.Lifecycle
import com.metrowin.launcher.ui.lock.MetroDreamService
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Only what a JVM can check: Android attaches the dream window itself, so the screen is tested apart (LockScreenTest). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MetroDreamServiceTest {
    @Test
    fun theServiceStartsWithALifecycleAndASavedStateRegistryForCompose() {
        val service = Robolectric.buildService(MetroDreamService::class.java).create().get()
        assertEquals(Lifecycle.State.CREATED, service.lifecycle.currentState)
        // The registry is attached and restored: Compose asks it for its saved state, which is none here.
        assertEquals(null, service.savedStateRegistry.consumeRestoredStateForKey("nothing"))
    }
}
