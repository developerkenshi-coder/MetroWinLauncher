package com.metrowin.launcher.ui.lock

import android.graphics.BitmapFactory
import android.service.dreams.DreamService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.metrowin.launcher.data.SettingsStore
import com.metrowin.launcher.ui.theme.MetroTheme
import java.io.File

/**
 * The Metro lock screen as a screen saver. Android keeps its own lock screen for apps that are not the system's, but
 * it lets the user pick any screen saver for when the phone charges or sits on a dock: this is that, with the photo,
 * the big time, the next alarm and the notifications of the Windows Phone lock screen. Swiping up leaves it.
 */
class MetroDreamService : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private var photo: ImageBitmap? by mutableStateOf(null)

    override fun onCreate() {
        super.onCreate()
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        val settings = SettingsStore(this).load()
        photo = loadPhoto(settings.hasLockPhoto, settings.hasStartBackground)
        val view = ComposeView(this).apply {
            // A service has no lifecycle of its own for Compose to follow: this one gives it.
            setViewTreeLifecycleOwner(this@MetroDreamService)
            setViewTreeSavedStateRegistryOwner(this@MetroDreamService)
            setContent {
                MetroTheme(accent = com.metrowin.launcher.ui.theme.MetroAccents[settings.accentIndex], dark = true) {
                    MetroLockScreen(
                        info = rememberLockInfo(),
                        photo = photo,
                        options = LockOptions(settings.lockShowAlarm, settings.lockShowNotifications, settings.lockShowBattery),
                        onUnlock = ::finish,
                    )
                }
            }
        }
        setContentView(view)
        registry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDetachedFromWindow() {
        registry.currentState = Lifecycle.State.DESTROYED
        super.onDetachedFromWindow()
    }

    /** The lock screen's own photo, or the Start photo, or none. */
    private fun loadPhoto(hasLock: Boolean, hasStart: Boolean): ImageBitmap? {
        val names = listOfNotNull("lock_background.jpg".takeIf { hasLock }, "start_background.jpg".takeIf { hasStart })
        return names.firstNotNullOfOrNull { name ->
            runCatching { BitmapFactory.decodeFile(File(filesDir, name).path)?.asImageBitmap() }.getOrNull()
        }
    }
}
