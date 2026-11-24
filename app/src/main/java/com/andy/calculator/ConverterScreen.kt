@file:OptIn(ExperimentalMaterial3Api::class)

package com.andy.calculator

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

class ConvUnit(val name: String, val symbol: String, val toBase: (Double) -> Double, val fromBase: (Double) -> Double)

private fun lin(name: String, symbol: String, factor: Double) =
    ConvUnit(name, symbol, { it * factor }, { it / factor })

class Category(val name: String, val units: List<ConvUnit>, val defaultFrom: Int, val defaultTo: Int)

val CATEGORIES = listOf(
    Category(
        "Area", listOf(
            lin("Acres", "ac", 4046.8564224),
            lin("Ares", "a", 100.0),
            lin("Hectares", "ha", 10000.0),
            lin("Square centimetres", "cm²", 1e-4),
            lin("Square feet", "ft²", 0.09290304),
            lin("Square inches", "in²", 0.00064516),
            lin("Square metres", "m²", 1.0),
            lin("Square kilometres", "km²", 1e6),
            lin("Square miles", "mi²", 2589988.110336),
            lin("Square yards", "yd²", 0.83612736),
        ), 0, 6
    ),
    Category(
        "Length", listOf(
            lin("Millimetres", "mm", 0.001),
            lin("Centimetres", "cm", 0.01),
            lin("Metres", "m", 1.0),
            lin("Kilometres", "km", 1000.0),
            lin("Inches", "in", 0.0254),
            lin("Feet", "ft", 0.3048),
            lin("Yards", "yd", 0.9144),
            lin("Miles", "mi", 1609.344),
            lin("Nautical miles", "NM", 1852.0),
            lin("Mils", "mil", 0.0000254),
        ), 3, 7
    ),
    Category(
        "Temperature", listOf(
            ConvUnit("Celsius", "°C", { it }, { it }),
            ConvUnit("Fahrenheit", "°F", { (it - 32.0) * 5.0 / 9.0 }, { it * 9.0 / 5.0 + 32.0 }),
            ConvUnit("Kelvin", "K", { it - 273.15 }, { it + 273.15 }),
        ), 0, 1
    ),
    Category(
        "Volume", listOf(
            lin("UK gallons", "gal (UK)", 4.54609),
            lin("US gallons", "gal (US)", 3.785411784),
            lin("Litres", "L", 1.0),
            lin("Millilitres", "mL", 0.001),
            lin("Cubic centimetres", "cm³", 0.001),
            lin("Cubic metres", "m³", 1000.0),
            lin("Cubic inches", "in³", 0.016387064),
            lin("Cubic feet", "ft³", 28.316846592),
            lin("US quarts", "qt", 0.946352946),
            lin("US pints", "pt", 0.473176473),
            lin("US cups", "cup", 0.2365882365),
            lin("US fluid ounces", "fl oz", 0.0295735295625),
            lin("Tablespoons", "tbsp", 0.01478676478125),
            lin("Teaspoons", "tsp", 0.00492892159375),
        ), 1, 2
    ),
    Category(
        "Mass", listOf(
            lin("Tonnes", "t", 1000.0),
            lin("UK tons", "t (UK)", 1016.0469088),
            lin("US tons", "t (US)", 907.18474),
            lin("Pounds", "lb", 0.45359237),
            lin("Ounces", "oz", 0.028349523125),
            lin("Stones", "st", 6.35029318),
            lin("Kilograms", "kg", 1.0),
            lin("Grams", "g", 0.001),
            lin("Milligrams", "mg", 1e-6),
            lin("Carats", "ct", 0.0002),
        ), 3, 6
    ),
    Category(
        "Data", listOf(
            lin("Bits", "bit", 0.125),
            lin("Bytes", "B", 1.0),
            lin("Kilobytes", "KB", 1024.0),
            lin("Megabytes", "MB", 1048576.0),
            lin("Gigabytes", "GB", 1073741824.0),
            lin("Terabytes", "TB", 1099511627776.0),
            lin("Petabytes", "PB", 1125899906842624.0),
        ), 3, 4
    ),
    Category(
        "Speed", listOf(
            lin("Metres per second", "m/s", 1.0),
            lin("Metres per hour", "m/h", 1.0 / 3600.0),
            lin("Kilometres per second", "km/s", 1000.0),
            lin("Kilometres per hour", "km/h", 1.0 / 3.6),
            lin("Inches per second", "in/s", 0.0254),
            lin("Inches per hour", "in/h", 0.0254 / 3600.0),
            lin("Feet per second", "ft/s", 0.3048),
            lin("Feet per hour", "ft/h", 0.3048 / 3600.0),
            lin("Miles per second", "mi/s", 1609.344),
            lin("Miles per hour", "mph", 0.44704),
            lin("Knots", "kn", 1852.0 / 3600.0),
        ), 3, 9
    ),
    Category(
        "Time", listOf(
            lin("Milliseconds", "ms", 0.001),
            lin("Seconds", "s", 1.0),
            lin("Minutes", "min", 60.0),
            lin("Hours", "h", 3600.0),
            lin("Days", "d", 86400.0),
            lin("Weeks", "wk", 604800.0),
            lin("Months", "mo", 2629746.0),
            lin("Years", "yr", 31556952.0),
        ), 3, 2
    ),
)

