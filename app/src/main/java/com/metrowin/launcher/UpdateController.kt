package com.metrowin.launcher

import android.app.Application
import com.metrowin.launcher.data.AppUpdate
import com.metrowin.launcher.data.UpdateChecker
import com.metrowin.launcher.data.UpdateInstaller
import com.metrowin.launcher.data.UpdateState
import com.metrowin.launcher.data.UpdateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Updates from GitHub: asks whether there is a newer release, downloads it and hands it to the installer of Android. */
internal class UpdateController(private val app: Application, private val scope: CoroutineScope) {
    private val store = UpdateStore(app)
    private fun installedVersion() = UpdateChecker.installedVersion(app)
    private val _state = MutableStateFlow<UpdateState>(
        store.release?.takeIf { AppUpdate.isNewer(it.version, installedVersion()) }?.let { UpdateState.Available(it.version, it.notes) } ?: UpdateState.Idle,
    )
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private fun busy() = _state.value is UpdateState.Checking || _state.value is UpdateState.Downloading

    /** Asks GitHub whether there is a newer release; the answer is the [state]. */
    fun check() {
        if (busy()) return
        _state.value = UpdateState.Checking
        scope.launch {
            val answered = withContext(Dispatchers.IO) { runCatching { UpdateChecker.check(app, installedVersion()) }.getOrDefault(false) }
            val found = store.release
            _state.value = when {
                !answered -> UpdateState.Failed
                found != null -> UpdateState.Available(found.version, found.notes)
                else -> UpdateState.UpToDate
            }
        }
    }

    /** The check every 12 hours also runs when the launcher comes up and the last one is older than that (the job may wait for Android). */
    fun checkIfDue(auto: Boolean) {
        if (auto && !busy() && System.currentTimeMillis() - store.lastCheck > AppUpdate.CHECK_EVERY_MS) check()
    }

    /** Downloads the newer release and hands it to the installer of Android, which opens its confirmation screen by itself. */
    fun install() {
        val release = store.release ?: return
        if (busy()) return
        if (!UpdateInstaller.mayInstall(app)) {
            // Android asks the user once to allow this app to install apps; the next touch goes on from here.
            _state.value = UpdateState.NeedsPermission
            UpdateInstaller.openPermissionScreen(app)
            return
        }
        _state.value = UpdateState.Downloading(0)
        scope.launch {
            val file = UpdateInstaller.cacheFile(app)
            val done = withContext(Dispatchers.IO) {
                AppUpdate.download(release, file) { percent -> _state.value = UpdateState.Downloading(percent) } && UpdateInstaller.install(app, file)
            }
            _state.value = if (done) UpdateState.Available(release.version, release.notes) else UpdateState.Failed
        }
    }
}
