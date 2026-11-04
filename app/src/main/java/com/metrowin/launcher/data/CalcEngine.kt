package com.metrowin.launcher.data

import java.math.BigDecimal
import java.math.MathContext
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

/**
 * The engine of the calculator app: a typed expression in, a number or the kind of error out. Pure, so it is tested with words and
 * numbers. Unlike [Calculator] (the search box, which refuses a bare number and rounds to ten digits) it works out everything a
 * scientific calculator does: `+ − × ÷ ^ mod`, `%` and `!`, brackets (left open they are closed), the constants `π`, `e` and `Ans`,
 * `sqrt ∛ abs exp ln log sin cos tan asin acos atan sinh cosh tanh`, and a product written without a sign (`2π`, `3(4+1)`).
 */
object CalcEngine {
    enum class Angle { Degrees, Radians }

    enum class Error { Syntax, Domain, Overflow }

    sealed interface Result {
        data class Value(val number: Double) : Result
        data class Failed(val error: Error) : Result
    }

    /** What the keypad types for a function: the engine reads these names. */
    val functions = listOf(
        "asin", "acos", "atan", "sinh", "cosh", "tanh", "sqrt", "sin", "cos", "tan", "abs", "exp", "ln", "log", "∛", "√",
    )

    fun evaluate(expression: String, angle: Angle = Angle.Degrees, ans: Double = 0.0): Result {
        val text = expression.trim()
        if (text.isEmpty() || text.length > MAX_LENGTH) return Result.Failed(Error.Syntax)
        return try {
            val value = Parser(tokenize(text), angle, ans).parse()
            when {
                value.isNaN() -> Result.Failed(Error.Domain)
                value.isInfinite() -> Result.Failed(Error.Overflow)
                else -> Result.Value(value)
            }
        } catch (e: CalcException) {
            Result.Failed(e.error)
        } catch (e: StackOverflowError) {
            Result.Failed(Error.Syntax)
        }
    }

