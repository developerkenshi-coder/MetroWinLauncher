package com.metrowin.launcher.mail

import androidx.compose.runtime.Immutable

/** Configured email account in the launcher. */
@Immutable
data class MailAccount(
    val id: String,
    val email: String,
    val providerName: String,
    val imapHost: String,
    val imapPort: Int = 993,
    val smtpHost: String,
    val smtpPort: Int = 465,
    val requiresAppPassword: Boolean = true,
    /** "microsoft" when the account signs in on the page of the provider (see [MicrosoftOAuth]) and keeps a token instead of a password. */
    val oauth: String? = null,
)

/** An email message displayed in the Metro Mail client. */
@Immutable
data class MailMessage(
    val id: String,
    val accountId: String,
    val senderName: String,
    val senderAddress: String,
    val subject: String,
    val snippet: String,
    val timestamp: Long,
    val isUnread: Boolean,
    val isFlagged: Boolean = false,
    val bodyText: String,
    val hasAttachments: Boolean = false,
)

/** A person in the header of a message: the name it was written with (may be empty) and the address. */
@Immutable
data class MailAddress(val name: String, val address: String) {
    val shown get() = name.ifBlank { address }
}

/** A file that came with a message; [index] says which one it is, to ask for it. */
@Immutable
data class MailAttachment(val name: String, val size: Long, val mime: String, val index: Int)

/**
 * What opening a message gives: the text and, when it has one, the page ([html], with the pictures that came inside it already in
 * place), who it was written to, the files that came with it, and where an answer goes.
 */
@Immutable
data class MailBody(
    val text: String,
    val html: String?,
    val attachments: List<MailAttachment>,
    val to: List<MailAddress>,
    val cc: List<MailAddress>,
    val replyTo: String?,
)
