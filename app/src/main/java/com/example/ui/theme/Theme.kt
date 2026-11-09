package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StudioColorScheme = darkColorScheme(
    primary = DouyinRed,
    onPrimary = Color.White,
    primaryContainer = DouyinRedDim,
    onPrimaryContainer = DouyinRed,
    secondary = LiveGreen,
    onSecondary = Color.Black,
    secondaryContainer = Color(0x2600D26A),
    onSecondaryContainer = LiveGreen,
    tertiary = OnceBlue,
    onTertiary = Color.White,
    background = StudioBg,
    onBackground = StudioText,
    surface = StudioCard,
    onSurface = StudioText,
    surfaceVariant = StudioCardVariant,
    onSurfaceVariant = StudioSub,
    outline = StudioBorder,
    outlineVariant = Color(0x14FFFFFF),
    error = Color(0xFFFF4D4F),
    onError = Color.White
)

@Composable
fun DoumouLiveTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioColorScheme,
        typography = Typography,
        content = content
    )
}
