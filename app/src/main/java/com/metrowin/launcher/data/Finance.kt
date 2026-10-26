package com.metrowin.launcher.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * One line of the Finance tile: what [item] ("fx:USD", "crypto:bitcoin") is worth in the base currency, and by how many percent
 * that moved ([changePercent], NaN when there is nothing to compare with).
 */
@Immutable
data class Quote(val item: String, val value: Double, val changePercent: Double)

/** The quotes of the Finance tile, as they were at [fetchedAt] ([date] is the day of the rates), for the [key] it was asked with. */
@Immutable
data class FinanceData(val base: String, val quotes: List<Quote>, val date: String, val fetchedAt: Long, val key: String)

/**
 * What the Finance tile can show: the currencies of the European Central Bank (rates through api.frankfurter.dev) and a few
 * cryptocurrencies (CoinGecko). No stock prices: there is no free source of them that needs no account.
 */
object FinanceItems {
    const val MAX = 4
    const val FX = "fx:"
    const val CRYPTO = "crypto:"

    /** CoinGecko's name of each coin, and the letters people know it by. */
    val coins: Map<String, String> = linkedMapOf(
        "bitcoin" to "BTC", "ethereum" to "ETH", "litecoin" to "LTC", "solana" to "SOL", "ripple" to "XRP", "dogecoin" to "DOGE",
    )

    val DEFAULT = listOf("fx:USD", "fx:GBP", "crypto:bitcoin")

    fun fx(code: String) = FX + code
    fun coin(id: String) = CRYPTO + id
    fun isFx(item: String) = item.startsWith(FX) && item.removePrefix(FX) in Currencies.codes
    fun isCoin(item: String) = item.startsWith(CRYPTO) && item.removePrefix(CRYPTO) in coins
    fun isValid(item: String) = isFx(item) || isCoin(item)

    /** The letters of [item] ("USD", "BTC"). */
    fun symbol(item: String): String = if (item.startsWith(CRYPTO)) coins[item.removePrefix(CRYPTO)].orEmpty() else item.removePrefix(FX)

    /** [items] kept when they are known, only once each, never the base currency against itself, and no more than [MAX]. */
    fun normalize(items: Collection<String>, base: String): List<String> =
        items.filter { isValid(it) && it != fx(base) }.distinct().take(MAX)

    /** Every item the user can pick for [base]: its currencies, then the coins. */
    fun available(base: String): List<String> =
        Currencies.codes.sorted().filter { it != base }.map(::fx) + coins.keys.map(::coin)
}

object Finance {
    const val FX_HOST = Currencies.HOST
    const val COIN_HOST = "api.coingecko.com"

    /** Rates fetched less than this long ago are shown without asking again, unless the settings changed. */
    const val RANGE_DAYS = 6L

    /** The currency to show values in: the one the user chose, or the one of the region of the phone when they did not, or the euro. */
    fun baseFor(setting: String, locale: Locale): String {
        if (setting in Currencies.codes) return setting
        val local = runCatching { Currency.getInstance(locale).currencyCode }.getOrNull()
        return local?.takeIf { it in Currencies.codes } ?: "EUR"
    }

    /** What a changed question means: a different base or other items need a new fetch, so the kept data are not used for them. */
    fun key(base: String, items: List<String>) = base + "|" + items.joinToString(",")

    /** The address of the currency rates of the last days for [items] and [base]; null when there is no currency to ask about. */
    fun fxUrl(base: String, items: List<String>, today: LocalDate): String? {
        val codes = items.filter(FinanceItems::isFx).map { it.removePrefix(FinanceItems.FX) }
        if (codes.isEmpty()) return null
        val symbols = (codes + base).filter { it != "EUR" }.distinct().sorted()
        val tail = if (symbols.isEmpty()) "" else "&symbols=${symbols.joinToString(",")}"
        return "https://$FX_HOST/v1/${today.minusDays(RANGE_DAYS)}..?base=EUR$tail"
    }

