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
    /** True when the domain is one that is not known: the servers are only a guess (imap.<domain>), and another way to find them may do better. */
    val generic: Boolean = false,
)

object MailServiceDetector {
    /** Yahoo and its sister names, and not any domain that happens to have "yahoo" in it ("notyahoo.example"). */
    internal fun isYahoo(domain: String) =
        domain == "yahoo.com" || domain.startsWith("yahoo.") || domain.endsWith(".yahoo.com") || domain == "ymail.com" || domain == "rocketmail.com"

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
            isYahoo(domain) -> MailProviderConfig(
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
                    requiresAppPassword = false,
                    generic = true,
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

/**
 * The settings of a mail server that the provider publishes itself (the "autoconfig" file that other mail programs read), asked of the
 * domain of the address, and of the public database of Thunderbird when the domain has none. Only the domain is sent, over HTTPS; only
 * servers that use TLS are accepted. Used only when the user turned on [MailOption.Autoconfig].
 */
object MailAutoconfig {
    /** The settings in [xml] for an address of [domain], or null when it has no usable IMAP and SMTP server with TLS. */
    fun parse(xml: String, domain: String): MailProviderConfig? = runCatching {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            // A file from the net is not trusted: no DTD, no external entities.
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
        fun server(tag: String, type: String): Triple<String, Int, String>? {
            val nodes = doc.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val node = nodes.item(i) as org.w3c.dom.Element
                if (node.getAttribute("type") != type) continue
                fun text(name: String) = node.getElementsByTagName(name).item(0)?.textContent?.trim().orEmpty()
                val socket = text("socketType").uppercase()
                if (socket != "SSL" && socket != "STARTTLS") continue
                val host = text("hostname").replace("%EMAILDOMAIN%", domain)
                val port = text("port").toIntOrNull() ?: continue
                if (host.isNotBlank()) return Triple(host, port, socket)
            }
            return null
        }
        val imap = server("incomingServer", "imap") ?: return null
        val smtp = server("outgoingServer", "smtp") ?: return null
        MailProviderConfig(displayName = domain.replaceFirstChar { it.uppercase() }, domain = domain, imapHost = imap.first, imapPort = imap.second, smtpHost = smtp.first, smtpPort = smtp.second, requiresAppPassword = false)
    }.getOrNull()

    /** Blocking. Asks the domain, then the database; null when neither knows it or the net fails. */
    fun lookup(domain: String): MailProviderConfig? {
        if (!Regex("^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$").matches(domain)) return null
        return listOf("https://autoconfig.$domain/mail/config-v1.1.xml", "https://autoconfig.thunderbird.net/v1.1/$domain").firstNotNullOfOrNull { url ->
            runCatching {
                val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply { connectTimeout = 6_000; readTimeout = 6_000; instanceFollowRedirects = false }
                try {
                    if (connection.responseCode != 200) null else parse(connection.inputStream.use { it.readNBytes(200_000).toString(Charsets.UTF_8) }, domain)
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
    }
}
