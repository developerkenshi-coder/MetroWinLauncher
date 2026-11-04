package com.metrowin.launcher.data

/**
 * What the keys of the calculator app do to the expression being typed. Pure (no Android), so every key is tested. The expression
 * is kept with the plain point and the signs `× ÷ −`; the screen turns the point into the one of the language.
 */
class CalcSession(
    var angle: CalcEngine.Angle = CalcEngine.Angle.Degrees,
    var memory: Double = 0.0,
    history: List<Entry> = emptyList(),
) {
    /** One line of the history: the expression and its result, as they were shown. */
    data class Entry(val expression: String, val result: String)

    /** What the screen shows, as it is now: a new value at every key, so a screen that holds it is drawn again. */
    data class View(
        val expression: String,
        val finished: Boolean,
        val failure: CalcEngine.Error?,
        val lastExpression: String,
        val preview: Double?,
        val memory: Double,
        val angle: CalcEngine.Angle,
        val history: List<Entry>,
    )

    fun view() = View(expression, finished, failure, lastExpression, preview, memory, angle, entries.toList())

    var expression: String = ""
        private set

    /** The number the last "=" gave: the expression is that number, and a digit typed now starts a new calculation. */
    var finished: Boolean = false
        private set

    var failure: CalcEngine.Error? = null
        private set

    /** The expression that "=" worked out last, for the line above the result. */
    var lastExpression: String = ""
        private set

    var ans: Double = 0.0
        private set

    private val entries = ArrayList(history)
    val history: List<Entry> get() = entries

    /** What the expression is worth now, while it is typed. Null while it is empty or incomplete. */
    val preview: Double?
        get() = (CalcEngine.evaluate(closed(expression), angle, ans) as? CalcEngine.Result.Value)?.number

    private fun closed(text: String): String {
        val open = text.count { it == '(' } - text.count { it == ')' }
        return text.trimEnd(*OPERATORS) + ")".repeat(open.coerceAtLeast(0))
    }

    private fun fresh() {
        if (finished) {
            expression = ""
            finished = false
        }
        failure = null
    }

    fun digit(d: Char) {
        require(d in '0'..'9')
        fresh()
        val number = currentNumber()
        // 007 is 7, but 0.07 stays.
        expression = if (number == "0") expression.dropLast(1) + d else expression + d
    }

    fun point() {
        fresh()
        val number = currentNumber()
        if ('.' in number) return
        expression += if (number.isEmpty()) "0." else "."
    }

    fun operator(op: Char) {
        require(op in OPERATORS)
        failure = null
        if (finished) finished = false
        if (expression.isEmpty()) {
            expression = if (op == '−') "−" else "0$op"
            return
        }
        val last = expression.last()
        expression = when {
            last in OPERATORS && expression.length == 1 -> if (op == '−') expression else "0$op"
            // After × ÷ ^ or an opening bracket a minus is a sign: 5×−3.
            last in OPERATORS && op == '−' && last in "×÷^" -> expression + op
            last in OPERATORS -> expression.dropLastWhile { it in OPERATORS } + op
            last == '(' -> if (op == '−') expression + op else expression
            else -> expression + op
        }
    }

    fun openBracket() {
        fresh()
        expression += "("
    }

    fun closeBracket() {
        if (finished) return
        val open = expression.count { it == '(' } - expression.count { it == ')' }
        val last = expression.lastOrNull() ?: return
        if (open > 0 && last !in OPERATORS && last != '(') expression += ")"
    }

    /** A function of the keypad: "sin(", "√(", "ln(". */
    fun function(name: String) {
        fresh()
        expression += "$name("
    }

    fun constant(symbol: String) {
        fresh()
        expression += symbol
    }

    /** "%" and "!" go after a number or a bracket. */
    fun postfix(symbol: Char) {
        if (failure != null) return
        val last = expression.lastOrNull() ?: return
        if (last.isDigit() || last == ')' || last == '%' || last == '!' || last == 'π' || last == 'e') {
            finished = false
            expression += symbol
        }
    }

    /** "x²", "1/x", "xʸ": the exponent is typed with the keys. */
    fun power(exponent: String) {
        failure = null
        finished = false
        if (expression.isEmpty()) return
        expression += "^$exponent"
    }

    /** "10ˣ": the exponent is typed with the keys. */
    fun tenPower() {
        fresh()
        expression += "10^"
    }

    fun reciprocal() {
        if (expression.isEmpty()) return
        val whole = closed(expression)
        failure = null
        finished = false
        expression = "1÷($whole"
    }

    /** ± : the sign of the number at the end flips; a bracket is opened for it when it follows an operator. */
    fun negate() {
        failure = null
        if (expression.isEmpty()) {
            expression = "−"
            return
        }
        // A result is a whole number (it may be written 1.5E+20): its sign flips as a whole.
        if (finished) {
            expression = if (expression.startsWith("−")) expression.drop(1) else "−$expression"
            return
        }
        if (expression.last().isDigit() || expression.last() == '.') {
            val number = currentNumber()
            val before = expression.dropLast(number.length)
            expression = when {
                before.endsWith("(−") -> before.dropLast(2) + number
                before == "−" -> number
                before.isEmpty() -> "−$number"
                else -> "$before(−$number"
            }
        } else if (expression == "−") {
            expression = ""
        }
    }

    fun backspace() {
        failure = null
        if (finished) {
            finished = false
        }
        if (expression.isEmpty()) return
        val token = TOKENS.firstOrNull { expression.endsWith(it) }
        expression = expression.dropLast(token?.length ?: 1)
    }

    fun clear() {
        expression = ""
        finished = false
        failure = null
    }

    fun clearHistory() {
        entries.clear()
    }

    /** "=": the result becomes the expression. False when it cannot be worked out (the reason is [failure]). */
    fun equals(): Boolean {
        if (expression.isEmpty() || finished) return false
        val source = closed(expression)
        if (source.isEmpty() || source.last() in OPERATORS) {
            failure = CalcEngine.Error.Syntax
            return false
        }
        return when (val result = CalcEngine.evaluate(source, angle, ans)) {
            is CalcEngine.Result.Value -> {
                val text = CalcEngine.format(result.number)
                entries.add(0, Entry(source, text))
                while (entries.size > MAX_HISTORY) entries.removeAt(entries.lastIndex)
                lastExpression = source
                ans = result.number
                expression = text.replace('-', '−')
                finished = true
                failure = null
                true
            }
            is CalcEngine.Result.Failed -> {
                failure = result.error
                false
            }
        }
    }

    /** The value of what is on the screen: the preview, or the result just given. */
    fun value(): Double? = preview

    fun memoryClear() { memory = 0.0 }

    fun memoryAdd(sign: Int) {
        val value = value() ?: return
        memory += sign * value
    }

    fun memoryRecall() {
        fresh()
        val text = CalcEngine.format(memory).replace('-', '−')
        // A recalled number follows an operator, or takes the place of the number being typed.
        val number = currentNumber()
        expression = expression.dropLast(number.length) + text
    }

    /** A line of the history is taken back as the number to go on from. */
    fun recall(entry: Entry) {
        failure = null
        expression = entry.result.replace('-', '−')
        finished = true
    }

    fun toggleAngle() {
        angle = if (angle == CalcEngine.Angle.Degrees) CalcEngine.Angle.Radians else CalcEngine.Angle.Degrees
    }

    /** The digits (and the point) at the end of the expression: what the next digit or point is added to. */
    private fun currentNumber(): String = expression.takeLastWhile { it.isDigit() || it == '.' }

    companion object {
        private val OPERATORS = charArrayOf('+', '−', '×', '÷', '^')
        const val MAX_HISTORY = 100

        /** What one press of backspace takes away: a whole function name and its bracket. */
        private val TOKENS = CalcEngine.functions.map { "$it(" } + "Ans"
    }
}