fun convert(value: Double, from: ConvUnit, to: ConvUnit) = to.fromBase(from.toBase(value))

@Composable
fun ConverterScreen(onBack: () -> Unit, vibrate: Boolean) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = CATEGORIES.map { it.name } + "Tip"
    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        TopAppBar(
            title = { Text("Unit converter") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
        )
        ScrollableTabRow(selectedTabIndex = tab, containerColor = Bg, contentColor = Accent, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, name ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(name, color = if (tab == i) Accent else TextDim) }
                )
            }
        }
        if (tab < CATEGORIES.size) {
            // a fresh state per category
            androidx.compose.runtime.key(tab) { UnitConverter(CATEGORIES[tab], vibrate) }
        } else {
            TipCalculator(vibrate)
        }
    }
}

/** Shared number entry used by the converter and the tip calculator. */
class NumberInput(initial: String = "1") {
    var text by mutableStateOf(initial)

    fun press(k: String): String? {
        when (k) {
            "C" -> text = "0"
            "⌫" -> text = if (text.length <= 1 || (text.length == 2 && text.startsWith("-"))) "0" else text.dropLast(1)
            "±" -> text = if (text.startsWith("-")) text.drop(1) else if (text == "0") text else "-$text"
            "." -> if (!text.contains('.')) text += "."
            else -> {
                val digits = text.count { it.isDigit() }
                if (digits >= 15) return "Can't enter more than 15 digits."
                val decimals = if (text.contains('.')) text.substringAfter('.').length else 0
                if (text.contains('.') && decimals >= 10) return "Can't enter more than 10 digits after decimal point."
                text = when (text) {
                    "0" -> k
                    "-0" -> "-$k"
                    else -> text + k
                }
            }
        }
        return null
    }

    val value: Double get() = text.toDoubleOrNull() ?: 0.0
}

fun showInput(t: String): String {
    val neg = t.startsWith("-")
    val body = if (neg) t.drop(1) else t
    val grouped = CalcEngine.group(body.substringBefore('.').ifEmpty { "0" }) +
        if (body.contains('.')) "." + body.substringAfter('.') else ""
    return (if (neg) "−" else "") + grouped
}

