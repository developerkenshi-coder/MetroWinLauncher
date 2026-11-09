package com.metrowin.launcher.photos

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.MediaStore
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
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

/** The Photo hub: the pictures of the phone by album, by day and the favourites, read only. A picture opens in the viewer of the hub (zoom, swipe, share, delete, info). */
class PhotosActivity : MiniActivity() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        var granted by remember { mutableStateOf(hasAccess(context)) }
        val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = hasAccess(context) }
        var photos by remember { mutableStateOf<List<Photo>?>(null) }
        var reload by remember { mutableIntStateOf(0) }
        // The picture on show in the viewer (by id: the list changes under it when a picture is deleted).
        var viewing by remember { mutableStateOf<Long?>(null) }
        LaunchedEffect(granted, reload) { if (granted) photos = withContext(Dispatchers.IO) { PhotoReader.read(context) } }
        val deleted = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { reload++ }
        Box(Modifier.fillMaxSize()) {
            PhotosScreen(photos, needsPermission = !granted, onAllow = { ask.launch(permissions()) }, onOpen = { viewing = it.id })
            val list = remember(photos) { photos.orEmpty().sortedByDescending { it.takenAt } }
            if (viewing != null && list.isNotEmpty()) {
                PhotoViewer(
                    list, start = list.indexOfFirst { it.id == viewing }.coerceAtLeast(0), onClose = { viewing = null },
                    onShare = { share(context, it) },
                    onDelete = { photo -> delete(context, photo, deleted::launch) { reload++ } },
                )
            }
        }
    }

    private companion object {
        fun permissions(): Array<String> = when {
            // From Android 14 the user may share only some pictures: that is access too.
            Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        fun hasAccess(context: Context) = permissions().any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

        fun share(context: Context, photo: Photo) {
            val send = Intent(Intent.ACTION_SEND).setType("image/*").putExtra(Intent.EXTRA_STREAM, PhotoReader.uriOf(photo.id)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }

        /** Android asks the user before a picture goes (from 11 on, one dialog for it); [done] is called when it went without asking. */
        fun delete(context: Context, photo: Photo, ask: (IntentSenderRequest) -> Unit, done: () -> Unit) {
            val uri = PhotoReader.uriOf(photo.id)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ask(IntentSenderRequest.Builder(MediaStore.createDeleteRequest(context.contentResolver, listOf(uri)).intentSender).build())
                } else {
                    try {
                        context.contentResolver.delete(uri, null, null)
                        done()
                    } catch (e: SecurityException) {
                        val recoverable = e as? android.app.RecoverableSecurityException ?: return@runCatching
                        ask(IntentSenderRequest.Builder(recoverable.userAction.actionIntent.intentSender).build())
                    }
                }
            }
        }
    }
}
