package com.metrowin.launcher.data

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

/** An app of the saved list: its package (what the store knows it by) and the name it had. */
@Immutable
data class SavedApp(val packageName: String, val label: String)

/**
 * The list of the apps the user installed, kept in a file, so that a new phone can be set up by going through it: the apps that are not
 * there yet are listed, each one opening its page in the store. No app is installed by the launcher itself.
 */
object AppListBackup {
    private const val KIND = "metrowin-apps"
    private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

    fun encode(apps: List<SavedApp>, now: Long): String = JSONObject().apply {
        put("kind", KIND)
        put("version", 1)
        put("at", now)
        put("apps", JSONArray().apply { apps.forEach { put(JSONObject().put("p", it.packageName).put("l", it.label)) } })
    }.toString()

    /** Null when [json] is not a list this launcher wrote. A package that is not a package name is left out. */
    fun decode(json: String): List<SavedApp>? = runCatching {
        val root = JSONObject(json)
        if (root.optString("kind") != KIND) return null
        val array = root.getJSONArray("apps")
        (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            val pkg = o.optString("p")
            if (PACKAGE.matches(pkg)) SavedApp(pkg, o.optString("l").take(80).ifBlank { pkg }) else null
        }.distinctBy { it.packageName }
    }.getOrNull()

    /** What [saved] holds that [installed] (package names) does not, in the order of the list. */
    fun missing(saved: List<SavedApp>, installed: Set<String>): List<SavedApp> = saved.filter { it.packageName !in installed }

    /** The page of the app in the store. */
    fun storeUri(packageName: String) = "market://details?id=$packageName"

    fun webStoreUri(packageName: String) = "https://play.google.com/store/apps/details?id=$packageName"
}
