package com.metrowin.launcher.photos

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.metrowin.launcher.ui.mini.MiniActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The Photo hub: the pictures of the phone by album, by day and the favourites, read only. A picture opens in the gallery the phone has. */
class PhotosActivity : MiniActivity() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        var granted by remember { mutableStateOf(hasAccess(context)) }
        val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = hasAccess(context) }
        var photos by remember { mutableStateOf<List<Photo>?>(null) }
        LaunchedEffect(granted) { if (granted) photos = withContext(Dispatchers.IO) { PhotoReader.read(context) } }
        PhotosScreen(photos, needsPermission = !granted, onAllow = { ask.launch(permissions()) }, onOpen = { open(context, it) })
    }

    private companion object {
        fun permissions(): Array<String> = when {
            // From Android 14 the user may share only some pictures: that is access too.
            Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        fun hasAccess(context: Context) = permissions().any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

        fun open(context: Context, photo: Photo) {
            val uri = PhotoReader.uriOf(photo.id)
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }
    }
}
