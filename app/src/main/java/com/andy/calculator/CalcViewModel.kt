package com.andy.calculator

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(val tokens: List<String>, val expression: String, val result: String)

private const val MAX_DIGITS = 15
private const val MAX_DECIMALS = 10
private const val MAX_TOKENS = 100
private const val MAX_HISTORY = 100

class CalcViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("calc", Context.MODE_PRIVATE)

    val tokens = mutableStateListOf<String>()
    var cursor by mutableIntStateOf(0)
        private set
    var showingResult by mutableStateOf(false)
        private set
    var lastExpression by mutableStateOf("")
        private set
    var preview by mutableStateOf("")
        private set

    var degrees by mutableStateOf(prefs.getBoolean("degrees", true))
        private set
    var second by mutableStateOf(false)
        private set
    var scientific by mutableStateOf(prefs.getBoolean("scientific", false))
        private set
    var showHistory by mutableStateOf(false)
    var vibration by mutableStateOf(prefs.getBoolean("vibration", true))
        private set

    val history = mutableStateListOf<HistoryEntry>()

    init {
        loadHistory()
    }

    private fun toast(msg: String) {
        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------- settings

    fun toggleDegrees() {
        degrees = !degrees
        prefs.edit().putBoolean("degrees", degrees).apply()
        refreshPreview()
    }

    fun toggleSecond() { second = !second }

    fun toggleScientific() {
        scientific = !scientific
        prefs.edit().putBoolean("scientific", scientific).apply()
    }

    fun toggleVibration() {
        vibration = !vibration
        prefs.edit().putBoolean("vibration", vibration).apply()
    }

    fun toggleHistory() { showHistory = !showHistory }

    // ---------------------------------------------------------------- editing helpers

    private val before: String? get() = tokens.getOrNull(cursor - 1)

    fun setCursor(i: Int) {
        if (showingResult) showingResult = false
        cursor = i.coerceIn(0, tokens.size)
    }

    /** After "=", digits start over but operators keep working on the result. */
    private fun leaveResult(startOver: Boolean) {
        if (!showingResult) return
        showingResult = false
        if (startOver) {
            tokens.clear()
            cursor = 0
        } else {
            cursor = tokens.size
        }
    }

    private fun insert(vararg new: String): Boolean {
        if (tokens.size + new.size > MAX_TOKENS) {
            toast("Can't enter more than $MAX_TOKENS characters.")
            return false
        }
        for (t in new) {
            tokens.add(cursor, t)
            cursor++
        }
        refreshPreview()
        return true
    }

    /** Inserts tokens that start a value, adding "×" when a value is right before them. */
    private fun insertValue(vararg new: String) {
        if (Tok.endsValue(before) && !(Tok.isDigitOrDot(new.first()) && Tok.isDigitOrDot(before!!))) {
            insert("×", *new)
        } else insert(*new)
    }

    /** Start and end (exclusive) of the run of digits around the cursor. */
    private fun numberRun(): IntRange {
        var s = cursor
        while (s > 0 && Tok.isDigitOrDot(tokens[s - 1])) s--
        var e = cursor
        while (e < tokens.size && Tok.isDigitOrDot(tokens[e])) e++
        return s until e
    }

    // ---------------------------------------------------------------- keys

    fun press(key: String) {
        when (key) {
            "C" -> clear()
            "( )" -> bracket()
            "%" -> postfix("%")
            "÷", "×", "−", "+" -> operator(key)
            "=" -> Unit // handled by equals()
            "+/−" -> negate()
            "." -> dot()
            else -> if (key.length == 1 && key[0].isDigit()) digit(key)
        }
    }

    fun pressScientific(key: String) {
        when (key) {
            "⇄" -> toggleSecond()
            "Rad", "Deg" -> toggleDegrees()
            "√" -> function("√(")
            "∛" -> function("∛(")
            "sin", "cos", "tan", "sin⁻¹", "cos⁻¹", "tan⁻¹",
            "sinh", "cosh", "tanh", "sinh⁻¹", "cosh⁻¹", "tanh⁻¹", "ln", "log" -> function("$key(")
            "eˣ" -> function("e^(")
            "2ˣ" -> function("2^(")
            "|x|" -> function("abs(")
            "1/x" -> reciprocal()
            "x²" -> powerSuffix("^(", "2", ")")
            "x³" -> powerSuffix("^(", "3", ")")
            "xʸ" -> powerSuffix("^(")
            "x!" -> postfix("!")
            "π", "e" -> {
                leaveResult(startOver = true)
                insertValue(key)
            }
        }
    }

    private fun digit(d: String) {
        leaveResult(startOver = true)
        val run = numberRun()
        val runTokens = tokens.subList(run.first, run.last + 1).toList()
        val digits = runTokens.count { it != "." }
        if (digits >= MAX_DIGITS) {
            toast("Can't enter more than $MAX_DIGITS digits.")
            return
        }
        val dotAt = runTokens.indexOf(".")
        if (dotAt >= 0 && cursor > run.first + dotAt) {
            val decimals = runTokens.size - dotAt - 1
            if (decimals >= MAX_DECIMALS) {
                toast("Can't enter more than $MAX_DECIMALS digits after decimal point.")
                return
            }
        }
        // "0" followed by a digit replaces the zero
        if (runTokens == listOf("0") && cursor == run.first + 1) {
            tokens[cursor - 1] = d
            refreshPreview()
            return
        }
        if (Tok.isLiteral(before ?: "")) insert("×", d) else insertValue(d)
    }

    private fun dot() {
        leaveResult(startOver = true)
        val run = numberRun()
        if (run.any { tokens[it] == "." }) return
        if (run.isEmpty()) insertValue("0", ".") else insert(".")
    }

    private fun operator(op: String) {
        leaveResult(startOver = false)
        val prev = before
        when {
            prev == null -> if (op == "−") insert("−") else toast(MSG_INVALID)
            Tok.isOp(prev) -> {
                val prevPrev = tokens.getOrNull(cursor - 2)
                if (prevPrev == null || Tok.opensBracket(prevPrev)) {
                    if (op != "−") toast(MSG_INVALID)
                } else {
                    tokens[cursor - 1] = op
                    refreshPreview()
                }
            }
            Tok.opensBracket(prev) -> if (op == "−") insert("−") else toast(MSG_INVALID)
            else -> insert(op)
        }
    }

    private fun postfix(p: String) {
        leaveResult(startOver = false)
        val prev = before
        if (prev != null && (Tok.isNumberPart(prev) || prev == ")" || prev in Tok.CONSTANTS || prev == "!")) {
            insert(p)
        } else toast(MSG_INVALID)
    }

    private fun bracket() {
        leaveResult(startOver = false)
        var open = 0
        for (i in 0 until cursor) {
            val t = tokens[i]
            if (Tok.opensBracket(t)) open++ else if (t == ")") open--
        }
        if (open > 0 && Tok.endsValue(before)) insert(")") else insertValue("(")
    }

    private fun function(f: String) {
        if (showingResult) {
            // apply the function to the result: √ on 81 -> √(81)
            val result = tokens.toList()
            showingResult = false
            tokens.clear()
            tokens.add(f)
            tokens.addAll(result)
            tokens.add(")")
            cursor = tokens.size
            refreshPreview()
            return
        }
        insertValue(f)
    }

    private fun reciprocal() {
        if (showingResult) {
            val result = tokens.toList()
            showingResult = false
            tokens.clear()
            tokens.addAll(listOf("1", "÷", "("))
            tokens.addAll(result)
            tokens.add(")")
            cursor = tokens.size
            refreshPreview()
            return
        }
        insertValue("1", "÷", "(")
    }

    private fun powerSuffix(vararg t: String) {
        leaveResult(startOver = false)
        if (Tok.endsValue(before)) insert(*t) else toast(MSG_INVALID)
    }

    private fun negate() {
        if (showingResult) {
            showingResult = false
            if (tokens.firstOrNull() == "−") tokens.removeAt(0) else tokens.add(0, "−")
            cursor = tokens.size
            refreshPreview()
            return
        }
        val run = numberRun()
        val start = if (run.isEmpty()) cursor else run.first
        if (start > 0 && tokens[start - 1] == "(−") {
            tokens.removeAt(start - 1)
            cursor--
            // drop the bracket that closed the negative number, if any
            val end = if (run.isEmpty()) start - 1 else run.last
            if (end < tokens.size && tokens[end] == ")") {
                tokens.removeAt(end)
                if (cursor > end) cursor--
            }
            refreshPreview()
        } else {
            if (run.isEmpty() && Tok.endsValue(before)) {
                insert("×", "(−")
            } else {
                if (tokens.size + 1 > MAX_TOKENS) return
                tokens.add(start, "(−")
                cursor++
                refreshPreview()
            }
        }
    }

    fun backspace() {
        if (showingResult) {
            showingResult = false
            cursor = tokens.size
        }
        if (cursor == 0) return
        tokens.removeAt(cursor - 1)
        cursor--
        refreshPreview()
    }

    fun clear() {
        tokens.clear()
        cursor = 0
        showingResult = false
        lastExpression = ""
        preview = ""
    }

    /** Returns true when the secret code was entered. */
    fun equals(): Boolean {
        if (tokens.isEmpty()) return false
        val joined = tokens.joinToString("")
        if (joined == "sin(666" || joined == "sin(666)") {
            clear()
            return true
        }
        if (showingResult) return false
        val last = tokens.last()
        if (Tok.isOp(last) || Tok.opensBracket(last)) {
            toast(MSG_INVALID)
            return false
        }
        // a plain number doesn't make a calculation
        if (tokens.all { Tok.isNumberPart(it) }) return false
        try {
            val v = CalcEngine.evaluate(tokens, degrees)
            val formatted = CalcEngine.format(v)
            val closed = tokens.toMutableList()
            var open = 0
            for (t in closed) if (Tok.opensBracket(t)) open++ else if (t == ")") open--
            repeat(open.coerceAtLeast(0)) { closed.add(")") }
            val expression = displayString(closed)
            addHistory(HistoryEntry(closed, expression, formatted))
            lastExpression = expression
            tokens.clear()
            tokens.addAll(CalcEngine.resultTokens(formatted))
            cursor = tokens.size
            showingResult = true
            preview = ""
        } catch (e: CalcException) {
            toast(e.userMessage)
        }
        return false
    }

    private fun refreshPreview() {
        preview = ""
        if (tokens.isEmpty() || tokens.all { Tok.isNumberPart(it) }) return
        // drop a trailing operator or open bracket so the preview tracks what's typed
        val t = tokens.toMutableList()
        while (t.isNotEmpty() && (Tok.isOp(t.last()) || Tok.opensBracket(t.last()))) t.removeAt(t.size - 1)
        if (t.isEmpty() || t.all { Tok.isNumberPart(it) }) return
        preview = try {
            CalcEngine.group(CalcEngine.format(CalcEngine.evaluate(t, degrees)))
        } catch (e: CalcException) {
            ""
        }
    }

    // ---------------------------------------------------------------- copy & paste

    fun copyText(): String = when {
        showingResult -> tokens.joinToString("").replace("−", "-")
        preview.isNotEmpty() -> preview.replace(",", "").replace("−", "-")
        else -> displayString(tokens).replace(",", "")
    }

    fun paste(text: String) {
        val clean = text.trim().replace(",", "").replace(" ", "")
        if (clean.isEmpty()) return
        val out = ArrayList<String>()
        for (c in clean) {
            out.add(
                when (c) {
                    in '0'..'9', '.' -> c.toString()
                    '+' -> "+"
                    '-', '−' -> "−"
                    '*', 'x', 'X', '×' -> "×"
                    '/', '÷' -> "÷"
                    '(' -> "("
                    ')' -> ")"
                    '%' -> "%"
                    else -> {
                        toast(MSG_INVALID)
                        return
                    }
                }
            )
        }
        leaveResult(startOver = true)
        if (Tok.startsValue(out.first())) insertValue(*out.toTypedArray()) else insert(*out.toTypedArray())
    }

    // ---------------------------------------------------------------- history

    fun insertFromHistory(entry: HistoryEntry, useResult: Boolean) {
        val new = if (useResult) CalcEngine.resultTokens(entry.result) else entry.tokens
        if (new.isEmpty()) return
        leaveResult(startOver = true)
        if (Tok.startsValue(new.first())) insertValue(*new.toTypedArray()) else insert(*new.toTypedArray())
    }

    private fun addHistory(e: HistoryEntry) {
        history.add(e)
        while (history.size > MAX_HISTORY) history.removeAt(0)
        saveHistory()
    }

    fun clearHistory() {
        history.clear()
        saveHistory()
    }

    private fun saveHistory() {
        val arr = JSONArray()
        for (h in history) {
            arr.put(
                JSONObject()
                    .put("t", JSONArray(h.tokens))
                    .put("x", h.expression)
                    .put("r", h.result)
            )
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    private fun loadHistory() {
        val raw = prefs.getString("history", null) ?: return
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ta = o.getJSONArray("t")
                val toks = (0 until ta.length()).map { ta.getString(it) }
                history.add(HistoryEntry(toks, o.getString("x"), o.getString("r")))
            }
        } catch (_: Exception) {
        }
    }
}

/** Plain text form of an expression, with thousands separators. */
fun displayString(tokens: List<String>): String = buildDisplay(tokens).plain
