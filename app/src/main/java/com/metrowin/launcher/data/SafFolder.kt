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
        resolver.openInputStream(file)!!.use { it.readAtMost(maxBytes) }
    }.getOrNull()

    /** Blocking. The names of the files in the folder. */
    fun names(): List<String> = runCatching {
        val found = ArrayList<String>()
        resolver.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId),
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
        )?.use { cursor -> while (cursor.moveToNext()) cursor.getString(0)?.let(found::add) }
        found
    }.getOrDefault(emptyList())

    /** Blocking. Takes the file called [name] out of the folder; false when there is none or it cannot be removed. */
    fun delete(name: String): Boolean = runCatching {
        val file = find(name) ?: return false
        DocumentsContract.deleteDocument(resolver, file)
    }.getOrDefault(false)

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

/** The sync file in a [SafFolder]. */
class SafSyncStorage(private val folder: SafFolder) : SyncStorage {
    override fun read(name: String): ByteArray? = folder.read(name)
    override fun write(name: String, bytes: ByteArray): Boolean = folder.write(name, "application/octet-stream", bytes)
}
