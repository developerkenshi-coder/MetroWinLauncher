package com.metrowin.launcher.mail

import android.content.Context
import androidx.annotation.StringRes
import com.metrowin.launcher.R
import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException
import javax.mail.SendFailedException

/** What went wrong with a mail call, in the terms the user can act on. */
enum class MailError(@StringRes val text: Int) {
    Login(R.string.mail_err_login),
    TooBig(R.string.mail_err_size),
    Recipient(R.string.mail_err_recipient),
    Network(R.string.mail_err_network),
    Certificate(R.string.mail_err_tls),
    NoTrash(R.string.mail_err_no_trash),
    Other(R.string.mail_err_other),
}

/** Turns what JavaMail throws (its texts are the lines the server said) into [MailError]. */
object MailErrors {
    /** The exception and everything it wraps: JavaMail hangs the real reason on getNextException, not always on the cause. */
    private fun chain(e: Throwable): List<Throwable> {
        val all = ArrayList<Throwable>()
        var next: Throwable? = e
        while (next != null && all.size < 8 && next !in all) {
            all += next
            next = (next as? MessagingException)?.nextException ?: next.cause
        }
        return all
    }

    fun kind(e: Throwable): MailError {
        val all = chain(e)
        val said = all.joinToString(" | ") { it.message.orEmpty() }.lowercase()
        return when {
            all.any { it is NoTrashException } -> MailError.NoTrash
            all.any { it is AuthenticationFailedException } || "authenticationfailed" in said.replace(" ", "") || "invalid credentials" in said || "535" in said -> MailError.Login
            all.any { it is javax.net.ssl.SSLException } || "certificate" in said || "pkix" in said -> MailError.Certificate
            "552" in said || "size" in said && ("exceed" in said || "limit" in said || "large" in said) || "too large" in said || "message too big" in said -> MailError.TooBig
            all.any { it is SendFailedException && it.invalidAddresses?.isNotEmpty() == true } || "550" in said || "553" in said || "user unknown" in said || "no such user" in said ||
                "recipient" in said || "invalid addresses" in said || all.any { it is javax.mail.internet.AddressException } -> MailError.Recipient
            all.any { it is java.net.UnknownHostException || it is java.net.ConnectException || it is java.net.SocketTimeoutException || it is java.net.SocketException || it is java.io.EOFException } ||
                "couldn't connect" in said || "unable to connect" in said -> MailError.Network
            else -> MailError.Other
        }
    }

    /** The line to show: the message for the kind, and for an unknown one what the server said (no secret is ever in it: the password is not part of any exception). */
    fun text(context: Context, e: Throwable): String {
        val kind = kind(e)
        return if (kind == MailError.Other) e.message?.lineSequence()?.firstOrNull().orEmpty().ifBlank { e.javaClass.simpleName } else context.getString(kind.text)
    }
}
