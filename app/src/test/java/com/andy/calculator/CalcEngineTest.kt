package com.andy.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CalcEngineTest {
    /** Splits an expression the way the keypad would enter it. */
    private fun toks(s: String): List<String> {
        val multi = listOf(
            "sin⁻¹(", "cos⁻¹(", "tan⁻¹(", "sinh⁻¹(", "cosh⁻¹(", "tanh⁻¹(", "sinh(", "cosh(", "tanh(",
            "sin(", "cos(", "tan(", "ln(", "log(", "√(", "∛(", "abs(", "e^(", "^(", "(−"
        )
        val out = ArrayList<String>()
        var i = 0
        while (i < s.length) {
            val m = multi.firstOrNull { s.startsWith(it, i) }
            if (m != null) {
                out.add(m); i += m.length
            } else {
                out.add(s[i].toString()); i++
            }
        }
        return out
    }

    private fun calc(s: String, deg: Boolean = true) = CalcEngine.format(CalcEngine.evaluate(toks(s), deg))

    @Test fun basics() {
        assertEquals("3", calc("1+2"))
        assertEquals("0.3", calc("0.1+0.2"))
        assertEquals("14", calc("2+3×4"))
        assertEquals("20", calc("(2+3)×4"))
        assertEquals("0.3333333333", calc("1÷3"))
        assertEquals("0.6666666667", calc("2÷3"))
        assertEquals("-6", calc("2−8"))
    }

    @Test fun percent() {
        assertEquals("220", calc("200+10%"))
        assertEquals("180", calc("200−10%"))
        assertEquals("20", calc("200×10%"))
        assertEquals("0.5", calc("50%"))
    }

    @Test fun negativesAndBrackets() {
        assertEquals("-2", calc("(−5+3"))
        assertEquals("-15", calc("3×(−5)"))
        assertEquals("8", calc("3−−5"))
        assertEquals("21", calc("3(4+3)"))
        assertEquals("20", calc("(2+3)(4"))
    }

    @Test fun scientific() {
        assertEquals("0.5", calc("sin(30)"))
        assertEquals("0", calc("sin(180)"))
        assertEquals("1", calc("tan(45)"))
        assertEquals("30", calc("sin⁻¹(0.5)"))
        assertEquals("1", calc("sin(π÷2)", deg = false))
        assertEquals("3", calc("√(9)"))
        assertEquals("3", calc("∛(27)"))
        assertEquals("2", calc("log(100)"))
        assertEquals("1", calc("ln(e)"))
        assertEquals("8", calc("2^(3)"))
        assertEquals("512", calc("2^(3)^(2)"))
        assertEquals("-4", calc("−2^(2)"))
        assertEquals("120", calc("5!"))
        assertEquals("5", calc("abs(−5)"))
        assertEquals("1024", calc("2^(10)"))
        assertEquals("6.2831853072", calc("2π"))
    }

    @Test fun errors() {
        assertThrows(CalcException::class.java) { calc("5÷0") }
        assertThrows(CalcException::class.java) { calc("√(−4)") }
        assertThrows(CalcException::class.java) { calc("tan(90)") }
        assertThrows(CalcException::class.java) { calc("ln(0)") }
        assertThrows(CalcException::class.java) { calc("()") }
    }

    @Test fun formatting() {
        assertEquals("1E20", CalcEngine.format(1e20))
        assertEquals("1.5E-12", CalcEngine.format(1.5e-12))
        assertEquals("1,234,567.89", CalcEngine.group("1234567.89"))
        assertEquals("−1,000", CalcEngine.group("-1000"))
        assertEquals(listOf("−", "5"), CalcEngine.resultTokens("-5"))
        assertEquals(listOf("1E20"), CalcEngine.resultTokens("1E20"))
        assertEquals("2E20", CalcEngine.format(CalcEngine.evaluate(listOf("1E20", "×", "2"), true)))
    }

    @Test fun conversions() {
        val temp = CATEGORIES.first { it.name == "Temperature" }.units
        assertEquals(212.0, convert(100.0, temp[0], temp[1]), 1e-9)
        assertEquals(273.15, convert(0.0, temp[0], temp[2]), 1e-9)
        val len = CATEGORIES.first { it.name == "Length" }.units
        assertEquals(1.609344, convert(1.0, len[7], len[3]), 1e-9)
    }
}
