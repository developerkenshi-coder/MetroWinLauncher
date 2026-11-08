package com.metrowin.launcher.mail

import android.net.Uri
import androidx.core.net.toUri
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The sign in of a Microsoft account (Outlook, Hotmail, Live) on the page of Microsoft, with the code flow and PKCE of an app that is
 * installed: the password is typed on their page, never here, and what the app keeps is a token that only reads and sends mail (and can
 * be taken back from the account). The app is registered with Microsoft as a public client, so there is no secret anywhere in it.
 *
 * shortcut: the flow that waits for the answer of the browser lives in memory; if Android ends the app meanwhile, the sign in is started again.
 */
object MicrosoftOAuth {
    /** The id of the registration of the app at Microsoft: it is public, as the id of any app that is installed. */
    const val CLIENT_ID = "433e3216-073d-4613-9c8c-63e645384b2a"
    const val REDIRECT = "com.metrowin.launcher://oauth"
    private const val AUTHORITY = "https://login.microsoftonline.com/consumers/oauth2/v2.0"
    private const val SCOPES = "https://outlook.office.com/IMAP.AccessAsUser.All https://outlook.office.com/SMTP.Send offline_access"
    private const val TIMEOUT_MS = 15_000

    /** What a sign in that waits for the browser needs when it comes back. */
    class Pending(val verifier: String, val state: String, val email: String)

    @Volatile
    var pending: Pending? = null

    /** The address the browser came back to, for the screen that waits for it; null until then. */
    val redirects = MutableStateFlow<Uri?>(null)

    class Tokens(val access: String, val refresh: String?, val expiresAtMs: Long)

    private fun random(bytes: Int): String =
        ByteArray(bytes).also { SecureRandom().nextBytes(it) }.let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP) }

    fun challengeOf(verifier: String): String =
        Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    /** The page of Microsoft where [email] signs in; the flow that waits for the answer is kept in [pending]. */
    fun authorizationUri(email: String): Uri {
        val verifier = random(48)
        val state = random(16)
        pending = Pending(verifier, state, email)
        return (AUTHORITY + "/authorize").toUri().buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challengeOf(verifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("login_hint", email)
            .appendQueryParameter("prompt", "select_account")
            .build()
    }

    /** The browser came back: the screen that waits for it takes it from [redirects]. */
    fun deliver(uri: Uri) {
        if (uri.scheme == "com.metrowin.launcher" && uri.host == "oauth") redirects.value = uri
    }

    /** Blocking. The tokens for the [code] the browser brought back, or an exception that says what Microsoft answered. */
    fun exchange(code: String, pending: Pending): Tokens =
        post(mapOf("grant_type" to "authorization_code", "code" to code, "redirect_uri" to REDIRECT, "code_verifier" to pending.verifier, "scope" to SCOPES))

    private var cached: Pair<String, Tokens>? = null

    /**
     * Blocking. A token to read and send mail with, made from the kept [refreshToken]; one that is still good is used again. Microsoft may
     * hand out a new refresh token with it: [onNewRefresh] keeps it.
     */
    fun accessToken(refreshToken: String, onNewRefresh: (String) -> Unit): String {
        cached?.takeIf { it.first == refreshToken && it.second.expiresAtMs > System.currentTimeMillis() + 60_000 }?.let { return it.second.access }
        val tokens = post(mapOf("grant_type" to "refresh_token", "refresh_token" to refreshToken, "scope" to SCOPES))
        tokens.refresh?.takeIf { it != refreshToken }?.let(onNewRefresh)
        cached = (tokens.refresh ?: refreshToken) to tokens
        return tokens.access
    }

    private fun post(form: Map<String, String>): Tokens {
        val body = (form + ("client_id" to CLIENT_ID)).entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        val connection = (URL("$AUTHORITY/token").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            val text = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = JSONObject(text.ifEmpty { "{}" })
            if (connection.responseCode !in 200..299) throw IOException(json.optString("error_description").ifEmpty { json.optString("error", "HTTP ${connection.responseCode}") }.lineSequence().first())
            return Tokens(
                access = json.getString("access_token"),
                refresh = json.optString("refresh_token").takeIf { it.isNotEmpty() },
                expiresAtMs = System.currentTimeMillis() + json.optLong("expires_in", 3_000L) * 1_000L,
            )
        } finally {
            connection.disconnect()
        }
    }
}
