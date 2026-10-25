package com.metrowin.launcher.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * A folder the user picked with the system's file picker (a document tree), where the launcher keeps its backup
 * and its sync file. It can be on the phone, on an SD card, or in an app that offers its files to the picker, such
 * as Google Drive. The launcher can only reach the folder the user picked, and only for as long as the user keeps it.
 */
class SafFolder(private val context: Context, private val tree: Uri) {
    private val resolver = context.contentResolver
    private val treeId: String get() = DocumentsContract.getTreeDocumentId(tree)
    private val folderUri: Uri get() = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)

    /** The name of the folder, or null when it cannot be read any more. */
    fun displayName(): String? = runCatching {
        resolver.query(folderUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    /** Blocking. Writes [bytes] to the file called [name] in the folder, making it if it is not there. */
    fun write(name: String, mimeType: String, bytes: ByteArray): Boolean = runCatching {
        val file = find(name) ?: DocumentsContract.createDocument(resolver, folderUri, mimeType, name) ?: return false
        resolver.openOutputStream(file, "wt")!!.use { it.write(bytes) }
        true
    }.getOrDefault(false)

    /** Blocking. The content of the file called [name], or null when there is none (or it is too big to be ours). */
    fun read(name: String, maxBytes: Int = MAX_BYTES): ByteArray? = runCatching {
        val file = find(name) ?: return null
        resolver.openInputStream(file)!!.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                if (out.size() + read > maxBytes) return null
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }.getOrNull()

    /** When the file called [name] was last changed (epoch milliseconds), or null. */
    fun lastModified(name: String): Long? = runCatching {
        val file = find(name) ?: return null
        resolver.query(file, arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }
    }.getOrNull()

    private fun find(name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == name) return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
            }
        }
        return null
    }

    companion object {
        const val MAX_BYTES = 4 * 1024 * 1024
        const val BACKUP_FILE = "metrowin-layout.json"
    }
}
