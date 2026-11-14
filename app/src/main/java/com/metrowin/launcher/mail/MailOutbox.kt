package com.metrowin.launcher.mail

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import androidx.core.content.edit
import com.metrowin.launcher.data.Prefs
import com.metrowin.launcher.data.SecureStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.concurrent.thread

/** What happened to a message handed to [MailOutbox.submit]. */
sealed interface SendOutcome {
    /** The server took it. */
    data object Sent : SendOutcome

    /** The network failed: the message is kept and sent again when there is a connection, even if Mail is closed. */
    data object Queued : SendOutcome

    /** The server refused it for a reason that waiting does not change (address, size, login): it is not kept, the writer must fix it. */
    class Refused(val error: Throwable) : SendOutcome
}

/**
 * The messages being sent. A message is written to the phone before the first try (the text encrypted, the files in the private folder of the
 * app), sent from a scope that outlives the screen, and kept until the server took it: leaving Mail or losing the network in the middle no longer
 * loses it. What the network failed is tried again by a job when there is a connection.
 */
object MailOutbox {
    private const val JOB_ID = 7304
    private const val MAX_ATTEMPTS = 12
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private class Entry(
        val id: String, val accountId: String, val to: String, val cc: String, val bcc: String, val subject: String, val text: String,
        val inReplyTo: String, val references: String, val files: List<Pair<String, String>>, val attempts: Int,
    )

    private fun prefs(context: Context) = Prefs.open(context.applicationContext, Prefs.MAIL_SECURE)

    private fun dir(context: Context, id: String) = File(context.applicationContext.noBackupFilesDir, "mail_outbox/$id")

    private fun key(id: String) = "outbox_$id"

    /** Writes the message down. Null when it could not be (the Keystore refused): the message is then only sent, not kept. */
    private fun save(context: Context, entry: Entry, bytes: List<ByteArray>?): Boolean {
        if (bytes != null) {
            val folder = dir(context, entry.id).apply { mkdirs() }
            bytes.forEachIndexed { i, data -> File(folder, "$i").writeBytes(data) }
        }
        val json = JSONObject().apply {
            put("account", entry.accountId); put("to", entry.to); put("cc", entry.cc); put("bcc", entry.bcc); put("subject", entry.subject); put("text", entry.text)
            put("inReplyTo", entry.inReplyTo); put("references", entry.references); put("attempts", entry.attempts)
            put("files", JSONArray().also { a -> entry.files.forEach { (n, m) -> a.put(JSONObject().put("name", n).put("mime", m)) } })
        }
        return SecureStore.putSecure(prefs(context), key(entry.id), json.toString())
    }

    private fun load(context: Context, id: String): Entry? = runCatching {
        val json = JSONObject(SecureStore.decrypt(prefs(context).getString(key(id), null) ?: return null) ?: return null)
        val files = json.getJSONArray("files")
        Entry(
            id, json.getString("account"), json.getString("to"), json.getString("cc"), json.getString("bcc"), json.getString("subject"), json.getString("text"),
            json.getString("inReplyTo"), json.getString("references"),
            (0 until files.length()).map { files.getJSONObject(it).let { f -> f.getString("name") to f.getString("mime") } }, json.getInt("attempts"),
        )
    }.getOrNull()

    private fun forget(context: Context, id: String) {
        prefs(context).edit { remove(key(id)) }
        dir(context, id).deleteRecursively()
    }

    private fun ids(context: Context): List<String> = prefs(context).all.keys.filter { it.startsWith("outbox_") }.map { it.removePrefix("outbox_") }

    /** How many messages wait to be sent. */
    fun waiting(context: Context): Int = ids(context).size

    /** Blocking: one try for [entry] with [files]. Gone from the phone when the server took it. */
    private fun attempt(context: Context, entry: Entry, files: List<Outgoing>): Result<Unit> {
        val repository = MailRepository(context.applicationContext)
        val account = repository.loadAccounts().firstOrNull { it.id == entry.accountId } ?: return Result.failure(IllegalStateException("no account"))
        val client = repository.client(account) ?: return Result.failure(IllegalStateException("no password"))
        return runCatching {
            try {
                client.send(entry.to, entry.cc, entry.subject, entry.text, files, entry.bcc, entry.inReplyTo, entry.references)
            } finally {
                client.close()
            }
        }.onSuccess { forget(context, entry.id) }
    }

