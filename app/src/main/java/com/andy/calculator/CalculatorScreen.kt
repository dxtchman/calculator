package com.andy.calculator

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

// ---------------------------------------------------------------- display text

class DisplayText(val text: AnnotatedString, private val starts: IntArray) {
    val plain: String get() = text.text

    fun charForToken(i: Int): Int = if (i < starts.size) starts[i] else text.length

    /** Nearest token boundary to a character offset. */
    fun tokenForChar(c: Int): Int {
        var best = starts.size
        var bestDist = abs(text.length - c)
        for (i in starts.indices) {
            val d = abs(starts[i] - c)
            if (d < bestDist) {
                best = i
                bestDist = d
            }
        }
        return best
    }
}

fun buildDisplay(tokens: List<String>, accent: Color = Accent): DisplayText {
    val starts = IntArray(tokens.size)
    val b = AnnotatedString.Builder()
    var i = 0
    while (i < tokens.size) {
        if (Tok.isDigitOrDot(tokens[i])) {
            var j = i
            while (j < tokens.size && Tok.isDigitOrDot(tokens[j])) j++
            var dotIdx = j
            for (k in i until j) if (tokens[k] == ".") { dotIdx = k; break }
            val intLen = dotIdx - i
            for (k in i until j) {
                val fromStart = k - i
                if (k < dotIdx && fromStart > 0 && (intLen - fromStart) % 3 == 0) b.append(",")
                starts[k] = b.length
                b.append(tokens[k])
            }
            i = j
        } else {
            starts[i] = b.length
            val t = tokens[i]
            when {
                Tok.isOp(t) -> b.withStyle(SpanStyle(color = accent)) { append(t) }
                Tok.isLiteral(t) -> b.append(t.replace("-", "−"))
                else -> b.append(t)
            }
            i++
        }
    }
    return DisplayText(b.toAnnotatedString(), starts)
}

// ---------------------------------------------------------------- screen

@Composable
fun CalculatorScreen(vm: CalcViewModel, onConverter: () -> Unit, onSecret: () -> Unit) {
    val config = LocalConfiguration.current
    val landscape = config.screenWidthDp > config.screenHeightDp
    val view = LocalView.current
    val tap: (() -> Unit) -> () -> Unit = { action ->
        {
            if (vm.vibration) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            action()
        }
    }
    val onEquals = tap { if (vm.equals()) onSecret() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .systemBarsPadding()
            .padding(horizontal = 10.dp)
    ) {
        CalcDisplay(vm, Modifier.weight(if (landscape) 0.9f else 1f).fillMaxWidth())
        Toolbar(vm, landscape, tap, onConverter)
        HorizontalDivider(color = Divider)
        Spacer(Modifier.height(6.dp))
        if (landscape) {
            Row(Modifier.weight(2.2f).fillMaxWidth()) {
                SciPad(vm, tap, Modifier.weight(1.3f).fillMaxHeight())
                Spacer(Modifier.width(8.dp))
                MainPad(vm, tap, onEquals, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            if (vm.scientific) SciPad(vm, tap, Modifier.weight(1.05f).fillMaxWidth())
            MainPad(vm, tap, onEquals, Modifier.weight(2.4f).fillMaxWidth())
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun CalcDisplay(vm: CalcViewModel, modifier: Modifier) {
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    val display = buildDisplay(vm.tokens)
    val currentDisplay by rememberUpdatedState(display)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val blinkTransition = rememberInfiniteTransition(label = "cursor")
    val blink by blinkTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = Easing { f -> if (f < 0.5f) 0f else 1f }),
            repeatMode = RepeatMode.Restart
        ),
        label = "blink"
    )
    val len = display.plain.length
    val textSize = when {
        vm.showingResult && len <= 12 -> 56.sp
        len <= 10 -> 50.sp
        len <= 14 -> 42.sp
        len <= 20 -> 34.sp
        else -> 28.sp
    }
    val scroll = rememberScrollState()
    LaunchedEffect(len) { scroll.scrollTo(scroll.maxValue) }

    Box(modifier) {
        if (!vm.degrees) {
            Text("Rad", color = TextDim, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp, start = 4.dp))
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(top = 28.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Bottom
        ) {
            if (vm.showingResult && vm.lastExpression.isNotEmpty()) {
                Text(
                    vm.lastExpression,
                    color = TextDim,
                    fontSize = 24.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                )
            }
            Box {
                Text(
                    text = display.text,
                    style = TextStyle(
                        fontSize = textSize,
                        lineHeight = textSize * 1.15f,
                        color = if (vm.showingResult) Accent else TextMain,
                        textAlign = TextAlign.End,
                        fontWeight = FontWeight.Light
                    ),
                    onTextLayout = { layout = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { pos ->
                                    layout?.let { l ->
                                        val off = l.getOffsetForPosition(pos)
                                        vm.moveCursor(currentDisplay.tokenForChar(off))
                                    }
                                },
                                onLongPress = { menu = true }
                            )
                        }
                        .drawWithContent {
                            drawContent()
                            val l = layout
                            if (!vm.showingResult && l != null) {
                                val c = currentDisplay.charForToken(vm.cursor)
                                    .coerceIn(0, l.layoutInput.text.length)
                                val r = l.getCursorRect(c)
                                val w = 2.dp.toPx()
                                // keep the cursor visible when it's at the very end
                                val x = r.left.coerceAtMost(size.width - w)
                                drawRect(
                                    Accent.copy(alpha = blink),
                                    topLeft = Offset(x, r.top + r.height * 0.12f),
                                    size = Size(w, r.height * 0.76f)
                                )
                            }
                        }
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Copy") }, onClick = {
                        menu = false
                        val t = vm.copyText()
                        if (t.isNotEmpty()) clipboard.setText(AnnotatedString(t))
                    })
                    DropdownMenuItem(text = { Text("Paste") }, onClick = {
                        menu = false
                        clipboard.getText()?.text?.let { vm.paste(it) }
                    })
                }
            }
            if (vm.preview.isNotEmpty()) {
                Text(
                    vm.preview,
                    color = TextDim,
                    fontSize = 30.sp,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun Toolbar(
    vm: CalcViewModel,
    landscape: Boolean,
    tap: (() -> Unit) -> () -> Unit,
    onConverter: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = tap { vm.toggleHistory() }) {
            Icon(Icons.Outlined.History, "History", tint = if (vm.showHistory) Accent else TextDim)
        }
        IconButton(onClick = tap(onConverter)) {
            Icon(Icons.Outlined.Straighten, "Unit converter", tint = TextDim)
        }
        if (!landscape) {
            IconButton(onClick = tap { vm.toggleScientific() }) {
                Icon(Icons.Outlined.Functions, "Scientific", tint = if (vm.scientific) Accent else TextDim)
            }
        }
        Spacer(Modifier.weight(1f))
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Outlined.MoreVert, "More", tint = TextDim)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Keypad vibration") },
                    trailingIcon = { Switch(checked = vm.vibration, onCheckedChange = { vm.toggleVibration() }) },
                    onClick = { vm.toggleVibration() }
                )
            }
        }
        IconButton(onClick = tap { vm.backspace() }) {
            Icon(Icons.AutoMirrored.Outlined.Backspace, "Backspace", tint = Accent)
        }
    }
}

