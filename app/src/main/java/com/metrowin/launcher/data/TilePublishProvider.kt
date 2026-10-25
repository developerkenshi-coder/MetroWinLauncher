package com.metrowin.launcher.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * How another app puts live content on its own tile. It calls this provider (authority `com.metrowin.launcher.tiles`)
 * with the method `publish` and the extras `title`, `text` and `count`, or `clear`; it needs the permission
 * `com.metrowin.launcher.permission.PUBLISH_TILE`. The answer says whether it was accepted. Only `call` does
 * anything: there is no data to query, and the tile is always the caller's own.
 */
class TilePublishProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        val enabled = SettingsStore(context).load().thirdPartyTiles
        return TileFeed.handle(context, enabled, callingPackage, method, extras)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
