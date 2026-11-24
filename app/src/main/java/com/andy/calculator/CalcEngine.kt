package com.andy.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.acosh
import kotlin.math.asin
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.atanh
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

const val MSG_INVALID = "Invalid format used."
const val MSG_DIV0 = "Can't divide by 0."
const val MSG_RANGE = "Value too large."

class CalcException(val userMessage: String) : Exception(userMessage)

/**
 * Tokens are the units the keypad inserts: single digits, ".", operators, "(", ")",
 * functions that carry their own open bracket ("sin(", "√(", "^(" ...), "(−" for a
 * negated group, constants and postfix "%" / "!". A result in E notation is one
 * literal token such as "1.5E20".
 */
object Tok {
    val OPS = setOf("+", "−", "×", "÷")
    val POSTFIX = setOf("%", "!")
    val CONSTANTS = setOf("π", "e")

    fun isDigitOrDot(t: String) = t.length == 1 && (t[0].isDigit() || t[0] == '.')
    fun isLiteral(t: String) = t.length > 1 && t[0].isDigit()
    fun isNumberPart(t: String) = isDigitOrDot(t) || isLiteral(t)
    fun isOp(t: String) = t in OPS

    /** Tokens that open a bracket: "(", "(−", "^(" and every function. */
    fun opensBracket(t: String) = t.endsWith("(") || t == "(−"
    fun isFunction(t: String) = t.endsWith("(") && t != "(" && t != "^(" && t != "(−"

    /** A token after which a value has just ended. */
    fun endsValue(t: String?) = t != null &&
        (isNumberPart(t) || t == ")" || t in CONSTANTS || t in POSTFIX)

    /** A token that starts a new value. */
    fun startsValue(t: String?) = t != null &&
        (isNumberPart(t) || t in CONSTANTS || t == "(" || t == "(−" || isFunction(t))
}

object CalcEngine {

    fun evaluate(tokens: List<String>, degrees: Boolean): Double {
        // "(−" is shorthand for a bracket followed by a minus sign.
        val expanded = ArrayList<String>(tokens.size + 4)
        for (t in tokens) {
            if (t == "(−") { expanded.add("("); expanded.add("−") } else expanded.add(t)
        }
        val v = Parser(expanded, degrees).parse()
        if (v.isNaN()) throw CalcException(MSG_INVALID)
        if (v.isInfinite() || abs(v) > 1e300) throw CalcException(MSG_RANGE)
        return v
    }

    /** Number shown as a result: at most 15 significant digits and 10 decimals. */
    fun format(v: Double): String {
        if (v == 0.0 || v.isNaN()) return "0"
        var bd = BigDecimal(v).round(MathContext(15, RoundingMode.HALF_UP))
        val a = bd.abs()
        if (a >= BigDecimal("1E15") || a < BigDecimal("1E-10")) {
            val s = String.format(Locale.US, "%.9E", v) // 1.234567890E+20
            val mant = s.substringBefore('E').trimEnd('0').trimEnd('.')
            val exp = s.substringAfter('E').toInt()
            return "${mant}E$exp"
        }
        if (bd.scale() > 10) bd = bd.setScale(10, RoundingMode.HALF_UP)
        bd = bd.stripTrailingZeros()
        val plain = bd.toPlainString()
        return if (plain == "-0") "0" else plain
    }

    /** Adds thousands separators and a real minus sign to a formatted number. */
    fun group(s: String): String {
        if (s.contains('E')) return s.replace("-", "−")
        val neg = s.startsWith("-")
        val body = if (neg) s.substring(1) else s
        val intPart = body.substringBefore('.')
        val frac = if (body.contains('.')) "." + body.substringAfter('.') else ""
        val sb = StringBuilder()
        for ((i, c) in intPart.withIndex()) {
            if (i > 0 && (intPart.length - i) % 3 == 0) sb.append(',')
            sb.append(c)
        }
        return (if (neg) "−" else "") + sb + frac
    }

    /** Turns a formatted result back into tokens so the user can keep calculating. */
    fun resultTokens(formatted: String): List<String> {
        val out = ArrayList<String>()
        var s = formatted
        if (s.startsWith("-")) { out.add("−"); s = s.substring(1) }
        if (s.contains('E')) out.add(s) else s.forEach { out.add(it.toString()) }
        return out
    }

    private class Parser(private val t: List<String>, private val degrees: Boolean) {
        private var pos = 0
        private fun peek(): String? = t.getOrNull(pos)

        fun parse(): Double {
            if (t.isEmpty()) throw CalcException(MSG_INVALID)
            val v = expr()
            if (pos < t.size) throw CalcException(MSG_INVALID)
            return v
        }

        private fun expr(): Double {
            var left = term().first
            while (true) {
                val op = peek()
                if (op == "+" || op == "−") {
                    pos++
                    val (right, isPercent) = term()
                    // 200 + 10% = 220, like a desk calculator
                    val r = if (isPercent) left * right else right
                    left = if (op == "+") left + r else left - r
                } else break
            }
            return left
        }

        /** Returns the value and whether the whole term was a single "n%". */
        private fun term(): Pair<Double, Boolean> {
            val first = unary()
            var v = first.first
            var count = 1
            while (true) {
                val op = peek()
                if (op == "×" || op == "÷") {
                    pos++
                    val r = unary().first
                    if (op == "÷") {
                        if (r == 0.0) throw CalcException(MSG_DIV0)
                        v /= r
                    } else v *= r
                    count++
                } else if (Tok.startsValue(op)) {
                    v *= unary().first // implied multiplication: 2π, 3(4)
                    count++
                } else break
            }
            return v to (first.second && count == 1)
        }

