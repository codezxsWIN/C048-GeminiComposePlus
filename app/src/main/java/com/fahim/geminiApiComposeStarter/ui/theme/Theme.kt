package com.fahim.geminiApiComposeStarter.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF80D5C6),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF174D44),
    onPrimaryContainer = Color(0xFFB4F2E5),
    secondary = Color(0xFFB6CBC4),
    secondaryContainer = Color(0xFF304740),
    tertiary = Color(0xFFB9CCE5),
    background = Color(0xFF101715),
    surface = Color(0xFF101715),
    surfaceContainer = Color(0xFF1C2421),
    surfaceContainerLow = Color(0xFF17201D),
    surfaceContainerHigh = Color(0xFF26302C),
    onSurface = Color(0xFFE1EAE5),
    onSurfaceVariant = Color(0xFFBFCAC4),
    outlineVariant = Color(0xFF3E4A44),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF176B5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3EEE3),
    onPrimaryContainer = Color(0xFF0A382F),
    secondary = Color(0xFF50675D),
    secondaryContainer = Color(0xFFE0EBE4),
    tertiary = Color(0xFF49627A),
    background = Color(0xFFF6F8F4),
    surface = Color(0xFFF6F8F4),
    surfaceContainer = Color(0xFFEDF1EB),
    surfaceContainerLow = Color(0xFFF1F5EF),
    surfaceContainerHigh = Color(0xFFE5EBE3),
    onSurface = Color(0xFF19251F),
    onSurfaceVariant = Color(0xFF46564D),
    outlineVariant = Color(0xFFC4CFC5),
)

@Composable
fun GeminiApiComposeStarterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
