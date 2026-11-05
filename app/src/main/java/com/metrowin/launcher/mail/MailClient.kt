package com.metrowin.launcher.mail

import java.util.Properties
import javax.mail.Address
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.FolderClosedException
import javax.mail.StoreClosedException
import javax.mail.search.FromStringTerm
import javax.mail.search.OrTerm
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

/** What the server said about a mail folder: its full [path] (what the server calls it) and the name to show. */
data class MailFolder(val path: String, val name: String)

/**
 * The mail of an account, read and sent over IMAP and SMTP, always over TLS. Every call blocks on the network: call it off the main thread.
 * The IMAP login is kept between calls; whoever makes the client calls [close] when done with it (a dead login is made again on the next call).
 */
class MailClient(private val account: MailAccount, private val secret: () -> String) {
    private fun properties(): Properties = Properties().apply {
        put("mail.imaps.host", account.imapHost)
        put("mail.imaps.port", account.imapPort.toString())
        put("mail.imaps.ssl.checkserveridentity", "true")
        // Reading a message to show a line of it must not mark it as read.
        put("mail.imaps.peek", "true")
        put("mail.imaps.connectiontimeout", TIMEOUT)
        put("mail.imaps.timeout", TIMEOUT)
        // Folders opened together (the list and the lines of text) share logins from a pool instead of each making a new one.
        put("mail.imaps.connectionpoolsize", "3")
        put("mail.imaps.connectionpooltimeout", "300000")
        put("mail.smtp.host", account.smtpHost)
        put("mail.smtp.port", account.smtpPort.toString())
        put("mail.smtp.auth", "true")
        put("mail.smtp.connectiontimeout", TIMEOUT)
        put("mail.smtp.timeout", TIMEOUT)
        put("mail.smtp.ssl.checkserveridentity", "true")
        // An account that signed in on the page of its provider has a token where the password would be.
        if (account.oauth != null) {
            put("mail.imaps.auth.mechanisms", "XOAUTH2")
            put("mail.imaps.auth.xoauth2.disable", "false")
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
    }

    private var kept: Store? = null

    @Synchronized
    private fun connected(): Store {
        kept?.takeIf { it.isConnected }?.let { return it }
        runCatching { kept?.close() }
        val store = Session.getInstance(properties()).getStore("imaps")
        store.connect(account.imapHost, account.imapPort, account.email, secret())
        kept = store
        return store
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

    private inline fun <T> folder(path: String, readWrite: Boolean, block: (UIDFolder) -> T): T = store { store ->
        val folder = store.getFolder(path)
        folder.open(if (readWrite) Folder.READ_WRITE else Folder.READ_ONLY)
        try {
            block(folder as UIDFolder)
        } finally {
            runCatching { folder.close(readWrite) }
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

    fun folders(): List<MailFolder> = store { store ->
        store.defaultFolder.list("*").filter { (it.type and Folder.HOLDS_MESSAGES) != 0 }.map { MailFolder(it.fullName, it.name) }
    }

    /** The latest [limit] messages of the folder, newest first; with a [query], the latest that the server finds it in. The text of each is left for [body]. */
    fun messages(path: String, limit: Int = LIMIT, query: String = ""): List<MailMessage> = folder(path, false) { uids ->
        val folder = uids as Folder
        val all = if (query.isBlank()) {
            val count = folder.messageCount
            if (count == 0) return@folder emptyList()
            folder.getMessages(maxOf(1, count - limit + 1), count)
        } else {
            folder.search(searchTerm(query.trim())).takeLast(limit).toTypedArray()
        }
        summaries(uids, all)
    }

    /** The envelope and flags of [found] fetched in one go, newest first. */
    private fun summaries(uids: UIDFolder, found: Array<Message>): List<MailMessage> {
        (uids as Folder).fetch(found, FetchProfile().apply { add(FetchProfile.Item.ENVELOPE); add(FetchProfile.Item.FLAGS); add(FetchProfile.Item.CONTENT_INFO) })
        return found.reversed().map { message ->
            val from = (message.from?.firstOrNull() as? InternetAddress)
            MailMessage(
                id = uids.getUID(message).toString(), accountId = account.id,
                senderName = from?.personal?.takeIf { it.isNotBlank() } ?: from?.address.orEmpty(), senderAddress = from?.address.orEmpty(),
                subject = message.subject.orEmpty(), snippet = "", timestamp = (message.receivedDate ?: message.sentDate)?.time ?: 0L,
                isUnread = !message.isSet(Flags.Flag.SEEN), isFlagged = message.isSet(Flags.Flag.FLAGGED), bodyText = "",
                hasAttachments = runCatching { message.isMimeType("multipart/mixed") }.getOrDefault(false),
            )
        }
    }

    /** How many messages of [path] are unread. */
    fun unread(path: String): Int = folder(path, false) { (it as Folder).unreadMessageCount }

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

    /** The first words of the messages [uids], one line each, for the list; a message that cannot be read has none. */
    fun snippets(path: String, uids: List<Long>): Map<String, String> = folder(path, false) { folder ->
        uids.mapNotNull { uid ->
            val text = runCatching {
                folder.getMessageByUID(uid)?.let { m -> MailParts.Collected(light = true).also { MailParts.walk(m, it) }.let { f -> f.plain.toString().ifBlank { MailParts.stripTags(f.html.toString()) } } }
            }.getOrNull()?.replace(Regex("\\s+"), " ")?.trim()
            if (text.isNullOrEmpty()) null else uid.toString() to text.take(SNIPPET)
        }.toMap()
    }

    /** What a message says, who it is for and what came with it; the message is marked as read. */
    fun body(path: String, uid: Long): MailBody = folder(path, true) { uids ->
        val message = uids.getMessageByUID(uid) ?: return@folder MailBody("", null, emptyList(), emptyList(), emptyList(), null)
        message.setFlag(Flags.Flag.SEEN, true)
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
        )
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

    fun setFlag(path: String, uid: Long, flag: Flags.Flag, on: Boolean) = folder(path, true) { uids ->
        uids.getMessageByUID(uid)?.setFlag(flag, on)
        Unit
    }

    /** Takes the message out of the folder: into the trash of the account when it has one, for good when it has none (or it is the trash). */
    fun delete(path: String, uid: Long) = store { store ->
        val folder = store.getFolder(path)
        folder.open(Folder.READ_WRITE)
        try {
            val message = (folder as UIDFolder).getMessageByUID(uid) ?: return@store
            val trash = store.defaultFolder.list("*").firstOrNull { it is com.sun.mail.imap.IMAPFolder && "\\Trash" in it.attributes }
            if (trash != null && trash.fullName != path) folder.copyMessages(arrayOf(message), trash)
            message.setFlag(Flags.Flag.DELETED, true)
        } finally {
            runCatching { folder.close(true) }
        }
    }

    /** Sends [body] to [to] (and [cc]) with the files [attachments] (name, kind, bytes) that came with it. */
    fun send(to: String, cc: String, subject: String, body: String, attachments: List<Triple<String, String, ByteArray>> = emptyList()) {
        val session = Session.getInstance(properties())
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(account.email))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(to) as Array<Address>)
            if (cc.isNotBlank()) setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc) as Array<Address>)
            setSubject(subject, "UTF-8")
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
                    store.defaultFolder.list("*").firstOrNull { it is com.sun.mail.imap.IMAPFolder && "\\Sent" in it.attributes }
                        ?.appendMessages(arrayOf(message.apply { setFlag(Flags.Flag.SEEN, true) }))
                }
            }
        }
    }

    companion object {
        /** The server looks for [query] in the subject and the sender. A non-ASCII word makes the library search on the phone instead. */
        fun searchTerm(query: String): SearchTerm = OrTerm(SubjectTerm(query), FromStringTerm(query))

        /** How many messages one page of the list holds. */
        const val LIMIT = 50
        private const val TIMEOUT = "15000"
        private const val SMTPS_PORT = 465
        private const val SNIPPET = 140
        private val KEEPS_SENT = setOf("imap.gmail.com", "outlook.office365.com", "imap-mail.outlook.com", "imap.mail.yahoo.com", "imap.mail.me.com")
    }
}
