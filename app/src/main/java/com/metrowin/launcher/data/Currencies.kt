package com.metrowin.launcher.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/**
 * "100 usd in eur", "50 dollari in euro", "£20 to €": a conversion between currencies. Understanding the words is pure;
 * the rates come from the European Central Bank through api.frankfurter.dev (see [CurrencyRepository]), only when the
 * user turned this on.
 */
object Currencies {
    /** What was asked: [amount] of the currency [from], in the currency [to] (ISO codes). */
    data class Query(val amount: Double, val from: String, val to: String)

    /** The rates of a day: how many of each currency one euro buys, and when they were fetched. */
    data class Rates(val date: String, val perEuro: Map<String, Double>, val fetchedAt: Long)

    /** The currencies the rates cover. */
    val codes: Set<String> = setOf(
        "AUD", "BGN", "BRL", "CAD", "CHF", "CNY", "CZK", "DKK", "EUR", "GBP", "HKD", "HUF", "IDR", "ILS", "INR", "ISK", "JPY", "KRW",
        "MXN", "MYR", "NOK", "NZD", "PHP", "PLN", "RON", "SEK", "SGD", "THB", "TRY", "USD", "ZAR",
    )

    private val names: Map<String, String> = buildMap {
        fun add(code: String, vararg words: String) = words.forEach { put(it, code) }
        add("EUR", "euro", "euros")
        add("USD", "dollaro", "dollari", "dollar", "dollars", "dollaro usa", "dollari usa", "us dollar", "us dollars", "dollaro americano", "dollari americani")
        add("GBP", "sterlina", "sterline", "pound", "pounds", "pound sterling", "pounds sterling", "lira sterlina", "lire sterline")
        add("JPY", "yen")
        add("CHF", "franco", "franchi", "franco svizzero", "franchi svizzeri", "franc", "francs", "swiss franc", "swiss francs")
        add("CNY", "yuan", "renminbi", "rmb")
        add("AUD", "dollaro australiano", "dollari australiani", "australian dollar", "australian dollars")
        add("CAD", "dollaro canadese", "dollari canadesi", "canadian dollar", "canadian dollars")
        add("INR", "rupia", "rupie", "rupee", "rupees")
        add("KRW", "won")
        add("BRL", "real", "reais")
        add("TRY", "lira turca", "lire turche", "turkish lira")
        add("ZAR", "rand")
        add("SEK", "corona svedese", "corone svedesi", "swedish krona")
        add("NOK", "corona norvegese", "corone norvegesi", "norwegian krone")
        add("DKK", "corona danese", "corone danesi", "danish krone")
        add("PLN", "zloty")
        add("MXN", "peso messicano", "pesos messicani", "mexican peso", "mexican pesos")
        add("THB", "baht")
        add("ILS", "shekel", "sheqel")
    }

    private val SYMBOLS = mapOf('€' to " eur ", '$' to " usd ", '£' to " gbp ", '¥' to " jpy ")
    private val SYMBOL_FIRST = Regex("""([€$£¥])\s*(\d+(?:[.,]\d+)?)""")
    private val PATTERN = Regex("""^(?:([-+]?\d+(?:[.,]\d+)?)\s*)?([\p{L} ]+?)\s+(?:in|to|a|into|=|->|=>)\s+([\p{L} ]+)$""")

    /** The conversion in [text], or null when it is not one (or names a currency the rates do not have). */
    fun parse(text: String): Query? {
        // "€10" is 10 euros: a symbol in front of a number goes after it.
        var raw = SYMBOL_FIRST.replace(text) { "${it.groupValues[2]} ${SYMBOLS[it.groupValues[1][0]]}" }
        SYMBOLS.forEach { (symbol, code) -> raw = raw.replace(symbol.toString(), code) }
        val folded = AppSections.fold(raw).replace(Regex("\\s+"), " ").trim()
        val match = PATTERN.matchEntire(folded) ?: return null
        val amount = match.groupValues[1].takeIf { it.isNotEmpty() }?.replace(',', '.')?.toDoubleOrNull() ?: 1.0
        val from = code(match.groupValues[2]) ?: return null
        val to = code(match.groupValues[3]) ?: return null
        if (from == to || !amount.isFinite() || abs(amount) >= 1e12) return null
        return Query(amount, from, to)
    }

