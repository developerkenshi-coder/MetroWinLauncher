package com.metrowin.launcher.mail

import android.content.Context
import android.os.Handler
import android.os.Looper

/** What the list of the Mail screen asks of a mailbox: [MailClient] on the network, a fake in the tests. Every call blocks: off the main thread. */
interface MailSource {
    /** One page of [limit] messages of the folder, newest first, after the [skip] newest ones; with a [query], the latest that the server finds it in. */
    fun messages(path: String, limit: Int = MailClient.LIMIT, query: String = "", skip: Int = 0): List<MailMessage>

    /** The first words of the messages [uids], by UID. */
    fun snippets(path: String, uids: List<Long>): Map<String, String>

    /** How many messages of [path] are unread. */
    fun unread(path: String): Int
}

/**
 * How the list of the inbox is brought in, in the order that shows something first: what was kept from the last time at once (no network), the
 * headers of the first page in one go, the lines of text only for the messages that have none yet, then the rest of the pages on demand.
 */
object MailListing {
    /** How many of the newest messages without one get a line of their text: the rows that fit on a screen, not the whole page. */
    const val SNIPPETS = 10

    /** The headers of the first page; the lines the screen already has for the same messages (from the kept list) stay on them. */
    fun firstPage(source: MailSource, path: String, limit: Int, query: String, shown: List<MailMessage>?): List<MailMessage> {
        val known = shown.orEmpty().associate { it.id to it.snippet }
        return source.messages(path, limit, query).map { it.copy(snippet = known[it.id].orEmpty()) }
    }

    /** The UIDs that still need a line: the newest ones that have none, not more than [SNIPPETS]. */
    fun needLines(list: List<MailMessage>): List<Long> = list.asSequence().filter { it.snippet.isEmpty() }.take(SNIPPETS).map { it.id.toLong() }.toList()

    /** The lines for what has none. Nothing is asked of the server when there is nothing missing. */
    fun lines(source: MailSource, path: String, list: List<MailMessage>): Map<String, String> {
        val missing = needLines(list)
        return if (missing.isEmpty()) emptyMap() else source.snippets(path, missing)
    }

    fun withLines(list: List<MailMessage>, lines: Map<String, String>): List<MailMessage> = list.map { m -> lines[m.id]?.let { m.copy(snippet = it) } ?: m }

    /** The next page, after the ones [shown]: only the older headers come from the server, and none is listed twice if the mailbox moved meanwhile. */
    fun morePage(source: MailSource, path: String, shown: List<MailMessage>): List<MailMessage> {
        val older = source.messages(path, MailClient.LIMIT, "", skip = shown.size)
        val have = shown.mapTo(HashSet()) { it.id }
        return shown + older.filter { it.id !in have }
    }
}

/**
 * The logins to the mailboxes, kept for a while after the Mail screen is left: opening the app again (or the next folder) does not make the
 * server a new TLS connection and login, which is most of what the wait for the list was. [warm] makes the login while the screen is being
 * drawn. The connection that waits for new mail ([MailIdleService]) cannot be shared: while it waits, the server takes no other command on it.
 */
object MailClients {
    private const val KEEP_MS = 5 * 60_000L
    private val kept = HashMap<String, MailClient>()
    private val handler = Handler(Looper.getMainLooper())
    private val closeAll = Runnable { drop() }

    /** The client of [account], the one kept if there is one. */
    @Synchronized
    fun of(account: MailAccount, repository: MailRepository): MailClient? {
        handler.removeCallbacks(closeAll)
        kept[account.id]?.let { return it }
        return repository.client(account)?.also { kept[account.id] = it }
    }

    /** The screen was left: the logins are let go in five minutes, unless the screen comes back. */
    fun release() {
        handler.removeCallbacks(closeAll)
        handler.postDelayed(closeAll, KEEP_MS)
    }

    /** The login of one account (it was removed), or of all when [id] is null. */
    @Synchronized
    fun drop(id: String? = null) {
        val gone = if (id == null) kept.values.toList().also { kept.clear() } else listOfNotNull(kept.remove(id))
        gone.forEach { it.close() }
    }

    /** Blocking. Reads the accounts and makes the login of the first one, so that the screen finds both ready. Nothing is shown of a failure here: the screen asks again and says it. */
    fun warm(context: Context) {
        runCatching {
            val repository = MailRepository(context.applicationContext)
            repository.loadAccounts().firstOrNull()?.let { of(it, repository)?.connect() }
        }
    }
}
