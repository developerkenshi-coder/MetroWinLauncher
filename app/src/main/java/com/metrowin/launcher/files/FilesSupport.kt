package com.metrowin.launcher.files

import com.metrowin.launcher.data.Prefs
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import org.json.JSONArray
import java.io.File

/** A place the file manager can go to at once: a storage, one of the folders every phone has, or a bookmark. */
data class Place(val name: String, val file: File, val kind: Kind) {
    enum class Kind { Storage, Standard, Bookmark }
}

/** What the file manager needs of Android: the access to the files, the storages, the bookmarks, and handing a file to another app. */
object FilesSupport {
    /** Whether the launcher may read and write every file (Android 11 and later ask for it in a screen of the settings). */
    fun hasAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** Opens the screen of Android that gives the access (Android 11 and later), on the page of this app when there is one. */
    fun openAccessSettings(context: Context) {
        val uri = Uri.fromParts("package", context.packageName, null)
        runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)) }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        }
    }

    fun storages(context: Context): List<Place> {
        val primary = Place(context.getString(com.metrowin.launcher.R.string.fm_internal), Environment.getExternalStorageDirectory(), Place.Kind.Storage)
        val manager = context.getSystemService(StorageManager::class.java)
        val others = runCatching {
            context.getExternalFilesDirs(null).drop(1).mapNotNull { it?.absolutePath?.substringBefore("/Android/data")?.let(::File) }
                .filter { it.canRead() }.map { root -> Place(runCatching { manager.getStorageVolume(root)?.getDescription(context) }.getOrNull() ?: root.name, root, Place.Kind.Storage) }
        }.getOrDefault(emptyList())
        return listOf(primary) + others
    }

    fun standardFolders(context: Context): List<Place> = listOf(
        Environment.DIRECTORY_DOWNLOADS to com.metrowin.launcher.R.string.fm_downloads, Environment.DIRECTORY_DCIM to com.metrowin.launcher.R.string.fm_camera,
        Environment.DIRECTORY_PICTURES to com.metrowin.launcher.R.string.fm_pictures, Environment.DIRECTORY_MUSIC to com.metrowin.launcher.R.string.fm_music,
        Environment.DIRECTORY_MOVIES to com.metrowin.launcher.R.string.fm_movies, Environment.DIRECTORY_DOCUMENTS to com.metrowin.launcher.R.string.fm_documents,
    ).mapNotNull { (dir, name) -> Environment.getExternalStoragePublicDirectory(dir).takeIf { it.isDirectory }?.let { Place(context.getString(name), it, Place.Kind.Standard) } }

    fun trash(context: Context): FileTrash = FileTrash(File(context.getExternalFilesDir("trash") ?: File(context.filesDir, "trash"), "bin"))

    fun uri(context: Context, file: File): Uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)

    fun open(context: Context, file: File) {
        runCatching {
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri(context, file), FileOps.mime(file))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(Intent.createChooser(view, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun share(context: Context, files: List<File>) {
        val plain = files.filter { it.isFile }
        if (plain.isEmpty()) return
        runCatching {
            val send = if (plain.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uri(context, plain[0])).setType(FileOps.mime(plain[0]))
            else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(plain.map { uri(context, it) })).setType("*/*")
            context.startActivity(Intent.createChooser(send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** What the file manager remembers: the order, the hidden files, the bookmarks and where each pane was. */
class FilesPrefs(context: Context) {
    private val prefs = Prefs.open(context, Prefs.FILES)

    var sort: Sort
        get() = Sort(runCatching { SortKey.valueOf(prefs.getString("sort", "Name")!!) }.getOrDefault(SortKey.Name), prefs.getBoolean("desc", false))
        set(value) = prefs.edit { putString("sort", value.key.name).putBoolean("desc", value.descending) }

    var showHidden: Boolean
        get() = prefs.getBoolean("hidden", false)
        set(value) = prefs.edit { putBoolean("hidden", value) }

    /** Whether the panels show tiles instead of a list. */
    var tiles: Boolean
        get() = prefs.getBoolean("tiles", false)
        set(value) = prefs.edit { putBoolean("tiles", value) }

    /** When the file manager last opened each file (path to time), for the order "last opened"; it holds the newest 300. */
    var opened: Map<String, Long>
        get() = runCatching {
            val json = org.json.JSONObject(prefs.getString("opened", "{}")!!)
            json.keys().asSequence().associateWith { json.getLong(it) }
        }.getOrDefault(emptyMap())
        private set(value) = prefs.edit { putString("opened", org.json.JSONObject(value).toString()) }

    fun markOpened(file: File): Map<String, Long> = FileOps.withOpened(opened, file.absolutePath, System.currentTimeMillis()).also { opened = it }

    var bookmarks: List<File>
        get() = runCatching {
            val array = JSONArray(prefs.getString("bookmarks", "[]"))
            (0 until array.length()).map { File(array.getString(it)) }
        }.getOrDefault(emptyList())
        set(value) = prefs.edit { putString("bookmarks", JSONArray(value.map { it.absolutePath }).toString()) }

    fun lastPath(pane: Int): File? = prefs.getString("pane$pane", null)?.let(::File)?.takeIf { it.isDirectory }

    fun setLastPath(pane: Int, file: File) = prefs.edit { putString("pane$pane", file.absolutePath) }
}
