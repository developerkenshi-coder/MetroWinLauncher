package com.metrowin.launcher.data

import java.math.BigDecimal
import java.math.MathContext
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The calculator of the search box: what is typed is worked out when it is an arithmetic expression. Pure, so it is tested
 * with words and numbers. It reads `12*3+4`, `(2+3)^2`, `15% of 80`, `100+10%`, `sqrt(16)`, `2 x 3` and the decimal comma
 * of the languages that use it.
 */
object Calculator {
    /** The value of [text], or null when it is not a calculation or cannot be worked out (a division by zero, an overflow). */
    fun evaluate(text: String): Double? {
        val cleaned = text.trim().lowercase(Locale.ROOT)
        if (cleaned.isEmpty() || cleaned.length > MAX_LENGTH) return null
        percentOf(cleaned)?.let { return it }
        val tokens = tokenize(normalizeNumbers(cleaned)) ?: return null
        if (!looksLikeCalculation(tokens) || DATE_OR_NUMBER.matches(cleaned)) return null
        return runCatching { Parser(tokens).parse() }.getOrNull()?.takeIf { it.isFinite() && abs(it) < LIMIT }
    }

    /** "15% of 80", "15% di 80": a share of a number. */
    private fun percentOf(text: String): Double? {
        val match = PERCENT_OF.matchEntire(text) ?: return null
        val pct = number(match.groupValues[1]) ?: return null
        val base = evaluate(match.groupValues[2]) ?: number(match.groupValues[2]) ?: return null
        return (pct * base / 100.0).takeIf { it.isFinite() }
    }

    /** A sum, a product, a function: not a bare number, a negative number, or a lone percentage. */
    private fun looksLikeCalculation(tokens: List<Token>): Boolean = tokens.withIndex().any { (i, token) ->
        token is Token.Func || token is Token.Op && i > 0 && tokens[i - 1].let { it is Token.Num || it == Token.Percent || it == Token.Paren(false) }
    }

    private fun number(text: String): Double? = normalizeNumbers(text.trim()).toDoubleOrNull()

    /**
     * Decimal comma or point: with only commas the comma is the decimal separator ("1,5"), with both the point groups the
     * thousands and the comma is the decimal ("1.000,5" and "1,000.5" are both read as one thousand and a half).
     */
    private fun normalizeNumbers(text: String): String = when {
        ',' in text && '.' in text -> {
            val lastComma = text.lastIndexOf(',')
            val lastPoint = text.lastIndexOf('.')
            if (lastComma > lastPoint) text.replace(".", "").replace(',', '.') else text.replace(",", "")
        }
        ',' in text -> text.replace(',', '.')
        else -> text
    }

    private sealed interface Token {
        data class Num(val value: Double) : Token
        data class Op(val symbol: Char) : Token
        data class Func(val name: String) : Token
        data class Paren(val open: Boolean) : Token
        data object Percent : Token
    }

