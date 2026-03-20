package org.ethereumphone.andyclaw.ui.theme

import android.app.Activity
import android.content.Context
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

object SystemColorManager {
    private val DEFAULT_ACCENT = Blue40

    var accentColor by mutableStateOf(DEFAULT_ACCENT)
        private set

    fun refresh(context: Context) {
        val colorInt = Settings.Secure.getInt(
            context.contentResolver,
            "systemui_accent_color",
            DEFAULT_ACCENT.toArgb(),
        )
        accentColor = Color(colorInt)
    }
}

private val DarkColorScheme = darkColorScheme(
    primary = Blue40,
    onPrimary = Neutral99,
    primaryContainer = Blue20,
    onPrimaryContainer = Blue80,

    secondary = Blue30,
    onSecondary = Neutral99,
    secondaryContainer = Blue10,
    onSecondaryContainer = Blue60,

    tertiary = Blue50,
    onTertiary = Neutral00,
    tertiaryContainer = Blue20,
    onTertiaryContainer = Blue80,

    background = Neutral00,
    onBackground = Neutral99,

    surface = Neutral06,
    onSurface = Neutral95,
    surfaceVariant = Neutral15,
    onSurfaceVariant = Neutral80,

    surfaceContainerLowest = Neutral00,
    surfaceContainerLow = Neutral04,
    surfaceContainer = Neutral06,
    surfaceContainerHigh = Neutral10,
    surfaceContainerHighest = Neutral15,

    outline = Neutral40,
    outlineVariant = Neutral20,

    error = ErrorRed,
    onError = Neutral00,
    errorContainer = ErrorRedDark,
    onErrorContainer = ErrorRed,

    inverseSurface = Neutral90,
    inverseOnSurface = Neutral10,
    inversePrimary = Blue20,

    scrim = Neutral00,
    surfaceTint = Blue40,
)

@Composable
fun AndyClawTheme(
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val colorScheme = DarkColorScheme

    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                window.statusBarColor = Neutral00.toArgb()
                window.navigationBarColor = Neutral00.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
