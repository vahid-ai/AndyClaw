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
    private val DEFAULT_ACCENT = Color(0xFF050505)

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

@Composable
private fun getDarkColorScheme(primaryColor: Color) = darkColorScheme(
    primary = primaryColor,
    secondary = primaryColor.copy(alpha = 0.8f),
    tertiary = primaryColor.copy(alpha = 0.6f),
    background = BackgroundDark,
    surface = SurfaceDark,
    onPrimary = TextPrimaryWhite,
    onSecondary = TextPrimaryWhite,
    onTertiary = TextPrimaryWhite,
    onBackground = TextPrimaryWhite,
    onSurface = TextPrimaryWhite,
    primaryContainer = primaryColor.copy(alpha = 0.15f),
    secondaryContainer = primaryColor.copy(alpha = 0.1f),
    tertiaryContainer = primaryColor.copy(alpha = 0.05f),
)

@Composable
fun AndyClawTheme(
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val primaryColor = SystemColorManager.accentColor
    val colorScheme = getDarkColorScheme(primaryColor)

    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                window.statusBarColor = BackgroundDarker.toArgb()
                window.navigationBarColor = BackgroundDarker.toArgb()
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