    /** The address of the prices of the coins among [items] in [base]; null when there is no coin to ask about or CoinGecko does not know the currency. */
    fun coinUrl(base: String, items: List<String>): String? {
        val ids = items.filter(FinanceItems::isCoin).map { it.removePrefix(FinanceItems.CRYPTO) }
        if (ids.isEmpty()) return null
        return "https://$COIN_HOST/api/v3/simple/price?ids=${ids.joinToString(",")}&vs_currencies=${base.lowercase(Locale.ROOT)}&include_24hr_change=true"
    }

    /**
     * The quotes of the currencies among [items] in [base] from the answer of Frankfurter for a range of days (rates per euro, one
     * set a day): the value of one unit of each currency in [base], and how it moved since the day before. Missing currencies
     * are left out. [Parsed.date] is the day of the latest rates.
     */
    fun parseFx(json: String, base: String, items: List<String>): Parsed? = runCatching {
        val rates = JSONObject(json).getJSONObject("rates")
        val days = rates.keys().asSequence().sorted().toList()
        if (days.isEmpty()) return null
        fun perEuro(day: String, code: String): Double? =
            if (code == "EUR") 1.0 else rates.getJSONObject(day).optDouble(code, Double.NaN).takeIf { it.isFinite() && it > 0 }
        fun valueOf(day: String, code: String): Double? {
            val inBase = perEuro(day, base) ?: return null
            val unit = perEuro(day, code) ?: return null
            return (inBase / unit).takeIf { it.isFinite() }
        }
        val latest = days.last()
        val before = days.getOrNull(days.size - 2)
        val quotes = items.filter(FinanceItems::isFx).mapNotNull { item ->
            val code = item.removePrefix(FinanceItems.FX)
            val now = valueOf(latest, code) ?: return@mapNotNull null
            val then = before?.let { valueOf(it, code) }
            Quote(item, now, if (then == null) Double.NaN else (now / then - 1.0) * 100.0)
        }
        if (quotes.isEmpty()) null else Parsed(quotes, latest)
    }.getOrNull()

    /** The quotes of the coins among [items] in [base] from the answer of CoinGecko: the price and the change of the last 24 hours. */
    fun parseCoins(json: String, base: String, items: List<String>): List<Quote> = runCatching {
        val root = JSONObject(json)
        val currency = base.lowercase(Locale.ROOT)
        items.filter(FinanceItems::isCoin).mapNotNull { item ->
            val entry = root.optJSONObject(item.removePrefix(FinanceItems.CRYPTO)) ?: return@mapNotNull null
            val price = entry.optDouble(currency, Double.NaN)
            if (!price.isFinite() || price <= 0) return@mapNotNull null
            Quote(item, price, entry.optDouble("${currency}_24h_change", Double.NaN).takeIf { it.isFinite() } ?: Double.NaN)
        }
    }.getOrDefault(emptyList())

    class Parsed(val quotes: List<Quote>, val date: String)

    /** The page of Google Finance for [item] in [base] ("USD-EUR", "BTC-EUR"), which a touch on a quote opens. */
    fun quoteUrl(item: String, base: String): String = "https://www.google.com/finance/quote/${FinanceItems.symbol(item)}-$base"

    /** The quotes in the order of [items]; the ones the sources did not give are left out. */
    fun ordered(items: List<String>, quotes: List<Quote>): List<Quote> = items.mapNotNull { item -> quotes.firstOrNull { it.item == item } }

    /** A value in the way a tile writes it: no more digits than make it useful. */
    fun format(value: Double, locale: Locale = Locale.getDefault()): String {
        val digits = when {
            value >= 1_000 -> 0
            value >= 10 -> 2
            value >= 1 -> 3
            else -> 4
        }
        return java.text.NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = digits
            minimumFractionDigits = if (digits == 0) 0 else minOf(2, digits)
        }.format(value)
    }

    /** "+0.4%" or "-1.2%" for a change; null when it is not known. */
    fun formatChange(percent: Double, locale: Locale = Locale.getDefault()): String? {
        if (!percent.isFinite()) return null
        val text = java.text.NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 2
        }.format(kotlin.math.abs(percent))
        return (if (percent < 0) "-" else "+") + text + "%"
    }

    /** The sign of a change as a step: 1 up, -1 down, 0 still (or not known). A move under a hundredth of a percent is not one. */
    fun trend(percent: Double): Int = when {
        !percent.isFinite() || kotlin.math.abs(percent) < 0.005 -> 0
        percent > 0 -> 1
        else -> -1
    }
}