    /**
     * [value] with up to 15 significant digits and no trailing zeros ("0.1+0.2" is 0.3), in scientific notation when it is very
     * large or very small. The point is the point of [locale] only when [locale] is given: the keypad works with the plain point.
     */
    fun format(value: Double, locale: Locale? = null): String {
        if (value == 0.0) return "0"
        val digits = BigDecimal(value).round(MathContext(SIGNIFICANT)).stripTrailingZeros()
        val exponent = digits.precision() - digits.scale() - 1
        val plain = if (exponent >= SCIENTIFIC_UP || exponent <= SCIENTIFIC_DOWN) {
            val mantissa = digits.unscaledValue().abs().toString()
            val head = mantissa.first() + if (mantissa.length > 1) "." + mantissa.drop(1) else ""
            (if (value < 0) "-" else "") + head + "E" + (if (exponent < 0) "-" else "+") + abs(exponent)
        } else {
            digits.toPlainString()
        }
        return if (locale == null) plain else plain.replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)
    }

    private class CalcException(val error: Error) : RuntimeException(null, null, false, false)

    private fun fail(error: Error): Nothing = throw CalcException(error)

    private sealed interface Token {
        data class Num(val value: Double) : Token
        data class Op(val symbol: Char) : Token
        data class Func(val name: String) : Token
        data class Const(val name: String) : Token
        data object Open : Token
        data object Close : Token
        data object Percent : Token
        data object Factorial : Token
        data object Mod : Token
    }

    private fun tokenize(text: String): List<Token> {
        val out = ArrayList<Token>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    var j = i
                    var point = false
                    while (j < text.length && (text[j].isDigit() || text[j] == '.')) {
                        if (text[j] == '.') { if (point) fail(Error.Syntax); point = true }
                        j++
                    }
                    var number = text.substring(i, j).toDoubleOrNull() ?: fail(Error.Syntax)
                    // 1E5, 1.5E-3: the form the display uses for a very large or very small result.
                    if (j < text.length && (text[j] == 'E') && j + 1 < text.length) {
                        var k = j + 1
                        if (text[k] == '+' || text[k] == '-' || text[k] == '−') k++
                        val start = k
                        while (k < text.length && text[k].isDigit()) k++
                        if (k > start) {
                            number = text.substring(i, k).replace('−', '-').toDoubleOrNull() ?: fail(Error.Syntax)
                            j = k
                        }
                    }
                    out += Token.Num(number)
                    i = j
                }
                c == '+' -> { out += Token.Op('+'); i++ }
                c == '-' || c == '−' || c == '–' -> { out += Token.Op('-'); i++ }
                c == '*' || c == '×' || c == '·' -> { out += Token.Op('*'); i++ }
                c == '/' || c == '÷' -> { out += Token.Op('/'); i++ }
                c == '^' -> { out += Token.Op('^'); i++ }
                c == '(' -> { out += Token.Open; i++ }
                c == ')' -> { out += Token.Close; i++ }
                c == '%' -> { out += Token.Percent; i++ }
                c == '!' -> { out += Token.Factorial; i++ }
                c == 'π' -> { out += Token.Const("pi"); i++ }
                c == '√' || c == '∛' -> { out += Token.Func(c.toString()); i++ }
                c.isLetter() -> {
                    var j = i
                    while (j < text.length && text[j].isLetter()) j++
                    val word = text.substring(i, j)
                    val lower = word.lowercase(Locale.ROOT)
                    when {
                        lower == "pi" -> out += Token.Const("pi")
                        word == "e" -> out += Token.Const("e")
                        lower == "ans" -> out += Token.Const("ans")
                        lower == "mod" -> out += Token.Mod
                        lower in functions -> out += Token.Func(lower)
                        else -> fail(Error.Syntax)
                    }
                    i = j
                }
                else -> fail(Error.Syntax)
            }
        }
        return out
    }

    private class Parser(private val tokens: List<Token>, private val angle: Angle, private val ans: Double) {
        private var pos = 0
        private var depth = 0

        fun parse(): Double {
            val value = expression().value
            if (pos != tokens.size) fail(Error.Syntax)
            return value
        }

        /** A number and whether it was written as a percentage ("10%"): "100+10%" adds a tenth of 100, as on any calculator. */
        private class Part(val value: Double, val percent: Boolean = false)

        private fun peek(): Token? = tokens.getOrNull(pos)

        private fun expression(): Part {
            var left = term()
            while (true) {
                val op = (peek() as? Token.Op)?.symbol?.takeIf { it == '+' || it == '-' } ?: break
                pos++
                val right = term()
                val amount = if (right.percent) left.value * right.value else right.value
                val sum = if (op == '+') left.value + amount else left.value - amount
                // 0.1+0.2-0.3 is nothing, not 5.5E-17: what is left of two equal numbers is only the error of the arithmetic.
                left = Part(if (abs(sum) < ROUNDING * maxOf(abs(left.value), abs(amount))) 0.0 else sum)
            }
            return left
        }

        private fun term(): Part {
            var left = unary()
            while (true) {
                val next = peek()
                val op = (next as? Token.Op)?.symbol?.takeIf { it == '*' || it == '/' }
                when {
                    op != null -> {
                        pos++
                        val right = unary().value
                        if (op == '/' && right == 0.0) fail(Error.Domain)
                        left = Part(if (op == '*') left.value * right else left.value / right)
                    }
                    next == Token.Mod -> {
                        pos++
                        val right = unary().value
                        if (right == 0.0) fail(Error.Domain)
                        left = Part(left.value - right * floor(left.value / right))
                    }
                    // 2π, 3(4+1), (1+2)(3+4): a product with no sign.
                    next is Token.Num || next is Token.Const || next is Token.Func || next == Token.Open -> left = Part(left.value * unary().value)
                    else -> break
                }
            }
            return left
        }

        private fun unary(): Part {
            val sign = peek() as? Token.Op
            if (sign != null && (sign.symbol == '-' || sign.symbol == '+')) {
                pos++
                val part = unary()
                return Part(if (sign.symbol == '-') -part.value else part.value, part.percent)
            }
            return power()
        }

        private fun power(): Part {
            val base = postfix()
            if ((peek() as? Token.Op)?.symbol == '^') {
                pos++
                if (++depth > MAX_DEPTH) fail(Error.Syntax)
                // Right to left, and the exponent may carry a sign: 2^-3, 2^3^2.
                val exponent = signedPower()
                depth--
                return Part(raise(base.value, exponent.value))
            }
            return base
        }

        private fun signedPower(): Part {
            val sign = peek() as? Token.Op
            if (sign != null && (sign.symbol == '-' || sign.symbol == '+')) {
                pos++
                val part = signedPower()
                return Part(if (sign.symbol == '-') -part.value else part.value)
            }
            return power()
        }

        private fun raise(base: Double, exponent: Double): Double {
            if (base == 0.0 && exponent < 0) fail(Error.Domain)
            if (base < 0 && exponent != floor(exponent)) fail(Error.Domain)
            return base.pow(exponent)
        }

        private fun postfix(): Part {
            var part = primary()
            while (true) {
                when (peek()) {
                    Token.Percent -> { pos++; part = Part(part.value / 100.0, percent = true) }
                    Token.Factorial -> { pos++; part = Part(factorial(part.value)) }
                    else -> return part
                }
            }
        }

        private fun factorial(x: Double): Double {
            if (x < 0 || x != floor(x) || x > MAX_FACTORIAL) fail(if (x > MAX_FACTORIAL) Error.Overflow else Error.Domain)
            var result = 1.0
            for (k in 2..x.toInt()) result *= k
            return result
        }

        private fun primary(): Part {
            when (val token = tokens.getOrNull(pos++) ?: fail(Error.Syntax)) {
                is Token.Num -> return Part(token.value)
                is Token.Const -> return Part(
                    when (token.name) {
                        "pi" -> Math.PI
                        "e" -> Math.E
                        else -> ans
                    },
                )
                Token.Open -> {
                    if (++depth > MAX_DEPTH) fail(Error.Syntax)
                    val value = if (peek() == null) fail(Error.Syntax) else expression().value
                    // A bracket left open is closed at the end, as the keypad shows.
                    if (peek() == Token.Close) pos++ else if (peek() != null) fail(Error.Syntax)
                    depth--
                    return Part(value)
                }
                is Token.Func -> {
                    val argument = if (peek() == Token.Open) {
                        pos++
                        if (++depth > MAX_DEPTH) fail(Error.Syntax)
                        val value = if (peek() == null) fail(Error.Syntax) else expression().value
                        if (peek() == Token.Close) pos++ else if (peek() != null) fail(Error.Syntax)
                        depth--
                        value
                    } else {
                        // "sqrt 16", "sin 30": the function of the number that follows.
                        postfix().value
                    }
                    return Part(apply(token.name, argument))
                }
                else -> fail(Error.Syntax)
            }
        }

        private fun toRadians(x: Double) = if (angle == Angle.Degrees) Math.toRadians(x) else x

        /** sin(180°) is 1.2E-16 in floating point, and 0 for whoever pressed the keys. */
        private fun Double.snapped() = if (abs(this) < TINY) 0.0 else this

        private fun fromRadians(x: Double) = if (angle == Angle.Degrees) Math.toDegrees(x) else x

        private fun apply(name: String, x: Double): Double = when (name) {
            "sqrt", "√" -> if (x < 0) fail(Error.Domain) else sqrt(x)
            "∛" -> cbrt(x)
            "abs" -> abs(x)
            "exp" -> exp(x)
            "ln" -> if (x <= 0) fail(Error.Domain) else ln(x)
            "log" -> if (x <= 0) fail(Error.Domain) else log10(x)
            "sin" -> sin(toRadians(x)).snapped()
            "cos" -> cos(toRadians(x)).snapped()
            "tan" -> {
                // tan has no value where cos is nothing: 90° and every odd multiple of it.
                val quarter = if (angle == Angle.Degrees) x / 90.0 else x / (Math.PI / 2)
                val odd = quarter - 2 * floor(quarter / 2)
                if (abs(odd - 1.0) < 1e-12) fail(Error.Domain)
                tan(toRadians(x)).snapped()
            }
            "asin" -> if (x < -1 || x > 1) fail(Error.Domain) else fromRadians(asin(x))
            "acos" -> if (x < -1 || x > 1) fail(Error.Domain) else fromRadians(acos(x))
            "atan" -> fromRadians(atan(x))
            "sinh" -> sinh(x)
            "cosh" -> cosh(x)
            "tanh" -> tanh(x)
            else -> fail(Error.Syntax)
        }
    }

    private const val MAX_LENGTH = 400
    private const val MAX_DEPTH = 60
    private const val MAX_FACTORIAL = 170.0
    private const val SIGNIFICANT = 15
    private const val SCIENTIFIC_UP = 16
    private const val SCIENTIFIC_DOWN = -10
    private const val TINY = 1e-15
    private const val ROUNDING = 1e-14
}
