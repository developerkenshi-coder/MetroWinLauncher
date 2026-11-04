package com.metrowin.launcher.gestures

import com.metrowin.launcher.data.LauncherSettings

/**
 * Banking apps look for what could read or cover their screen: an accessibility service that is on, a permission to draw over other apps, a
 * window over theirs. The launcher cannot hide that a service is on or a permission is granted, but it can take every window of its own
 * away while such an app is in front, and bring them back after. This is the rule for which apps count.
 */
object SafeMode {
    /** Part of a package name that says a bank, a card, a wallet or an exchange; the user takes out the ones that are not (see [LauncherSettings.bankSafeSkip]). */
    private val WORDS = listOf(
        "bank", "banco", "banca", "banque", "bancaire", "bbva", "santander", "caixa", "unicredit", "intesa", "postepay", "posteitaliane",
        "paypal", "revolut", "monzo", "n26", "fineco", "mediolanum", "webank", "satispay", "hsbc", "barclays", "lloyds", "natwest", "wellsfargo",
        "sabadell", "ibercaja", "unicaja", "kutxa", "cajamar", "abanca", "bnpparibas", "creditagricole", "societegenerale", "transferwise",
        "binance", "coinbase", "kraken", "bitpanda", "nexi", "americanexpress", "mastercard",
    )

    /** Whether windows over [packageName] are taken away: on, and the app is a bank by its name or the user listed it, and not one the user took out. */
    fun isSensitive(packageName: String, settings: LauncherSettings): Boolean {
        if (!settings.bankSafe) return false
        if (packageName in settings.bankSafeAdd) return true
        if (packageName in settings.bankSafeSkip) return false
        return looksLikeBank(packageName)
    }

    fun looksLikeBank(packageName: String): Boolean {
        val name = packageName.lowercase()
        return WORDS.any { it in name }
    }
}
