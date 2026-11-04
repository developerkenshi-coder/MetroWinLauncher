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

/** The kinds of code a card of the wallet can be drawn as. */
enum class CodeFormat(val zxing: BarcodeFormat, val twoDimensional: Boolean) {
    Code128(BarcodeFormat.CODE_128, false), Ean13(BarcodeFormat.EAN_13, false), Code39(BarcodeFormat.CODE_39, false),
    Qr(BarcodeFormat.QR_CODE, true), Aztec(BarcodeFormat.AZTEC, true), Pdf417(BarcodeFormat.PDF_417, true);

    companion object {
        fun of(format: BarcodeFormat): CodeFormat? = entries.firstOrNull { it.zxing == format }
    }
}

enum class CardKind { Loyalty, Ticket, Other }

/**
 * A card of the wallet: a loyalty card, a ticket, anything with a code. [expiry] is the last day it is good, as an epoch day (0 for
 * no date); [color] is a number of the accent colours (0 for the accent of the phone).
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
)

/** What the wallet does with cards. Pure but for the pictures, which need the bitmap classes of Android only to be read. */
object Wallet {
    const val MAX_CARDS = 200
    const val MAX_CODE = 512

    /** The matrix of [code] drawn as [format], or null when the code cannot be written in that format (a letter in an EAN, a wrong length). */
    fun matrix(code: String, format: CodeFormat): BitMatrix? = runCatching {
        val hints = mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.CHARACTER_SET to "UTF-8")
        val width = if (format.twoDimensional) 0 else 600
        val height = if (format.twoDimensional) 0 else 1
        MultiFormatWriter().encode(code, format.zxing, width, height, hints)
    }.getOrNull()

    fun valid(code: String, format: CodeFormat): Boolean = code.isNotBlank() && code.length <= MAX_CODE && matrix(code, format) != null

    /** The code in a picture, and its format; null when there is none that can be read. */
    fun read(bitmap: Bitmap): Pair<String, CodeFormat>? = runCatching {
        val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val hints = mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.POSSIBLE_FORMATS to CodeFormat.entries.map { it.zxing })
        val result = MultiFormatReader().apply { setHints(hints) }.decode(BinaryBitmap(HybridBinarizer(source)))
        CodeFormat.of(result.barcodeFormat)?.let { result.text to it }
    }.getOrNull()

    /** Days until the card is no good (negative when it is not), or null for a card with no date. */
    fun daysLeft(card: Card, today: Long): Long? = card.expiry.takeIf { it != 0L }?.minus(today)

    fun encode(cards: List<Card>): String = JSONArray().also { array ->
        cards.forEach {
            array.put(
                JSONObject().put("id", it.id).put("name", it.name).put("code", it.code).put("format", it.format.name).put("kind", it.kind.name)
                    .put("expiry", it.expiry).put("note", it.note).put("color", it.color).put("created", it.created),
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
                    id = o.getString("id").take(64), name = o.getString("name").take(60), code = o.getString("code").take(MAX_CODE), format = format,
                    kind = runCatching { CardKind.valueOf(o.optString("kind", "Loyalty")) }.getOrDefault(CardKind.Loyalty),
                    expiry = o.optLong("expiry"), note = o.optString("note").take(500), color = o.optInt("color").coerceIn(0, 15), created = o.optLong("created"),
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
}
