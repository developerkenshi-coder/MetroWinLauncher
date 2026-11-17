package com.metrowin.launcher

import android.app.Application
import com.metrowin.launcher.data.CrashLog
import com.metrowin.launcher.data.PrefsMigrations
import com.metrowin.launcher.data.SettingsStore

/** Runs before any activity, service or receiver of the launcher, so no one reads a preference file that is still on an old schema. */
class MetroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PrefsMigrations.run(this)
        // The newest crash is kept in a file, for the bug ticket.
        // AboutInfo.of reads the display, which an Application has none of: the version comes straight from the package.
        CrashLog.install(filesDir, runCatching { packageManager.getPackageInfo(packageName, 0).let { "${it.versionName} (${androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(it)})" } }.getOrDefault("?"))
        // The mini apps decode the settings in onCreate, before the first frame: this leaves the decoded copy ready, so they do not do it on the main thread.
        Thread { SettingsStore.of(this).load() }.start()
    }
}
