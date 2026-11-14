package com.metrowin.launcher.mail

import java.util.Properties
import javax.mail.Address
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.FolderClosedException
import javax.mail.StoreClosedException
import javax.mail.search.AndTerm
import javax.mail.search.BodyTerm
import javax.mail.search.FlagTerm
import javax.mail.search.FromStringTerm
import javax.mail.search.OrTerm
import javax.mail.search.RecipientStringTerm
import javax.mail.search.SearchTerm
import javax.mail.search.SubjectTerm
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.Transport

/** What a look at the inbox found: how many messages are unread, the highest UID there, and the unread ones newer than the last look. */
data class Arrivals(val unread: Int, val lastUid: Long, val fresh: List<MailMessage>)

/** What the server said about a mail folder: its full [path] (what the server calls it), the name to show, what it is for ([role]: "\\Trash", "\\Junk"... or null) and how many messages in it are unread (-1: not known). */
data class MailFolder(val path: String, val name: String, val role: String? = null, val unread: Int = -1)

/** Which messages of a folder a list wants. */
enum class MailFilter { All, Unseen, Flagged }

/** Taking a message out of a folder that has no trash would delete it for good: the caller must ask the user and say so ([MailClient.delete] with `permanent = true`). */
class NoTrashException : Exception("no trash")

/**
 * The mail of an account, read and sent over IMAP and SMTP, always over TLS. Every call blocks on the network: call it off the main thread.
 * The IMAP login is kept between calls; whoever makes the client calls [close] when done with it (a dead login is made again on the next call).
 */
