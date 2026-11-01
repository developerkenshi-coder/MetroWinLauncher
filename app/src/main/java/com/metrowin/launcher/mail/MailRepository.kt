package com.metrowin.launcher.mail

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.metrowin.launcher.data.SecureStore
import org.json.JSONArray
import org.json.JSONObject

/** The mail accounts of the launcher. The accounts and their passwords are kept encrypted (see [SecureStore]); the passwords apart from the accounts. */
class MailRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("metrowin_mail_secure", Context.MODE_PRIVATE)

    fun loadAccounts(): List<MailAccount> {
        val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        // What an older version saved was not encrypted and had no password: it fails here, and the account is added again.
        return runCatching {
            val json = JSONArray(SecureStore.decrypt(raw) ?: return emptyList())
            (0 until json.length()).map { i ->
                val obj = json.getJSONObject(i)
                MailAccount(
                    id = obj.getString("id"),
                    email = obj.getString("email"),
                    providerName = obj.optString("providerName", "Mail"),
                    imapHost = obj.getString("imapHost"),
                    imapPort = obj.optInt("imapPort", 993),
                    smtpHost = obj.getString("smtpHost"),
                    smtpPort = obj.optInt("smtpPort", 465),
                    requiresAppPassword = obj.optBoolean("requiresAppPassword", true),
                    oauth = obj.optString("oauth").takeIf { it.isNotEmpty() },
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Adds [account] with its [password]. */
    fun saveAccount(account: MailAccount, password: String) {
        write(loadAccounts().filter { it.id != account.id } + account)
        prefs.edit { putString(KEY_PASSWORD + account.id, SecureStore.encrypt(password)) }
    }

    fun removeAccount(accountId: String) {
        write(loadAccounts().filter { it.id != accountId })
        prefs.edit {
            remove(KEY_PASSWORD + accountId)
            prefs.all.keys.filter { it.startsWith(KEY_CACHE + accountId) }.forEach(::remove)
        }
    }

    fun password(accountId: String): String? = prefs.getString(KEY_PASSWORD + accountId, null)?.let(SecureStore::decrypt)

    /** The messages last seen in a folder, kept encrypted so that the list is there before the server answers. */
    fun cached(accountId: String, folder: String): List<MailMessage>? = runCatching {
        val json = JSONArray(SecureStore.decrypt(prefs.getString(KEY_CACHE + accountId + folder, null) ?: return null) ?: return null)
        (0 until json.length()).map { i ->
            json.getJSONObject(i).let {
                MailMessage(
                    it.getString("id"), accountId, it.getString("name"), it.getString("address"), it.getString("subject"), it.optString("snippet"),
                    it.getLong("time"), it.getBoolean("unread"), it.optBoolean("flagged"), "", it.optBoolean("att"),
                )
            }
        }
    }.getOrNull()

    fun cache(accountId: String, folder: String, messages: List<MailMessage>) {
        val json = JSONArray()
        messages.forEach {
            json.put(JSONObject().apply {
                put("id", it.id); put("name", it.senderName); put("address", it.senderAddress); put("subject", it.subject)
                put("snippet", it.snippet); put("time", it.timestamp); put("unread", it.isUnread); put("flagged", it.isFlagged); put("att", it.hasAttachments)
            })
        }
        prefs.edit { putString(KEY_CACHE + accountId + folder, SecureStore.encrypt(json.toString())) }
    }

    /** The way into the mailbox: the password, or, for an account that signed in on the page of Microsoft, a token made from the kept refresh token. */
    fun client(account: MailAccount): MailClient? = password(account.id)?.let { secret ->
        if (account.oauth == "microsoft") {
            MailClient(account) { MicrosoftOAuth.accessToken(secret) { fresh -> prefs.edit { putString(KEY_PASSWORD + account.id, SecureStore.encrypt(fresh)) } } }
        } else {
            MailClient(account) { secret }
        }
    }

    private fun write(accounts: List<MailAccount>) {
        val json = JSONArray()
        accounts.forEach { acc ->
            json.put(
                JSONObject().apply {
                    put("id", acc.id); put("email", acc.email); put("providerName", acc.providerName)
                    put("imapHost", acc.imapHost); put("imapPort", acc.imapPort)
                    put("smtpHost", acc.smtpHost); put("smtpPort", acc.smtpPort)
                    put("requiresAppPassword", acc.requiresAppPassword)
                    acc.oauth?.let { put("oauth", it) }
                },
            )
        }
        prefs.edit { putString(KEY_ACCOUNTS, SecureStore.encrypt(json.toString())) }
    }

    private companion object {
        const val KEY_ACCOUNTS = "accounts_data"
        const val KEY_PASSWORD = "password_"
        const val KEY_CACHE = "cache_"
    }
}
