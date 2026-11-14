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

    /** The phone, then every other mounted volume: an SD card or a USB stick (the ones Android lists, so an OTG drive too). */
    fun storages(context: Context): List<Place> {
        val primary = Place(context.getString(com.metrowin.launcher.R.string.fm_internal), Environment.getExternalStorageDirectory(), Place.Kind.Storage)
        val manager = context.getSystemService(StorageManager::class.java)
        val listed = runCatching {
            if (Build.VERSION.SDK_INT >= 30) manager.storageVolumes.filter { !it.isPrimary && it.state == Environment.MEDIA_MOUNTED }
                .mapNotNull { v -> v.directory?.let { Place(v.getDescription(context) ?: it.name, it, Place.Kind.Storage) } }
            else emptyList()
        }.getOrDefault(emptyList())
        val byApps = runCatching {
            context.getExternalFilesDirs(null).drop(1).mapNotNull { it?.absolutePath?.substringBefore("/Android/data")?.let(::File) }
                .filter { it.canRead() }.map { root -> Place(runCatching { manager.getStorageVolume(root)?.getDescription(context) }.getOrNull() ?: root.name, root, Place.Kind.Storage) }
        }.getOrDefault(emptyList())
        return listOf(primary) + (listed + byApps).distinctBy { it.file.absolutePath }.filter { it.file.absolutePath != primary.file.absolutePath }
    }

    fun standardFolders(context: Context): List<Place> = listOf(
        Environment.DIRECTORY_DOWNLOADS to com.metrowin.launcher.R.string.fm_downloads, Environment.DIRECTORY_DCIM to com.metrowin.launcher.R.string.fm_camera,
        Environment.DIRECTORY_PICTURES to com.metrowin.launcher.R.string.fm_pictures, Environment.DIRECTORY_MUSIC to com.metrowin.launcher.R.string.fm_music,
        Environment.DIRECTORY_MOVIES to com.metrowin.launcher.R.string.fm_movies, Environment.DIRECTORY_DOCUMENTS to com.metrowin.launcher.R.string.fm_documents,
    ).mapNotNull { (dir, name) -> Environment.getExternalStoragePublicDirectory(dir).takeIf { it.isDirectory }?.let { Place(context.getString(name), it, Place.Kind.Standard) } }

    fun trash(context: Context): FileTrash = FileTrash(File(context.getExternalFilesDir("trash") ?: File(context.filesDir, "trash"), "bin"))

    fun uri(context: Context, file: File): Uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)

    fun open(context: Context, file: File) {
        if (file.extension.equals("apk", true)) { install(context, file); return }
        runCatching {
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri(context, file), FileOps.mime(file))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(Intent.createChooser(view, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Starts the installer of Android for [file]; when the launcher may not install yet, opens the screen of Android where that is allowed. */
    fun install(context: Context, file: File) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
            context.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri(context, file), "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
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

/** The things of the file manager that can be switched, in the menu "more" > options. [default] is what it is until the person chooses. */
enum class Opt(val key: String, val default: Boolean, val label: Int) {
    Natural("opt_natural", true, com.metrowin.launcher.R.string.fmx_opt_natural),
    SortPerFolder("opt_sort_folder", false, com.metrowin.launcher.R.string.fmx_opt_sort_folder),
    Undo("opt_undo", true, com.metrowin.launcher.R.string.fmx_opt_undo),
    CategoryColors("opt_colors", true, com.metrowin.launcher.R.string.fmx_opt_colors),
    ListThumbs("opt_list_thumbs", true, com.metrowin.launcher.R.string.fmx_opt_list_thumbs),
    RichThumbs("opt_rich_thumbs", false, com.metrowin.launcher.R.string.fmx_opt_rich_thumbs),
    RecentMark("opt_recent_mark", true, com.metrowin.launcher.R.string.fmx_opt_recent_mark),
    LargeText("opt_large_text", false, com.metrowin.launcher.R.string.fmx_opt_large_text),
    Range("opt_range", true, com.metrowin.launcher.R.string.fmx_opt_range),
    Tags("opt_tags", true, com.metrowin.launcher.R.string.fmx_opt_tags),
    RecentView("opt_recent_view", true, com.metrowin.launcher.R.string.fmx_opt_recent_view),
    Analyzer("opt_analyzer", true, com.metrowin.launcher.R.string.fmx_opt_analyzer),
    Duplicates("opt_duplicates", false, com.metrowin.launcher.R.string.fmx_opt_duplicates),
    ContentSearch("opt_content_search", false, com.metrowin.launcher.R.string.fmx_opt_content_search),
    BulkRename("opt_bulk_rename", true, com.metrowin.launcher.R.string.fmx_opt_bulk_rename),
    ArchiveList("opt_archive_list", true, com.metrowin.launcher.R.string.fmx_opt_archive_list),
    ImageViewer("opt_image_viewer", false, com.metrowin.launcher.R.string.fmx_opt_image_viewer),
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

    fun flag(opt: Opt): Boolean = prefs.getBoolean(opt.key, opt.default)

    fun setFlag(opt: Opt, on: Boolean) = prefs.edit { putBoolean(opt.key, on) }

    /** The order chosen for a folder, for the people who ask for one order per folder (path to key, descending, natural); the newest 200. */
    var folderSorts: Map<String, Sort>
        get() = runCatching {
            val json = org.json.JSONObject(prefs.getString("folder_sorts", "{}")!!)
            json.keys().asSequence().associateWith { k -> json.getJSONArray(k).let { Sort(SortKey.valueOf(it.getString(0)), it.getBoolean(1)) } }
        }.getOrDefault(emptyMap())
        set(value) = prefs.edit { putString("folder_sorts", org.json.JSONObject(value.entries.toList().takeLast(200).associate { (k, v) -> k to JSONArray().put(v.key.name).put(v.descending) }).toString()) }

    /** The colour tag (1 to 5) of a file, by path; the newest 500. */
    var tags: Map<String, Int>
        get() = runCatching {
            val json = org.json.JSONObject(prefs.getString("tags", "{}")!!)
            json.keys().asSequence().associateWith { json.getInt(it) }
        }.getOrDefault(emptyMap())
        set(value) = prefs.edit { putString("tags", org.json.JSONObject(value.entries.toList().takeLast(500).associate { it.key to it.value }).toString()) }

    /** The side of a tile of the tile view in dp: 80, 104 or 140. */
    var tileSide: Int
        get() = prefs.getInt("tile_side", 104)
        set(value) = prefs.edit { putInt("tile_side", value) }

    var editorWrap: Boolean
        get() = prefs.getBoolean("editor_wrap", true)
        set(value) = prefs.edit { putBoolean("editor_wrap", value) }

    var editorNumbers: Boolean
        get() = prefs.getBoolean("editor_numbers", false)
        set(value) = prefs.edit { putBoolean("editor_numbers", value) }

    var editorSize: Int
        get() = prefs.getInt("editor_size", 16)
        set(value) = prefs.edit { putInt("editor_size", value.coerceIn(10, 28)) }

    fun lastPath(pane: Int): File? = prefs.getString("pane$pane", null)?.let(::File)?.takeIf { it.isDirectory }

    fun setLastPath(pane: Int, file: File) = prefs.edit { putString("pane$pane", file.absolutePath) }
}
