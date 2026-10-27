package com.metrowin.launcher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.metrowin.launcher.R
import com.metrowin.launcher.model.Glyph

/** The kinds of file the search can look for. The user chooses which of them appear (see [LauncherSettings.searchFileTypes]). */
enum class FileType(val code: String, @param:StringRes val label: Int, val glyph: Glyph) {
    Images("images", R.string.file_type_images, Glyph.Photos),
    Video("video", R.string.file_type_video, Glyph.Video),
    Audio("audio", R.string.file_type_audio, Glyph.Music),
    Documents("documents", R.string.file_type_documents, Glyph.Files);

    /** The permission that lets the phone's media index answer for this kind. */
    val permission: String
        get() = when {
            Build.VERSION.SDK_INT < 33 -> Manifest.permission.READ_EXTERNAL_STORAGE
            this == Images -> Manifest.permission.READ_MEDIA_IMAGES
            this == Video -> Manifest.permission.READ_MEDIA_VIDEO
            this == Audio -> Manifest.permission.READ_MEDIA_AUDIO
            // Android 13 and later lets an app see only its own documents; the media permission is the best that can be asked.
            else -> Manifest.permission.READ_MEDIA_IMAGES
        }

    companion object {
        fun fromCode(code: String) = entries.firstOrNull { it.code == code }
    }
}

/** A file the search found: where it is ([uri] in the media index), its name, kind, size in bytes and when it changed (milliseconds). */
@Immutable
data class FileMatch(val uri: String, val name: String, val type: FileType, val size: Long, val modifiedAt: Long)

/** Looks for files by name in the media index of the phone, only for the kinds the user chose. Blocking: call it off the main thread. */
class FileSearch(private val context: Context) {
    fun search(query: String, types: Set<FileType>, limitPerType: Int = LIMIT): List<FileMatch> {
        val text = query.trim()
        if (text.length < MIN_QUERY || types.isEmpty()) return emptyList()
        return types.sortedBy { it.ordinal }.flatMap { type ->
            if (!LivePermissions.isGranted(context, type.permission)) emptyList() else runCatching { find(text, type, limitPerType) }.getOrDefault(emptyList())
        }
    }

    private fun find(text: String, type: FileType, limit: Int): List<FileMatch> {
        val collection = when (type) {
            FileType.Images -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            FileType.Video -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            FileType.Audio -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            FileType.Documents -> MediaStore.Files.getContentUri("external")
        }
        val name = MediaStore.MediaColumns.DISPLAY_NAME
        var selection = "$name LIKE ? ESCAPE '\\'"
        val args = mutableListOf("%" + like(text) + "%")
        if (type == FileType.Documents) {
            selection += " AND (" + DOCUMENT_MIMES.joinToString(" OR ") { "${MediaStore.MediaColumns.MIME_TYPE} LIKE ?" } + ")"
            args += DOCUMENT_MIMES
        }
        val found = ArrayList<FileMatch>(limit)
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID, name, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED),
            selection, args.toTypedArray(), "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
        )?.use { cursor ->
            while (found.size < limit && cursor.moveToNext()) {
                val title = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                found += FileMatch(ContentUris.withAppendedId(collection, cursor.getLong(0)).toString(), title, type, cursor.getLong(2), cursor.getLong(3) * 1000L)
            }
        }
        return found
    }

    companion object {
        const val LIMIT = 4
        private const val MIN_QUERY = 2

        /** The mime types the documents are made of: PDF, text, the office formats and e-books. */
        private val DOCUMENT_MIMES = listOf("application/pdf", "text/%", "application/msword", "application/vnd.%", "application/epub%", "application/rtf")

        /** [text] as the pattern of a LIKE with `\` as the escape: the `%`, `_` and `\` it holds are letters, not wildcards. */
        fun like(text: String): String = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

        /** "2.4 MB", "310 KB": the size as a person reads it. */
        fun sizeText(bytes: Long): String = when {
            bytes >= 1_000_000_000L -> "%.1f GB".format(java.util.Locale.ROOT, bytes / 1e9)
            bytes >= 1_000_000L -> "%.1f MB".format(java.util.Locale.ROOT, bytes / 1e6)
            bytes >= 1_000L -> "${bytes / 1_000L} KB"
            else -> "$bytes B"
        }
    }
}
