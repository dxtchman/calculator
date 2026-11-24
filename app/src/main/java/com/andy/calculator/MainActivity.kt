package com.andy.calculator

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel

val Bg = Color(0xFF0D0E11)
val KeyNum = Color(0xFF1C1E24)
val KeyFn = Color(0xFF272A33)
val KeySci = Color(0xFF15171C)
val Accent = Color(0xFF4FD8C4)
val OnAccent = Color(0xFF062420)
val ClearRed = Color(0xFFFF6B6B)
val TextMain = Color(0xFFF2F3F5)
val TextDim = Color(0xFF8B8F99)
val Divider = Color(0x22FFFFFF)

private val Colors = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    secondary = Accent,
    error = ClearRed,
    background = Bg,
    surface = Color(0xFF16181D),
    surfaceVariant = KeyNum,
    surfaceContainer = Color(0xFF1A1C22),
    surfaceContainerHigh = Color(0xFF22252C),
    surfaceContainerHighest = Color(0xFF2A2D35),
    onSurface = TextMain,
    onSurfaceVariant = TextDim,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}

class MainActivity : ComponentActivity() {
    private val app: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme { Root(app, ::setSecure) }
        }
    }

    private fun setSecure(secure: Boolean) {
        if (secure) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onResume() {
        super.onResume()
        app.onReturn()
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app locks the folder, unless we sent the user to a picker or viewer.
        if (!isChangingConfigurations && !app.externalActivityOpen) app.lock()
    }
}

@Composable
private fun Root(app: AppViewModel, setSecure: (Boolean) -> Unit) {
    val calc: CalcViewModel = viewModel()
    val screen = app.screen
    LaunchedEffect(screen) {
        setSecure(screen == Screen.VaultHome || screen == Screen.VaultUnlock || screen == Screen.VaultSetup)
    }
    when (screen) {
        Screen.Calculator -> CalculatorScreen(
            calc,
            onConverter = { app.screen = Screen.Converter },
            onSecret = { app.openVault() }
        )
        Screen.Converter -> {
            BackHandler { app.screen = Screen.Calculator }
            ConverterScreen(onBack = { app.screen = Screen.Calculator }, vibrate = calc.vibration)
        }
        Screen.VaultSetup -> {
            BackHandler { app.screen = Screen.Calculator }
            VaultSetupScreen(app)
        }
        Screen.VaultUnlock -> {
            BackHandler { app.screen = Screen.Calculator }
            VaultUnlockScreen(app)
        }
        Screen.VaultHome -> VaultHomeScreen(app)
    }
}