/** The quotes as JSON, for the copy kept on the phone. */
object FinanceCodec {
    fun encode(data: FinanceData): String = JSONObject().apply {
        put("base", data.base)
        put("date", data.date)
        put("fetchedAt", data.fetchedAt)
        put("key", data.key)
        put(
            "quotes",
            JSONArray().also { array ->
                data.quotes.forEach { quote ->
                    array.put(JSONObject().put("i", quote.item).put("v", quote.value).apply { if (quote.changePercent.isFinite()) put("c", quote.changePercent) })
                }
            },
        )
    }.toString()

    fun decode(json: String?): FinanceData? = runCatching {
        val o = JSONObject(json ?: return null)
        val array = o.getJSONArray("quotes")
        val quotes = List(array.length()) { i ->
            val q = array.getJSONObject(i)
            val item = q.getString("i")
            require(FinanceItems.isValid(item))
            Quote(item, q.getDouble("v"), if (q.has("c")) q.getDouble("c") else Double.NaN)
        }
        FinanceData(o.getString("base"), quotes, o.optString("date"), o.getLong("fetchedAt"), o.optString("key"))
    }.getOrNull()
}

/** Fetches the quotes and keeps the last ones, so the tile has something to show without a connection. */
class FinanceRepository(context: Context) {
    private val prefs = context.getSharedPreferences("metrowin", Context.MODE_PRIVATE)

    fun cached(): FinanceData? = FinanceCodec.decode(prefs.getString(KEY, null))

    /** Blocking, call it off the main thread. The new quotes (and they are kept), or null when nothing could be read. */
    fun fetch(base: String, items: List<String>, now: Long, today: LocalDate = LocalDate.now()): FinanceData? {
        val fx = Finance.fxUrl(base, items, today)?.let { SafeHttp.get(it, setOf(Finance.FX_HOST)) }?.let { Finance.parseFx(it, base, items) }
        val coins = Finance.coinUrl(base, items)?.let { SafeHttp.get(it, setOf(Finance.COIN_HOST)) }?.let { Finance.parseCoins(it, base, items) }.orEmpty()
        val quotes = Finance.ordered(items, fx?.quotes.orEmpty() + coins)
        if (quotes.isEmpty()) return null
        val data = FinanceData(base, quotes, fx?.date.orEmpty(), now, Finance.key(base, items))
        prefs.edit { putString(KEY, FinanceCodec.encode(data)) }
        return data
    }

    private companion object {
        const val KEY = "finance_data"
    }
}

/** The headlines on the phone, kept apart from the settings so that a change of the source discards them. */
class NewsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("metrowin", Context.MODE_PRIVATE)

    fun cached(): NewsData? = NewsCodec.decode(prefs.getString(KEY, null))

    /** Blocking, call it off the main thread. The new headlines (and they are kept), or null when nothing could be read. */
    fun fetch(source: String, custom: String, edition: NewsEdition, now: Long): NewsData? {
        val url = NewsSources.url(source, custom, edition) ?: return null
        val xml = SafeHttp.get(url, NewsSources.hosts(url), maxBytes = MAX_BYTES) ?: return null
        val feed = Feeds.parse(xml) ?: return null
        val data = NewsData(feed.title, feed.items, now, NewsSources.key(source, custom, edition))
        prefs.edit { putString(KEY, NewsCodec.encode(data)) }
        return data
    }

    private companion object {
        const val KEY = "news_data"
        const val MAX_BYTES = 512 * 1024
    }
}
