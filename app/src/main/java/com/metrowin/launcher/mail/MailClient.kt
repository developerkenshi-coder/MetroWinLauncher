package com.metrowin.launcher.mail

import java.util.Properties
import javax.mail.Address
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.Transport

/** What the server said about a mail folder: its full [path] (what the server calls it) and the name to show. */
data class MailFolder(val path: String, val name: String)

/**
 * The mail of an account, read and sent over IMAP and SMTP, always over TLS. Every call blocks on the network: call it off the main thread.
 * A connection is opened for the call and closed after it; nothing is kept between calls.
 *
 * shortcut: one connection per call is slow for long lists, keep one open per screen when it matters.
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

    private inline fun <T> store(block: (Store) -> T): T {
        val store = Session.getInstance(properties()).getStore("imaps")
        store.connect(account.imapHost, account.imapPort, account.email, secret())
        try {
            return block(store)
        } finally {
            runCatching { store.close() }
        }
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
    fun check() = store { }

    fun folders(): List<MailFolder> = store { store ->
        store.defaultFolder.list("*").filter { (it.type and Folder.HOLDS_MESSAGES) != 0 }.map { MailFolder(it.fullName, it.name) }
    }

    /** The latest [limit] messages of the folder, newest first. The text of each is left for [body]. */
    fun messages(path: String, limit: Int = LIMIT): List<MailMessage> = folder(path, false) { uids ->
        val folder = uids as Folder
        val count = folder.messageCount
        if (count == 0) return@folder emptyList()
        val found = folder.getMessages(maxOf(1, count - limit + 1), count)
        folder.fetch(found, FetchProfile().apply { add(FetchProfile.Item.ENVELOPE); add(FetchProfile.Item.FLAGS); add(FetchProfile.Item.CONTENT_INFO) })
        found.reversed().map { message ->
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
    }

    private companion object {
        const val TIMEOUT = "15000"
        const val SMTPS_PORT = 465
        const val LIMIT = 50
        const val SNIPPET = 140
    }
}