@Composable
fun Key(
    label: String,
    modifier: Modifier,
    bg: Color,
    fg: Color,
    fontSize: TextUnit = 30.sp,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontSize = fontSize, maxLines = 1)
    }
}

@Composable
private fun MainPad(
    vm: CalcViewModel,
    tap: (() -> Unit) -> () -> Unit,
    onEquals: () -> Unit,
    modifier: Modifier,
) {
    val rows = listOf(
        listOf("C", "( )", "%"),
        listOf("7", "8", "9"),
        listOf("4", "5", "6"),
        listOf("1", "2", "3"),
        listOf("+/−", "0", "."),
    )
    Row(modifier) {
        Box(Modifier.weight(3f).fillMaxHeight()) {
            if (vm.showHistory) {
                HistoryPanel(vm, Modifier.fillMaxSize())
            } else {
                Column(Modifier.fillMaxSize()) {
                    rows.forEach { row ->
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            row.forEach { k ->
                                val fn = k == "C" || k == "( )" || k == "%"
                                Key(
                                    label = k,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    bg = if (fn) KeyFn else KeyNum,
                                    fg = when (k) {
                                        "C" -> ClearRed
                                        "( )", "%" -> Accent
                                        else -> TextMain
                                    },
                                    fontSize = if (k.length > 1) 26.sp else 32.sp,
                                    onClick = tap { vm.press(k) }
                                )
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            listOf("÷", "×", "−", "+", "=").forEach { k ->
                val eq = k == "="
                Key(
                    label = k,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    bg = if (eq) Accent else KeyFn,
                    fg = if (eq) OnAccent else Accent,
                    fontSize = 36.sp,
                    onClick = if (eq) onEquals else tap { vm.press(k) }
                )
            }
        }
    }
}

@Composable
private fun SciPad(vm: CalcViewModel, tap: (() -> Unit) -> () -> Unit, modifier: Modifier) {
    val mode = if (vm.degrees) "Rad" else "Deg"
    val keys = if (!vm.second) {
        listOf("⇄", mode, "√", "sin", "cos", "tan", "ln", "log", "1/x", "eˣ", "x²", "xʸ", "|x|", "π", "e")
    } else {
        listOf("⇄", mode, "∛", "sin⁻¹", "cos⁻¹", "tan⁻¹", "sinh", "cosh", "tanh", "sinh⁻¹", "cosh⁻¹", "tanh⁻¹", "2ˣ", "x³", "x!")
    }
    Column(modifier) {
        keys.chunked(5).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                row.forEach { k ->
                    val active = k == "⇄" && vm.second
                    Key(
                        label = k,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        bg = if (active) Accent else KeySci,
                        fg = if (active) OnAccent else TextMain,
                        fontSize = if (k.length > 4) 15.sp else 19.sp,
                        onClick = tap { vm.pressScientific(k) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryPanel(vm: CalcViewModel, modifier: Modifier) {
    Column(
        modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(KeyNum)
    ) {
        if (vm.history.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No history", color = TextDim, fontSize = 18.sp)
            }
        } else {
            val state = rememberLazyListState(initialFirstVisibleItemIndex = vm.history.size - 1)
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = state) {
                itemsIndexed(vm.history) { i, h ->
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            h.expression,
                            color = TextDim,
                            fontSize = 18.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.clickable { vm.insertFromHistory(h, useResult = false) }
                        )
                        Text(
                            "=" + CalcEngine.group(h.result),
                            color = Accent,
                            fontSize = 22.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.clickable { vm.insertFromHistory(h, useResult = true) }
                        )
                    }
                    if (i < vm.history.size - 1) HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        TextButton(
            onClick = { vm.clearHistory() },
            enabled = vm.history.isNotEmpty(),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp)
        ) {
            Text("Clear history", color = if (vm.history.isNotEmpty()) Accent else TextDim)
        }
    }
}