        private fun unary(): Pair<Double, Boolean> {
            val p = peek()
            if (p == "−") {
                pos++
                val (v, pct) = unary()
                return -v to pct
            }
            if (p == "+") {
                pos++
                return unary()
            }
            return power()
        }

        private fun power(): Pair<Double, Boolean> {
            val (base, pct) = postfix(primary())
            if (peek() == "^(") {
                val e = exponentChain()
                return postfix(checkedPow(base, e))
            }
            return base to pct
        }

        /** Parses "^(a)^(b)..." right to left, so 2^(3)^(2) = 2^9. */
        private fun exponentChain(): Double {
            pos++ // ^(
            val e = expr()
            closeBracket()
            return if (peek() == "^(") checkedPow(e, exponentChain()) else e
        }

        private fun postfix(start: Double): Pair<Double, Boolean> {
            var v = start
            var pct = false
            while (true) {
                when (peek()) {
                    "%" -> { pos++; v /= 100.0; pct = true }
                    "!" -> { pos++; v = factorial(v); pct = false }
                    else -> return v to pct
                }
            }
        }

        private fun closeBracket() {
            when (peek()) {
                ")" -> pos++
                null -> Unit // brackets left open are closed automatically
                else -> throw CalcException(MSG_INVALID)
            }
        }

        private fun primary(): Double {
            val tok = peek() ?: throw CalcException(MSG_INVALID)
            return when {
                Tok.isLiteral(tok) -> { pos++; tok.toDoubleOrNull() ?: throw CalcException(MSG_INVALID) }
                Tok.isDigitOrDot(tok) -> number()
                tok == "π" -> { pos++; PI }
                tok == "e" -> { pos++; E }
                tok == "(" -> {
                    pos++
                    if (peek() == ")") throw CalcException(MSG_INVALID)
                    val v = expr()
                    closeBracket()
                    v
                }
                Tok.isFunction(tok) -> {
                    pos++
                    if (peek() == ")" || peek() == null) throw CalcException(MSG_INVALID)
                    val a = expr()
                    closeBracket()
                    applyFunction(tok.dropLast(1), a)
                }
                else -> throw CalcException(MSG_INVALID)
            }
        }

        private fun number(): Double {
            val sb = StringBuilder()
            while (true) {
                val p = peek() ?: break
                if (!Tok.isDigitOrDot(p)) break
                sb.append(p)
                pos++
            }
            val s = sb.toString()
            if (s == "." || s.count { it == '.' } > 1) throw CalcException(MSG_INVALID)
            return s.toDouble()
        }

        private fun toRad(a: Double) = if (degrees) Math.toRadians(a) else a
        private fun fromRad(a: Double) = if (degrees) Math.toDegrees(a) else a

        private fun clean(v: Double) = if (abs(v) < 1e-14) 0.0 else v

        private fun applyFunction(name: String, a: Double): Double {
            val r = when (name) {
                "sin" -> clean(sin(toRad(a)))
                "cos" -> clean(cos(toRad(a)))
                "tan" -> {
                    if (degrees && abs(a % 180.0) == 90.0) throw CalcException(MSG_INVALID)
                    clean(tan(toRad(a)))
                }
                "sin⁻¹" -> { domain(a in -1.0..1.0); fromRad(asin(a)) }
                "cos⁻¹" -> { domain(a in -1.0..1.0); fromRad(acos(a)) }
                "tan⁻¹" -> fromRad(atan(a))
                "sinh" -> sinh(a)
                "cosh" -> cosh(a)
                "tanh" -> tanh(a)
                "sinh⁻¹" -> asinh(a)
                "cosh⁻¹" -> { domain(a >= 1.0); acosh(a) }
                "tanh⁻¹" -> { domain(a > -1.0 && a < 1.0); atanh(a) }
                "ln" -> { domain(a > 0.0); ln(a) }
                "log" -> { domain(a > 0.0); log10(a) }
                "√" -> { domain(a >= 0.0); sqrt(a) }
                "∛" -> cbrt(a)
                "abs" -> abs(a)
                "e^" -> exp(a)
                "2^" -> 2.0.pow(a)
                else -> throw CalcException(MSG_INVALID)
            }
            if (r.isNaN()) throw CalcException(MSG_INVALID)
            if (r.isInfinite()) throw CalcException(MSG_RANGE)
            return r
        }

        private fun domain(ok: Boolean) {
            if (!ok) throw CalcException(MSG_INVALID)
        }

        private fun checkedPow(b: Double, e: Double): Double {
            if (b == 0.0 && e < 0) throw CalcException(MSG_DIV0)
            val r = b.pow(e)
            if (r.isNaN()) throw CalcException(MSG_INVALID)
            if (r.isInfinite()) throw CalcException(MSG_RANGE)
            return r
        }

        private fun factorial(v: Double): Double {
            if (v < 0 || v != floor(v)) throw CalcException(MSG_INVALID)
            if (v > 170) throw CalcException(MSG_RANGE)
            var r = 1.0
            var i = 2
            while (i <= v.toInt()) { r *= i; i++ }
            return r
        }
    }
}
