package com.metrowin.launcher.mail

import androidx.compose.runtime.Immutable

@Immutable
data class MailProviderConfig(
    val displayName: String,
    val domain: String,
    val imapHost: String,
    val imapPort: Int = 993,
    val smtpHost: String,
    val smtpPort: Int = 465,
    val requiresAppPassword: Boolean = true,
    /** The page of the provider where a password for apps is made, for accounts with two-step verification; null when it has none. */
    val appPasswordUrl: String? = null,
    /** "microsoft" when the account signs in on the page of the provider instead of with a password. */
    val oauth: String? = null,
)

object MailServiceDetector {

    /** Automatically detects host, ports, and security settings from the entered email address. */
    fun detect(email: String): MailProviderConfig {
        val trimmed = email.trim().lowercase()
        val domain = trimmed.substringAfter("@", "").trim()

        return when {
            domain == "gmail.com" || domain.endsWith(".google.com") -> MailProviderConfig(
                displayName = "Google Gmail",
                domain = domain,
                imapHost = "imap.gmail.com",
                smtpHost = "smtp.gmail.com",
                requiresAppPassword = true,
                appPasswordUrl = "https://myaccount.google.com/apppasswords"
            )
            domain in listOf("outlook.com", "hotmail.com", "live.com", "msn.com", "outlook.it", "hotmail.it") -> MailProviderConfig(
                displayName = "Microsoft Outlook",
                domain = domain,
                imapHost = "outlook.office365.com",
                smtpHost = "smtp.office365.com",
                smtpPort = 587,
                requiresAppPassword = true,
                appPasswordUrl = "https://account.live.com/proofs/AppPassword",
                oauth = "microsoft"
            )
            domain.contains("yahoo") || domain.endsWith("ymail.com") -> MailProviderConfig(
                displayName = "Yahoo Mail",
                domain = domain,
                imapHost = "imap.mail.yahoo.com",
                smtpHost = "smtp.mail.yahoo.com",
                requiresAppPassword = true,
                appPasswordUrl = "https://login.yahoo.com/account/security/app-passwords"
            )
            domain == "virgilio.it" -> MailProviderConfig(
                displayName = "Virgilio Mail",
                domain = domain,
                imapHost = "in.virgilio.it",
                smtpHost = "out.virgilio.it",
                requiresAppPassword = false
            )
            domain == "libero.it" -> MailProviderConfig(
                displayName = "Libero Mail",
                domain = domain,
                imapHost = "imapmail.libero.it",
                smtpHost = "smtp.libero.it",
                requiresAppPassword = false
            )
            domain == "icloud.com" || domain == "me.com" || domain == "mac.com" -> MailProviderConfig(
                displayName = "Apple iCloud",
                domain = domain,
                imapHost = "imap.mail.me.com",
                smtpHost = "smtp.mail.me.com",
                smtpPort = 587,
                requiresAppPassword = true,
                appPasswordUrl = "https://appleid.apple.com/account/manage"
            )
            domain.isNotBlank() -> {
                val hostDomain = if (domain.startsWith("mail.")) domain.removePrefix("mail.") else domain
                MailProviderConfig(
                    displayName = domain.replaceFirstChar { it.uppercase() },
                    domain = domain,
                    imapHost = "imap.$hostDomain",
                    smtpHost = "smtp.$hostDomain",
                    requiresAppPassword = false
                )
            }
            else -> MailProviderConfig(
                displayName = "IMAP Mail",
                domain = "",
                imapHost = "imap.mail.com",
                smtpHost = "smtp.mail.com",
                requiresAppPassword = false
            )
        }
    }
}
