package com.metrowin.launcher.wallet

import com.metrowin.launcher.data.SyncCrypto
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import java.util.zip.ZipInputStream

/** What reading a file given to the wallet came to. */
sealed interface Imported {
    data class Cards(val cards: List<Card>, val rgb: Int? = null) : Imported
    data object NeedsPassphrase : Imported
    data object WrongPassphrase : Imported
    data object TooBig : Imported
    data object Unreadable : Imported
}

/**
 * The files the wallet writes and reads. A backup is the JSON of the cards, in the clear or (when asked) sealed with a passphrase the way
 * the sync files of the launcher are: AES-GCM, so a changed file is refused. A `.pkpass` (a zip with a `pass.json`) is read as one card.
 */
object WalletBackup {
    fun write(cards: List<Card>, passphrase: String?): ByteArray {
        val json = Wallet.encode(cards).toByteArray(Charsets.UTF_8)
        return if (passphrase.isNullOrEmpty()) json else SyncCrypto.encrypt(json, passphrase)
    }

    fun read(bytes: ByteArray?, passphrase: String? = null): Imported {
        if (bytes == null) return Imported.Unreadable
        if (bytes.size > Wallet.MAX_IMPORT_BYTES) return Imported.TooBig
        val plain = when {
            SyncCrypto.isSyncFile(bytes) -> {
                if (passphrase.isNullOrEmpty()) return Imported.NeedsPassphrase
                SyncCrypto.decrypt(bytes, passphrase) ?: return Imported.WrongPassphrase
            }
            isZip(bytes) -> return pass(bytes)?.let { Imported.Cards(listOf(it.first), it.second) } ?: Imported.Unreadable
            else -> bytes
        }
        val cards = Wallet.decode(String(plain, Charsets.UTF_8))
        return if (cards.isEmpty()) Imported.Unreadable else Imported.Cards(cards)
    }

    private fun isZip(bytes: ByteArray) = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    /** The card of a `.pkpass` and the colour it asks for (an RGB number, or null): its first barcode, its name, its end date. Null when it has no code the wallet can draw. */
    internal fun pass(bytes: ByteArray): Pair<Card, Int?>? = runCatching {
        val json = ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.firstOrNull { it.name == "pass.json" }?.let { readCapped(zip) }
        } ?: return null
        val pass = JSONObject(json)
        val barcode = pass.optJSONArray("barcodes")?.optJSONObject(0) ?: pass.optJSONObject("barcode") ?: return null
        val format = when (barcode.optString("format")) {
            "PKBarcodeFormatQR" -> CodeFormat.Qr
            "PKBarcodeFormatPDF417" -> CodeFormat.Pdf417
            "PKBarcodeFormatAztec" -> CodeFormat.Aztec
            "PKBarcodeFormatCode128" -> CodeFormat.Code128
            else -> return null
        }
        val code = barcode.optString("message").take(Wallet.MAX_CODE).takeIf { Wallet.valid(it, format) } ?: return null
        val name = listOf("organizationName", "logoText", "description").map { pass.optString(it) }.firstOrNull { it.isNotBlank() } ?: return null
        val kind = when {
            pass.has("eventTicket") || pass.has("boardingPass") || pass.has("coupon") -> CardKind.Ticket
            pass.has("storeCard") -> CardKind.Loyalty
            else -> CardKind.Other
        }
        val expiry = pass.optString("expirationDate").takeIf { it.isNotBlank() }?.let { runCatching { OffsetDateTime.parse(it).toLocalDate().toEpochDay() }.getOrNull() } ?: 0L
        val card = Card(UUID.randomUUID().toString(), name.take(Wallet.MAX_NAME), code, format, kind, expiry, created = System.currentTimeMillis())
        card to rgb(pass.optString("backgroundColor"))
    }.getOrNull()

    /** "rgb(12, 34, 56)" as one number, or null. */
    internal fun rgb(text: String): Int? {
        val parts = Regex("""rgb\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*\)""").matchEntire(text.trim())?.groupValues?.drop(1)?.map { it.toInt() } ?: return null
        return if (parts.any { it > 255 }) null else (parts[0] shl 16) or (parts[1] shl 8) or parts[2]
    }

    /** The text of the entry being read, at most 1 MB: a zip that says it is small and is not cannot fill the memory. */
    private fun readCapped(zip: ZipInputStream): String? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = zip.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > 1_000_000) return null
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
