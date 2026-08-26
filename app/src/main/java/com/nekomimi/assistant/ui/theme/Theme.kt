/*
 * 猫猫助手 (Nekomimi) — Android accessibility text-rewriting assistant
 * Copyright (C) 2026 Verlintas
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.nekomimi.assistant.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// 白粉淡蓝配色规则：
//  - 按键只允许白色或粉色（primary/onPrimary/secondaryContainer 均为粉系，蓝色绝不落在控件上）
//  - 蓝色仅作为卡片背景使用（显式指定，见 NekoCardColors.BlueCard）
//  - 白色可用于文字、按钮、应用背景
object NekoCardColors {
    val BlueCardLight = Color(0xFFD6E9FF) // 淡蓝卡片背景（浅色模式）
    val BlueCardDark = Color(0xFF355279)   // 淡蓝卡片背景（深色模式）
}

private val LightColors = lightColorScheme(
    primary = Color(0xFFEC6BA5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E8),
    onPrimaryContainer = Color(0xFF3E0022),
    secondary = Color(0xFFB75E8A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E8),
    onSecondaryContainer = Color(0xFF3E0022),
    tertiary = Color(0xFF7C7580),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE9C9),
    onTertiaryContainer = Color(0xFF2A1600),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF6EBF0),
    onSurface = Color(0xFF211A1E),
    onSurfaceVariant = Color(0xFF5F5A5E),
    outline = Color(0xFFB9ABB1),
    outlineVariant = Color(0xFFE5D8DE),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB1D0),
    onPrimary = Color(0xFF66003A),
    primaryContainer = Color(0xFF8C2560),
    onPrimaryContainer = Color(0xFFFFD9E8),
    secondary = Color(0xFFE5A8C5),
    onSecondary = Color(0xFF3A0A28),
    secondaryContainer = Color(0xFF5C2A47),
    onSecondaryContainer = Color(0xFFFFD9E8),
    tertiary = Color(0xFFBFBCC4),
    onTertiary = Color(0xFF2A2530),
    tertiaryContainer = Color(0xFF4A3308),
    onTertiaryContainer = Color(0xFFFFE9C9),
    background = Color(0xFF1A1118),
    surface = Color(0xFF1A1118),
    surfaceVariant = Color(0xFF4A4146),
    onSurface = Color(0xFFF0E0E8),
    onSurfaceVariant = Color(0xFFD0C4C9),
    outline = Color(0xFF94868E),
    outlineVariant = Color(0xFF43383F),
    error = Color(0xFFFFB4AB),
)

@Composable
fun NekomimiTheme(
    // 固定浅色模式：不跟随系统深色主题，保证界面始终是白粉配色
    darkTheme: Boolean = false,
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