    private fun code(word: String): String? {
        val key = word.trim()
        return names[key] ?: key.uppercase(Locale.ROOT).takeIf { it in codes }
    }

    /** [Query.amount] of the first currency in the second, or null when the rates lack one of them. */
    fun convert(query: Query, rates: Rates): Double? {
        val from = if (query.from == "EUR") 1.0 else rates.perEuro[query.from] ?: return null
        val to = if (query.to == "EUR") 1.0 else rates.perEuro[query.to] ?: return null
        return (query.amount / from * to).takeIf { it.isFinite() }
    }

    /** The answer for [query] with [rates]: "100 USD = 92.4 EUR", the number alone for copying, and the day of the rates. */
    fun answer(query: Query, rates: Rates, locale: Locale = Locale.getDefault()): QuickAnswer.Currency? {
        val result = convert(query, rates) ?: return null
        val shown = format(result, locale)
        return QuickAnswer.Currency("${format(query.amount, locale, minDigits = 0)} ${query.from} = $shown ${query.to}", shown, rates.date)
    }

    /** Two decimals for an amount of a currency; more for a very small one; no grouping, so it pastes anywhere. */
    fun format(value: Double, locale: Locale = Locale.getDefault(), minDigits: Int = 2): String {
        val digits = when {
            abs(value) >= 1 -> 2
            abs(value) >= 0.01 -> 4
            else -> 6
        }
        return NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = false
            maximumFractionDigits = digits
            minimumFractionDigits = minOf(minDigits, digits)
        }.format(value)
    }

    /** The rates in the answer of Frankfurter (`{"base":"EUR","date":"2026-10-23","rates":{"USD":1.08,…}}`); null for anything else. */
    fun parseRates(json: String, fetchedAt: Long): Rates? = runCatching {
        val root = JSONObject(json)
        if (root.optString("base") != "EUR") return null
        val table = root.getJSONObject("rates")
        val perEuro = HashMap<String, Double>()
        table.keys().forEach { code ->
            val rate = table.optDouble(code, Double.NaN)
            if (code in codes && rate.isFinite() && rate > 0) perEuro[code] = rate
        }
        if (perEuro.size < MIN_RATES) return null
        Rates(root.optString("date"), perEuro, fetchedAt)
    }.getOrNull()

    const val RATES_URL = "https://api.frankfurter.dev/v1/latest?base=EUR"
    const val HOST = "api.frankfurter.dev"

    /** Rates fetched less than this long ago are used without asking again. */
    const val MAX_AGE_MS = 6 * 60 * 60 * 1000L
    private const val MIN_RATES = 5
}

/** Fetches the rates and keeps the last ones, so a conversion works offline with them. */
class CurrencyRepository(context: Context) {
    private val prefs = context.getSharedPreferences("metrowin", Context.MODE_PRIVATE)

    /** The rates fetched last, or null if there never were any. */
    fun cached(): Currencies.Rates? {
        val json = prefs.getString(KEY_RATES, null) ?: return null
        return Currencies.parseRates(json, prefs.getLong(KEY_FETCHED, 0L))
    }

    /** Blocking, call it off the main thread. The new rates (and they are kept), or null when there is no connection. */
    fun fetch(now: Long): Currencies.Rates? {
        val json = SafeHttp.get(Currencies.RATES_URL, setOf(Currencies.HOST)) ?: return null
        val rates = Currencies.parseRates(json, now) ?: return null
        prefs.edit {
            putString(KEY_RATES, json)
            putLong(KEY_FETCHED, now)
        }
        return rates
    }

    private companion object {
        const val KEY_RATES = "currency_rates"
        const val KEY_FETCHED = "currency_rates_at"
    }
}
