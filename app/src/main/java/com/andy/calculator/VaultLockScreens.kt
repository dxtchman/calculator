package com.andy.calculator

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    pin: Boolean,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(if (pin) v.filter { it.isDigit() } else v) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (pin) KeyboardType.NumberPassword else KeyboardType.Password,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            IconButton(onClick = { show = !show }) {
                Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (show) "Hide" else "Show")
            }
        },
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, focusedLabelColor = Accent, cursorColor = Accent),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun BusyOverlay(text: String?) {
    if (text == null) return
    Box(
        Modifier.fillMaxSize().background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.background(KeyNum, RoundedCornerShape(20.dp)).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = Accent)
            Spacer(Modifier.height(16.dp))
            Text(text, color = TextMain)
        }
    }
}

@Composable
private fun PinOrPassword(pin: Boolean, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent, selectedLabelColor = OnAccent)
        FilterChip(selected = pin, onClick = { onChange(true) }, label = { Text("PIN") }, colors = colors)
        FilterChip(selected = !pin, onClick = { onChange(false) }, label = { Text("Password") }, colors = colors)
    }
}

@Composable
fun VaultSetupScreen(app: AppViewModel) {
    var pin by remember { mutableStateOf(true) }
    var pw by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        error = when {
            pw.length < 4 -> if (pin) "Use at least 4 digits." else "Use at least 4 characters."
            pw != confirm -> if (pin) "PINs don't match." else "Passwords don't match."
            else -> null
        }
        if (error == null) app.setup(pw, pin)
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            Icon(Icons.Outlined.Lock, null, tint = Accent, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("Set up your locked folder", color = TextMain, fontSize = 24.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                "Photos, videos, files and notes you put here are encrypted and hidden from other apps. " +
                    "To open it, type sin666 on the calculator and press =.",
                color = TextDim,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            PinOrPassword(pin) {
                pin = it
                pw = ""
                confirm = ""
                error = null
            }
            Spacer(Modifier.height(12.dp))
            PasswordField(pw, { pw = it; error = null }, if (pin) "PIN" else "Password", pin)
            Spacer(Modifier.height(8.dp))
            PasswordField(
                confirm, { confirm = it; error = null }, if (pin) "Confirm PIN" else "Confirm password", pin,
                imeAction = ImeAction.Done, onDone = { submit() }
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = ClearRed)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "There's no way to recover your files if you forget it.",
                color = ClearRed.copy(alpha = 0.85f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { submit() },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Create locked folder", fontSize = 16.sp) }
            TextButton(onClick = { app.screen = Screen.Calculator }) { Text("Cancel", color = TextDim) }
        }
        BusyOverlay(app.busy)
    }
}

@Composable
fun VaultUnlockScreen(app: AppViewModel) {
    val pin = app.repo.usesPin
    var pw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val focus = remember { FocusRequester() }
    val lockedOut = now < app.lockedUntil

    LaunchedEffect(app.lockedUntil) {
        while (SystemClock.elapsedRealtime() < app.lockedUntil) {
            now = SystemClock.elapsedRealtime()
            delay(250)
        }
        now = SystemClock.elapsedRealtime()
    }
    LaunchedEffect(Unit) {
        try { focus.requestFocus() } catch (_: Exception) {}
    }

    fun submit() {
        if (pw.isEmpty() || lockedOut) return
        app.unlock(pw) { msg ->
            error = msg
            pw = ""
        }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))
            Icon(Icons.Outlined.Lock, null, tint = Accent, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("Locked folder", color = TextMain, fontSize = 24.sp)
            Spacer(Modifier.height(6.dp))
            Text(if (pin) "Enter your PIN" else "Enter your password", color = TextDim)
            Spacer(Modifier.height(24.dp))
            PasswordField(
                pw, { pw = it; error = null }, if (pin) "PIN" else "Password", pin,
                modifier = Modifier.focusRequester(focus),
                imeAction = ImeAction.Done,
                onDone = { submit() }
            )
            Spacer(Modifier.height(8.dp))
            if (lockedOut) {
                val secs = ((app.lockedUntil - now) / 1000) + 1
                Text("Too many attempts. Try again in ${secs}s.", color = ClearRed)
            } else {
                error?.let { Text(it, color = ClearRed) }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth()) {
                TextButton(onClick = { app.screen = Screen.Calculator }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text("Cancel", color = TextDim, fontSize = 16.sp)
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = { submit() },
                    enabled = !lockedOut && pw.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) { Text("Unlock", fontSize = 16.sp) }
            }
        }
        BusyOverlay(app.busy)
    }
}
