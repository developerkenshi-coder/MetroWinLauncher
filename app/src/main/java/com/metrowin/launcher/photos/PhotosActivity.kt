package com.metrowin.launcher.photos

import android.Manifest
import android.app.Activity
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.mini.MiniActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Photo hub: the pictures of the phone by album, by day, the favourites (and the memories and the duplicates), with a viewer
 * (zoom, swipe, share, delete, mark, info, slideshow). Reading is from MediaStore; deleting (to the system trash from Android 11, with a way back)
 * and marking go through the dialogs of Android, which ask the user first.
 */
class PhotosActivity : MiniActivity() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val prefs = rememberPhotoPrefs()
        var access by remember { mutableStateOf(accessOf(context)) }
        val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { access = accessOf(context) }
        var photos by remember { mutableStateOf<List<Photo>?>(null) }
        // How many were read, and whether the last page was full (so there may be more).
        var more by remember { mutableStateOf(false) }
        var reload by remember { mutableIntStateOf(0) }
        // The picture on show in the viewer (by id: the list changes under it when a picture is deleted).
        var viewing by remember { mutableStateOf<Long?>(null) }
        // Where the small picture of it was, so the viewer grows out of that place.
        var viewingFrom by remember { mutableStateOf<Rect?>(null) }
        // The pictures just moved to the trash (to bring back), and the ones waiting for the question before an old-fashioned delete.
        var trashed by remember { mutableStateOf<List<Uri>>(emptyList()) }
        var pendingTrash by remember { mutableStateOf<List<Uri>>(emptyList()) }
        var confirm by remember { mutableStateOf<List<Photo>?>(null) }

        // A picture that is taken or removed while the hub is open shows up: the observer asks for a new reading, a moment after the last change.
        DisposableEffect(Unit) {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) { reload++ }
            }
            context.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
            onDispose { context.contentResolver.unregisterContentObserver(observer) }
        }
        LaunchedEffect(access.any, reload) {
            if (!access.any) return@LaunchedEffect
            if (reload > 0) delay(400)
            val keep = maxOf(photos?.size ?: 0, PhotoReader.LIMIT)
            val read = withContext(Dispatchers.IO) { PhotoReader.read(context, keep) }
            photos = read
            more = read.size >= keep
        }
        val system = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) trashed = pendingTrash else trashed = emptyList()
            pendingTrash = emptyList()
        }
        fun launchSystem(sender: android.content.IntentSender) = system.launch(IntentSenderRequest.Builder(sender).build())

        val actions = PhotoActions(
            share = { list -> scope.launch { share(context, list, prefs[PhotoOption.StripMetadata]) } },
            delete = { list -> delete(context, list, ::launchSystem, { pendingTrash = it }) { confirm = list } },
            favorite = { list, on -> favorite(context, list, on, ::launchSystem) },
            loadMore = if (more) ({
                scope.launch {
                    val next = withContext(Dispatchers.IO) { PhotoReader.read(context, PhotoReader.LIMIT, photos?.size ?: 0) }
                    photos = photos.orEmpty() + next
                    more = next.size >= PhotoReader.LIMIT
                }
                Unit
            }) else null,
            partial = access.partial,
            selectMore = { ask.launch(permissions()) },
        )
        Box(Modifier.fillMaxSize()) {
            PhotosScreen(photos, needsPermission = !access.any, onAllow = { ask.launch(permissions()) }, onOpen = { photo, from -> viewing = photo.id; viewingFrom = from }, prefs = prefs, actions = actions)
            val list = remember(photos) { photos.orEmpty().sortedByDescending { it.takenAt } }
            if (viewing != null && list.isNotEmpty()) {
                PhotoViewer(
                    list, start = list.indexOfFirst { it.id == viewing }.coerceAtLeast(0), onClose = { viewing = null }, from = viewingFrom, prefs = prefs,
                    onShare = { actions.share(listOf(it)) },
                    onDelete = { actions.delete(listOf(it)) },
                    onFavorite = { photo, on -> actions.favorite(listOf(photo), on) },
                    onWallpaper = { wallpaper(context, it) },
                )
            }
            confirm?.let { list -> ConfirmDelete(list.size, onYes = { confirm = null; deleteNow(context, list, { reload++ }) }, onNo = { confirm = null }) }
            if (trashed.isNotEmpty()) {
                // Seconds to change one's mind; the system trash keeps them for a month in any case.
                LaunchedEffect(trashed) { delay(8_000); trashed = emptyList() }
                Notice(
                    context.resources.getQuantityString(R.plurals.ph_trashed, trashed.size, trashed.size), stringResource(R.string.ph_undo),
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                    onAction = { restore(context, trashed, ::launchSystem); trashed = emptyList() },
                )
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        PhotoThumbs.trim(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) FullCache.clear()
    }

    /** What the user let the hub read: [any] is access to at least some pictures; [partial] is the Android 14 choice "only the ones I pick". */
    private class Access(val any: Boolean, val partial: Boolean)

    private companion object {
        fun permissions(): Array<String> = when {
            // From Android 14 the user may share only some pictures: that is access too.
            Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        private fun granted(context: Context, permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        fun accessOf(context: Context): Access {
            val any = permissions().any { granted(context, it) }
            val partial = Build.VERSION.SDK_INT >= 34 && !granted(context, Manifest.permission.READ_MEDIA_IMAGES) && granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            return Access(any, partial)
        }

        fun uris(list: List<Photo>) = list.map { PhotoReader.uriOf(it.id) }

        /** The system share sheet; with [strip] the pictures go as copies without metadata (made away from the screen). */
        suspend fun share(context: Context, list: List<Photo>, strip: Boolean) {
            val streams: List<Uri> = if (strip) {
                withContext(Dispatchers.IO) { runCatching { strippedCopies(context, list) }.getOrNull() }
                    ?: return Toast.makeText(context, R.string.ph_strip_failed, Toast.LENGTH_LONG).show()
            } else uris(list)
            val send = (if (streams.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, streams.first())
            else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(streams)))
                .setType("image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }

        fun wallpaper(context: Context, photo: Photo) {
            val attach = Intent(Intent.ACTION_ATTACH_DATA).setDataAndType(PhotoReader.uriOf(photo.id), "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { context.startActivity(Intent.createChooser(attach, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }

        /**
         * From Android 11 the pictures go to the system trash (one dialog of Android asks first, and they can be brought back for a month);
         * before it they are deleted for good, after a question of the hub ([confirm]). [remember] keeps what went to the trash, for the undo.
         */
        fun delete(context: Context, list: List<Photo>, ask: (android.content.IntentSender) -> Unit, remember: (List<Uri>) -> Unit, confirm: () -> Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val uris = uris(list)
                runCatching { ask(MediaStore.createTrashRequest(context.contentResolver, uris, true).intentSender); remember(uris) }
                    .onFailure { Toast.makeText(context, R.string.ph_delete_failed, Toast.LENGTH_LONG).show() }
            } else confirm()
        }

        /** Android 10 and before: the delete itself, one picture at a time; what Android refuses is said, not swallowed. */
        fun deleteNow(context: Context, list: List<Photo>, done: () -> Unit) {
            var failed = false
            list.forEach { photo ->
                try {
                    context.contentResolver.delete(PhotoReader.uriOf(photo.id), null, null)
                } catch (e: SecurityException) {
                    failed = true
                } catch (e: RuntimeException) {
                    failed = true
                }
            }
            if (failed) Toast.makeText(context, R.string.ph_delete_failed, Toast.LENGTH_LONG).show()
            done()
        }

        fun restore(context: Context, uris: List<Uri>, ask: (android.content.IntentSender) -> Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) runCatching { ask(MediaStore.createTrashRequest(context.contentResolver, uris, false).intentSender) }
        }

        fun favorite(context: Context, list: List<Photo>, on: Boolean, ask: (android.content.IntentSender) -> Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) runCatching { ask(MediaStore.createFavoriteRequest(context.contentResolver, uris(list), on).intentSender) }
        }
    }
}