@Composable
private fun UnitConverter(cat: Category, vibrate: Boolean) {
    var fromIdx by remember { mutableIntStateOf(cat.defaultFrom) }
    var toIdx by remember { mutableIntStateOf(cat.defaultTo) }
    var topActive by remember { mutableStateOf(true) }
    val input = remember { NumberInput("1") }
    val view = LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current

    val activeUnit = cat.units[if (topActive) fromIdx else toIdx]
    val otherUnit = cat.units[if (topActive) toIdx else fromIdx]
    val result = CalcEngine.group(CalcEngine.format(convert(input.value, activeUnit, otherUnit)))
    val topText = if (topActive) showInput(input.text) else result
    val bottomText = if (topActive) result else showInput(input.text)

    fun swapActive(toTop: Boolean) {
        if (toTop == topActive) return
        // the value you see becomes the one you're editing
        val shown = (if (toTop) topText else bottomText).replace(",", "").replace("−", "-")
        input.text = if (shown.contains('E')) "0" else shown
        topActive = toTop
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        UnitRow(cat, fromIdx, { fromIdx = it }, topText, topActive) { swapActive(true) }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            IconButton(onClick = {
                val f = fromIdx
                fromIdx = toIdx
                toIdx = f
            }) { Icon(Icons.Outlined.SwapVert, "Swap units", tint = Accent) }
        }
        UnitRow(cat, toIdx, { toIdx = it }, bottomText, !topActive) { swapActive(false) }
        Spacer(Modifier.weight(1f))
        ConverterKeypad(Modifier.fillMaxWidth().height(340.dp)) { k ->
            if (vibrate) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            input.press(k)?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun UnitRow(
    cat: Category,
    selected: Int,
    onSelect: (Int) -> Unit,
    value: String,
    active: Boolean,
    onActivate: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) KeyFn else KeyNum)
            .clickable(onClick = onActivate)
            .padding(16.dp)
    ) {
        Box {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = true }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(cat.units[selected].name, color = TextMain, fontSize = 16.sp)
                Icon(Icons.Outlined.ArrowDropDown, null, tint = TextDim)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                cat.units.forEachIndexed { i, u ->
                    DropdownMenuItem(
                        text = { Text("${u.name} (${u.symbol})", color = if (i == selected) Accent else TextMain) },
                        onClick = {
                            onSelect(i)
                            open = false
                        }
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                color = if (active) Accent else TextMain,
                fontSize = if (value.length > 14) 26.sp else 36.sp,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(cat.units[selected].symbol, color = TextDim, fontSize = 16.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
    }
}

@Composable
private fun ConverterKeypad(modifier: Modifier, onKey: (String) -> Unit) {
    val rows = listOf(
        listOf("7", "8", "9", "⌫"),
        listOf("4", "5", "6", "C"),
        listOf("1", "2", "3", "±"),
        listOf("0", "."),
    )
    Column(modifier) {
        rows.forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                row.forEach { k ->
                    val fn = k == "⌫" || k == "C" || k == "±"
                    val w = if (k == "0") 2f else if (k == ".") 2f else 1f
                    if (k == "⌫") {
                        Box(
                            Modifier
                                .weight(w)
                                .fillMaxHeight()
                                .padding(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(KeyFn)
                                .clickable { onKey(k) },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.AutoMirrored.Outlined.Backspace, "Backspace", tint = Accent) }
                    } else {
                        Key(
                            label = k,
                            modifier = Modifier.weight(w).fillMaxHeight(),
                            bg = if (fn) KeyFn else KeyNum,
                            fg = when (k) {
                                "C" -> ClearRed
                                "±" -> Accent
                                else -> TextMain
                            },
                            fontSize = 28.sp,
                            onClick = { onKey(k) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TipCalculator(vibrate: Boolean) {
    val bill = remember { NumberInput("0") }
    var tipPct by remember { mutableFloatStateOf(15f) }
    var people by remember { mutableIntStateOf(1) }
    val view = LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current

    val pct = tipPct.roundToInt()
    val amount = bill.value.coerceAtLeast(0.0)
    val tip = amount * pct / 100.0
    val total = amount + tip
    fun money(v: Double) = CalcEngine.group(String.format(java.util.Locale.US, "%.2f", v))

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(KeyFn).padding(16.dp)
        ) {
            Text("Bill", color = TextDim, fontSize = 15.sp)
            Text(
                showInput(bill.text),
                color = Accent,
                fontSize = 36.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tip  $pct%", color = TextMain, fontSize = 16.sp, modifier = Modifier.width(90.dp))
            Slider(
                value = tipPct,
                onValueChange = { tipPct = it },
                valueRange = 0f..50f,
                steps = 49,
                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                modifier = Modifier.weight(1f)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Split", color = TextMain, fontSize = 16.sp, modifier = Modifier.width(90.dp))
            Key("−", Modifier.width(56.dp).height(48.dp), KeyFn, Accent, 24.sp) { if (people > 1) people-- }
            Text(
                "$people ${if (people == 1) "person" else "people"}",
                color = TextMain,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Key("+", Modifier.width(56.dp).height(48.dp), KeyFn, Accent, 24.sp) { if (people < 100) people++ }
        }
        HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 8.dp))
        TipLine("Tip", money(tip))
        TipLine("Total", money(total))
        if (people > 1) TipLine("Each person pays", money(total / people), highlight = true)
        Spacer(Modifier.weight(1f))
        ConverterKeypad(Modifier.fillMaxWidth().height(300.dp)) { k ->
            if (vibrate) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            if (k == "±") return@ConverterKeypad
            bill.press(k)?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TipLine(label: String, value: String, highlight: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextDim, fontSize = 17.sp)
        Text(value, color = if (highlight) Accent else TextMain, fontSize = if (highlight) 24.sp else 19.sp)
    }
}
