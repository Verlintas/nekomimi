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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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

    // Android 13+ 通知权限（看门狗掉线提醒依赖通知）
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val needsNotifPermission = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    if (needsNotifPermission) {
        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
