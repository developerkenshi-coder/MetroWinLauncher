package com.metrowin.launcher.wallet

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import org.json.JSONArray
import org.json.JSONObject

/** The kinds of code a card of the wallet can be drawn as. The first six are the oldest: cards saved with them keep their names. */
enum class CodeFormat(val zxing: BarcodeFormat, val twoDimensional: Boolean) {
    Code128(BarcodeFormat.CODE_128, false), Ean13(BarcodeFormat.EAN_13, false), Code39(BarcodeFormat.CODE_39, false),
    Qr(BarcodeFormat.QR_CODE, true), Aztec(BarcodeFormat.AZTEC, true), Pdf417(BarcodeFormat.PDF_417, true),
    Ean8(BarcodeFormat.EAN_8, false), UpcA(BarcodeFormat.UPC_A, false), UpcE(BarcodeFormat.UPC_E, false),
    Itf(BarcodeFormat.ITF, false), Codabar(BarcodeFormat.CODABAR, false), DataMatrix(BarcodeFormat.DATA_MATRIX, true);

    companion object {
        fun of(format: BarcodeFormat): CodeFormat? = entries.firstOrNull { it.zxing == format }
    }
}

enum class CardKind { Loyalty, Ticket, Other }

enum class SortMode { Name, Expiry, LastUsed }

enum class ExpiryState { None, Valid, Soon, Expired }

/**
 * A card of the wallet: a loyalty card, a ticket, anything with a code. [expiry] is the last day it is good, as an epoch day (0 for
 * no date); [color] is a number of the accent colours (0 for the accent of the phone). [lastUsed] (epoch milliseconds, 0 for never) and [uses]
 * are kept only when the user asks the wallet to remember.
 */
data class Card(
    val id: String,
    val name: String,
    val code: String,
    val format: CodeFormat = CodeFormat.Code128,
    val kind: CardKind = CardKind.Loyalty,
    val expiry: Long = 0L,
    val note: String = "",
    val color: Int = 0,
    val created: Long = 0L,
    val favorite: Boolean = false,
    val lastUsed: Long = 0L,
    val uses: Int = 0,
)

/** What the wallet does with cards. Pure but for the pictures, which need the bitmap classes of Android only to be read. */
object Wallet {
    const val MAX_CARDS = 200
    const val MAX_CODE = 512
    const val MAX_NAME = 60
    const val MAX_NOTE = 500
    const val MAX_ACCENT = 15

    /** The biggest file taken for a backup of the wallet. */
    const val MAX_IMPORT_BYTES = 4_000_000

    /** A card that is good for this many days or fewer is "soon" to be no good. */
    const val SOON_DAYS = 30L

