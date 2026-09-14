package com.fliip.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Orange = Color(0xFFFF6A00)
private val OrangeDark = Color(0xFFCC5200)

private val DarkColors = darkColorScheme(
    primary = Orange,
    onPrimary = Color.Black,
    secondary = Color(0xFF00E0C6),
    background = Color(0xFF0E0E10),
    surface = Color(0xFF17171B),
    surfaceVariant = Color(0xFF23232A),
)

private val LightColors = lightColorScheme(
    primary = OrangeDark,
    onPrimary = Color.White,
    secondary = Color(0xFF008579),
)

@Composable
fun FliipTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            window.statusBarColor = colors.background.toArgb()
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}
