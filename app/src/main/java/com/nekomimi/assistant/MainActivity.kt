/*
 * 猫猫助手 (Nekomimi) — Android accessibility text-rewriting assistant
 * Copyright (C) 2026 Verlintas
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.nekomimi.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nekomimi.assistant.engine.ConfigStore
import com.nekomimi.assistant.log.CrashHandler
import com.nekomimi.assistant.log.LogStore
import com.nekomimi.assistant.service.NekoAccessibilityService
import com.nekomimi.assistant.ui.AppState
import com.nekomimi.assistant.ui.screens.HomeScreen
import com.nekomimi.assistant.ui.screens.LogsScreen
import com.nekomimi.assistant.ui.screens.RulesScreen
import com.nekomimi.assistant.ui.screens.SettingsScreen
import com.nekomimi.assistant.ui.theme.NekomimiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogStore.init(this)
        CrashHandler.install(this)
        enableEdgeToEdge()
        setContent {
            NekomimiTheme {
                App()
            }
        }
    }
}

@Composable
private fun App() {
    val context = LocalContext.current
    val appState = remember { AppState(context.applicationContext) }
    var tab by remember { mutableIntStateOf(0) }

    // 从系统设置返回时刷新状态（无障碍开关/暂停可能已变化）
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                appState.refresh()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Android 13+ 通知权限（看门狗掉线提醒依赖通知）。
    // 仅在首次启动自动请求；拒绝后由首页引导手动开启（避免每次打开都弹权限框）。
    // 必须在 LaunchedEffect 中请求：rememberLauncherForActivityResult 的注册
    // 发生在首次组合完成之后，组合期间直接 launch() 会抛
    // "Launcher has not been initialized"（首帧闪退）。
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val needsNotifPermission = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    LaunchedEffect(Unit) {
        if (needsNotifPermission && !ConfigStore.hasAskedNotificationPermission(context)) {
            ConfigStore.markNotificationPermissionAsked(context)
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val items = listOf(
                    "首页" to Icons.Filled.Home,
                    "规则" to Icons.AutoMirrored.Filled.Rule,
                    "设置" to Icons.Filled.Settings,
                    "日志" to Icons.AutoMirrored.Filled.List,
                )
                items.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            0 -> HomeScreen(appState, modifier)
            1 -> RulesScreen(appState, modifier)
            2 -> SettingsScreen(appState, modifier)
            3 -> LogsScreen(appState, modifier)
        }
    }
}
