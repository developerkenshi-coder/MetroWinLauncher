package com.metrowin.launcher.notifications

import android.content.Context
import com.metrowin.launcher.notes.KeystoreCrypt
import com.metrowin.launcher.notes.NoteCrypt
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** A notification as it is kept on disk: what it says and where it came from, never the intents (a PendingIntent cannot be saved). */
data class LedgerItem(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val longText: String?,
    val postTime: Long,
    val autoCancel: Boolean,
    val urgent: Boolean,
    val group: String?,
    val summary: Boolean,
)

/**
 * The register of the notifications the launcher has seen: each one is written the moment the service is told of it, whether or not a
 * screen of the launcher is on, and leaves only when the user takes it away in the launcher, when a newer post of the same conversation
 * takes its place, or after [MAX_AGE_MS]. The app that posted it taking it back, the process being killed or the listener rebinding are
 * not ways out: those were how notifications were lost. Encrypted at rest like the notes; the writes are coalesced on one thread.
 */
class NotificationLedger(
    private val file: File,
    private val crypt: NoteCrypt = KeystoreCrypt,
    private val writer: Executor = Executors.newSingleThreadExecutor { Thread(it, "notif-ledger").apply { isDaemon = true } },
) {
    constructor(context: Context) : this(File(context.filesDir, "notifications.dat"))

    private val items = LinkedHashMap<String, LedgerItem>()
    private val journal = ArrayDeque<String>()
    private val queued = AtomicBoolean(false)

    /** What the disk has, the old ones out. A file that cannot be read is put aside, not written over. */
    fun load(now: Long = System.currentTimeMillis()): List<LedgerItem> {
        if (file.exists()) {
            val plain = runCatching { crypt.decrypt(file.readText()) }.getOrNull()
            if (plain == null) file.renameTo(File(file.parentFile, "notifications.unreadable")) else decode(plain, now)
        }
        return all(now)
    }

    @Synchronized
    fun all(now: Long = System.currentTimeMillis()): List<LedgerItem> {
        items.values.removeAll { now - it.postTime > MAX_AGE_MS }
        return items.values.sortedByDescending { it.postTime }
    }

    /** Written now; the same key again is the next message of that conversation and takes the place of the old one. */
    fun record(item: LedgerItem, now: Long = System.currentTimeMillis()) {
        synchronized(this) {
            if (items[item.key] == item) return
            items.remove(item.key)
            items[item.key] = item
            items.values.removeAll { now - it.postTime > MAX_AGE_MS }
            while (items.size > MAX_ITEMS) items.remove(items.keys.first())
        }
        persist()
    }

    fun remove(keys: Collection<String>) {
        if (synchronized(this) { items.keys.removeAll(keys.toSet()) }) persist()
    }

    fun clear() {
        synchronized(this) { items.clear() }
        persist()
    }

    /** One line of the journal: where, what and when, never what a notification says. It goes to disk with the next write. */
    @Synchronized
    fun note(line: String) {
        journal.addLast(line)
        while (journal.size > MAX_JOURNAL) journal.removeFirst()
    }

    @Synchronized
    fun journalLines(): List<String> = journal.toList()

    private fun persist() {
        if (queued.compareAndSet(false, true)) writer.execute { queued.set(false); write() }
    }

    /** False when nothing was written (the Keystore cannot encrypt): the file is left as it was. */
    private fun write(): Boolean {
        val sealed = crypt.encrypt(encode())
        if (sealed.isEmpty()) return false
        return runCatching {
            val temporary = File(file.parentFile, file.name + ".tmp")
            temporary.writeText(sealed)
            if (!temporary.renameTo(file)) {
                file.delete()
                temporary.renameTo(file)
            }
            true
        }.getOrDefault(false)
    }

    @Synchronized
    private fun encode(): String = JSONObject()
        .put("version", 1)
        .put("journal", JSONArray(journal.toList()))
        .put("items", JSONArray().also { array -> items.values.forEach { array.put(encodeItem(it)) } })
        .toString()

    private fun encodeItem(i: LedgerItem) = JSONObject()
        .put("key", i.key).put("pkg", i.packageName).put("title", i.title).put("text", i.text).put("long", i.longText)
        .put("time", i.postTime).put("auto", i.autoCancel).put("urgent", i.urgent).put("group", i.group).put("summary", i.summary)

    @Synchronized
    private fun decode(json: String, now: Long) {
        runCatching {
            val root = JSONObject(json)
            root.optJSONArray("journal")?.let { for (n in 0 until it.length()) journal.addLast(it.getString(n)) }
            val array = root.getJSONArray("items")
            for (n in 0 until minOf(array.length(), MAX_ITEMS)) {
                runCatching {
                    val o = array.getJSONObject(n)
                    val item = LedgerItem(
                        key = o.getString("key"), packageName = o.getString("pkg"), title = o.optString("title"), text = o.optString("text"),
                        longText = if (o.isNull("long")) null else o.getString("long"), postTime = o.getLong("time"),
                        autoCancel = o.optBoolean("auto"), urgent = o.optBoolean("urgent"),
                        group = if (o.isNull("group")) null else o.getString("group"), summary = o.optBoolean("summary"),
                    )
                    if (now - item.postTime <= MAX_AGE_MS) items[item.key] = item
                }
            }
        }
    }

    companion object {
        /** A notification the user never took away is dropped after this long. */
        const val MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000
        const val MAX_ITEMS = 300
        const val MAX_JOURNAL = 120

        /** The longest text kept for one notification: a chat that posts a whole page cannot fill the file. */
        const val MAX_TEXT = 2_000
    }
}
