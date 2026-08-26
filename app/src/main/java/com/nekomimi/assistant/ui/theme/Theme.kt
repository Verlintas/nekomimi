package com.nekomimi.assistant.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// 白粉淡蓝配色：背景纯白，主色柔粉，点缀淡蓝
private val LightColors = lightColorScheme(
    primary = Color(0xFFEC6BA5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E8),
    onPrimaryContainer = Color(0xFF3E0022),
    secondary = Color(0xFF5F9CD4),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E9FF),
    onSecondaryContainer = Color(0xFF001E33),
    tertiary = Color(0xFF9CB8E8),
    onTertiary = Color(0xFF0A2540),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF6EBF0),
    onSurface = Color(0xFF211A1E),
    onSurfaceVariant = Color(0xFF5F5A5E),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB1D0),
    onPrimary = Color(0xFF66003A),
    primaryContainer = Color(0xFF8C2560),
    onPrimaryContainer = Color(0xFFFFD9E8),
    secondary = Color(0xFFA9CBEF),
    onSecondary = Color(0xFF0A2F55),
    secondaryContainer = Color(0xFF355279),
    onSecondaryContainer = Color(0xFFD6E9FF),
    tertiary = Color(0xFFB9C8F0),
    onTertiary = Color(0xFF223A55),
    background = Color(0xFF1A1118),
    surface = Color(0xFF1A1118),
    surfaceVariant = Color(0xFF4A4146),
    onSurface = Color(0xFFF0E0E8),
    onSurfaceVariant = Color(0xFFD0C4C9),
    error = Color(0xFFFFB4AB),
)

@Composable
fun NekomimiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
