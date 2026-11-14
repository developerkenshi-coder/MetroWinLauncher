package com.metrowin.launcher.wallet

import com.metrowin.launcher.data.Prefs
import android.content.Context
import androidx.core.content.edit
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
class WalletVault(
    private val file: File,
    private val crypt: NoteCrypt = KeystoreCrypt,
    private val legacy: (() -> List<Card>)? = null,
    private val dropLegacy: () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    constructor(context: Context) : this(
        File(context.filesDir, "wallet.dat"),
        legacy = { WalletStore(context).load().map { Card(it.id.ifBlank { UUID.randomUUID().toString() }, it.name, it.code) } },
        dropLegacy = { Prefs.main(context).edit { remove("wallet") } },
    )

    private val sealed = AtomicEncryptedFile(file, crypt)

    /** True when the last [load] found a file it could not read (the key of the Keystore is gone): the wallet is empty and the file was put aside. */
    @Volatile
    var unreadable = false
        private set

    /** The cards; the ones of the older wallet are brought in the first time. */
    fun load(): List<Card> {
        unreadable = false
        if (!file.exists()) {
            val old = legacy?.invoke().orEmpty()
            if (old.isEmpty()) return emptyList()
            // The old cards are removed only when the new file is written.
            if (save(old)) dropLegacy()
            return old
        }
        val plain = sealed.read()
        if (plain == null) {
            // A name of its own each time: a second failure must not cover the first file, which may still be good with the right key.
            sealed.putAside("wallet.unreadable.${clock()}")
            unreadable = true
            return emptyList()
        }
        return Wallet.decode(plain)
    }

    fun save(cards: List<Card>): Boolean = sealed.write(Wallet.encode(cards))
}