    /** Whether waiting can help: a network that is down or a server that is busy yes, an address or a size or a password that is wrong no. */
    fun worthRetrying(e: Throwable): Boolean = MailErrors.kind(e).let { it == MailError.Network || it == MailError.Other }

    /**
     * Writes the message down, sends it from a scope that outlives the screen and says how it went. [Queued] leaves it written, with a job that
     * tries again when there is a network.
     */
    suspend fun submit(context: Context, accountId: String, draft: Draft, files: List<Outgoing>): SendOutcome {
        val app = context.applicationContext
        return scope.async {
            val entry = Entry(
                UUID.randomUUID().toString(), accountId, draft.to.trim(), draft.cc.trim(), draft.bcc.trim(), draft.subject, draft.text,
                draft.inReplyTo, draft.references, files.map { it.first to it.second }, 0,
            )
            val kept = runCatching { save(app, entry, files.map { it.third }) }.getOrDefault(false)
            val result = attempt(app, entry, files)
            val error = result.exceptionOrNull()
            when {
                error == null -> SendOutcome.Sent
                kept && worthRetrying(error) -> { schedule(app); SendOutcome.Queued }
                else -> { if (kept) forget(app, entry.id); SendOutcome.Refused(error) }
            }
        }.await()
    }

    /** Blocking, from the job: tries what waits again. True when nothing is left that a later try could send. */
    fun runAll(context: Context): Boolean = synchronized(lock) {
        var again = false
        ids(context).forEach { id ->
            val entry = load(context, id) ?: return@forEach forget(context, id)
            val files = entry.files.mapIndexed { i, (name, mime) -> Triple(name, mime, File(dir(context, id), "$i").readBytes()) }
            val error = attempt(context, entry, files).exceptionOrNull() ?: return@forEach
            if (worthRetrying(error) && entry.attempts + 1 < MAX_ATTEMPTS) {
                save(context, Entry(id, entry.accountId, entry.to, entry.cc, entry.bcc, entry.subject, entry.text, entry.inReplyTo, entry.references, entry.files, entry.attempts + 1), null)
                again = true
            } else {
                // Not to be sent: its text goes back to the draft the writer finds when opening a new message, and the user is told.
                MailDrafts.save(context, Draft(entry.to, entry.cc, entry.bcc, entry.subject, entry.text, entry.inReplyTo, entry.references))
                forget(context, id)
                MailSync.tellNotSent(context, MailErrors.text(context, error))
            }
        }
        !again
    }

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        runCatching {
            scheduler.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, MailOutboxJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setBackoffCriteria(60_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build(),
            )
        }
    }
}

/** Runs [MailOutbox.runAll] when Android calls it, which is when there is a network. */
class MailOutboxJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        thread(name = "mail-outbox") { jobFinished(params, !MailOutbox.runAll(applicationContext)) }
        return true
    }

    override fun onStopJob(params: JobParameters) = true
}

/** The message being written when Mail was left, kept encrypted so that the next new message starts where this one stopped. */
object MailDrafts {
    private const val KEY = "draft_new"

    private fun prefs(context: Context) = Prefs.open(context.applicationContext, Prefs.MAIL_SECURE)

    fun save(context: Context, draft: Draft) {
        if (draft.isBlank()) return clear(context)
        val json = JSONObject().put("to", draft.to).put("cc", draft.cc).put("bcc", draft.bcc).put("subject", draft.subject).put("text", draft.text)
            .put("inReplyTo", draft.inReplyTo).put("references", draft.references)
        SecureStore.putSecure(prefs(context), KEY, json.toString())
    }

    fun load(context: Context): Draft? = runCatching {
        val json = JSONObject(SecureStore.decrypt(prefs(context).getString(KEY, null) ?: return null) ?: return null)
        Draft(json.getString("to"), json.getString("cc"), json.getString("bcc"), json.getString("subject"), json.getString("text"), json.optString("inReplyTo"), json.optString("references"))
    }.getOrNull()

    fun clear(context: Context) = prefs(context).edit { remove(KEY) }
}
