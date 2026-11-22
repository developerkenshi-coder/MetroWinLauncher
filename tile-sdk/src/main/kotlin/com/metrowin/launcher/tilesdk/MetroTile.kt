/*
 * Copyright 2026 The MetroWin Launcher authors
 * Licensed under the Apache License, Version 2.0 (see tile-sdk/LICENSE).
 */
package com.metrowin.launcher.tilesdk

import android.content.Context
import android.net.Uri
import android.os.Bundle

/** What a call to [MetroTile] came to. */
sealed interface MetroTileResult {
    /** The launcher took it (the tile shows it if the user pinned your app on Start). */
    data object Accepted : MetroTileResult

    /** The launcher is not installed, or not reachable (a phone that stopped it, or an old version without the feature). */
    data object NotInstalled : MetroTileResult

    /** The launcher answered no: [reason] is `unknown caller`, `turned off by the user`, `too fast` or `unknown method`. */
    data class Refused(val reason: String) : MetroTileResult
}

/**
 * Puts live content on the tile of **your own app** in MetroWin Launcher: a title, a line of text and a number. The tile is always the one of the
 * app that calls (Android tells the launcher who called; nothing you pass can name another app), and it only shows if the user pinned your app on
 * Start. The user can turn the feature off in the launcher: you get [MetroTileResult.Refused].
 *
 * Call it when the content changes, not on a timer: a change that comes less than a quarter of a second after the last one is refused
 * (`too fast`), the same values again are always accepted and cost nothing. Each call is a short call to another process: do it off the main thread.
 * The limits are the launcher's ([MAX_TITLE], [MAX_TEXT], [MAX_COUNT]); longer text is cut here before it is sent.
 */
object MetroTile {
    /** Where the launcher listens. A debug build of the launcher installed with an id suffix listens at `<id><suffix>.tiles`: pass it as `authority`. */
    const val AUTHORITY = "com.metrowin.launcher.tiles"

    /** The contract this SDK speaks (the `version` the launcher answers with is at least this). */
    const val PROTOCOL = 1

    const val MAX_TITLE = 60
    const val MAX_TEXT = 160
    const val MAX_COUNT = 99_999

    private const val METHOD_PUBLISH = "publish"
    private const val METHOD_CLEAR = "clear"
    private const val METHOD_VERSION = "version"

    /** True when MetroWin Launcher (with the live tiles) is on the phone. */
    @JvmStatic
    @JvmOverloads
    fun isInstalled(context: Context, authority: String = AUTHORITY): Boolean =
        runCatching { context.packageManager.resolveContentProvider(authority, 0) != null }.getOrDefault(false)

    /** The contract version the installed launcher speaks, or null when it is not there. */
    @JvmStatic
    @JvmOverloads
    fun version(context: Context, authority: String = AUTHORITY): Int? =
        call(context, authority, METHOD_VERSION, null).let { (it as? Answer.Got)?.bundle?.getInt("version") }

    /** Shows [title], [text] and [count] (0 to 99 999; 0 shows no number) on the tile of this app. */
    @JvmStatic
    @JvmOverloads
    fun publish(context: Context, title: String?, text: String?, count: Int = 0, authority: String = AUTHORITY): MetroTileResult {
        val extras = Bundle().apply {
            putString("title", title.orEmpty().take(MAX_TITLE))
            putString("text", text.orEmpty().take(MAX_TEXT))
            putInt("count", count.coerceIn(0, MAX_COUNT))
        }
        return result(call(context, authority, METHOD_PUBLISH, extras))
    }

    /** Takes the content off the tile of this app (it goes back to what it shows without content). */
    @JvmStatic
    @JvmOverloads
    fun clear(context: Context, authority: String = AUTHORITY): MetroTileResult = result(call(context, authority, METHOD_CLEAR, null))

    private sealed interface Answer {
        data object Missing : Answer
        class Got(val bundle: Bundle) : Answer
    }

    private fun call(context: Context, authority: String, method: String, extras: Bundle?): Answer = try {
        // "Unknown URI" when the launcher is not installed, a SecurityException when the permission is missing from the manifest: both are "not there".
        context.contentResolver.call(Uri.parse("content://$authority"), method, null, extras)?.let(Answer::Got) ?: Answer.Missing
    } catch (e: IllegalArgumentException) {
        Answer.Missing
    } catch (e: SecurityException) {
        Answer.Missing
    } catch (e: RuntimeException) {
        Answer.Missing
    }

    private fun result(answer: Answer): MetroTileResult = when (answer) {
        Answer.Missing -> MetroTileResult.NotInstalled
        is Answer.Got ->
            if (answer.bundle.getBoolean("accepted")) MetroTileResult.Accepted
            else MetroTileResult.Refused(answer.bundle.getString("reason").orEmpty())
    }
}
