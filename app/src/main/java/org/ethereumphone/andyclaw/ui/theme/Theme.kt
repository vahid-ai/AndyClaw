package org.ethereumphone.andyclaw.ui.theme

import android.app.Activity
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

class SystemAccentObserver(
    private val context: Context,
    private val onColorChanged: (Color) -> Unit,
) : ContentObserver(Handler(Looper.getMainLooper())) {
    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        val accentColor = try {
            val colorInt = Settings.Secure.getInt(context.contentResolver, "systemui_accent_color")
            Color(colorInt)
        } catch (_: Exception) {
            Color.Red
        }
        onColorChanged(accentColor)
    }

    fun register() {
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("systemui_accent_color"),
            false,
            this,
        )
    }

    fun unregister() {
        context.contentResolver.unregisterContentObserver(this)
    }
}

@Composable
fun rememberSystemAccentColor(context: Context): Color {
    var accentColor by remember { mutableStateOf(Color.Red) }

    LaunchedEffect(Unit) {
        try {
            val colorInt = Settings.Secure.getInt(context.contentResolver, "systemui_accent_color")
            accentColor = Color(colorInt)
        } catch (_: Exception) {
            accentColor = Color.Red
        }
    }

    DisposableEffect(context) {
        val observer = SystemAccentObserver(context) { newColor ->
            accentColor = newColor
        }
        observer.register()
        onDispose { observer.unregister() }
    }

    return accentColor
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
    val context = LocalContext.current
    val view = LocalView.current
    val primaryColor = rememberSystemAccentColor(context)
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
