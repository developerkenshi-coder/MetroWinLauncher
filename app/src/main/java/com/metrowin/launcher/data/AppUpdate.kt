package com.metrowin.launcher.data

import androidx.compose.runtime.Immutable
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** The newest release published on GitHub: its version, and where the APK and its checksums are. */
@Immutable
data class UpdateRelease(val version: String, val apkUrl: String, val apkSize: Long, val sumsUrl: String?, val notes: String = "")

/** What the update section of the settings shows. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val version: String, val notes: String = "") : UpdateState
    data class Downloading(val percent: Int) : UpdateState

    /** Android has to be told once that this app may install apps: its screen is open, the next touch goes on. */
    data object NeedsPermission : UpdateState

    /** The check or the download did not work (no connection, GitHub did not answer, the file did not match its checksum). */
    data object Failed : UpdateState
}

/**
 * The updates of the app from the releases of its GitHub page. Only HTTPS, only the hosts of GitHub, the file must be the size
 * the release says (and no bigger than [MAX_APK_BYTES]) and must match the SHA256SUMS of the release; Android then checks that
 * the new APK is signed with the same key as the installed one.
 */
object AppUpdate {
    private const val RELEASES = "https://api.github.com/repos/developerkenshi-coder/MetroWinLauncher/releases/latest"
    private const val DOWNLOADS = "https://github.com/developerkenshi-coder/MetroWinLauncher/releases/download/"
    private val API_HOSTS = setOf("api.github.com")
    private val DOWNLOAD_HOSTS = setOf("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")
    const val MAX_APK_BYTES = 80L * 1024 * 1024
    const val CHECK_EVERY_MS = 12L * 60 * 60 * 1000

    /** `v26.3S` or `26.3S` as its numbers (26, 3); null when it has none. The letter at the end does not count. */
    fun numbers(version: String): List<Int>? =
        Regex("""^v?(\d+(?:\.\d+)*)""").find(version.trim())?.groupValues?.get(1)?.split('.')?.map { it.toIntOrNull() ?: return null }

    /** [remote] is a later version than [local]. A version that cannot be read is never newer. */
    fun isNewer(remote: String, local: String): Boolean {
        val a = numbers(remote) ?: return false
        val b = numbers(local) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** The release in the answer of GitHub (`/releases/latest`): not a draft, not a test version, with an APK that is on GitHub. */
    fun parseLatest(json: String): UpdateRelease? = runCatching {
        val root = JSONObject(json)
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
        val version = root.getString("tag_name").removePrefix("v")
        if (numbers(version) == null) return null
        val assets = root.getJSONArray("assets")
        var apk: UpdateRelease? = null
        var sums: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val url = asset.getString("browser_download_url")
            if (!url.startsWith(DOWNLOADS)) continue
            val name = asset.getString("name")
            if (name.endsWith(".apk", ignoreCase = true) && apk == null) apk = UpdateRelease(version, url, asset.optLong("size"), null)
            if (name == "SHA256SUMS") sums = url
        }
        apk?.copy(sumsUrl = sums, notes = notesText(root.optString("body")))
    }.getOrNull()

    const val MAX_NOTES = 4_000

    /**
     * The text a release is described with, as plain lines to read in the app (what is new, what changed): the marks of Markdown
     * (headings, bold, code, the bullets) are taken off or turned into a dot, and it is cut at [MAX_NOTES] characters.
     */
    fun notesText(markdown: String): String =
        markdown.replace("\r\n", "\n").lineSequence()
            .map { line ->
                line.trim().replace(Regex("^#{1,6}\\s*"), "").replace(Regex("^[-*+]\\s+"), "\u2022 ")
                    .replace("**", "").replace("__", "").replace("`", "")
                    .replace(Regex("\\[([^\\]]*)]\\([^)]*\\)"), "$1")
            }
            .joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim().take(MAX_NOTES)

    /** The checksum written for [fileName] in a SHA256SUMS file (`<hash>  <name>` on each line), in lower case; null when it has none. */
    fun checksumFor(sums: String, fileName: String): String? =
        sums.lineSequence()
            .map { it.trim().split(Regex("""\s+"""), limit = 2) }
            .firstOrNull { it.size == 2 && it[1].trimStart('*') == fileName && it[0].matches(Regex("[0-9a-fA-F]{64}")) }
            ?.get(0)?.lowercase()

    /** Blocking, call it off the main thread. The latest release, or null when GitHub cannot be reached or has nothing usable. */
    fun fetchLatest(): UpdateRelease? = SafeHttp.get(RELEASES, API_HOSTS, maxBytes = 512 * 1024)?.let(::parseLatest)

    /**
     * Blocking. Downloads the APK of [release] to [target], telling [onProgress] how far it is (0 to 100). False, and no file left, when
     * anything is wrong: the connection, the size, or the checksum.
     */
    fun download(release: UpdateRelease, target: File, onProgress: (Int) -> Unit): Boolean {
        val ok = runCatching {
            val expected = release.sumsUrl?.let { url ->
                val sums = SafeHttp.get(url, DOWNLOAD_HOSTS, maxBytes = 16 * 1024) ?: return@runCatching false
                checksumFor(sums, release.apkUrl.substringAfterLast('/')) ?: return@runCatching false
            }
            target.parentFile?.mkdirs()
            val digest = MessageDigest.getInstance("SHA-256")
            var url = URL(release.apkUrl)
            repeat(SafeHttp.MAX_REDIRECTS + 1) {
                require(url.protocol == "https" && url.host in DOWNLOAD_HOSTS)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = SafeHttp.TIMEOUT_MS
                    readTimeout = 20_000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "MetroWinLauncher")
                }
                try {
                    val code = connection.responseCode
                    if (code in setOf(301, 302, 303, 307, 308)) {
                        url = URL(url, connection.getHeaderField("Location") ?: return@runCatching false)
                        return@repeat
                    }
                    if (code != HttpURLConnection.HTTP_OK) return@runCatching false
                    val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
                    if (total > MAX_APK_BYTES || (release.apkSize > 0 && total != release.apkSize)) return@runCatching false
                    var done = 0L
                    connection.inputStream.use { input ->
                        target.outputStream().use { out ->
                            val buffer = ByteArray(32 * 1024)
                            var last = -1
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                done += n
                                if (done > MAX_APK_BYTES) return@runCatching false
                                digest.update(buffer, 0, n)
                                out.write(buffer, 0, n)
                                val percent = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else 0
                                if (percent != last) {
                                    last = percent
                                    onProgress(percent)
                                }
                            }
                        }
                    }
                    if (total > 0 && done != total) return@runCatching false
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    return@runCatching expected == null || expected == actual
                } finally {
                    connection.disconnect()
                }
            }
            false
        }.getOrDefault(false)
        if (!ok) target.delete()
        return ok
    }
}
