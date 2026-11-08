package com.metrowin.launcher

import android.app.Application
import com.metrowin.launcher.data.PrefsMigrations

/** Runs before any activity, service or receiver of the launcher, so no one reads a preference file that is still on an old schema. */
class MetroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PrefsMigrations.run(this)
    }
}