class MailClient(
    private val account: MailAccount,
    /** Properties that win over the ones made here: the tests use them to talk to a server without TLS. */
    private val overrides: Properties = Properties(),
    private val secret: () -> String,
) : MailSource {
    /** 993 speaks TLS from the first byte ("imaps"); any other port (143) starts in the clear and must switch to TLS before the password goes. */
    private val protocol = if (account.imapPort == IMAPS_PORT) "imaps" else "imap"

    private fun properties(readTimeout: String = TIMEOUT): Properties = Properties().apply {
        val p = "mail.$protocol"
        put("$p.host", account.imapHost)
        put("$p.port", account.imapPort.toString())
        put("$p.ssl.checkserveridentity", "true")
        if (protocol == "imap") {
            put("$p.starttls.enable", "true")
            put("$p.starttls.required", "true")
        }
        // Reading a message to show a line of it must not mark it as read.
        put("$p.peek", "true")
        put("$p.connectiontimeout", TIMEOUT)
        put("$p.timeout", readTimeout)
        // Folders opened together (the list and the lines of text) share logins from a pool instead of each making a new one.
        put("$p.connectionpoolsize", "3")
        put("$p.connectionpooltimeout", "300000")
        put("mail.smtp.host", account.smtpHost)
        put("mail.smtp.port", account.smtpPort.toString())
        put("mail.smtp.auth", "true")
        put("mail.smtp.connectiontimeout", TIMEOUT)
        put("mail.smtp.timeout", TIMEOUT)
        put("mail.smtp.ssl.checkserveridentity", "true")
        // An account that signed in on the page of its provider has a token where the password would be.
        if (account.oauth != null) {
            put("$p.auth.mechanisms", "XOAUTH2")
            put("$p.auth.xoauth2.disable", "false")
            put("mail.smtp.auth.mechanisms", "XOAUTH2")
            put("mail.smtp.auth.xoauth2.disable", "false")
        }
        // 465 speaks TLS from the first byte; the other ports start in the clear and must switch to TLS before the password goes.
        if (account.smtpPort == SMTPS_PORT) {
            put("mail.smtp.ssl.enable", "true")
        } else {
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.starttls.required", "true")
        }
        putAll(overrides)
    }

    private var kept: Store? = null

    /** Makes the login now (TLS and password), so the first command finds it ready. Throws when the server cannot be reached. */
    fun connect() {
        connected()
    }

    @Synchronized
    private fun connected(): Store {
        kept?.takeIf { it.isConnected }?.let { return it }
        runCatching { kept?.close() }
        val store = Session.getInstance(properties()).getStore(protocol)
        store.connect(account.imapHost, account.imapPort, account.email, secret())
        kept = store
        return store
    }

    @Volatile private var idleStore: Store? = null

    /**
     * Holds [path] open in IMAP IDLE on a login of its own, and calls [onArrival] when the server says the folder changed ([onConnected] first,
     * once the folder is open). The IDLE is renewed every [renewMs] (servers close it at 30 minutes). Returns false at once when the server
     * has no IDLE; else blocks until [stopIdle] or throws when the connection drops.
     */
    fun idle(path: String, renewMs: Long, onConnected: () -> Unit, onArrival: () -> Unit): Boolean {
        // The read timeout is longer than the renewal: a connection that died without a word is noticed by the next one.
        val store = Session.getInstance(properties((renewMs + 5 * 60_000L).toString())).getStore(protocol)
        idleStore = store
        val renew = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        try {
            store.connect(account.imapHost, account.imapPort, account.email, secret())
            if (!(store as com.sun.mail.imap.IMAPStore).hasCapability("IDLE")) return false
            val folder = store.getFolder(path) as com.sun.mail.imap.IMAPFolder
            folder.open(Folder.READ_ONLY)
            folder.addMessageCountListener(object : javax.mail.event.MessageCountAdapter() {
                override fun messagesAdded(e: javax.mail.event.MessageCountEvent) = onArrival()
            })
            // A NOOP from another thread ends the IDLE that is running; the loop below starts the next one.
            renew.scheduleWithFixedDelay({
                runCatching { folder.doCommand { it.simpleCommand("NOOP", null); null } }
            }, renewMs, renewMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            onConnected()
            while (store.isConnected) {
                val began = System.currentTimeMillis()
                folder.idle()
                // A server that answers IDLE at once, again and again, must not make a busy loop.
                if (System.currentTimeMillis() - began < 1000) Thread.sleep(1000)
            }
            return true
        } finally {
            renew.shutdownNow()
            runCatching { store.close() }
            idleStore = null
        }
    }

    /** Ends [idle] (it throws or returns on its own thread). */
    fun stopIdle() {
        runCatching { idleStore?.close() }
    }

    /** Lets go of the kept login. */
    @Synchronized
    fun close() {
        runCatching { kept?.close() }
        kept = null
    }

    private inline fun <T> store(block: (Store) -> T): T = try {
        block(connected())
    } catch (e: StoreClosedException) {
        close()
        block(connected())
    } catch (e: FolderClosedException) {
        close()
        block(connected())
    }

    /**
     * Opens [path] and runs [block] on it, then closes it WITHOUT expunge, always: closing with `true` would remove from the server every message
     * marked \Deleted in the folder, also the ones another mail program marked and meant to keep until it asks itself. Flags set meanwhile are
     * already on the server (each is its own command), so nothing needs the expunge.
     */
    private inline fun <T> folder(path: String, readWrite: Boolean, block: (UIDFolder) -> T): T = store { store ->
        val folder = store.getFolder(path)
        folder.open(if (readWrite) Folder.READ_WRITE else Folder.READ_ONLY)
        try {
            block(folder as UIDFolder)
        } finally {
            runCatching { folder.close(false) }
        }
    }

    /** Opens a connection with the password, to know it is right. Throws when it is not. */
    fun check() {
        try {
            connected()
        } finally {
            close()
        }
    }

    /** The folders that hold messages, with what each is for and how many messages in it are unread (one STATUS each, the folder is not opened). */
    fun folders(): List<MailFolder> = store { store ->
        store.defaultFolder.list("*").filter { (it.type and Folder.HOLDS_MESSAGES) != 0 }.map { f ->
            val role = (f as? com.sun.mail.imap.IMAPFolder)?.attributes?.firstOrNull { it in ROLES } ?: guessRole(f.fullName, f.name)
            MailFolder(f.fullName, f.name, role, runCatching { f.unreadMessageCount }.getOrDefault(-1))
        }
    }

    /**
     * [limit] messages of the folder, newest first, after the [skip] newest; with a [query] or a [filter], the ones that the server finds.
     * A message marked \Deleted (by another program that has not expunged yet) is not listed. The text of each is left for [snippets] and [body].
     */
    override fun messages(path: String, limit: Int, query: String, skip: Int, filter: MailFilter): List<MailMessage> = folder(path, false) { uids ->
        val folder = uids as Folder
        val all = if (query.isBlank() && filter == MailFilter.All) {
            val end = folder.messageCount - skip
            if (end <= 0) return@folder emptyList()
            folder.getMessages(maxOf(1, end - limit + 1), end)
        } else {
            val found = folder.search(listTerm(query, filter))
            val end = found.size - skip
            if (end <= 0) return@folder emptyList()
            found.copyOfRange(maxOf(0, end - limit), end)
        }
        summaries(uids, all)
    }

    /** The envelope and flags of [found] fetched in one go, newest first. */
    private fun summaries(uids: UIDFolder, found: Array<Message>): List<MailMessage> {
        (uids as Folder).fetch(
            found,
            FetchProfile().apply {
                add(FetchProfile.Item.ENVELOPE); add(FetchProfile.Item.FLAGS); add(FetchProfile.Item.CONTENT_INFO)
                // What puts the messages of a conversation together.
                add("Message-ID"); add("In-Reply-To"); add("References")
            },
        )
        return found.reversed().filterNot { it.isSet(Flags.Flag.DELETED) }.map { message ->
            val from = (message.from?.firstOrNull() as? InternetAddress)
            MailMessage(
                id = uids.getUID(message).toString(), accountId = account.id,
                senderName = from?.personal?.takeIf { it.isNotBlank() } ?: from?.address.orEmpty(), senderAddress = from?.address.orEmpty(),
                subject = message.subject.orEmpty(), snippet = "", timestamp = (message.receivedDate ?: message.sentDate)?.time ?: 0L,
                isUnread = !message.isSet(Flags.Flag.SEEN), isFlagged = message.isSet(Flags.Flag.FLAGGED), bodyText = "",
                hasAttachments = runCatching { MailParts.hasFiles(message) }.getOrDefault(false),
                messageId = message.getHeader("Message-ID")?.firstOrNull().orEmpty().trim(),
                refs = (message.getHeader("In-Reply-To").orEmpty().toList() + message.getHeader("References").orEmpty().toList()).joinToString(" ").trim(),
            )
        }
    }

    /** How many messages of [path] are unread: one question to the server (STATUS), the folder is not opened for it. */
    override fun unread(path: String): Int = store { it.getFolder(path).unreadMessageCount }

    /**
     * What the background check needs from [path]: the unread count, the highest UID in it, and the unread messages with a UID above [after]
     * (newest first; none when [after] is null, which is the first look at the folder).
     */
    fun arrivals(path: String, after: Long?): Arrivals = folder(path, false) { uids ->
        val folder = uids as Folder
        val count = folder.messageCount
        val last = if (count == 0) 0L else uids.getUID(folder.getMessage(count))
        // A range that ends in "*" always brings the last message, even when its UID is not above the start: those are filtered out here.
        val fresh = if (after == null || last <= after) {
            emptyList()
        } else {
            summaries(uids, uids.getMessagesByUID(after + 1, UIDFolder.LASTUID)).filter { it.id.toLong() > after && it.isUnread }.take(LIMIT)
        }
        Arrivals(folder.unreadMessageCount, last, fresh)
    }

    /**
     * The first words of the messages [uids], one line each, for the list; a message that cannot be read has none. The messages are found
     * and their structure fetched in one go each, then only the start of the first text part of each is read (see [MailParts.Collected.light]).
     */
    override fun snippets(path: String, uids: List<Long>): Map<String, String> = folder(path, false) { folder ->
        val found = folder.getMessagesByUID(uids.toLongArray()).filterNotNull().toTypedArray()
        runCatching { (folder as Folder).fetch(found, FetchProfile().apply { add(FetchProfile.Item.CONTENT_INFO) }) }
        found.mapNotNull { m ->
            val text = runCatching {
                MailParts.Collected(light = true).also { MailParts.walk(m, it) }.let { f -> f.plain.toString().ifBlank { MailParts.stripTags(f.html.toString()) } }
            }.getOrNull()?.replace(Regex("\\s+"), " ")?.trim()
            if (text.isNullOrEmpty()) null else folder.getUID(m).toString() to text.take(SNIPPET)
        }.toMap()
    }

    /**
     * What a message says, who it is for and what came with it. The folder is opened read only, so reading cannot change anything else in
     * it; with [markSeen] the message is then marked as read by a command of its own, whose failure does not lose the reading.
     */
    fun body(path: String, uid: Long, markSeen: Boolean = true): MailBody {
        val opened = folder(path, false) { uids ->
            val message = uids.getMessageByUID(uid) ?: return@folder null
            val found = MailParts.Collected()
            MailParts.walk(message, found)
            fun people(type: Message.RecipientType) = message.getRecipients(type).orEmpty().mapNotNull { it as? InternetAddress }
                .map { MailAddress(it.personal.orEmpty(), it.address.orEmpty()) }
            val html = found.html.toString().takeIf { it.isNotBlank() }?.let { page ->
                // The pictures that came inside the message are put where the page asks for them (cid:), as they are.
                found.inline.entries.fold(page) { acc, (cid, uri) -> acc.replace("cid:$cid", uri, ignoreCase = true) }
            }
            MailBody(
                text = found.plain.toString().ifBlank { html?.let(MailParts::stripTags).orEmpty() }.trim(),
                html = html,
                attachments = found.attachments,
                to = people(Message.RecipientType.TO),
                cc = people(Message.RecipientType.CC),
                replyTo = (message.replyTo?.firstOrNull() as? InternetAddress)?.address,
                messageId = message.getHeader("Message-ID")?.firstOrNull().orEmpty().trim(),
                references = message.getHeader("References").orEmpty().joinToString(" ").trim(),
                bcc = people(Message.RecipientType.BCC),
            )
        } ?: return MailBody("", null, emptyList(), emptyList(), emptyList(), null)
        if (markSeen) runCatching { setFlag(path, uid, Flags.Flag.SEEN, true) }
        return opened
    }

    /** One file of a message, as the bytes it holds: its name, its kind and the bytes. */
    fun attachment(path: String, uid: Long, index: Int): Triple<String, String, ByteArray> = folder(path, false) { uids ->
        val message = uids.getMessageByUID(uid) ?: error("gone")
        val found = MailParts.Collected(wantedIndex = index)
        MailParts.walk(message, found)
        val part = found.wanted ?: error("gone")
        val info = found.attachments.first { it.index == index }
        Triple(info.name, info.mime, part.inputStream.use { it.readBytes() })
    }

    /** Sets or clears [flag] on the message. Throws when the message is not there any more or the server says no: nothing is changed then. */
    fun setFlag(path: String, uid: Long, flag: Flags.Flag, on: Boolean) = folder(path, true) { uids ->
        (uids.getMessageByUID(uid) ?: error("gone")).setFlag(flag, on)
    }

    /** The folder of the account for [role] ("\\Trash", "\\Archive", "\\Junk", "\\Sent"): the one the server marks, else one of the usual names. */
    private fun roleFolder(store: Store, role: String): Folder? {
        if (role == "\\All") return store.defaultFolder.list("*").firstOrNull { it is com.sun.mail.imap.IMAPFolder && role in it.attributes }
        val all = store.defaultFolder.list("*")
        return all.firstOrNull { it is com.sun.mail.imap.IMAPFolder && role in it.attributes }
            ?: all.firstOrNull { f -> NAMES[role].orEmpty().any { it.equals(f.fullName, true) || it.equals(f.name, true) } }
    }

    /**
     * Takes [message] out of [folder] and nothing else: it is marked \Deleted and only it is expunged (UID EXPUNGE). A server without that
     * command gets a plain EXPUNGE only when no other message in the folder is marked \Deleted, so another program's marks are never swept.
     * Otherwise the message stays marked: it is not listed any more, and nothing is lost.
     */
    private fun remove(store: Store, folder: Folder, message: Message) {
        message.setFlag(Flags.Flag.DELETED, true)
        val imapStore = store as com.sun.mail.imap.IMAPStore
        if (imapStore.hasCapability("UIDPLUS")) {
            (folder as com.sun.mail.imap.IMAPFolder).expunge(arrayOf(message))
        } else if (folder.search(FlagTerm(Flags(Flags.Flag.DELETED), true)).size == 1) {
            folder.expunge()
        }
    }

    /**
     * Takes the message out of the folder, into the trash of the account. A copy that fails stops everything: the message stays where it is.
     * With no trash (or in the trash itself) the message would be gone for good: [NoTrashException] is thrown unless [permanent] says the user knows.
     */
    fun delete(path: String, uid: Long, permanent: Boolean = false) = store { store ->
        val folder = store.getFolder(path)
        folder.open(Folder.READ_WRITE)
        try {
            val message = (folder as UIDFolder).getMessageByUID(uid) ?: return@store
            val trash = roleFolder(store, "\\Trash")?.takeIf { it.fullName != path }
            if (trash == null && !permanent) throw NoTrashException()
            trash?.let { folder.copyMessages(arrayOf(message), it) }
            remove(store, folder, message)
        } finally {
            runCatching { folder.close(false) }
        }
    }

    /** Whether [delete] of a message of [path] goes to a trash (false: it would be for good and the user must be asked). */
    fun hasTrash(path: String): Boolean = store { roleFolder(it, "\\Trash")?.fullName.let { t -> t != null && t != path } }

    /** Moves the message to [target] (a folder path), or to the folder for [role] when [target] is null. Throws when there is no such folder or the copy fails: the message stays. */
    fun move(path: String, uid: Long, target: String?, role: String? = null) = store { store ->
        val to = if (target != null) store.getFolder(target) else roleFolder(store, role ?: error("no target"))
        // Gmail has no archive folder: the message already lives in "All Mail", so archiving is only taking it out of the inbox.
        val allMail = if (to == null && role == "\\Archive") roleFolder(store, "\\All") else null
        if ((to == null && allMail == null) || to?.fullName == path) error("no folder")
        val folder = store.getFolder(path)
        folder.open(Folder.READ_WRITE)
        try {
            val message = (folder as UIDFolder).getMessageByUID(uid) ?: error("gone")
            to?.let { folder.copyMessages(arrayOf(message), it) }
            remove(store, folder, message)
        } finally {
            runCatching { folder.close(false) }
        }
    }

    /**
     * Sends [body] to [to] (and [cc], [bcc]) with the files [attachments] (name, kind, bytes) that came with it. [inReplyTo] and [references]
     * put an answer in the conversation of the message it answers.
     */
    fun send(
        to: String, cc: String, subject: String, body: String, attachments: List<Triple<String, String, ByteArray>> = emptyList(),
        bcc: String = "", inReplyTo: String = "", references: String = "",
    ) {
        val session = Session.getInstance(properties())
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(account.email))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(to) as Array<Address>)
            if (cc.isNotBlank()) setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc) as Array<Address>)
            if (bcc.isNotBlank()) setRecipients(Message.RecipientType.BCC, InternetAddress.parse(bcc) as Array<Address>)
            setSubject(subject, "UTF-8")
            if (inReplyTo.isNotBlank()) {
                setHeader("In-Reply-To", inReplyTo)
                setHeader("References", (references.trim() + " " + inReplyTo).trim())
            }
            if (attachments.isEmpty()) {
                setText(body, "UTF-8")
            } else {
                val parts = javax.mail.internet.MimeMultipart()
                parts.addBodyPart(javax.mail.internet.MimeBodyPart().apply { setText(body, "UTF-8") })
                attachments.forEach { (name, mime, bytes) ->
                    parts.addBodyPart(
                        javax.mail.internet.MimeBodyPart().apply {
                            dataHandler = javax.activation.DataHandler(javax.mail.util.ByteArrayDataSource(bytes, mime))
                            fileName = javax.mail.internet.MimeUtility.encodeText(name)
                        },
                    )
                }
                setContent(parts)
            }
        }
        Transport.send(message, account.email, secret())
        // Servers that keep no copy of what goes out over SMTP get one in Sent; the big providers already do, and would show it twice.
        if (account.imapHost !in KEEPS_SENT) {
            runCatching {
                store { store ->
                    roleFolder(store, "\\Sent")?.appendMessages(arrayOf(message.apply { setFlag(Flags.Flag.SEEN, true) }))
                }
            }
        }
    }

    companion object {
        /** What a folder is for, from its usual names, when the server does not say ([MailFolder.role]). */
        fun guessRole(fullName: String, name: String): String? =
            NAMES.entries.firstOrNull { (_, names) -> names.any { it.equals(fullName, true) || it.equals(name, true) } }?.key

        /**
         * The server looks for [query] in the subject, the sender, the recipients and the text. A word with letters outside ASCII is searched
         * with the charset UTF-8 when the server takes it; the library searches on the phone when it does not.
         */
        fun searchTerm(query: String): SearchTerm = OrTerm(
            arrayOf(
                SubjectTerm(query), FromStringTerm(query), BodyTerm(query),
                RecipientStringTerm(Message.RecipientType.TO, query), RecipientStringTerm(Message.RecipientType.CC, query),
            ),
        )

        /** What a list asks the server: [query] (if any), narrowed to the unread or the flagged, and never what another program marked \Deleted. */
        fun listTerm(query: String, filter: MailFilter): SearchTerm {
            val terms = ArrayList<SearchTerm>()
            if (query.isNotBlank()) terms += searchTerm(query.trim())
            when (filter) {
                MailFilter.Unseen -> terms += FlagTerm(Flags(Flags.Flag.SEEN), false)
                MailFilter.Flagged -> terms += FlagTerm(Flags(Flags.Flag.FLAGGED), true)
                MailFilter.All -> {}
            }
            terms += FlagTerm(Flags(Flags.Flag.DELETED), false)
            return AndTerm(terms.toTypedArray())
        }

        /** How many messages one page of the list holds. */
        const val LIMIT = 50
        private const val TIMEOUT = "15000"
        private const val SMTPS_PORT = 465
        private const val IMAPS_PORT = 993
        private const val SNIPPET = 140
        private val KEEPS_SENT = setOf("imap.gmail.com", "outlook.office365.com", "imap-mail.outlook.com", "imap.mail.yahoo.com", "imap.mail.me.com")
        private val ROLES = setOf("\\Trash", "\\Junk", "\\Archive", "\\Sent", "\\Drafts", "\\All")
        private val NAMES = mapOf(
            "\\Trash" to listOf("Trash", "Deleted", "Deleted Items", "Deleted Messages", "INBOX.Trash", "Bin", "Cestino", "Posta eliminata"),
            "\\Junk" to listOf("Junk", "Spam", "Junk E-mail", "INBOX.Spam", "Bulk Mail", "Posta indesiderata"),
            "\\Archive" to listOf("Archive", "Archivio", "Archived", "INBOX.Archive"),
            "\\Sent" to listOf("Sent", "Sent Items", "Sent Messages", "INBOX.Sent", "Posta inviata", "Inviati"),
        )
    }
}
