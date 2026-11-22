package com.metrowin.launcher.music

import android.Manifest
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.metrowin.launcher.ui.mini.MiniActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The Music hub: the songs of the phone by artist, album and title, and playlists of its own, read from MediaStore. It only reads: a song is
 * played by the music app of the user (see [MusicPlay]), so there is no service of the launcher and no permission beyond reading audio.
 */
class MusicActivity : MiniActivity() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        var allowed by remember { mutableStateOf(hasAccess(context)) }
        val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = hasAccess(context) }
        var tracks by remember { mutableStateOf<List<Track>?>(null) }
        var reload by remember { mutableIntStateOf(0) }
        val playlists = remember { MusicPlaylists(context) }

        // A song that is added or removed while the hub is open shows up: the observer asks for a new reading, a moment after the last change.
        DisposableEffect(Unit) {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) { reload++ }
            }
            context.contentResolver.registerContentObserver(MusicReader.uri, true, observer)
            onDispose { context.contentResolver.unregisterContentObserver(observer) }
        }
        LaunchedEffect(allowed, reload) {
            if (!allowed) return@LaunchedEffect
            if (reload > 0) delay(400)
            tracks = withContext(Dispatchers.IO) { MusicReader.read(context) }
        }
        MusicScreen(
            tracks, needsPermission = !allowed, onAllow = { ask.launch(permission()) }, playlists = playlists,
            onPlay = { MusicPlay.song(context, it) }, onPlayAlbum = { MusicPlay.album(context, it) },
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) MusicCovers.trim()
    }

    private companion object {
        fun permission(): String = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

        fun hasAccess(context: Context) = ContextCompat.checkSelfPermission(context, permission()) == PackageManager.PERMISSION_GRANTED
    }
}