    /** The matrix of [code] drawn as [format], or null when the code cannot be written in that format (a letter in an EAN, a wrong length). */
    fun matrix(code: String, format: CodeFormat): BitMatrix? = runCatching {
        val hints = mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.CHARACTER_SET to "UTF-8")
        val width = if (format.twoDimensional) 0 else 600
        val height = if (format.twoDimensional) 0 else 1
        MultiFormatWriter().encode(code, format.zxing, width, height, hints)
    }.getOrNull()

    fun valid(code: String, format: CodeFormat): Boolean = code.isNotBlank() && code.length <= MAX_CODE && matrix(code, format) != null

    /**
     * [code] with the check digit a shorter EAN or UPC leaves out added (12 digits for an EAN-13, 7 for an EAN-8, 11 for a UPC-A), so the
     * card shows the whole number under the bars. Anything else comes back as it is; a number with a wrong check digit is left to [valid].
     */
    fun completed(code: String, format: CodeFormat): String {
        val missing = when (format) {
            CodeFormat.Ean13 -> 12
            CodeFormat.Ean8 -> 7
            CodeFormat.UpcA -> 11
            else -> return code
        }
        return if (code.length == missing && code.all { it in '0'..'9' }) code + checkDigit(code) else code
    }

    /** The GS1 check digit of [digits] (the number without it): the digits from the right count 3, 1, 3, 1... */
    fun checkDigit(digits: String): Char {
        val sum = digits.reversed().withIndex().sumOf { (i, c) -> (c - '0') * if (i % 2 == 0) 3 else 1 }
        return '0' + (10 - sum % 10) % 10
    }

    /** The code in a picture, and its format; null when there is none that can be read. */
    fun read(bitmap: Bitmap): Pair<String, CodeFormat>? = runCatching {
        val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val result = MultiFormatReader().apply { setHints(readHints()) }.decode(BinaryBitmap(HybridBinarizer(source)))
        CodeFormat.of(result.barcodeFormat)?.let { result.text to it }
    }.getOrNull()

    /** Light codes on a dark ground are read too (ALSO_INVERTED), which a QR code on a dark screen needs. */
    internal fun readHints(): Map<DecodeHintType, Any> =
        mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.ALSO_INVERTED to true, DecodeHintType.POSSIBLE_FORMATS to CodeFormat.entries.map { it.zxing })

    /** Days until the card is no good (negative when it is not), or null for a card with no date. */
    fun daysLeft(card: Card, today: Long): Long? = card.expiry.takeIf { it != 0L }?.minus(today)

    fun expiryState(card: Card, today: Long): ExpiryState {
        val left = daysLeft(card, today) ?: return ExpiryState.None
        return when {
            left < 0 -> ExpiryState.Expired
            left <= SOON_DAYS -> ExpiryState.Soon
            else -> ExpiryState.Valid
        }
    }

    /** Whether [card] answers a search: the name, and with [inNote] the note as well. A blank [query] matches all. */
    fun matches(card: Card, query: String, inNote: Boolean = true): Boolean {
        val q = query.trim()
        return q.isEmpty() || card.name.contains(q, true) || (inNote && card.note.contains(q, true))
    }

    /** [cards] in the order asked; the favourites come first, and cards that tie keep the order of the name. */
    fun sorted(cards: List<Card>, mode: SortMode): List<Card> {
        val byName = compareBy<Card> { it.name.lowercase() }
        val order = when (mode) {
            SortMode.Name -> byName
            // No date goes last, the nearest date first.
            SortMode.Expiry -> compareBy<Card> { if (it.expiry == 0L) Long.MAX_VALUE else it.expiry }.then(byName)
            SortMode.LastUsed -> compareByDescending<Card> { it.lastUsed }.then(byName)
        }
        return cards.sortedWith(compareByDescending<Card> { it.favorite }.then(order))
    }

    fun encode(cards: List<Card>): String = JSONArray().also { array ->
        cards.forEach {
            array.put(
                JSONObject().put("id", it.id).put("name", it.name).put("code", it.code).put("format", it.format.name).put("kind", it.kind.name)
                    .put("expiry", it.expiry).put("note", it.note).put("color", it.color).put("created", it.created)
                    .put("favorite", it.favorite).put("lastUsed", it.lastUsed).put("uses", it.uses),
            )
        }
    }.toString()

    fun decode(json: String?): List<Card> = runCatching {
        val array = JSONArray(json ?: return emptyList())
        (0 until minOf(array.length(), MAX_CARDS)).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                val format = runCatching { CodeFormat.valueOf(o.optString("format", "Code128")) }.getOrDefault(CodeFormat.Code128)
                Card(
                    id = o.getString("id").take(64), name = o.getString("name").take(MAX_NAME), code = o.getString("code").take(MAX_CODE), format = format,
                    kind = runCatching { CardKind.valueOf(o.optString("kind", "Loyalty")) }.getOrDefault(CardKind.Loyalty),
                    expiry = o.optLong("expiry"), note = o.optString("note").take(MAX_NOTE), color = o.optInt("color").coerceIn(0, MAX_ACCENT), created = o.optLong("created"),
                    favorite = o.optBoolean("favorite"), lastUsed = o.optLong("lastUsed"), uses = o.optInt("uses").coerceAtLeast(0),
                )
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    /** [incoming] joined to [existing]: a card with the same id is replaced by the incoming one, the others are added. */
    fun merge(existing: List<Card>, incoming: List<Card>): List<Card> {
        val byId = LinkedHashMap<String, Card>()
        existing.forEach { byId[it.id] = it }
        incoming.forEach { byId[it.id] = it }
        return byId.values.take(MAX_CARDS)
    }

    /** How many of [incoming] are in [merged]: the number to tell the user, which is smaller than the file when the wallet is full. */
    fun accepted(merged: List<Card>, incoming: List<Card>): Int = merged.map { it.id }.toSet().let { ids -> incoming.map { it.id }.distinct().count { it in ids } }
}
