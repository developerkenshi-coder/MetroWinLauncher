package com.metrowin.launcher

import android.app.Application
import com.metrowin.launcher.data.PrefsMigrations
import com.metrowin.launcher.data.SettingsStore

/** Runs before any activity, service or receiver of the launcher, so no one reads a preference file that is still on an old schema. */
class MetroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PrefsMigrations.run(this)
        // The mini apps decode the settings in onCreate, before the first frame: this leaves the decoded copy ready, so they do not do it on the main thread.
        Thread { SettingsStore.of(this).load() }.start()
    }
}
