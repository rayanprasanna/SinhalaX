package dev.valhalla.sinhalax.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Small, safe calculator for the keyboard: + − × ÷, %, brackets, decimals and leading minus.
 * Accepts both the keyboard symbols (× ÷ −) and ASCII (* / -). Uses BigDecimal, so
 * 0.1 + 0.2 = 0.3 (not 0.30000000000000004).
 *
 * `%` after a number means "per cent of": 50% = 0.5, and 200 + 10% = 220 (like phone calculators).
 */
object Calculator {

    private val MC = MathContext(16, RoundingMode.HALF_UP)

    /** The result, or null if the expression is incomplete or invalid (or divides by zero). */
    fun evaluate(expression: String): BigDecimal? {
        val tokens = tokenize(expression) ?: return null
        if (tokens.isEmpty()) return null
        return runCatching {
            val p = Parser(tokens)
            val v = p.expression()
            if (!p.atEnd()) null else v
        }.getOrNull()
    }

    /** "1000", "0.5", "-3.25", "1.23456789E+20" for huge numbers. */
    fun format(value: BigDecimal): String {
        val v = value.round(MathContext(12, RoundingMode.HALF_UP)).stripTrailingZeros()
        if (v.signum() == 0) return "0"
        return if (v.abs() >= BigDecimal("1e15") || (v.abs() < BigDecimal("1e-9"))) v.toString() else v.toPlainString()
    }

    /** evaluate + format, or null. */
    fun result(expression: String): String? = evaluate(expression)?.let(::format)

    // ------------------------------------------------------------------ tokens

    private sealed interface Tok {
        data class Num(val v: BigDecimal) : Tok
        data class Op(val c: Char) : Tok // + - * /
        data object Percent : Tok
        data object Open : Tok
        data object Close : Tok
    }

    private fun tokenize(input: String): List<Tok>? {
        val s = input.replace(",", "") // thousands separators: 1,000 = 1000
        val out = mutableListOf<Tok>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                    val text = s.substring(start, i)
                    if (text.count { it == '.' } > 1 || text == ".") return null
                    out += Tok.Num(BigDecimal(if (text.startsWith(".")) "0$text" else text))
                }
                c == '+' -> { out += Tok.Op('+'); i++ }
                c == '-' || c == '−' -> { out += Tok.Op('-'); i++ }
                c == '*' || c == '×' || c == 'x' || c == 'X' -> { out += Tok.Op('*'); i++ }
                c == '/' || c == '÷' -> { out += Tok.Op('/'); i++ }
                c == '%' -> { out += Tok.Percent; i++ }
                c == '(' -> { out += Tok.Open; i++ }
                c == ')' -> { out += Tok.Close; i++ }
                else -> return null
            }
        }
        return out
    }

    // ------------------------------------------------------------------ parser

    private class Parser(private val t: List<Tok>) {
        private var i = 0

        fun atEnd() = i >= t.size

        private fun peek(): Tok? = t.getOrNull(i)

        // expression := term (('+' | '-') term)*
        fun expression(): BigDecimal {
            var left = term()
            while (true) {
                val op = peek() as? Tok.Op ?: break
                if (op.c != '+' && op.c != '-') break
                i++
                val right = term()
                // "200 + 10%" adds 10% of 200 (right was a percentage of the left side).
                val rightValue = if (lastWasPercent) left.multiply(right, MC) else right
                left = if (op.c == '+') left.add(rightValue, MC) else left.subtract(rightValue, MC)
            }
            return left
        }

        private var lastWasPercent = false

        // term := factor (('*' | '/') factor)*
        private fun term(): BigDecimal {
            var left = factor()
            while (true) {
                val op = peek() as? Tok.Op ?: break
                if (op.c != '*' && op.c != '/') break
                i++
                val right = factor()
                left = if (op.c == '*') {
                    left.multiply(right, MC)
                } else {
                    if (right.signum() == 0) error("divide by zero")
                    left.divide(right, MC)
                }
                lastWasPercent = false
            }
            return left
        }

        // factor := '-' factor | '+' factor | primary '%'?
        private fun factor(): BigDecimal {
            val tok = peek()
            if (tok is Tok.Op && (tok.c == '-' || tok.c == '+')) {
                i++
                val v = factor()
                return if (tok.c == '-') v.negate() else v
            }
            var v = primary()
            lastWasPercent = false
            if (peek() == Tok.Percent) {
                i++
                v = v.divide(BigDecimal(100), MC)
                lastWasPercent = true
            }
            return v
        }

        private fun primary(): BigDecimal = when (val tok = peek()) {
            is Tok.Num -> { i++; tok.v }
            Tok.Open -> {
                i++
                val v = expression()
                if (peek() != Tok.Close) error("missing )")
                i++
                v
            }
            else -> error("number expected")
        }
    }
}