    private fun tokenize(text: String): List<Token>? {
        val out = ArrayList<Token>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    var j = i
                    while (j < text.length && (text[j].isDigit() || text[j] == '.')) j++
                    out += Token.Num(text.substring(i, j).toDoubleOrNull() ?: return null)
                    i = j
                }
                c == '+' || c == '-' || c == '−' || c == '–' -> { out += Token.Op(if (c == '+') '+' else '-'); i++ }
                c == '*' || c == '×' || c == '·' -> { out += Token.Op('*'); i++ }
                c == '/' || c == '÷' -> { out += Token.Op('/'); i++ }
                c == '^' -> { out += Token.Op('^'); i++ }
                // "2 x 3": an x between two operands is a times sign.
                c == 'x' && out.lastOrNull().let { it is Token.Num || it == Token.Percent || it == Token.Paren(false) } -> { out += Token.Op('*'); i++ }
                c == '(' -> { out += Token.Paren(true); i++ }
                c == ')' -> { out += Token.Paren(false); i++ }
                c == '%' -> { out += Token.Percent; i++ }
                c == 'π' -> { out += Token.Num(Math.PI); i++ }
                c.isLetter() -> {
                    var j = i
                    while (j < text.length && text[j].isLetter()) j++
                    val word = text.substring(i, j)
                    when (word) {
                        "pi" -> out += Token.Num(Math.PI)
                        "sqrt", "radice" -> out += Token.Func("sqrt")
                        "abs" -> out += Token.Func("abs")
                        else -> return null
                    }
                    i = j
                }
                else -> return null
            }
        }
        return out
    }

    private class Parser(private val tokens: List<Token>) {
        private var pos = 0

        fun parse(): Double {
            val value = expression().first
            if (pos != tokens.size) error("trailing")
            return value
        }

        private fun peek(): Token? = tokens.getOrNull(pos)

        /** Sum and difference; "100+10%" is a hundred and ten, as on any calculator. */
        private fun expression(): Pair<Double, Boolean> {
            var (left, _) = term()
            while (true) {
                val op = (peek() as? Token.Op)?.symbol?.takeIf { it == '+' || it == '-' } ?: break
                pos++
                val (right, isPercent) = term()
                val amount = if (isPercent) left * right else right
                left = if (op == '+') left + amount else left - amount
            }
            return left to false
        }

        private fun term(): Pair<Double, Boolean> {
            var (left, percent) = power()
            while (true) {
                val op = (peek() as? Token.Op)?.symbol?.takeIf { it == '*' || it == '/' } ?: break
                pos++
                val right = power().first
                left = if (op == '*') left * right else left / right
                percent = false
            }
            return left to percent
        }

        private fun power(): Pair<Double, Boolean> {
            val (base, percent) = unary()
            if ((peek() as? Token.Op)?.symbol == '^') {
                pos++
                return base.pow(power().first) to false
            }
            return base to percent
        }

        private fun unary(): Pair<Double, Boolean> {
            val sign = peek() as? Token.Op
            if (sign != null && (sign.symbol == '-' || sign.symbol == '+')) {
                pos++
                val (value, percent) = unary()
                return (if (sign.symbol == '-') -value else value) to percent
            }
            var (value, percent) = primary()
            while (peek() == Token.Percent) {
                pos++
                value /= 100.0
                percent = true
            }
            return value to percent
        }

        private fun primary(): Pair<Double, Boolean> {
            when (val token = tokens.getOrNull(pos++) ?: error("end")) {
                is Token.Num -> return token.value to false
                is Token.Paren -> {
                    if (!token.open) error("unexpected )")
                    val value = expression().first
                    if (tokens.getOrNull(pos++) != Token.Paren(false)) error("missing )")
                    return value to false
                }
                is Token.Func -> {
                    if (tokens.getOrNull(pos++) != Token.Paren(true)) error("missing (")
                    val argument = expression().first
                    if (tokens.getOrNull(pos++) != Token.Paren(false)) error("missing )")
                    return when (token.name) {
                        "sqrt" -> { if (argument < 0) error("sqrt of a negative") else sqrt(argument) }
                        else -> abs(argument)
                    } to false
                }
                else -> error("unexpected $token")
            }
        }
    }

    /** [value] as it is written in [locale]: up to ten digits, no trailing zeros, no grouping. */
    fun format(value: Double, locale: Locale): String {
        val plain = BigDecimal(value).round(MathContext(SIGNIFICANT)).stripTrailingZeros().toPlainString()
        val symbols = DecimalFormatSymbols.getInstance(locale)
        return plain.replace('.', symbols.decimalSeparator).let { if (it == "-0") "0" else it }
    }

    /** 2024-05-12 and 12/05/2024 are dates, not subtractions. */
    private val DATE_OR_NUMBER = Regex("""^\d{1,4}[-/.]\d{1,2}[-/.]\d{1,4}$""")
    private val PERCENT_OF = Regex("""^([\d.,]+)\s*%\s*(?:of|di|del|della|dei|delle|de)\s+(.+)$""")
    private const val MAX_LENGTH = 120
    private const val SIGNIFICANT = 10
    private const val LIMIT = 1e15
}
