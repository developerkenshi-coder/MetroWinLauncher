package com.metrowin.launcher.wallet

import com.metrowin.launcher.data.Prefs
import android.content.Context
import com.metrowin.launcher.data.AtomicEncryptedFile
import com.metrowin.launcher.data.WalletStore
import com.metrowin.launcher.notes.KeystoreCrypt
import com.metrowin.launcher.notes.NoteCrypt
import java.io.File
import java.util.UUID

/**
 * The cards of the wallet, in one encrypted file (the codes of loyalty cards and tickets are worth stealing). The cards the older wallet
 * kept in the clear are brought here once, and then taken out of the preferences. A failed write never loses a card: the new file replaces the old one only when it is whole.
 */
class WalletVault(private val file: File, private val crypt: NoteCrypt = KeystoreCrypt, private val legacy: (() -> List<Card>)? = null, private val dropLegacy: () -> Unit = {}) {
    constructor(context: Context) : this(
        File(context.filesDir, "wallet.dat"),
        legacy = { WalletStore(context).load().map { Card(it.id.ifBlank { UUID.randomUUID().toString() }, it.name, it.code) } },
        dropLegacy = { Prefs.main(context).edit().remove("wallet").apply() },
    )

    private val sealed = AtomicEncryptedFile(file, crypt)

    /** The cards; the ones of the older wallet are brought in the first time. */
    fun load(): List<Card> {
        if (!file.exists()) {
            val old = legacy?.invoke().orEmpty()
            if (old.isEmpty()) return emptyList()
            // The old cards are removed only when the new file is written.
            if (save(old)) dropLegacy()
            return old
        }
        val plain = sealed.read()
        if (plain == null) {
            sealed.putAside("wallet.unreadable")
            return emptyList()
        }
        return Wallet.decode(plain)
    }

    fun save(cards: List<Card>): Boolean = sealed.write(Wallet.encode(cards))
}
