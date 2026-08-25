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

private val LightColors = lightColorScheme(
    primary = Color(0xFFE65100),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC5),
    onPrimaryContainer = Color(0xFF331500),
    secondary = Color(0xFF8D4F24),
    secondaryContainer = Color(0xFFFFDBC5),
    onSecondaryContainer = Color(0xFF331500),
    background = Color(0xFFFFF8E1),
    surface = Color(0xFFFFF8E1),
    surfaceVariant = Color(0xFFF5DDB8),
    onSurface = Color(0xFF211A12),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB687),
    onPrimary = Color(0xFF542300),
    primaryContainer = Color(0xFF763500),
    onPrimaryContainer = Color(0xFFFFDBC5),
    secondary = Color(0xFFF0B98C),
    onSecondary = Color(0xFF4A2700),
    background = Color(0xFF1A120B),
    surface = Color(0xFF1A120B),
    onSurface = Color(0xFFF0E0D0),
)

@Composable
fun NekomimiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
